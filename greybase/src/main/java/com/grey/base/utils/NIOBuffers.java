/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class NIOBuffers
{
	public static final class BufferFactory
		implements com.grey.base.collections.ObjectWell.ObjectFactory
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
		final int bufsiz = (int)(chenc.maxBytesPerChar() * chbuf.capacity()) + 1;
		bybuf = prepareBuffer(bufsiz, bybuf, directbuf);
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

	public static java.nio.ByteBuffer encode(CharSequence str, int off, int len, java.nio.ByteBuffer bybuf, boolean directbuf)
	{
		bybuf = prepareBuffer(len, bybuf, directbuf);
		encode(str, off, len, bybuf);
		return bybuf;
	}

	// direct manipulation of ByteBuffer to directly map bytes to chars without any charset encoding - assumes 8-bit chars
	public static void encode(CharSequence str, int off, int len, java.nio.ByteBuffer bybuf)
	{
		final int lmt = off + len;

		if (bybuf.hasArray()) {
			// not sure if we gain much by this, especially as we still have to make a method call on str.charAt()
			final byte[] arr = bybuf.array();
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
	}

	public static java.nio.ByteBuffer encode(byte[] databuf, int off, int len, java.nio.ByteBuffer bybuf, boolean directbuf)
	{
		bybuf = prepareBuffer(len, bybuf, directbuf);
		encode(databuf, off, len, bybuf);
		return bybuf;
	}

	public static java.nio.ByteBuffer encode(byte[] databuf, java.nio.ByteBuffer bybuf, boolean directbuf)
	{
		return encode(databuf, 0, databuf.length, bybuf, directbuf);
	}

	public static void encode(byte[] databuf, java.nio.ByteBuffer bybuf)
	{
		encode(databuf, 0, databuf.length, bybuf);
	}

	// direct manipulation of ByteBuffer to load a byte array into it
	public static void encode(byte[] databuf, int off, int len, java.nio.ByteBuffer bybuf)
	{
		bybuf.put(databuf, off, len);
		bybuf.position(0);
		bybuf.limit(len);
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
		final java.nio.CharBuffer chbuf = java.nio.CharBuffer.allocate(bybuf.remaining());  // number of decoded chars will never exceed number of bytes
		if (sb == null) sb = new StringBuilder();
		decodeCB(bybuf, chbuf, chdec);
		sb.append(chbuf);
		return sb;
	}
	
	public static java.nio.CharBuffer decodeCB(java.nio.ByteBuffer bybuf, java.nio.CharBuffer chbuf, java.nio.charset.CharsetDecoder chdec)
	{
		chdec.reset();
		java.nio.charset.CoderResult sts = chdec.decode(bybuf, chbuf, true);
		if (sts == java.nio.charset.CoderResult.UNDERFLOW) sts = chdec.flush(chbuf);
		if (sts != java.nio.charset.CoderResult.UNDERFLOW) throw new RuntimeException("Failed to byte-encode strlen=" + chbuf.length()
				+ " into bufsiz=" + bybuf.capacity() + " with " + chdec + " - " + sts);
		chbuf.flip();
		return chbuf;
	}

	/*
	 * srcbuf is in a non-flipped format, containing data in the range range position() to limit()
	 * On return, its position() is incremented by the number of bytes transferred.
	 * Same for dstbuf.
	 */
	public static int transfer(java.nio.ByteBuffer srcbuf, java.nio.ByteBuffer dstbuf, byte[] xferbuf)
	{
		final int off_src = srcbuf.position();
		final int off_dst = dstbuf.position();
		final int datalen = srcbuf.limit() - off_src;
		final int nbytes = Math.min(dstbuf.limit() - off_dst, datalen);

		if (srcbuf.hasArray()) {
			if (dstbuf.hasArray()) {
				System.arraycopy(srcbuf.array(), srcbuf.arrayOffset() + off_src, dstbuf.array(), dstbuf.arrayOffset() + off_dst, nbytes);
				dstbuf.position(off_dst + nbytes);
			} else {
				dstbuf.put(srcbuf.array(), srcbuf.arrayOffset() + off_src, nbytes);
			}
			srcbuf.position(off_src + nbytes);
		} else if (dstbuf.hasArray()) {
			srcbuf.get(dstbuf.array(), dstbuf.arrayOffset() + off_dst, nbytes);
			dstbuf.position(off_dst + nbytes);
		} else {
			// neither buffer exposes its backing array - need to copy via intermediate array, to avail of bulk get/put
			if (xferbuf == null || xferbuf.length < nbytes) return -nbytes;
			srcbuf.get(xferbuf, 0, nbytes);
			dstbuf.put(xferbuf, 0, nbytes);
		}
		return nbytes;
	}

	public static int transfer(java.nio.ByteBuffer srcbuf, java.nio.ByteBuffer dstbuf)
	{
		if (srcbuf.hasArray() || dstbuf.hasArray()) return transfer(srcbuf, dstbuf, null);
		final int nbytes = srcbuf.remaining();
		dstbuf.put(srcbuf);
		return nbytes;
	}

	public static java.nio.charset.CharsetEncoder getEncoder(String charset)
	{
		return java.nio.charset.Charset.forName(charset).newEncoder();
	}
	
	public static java.nio.charset.CharsetDecoder getDecoder(String charset)
	{
		return java.nio.charset.Charset.forName(charset).newDecoder();
	}

	private static java.nio.ByteBuffer prepareBuffer(int siz, java.nio.ByteBuffer buf, boolean directbuf)
	{
		if (buf == null || buf.capacity() < siz) {
			if (buf != null) directbuf = buf.isDirect();
			buf = create(siz, directbuf);
		} else {
			buf.clear();
		}
		return buf;
	}
}