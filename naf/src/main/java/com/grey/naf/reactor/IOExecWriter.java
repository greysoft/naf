/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class IOExecWriter
{
	public final com.grey.naf.BufferSpec bufspec;
	private final com.grey.base.utils.ObjectQueue<java.nio.ByteBuffer> xmtq;
	private ChannelMonitor chanmon;
	private int writemark;  //current position in buffer at head of xmtq queue

	private java.nio.channels.FileChannel file_chan;
	private long file_bufsiz;
	private long file_limit;
	private long file_pos;

	public boolean isBlocked() {return (xmtq.size() != 0 || file_chan != null);}
	public boolean transmit(java.nio.channels.FileChannel fchan) throws java.io.IOException {return transmit(fchan, 0, 0, 0);}
	public boolean transmit(CharSequence data) throws java.io.IOException {return transmit(data, 0, data.length());}

	public IOExecWriter(com.grey.naf.BufferSpec spec)
	{
		bufspec = spec;
		xmtq = new com.grey.base.utils.ObjectQueue<java.nio.ByteBuffer>(java.nio.ByteBuffer.class, 5, 5);
	}

	protected void initChannel(ChannelMonitor cm)
	{
		clearChannel();
		chanmon = cm;
	}

	protected void clearChannel()
	{
		while (xmtq.size() != 0) dequeue();
		chanmon = null;
		file_chan = null;
	}

	public boolean transmit(byte[] data, int off, int len) throws java.io.IOException
	{
		boolean sent = false;
		while (len != 0) {
			int chunk = (len > bufspec.xmtbufsiz ? bufspec.xmtbufsiz : len);
			java.nio.ByteBuffer niobuf = allocBuffer();
			try {
				com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf, bufspec.directbufs);
				sent = transmit(niobuf);
			} finally {
				releaseBuffer(niobuf);
			}
			off += chunk;
			len -= chunk;
		}
		return sent;  //return status of last transmit
	}

	// NB: The caller is responsible for ensuring that this writer's buffer-size can take the encoded string
	public boolean transmit(java.nio.CharBuffer data, java.nio.charset.CharsetEncoder chenc) throws java.io.IOException
	{
		java.nio.ByteBuffer niobuf = allocBuffer();
		try {
			com.grey.base.utils.NIOBuffers.encode(data, niobuf, chenc, bufspec.directbufs);
			return transmit(niobuf);
		} finally {
			releaseBuffer(niobuf);
		}
	}

	// Convenience method which only works for 8-bit charsets - transposes the string into the
	// NIO buffer byte-for-char
	public boolean transmit(CharSequence data, int off, int len) throws java.io.IOException
	{
		boolean sent = false;
		while (len != 0) {
			int chunk = (len > bufspec.xmtbufsiz ? bufspec.xmtbufsiz : len);
			java.nio.ByteBuffer niobuf = allocBuffer();
			try {
				com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf, bufspec.directbufs);
				sent = transmit(niobuf);
			} finally {
				releaseBuffer(niobuf);
			}
			off += chunk;
			len -= chunk;
		}
		return sent;
	}

	// This method writes data in the range position() to limit() and on return position() is advanced by the
	// number of bytes written.
	// It's up to all callers to set position and limit as appropriate before calling into here. All the calls
	// from within this class guarantee that position=0.
	public boolean transmit(java.nio.ByteBuffer xmtbuf) throws java.io.IOException
	{
		if (chanmon.sslconn != null) {
			boolean done = chanmon.sslconn.transmit(xmtbuf);
			return (done && !isBlocked());
		}
		return write(xmtbuf);
	}

	protected boolean write(java.nio.ByteBuffer xmtbuf) throws java.io.IOException
	{
		int initpos = xmtbuf.position();
		if (isBlocked()) {
			if (file_chan != null) throw new IllegalStateException("Sending ByteBuffer before previous send-file completes");
			enqueue(xmtbuf, initpos, xmtbuf.remaining());
			return false;
		}
		int nbytes = sendBuffer(xmtbuf);
		if (nbytes == -1) return false;
		int remainbytes = xmtbuf.remaining();
		if (remainbytes == 0) return true;

		// the partially written (or completely unwritten) buffer becomes the head of the queue
		LEVEL lvl = LEVEL.TRC3;
		if (chanmon.dsptch.logger.isActive(lvl)) {
			chanmon.dsptch.logger.log(lvl, "IOExec: Send blocked with "+nbytes+"/"+remainbytes+"/xmtq="+xmtq.size()+" - "+chanmon.iochan);
		}
		writemark = initpos + nbytes;
		enqueue(xmtbuf, writemark, remainbytes);
		enableWrite();
		return false;
	}

	/**
	 * This may be called if the channel is still blocked by a backlog of ByteBuffer writes, but nothing (including another
	 * file) should be sent if the channel is still blocked on a previous file-send.
	 */
	public boolean transmit(java.nio.channels.FileChannel fchan, long bufsiz, long pos, long limit) throws java.io.IOException
	{
		if (limit == 0) limit = fchan.size();
		if (bufsiz == 0) bufsiz = limit - pos;

		if (chanmon.sslconn != null) {
			boolean done = chanmon.sslconn.transmit(fchan, pos, limit);
			return (done && !isBlocked());
		}
		file_pos = pos;
		file_limit = limit;
		file_bufsiz = bufsiz;

		if (isBlocked()) {
			// mark file to be sent once channel is unblocked.
			if (file_chan != null) throw new IllegalStateException("Sending file before previous send-file completes");
			file_chan = fchan;
			return false;
		}
		if (sendFile(fchan)) return true;
		enableWrite();
		file_chan = fchan;
		return false;
	}

	// Recall that a file-send can be initiated while previous ByteBuffer sends are still backlogged, so
	// this method makes sure all pending ByteBuffers have been sent before checking for a file-send.
	protected void handleIO() throws com.grey.base.FaultException, java.io.IOException
	{
		boolean done = drainQueue();

		if (done && file_chan != null) {
			done = sendFile(file_chan);
		}

		if (done) {
			// we've drained the write backlog, so reset Dispatcher registration
			file_chan = null;
			endTransmit();
		}
	}

	private void endTransmit() throws com.grey.base.FaultException, java.io.IOException
	{
		if (chanmon != null) {
			// The I/O operation is already over, so just swallow any exceptions.
			// They are probably due to a remote disconnect, and we can handle that later if/when we do any more I/O on this channel
			try {
				chanmon.disableWrite();
			} catch (Exception ex) {
				chanmon.dsptch.logger.log(LEVEL.TRC2, ex, false, "IOExec: failed to disable Write");
			}
			chanmon.transmitCompleted();
		}
	}

	private boolean drainQueue() throws java.io.IOException
	{
		while (xmtq.size() != 0) {
			if (chanmon == null) break;
			int nbytes;
			java.nio.ByteBuffer xmtbuf = xmtq.peek();
			xmtbuf.position(writemark);
			if ((nbytes = sendBuffer(xmtbuf)) == -1) return false;

			if (xmtbuf.remaining() != 0) {
				writemark += nbytes;
				return false;
			}
			dequeue();
			writemark = 0;
		}
		return true;
	}
	
	private void enqueue(java.nio.ByteBuffer xmtbuf, int xmtoff, int xmtbytes)
	{
		if (xmtbuf.isReadOnly()) {
			// no need to take a copy of buffer, as its contents are guaranteed to be preserved while it's on the transmit queue
			xmtq.add(xmtbuf);
			return;
		}
		writemark = 0; // we will be copying into start of allocated queue buffer

		while (xmtbytes != 0) {
			java.nio.ByteBuffer buf = allocBuffer();
			xmtbuf.position(xmtoff);
			xmtbuf.limit(xmtoff + xmtbytes);
			int nbytes = chanmon.dsptch.transfer(xmtbuf, buf);
			buf.limit(nbytes);
			xmtq.add(buf);
			xmtbytes -= nbytes;
			xmtoff += nbytes;
		}
	}

	// Remove head of queue and return to pool (if it came from the pool)
	private void dequeue()
	{
		java.nio.ByteBuffer buf = xmtq.remove();
		if (!buf.isReadOnly()) releaseBuffer(buf);
	}

	// It turns out that the first FileChannel.transferTo() on an unblocked connection succeeds whatever its size,
	// and only the second one would block (if we've overwhelmed the connection).
	// Eg. Even a million-byte write succeeds (returns nbytes == cnt) on an NIO pipe (which we know to be 8K) but
	// the next one returns zero. The mega buffers still result in a successful eventual send.
	private boolean sendFile(java.nio.channels.FileChannel fchan) throws java.io.IOException
	{
		java.nio.channels.WritableByteChannel iochan = (java.nio.channels.WritableByteChannel)chanmon.iochan;
		while (file_pos != file_limit) {
			long nbytes = 0;
			long cnt = file_limit - file_pos;
			if (cnt > file_bufsiz) cnt = file_bufsiz;

			try {
				// this throws on closed channel (java.io.IOException) or other error, so we can't be sure it's closed, but it might as well be
				nbytes = fchan.transferTo(file_pos, cnt, iochan);
			} catch (Exception ex) {
				LEVEL lvl = LEVEL.TRC3;
				String errmsg = "IOExec: file-send failed";
				if (chanmon.dsptch.logger.isActive(lvl)) errmsg += " on "+iochan;
				chanmon.brokenPipe(lvl, "Broken pipe on Send", errmsg, ex);
				return true;
			}
			file_pos += nbytes;
			if (nbytes != cnt) return false;
		}
		return true;
	}

	private int sendBuffer(java.nio.ByteBuffer xmtbuf) throws java.io.IOException
	{
		int nbytes = -1;
		java.nio.channels.WritableByteChannel iochan = (java.nio.channels.WritableByteChannel)chanmon.iochan;
		try {
			// this throws on closed channel (java.io.IOException) or other error, so we can't be sure it's closed, but it might as well be
			nbytes = iochan.write(xmtbuf);
		} catch (Exception ex) {
			LEVEL lvl = LEVEL.TRC3;
			String errmsg = "IOExec: buffer-send failed";
			if (chanmon.dsptch.logger.isActive(lvl)) errmsg += " on "+iochan;
			chanmon.brokenPipe(lvl, "Broken pipe on Send", errmsg, ex);
		}
		return nbytes;
	}

	private void enableWrite() throws java.io.IOException
	{
		try {
			// Register for Write notifications, replacing any existing Dispatcher notifications registered for this channel.
			// Assuming our code is error-free, an exception here typically means the remote party has closed the connection.
			chanmon.enableWrite();
		} catch (Exception ex) {
			chanmon.brokenPipe(LEVEL.TRC2, "I/O error on Send registration", "IOExec: failed to enable Write", ex);
		}
	}

	private java.nio.ByteBuffer allocBuffer()
	{
		java.nio.ByteBuffer buf = bufspec.xmtpool.extract();
		buf.clear();
		return buf;
	}

	private void releaseBuffer(java.nio.ByteBuffer buf)
	{
		bufspec.xmtpool.store(buf);
	}
}