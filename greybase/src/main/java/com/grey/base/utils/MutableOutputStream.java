/*
 * Copyright 2013-2014 Grey Software (Yusef Badri) - All rights reserved
 */
package com.grey.base.utils;

/**
 * Alternative to BufferedOutputStream, which allows the buffer contents to be manipulated before they are flushed.
 * Unlike BufferedOutputStream, our goal is not simply to minimise writes, but to do so subject to always keeping the
 * most recent buf.length bytes in our buffer.
 */
public class MutableOutputStream extends java.io.OutputStream
{
	private final java.io.OutputStream ostrm;
	private final byte[] buf;
	private final byte[] holder = new byte[1];
	private int buflen;

	public int getBufferCap() {return buf.length;}
	public synchronized int getBufferLen() {return buflen;}

	public MutableOutputStream(java.io.OutputStream s, int bufsiz) {
		ostrm = s;
		buf = new byte[bufsiz];
	}

	@Override
	public synchronized void write(int val) throws java.io.IOException
	{
		holder[0] = (byte)val;
		write(holder, 0, 1);
	}

	@Override
	public synchronized void write(byte wbuf[], int woff, int wlen) throws java.io.IOException
	{
		int excess = buflen + wlen - buf.length;
		if (excess > 0) {
			if (excess > buflen) {
				//means wlen > buf.length, so entire existing buffer gets flushed
				ostrm.write(buf, 0, buflen);
				buflen = 0;
				//write out the leading part of wbuf that doesn't fit in buffer
				int chunk = wlen - buf.length;
				ostrm.write(wbuf, woff, chunk);
				woff += chunk;
				wlen -= chunk;
			} else {
				//flush leading part of buffer to make space for wbuf
				ostrm.write(buf, 0, excess);
				buflen -= excess;
				System.arraycopy(buf, excess, buf, 0, buflen);
			}
		}
		System.arraycopy(wbuf, woff, buf, buflen, wlen);
		buflen += wlen;
	}

	@Override
	public synchronized void flush() throws java.io.IOException
	{
		synchronized (this) {
			if (buflen != 0) {
				ostrm.write(buf, 0, buflen);
				buflen = 0;
			}
		}
		ostrm.flush();
	}

	@Override
	public void close() throws java.io.IOException
	{
		try {
			flush();
		} finally {
			ostrm.close();
		}
	}

	public synchronized void truncateBy(int len)
	{
		if (len > buflen) throw new IllegalArgumentException("truncateBy="+len+" on buflen="+buflen+"/"+buf.length+" - "+this);
		buflen -= len;
	}

	public synchronized int getBufferedByte(int pos)
	{
		if (pos >= buflen) throw new IllegalArgumentException("get-"+pos+" on buflen="+buflen+"/"+buf.length+" - "+this);
		return buf[pos];
	}

	public synchronized int setBufferedByte(int pos, int val)
	{
		if (pos >= buflen) throw new IllegalArgumentException("set-"+pos+" on buflen="+buflen+"/"+buf.length+" - "+this);
		int old = buf[pos];
		buf[pos] = (byte)val;
		return old;
	}
}