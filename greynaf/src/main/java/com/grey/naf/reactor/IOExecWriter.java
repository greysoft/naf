/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.nio.channels.FileChannel;

import com.grey.base.config.SysProps;
import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.FileOps;
import com.grey.logging.Logger.LEVEL;
import com.grey.naf.errors.NAFException;

public class IOExecWriter
{
	static final int MAXBUFSIZ = SysProps.get("greynaf.io.xmtqbufsiz", 64*1024);
	static final int FILEBUFSIZ = SysProps.get("greynaf.io.filebufsiz", 8*1024*1024);
	private static final LEVEL WRBLOCKTRC = LEVEL.valueOf(SysProps.get("greynaf.io.blocktrc", LEVEL.OFF.toString()));

	private final com.grey.naf.BufferGenerator bufspec; //NB: xmtbufsiz is ignored as a starting point
	private final com.grey.base.collections.ObjectQueue<Object> xmtq;
	private CM_Stream chanmon;
	private int writemark; //current position in buffer at head of xmtq queue

	public boolean isBlocked() {return (xmtq.size() != 0);}
	public void transmit(FileChannel fchan) throws java.io.IOException {transmit(fchan, 0, false);}
	public void transmit(FileChannel fchan, long pos, boolean noclose) throws java.io.IOException {transmit(fchan, pos, 0, noclose);}
	public void transmit(java.nio.ByteBuffer xmtbuf) throws java.io.IOException {transmit(xmtbuf, false);}
	public void transmit(ByteArrayRef data) throws java.io.IOException {transmit(data.buffer(), data.offset(), data.size());}
	public void transmit(byte[] data) throws java.io.IOException {transmit(data, 0, data.length);}
	public void transmit(CharSequence data) throws java.io.IOException {transmit(data, 0, data.length());}
	//this is just a convenience which saves callers from having to cast ByteChars params
	public void transmit(ByteChars data) throws java.io.IOException {transmit((ByteArrayRef)data);}

	IOExecWriter(com.grey.naf.BufferGenerator spec)
	{
		bufspec = spec;
		xmtq = new com.grey.base.collections.ObjectQueue<Object>(Object.class, 4, 4);
	}

	void initChannel(CM_Stream cm)
	{
		clearChannel();
		chanmon = cm;
		writemark = 0;
	}

	void clearChannel()
	{
		while (xmtq.size() != 0) dequeue(null);
		chanmon = null;
	}

