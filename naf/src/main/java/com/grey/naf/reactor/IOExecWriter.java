/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.logging.Logger.LEVEL;

public final class IOExecWriter
{
	static final int MAXBUFSIZ = SysProps.get("greynaf.io.xmtqbufsiz", 64*1024);
	static final int FILEBUFSIZ = SysProps.get("greynaf.io.filebufsiz", 8*1024*1024);
	private static final LEVEL WRBLOCKTRC = LEVEL.valueOf(SysProps.get("greynaf.io.blocktrc", LEVEL.OFF.toString()));

	public final com.grey.naf.BufferSpec bufspec; //NB: xmtbufsiz is just treated as a starting point
	private final com.grey.base.utils.ObjectQueue<Object> xmtq;
	private ChannelMonitor chanmon;
	private int writemark;  //current position in buffer at head of xmtq queue

	public boolean isBlocked() {return (xmtq.size() != 0);}
	public void transmit(java.nio.channels.FileChannel fchan) throws java.io.IOException {transmit(fchan, 0, false);}
	public void transmit(java.nio.channels.FileChannel fchan, long pos, boolean noclose) throws java.io.IOException {transmit(fchan, pos, 0, noclose);}
	public void transmit(CharSequence data) throws java.io.IOException {transmit(data, 0, data.length());}

	public IOExecWriter(com.grey.naf.BufferSpec spec)
	{
		bufspec = spec;
		xmtq = new com.grey.base.utils.ObjectQueue<Object>(Object.class, 4, 4);
	}

	void initChannel(ChannelMonitor cm)
	{
		clearChannel();
		chanmon = cm;
	}

	void clearChannel()
	{
		while (xmtq.size() != 0) dequeue(null);
		chanmon = null;
	}

