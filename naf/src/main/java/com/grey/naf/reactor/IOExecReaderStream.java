/*
 * Copyright 2014-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class IOExecReaderStream
	extends IOExecReader
{
	private int readmark;  // rcvbuf position - number of bytes we've already consumed and returned to caller, marks start position of next read
	private int scanmark; // rcvbuf position - number of bytes we've already scanned, looking for read demarcation. Always: readmark <= scanmark
	private int rcvmax;   // if non-zero, the max bytes to return - if rcvdlm not specified, then this is also the min size, ie. a fixed-size read
	private byte rcvdlm;  // the byte-value that demarcates delimited reads, eg. a line-oriented reader would specify LineFeed ('\n' = 0xA)

	IOExecReaderStream(com.grey.naf.BufferSpec spec)
	{
		super(spec);
	}

	void initChannel(CM_Stream cm)
	{
		super.initChannel(cm);
		if (rcvbuf != null) rcvbuf.clear(); //this is only compatible with receive(0) and non-SSL mode
		readmark = 0;
		scanmark = 0;
	}

	// Non-zero max means a fixed-size read.
	public void receive(int max) throws com.grey.base.FaultException, java.io.IOException
	{
		clearFlag(F_HASDLM);
		if (max != 0 && max > rcvbuf.capacity()) max = rcvbuf.capacity(); //just return the max possible
		enableReceive(max);
	}

	public void receiveDelimited(byte dlm) throws com.grey.base.FaultException, java.io.IOException
	{
		setFlag(F_HASDLM);
		rcvdlm = dlm;
		enableReceive(0);
	}

	// We use F_INRCVCB to guard against reentrancy here, ie. a ChannelMonitor calling this from within its ioReceived()
	// callback, and thus calling back into itself with potentially bad results (including infinite recursion).
	// It also guards against a parallel reentrancy bug in the SSL case, where we might call its deliver() method while
	// still inside our own handleIO() callback from SSLConnection.
	private void enableReceive(int max) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!enableReceive()) return;
		if (rcvbuf == null) return;
		rcvmax = max;
		if (isFlagSet(F_INRCVCB)) return; //beyond here lies re-entrancy

		//read any pending data in our local holding buffer
		CM_Stream cm = (CM_Stream)chanmon;
		while (readNextChunk(cm));
		//check if SSL layer (if any) has any more data buffered up - make sure we're still enabled first
		if (chanmon != null && cm.sslconn != null && isFlagSet(F_ENABLED)) cm.sslconn.deliver();
	}

	int handleIO(java.nio.ByteBuffer srcbuf) throws com.grey.base.FaultException, java.io.IOException
	{
		CM_Stream cm = (CM_Stream)chanmon;
		if (rcvbuf == null) {
			if (srcbuf != null) {
				throw new IllegalStateException("IOExecReaderStream.handleIO() called with srcbuf="+srcbuf+" for "+getClass().getName()+" with null rcvbuf");
			}
			cm.ioReceived(null);
			return 0;
		}
		int bufpos = 0;
		int nbytes = -1;  //will remain -1 if channel-read throws
		String discmsg = "Remote disconnect";

		// Windows (or Java?) doesn't reliably report a lost connection by returning -1, so trap exceptions and interpret in same way
		try {
			if (scanmark == rcvbuf.capacity()) {
				// the receive buffer is full and we've scanned up to the end of it (but not necessarily consumed it all)
				compact();
			}
			if (!isFlagSet(F_ARRBACK)) bufpos = rcvbuf.position();
			if (srcbuf != null) {
				nbytes = chanmon.dsptch.transfer(srcbuf, rcvbuf);
			} else {
				final java.nio.channels.ReadableByteChannel iochan = (java.nio.channels.ReadableByteChannel)chanmon.iochan;
				nbytes = iochan.read(rcvbuf);
			}
		} catch (Exception ex) {
			discmsg = "Broken pipe on Receive";
			LEVEL lvl = (ex instanceof RuntimeException ? LEVEL.ERR : LEVEL.TRC3);
			if (chanmon.dsptch.logger.isActive(lvl)) {
				chanmon.dsptch.logger.log(lvl, ex, lvl==LEVEL.ERR, "IOExec: read() failed on "+chanmon.getClass().getName()+"/E"+chanmon.getCMID()+"/"+chanmon.iochan);
			}
		}
		if (nbytes == 0) return 0;

		if (nbytes == -1) {
			chanmon.ioDisconnected(discmsg);
			return -1;
		}

		if (!isFlagSet(F_ARRBACK)) {
			// rewind to start of the block we just read, to copy it - the get() will then restore rcvbuf position to where it was after read()
			rcvbuf.position(bufpos);
			rcvbuf.get(userbuf.ar_buf, bufpos, nbytes);
		}

		while (readNextChunk(cm)) {
			if (chanmon != null && cm.sslconn != null && srcbuf == null) {
				// We've obviously switched to SSL mode while working through the contents of the last read, and
				// the rest of it is part of the SSL phase. Hand it off to the SSL manager and our own handleIO()
				// will not get called again to do a socket read.
				rcvbuf.limit(rcvbuf.position());
				rcvbuf.position(readmark);
				scanmark = readmark; //guard against callbacks from sslconn.handleIO()
				cm.sslconn.handleIO(rcvbuf);
				rcvbuf.clear();
				scanmark = 0;
				readmark = 0;
				break;
			}
		}
		return nbytes;
	}

	// Returns false to indicate rcvbuf definitely cannot satisfy another read
	private boolean readNextChunk(CM_Stream cm) throws com.grey.base.FaultException, java.io.IOException
	{
		final int buflimit = rcvbuf.position();
		if (!isFlagSet(F_ENABLED) || scanmark == buflimit) return false;
		int userbytes = 0; //number of bytes to return in callback

		if (isFlagSet(F_HASDLM)) {
			// NB: If we do find the delimiter byte, we set userbytes to include it
			while (scanmark != buflimit) {
				if (userbuf.ar_buf[rcvbuf0 + scanmark++] == rcvdlm) {
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
				userbuf.ar_off = rcvbuf0 + readmark;
				userbuf.ar_len = buflimit - readmark;
				rcvbuf.clear();
				scanmark = 0;
				readmark = 0;
				setFlag(F_INRCVCB);
				try {
					cm.ioReceived(userbuf);
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

		if (buflimit == rcvbuf.capacity() && userbytes == 0 && readmark == 0) {
			// We're now potentially deadlocked as rcvbuf is full of unread data, but it's not enough to satisfy the read op.
			// Just return the partial data to the caller.
			userbytes = buflimit;
		}
		if (userbytes == 0) return false;
		userbuf.ar_off = rcvbuf0 + readmark;
		userbuf.ar_len = userbytes;
		if (scanmark == buflimit) {
			// We can optimise by clearing the receive buffer now that it's been fully consumed.
			// Else we will keep bumping into the end of it and having to shift the contents leftward
			// in compact() so this minimises the number of such buffer-copy ops.
			rcvbuf.clear();
			scanmark = 0;
		}
		readmark = scanmark;  //gives same result as readmark += userbytes (assuming we didn't do a clear)
		setFlag(F_INRCVCB);
		try {
			cm.ioReceived(userbuf);
		} finally {
			clearFlag(F_INRCVCB);
		}
		return true;
	}

	/*
	 * Discard the bytes we've already returned to the caller.
	 * This can be more or less laborious, depending on whether there's still a fragment of unread data left at the
	 * end of rcvbuf (ie. we've read it off the connection, but haven't yet returned it to the caller) and on whether
	 * we have access to the backing array.
	 * If there is an unread data at the end, we have to preserve it, by shifting it down to the start of the buffer.
	 * 
	 * Note that in the event of userbuf not being the backing array of rcvbuf, we don't need to update the contents
	 * of rcvbuf as they've already been copied to userbuf. We just need to align its position.
	 */
	private void compact()
	{
		int unread = rcvbuf.position() - readmark;
		if (unread == 0) {
			// the buffer has been fully consumed by the user, so discard all contents
			rcvbuf.clear();
			scanmark = 0;
		} else {
			// there is a fragment of unread data at end of buffer so shift it down to start of buffer
			System.arraycopy(userbuf.ar_buf, rcvbuf0 + readmark, userbuf.ar_buf, rcvbuf0, unread);
			scanmark -= readmark;
			rcvbuf.position(unread);
		}
		readmark = 0;
	}

	// Discards everything in receive buffer, whether it's been read yet or not.
	public int flush()
	{
		final int nbytes = rcvbuf.position() - readmark;
		rcvbuf.clear();
		scanmark = 0;
		readmark = 0;
		return nbytes;
	}

	// As with compact(), we don't need to update rcvbuf contents if we're not using its backing array.
	public void pushback(byte[] data, int off, int len)
	{
		int unread = rcvbuf.position() - readmark;
		if (unread != 0) {
			byte[] tmp = chanmon.dsptch.allocMemBuffer(len + unread);
			System.arraycopy(data, off, tmp, 0, len);
			System.arraycopy(userbuf.ar_buf, rcvbuf0 + readmark, tmp, len, unread);
			rcvbuf.clear();
			data = tmp;
			off = 0;
			len += unread;
		}
		scanmark = 0;
		readmark = 0;
		System.arraycopy(data, off, userbuf.ar_buf, rcvbuf0, len);
		rcvbuf.position(len);
	}

	@Override
	void dumpState(StringBuilder sb, String dlm)
	{
		super.dumpState(sb, dlm);
		if (rcvbuf == null) return;
		String rdlm = (isFlagSet(F_HASDLM) ? "0x"+Integer.toHexString(rcvdlm) :String.valueOf(rcvmax));
		sb.append('/').append(rdlm);
		sb.append('/').append(String.valueOf(rcvbuf.position() - scanmark));
	}
}