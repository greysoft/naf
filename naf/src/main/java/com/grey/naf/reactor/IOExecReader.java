/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class IOExecReader
{
	private static final int F_ARRBACK = 1 << 0; //rcvbuf has backing array
	private static final int F_ENABLED = 1 << 1; //receive is currently enabled
	private static final int F_HASDLM = 1 << 2;  //current receive phase is delimited by particular byte value (rcvdlm)
	private static final int F_INRCVCB = 1 << 3;  //inside ChannelMonitor.ioReceived() callback

	private final com.grey.naf.BufferSpec bufspec;
	private final java.nio.ByteBuffer rcvbuf;
	private final int rcvbuf0;
	private final com.grey.base.utils.ArrayRef<byte[]> userbuf;   //for passing data back to user (ie. the callback entity)

	private ChannelMonitor chanmon;
	private int readmark;  // rcvbuf position - number of bytes we've already consumed and returned to caller, marks start position of next read
	private int scanmark; // rcvbuf position - number of bytes we've already scanned, looking for read demarcation. Always: readmark <= scanmark
	private byte rcvdlm;  // the byte-value that demarcates delimited reads, eg. a line-oriented reader would specify LineFeed ('\n' = 0xA)
	private int rcvmax;   // if non-zero, the max bytes to return - if rcvdlm not specified, then this is also the min size, ie. a fixed-size read
	private byte iostate;

	private void setFlag(int f) {iostate |= f;}
	private void clearFlag(int f) {iostate &= ~f;}
	private boolean isFlagSet(int f) {return ((iostate & f) != 0);}

	public int getBufferSize() {return bufspec.rcvbufsiz;}

	public IOExecReader(com.grey.naf.BufferSpec spec)
	{
		bufspec = spec;
		rcvbuf = com.grey.base.utils.NIOBuffers.create(bufspec.rcvbufsiz, bufspec.directbufs);

		// this seems to depend on directbufs setting (false for direct buffers)
		byte[] arrb;
		if (rcvbuf.hasArray()) {
			setFlag(F_ARRBACK);
			arrb = rcvbuf.array();
			rcvbuf0 = rcvbuf.arrayOffset();
		} else {
			arrb = new byte[bufspec.rcvbufsiz];
			rcvbuf0 = 0;
		}
		userbuf = new com.grey.base.utils.ArrayRef<byte[]>(arrb);
		userbuf.ar_len = 0;
	}

	void initChannel(ChannelMonitor cm)
	{
		chanmon = cm;
		readmark = 0;
		scanmark = 0;
		rcvbuf.clear();
	}

	void clearChannel()
	{
		chanmon = null;
		clearFlag(F_ENABLED);
	}

	// Non-zero max means a fixed-size read.
	// NB: Note that max==0 is the only valid receive mode if F_UDP is set, and we enforce it silently.
	public void receive(int max) throws com.grey.base.FaultException, java.io.IOException
	{
		clearFlag(F_HASDLM);
		enableReceive(max);
	}

	public void receiveDelimited(byte dlm) throws com.grey.base.FaultException, java.io.IOException
	{
		if (chanmon.isUDP()) {
			throw new java.lang.IllegalArgumentException("IOExecReader: Delimited receives not allowed in UDP mode");
		}
		setFlag(F_HASDLM);
		rcvdlm = dlm;
		enableReceive(0);
	}

	// Regarding the likelihood that the ChannelMonitor.ioReceived() callback will in turn call this via our public
	// receive() or receiveDelimited() methods ...
	// From NAF's point of view it is perfectly safe to recursively call this method from inside its own receive() loop,
	// but we use F_INRCVCB to avoid doing so, to help the application code avoid reentrancy issues and prevent the
	// call stack growing indefinitely.
	private void enableReceive(int max) throws com.grey.base.FaultException, java.io.IOException
	{
		rcvmax = max;
		try {
			// assuming our code is error-free, an exception here typically means the remote party has closed the connection
			if (!isFlagSet(F_ENABLED)) {
				chanmon.enableRead();
				setFlag(F_ENABLED);
			}
		} catch (Exception ex) {
			chanmon.brokenPipe(LEVEL.TRC2, "I/O error on Receive registration", "IOExec: failed to enable Read", ex);
			return;
		}

		if (!isFlagSet(F_INRCVCB) && !chanmon.isUDP()) {
			//read any pending data in our local holding buffer
			while (readNextChunk());
			//if we're still enabled, kickstart the SSL component (if any) in case it stalled while we were suspended
			if (chanmon != null && chanmon.sslconn != null && isFlagSet(F_ENABLED)) chanmon.sslconn.forwardReceivedIO();
		}
	}

	public void endReceive()
	{
		if (isFlagSet(F_ENABLED) && chanmon != null) {
			// The I/O operation is already over, so just swallow any exceptions.
			// They are probably due to a remote disconnect, and we can handle that later if/when we do any more I/O on this channel
			try {
				chanmon.disableRead();
			} catch (Exception ex) {
				LEVEL lvl = LEVEL.TRC2;
				if (chanmon.dsptch.logger.isActive(lvl)) {
					chanmon.dsptch.logger.log(lvl, ex, false, "IOExec: failed to disable Read on E"+chanmon.cm_id);
				}
			}
		}
		clearFlag(F_ENABLED);
	}

	// Could check Dispatcher.canRead(SelectionKey), but read() safely returns zero if no data, so we can save ourselves the method call.
	void handleIO() throws com.grey.base.FaultException, java.io.IOException
	{
		handleIO(null);
	}

	// Returns False if we're unable to consume any of the incoming data
	boolean handleIO(java.nio.ByteBuffer srcbuf) throws com.grey.base.FaultException, java.io.IOException
	{
		int bufpos = 0;
		int nbytes = -1;  //will remain -1 if channel-read throws
		java.net.InetSocketAddress remaddr = null;
		String discmsg = "Remote disconnect";

		// Windows (or Java?) doesn't reliably report a lost connection by returning -1, so trap exceptions and interpret in same way
		try {
			if (chanmon.isUDP()) {
				java.nio.channels.DatagramChannel iochan = (java.nio.channels.DatagramChannel)chanmon.iochan;
				rcvbuf.clear();
				remaddr = (java.net.InetSocketAddress)iochan.receive(rcvbuf);
				nbytes = rcvbuf.position();
			} else {
				if (scanmark == bufspec.rcvbufsiz) {
					// the receive buffer is full and we've scanned up to the end of it (but not necessarily consumed it all)
					compact();
				}
				if (!isFlagSet(F_ARRBACK)) bufpos = rcvbuf.position();
				if (srcbuf != null) {
					nbytes = chanmon.dsptch.transfer(srcbuf, rcvbuf);
					if (nbytes == 0) return false; //rcvbuf must be full
				} else {
					java.nio.channels.ReadableByteChannel iochan = (java.nio.channels.ReadableByteChannel)chanmon.iochan;
					nbytes = iochan.read(rcvbuf);
				}
			}
		} catch (Exception ex) {
			if (ex instanceof java.net.PortUnreachableException) return true;  //indicates we've just received associated ICMP packet - discard
			discmsg = "Broken pipe on Receive";
			LEVEL lvl = LEVEL.TRC3;
			if (chanmon.dsptch.logger.isActive(lvl)) {
				chanmon.dsptch.logger.log(lvl, ex, false, "IOExec: read() failed on E"+chanmon.cm_id+"/"+chanmon.iochan);
			}
		}
		if (nbytes == 0) return true;

		if (nbytes == -1) {
			chanmon.ioDisconnected(discmsg);
			return true;
		}

		if (!isFlagSet(F_ARRBACK)) {
			// rewind to start of the block we just read, to copy it - the get() will then restore rcvbuf position to where it was after read()
			rcvbuf.position(bufpos);
			rcvbuf.get(userbuf.ar_buf, bufpos, nbytes);  //rcvbuf is known to be zero in this case
		}

		if (chanmon.isUDP()) {
			// All data is always passed up as soon as it arrives, so can optimise this case
			// INRCVCB flag doesn't apply here.
			userbuf.ar_off = rcvbuf0;
			userbuf.ar_len = nbytes;
			chanmon.ioReceived(userbuf, remaddr);
			return true;
		}

		while (readNextChunk()) {
			if (chanmon != null && chanmon.sslconn != null && srcbuf == null) {
				// We've obviously switched to SSL mode while working through the contents of the last read, and
				// the rest of it is part of the SSL phase. Hand it off to the SSL manager and our own handleIO()
				// will not get called again to do a socket read.
				rcvbuf.limit(rcvbuf.position());
				rcvbuf.position(readmark);
				scanmark = rcvbuf.position(); //guard against callbacks from sslconn.handleIO()
				chanmon.sslconn.handleIO(rcvbuf);
				rcvbuf.clear();
				scanmark = 0;
				readmark = 0;
				break;
			}
		}
		return true;
	}

	// Returns false to indicate rcvbuf definitely cannot satisfy another read
	private boolean readNextChunk() throws com.grey.base.FaultException, java.io.IOException
	{
		int buflimit = rcvbuf.position();
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
				// No need to set INRCVCB, because a recursive call to enableReceive() will find no more data.
				userbuf.ar_off = rcvbuf0 + readmark;
				userbuf.ar_len = buflimit - readmark;
				rcvbuf.clear();
				scanmark = 0;
				readmark = 0;
				chanmon.ioReceived(userbuf);
				return false;
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

		if (buflimit == bufspec.rcvbufsiz && userbytes == 0 && readmark == 0) {
			// We're now potentially deadlocked as rcvbuf is full of unread data, but it's not enough to satisfy the read op.
			// Just return the partial data to the caller.
			userbytes = buflimit;
		}
		if (userbytes == 0) return false;
		userbuf.ar_off = rcvbuf0 + readmark;
		userbuf.ar_len = userbytes;
		readmark = scanmark;  //gives same result as: readmark += userbytes;
		if (scanmark == buflimit) {
			// We can optimise by clearing the receive buffer now that it's been fully consumed.
			// Else we will keep bumping into the end of it and having to shift the contents
			// leftward in compact() so this minimises the number of such buffer-copy ops.
			compact();
		}
		setFlag(F_INRCVCB);
		try {
			chanmon.ioReceived(userbuf);
		} finally {
			clearFlag(F_INRCVCB);
		}
		return true;
	}

	/*
	 * Discard the bytes we've already returned to the caller.
	 * This can be more or less laborious, depending on whether there's still a fragment of unread data left at the end of the buffer (ie. we've
	 * read it off the connection, but haven't yet read if from this buffer and returned it to the caller) and on whether we have access to the
	 * backing array.
	 * If there is an unread data at the end, we have to preserve it, by shifting it down to the start of the buffer.
	 * Note that we could avoid doing this shift by clearing the buffer every time the caller catches up with the received data (ie. whenever
	 * we return user-data while readmark=buflimit) but the tradeoff is that we would probably end up having to clear the buffer on every single,
	 * read whereas this way it's only on every nth read ... maybe make this configurable so we can test it?
	 */
	private void compact()
	{
		if (readmark != scanmark) {
			// there is a fragment of unread data at end of buffer so shift it down to start of buffer
			scanmark -= readmark;  // this is the size of the unread fragment, and hence also the new scanmark value
			System.arraycopy(userbuf.ar_buf, rcvbuf0 + readmark, userbuf.ar_buf, rcvbuf0, scanmark);
			rcvbuf.position(scanmark);
		} else {
			// the buffer has been fully consumed by the user, so discard all contents
			rcvbuf.clear();
			scanmark = 0;
		}
		readmark = 0;
	}

	// Discards everything in receive buffer, whether it's been read yet or not.
	// In theory, we might have thought to return 'scanmark - readmark' since that has the same equilibrium value,
	// but it would only reach that after completing all the receive() calls that follow an iochan.read() and if this
	// method is called from a ChannelMonitor.ioReceived() callback, scanmark would not yet have advanced to its
	// ultimate position. So it would be wrong.
	public int flush()
	{
		int nbytes = rcvbuf.position() - readmark;
		rcvbuf.clear();
		scanmark = 0;
		readmark = 0;
		return nbytes;
	}
}