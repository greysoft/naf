/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class NIOBuffersTest
{
	private static final String ORIGTXT1 = "  Hello NIO  ";
	private static final String ORIGTXT2 = "  And Again  ";

	@org.junit.Test
	public void factory()
	{
		int cap = 1024;
		NIOBuffers.BufferFactory fact = new NIOBuffers.BufferFactory(cap, false);
		java.nio.ByteBuffer buf1 = fact.factory_create();
		org.junit.Assert.assertFalse(buf1.isDirect());
		NIOBuffers.BufferFactory fact2 = new NIOBuffers.BufferFactory(cap, true);
		java.nio.ByteBuffer buf2 = fact2.factory_create();
		org.junit.Assert.assertTrue(buf2.isDirect());

		java.nio.ByteBuffer[] arr = new java.nio.ByteBuffer[]{buf1, buf2};
		for (int idx = 0; idx != arr.length; idx++) {
			org.junit.Assert.assertEquals(cap, arr[idx].capacity());
			org.junit.Assert.assertEquals(cap, arr[idx].limit());
			org.junit.Assert.assertEquals(0, arr[idx].position());
			org.junit.Assert.assertFalse(arr[idx].isReadOnly());
		}
	}

	@org.junit.Test
	public void encode8()
	{
		java.nio.ByteBuffer buf = NIOBuffers.encode(ORIGTXT1, null, null, null, null, true);
		verifyEncode(ORIGTXT1, buf);
		java.nio.ByteBuffer buf2 = NIOBuffers.encode(ORIGTXT2, null, null, null, buf, false);
		org.junit.Assert.assertSame(buf, buf2);
		verifyEncode(ORIGTXT2, buf);
		buf = NIOBuffers.encode(ORIGTXT1, null, null, null, null, false);
		verifyEncode(ORIGTXT1, buf);
		buf2 = NIOBuffers.encode(ORIGTXT2, null, null, null, buf, true);
		org.junit.Assert.assertSame(buf, buf2);
		verifyEncode(ORIGTXT2, buf);

		// the text is all 8-bit, so this should also work
		byte[] barr = new byte[ORIGTXT1.length()];
		for (int idx = 0; idx != ORIGTXT1.length(); idx++) barr[idx] = (byte)ORIGTXT1.charAt(idx);
		buf = NIOBuffers.encode(barr, 0, barr.length, null, true);
		org.junit.Assert.assertEquals(0, buf.position());
		org.junit.Assert.assertEquals(barr.length, buf.limit());
		org.junit.Assert.assertEquals(barr.length, buf.capacity());
		StringBuilder sb = NIOBuffers.decode(buf, 0, -1, null, false);
		org.junit.Assert.assertEquals(ORIGTXT1, sb.toString());
		// now repeat into existing buffer
		buf2 = NIOBuffers.encode(barr, 0, barr.length, buf, true);
		org.junit.Assert.assertSame(buf, buf2);
		org.junit.Assert.assertEquals(0, buf.position());
		org.junit.Assert.assertEquals(barr.length, buf.limit());
		org.junit.Assert.assertEquals(barr.length, buf.capacity());
		sb = NIOBuffers.decode(buf, 0, -1, null, false);
		org.junit.Assert.assertEquals(ORIGTXT1, sb.toString());
	}

	@org.junit.Test
	public void encodeCharset()
	{
		java.nio.ByteBuffer buf = NIOBuffers.encode(ORIGTXT1, null, null, "UTF-8", null, false);
		verifyEncode(ORIGTXT1, buf);
		java.nio.ByteBuffer buf2 = NIOBuffers.encode(ORIGTXT2, null, null, "UTF-8", buf, false);
		org.junit.Assert.assertSame(buf, buf2);
		verifyEncode(ORIGTXT2, buf);

		java.nio.charset.CharsetEncoder chenc = NIOBuffers.getEncoder("UTF-8");
		buf = NIOBuffers.encode(ORIGTXT1, chenc, null, null, null, true);
		verifyEncode(ORIGTXT1, buf);
	}

	@org.junit.Test
	public void transfer()
	{
		verifyTransfer(false, false);
		verifyTransfer(true, false);
		verifyTransfer(false, true);
		verifyTransfer(true, true);
	}

	private void verifyEncode(String txt, java.nio.ByteBuffer bybuf)
	{
		org.junit.Assert.assertEquals(0, bybuf.position());
		org.junit.Assert.assertEquals(txt.length(), bybuf.limit());
		java.nio.charset.CharsetDecoder chdec = NIOBuffers.getDecoder("UTF-8");

		StringBuilder sb = NIOBuffers.decode(bybuf, null, chdec);
		org.junit.Assert.assertEquals(txt.length(), bybuf.position());
		org.junit.Assert.assertEquals(txt.length(), bybuf.limit());
		bybuf.flip();
		org.junit.Assert.assertEquals(txt, sb.toString());
		org.junit.Assert.assertEquals(0, bybuf.position());
		org.junit.Assert.assertEquals(txt.length(), bybuf.limit());

		StringBuilder sb2 = NIOBuffers.decode(bybuf, 0, -1, sb, false);
		org.junit.Assert.assertSame(sb, sb2);
		org.junit.Assert.assertEquals(txt+txt, sb.toString());
		org.junit.Assert.assertEquals(0, bybuf.position());
		org.junit.Assert.assertEquals(txt.length(), bybuf.limit());
		
		sb = NIOBuffers.decode(bybuf, 0, -1, null, true);
		org.junit.Assert.assertEquals(txt.trim(), sb.toString());
		org.junit.Assert.assertEquals(0, bybuf.position());
		org.junit.Assert.assertEquals(txt.length(), bybuf.limit());
	}

	private void verifyTransfer(boolean srcdirect, boolean dstdirect)
	{
		String txt1 = "123";
		String txt2 = "abcde";
		String srctxt = txt1+txt2;
		byte[] srcdata = srctxt.getBytes();
		org.junit.Assert.assertEquals(srctxt.length(), srcdata.length); //sanity check
		java.nio.ByteBuffer srcbuf = NIOBuffers.create(srcdata.length, srcdirect);
		java.nio.ByteBuffer dstbuf = NIOBuffers.create(srcbuf.capacity(), dstdirect);
		org.junit.Assert.assertEquals(0, srcbuf.position());
		org.junit.Assert.assertEquals(srcdata.length, srcbuf.capacity());
		org.junit.Assert.assertEquals(srcbuf.limit(), srcbuf.capacity());

		srcbuf.put(srcdata);
		org.junit.Assert.assertEquals(srcdata.length, srcbuf.position());
		org.junit.Assert.assertEquals(srcdata.length, srcbuf.capacity());
		org.junit.Assert.assertEquals(srcbuf.limit(), srcbuf.capacity());
		srcbuf.flip();
		org.junit.Assert.assertEquals(0, srcbuf.position());
		org.junit.Assert.assertEquals(srcbuf.limit(), srcbuf.capacity());
		srcbuf.position(txt1.length());
		org.junit.Assert.assertEquals(txt1.length(), srcbuf.position());
		org.junit.Assert.assertEquals(srcbuf.limit(), srcbuf.capacity());

		dstbuf.put((byte)'x');
		dstbuf.mark();
		dstbuf.put((byte)'x');
		dstbuf.reset();

		int nbytes = NIOBuffers.transfer(srcbuf, dstbuf, null);
		if (srcdirect && dstdirect) {
			org.junit.Assert.assertEquals(-txt2.length(), nbytes);
			nbytes = NIOBuffers.transfer(srcbuf, dstbuf, new byte[-nbytes]);
		}
		org.junit.Assert.assertEquals(txt2.length(), nbytes);
		org.junit.Assert.assertEquals(txt1.length()+nbytes, srcbuf.position());
		org.junit.Assert.assertEquals(srcbuf.limit(), srcbuf.capacity());
		org.junit.Assert.assertEquals(nbytes+1, dstbuf.position());
		org.junit.Assert.assertEquals(dstbuf.limit(), dstbuf.capacity());
		dstbuf.flip();
		org.junit.Assert.assertEquals((byte)'x', dstbuf.get());
		for (int idx = 0; idx != txt2.length(); idx++) {
			org.junit.Assert.assertEquals((byte)txt2.charAt(idx), dstbuf.get());
		}

		if (srcdirect && dstdirect) {
			// do an additional test of the no-transfer-buf method, with buffers that normally need a transfer-buf
			srcbuf.clear();
			srcbuf.put(srcdata);
			srcbuf.flip();
			srcbuf.position(txt1.length());
			dstbuf.clear();
			dstbuf.put((byte)'x');
			dstbuf.mark();
			dstbuf.put((byte)'x');
			dstbuf.reset();
			nbytes = NIOBuffers.transfer(srcbuf, dstbuf);
			org.junit.Assert.assertEquals(txt2.length(), nbytes);
			org.junit.Assert.assertEquals(txt1.length()+nbytes, srcbuf.position());
			org.junit.Assert.assertEquals(srcbuf.limit(), srcbuf.capacity());
			org.junit.Assert.assertEquals(nbytes+1, dstbuf.position());
			org.junit.Assert.assertEquals(dstbuf.limit(), dstbuf.capacity());
			dstbuf.flip();
			org.junit.Assert.assertEquals((byte)'x', dstbuf.get());
			for (int idx = 0; idx != txt2.length(); idx++) {
				org.junit.Assert.assertEquals((byte)txt2.charAt(idx), dstbuf.get());
			}
		} else if (!srcdirect && !dstdirect) {
			// do an additional test of the no-transfer-buf method, with buffers that don't need transfer-buf
			srcbuf.clear();
			srcbuf.put(srcdata);
			srcbuf.flip();
			srcbuf.position(txt1.length());
			dstbuf.clear();
			int dstlmt = txt2.length() - 1;
			dstbuf.limit(dstlmt);
			nbytes = NIOBuffers.transfer(srcbuf, dstbuf);
			org.junit.Assert.assertEquals(dstlmt, nbytes);
			org.junit.Assert.assertEquals(txt1.length()+nbytes, srcbuf.position());
			org.junit.Assert.assertEquals(srcbuf.limit(), srcbuf.capacity());
			org.junit.Assert.assertEquals(nbytes, dstbuf.position());
			org.junit.Assert.assertEquals(dstlmt, dstbuf.limit());
			dstbuf.flip();
			for (int idx = 0; idx != txt2.length()-1; idx++) {
				org.junit.Assert.assertEquals((byte)txt2.charAt(idx), dstbuf.get());
			}
		}
	}
}