	public void transmit(byte[] data, int off, int len) throws java.io.IOException
	{
		while (len != 0) {
			final int chunk = Math.min(len, MAXBUFSIZ);
			java.nio.ByteBuffer niobuf = allocBuffer(chunk);
			try {
				com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf);
				if (transmit(niobuf, true)) niobuf = null;
			} finally {
				if (niobuf != null) releaseBuffer(niobuf);
			}
			off += chunk;
			len -= chunk;
		}
	}

	// Convenience method which only works for 8-bit charsets - transposes the string into the
	// NIO buffer byte-for-char
	public void transmit(CharSequence data, int off, int len) throws java.io.IOException
	{
		if (data.getClass() == com.grey.base.utils.ByteChars.class) {
			com.grey.base.utils.ByteChars bc = (com.grey.base.utils.ByteChars)data;
			transmit(bc.buffer(), bc.offset(off), len);
			return;
		}

		while (len != 0) {
			final int chunk = Math.min(len, MAXBUFSIZ);
			java.nio.ByteBuffer niobuf = allocBuffer(chunk);
			try {
				com.grey.base.utils.NIOBuffers.encode(data, off, chunk, niobuf);
				if (transmit(niobuf, true)) niobuf = null;
			} finally {
				if (niobuf != null) releaseBuffer(niobuf);
			}
			off += chunk;
			len -= chunk;
		}
	}

	// This method writes data in the range position() to limit() and on return position() is advanced by the
	// number of bytes written.
	// It's up to all callers to set position and limit as appropriate before calling into here. All the calls
	// from within this class guarantee that position=0.
	// Returns true to indicate that the given buffer has been put on xmtq, so caller shouldn't touch it again.
	private boolean transmit(java.nio.ByteBuffer xmtbuf, boolean is_poolbuf) throws java.io.IOException
	{
		if (chanmon.sslConnection() != null) {
			chanmon.sslConnection().transmit(xmtbuf);
			return false;
		}
		write(xmtbuf, is_poolbuf);
		return is_poolbuf && isBlocked();
	}

	void write(java.nio.ByteBuffer xmtbuf, boolean is_poolbuf) throws java.io.IOException
	{
		if (isBlocked()) {
			enqueue(xmtbuf, xmtbuf.remaining(), is_poolbuf);
			return;
		}
		final int nbytes = sendBuffer(xmtbuf);
		if (nbytes == -1) return;
		final int remainbytes = xmtbuf.remaining();
		if (remainbytes == 0) return;

		// the partially written (or completely unwritten) buffer becomes the head of the queue
		if (chanmon.getLogger().isActive(WRBLOCKTRC)) {
			chanmon.getLogger().log(WRBLOCKTRC, "IOExec: Buffer-send blocked with "+nbytes+"/"+remainbytes
					+" - "+chanmon.getClass().getName()+"/E"+chanmon.getCMID()+"/"+chanmon.getChannel());
		}
		writemark = enqueue(xmtbuf, remainbytes, is_poolbuf);
		chanmon.enableWrite();
	}

	// Note that this method takes ownership of the file stream, and closes it when done.
	// Passing in noclose=true overrides this behaviour, and is intended for situations where the caller intends to make
	// several consecutive transmit calls on the same file (eg. at different offsets).
	// If this transmit op could not be carried out immediately and got enqueued, then noclose is ignored and this class
	// takes responsibility for closing the file regardless.
	public void transmit(java.nio.channels.FileChannel fchan, long pos, long lmt, boolean noclose) throws java.io.IOException
	{
		try {
			if (chanmon.sslConnection() != null) {
				final java.nio.ByteBuffer niobuf = chanmon.getDispatcher().allocNIOBuffer((int)Math.min(lmt-pos, MAXBUFSIZ));
				final long maxlmt = fchan.size();
				lmt = (lmt == 0 ? maxlmt : (lmt > maxlmt ? maxlmt : lmt));
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
			if (lmt == 0) lmt = fchan.size(); //sendFile() will correct lmt if it's too large
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

	public void transmitChunked(java.nio.channels.FileChannel fchan, long pos, long lmt, int bufsiz, boolean noclose) throws java.io.IOException
	{
		if (bufsiz == 0) bufsiz = FILEBUFSIZ;
		try {
			if (lmt == 0) lmt = fchan.size();
			while (pos < lmt) {
				long chunklmt = Math.min(pos+bufsiz, lmt);
				transmit(fchan, pos, chunklmt, true); //need to send all the chunks before we consider closing
				pos = chunklmt;
			}
			//even if an SSL connection is blocked, file will have been transferred to queued ByteBuffers so can close
			if (isBlocked() && chanmon.sslConnection() == null) noclose = true;
		} finally {
			if (!noclose) fchan.close();
		}
	}

	// Convenience method - FileChannel is closed by transmitChunked()
	public void transmit(java.nio.file.Path fh) throws java.io.IOException
	{
		java.nio.channels.FileChannel fchan = java.nio.channels.FileChannel.open(fh, FileOps.OPENOPTS_READ);
		transmitChunked(fchan, 0, 0, 0, false);
	}

	// Recall that a file-send can be initiated while previous ByteBuffer sends are still backlogged, so
	// this method makes sure all pending ByteBuffers have been sent before checking for a file-send.
	void handleIO() throws CM_Stream.BrokenPipeException
	{
		if (drainQueue()) {
			// we've drained the write backlog, so reset Dispatcher registration
			chanmon.transmitCompleted();
		}
	}

	// Beware: A broken pipe in sendFile() or sendBuffer() could mean that the whole channel has been diposed of and our
	// clearChannel() method called before they return, meaning that xmtq would be empty by the time we call dequeue() so
	// it has to handle that despite being apparently called from within a loop on non-zero xmtq.size()
	private boolean drainQueue() throws CM_Stream.BrokenPipeException
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

	private int enqueue(java.nio.ByteBuffer databuf, int xmtbytes, boolean is_poolbuf)
	{
		if (is_poolbuf || databuf.isReadOnly()) {
			// no need to take copy of read-only buffer, as it is guaranteed to be preserved while on the queue
			int pos = databuf.position();
			if (pos == 0 || xmtq.size() == 0) {
				// but make sure we don't put offset buffer on middle of queue - writemark is expected to be zero there
				xmtq.add(databuf);
				return pos;
			}
		}

		while (xmtbytes != 0) {
			final int chunk = Math.min(xmtbytes, MAXBUFSIZ);
			final java.nio.ByteBuffer qbuf = allocBuffer(chunk);
			final int nbytes = chanmon.getDispatcher().transfer(databuf, qbuf);
			qbuf.limit(nbytes);
			xmtq.add(qbuf);
			xmtbytes -= nbytes;
		}
		return 0; //buffer was copied to start of new buffer(s)
	}

	private void enqueue(java.nio.channels.FileChannel fchan, long pos, long lmt)
	{
		FileWrite fw = chanmon.getDispatcher().allocFileWrite().set(fchan, pos, lmt);
		xmtq.add(fw);
	}

	// Remove head of queue and return to pool (if it came from the pool)
	private void dequeue(Boolean is_filewrite)
	{
		Object obj = xmtq.remove();
		if (obj == null) return;
		boolean is_fw = (is_filewrite == null ? obj.getClass() == FileWrite.class : is_filewrite.booleanValue());
		if (is_fw) {
			releaseFileWrite((FileWrite)obj);
		} else {
			java.nio.ByteBuffer buf = (java.nio.ByteBuffer)obj;
			if (!buf.isReadOnly()) releaseBuffer(buf);
		}
	}

	// It turns out that the first FileChannel.transferTo() on an unblocked connection usually succeeds whatever its size,
	// and only the second one would block (if we've overwhelmed the connection).
	// Eg. Even a million-byte write succeeds (returns nbytes == cnt) on an NIO pipe (which we know to be 8K) but
	// the next one returns zero. The mega buffers still result in a successful eventual send.
	//
	// Returns True if we are finished with the file (whether due to success or failure) and False to indicate that
	// the send is still in progress, ie. the file is queued for send. The latter means that this IOExecWriter instance
	// has taken responsibility for closing the file stream.
	//
	// Note that on Windows, the fchan.transferTo() seems to do all or nothing, ie. it will return sendbytes or zero
	// even with files of many megabytes in size.
	// Linux on the other hand seems to return up to 65K or zero. I tried repeating partial writes which returned 65K
	// in case that's just a limiting buffer size and further 65K writes were possible, but it turned out we are genuinely
	// blocked and any further write returns zero, so it would just be a wasted system call.
	private boolean sendFile(java.nio.channels.FileChannel fchan, long pos, long lmt, FileWrite fw) throws CM_Stream.BrokenPipeException
	{
		final java.nio.channels.WritableByteChannel iochan = (java.nio.channels.WritableByteChannel)chanmon.getChannel();
		final long sendbytes = lmt - pos;
		try {
			//throws on closed channel (java.io.IOException) or other error, so can't be sure it's closed, but it might as well be
			final long nbytes = fchan.transferTo(pos, sendbytes, iochan);
			if (nbytes != sendbytes) {
				//We didn't write as much as we requested, so we're probably blocked, but it could also be because
				//we reached end-of-file.
				//NB: This code would also spot any reduction in file size since we started this file-send.
				final long maxlmt = fchan.size();
				pos += nbytes;
				if (lmt > maxlmt) lmt = maxlmt;
				if (pos < lmt) {
					//Not at end-of-file (or even end-of-send, if we were only sending partial file), so we're blocked
					if (fw == null) {
						// enqueue the file
						if (chanmon.getLogger().isActive(WRBLOCKTRC)) {
							chanmon.getLogger().log(WRBLOCKTRC, "IOExec: File-send="+sendbytes+" blocked with "+nbytes+"/"+sendbytes
									+" - "+chanmon.getClass().getName()+"/E"+chanmon.getCMID()+"/"+chanmon.getChannel());
						}
						enqueue(fchan, pos, lmt);
					} else {
						// file was already enqueued, so update its progress
						fw.offset = pos;
						fw.limit = lmt;
					}
					return false;
				}
			}
		} catch (Exception ex) {
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : CM_TCP.LOGLEVEL_CNX);
			String errmsg = "IOExec: file-send="+sendbytes+" failed";
			if (chanmon.getLogger().isActive(lvl)) errmsg += " on "+iochan;
			chanmon.brokenPipe(lvl, "Broken pipe on file-send", errmsg, ex);
		}
		return true;
	}

	private int sendBuffer(java.nio.ByteBuffer xmtbuf) throws CM_Stream.BrokenPipeException
	{
		final java.nio.channels.WritableByteChannel iochan = (java.nio.channels.WritableByteChannel)chanmon.getChannel();
		try {
			//throws on closed channel (java.io.IOException) or other error, so can't be sure it's closed, but it might as well be
			return iochan.write(xmtbuf);
		} catch (Exception ex) {
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : CM_TCP.LOGLEVEL_CNX);
			String errmsg = "IOExec: buffer-send failed";
			if (chanmon.getLogger().isActive(lvl)) errmsg += " on "+iochan;
			chanmon.brokenPipe(lvl, "Broken pipe on buffer-send", errmsg, ex);
			return -1;
		}
	}

	private java.nio.ByteBuffer allocBuffer(int siz)
	{
		return bufspec.allocBuffer(siz);
	}

	private void releaseBuffer(java.nio.ByteBuffer buf)
	{
		bufspec.releaseBuffer(buf);
	}

	// Note that because of methods like transmitChunked() we could have multiple FileWrite objects on the xmtq
	// with the same FileChannel object, so that's why we have to check if any references remain to a FileChannel
	// before we close.
	private void releaseFileWrite(FileWrite fw)
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
				LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC2);
				if (chanmon != null && chanmon.getLogger().isActive(lvl)) {
					chanmon.getLogger().log(lvl, ex, lvl==LEVEL.ERR, "IOExec: failed to close file - "+chanmon.getClass().getName()+"/E"+chanmon.getCMID());
				}
			}
		}
		fw.chan = null;
		chanmon.getDispatcher().releaseFileWrite(fw);
	}


	static final class FileWrite {
		java.nio.channels.FileChannel chan;
		long offset;
		long limit;

		public FileWrite set(java.nio.channels.FileChannel c, long pos, long lmt) {
			chan = c;
			offset = pos;
			limit = lmt;
			return this;
		}
	}
}