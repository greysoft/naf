/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;
import com.grey.naf.errors.NAFException;

public class IOExecReaderStream
	extends IOExecReader
{
	private int readmark;  // getReceiveBuffer() position - number of bytes we've already consumed and returned to caller, marks start position of next read
	private int scanmark; // getReceiveBuffer() position - number of bytes we've already scanned, looking for read demarcation. Always: readmark <= scanmark
	private int rcvmax;   // if non-zero, the max bytes to return - if rcvdlm not specified, then this is also the min size, ie. a fixed-size read
	private byte rcvdlm;  // the byte-value that demarcates delimited reads, eg. a line-oriented reader would specify LineFeed ('\n' = 0xA)
	private final int rcvbuf0;

	IOExecReaderStream(com.grey.naf.BufferSpec spec)
	{
		super(spec);
		rcvbuf0 = (getUserBuffer() == null ? 0 : getUserBuffer().offset());
	}

	void initChannel(CM_Stream cm)
	{
		super.initChannel(cm);
		if (getReceiveBuffer() != null) getReceiveBuffer().clear(); //this is only compatible with receive(0) and non-SSL mode
		readmark = 0;
		scanmark = 0;
	}

	// Non-zero max means a fixed-size read.
	public void receive(int max) throws java.io.IOException
	{
		clearFlag(F_HASDLM);
		if (max != 0 && max > getReceiveBuffer().capacity()) max = getReceiveBuffer().capacity(); //just return the max possible
		enableReceive(max);
	}

	public void receiveDelimited(byte dlm) throws java.io.IOException
	{
		setFlag(F_HASDLM);
		rcvdlm = dlm;
		enableReceive(0);
	}

	// We use F_INRCVCB to guard against reentrancy here, ie. a ChannelMonitor calling this from within its ioReceived()
	// callback, and thus calling back into itself with potentially bad results (including infinite recursion).
	// It also guards against a parallel reentrancy bug in the SSL case, where we might call its deliver() method while
	// still inside our own handleIO() callback from SSLConnection.
	private void enableReceive(int max) throws java.io.IOException
	{
		if (!enableReceive()) return;
		if (getReceiveBuffer() == null) return;
		rcvmax = max;
		if (isFlagSet(F_INRCVCB)) return; //beyond here lies re-entrancy

		//deliver any pending data in our local holding buffer
		CM_Stream cm = (CM_Stream)getCM();
		while (deliverNextChunk(cm));
		//check if SSL layer (if any) has any more data buffered up - make sure we're still enabled first
		if (getCM() != null && cm.sslConnection() != null && isFlagSet(F_ENABLED)) cm.sslConnection().deliver();
	}

	int handleIO(java.nio.ByteBuffer srcbuf) throws java.io.IOException
	{
		CM_Stream cm = (CM_Stream)getCM();
		if (getReceiveBuffer() == null) {
			if (srcbuf != null) {
				throw new IllegalStateException("IOExecReaderStream.handleIO() called with srcbuf="+srcbuf+" for "+getClass().getName()+" with null rcvbuf");
			}
			cm.ioReceived(null);
			return 0;
		}
		int bufpos = 0; //only relevant if getReceiveBuffer() doesn't have backing array
		int nbytes = -1;  //will remain -1 if channel-read throws
		String discmsg = "Remote disconnect";

		// Windows (or Java?) doesn't reliably report a lost connection by returning -1, so trap exceptions and interpret in same way
		try {
			if (scanmark == getReceiveBuffer().capacity()) {
				// the receive buffer is full and we've scanned up to the end of it (but not necessarily consumed it all)
				compact();
			}
			if (!isFlagSet(F_ARRBACK)) bufpos = getReceiveBuffer().position();
			if (srcbuf != null) {
				nbytes = getCM().getDispatcher().transfer(srcbuf, getReceiveBuffer());
			} else {
				final java.nio.channels.ReadableByteChannel iochan = (java.nio.channels.ReadableByteChannel)getCM().getChannel();
				nbytes = iochan.read(getReceiveBuffer());
			}
		} catch (Exception ex) {
			discmsg = "Broken pipe on Receive";
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : CM_TCP.LOGLEVEL_CNX);
			if (getCM().getLogger().isActive(lvl)) {
				getCM().getLogger().log(lvl, ex, lvl==LEVEL.ERR, "IOExec: read() failed on "+getCM().getClass().getName()+"/E"+getCM().getCMID()+"/"+getCM().getChannel());
			}
		}
		if (nbytes == 0) return 0;

		if (nbytes == -1) {
			getCM().ioDisconnected(discmsg);
			return -1;
		}

		if (!isFlagSet(F_ARRBACK)) {
			// rewind to start of the block we just read, to copy it - the get() will then restore getReceiveBuffer() position to where it was after read()
			getReceiveBuffer().position(bufpos);
			getReceiveBuffer().get(getUserBuffer().buffer(), bufpos, nbytes);
		}

		while (deliverNextChunk(cm)) {
			if (getCM() != null && cm.sslConnection() != null && srcbuf == null) {
				// We've obviously switched to SSL mode while working through the contents of the last read, and
				// the rest of it is part of the SSL phase. Hand it off to the SSL manager and our own handleIO()
				// will not get called again to do a socket read.
				getReceiveBuffer().limit(getReceiveBuffer().position());
				getReceiveBuffer().position(readmark);
				scanmark = readmark; //guard against callbacks from sslconn.handleIO()
				cm.sslConnection().handleIO(getReceiveBuffer());
				getReceiveBuffer().clear();
				scanmark = 0;
				readmark = 0;
				break;
			}
		}
		return nbytes;
	}

	// Returns false to indicate getReceiveBuffer() definitely cannot satisfy another user read
	private boolean deliverNextChunk(CM_Stream cm) throws java.io.IOException
	{
		final int buflimit = getReceiveBuffer().position();
		if (!isFlagSet(F_ENABLED) || scanmark == buflimit) return false;
		int userbytes = 0; //number of bytes to return in callback

		if (isFlagSet(F_HASDLM)) {
			// NB: If we do find the delimiter byte, we set userbytes to include it
			while (scanmark != buflimit) {
				if (getUserBuffer().buffer()[rcvbuf0 + scanmark++] == rcvdlm) {
					userbytes = scanmark - readmark;
					break;
				}
			}
		} else {
			if (rcvmax == 0) {
				// User just wants all data returned as it arrives - we can optimise this case.
				// For comparison with the other modes below, the theoretical sequence of ops
				// after setting buf.ar_off is:
				//     scanmark = buflimit; userbytes = scanmark - readmark; buf.ar_len = userbytes
				//     readmark = scanmark (or alternatively, readmark += userbytes)
				//     compact()
				// So we effectively condense them into 2 assignments with the same effect.
				getUserBuffer().set(getUserBuffer().buffer(), rcvbuf0 + readmark, buflimit - readmark);
				getReceiveBuffer().clear();
				scanmark = 0;
				readmark = 0;
				setFlag(F_INRCVCB);
				try {
					cm.ioReceived(getUserBuffer());
				} finally {
					clearFlag(F_INRCVCB);
				}
				return true; //there might be more data if app called pushback()
			}

			// we are in a fixed-sized read
			if (buflimit - readmark >= rcvmax) {
				// we have sufficient data to satisfy it
				userbytes = rcvmax;
				scanmark = readmark + userbytes;
			} else {
				scanmark = buflimit;
			}
		}

		if (buflimit == getReceiveBuffer().capacity() && userbytes == 0 && readmark == 0) {
			// We're now potentially deadlocked as getReceiveBuffer() is full of unread data, but it's not enough to satisfy the read op.
			// Just return the partial data to the caller.
			userbytes = buflimit;

		}
		if (userbytes == 0) return false;

		getUserBuffer().set(getUserBuffer().buffer(), rcvbuf0 + readmark, userbytes);
		if (scanmark == buflimit) {
			// We can optimise by clearing the receive buffer now that it's been fully consumed.
			// Else we will keep bumping into the end of it and having to shift the contents leftward
			// in compact() so this minimises the number of such buffer-copy ops.
			getReceiveBuffer().clear();
			scanmark = 0;
		}
		readmark = scanmark;  //gives same result as readmark += userbytes (assuming we didn't do a clear)
		setFlag(F_INRCVCB);
		try {
			cm.ioReceived(getUserBuffer());
		} finally {
			clearFlag(F_INRCVCB);
		}
		return true;
	}

	/*
	 * Discard the bytes we've already returned to the caller.
	 * This can be more or less laborious, depending on whether there's still a fragment of unread data left at the
	 * end of getReceiveBuffer() (ie. we've read it off the connection, but haven't yet returned it to the caller) and on whether
	 * we have access to the backing array.
	 * If there is unread data at the end, we have to preserve it, by shifting it down to the start of the buffer.
	 * 
	 * Note that in the event of userbuf not being the backing array of getReceiveBuffer(), we don't need to update the contents
	 * of getReceiveBuffer() as they've already been copied to userbuf. We just need to align its position.
	 */
	private void compact()
	{
		int unread = getReceiveBuffer().position() - readmark;
		if (unread == 0) {
			// the buffer has been fully consumed by the user, so discard all contents
			getReceiveBuffer().clear();
			scanmark = 0;
		} else {
			// there is a fragment of unread data at end of buffer so shift it down to start of buffer
			System.arraycopy(getUserBuffer().buffer(), rcvbuf0 + readmark, getUserBuffer().buffer(), rcvbuf0, unread);
			scanmark -= readmark;
			getReceiveBuffer().position(unread);
		}
		readmark = 0;
	}

	// Discards everything in receive buffer, whether it's been read yet or not.
	public int flush()
	{
		int nbytes = getReceiveBuffer().position() - readmark;
		getReceiveBuffer().clear();
		scanmark = 0;
		readmark = 0;
		return nbytes;
	}

	// As with compact(), we don't need to update getReceiveBuffer() contents if we're not using its backing array.
	public void pushback(byte[] data, int off, int len)
	{
		int unread = getReceiveBuffer().position() - readmark;
		if (unread != 0) {
			byte[] tmp = getCM().getDispatcher().allocMemBuffer(len + unread);
			System.arraycopy(data, off, tmp, 0, len);
			getUserBuffer().copyOut(readmark, tmp, len, unread);
			getReceiveBuffer().clear();
			data = tmp;
			off = 0;
			len += unread;
		}
		scanmark = 0;
		readmark = 0;
		getUserBuffer().copyIn(data, off, len);
		getReceiveBuffer().position(len);
	}

	@Override
	protected void dumpState(StringBuilder sb, String dlm)
	{
		super.dumpState(sb, dlm);
		if (getReceiveBuffer() == null) return;
		String rdlm = (isFlagSet(F_HASDLM) ? "0x"+Integer.toHexString(rcvdlm) :String.valueOf(rcvmax));
		sb.append('/').append(rdlm);
		sb.append('/').append(String.valueOf(getReceiveBuffer().position() - scanmark));
	}
}