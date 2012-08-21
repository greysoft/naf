/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class NIOBuffers
{
	public static final class BufferFactory
		implements ObjectWell.ObjectFactory
	{
		private final int bufsiz;
		private final boolean directbuf;

		public BufferFactory(int siz, boolean direct)
		{
			bufsiz = siz;
			directbuf = direct;
		}

		@Override
		public java.nio.ByteBuffer factory_create()
		{
			return NIOBuffers.create(bufsiz, directbuf);
		}
	}


	public static java.nio.ByteBuffer create(int bufsiz, boolean directbuf)
	{
		java.nio.ByteBuffer buf;
		
		if (directbuf) {
			buf = java.nio.ByteBuffer.allocateDirect(bufsiz);
		} else {
			buf = java.nio.ByteBuffer.allocate(bufsiz);
		}
		return buf;
	}

	public static java.nio.ByteBuffer ensureCapacity(java.nio.ByteBuffer bybuf, int mincap, boolean directbuf)
	{
		if (bybuf == null || bybuf.capacity() < mincap) {
			bybuf = create(mincap, directbuf);
		}
		return bybuf;
	}

	// This function potentially allocates the minimum ByteBuffer necessary to contain the given string. Caller must pre-allocate if they want larger.
	// The ByteBuffer is returned in its "flipped" state, such that it is ready for relative-get operations (ie. ready for reading from).
	//
	// This is really a convenience method for code that may potentially have any of these parameters available (typically config-driven
	// startup code), but performance-critical code should really know which form of the encode() it wants - and if it's not doing a direct
	// char-to-byte mapping, it should have a pre-allocated CharsetEncoder object.
	public static java.nio.ByteBuffer encode(CharSequence strbuf, java.nio.charset.CharsetEncoder chenc, java.nio.charset.Charset chset, String chname,
			java.nio.ByteBuffer bybuf, boolean directbuf)
	{
		if (chenc == null) {
			if (chset == null) {
				if (chname != null && chname.length() != 0) {
					chset = java.nio.charset.Charset.forName(chname);
				}
			}
			if (chset != null) chenc = chset.newEncoder();
		}
		if (chenc != null) return encode(strbuf, bybuf, chenc, directbuf);
		return encode(strbuf, bybuf, directbuf);
	}

	public static java.nio.ByteBuffer encode(CharSequence str, java.nio.ByteBuffer bybuf, java.nio.charset.CharsetEncoder chenc, boolean directbuf)
	{
		return encode(java.nio.CharBuffer.wrap(str), bybuf, chenc, directbuf);
	}

	public static java.nio.ByteBuffer encode(java.nio.CharBuffer chbuf, java.nio.ByteBuffer bybuf, java.nio.charset.CharsetEncoder chenc,
			boolean directbuf)
	{
		if (bybuf == null) {
			int bufsiz = (int)(chenc.maxBytesPerChar() * chbuf.capacity()) + 1;
			bybuf = create(bufsiz, directbuf);
		} else {
			bybuf.clear();
		}
		chenc.reset();
		java.nio.charset.CoderResult sts = chenc.encode(chbuf, bybuf, true);
		if (sts == java.nio.charset.CoderResult.UNDERFLOW) sts = chenc.flush(bybuf);
		if (sts != java.nio.charset.CoderResult.UNDERFLOW) throw new RuntimeException("Failed to byte-encode strlen=" + chbuf.length()
				+ " into bufsiz=" + bybuf.capacity() + " with " + chenc + " - " + sts);
		bybuf.flip();
		return bybuf;
	}

	// direct manipulation of ByteBuffer to directly map bytes to chars without any charset encoding - assumes 8-bit chars
	public static java.nio.ByteBuffer encode(CharSequence str, java.nio.ByteBuffer bybuf, boolean directbuf)
	{
		return encode(str, 0, str.length(), bybuf, directbuf);
	}

	// direct manipulation of ByteBuffer to directly map bytes to chars without any charset encoding - assumes 8-bit chars
	public static java.nio.ByteBuffer encode(CharSequence str, int off, int len, java.nio.ByteBuffer bybuf, boolean directbuf)
	{
		if (bybuf == null) {
			bybuf = create(len, directbuf);
		} else {
			bybuf.clear();
		}
		int lmt = off + len;

		if (bybuf.hasArray()) {
			// not sure if we gain much by this, especially as we still have to make a method call on str.charAt()
			byte[] arr = bybuf.array();
			int bidx = bybuf.arrayOffset();

			for (int idx = off; idx != lmt; idx++) {
				arr[bidx++] = (byte)str.charAt(idx);
			}
		} else {
			int bidx = 0;
			for (int idx = off; idx != lmt; idx++) {
				bybuf.put(bidx++, (byte)str.charAt(idx));
			}
		}
		//position is guaranteed to be zero at this stage, so set the limit to complete our work
		bybuf.limit(len);
		return bybuf;
	}

	// direct manipulation of ByteBuffer to load a byte array into it
	public static java.nio.ByteBuffer encode(byte[] buf, int off, int len, java.nio.ByteBuffer bybuf, boolean directbuf)
	{
		if (bybuf == null) {
			bybuf = create(len, directbuf);
		} else {
			bybuf.clear();
		}
		bybuf.put(buf, off, len);
		bybuf.position(0);
		bybuf.limit(len);
		return bybuf;
	}
	
	
	// assumes 8-bit chars
	public static StringBuilder decode(java.nio.ByteBuffer bybuf, int off, int limit, StringBuilder sb, boolean trimwhite)
	{
		int idx = off;
		if (limit == -1) limit = bybuf.limit();
		
		if (trimwhite) {
			while ((idx < limit) && (bybuf.get(idx) <= 32)) idx++;
			while ((idx < limit) && (bybuf.get(limit - 1) <= 32)) limit--;
		}
		if (sb == null) sb = new StringBuilder();
		while (idx < limit)sb.append((char)bybuf.get(idx++));
		return sb;
	}
	
	public static StringBuilder decode(java.nio.ByteBuffer bybuf, StringBuilder sb, java.nio.charset.CharsetDecoder chdec)
	{
		java.nio.CharBuffer chbuf = java.nio.CharBuffer.allocate(bybuf.remaining());  // number of decoded chars will never exceed number of bytes
		if (sb == null) sb = new StringBuilder();
		chbuf = decodeCB(bybuf, chbuf, chdec);
		sb.append(chbuf);
		return sb;
	}
	
	public static java.nio.CharBuffer decodeCB(java.nio.ByteBuffer bybuf, java.nio.CharBuffer chbuf, java.nio.charset.CharsetDecoder chdec)
	{
		java.nio.charset.CoderResult sts;
		chdec.reset();

		sts = chdec.decode(bybuf, chbuf, true);
		if (sts == java.nio.charset.CoderResult.UNDERFLOW) sts = chdec.flush(chbuf);
		if (sts != java.nio.charset.CoderResult.UNDERFLOW) throw new RuntimeException("Failed to byte-encode strlen=" + chbuf.length()
				+ " into bufsiz=" + bybuf.capacity() + " with " + chdec + " - " + sts);
		chbuf.flip();
		return chbuf;
	}
	
	
	public static java.nio.charset.CharsetEncoder getEncoder(String charset)
	{
		return java.nio.charset.Charset.forName(charset).newEncoder();
	}
	
	public static java.nio.charset.CharsetDecoder getDecoder(String charset)
	{
		return java.nio.charset.Charset.forName(charset).newDecoder();
	}
}
