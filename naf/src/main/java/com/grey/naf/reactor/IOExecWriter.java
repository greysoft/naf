/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class IOExecWriter
{
	private final com.grey.naf.BufferSpec bufspec;
	private final com.grey.base.utils.ObjectQueue<java.nio.ByteBuffer> xmtq;
	private ChannelMonitor chanmon;
	private int writemark;  // current position in buffer at head of queue

	private java.nio.channels.FileChannel file_chan;
	private long file_bufsiz;
	private long file_limit;
	private long file_pos;

	// temp working buffer, preallocated (on demand) for efficiency
	private byte[] transferbuf;

	public boolean isBlocked() {return (xmtq.size() != 0 || file_chan != null);}

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
			java.nio.ByteBuffer niobuf = bufspec.xmtpool.extract();
			try {
				com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf, bufspec.directbufs);
				sent = transmit(niobuf);
			} finally {
				bufspec.xmtpool.store(niobuf);
			}
			off += chunk;
			len -= chunk;
		}
		return sent;  //return status of last transmit
	}

	// NB: The caller is responsible for ensuring that this writer's buffer-size can take the encoded string
	public boolean transmit(java.nio.CharBuffer data, java.nio.charset.CharsetEncoder chenc) throws java.io.IOException
	{
		java.nio.ByteBuffer niobuf = bufspec.xmtpool.extract();
		try {
			com.grey.base.utils.NIOBuffers.encode(data, niobuf, chenc, bufspec.directbufs);
			return transmit(niobuf);
		} finally {
			bufspec.xmtpool.store(niobuf);
		}
	}

	// Convenience method which only works for 8-bit charsets - transposes the string into the
	// NIO buffer byte-for-char
	public boolean transmit(CharSequence data, int off, int len) throws java.io.IOException
	{
		boolean sent = false;
		while (len != 0) {
			int chunk = (len > bufspec.xmtbufsiz ? bufspec.xmtbufsiz : len);
			java.nio.ByteBuffer niobuf = bufspec.xmtpool.extract();
			try {
				com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf, bufspec.directbufs);
				sent = transmit(niobuf);
			} finally {
				bufspec.xmtpool.store(niobuf);
			}
			off += chunk;
			len -= chunk;
		}
		return sent;
	}

	// It's up to all callers to set position and limit as appropriate before calling into here.
	// All the calls from within this class guarantee that position=0.
	public boolean transmit(java.nio.ByteBuffer xmtbuf) throws java.io.IOException
	{
		int initpos = xmtbuf.position();
		if (isBlocked()) {
			enqueue(xmtbuf, initpos, xmtbuf.limit());
			return false;
		}
		writemark = sendBuffer(xmtbuf);
		if (writemark == -1) return false;
		int remainbytes = xmtbuf.remaining();
		if (remainbytes == 0) return true;
		
		// the partially written (potentially completely unwritten) buffer becomes the head of the queue - writemark is already set
		if (chanmon.dsptch.logger.isActive(LEVEL.TRC2)) {
			chanmon.dsptch.logger.log(LEVEL.TRC2, "IOExec: Send blocked with "+writemark+"/"+remainbytes+" - "+chanmon.iochan);
		}
		writemark += initpos;
		enqueue(xmtbuf, writemark, remainbytes);
		enableWrite();
		return false;
	}

	public boolean transmit(java.nio.channels.FileChannel fchan, long bufsiz, long pos, long limit) throws java.io.IOException
	{
		if (isBlocked()) throw new IllegalStateException("Cannot transmit FileChannel on blocked I/O channel");
		file_pos = pos;
		if ((file_limit = limit) == 0) file_limit = fchan.size();
		if ((file_bufsiz = bufsiz) == 0) file_bufsiz = file_limit - file_pos;

		if (sendFile(fchan)) return true;
		enableWrite();
		file_chan = fchan;
		return false;
	}

	protected void handleIO() throws com.grey.base.FaultException, java.io.IOException
	{
		boolean done = false;

		if (file_chan != null)
		{
			java.nio.channels.FileChannel fchan = file_chan;
			file_chan = null;
			done = sendFile(fchan);
			file_chan = fchan;
		}
		else
		{
			done = drainQueue();	
		}

		if (done)
		{
			// we've drained the write backlog, so reset Dispatcher registration
			endTransmit();
		}
	}

	private void endTransmit() throws com.grey.base.FaultException, java.io.IOException
	{
		file_chan = null;

		if (chanmon != null)
		{
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
		while (xmtq.size() != 0)
		{
			if (chanmon == null) break;
			int nbytes;
			java.nio.ByteBuffer xmtbuf = xmtq.peek();
			xmtbuf.position(writemark);
			if ((nbytes = sendBuffer(xmtbuf)) == -1) return false;

			if (xmtbuf.remaining() != 0)
			{
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
		if (xmtbuf.isReadOnly())
		{
			// no need to take a copy of buffer, as its contents are guaranteed to be preserved while it's on the transmit queue
			xmtq.add(xmtbuf);
			return;
		}
		writemark = 0; // we will be copying into start of allocated queue buffer

		while (xmtbytes != 0)
		{
			java.nio.ByteBuffer buf = bufspec.xmtpool.extract();
			int nbytes = (xmtbytes > bufspec.xmtbufsiz ? bufspec.xmtbufsiz : xmtbytes);
			buf.limit(nbytes);

			if (xmtbuf.hasArray())
			{
				buf.position(0);
				buf.put(xmtbuf.array(), xmtbuf.arrayOffset() + xmtoff, nbytes);
			}
			else if (buf.hasArray())
			{
				xmtbuf.position(xmtoff);
				xmtbuf.get(buf.array(), buf.arrayOffset(), nbytes);
			}
			else
			{
				// neither buffer exposes its backing array - need to copy via intermediate primitive buffer, to avail of bulk get/put
				if (transferbuf == null || transferbuf.length < nbytes) transferbuf = new byte[nbytes];
				xmtbuf.position(xmtoff);
				xmtbuf.get(transferbuf, 0, nbytes);
				buf.position(0);
				buf.put(transferbuf, 0, nbytes);
			}
			xmtq.add(buf);
			xmtbytes -= nbytes;
			xmtoff += nbytes;
		}
	}

	// Remove head of queue and return to pool (if it came from the pool)
	private void dequeue()
	{
		java.nio.ByteBuffer buf = xmtq.remove();
		if (!buf.isReadOnly()) bufspec.xmtpool.store(buf);
	}

	// It turns out that the first FileChannel.transferTo() on an unblocked connection succeeds whatever its size,
	// and only the second one would block (if we've overwhelmed the connection).
	// Eg. Even a million-byte write succeeds (returns nbytes == cnt) on an NIO pipe (which we know to be 8K) but
	// the next one returns zero. The mega buffers still result in a successful eventual send.
	private boolean sendFile(java.nio.channels.FileChannel fchan) throws java.io.IOException
	{
		java.nio.channels.WritableByteChannel iochan = (java.nio.channels.WritableByteChannel)chanmon.iochan;
		while (file_pos != file_limit)
		{
			long nbytes = 0;
			long cnt = file_limit - file_pos;
			if (cnt > file_bufsiz) cnt = file_bufsiz;

			try {
				// this throws on closed channel (java.io.IOException) or other error, so we can't be sure it's closed, but it might as well be
				nbytes = fchan.transferTo(file_pos, cnt, iochan);
			} catch (Exception ex) {
				chanmon.dsptch.logger.log(LEVEL.TRC3, ex, false, "IOExec: file-send failed on "+iochan);
				chanmon.ioDisconnected();
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
			chanmon.dsptch.logger.log(LEVEL.TRC3, ex, false, "IOExec: buffer-send failed on "+iochan);
			chanmon.ioDisconnected();
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
			chanmon.dsptch.logger.log(LEVEL.TRC2, ex, false, "IOExec: failed to enable Write");
			chanmon.ioDisconnected();
		}
	}
}