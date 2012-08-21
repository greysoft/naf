/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

import com.grey.base.utils.ByteChars;

public class Base64Test
{
	@org.junit.Test
	public void miscellaneous()
	{
		org.junit.Assert.assertNull(Base64.encode(null));
		org.junit.Assert.assertNull(Base64.decode(null));
		org.junit.Assert.assertNull(Base64.encodeBytes(new byte[]{1,2}, 0, 0, 0, null));
		org.junit.Assert.assertNull(Base64.decodeBytes(new byte[]{1,2}, 0, 0, null));

		// verify Encode and Decode against the test vectors in section 10 of RFC-4648
		verifyBasic("f", "Zg==");
		verifyBasic("fo", "Zm8=");
		verifyBasic("foo", "Zm9v");
		verifyBasic("foob", "Zm9vYg==");
		verifyBasic("fooba", "Zm9vYmE=");
		verifyBasic("foobar", "Zm9vYmFy");

		// test all 3 padding and line-size scenarios
		verifyAdvanced("foobarlisticcalidiciousxy", 3, 0);     // default line-size, will not get trigerred
		verifyAdvanced("foobarlisticcalidiciousxy1", 3, -1);   // no linebreaks
		verifyAdvanced("foobarlisticcalidiciousxy12", 3, 10);  // small line-size, will get trigerred
	}

	// test alternative line endings with an encoded block large enough to contain any
	@org.junit.Test
	public void lineEndings()
	{
		StringBuilder sb = new StringBuilder();
		for (int loop = 0; loop != 50; loop++) sb.append("/Blah ").append(loop);
		final String str = sb.toString();
		// do standard test on large text
		final byte[] plaindata = str.getBytes();
		final char[] encdata = Base64.encode(plaindata);
		byte[] plaindata2 = Base64.decode(encdata);
		org.junit.Assert.assertArrayEquals(plaindata, plaindata2);
		verifyBasicBytes(plaindata, null);
		// now test the decode with alternative line endings
		String encstr = new String(encdata).replace("\r\n", "\n");
		final char[] encdata2 = encstr.toCharArray();
		org.junit.Assert.assertFalse(java.util.Arrays.equals(encdata, encdata2));  //verify we modified the line endings
		plaindata2 = Base64.decode(encdata2);
		org.junit.Assert.assertArrayEquals(plaindata, plaindata2);
		org.junit.Assert.assertEquals(str, new String(plaindata2));
	}

	static private void verifyBasic(final String origtxt, final String expbase64txt)
	{
		byte[] plaindata = origtxt.getBytes();
		char[] encdata = Base64.encode(plaindata);
		if (expbase64txt != null) {
			String encstr = new String(encdata);
			org.junit.Assert.assertEquals(expbase64txt, encstr);
		}

		byte[] plaindata2 = Base64.decode(encdata);
		String plaintxt2 = new String(plaindata2);
		org.junit.Assert.assertArrayEquals(plaindata, plaindata2);
		org.junit.Assert.assertEquals(origtxt, plaintxt2);

		String encspaces = "  "+new String(encdata)+" ";
		plaindata2 = Base64.decode(encspaces.toCharArray());
		org.junit.Assert.assertArrayEquals(plaindata, plaindata2);

		verifyBasicBytes(plaindata, expbase64txt);
	}

	static private void verifyBasicBytes(byte[] plaindata, String expbase64txt)
	{
		byte[] encdata = Base64.encodeBytes(plaindata, 0, plaindata.length, 0, null);
		String encstr = new String(encdata);
		if (expbase64txt != null) org.junit.Assert.assertEquals(expbase64txt, encstr);
		byte[] plaindata2 = Base64.decodeBytes(encdata, 0, encdata.length, null);
		org.junit.Assert.assertArrayEquals(plaindata, plaindata2);
	}

	// This tests complicated variants of the Base64 method, involving CharBlock and ByteChars, as well as line-break handling
	static private void verifyAdvanced(String origtxt, int origoff, int linesize)
	{
		final byte[] plaindata = origtxt.getBytes();
		int plainlen = plaindata.length - origoff;
		origtxt = new String(plaindata, origoff, plainlen);

		// set up buffer handles with non-zero offsets, excess space at end, and small initial lengths (which should get ignored)
		int encoff = 4;
		char[] encbuf = new char[(plainlen * 2) + encoff];
		com.grey.base.utils.CharBlock cb = new com.grey.base.utils.CharBlock(encbuf, encoff, 2, false);
		byte[] rawbuf = new byte[encbuf.length * 4];
		com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars(rawbuf, encoff + 2, 4, false);

		char[] encdata = Base64.encode(plaindata, origoff, plainlen, linesize, cb);
		byte[] plaindata2 = Base64.decode(encdata, cb.ar_off, cb.ar_len, bc);
		String dstr = new String(plaindata2, bc.ar_off, bc.ar_len);
		org.junit.Assert.assertEquals(origtxt, dstr);

		bc = new ByteChars(1);
		byte[] encbdata = Base64.encodeBytes(plaindata, 0, plaindata.length, linesize, bc);
		org.junit.Assert.assertSame(encbdata, bc.ar_buf);
		bc = new ByteChars(1);
		plaindata2 = Base64.decodeBytes(encbdata, 0, encbdata.length, bc);
		org.junit.Assert.assertSame(plaindata2, bc.ar_buf);
		org.junit.Assert.assertArrayEquals(plaindata, plaindata2);
	}
}