	public void transmit(byte[] data, int off, int len) throws java.io.IOException
	{
		final java.nio.ByteBuffer niobuf = chanmon.dsptch.allocBuffer(Math.min(len, MAXBUFSIZ));
		while (len != 0) {
			final int chunk = Math.min(len, MAXBUFSIZ);
			niobuf.clear();
			com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf, bufspec.directbufs);
			transmit(niobuf);
			off += chunk;
			len -= chunk;
		}
	}

	// Convenience method which only works for 8-bit charsets - transposes the string into the
	// NIO buffer byte-for-char
	public void transmit(CharSequence data, int off, int len) throws java.io.IOException
	{
		final java.nio.ByteBuffer niobuf = chanmon.dsptch.allocBuffer(Math.min(len, MAXBUFSIZ));
		while (len != 0) {
			final int chunk = Math.min(len, MAXBUFSIZ);
			niobuf.clear();
			com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf, bufspec.directbufs);
			transmit(niobuf);
			off += chunk;
			len -= chunk;
		}
	}

	// This method writes data in the range position() to limit() and on return position() is advanced by the
	// number of bytes written.
	// It's up to all callers to set position and limit as appropriate before calling into here. All the calls
	// from within this class guarantee that position=0.
	public void transmit(java.nio.ByteBuffer xmtbuf) throws java.io.IOException
	{
		if (chanmon.sslconn != null) {
			chanmon.sslconn.transmit(xmtbuf);
			return;
		}
		write(xmtbuf);
	}

	void write(java.nio.ByteBuffer xmtbuf) throws ChannelMonitor.BrokenPipeException
	{
		final int initpos = xmtbuf.position();
		if (isBlocked()) {
			enqueue(xmtbuf, initpos, xmtbuf.remaining());
			return;
		}
		final int nbytes = sendBuffer(xmtbuf);
		if (nbytes == -1) return;
		final int remainbytes = xmtbuf.remaining();
		if (remainbytes == 0) return;

		// the partially written (or completely unwritten) buffer becomes the head of the queue
		if (chanmon.dsptch.logger.isActive(WRBLOCKTRC)) {
			chanmon.dsptch.logger.log(WRBLOCKTRC, "IOExec: Buffer-send blocked with "+nbytes+"/"+remainbytes
					+" - E"+chanmon.cm_id+"/"+chanmon.getClass().getName()+"/"+chanmon.iochan);
		}
		writemark = enqueue(xmtbuf, initpos + nbytes, remainbytes);
		chanmon.enableWrite();
	}

	// Note that this method takes ownership of the file stream, and closes it when done.
	// Passing in noclose=true overrides this behaviour, and is intended for situations where the
	// caller intends to make several consecutive transmit calls on the same file (eg. at different offsets).
	// If this transmit op could not be carried out immediately and got enqueued, then noclose is ignored
	// and this class still takes responsibility for closing the file.
	public void transmit(java.nio.channels.FileChannel fchan, long pos, long lmt, boolean noclose) throws java.io.IOException
	{
		if (lmt == 0) lmt = fchan.size();
		try {
			if (chanmon.sslconn != null) {
				final java.nio.ByteBuffer niobuf = chanmon.dsptch.allocBuffer((int)Math.min(lmt-pos, MAXBUFSIZ));
				while (pos < lmt) {
					niobuf.clear();
					int chunksiz = (int)(lmt - pos);
					if (niobuf.capacity() > chunksiz) niobuf.limit(chunksiz);
					int nbytes = fchan.read(niobuf, pos);
					niobuf.flip();
					transmit(niobuf);
					pos += nbytes;
				}
				return;
			}
			if (isBlocked()) {
				enqueue(fchan, pos, lmt);
				noclose = true;
				return;
			}
			if (sendFile(fchan, pos, lmt, null)) return;
			noclose = true; //this transmit op has been queued
		} finally {
			if (!noclose) fchan.close();
		}
		chanmon.enableWrite();
	}

	public void transmitChunked(java.nio.channels.FileChannel chan, long pos, long lmt, int bufsiz, boolean noclose) throws java.io.IOException
	{
		if (bufsiz == 0) bufsiz = FILEBUFSIZ;
		try {
			if (lmt == 0) lmt = chan.size();
			while (pos < lmt) {
				long chunklmt = Math.min(pos+bufsiz, lmt);
				transmit(chan, pos, chunklmt, true);
				pos = chunklmt;
			}
			if (isBlocked()) noclose = true;
		} finally {
			if (!noclose) chan.close();
		}
	}

	// convenience method
	public void transmit(java.io.File fh) throws java.io.IOException
	{
		java.io.FileInputStream strm = new java.io.FileInputStream(fh);
		try {
			java.nio.channels.FileChannel chan = strm.getChannel();
			strm = null;
			transmitChunked(chan, 0, 0, 0, false);
		} finally {
			if (strm != null) strm.close();
		}
	}

	// Recall that a file-send can be initiated while previous ByteBuffer sends are still backlogged, so
	// this method makes sure all pending ByteBuffers have been sent before checking for a file-send.
	void handleIO() throws ChannelMonitor.BrokenPipeException
	{
		if (drainQueue()) {
			// we've drained the write backlog, so reset Dispatcher registration
			chanmon.transmitCompleted();
		}
	}

	private boolean drainQueue() throws ChannelMonitor.BrokenPipeException
	{
		while (xmtq.size() != 0) {
			if (chanmon == null) return false;
			final Object obj = xmtq.peek();
			if (obj.getClass() == FileWrite.class) {
				final FileWrite fw = (FileWrite)obj;
				if (!sendFile(fw.chan, fw.offset, fw.limit, fw)) return false; //not fully transmitted
				dequeue(Boolean.TRUE);
			} else {
				final java.nio.ByteBuffer xmtbuf = (java.nio.ByteBuffer)obj;
				xmtbuf.position(writemark);
				final int nbytes = sendBuffer(xmtbuf);
				if (nbytes == -1) return false;

				if (xmtbuf.remaining() != 0) {
					//buffer not yet fully transmitted
					writemark += nbytes;
					return false;
				}
				writemark = 0; //point at start of next xmtq node
				dequeue(Boolean.FALSE);
			}
		}
		return true;
	}

	private int enqueue(java.nio.ByteBuffer databuf, int xmtoff, int xmtbytes)
	{
		if (databuf.isReadOnly()) {
			// no need to take a copy of buffer, as its contents are guaranteed to be preserved while it's on the transmit queue
			xmtq.add(databuf);
			return xmtoff;
		}

		while (xmtbytes != 0) {
			final int chunk = Math.min(xmtbytes, MAXBUFSIZ);
			final java.nio.ByteBuffer qbuf = allocBuffer(chunk);
			databuf.position(xmtoff);
			databuf.limit(xmtoff + chunk);
			final int nbytes = chanmon.dsptch.transfer(databuf, qbuf);
			qbuf.limit(nbytes);
			xmtq.add(qbuf);
			xmtbytes -= nbytes;
			xmtoff += nbytes;
		}
		return 0; //buffer was copied to start of new buffer(s)
	}

	private void enqueue(java.nio.channels.FileChannel fchan, long pos, long lmt)
	{
		xmtq.add(allocFileWrite(fchan, pos, lmt));
	}

	// Remove head of queue and return to pool (if it came from the pool)
	private void dequeue(Boolean is_filewrite)
	{
		final Object obj = xmtq.remove();
		boolean is_fw = (is_filewrite == null ? obj.getClass() == FileWrite.class : is_filewrite.booleanValue());
		if (is_fw) {
			final FileWrite fw = (FileWrite)obj;
			releaseFileWrite(fw);
		} else {
			final java.nio.ByteBuffer buf = (java.nio.ByteBuffer)obj;
			if (!buf.isReadOnly()) releaseBuffer(buf);
		}
	}

	// It turns out that the first FileChannel.transferTo() on an unblocked connection usually succeeds whatever its size,
	// and only the second one would block (if we've overwhelmed the connection).
	// Eg. Even a million-byte write succeeds (returns nbytes == cnt) on an NIO pipe (which we know to be 8K) but
	// the next one returns zero. The mega buffers still result in a successful eventual send.
	private boolean sendFile(java.nio.channels.FileChannel fchan, long pos, long lmt, FileWrite fw) throws ChannelMonitor.BrokenPipeException
	{
		final java.nio.channels.WritableByteChannel iochan = (java.nio.channels.WritableByteChannel)chanmon.iochan;
		final long sendbytes = lmt - pos;
		try {
			//throws on closed channel (java.io.IOException) or other error, so can't be sure it's closed, but it might as well be
			final long nbytes = fchan.transferTo(pos, sendbytes, iochan);
			if (nbytes != sendbytes) {
				//We didn't write as much as we requested, but that could be because we reached end-of-file.
				//NB: This code is also designed to spot any reduction in file size, since we started this file-send.
				final long maxlmt = fchan.size();
				if (lmt > maxlmt) lmt = maxlmt;
				if ((pos = pos + nbytes) < lmt) {
					//Not at end-of-file (or even end-of-send, if we were only sending part of the file)
					if (fw == null) {
						if (chanmon.dsptch.logger.isActive(WRBLOCKTRC)) {
							chanmon.dsptch.logger.log(WRBLOCKTRC, "IOExec: File-send="+sendbytes+" blocked with "+nbytes+"/"+sendbytes
									+" - E"+chanmon.cm_id+"/"+chanmon.getClass().getName()+"/"+chanmon.iochan);
						}
						enqueue(fchan, pos, lmt);
					} else {
						fw.offset = pos;
						fw.limit = lmt;
					}
					return false;
				}
			}
		} catch (Exception ex) {
			LEVEL lvl = LEVEL.TRC3;
			String errmsg = "IOExec: file-send="+sendbytes+" failed";
			if (chanmon.dsptch.logger.isActive(lvl)) errmsg += " on "+iochan;
			chanmon.brokenPipe(lvl, "Broken pipe on file-send", errmsg, ex);
			return false;
		}
		return true;
	}

	private int sendBuffer(java.nio.ByteBuffer xmtbuf) throws ChannelMonitor.BrokenPipeException
	{
		final java.nio.channels.WritableByteChannel iochan = (java.nio.channels.WritableByteChannel)chanmon.iochan;
		try {
			//throws on closed channel (java.io.IOException) or other error, so can't be sure it's closed, but it might as well be
			return iochan.write(xmtbuf);
		} catch (Exception ex) {
			LEVEL lvl = LEVEL.TRC3;
			String errmsg = "IOExec: buffer-send failed";
			if (chanmon.dsptch.logger.isActive(lvl)) errmsg += " on "+iochan;
			chanmon.brokenPipe(lvl, "Broken pipe on buffer-send", errmsg, ex);
			return -1;
		}
	}

	private java.nio.ByteBuffer allocBuffer(int siz)
	{
		java.nio.ByteBuffer buf = bufspec.xmtpool.extract();
		if (siz > buf.capacity()) buf = com.grey.base.utils.NIOBuffers.create(siz, bufspec.directbufs);
		buf.clear();
		return buf;
	}


	private void releaseBuffer(java.nio.ByteBuffer buf)
	{
		bufspec.xmtpool.store(buf);
	}

	private IOExecWriter.FileWrite allocFileWrite(java.nio.channels.FileChannel chan, long pos, long lmt)
	{
		return chanmon.dsptch.filewritepool.extract().set(chan, pos, lmt);
	}

	private void releaseFileWrite(IOExecWriter.FileWrite fw)
	{
		final int cnt = xmtq.size();
		boolean close = true;
		for (int idx = 0; idx != cnt; idx++) {
			final Object obj = xmtq.peek(idx);
			if (obj.getClass() == FileWrite.class && ((FileWrite)obj).chan == fw.chan) {
				close = false;
				break;
			}
		}
		if (close) {
			try {
				fw.chan.close();
			} catch (Exception ex) {
				LEVEL lvl = LEVEL.TRC2;
				if (chanmon != null && chanmon.dsptch.logger.isActive(lvl)) {
					chanmon.dsptch.logger.log(lvl, ex, false, "IOExec: failed to close file - E"+chanmon.cm_id+"/"+chanmon.getClass().getName());
				}
			}
		}
		fw.chan = null;
		chanmon.dsptch.filewritepool.store(fw);
	}


	static final class FileWrite
	{
		public static final class Factory
			implements com.grey.base.utils.ObjectWell.ObjectFactory
		{
			@Override
			public IOExecWriter.FileWrite factory_create() {return new IOExecWriter.FileWrite();}
		}

		public java.nio.channels.FileChannel chan;
		public long offset;
		public long limit;

		public IOExecWriter.FileWrite set(java.nio.channels.FileChannel c, long pos, long lmt)
		{
			chan = c;
			offset = pos;
			limit = lmt;
			return this;
		}
	}
}