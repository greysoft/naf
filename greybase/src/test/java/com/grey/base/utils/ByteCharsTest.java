/*
 * Copyright 2011-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class ByteCharsTest
{
	@org.junit.Test
	public void testConstructors() throws Exception
	{
		byte[] src_arr = new byte[]{11, 12, 13, 14, 15};
		String src_str = "ABCDE";
		int off = 1;
		int len = 2;
		int cap = 10;

		ByteChars ah = new ByteChars(-1);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);
		ah = new ByteChars(cap);
		verify(ah, 0, 0, cap);
		ah = new ByteChars();
		org.junit.Assert.assertNotNull(ah.ar_buf);
		org.junit.Assert.assertEquals(0, ah.ar_off);
		org.junit.Assert.assertEquals(0, ah.ar_len);

		ah = new ByteChars(src_arr, off, len, false);
		verify(ah, off, len, src_arr.length - off);
		org.junit.Assert.assertTrue(src_arr == ah.ar_buf);
		org.junit.Assert.assertEquals(src_arr[off], ah.byteAt(0));
		org.junit.Assert.assertEquals(src_arr[off+1], ah.byteAt(1));
		ah = new ByteChars(src_arr, off, len, true);
		verify(ah, 0, len, len);
		org.junit.Assert.assertFalse(src_arr == ah.ar_buf);
		org.junit.Assert.assertEquals(src_arr[off], ah.byteAt(0));
		org.junit.Assert.assertEquals(src_arr[off+1], ah.byteAt(1));
		ah = new ByteChars(src_arr);
		off = 0; len = src_arr.length;
		verify(ah, off, len, len);
		org.junit.Assert.assertTrue(src_arr == ah.ar_buf);
		org.junit.Assert.assertEquals(src_arr[off], ah.byteAt(0));
		org.junit.Assert.assertEquals(src_arr[len - 1], ah.byteAt(len - 1));

		ah = new ByteChars(src_str);
		verify(ah, 0, src_str.length(), src_str.length());
		org.junit.Assert.assertEquals(src_str.charAt(0), ah.byteAt(0));
		org.junit.Assert.assertEquals(src_str.charAt(src_str.length()-1), ah.byteAt(src_str.length()-1));
		off = 1; len = src_str.length() - 2;
		ah = new ByteChars(src_str, off, len);
		verify(ah, 0, len, len);
		org.junit.Assert.assertEquals(src_str.charAt(1), ah.byteAt(0));
		org.junit.Assert.assertEquals(src_str.charAt(len), ah.byteAt(len-1));

		ByteChars src_ah = new ByteChars(src_str);
		ah = new ByteChars(src_ah, false);
		org.junit.Assert.assertTrue(src_ah.ar_buf == ah.ar_buf);
		ah = new ByteChars(src_ah, off, len, true);
		verify(ah, 0, len, len);
		org.junit.Assert.assertFalse(src_ah.ar_buf == ah.ar_buf);
		org.junit.Assert.assertEquals(src_ah.byteAt(1), ah.byteAt(0));
		org.junit.Assert.assertEquals(src_ah.byteAt(len), ah.byteAt(len-1));

		// This constructor call doesn't compile! Just pass in ArrayRef<byte[]> via the byte[] constructors
		ArrayRef<byte[]> ahbyte = new ArrayRef<byte[]>(src_arr);
		ah = new ByteChars(ahbyte);
		org.junit.Assert.assertTrue(src_arr == ahbyte.ar_buf);
		org.junit.Assert.assertTrue(ah.ar_buf == ahbyte.ar_buf);
		org.junit.Assert.assertEquals(src_arr.length, ah.length());
		org.junit.Assert.assertEquals(src_arr[0], ah.byteAt(0));
		org.junit.Assert.assertEquals(src_arr[src_arr.length-1], ah.byteAt(src_arr.length-1));

		//not actually constructors, but test these here
		ah = new ByteChars(-1);
		byte[] ahbuf1 = ah.ar_buf;
		byte[] b = new byte[4];
		ah.pointAt(b);
		verify(ah, 0, b.length, b.length);
		org.junit.Assert.assertNotSame(ahbuf1, ah.ar_buf);
		ah.pointAt((byte[])null);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);

		java.security.MessageDigest hashproc = java.security.MessageDigest.getInstance("MD5");
		ah = new ByteChars("1");
		com.grey.base.utils.ByteChars digest = ah.digest(hashproc);
		org.junit.Assert.assertNotSame(digest, ah);
		org.junit.Assert.assertEquals(1, ah.length());
		org.junit.Assert.assertEquals('1', ah.byteAt(0));
	}

	@org.junit.Test
	public void testCopy()
	{
		byte[] barr = new byte[]{11, 12, 13, 14, 15};
		ByteChars src = new ByteChars(barr, 1, 3, false);

		ByteChars dst = src.copy(null);
		org.junit.Assert.assertFalse(src == dst);
		org.junit.Assert.assertNotSame(src.ar_buf, dst.ar_buf);
		org.junit.Assert.assertEquals(0, dst.ar_off);
		org.junit.Assert.assertEquals(src.length(), dst.length());
		org.junit.Assert.assertEquals(src.hashCode(), dst.hashCode());
		org.junit.Assert.assertTrue(src.equals(dst));

		int off = 1; int len = 2;
		dst = src.copy(off, len, null);
		org.junit.Assert.assertFalse(src == dst);
		org.junit.Assert.assertNotSame(src.ar_buf, dst.ar_buf);
		org.junit.Assert.assertEquals(0, dst.ar_off);
		org.junit.Assert.assertEquals(len, dst.length());

		off = 2;
		src = new ByteChars(barr);
		dst = new ByteChars(src.length()+off);
		dst.ar_off = off;
		byte[] oldbuf = dst.ar_buf;
		ByteChars newbc = src.copy(dst);
		org.junit.Assert.assertTrue(newbc == dst);
		org.junit.Assert.assertTrue(oldbuf == dst.ar_buf);
		org.junit.Assert.assertEquals(off, dst.ar_off);
		org.junit.Assert.assertTrue(src.length() == dst.length());
		org.junit.Assert.assertTrue(src.hashCode() == dst.hashCode());
		org.junit.Assert.assertTrue(src.equals(dst));

		dst = new ByteChars(src.length() - 1);
		oldbuf = dst.ar_buf;
		newbc = src.copy(dst);
		org.junit.Assert.assertTrue(newbc == dst);
		org.junit.Assert.assertFalse(oldbuf == dst.ar_buf);
		org.junit.Assert.assertEquals(0, dst.ar_off);
		org.junit.Assert.assertEquals(src.length(), dst.length());
		org.junit.Assert.assertTrue(src.hashCode() == dst.hashCode());
		org.junit.Assert.assertTrue(src.equals(dst));

		off = 2;
		dst = new ByteChars(src.length() - 1);
		dst.ar_off = 1;
		oldbuf = dst.ar_buf;
		newbc = src.copy(off, src.length() - off, dst);
		org.junit.Assert.assertTrue(newbc == dst);
		org.junit.Assert.assertTrue(oldbuf == dst.ar_buf);
		org.junit.Assert.assertEquals(1, dst.ar_off);
		org.junit.Assert.assertEquals(src.length() - off, dst.length());
		org.junit.Assert.assertFalse(src.hashCode() == dst.hashCode());
		org.junit.Assert.assertFalse(src.equals(dst));
	}

	// Note that String appends are exercised by the Constructors test, and and StringBuilder appends by the Numbers test
	@org.junit.Test
	public void testAppend()
	{
		// append another ByteChars twice, such that 2nd append will trigger a Grow
		String val = "abcd";
		int cap = val.length() + (val.length() / 2);
		ByteChars ah1 = new ByteChars(cap);
		verify(ah1, 0, 0, cap);
		ByteChars ah2 = new ByteChars(val);
		verify(ah2, 0, val.length(), val.length());
		byte[] barr = ah1.ar_buf;
		ah1.append(ah2);
		verify(ah1, 0, ah2.size(), cap);
		org.junit.Assert.assertTrue(barr == ah1.ar_buf);
		ah1.append(ah2);
		org.junit.Assert.assertEquals(ah1.size(), 2*ah2.size());
		org.junit.Assert.assertFalse(barr == ah1.ar_buf);

		// loop up to the point at which we would trigger a grow() and then do the decisive append after the loop
		cap = 4;
		ah1 = new ByteChars(cap);
		barr = ah1.ar_buf;
		for (int idx = 0; idx != cap; idx++)
		{
			ah1.append((byte)255);
			org.junit.Assert.assertEquals(ah1.size(), idx+1);
			org.junit.Assert.assertTrue(barr == ah1.ar_buf);
		}
		ah1.append((byte)255);
		org.junit.Assert.assertEquals(ah1.size(), cap+1);
		org.junit.Assert.assertFalse(barr == ah1.ar_buf);

		// append a CharSequence other than String, StringBuilder or ourselves
		String str = "abc";
		ah1 = new ByteChars(new StringBuffer(str));
		ah1.append(str);
		org.junit.Assert.assertEquals(str.length() * 2, ah1.size());
		org.junit.Assert.assertEquals(str+str, ah1.toString());

		// make sure null/blank appends do nothing
		str = ah1.toString();
		int siz = ah1.size();
		int off = ah1.ar_off;
		ah1.append((byte[])null);
		org.junit.Assert.assertTrue(ah1.size() == siz && ah1.ar_off == off && str.equals(ah1.toString()));
		ah1.append((char[])null);
		org.junit.Assert.assertTrue(ah1.size() == siz && ah1.ar_off == off && str.equals(ah1.toString()));
		ah1.append((CharSequence)null);
		org.junit.Assert.assertTrue(ah1.size() == siz && ah1.ar_off == off && str.equals(ah1.toString()));
		ah1.append("");
		org.junit.Assert.assertTrue(ah1.size() == siz && ah1.ar_off == off && str.equals(ah1.toString()));
		ah1.append("x");
		org.junit.Assert.assertFalse(ah1.size() == siz || str.equals(ah1.toString()));

		// make sure that set() clears existing values
		ah1.ar_off++;
		ah1.set("abcdefghijkl");
		ah1.set("xyz");
		org.junit.Assert.assertEquals(3, ah1.size());
		org.junit.Assert.assertEquals("xyz", ah1.toString());
		ah1.set("xyz", 2, 1);
		org.junit.Assert.assertEquals(1, ah1.size());
		org.junit.Assert.assertEquals('z', ah1.byteAt(0));
		ah1.set((CharSequence)null);
		org.junit.Assert.assertEquals(0, ah1.size());
		ah1.set("xyz");
		org.junit.Assert.assertEquals(3, ah1.size());
		ah1.set((byte[])null);
		org.junit.Assert.assertEquals(0, ah1.size());

		barr = new byte[]{1,2};
		byte[] barr2 = new byte[]{3,4};
		ah1.set("abcdefghijkl");
		ah1.set(barr);
		org.junit.Assert.assertEquals(barr.length, ah1.size());
		org.junit.Assert.assertEquals(barr[0], ah1.byteAt(0));
		org.junit.Assert.assertEquals(barr[1], ah1.byteAt(1));
		ah1.append(barr2);
		org.junit.Assert.assertEquals(barr.length+barr2.length, ah1.size());
		org.junit.Assert.assertEquals(barr[0], ah1.byteAt(0));
		org.junit.Assert.assertEquals(barr[1], ah1.byteAt(1));
		org.junit.Assert.assertEquals(barr2[0], ah1.byteAt(2));
		org.junit.Assert.assertEquals(barr2[1], ah1.byteAt(3));

		char[] carr = "123".toCharArray();
		ah1.set("a");
		ah1.append(carr);
		org.junit.Assert.assertEquals(carr.length+1, ah1.size());
		org.junit.Assert.assertEquals("a"+new String(carr), ah1.toString());
		int prevlen = ah1.size();
		carr = new char[0];
		ah1.append(carr);
		org.junit.Assert.assertEquals(prevlen, ah1.size());
	}

	// Note that in general, we can't guarantee that hashcodes will ever be unequal, but we can guarantee when they are equal.
	@org.junit.Test
	public void testEquals()
	{
		String str = "Test Me";

		// test pointers to same content in different memory buffers
		ByteChars ah1 = new ByteChars(str);
		ByteChars ah2 = new ByteChars(str);
		ByteChars ah3 = new ByteChars();
		org.junit.Assert.assertFalse(ah1.ar_buf == ah2.ar_buf);
		org.junit.Assert.assertTrue(ah1.equals(ah2));
		org.junit.Assert.assertTrue(ah1.hashCode() == ah2.hashCode());
		org.junit.Assert.assertFalse(ah1.equals(str));

		org.junit.Assert.assertTrue(ah1.equalsBytes(ah2.toByteArray()));
		org.junit.Assert.assertFalse(ah1.equals(ah2.toByteArray()));
		org.junit.Assert.assertFalse(ah1.equalsBytes(ah2.toByteArray(), 0, ah1.ar_len-1));
		org.junit.Assert.assertTrue(ah1.equalsBytes(ByteOps.getBytes8(str)));
		org.junit.Assert.assertFalse(ah1.equalsBytes(ByteOps.getBytes8("Test Mx"))); //same length
		org.junit.Assert.assertFalse(ah1.equalsBytes(null));

		org.junit.Assert.assertTrue(ah1.equalsChars(str.toCharArray()));
		org.junit.Assert.assertFalse(ah1.equals(str.toCharArray()));
		org.junit.Assert.assertFalse(ah1.equalsChars("Test Mx".toCharArray())); //same length
		org.junit.Assert.assertFalse(ah1.equalsChars(ah2.toCharArray(), 0, ah1.ar_len-1));
		org.junit.Assert.assertFalse(ah1.equalsChars(null));

		org.junit.Assert.assertTrue(ah1.equalsIgnoreCase(str.toLowerCase()));
		org.junit.Assert.assertTrue(ah1.equalsIgnoreCase(str.toUpperCase()));
		org.junit.Assert.assertTrue(ah1.equalsIgnoreCase(str));
		org.junit.Assert.assertFalse(ah1.equalsIgnoreCase("Test Mx")); //same length
		org.junit.Assert.assertFalse(ah1.equalsIgnoreCase(str+"x"));
		org.junit.Assert.assertFalse(ah1.equalsIgnoreCase(""));
		org.junit.Assert.assertTrue(ah3.equalsIgnoreCase(""));
		org.junit.Assert.assertFalse(ah1.equalsIgnoreCase(null));

		ah2.ar_buf[ah2.ar_off + ah2.ar_len - 1]++;
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// miscellaneous
		org.junit.Assert.assertTrue(ah1.equals(ah1));
		org.junit.Assert.assertFalse(ah1.equals(null));

		ah1.setByte(0, (byte)0);
		org.junit.Assert.assertEquals(0, ah1.byteAt(0));
		org.junit.Assert.assertEquals(0, ah1.charAt(0));
		ah1.setByte(0, (byte)'A');
		org.junit.Assert.assertEquals('A', ah1.byteAt(0));
		org.junit.Assert.assertEquals('A', ah1.charAt(0));
		ah1.setByte(0, (byte)250);
		org.junit.Assert.assertEquals(250, ah1.byteAt(0));
		org.junit.Assert.assertEquals(250, ah1.charAt(0));
		ah1.setByte(0, (byte)255);
		org.junit.Assert.assertEquals(255, ah1.byteAt(0));
		org.junit.Assert.assertEquals(255, ah1.charAt(0));

		ByteChars bc1 = new ByteChars("HELLO");
		ByteChars bc2 = new ByteChars("hello");
		org.junit.Assert.assertFalse(bc1.equals(bc2));
		org.junit.Assert.assertTrue(bc1.equalsIgnoreCase(bc2));
		org.junit.Assert.assertTrue(bc2.equalsIgnoreCase(bc1));
		org.junit.Assert.assertNotEquals("hello", bc1.toString());
		ByteChars bc = bc1.toLowerCase();
		org.junit.Assert.assertTrue(bc == bc1);
		org.junit.Assert.assertEquals("hello", bc1.toString());
		org.junit.Assert.assertTrue(bc1.equals(bc2));
		bc = bc2.toUpperCase();
		org.junit.Assert.assertTrue(bc == bc2);
		org.junit.Assert.assertEquals("HELLO", bc2.toString());
		org.junit.Assert.assertFalse(bc1.equals(bc2));

		ArrayRef<byte[]> aref = new ArrayRef<byte[]>(bc1.ar_buf, bc1.ar_off, bc1.ar_len, false);
		org.junit.Assert.assertFalse(aref.equals(bc1));
		org.junit.Assert.assertFalse(bc1.equals(aref));
		aref = bc1;
		org.junit.Assert.assertTrue(aref.equals(bc1));
		org.junit.Assert.assertTrue(bc1.equals(aref));
	}

	@org.junit.Test
	public void testCompare()
	{
		ByteChars ah1 = new ByteChars("abc");
		ByteChars ah2 = new ByteChars("abd");
		org.junit.Assert.assertTrue(ah1.compareTo(ah1) == 0);
		org.junit.Assert.assertTrue(ah1.compareTo(ah2) < 0);
		org.junit.Assert.assertTrue(ah2.compareTo(ah1) > 0);
		ah2 = new ByteChars("abc");
		org.junit.Assert.assertTrue(ah1.compareTo(ah2) == 0);
		ah2 = new ByteChars("abcd");
		org.junit.Assert.assertTrue(ah1.compareTo(ah2) < 0);
	}

	@org.junit.Test
	public void testIndex()
	{
		ByteChars ah = new ByteChars();
		ah.append((byte)1).append((byte)2).append((byte)3).append((byte)4).append((byte)2).append((byte)9);
		org.junit.Assert.assertEquals(6, ah.size());
		int off = ah.indexOf((byte)99);
		org.junit.Assert.assertEquals(-1, off);
		off = ah.indexOf((byte)1);
		org.junit.Assert.assertEquals(0, off);
		off = ah.indexOf((byte)2);
		org.junit.Assert.assertEquals(1, off);
		off = ah.indexOf((byte)9);
		org.junit.Assert.assertEquals(ah.size()-1, off);
		off = ah.indexOf(1, (byte)2);
		org.junit.Assert.assertEquals(1, off);
		off = ah.indexOf(2, (byte)2);
		org.junit.Assert.assertEquals(ah.size()-2, off);
		// reverse direction
		off = ah.lastIndexOf((byte)2);
		org.junit.Assert.assertEquals(ah.size()-2, off);
		off = ah.lastIndexOf(off, (byte)2);
		org.junit.Assert.assertEquals(ah.size()-2, off);
		off = ah.lastIndexOf(off-1, (byte)2);
		org.junit.Assert.assertEquals(1, off);
		off = ah.lastIndexOf((byte)99);
		org.junit.Assert.assertEquals(-1, off);

		// now look for a string
		String str = "axabxwabc";
		int correct = str.indexOf("abc");
		org.junit.Assert.assertFalse(correct == 0 || correct == -1); // to make sure we didn't mistype anything above
		ah = new ByteChars(str);
		off = ah.indexOf("abc");
		org.junit.Assert.assertEquals(correct, off);
		off = ah.indexOf(2, "abc");
		org.junit.Assert.assertEquals(correct, off);
		off = ah.indexOf("badpattern");
		org.junit.Assert.assertEquals(-1, off);
		off = ah.indexOf((CharSequence)null);
		org.junit.Assert.assertEquals(-1, off);

		// test some initial limits
		off = ah.indexOf(ah.size()-1, "badpattern");
		org.junit.Assert.assertEquals(-1, off);
		off = ah.indexOf(ah.size(), "badpattern");
		org.junit.Assert.assertEquals(-1, off);

		// test what happens if we encounter end-of-ByteChars in mid-pattern
		str = "axabxwab";
		ah = new ByteChars(str);
		off = ah.indexOf("abc");
		org.junit.Assert.assertEquals(-1, off);

		// test the byte[] scanners
		ah = new ByteChars("x12345");
		ah.advance(1);
		byte[] seq = ah.toByteArray();
		off = ah.indexOf(seq);
		org.junit.Assert.assertEquals(0, off);
		seq = ah.toByteArray(1, 3);
		off = ah.indexOf(seq);
		org.junit.Assert.assertEquals(1, off);
		off = ah.indexOf(1, seq, 0, seq.length);
		org.junit.Assert.assertEquals(1, off);
		off = ah.indexOf(2, seq, 0, seq.length);
		org.junit.Assert.assertEquals(-1, off);
		seq[2] = 'x';
		off = ah.indexOf(seq);
		org.junit.Assert.assertEquals(-1, off);
		off = ah.indexOf((byte[])null);
		org.junit.Assert.assertEquals(-1, off);
		off = ah.indexOf(new byte[0]);
		org.junit.Assert.assertEquals(-1, off);

		ah = new ByteChars("x12x2xx5x");
		int cnt = ah.count('x');
		org.junit.Assert.assertEquals(5, cnt);
		cnt = ah.count(1, 'x');
		org.junit.Assert.assertEquals(4, cnt);
		seq = ByteOps.getBytes8("2x");
		cnt = ah.count(seq);
		org.junit.Assert.assertEquals(2, cnt);
		org.junit.Assert.assertEquals(0, ah.count(null));

		//test the convenience methods
		String pfx = "123";
		String mainpart = "abc xyz";
		ah = new ByteChars(pfx+mainpart);
		ByteChars bclight = new ByteChars(-1);
		bclight.pointAt(ah.ar_buf, ah.ar_off+pfx.length(), ah.ar_len - pfx.length());
		org.junit.Assert.assertTrue(bclight.startsWith(mainpart.subSequence(0, 3)));
		org.junit.Assert.assertTrue(bclight.startsWith(mainpart));
		org.junit.Assert.assertFalse(bclight.startsWith(mainpart.subSequence(0, 3)+"9"));
		org.junit.Assert.assertFalse(bclight.startsWith("abc xy9"));
		org.junit.Assert.assertFalse(bclight.startsWith(mainpart+"9"));
		org.junit.Assert.assertTrue(bclight.startsWith("")); //String behaves this way
		org.junit.Assert.assertFalse(bclight.startsWith(null)); //String throws NPE
		org.junit.Assert.assertTrue(bclight.endsWith("xyz"));
		org.junit.Assert.assertTrue(bclight.endsWith(mainpart));
		org.junit.Assert.assertFalse(bclight.endsWith("xyz9"));
		org.junit.Assert.assertFalse(bclight.endsWith("abc xy9"));
		org.junit.Assert.assertFalse(bclight.endsWith(mainpart+"9"));
		org.junit.Assert.assertTrue(bclight.endsWith("")); //String behaves this way
		org.junit.Assert.assertFalse(bclight.endsWith(null)); //String throws NPE
	}

	@org.junit.Test
	public void testNumbers()
	{
		long numval = 37;
		StringBuilder strbuf = new StringBuilder();
		ByteChars ah = new ByteChars(64);

		// positive decimal
		ah.append(numval, strbuf);
		int numval2 = (int)ah.parseDecimal();
		org.junit.Assert.assertEquals(numval, numval2);
		ah.set("+"+numval);
		numval2 = (int)ah.parseDecimal();
		org.junit.Assert.assertEquals(numval, numval2);
		// negative
		ah.set("-"+numval);
		numval2 = (int)ah.parseDecimal();
		org.junit.Assert.assertEquals(-numval, numval2);

		// with surrounding text
		ah.set("blah");
		int off = ah.size();
		ah.append(numval, strbuf);
		int off2 = ah.size();
		ah.append("blah");
		numval2 = (int)ah.parseDecimal(off, off2 - off);
		org.junit.Assert.assertEquals(numval, numval2);
		// negative decimal
		ah.truncateTo(off);
		ah.append(-1 * numval, strbuf);
		off2 = ah.size();
		numval2 = (int)ah.parseDecimal(off, off2 - off);
		org.junit.Assert.assertEquals('-', ah.ar_buf[off]);
		org.junit.Assert.assertEquals(-numval, numval2);

		// hex number
		ah.set(Long.toHexString(numval));
		numval2 = (int)ah.parseHexadecimal();
		org.junit.Assert.assertEquals(numval, numval2);
		// with surrounding text
		ah.set("blah");
		off = ah.size();
		ah.append(Long.toHexString(numval));
		off2 = ah.size();
		numval2 = (int)ah.parseHexadecimal(off, off2 - off);
		org.junit.Assert.assertEquals(numval, numval2);
		//negative
		ah.truncateTo(off);
		ah.append('-');
		ah.append(Long.toHexString(numval));
		off2 = ah.size();
		numval2 = (int)ah.parseHexadecimal(off, off2 - off);
		org.junit.Assert.assertEquals(-numval, numval2);
		ah.set("23blah");
		try {
			numval2 = (int)ah.parseHexadecimal();
			org.junit.Assert.fail("parseHexadecimal failed to reject invalid number: "+ah+" - returned "+numval2);
		} catch (NumberFormatException ex) {} //expected error - gets thrown for any invalid char
		ah.set("-");
		numval2 = (int)ah.parseDecimal();
		org.junit.Assert.assertEquals(0, numval2);
		ah.set("+");
		numval2 = (int)ah.parseDecimal();
		org.junit.Assert.assertEquals(0, numval2);
		ah.ar_len = 0;
		numval2 = (int)ah.parseDecimal();
		org.junit.Assert.assertEquals(0, numval2);
	}

	@org.junit.Test
	public void testSequences()
	{
		String src = "ABCDE";
		CharSequence src_seq1 = src.subSequence(0, src.length());
		CharSequence src_seq2 = src.subSequence(1, src.length() - 1);
		org.junit.Assert.assertEquals(src, src_seq1.toString());
		org.junit.Assert.assertEquals(src.subSequence(1, src.length()-1), src_seq2.toString());
		org.junit.Assert.assertEquals(src_seq1.length(), src_seq2.length() + 2);
		ByteChars ah = new ByteChars(src);

		CharSequence seq = ah.subSequence(0, ah.length());
		org.junit.Assert.assertEquals(seq.toString(), src_seq1);
		seq = ah.subSequence(1, ah.length() - 1);
		org.junit.Assert.assertEquals(seq.toString(), src_seq2);
		if (seq instanceof ByteChars) org.junit.Assert.assertFalse(((ByteChars)seq).ar_buf == ah.ar_buf);

		ah.advance(1);
		org.junit.Assert.assertEquals(src.length() - 1, ah.length());
		char[] carr = ah.toCharArray();
		org.junit.Assert.assertEquals(ah.length(), carr.length);
		org.junit.Assert.assertEquals(ah.charAt(0), carr[0]);
		org.junit.Assert.assertEquals(ah.charAt(ah.length() - 1), carr[ah.length() - 1]);
		char[] carr2 = ah.toCharArray(0, ah.size(), carr);
		org.junit.Assert.assertTrue(carr2 == carr);
		char[] carr3 = new char[ah.size() - 1];
		carr2 = ah.toCharArray(0, ah.size(), carr3);
		org.junit.Assert.assertFalse(carr2 == carr3);

		byte[] barr = ah.toByteArray();
		org.junit.Assert.assertEquals(ah.length(), barr.length);
		org.junit.Assert.assertEquals(ah.charAt(0), barr[0]);
		org.junit.Assert.assertEquals(ah.charAt(ah.length() - 1), barr[ah.length() - 1]);

		ah = ByteChars.convertCharSequence(src, null);
		org.junit.Assert.assertEquals(ah.toString(), src);
		ByteChars ah2 = ByteChars.convertCharSequence(null, ah);
		org.junit.Assert.assertNull(ah2);
		ByteChars ah3 = new ByteChars();
		ah2 = ByteChars.convertCharSequence(ah, ah3);
		org.junit.Assert.assertTrue(ah2 == ah);
		ah2 = ByteChars.convertCharSequence(src, ah3);
		org.junit.Assert.assertTrue(ah2 == ah3);

		ah = new ByteChars();
		String str = ah.toString();
		org.junit.Assert.assertNotNull(str);
		org.junit.Assert.assertEquals(0, str.length());
	}

	@org.junit.Test
	public void testExtract()
	{
		String str = "frag1:fragment2:frag3";
		ByteChars ah = new ByteChars(str);
		byte dlm = (byte)':';
		ByteChars ptr = ah.extractTerm(dlm, 0, 0, false, null);
		org.junit.Assert.assertEquals("frag1", ptr.toString());
		ByteChars ptr2 = ah.extractTerm(dlm, 0, 1, false, ptr);
		org.junit.Assert.assertTrue(ptr2 == ptr);
		org.junit.Assert.assertEquals("fragment2", ptr.toString());
		ptr2 = ah.extractTerm(dlm, 0, 2, false, ptr);
		org.junit.Assert.assertEquals("frag3", ptr2.toString());

		ptr2 = ah.extractTerm(dlm, 0, 3, false, ptr);
		org.junit.Assert.assertNull(ptr2);
		ptr2 = ah.extractTerm((byte)'X', 0, 0, false, ptr);
		org.junit.Assert.assertEquals(str, ptr2.toString());

		ptr2 = ah.extractTerm(dlm, 0, 1, true, ptr);
		org.junit.Assert.assertEquals("fragment2:frag3", ptr2.toString());

		str = "frag1:frag2:";
		ah = new ByteChars(str);
		ptr2 = ah.extractTerm(dlm, 0, 0, false, ptr);
		org.junit.Assert.assertEquals("frag1", ptr2.toString());
		ptr2 = ah.extractTerm(dlm, 0, 1, false, ptr);
		org.junit.Assert.assertEquals("frag2", ptr2.toString());
		ptr2 = ah.extractTerm(dlm, 0, 2, false, ptr);
		org.junit.Assert.assertNull(ptr2);
	}

	// Test compatibility with a previous interface
	@org.junit.Test
	public void testConstructorsV1()
	{
		byte[] src_arr = new byte[]{11, 12, 13, 14, 15};
		String src_str = "ABCDE";
		int off = 1;

		ByteChars ah = new ByteChars(src_arr);
		verify(ah, 0, src_arr.length, src_arr.length);
		org.junit.Assert.assertTrue(src_arr == ah.ar_buf);

		ah = new ByteChars(src_arr, off, src_arr.length-off, false);
		verify(ah, off, src_arr.length-off, src_arr.length-off);
		org.junit.Assert.assertTrue(src_arr == ah.ar_buf);
		ah = new ByteChars(src_arr, off, src_arr.length-off, true);
		verify(ah, 0, src_arr.length-off, src_arr.length-off);
		org.junit.Assert.assertFalse(src_arr == ah.ar_buf);

		ah = new ByteChars(src_str);
		verify(ah, 0, src_str.length(), src_str.length());
	}

	@org.junit.Test
	public void testParseTerms()
	{
		java.util.ArrayList<ByteChars> lst = parseTerms(null);
		org.junit.Assert.assertEquals(0, lst.size());
		lst = parseTerms("");
		org.junit.Assert.assertEquals(0, lst.size());
		lst = parseTerms("|");
		org.junit.Assert.assertEquals(0, lst.size());
		lst = parseTerms("||");
		org.junit.Assert.assertEquals(0, lst.size());

		lst = parseTerms("abc");
		org.junit.Assert.assertEquals(1, lst.size());
		org.junit.Assert.assertTrue(StringOps.sameSeq("abc", lst.get(0)));
		lst = parseTerms("|abc");
		org.junit.Assert.assertEquals(1, lst.size());
		org.junit.Assert.assertTrue(StringOps.sameSeq("abc", lst.get(0)));
		lst = parseTerms("abc|");
		org.junit.Assert.assertEquals(1, lst.size());
		org.junit.Assert.assertTrue(StringOps.sameSeq("abc", lst.get(0)));
		lst = parseTerms("|abc|");
		org.junit.Assert.assertEquals(1, lst.size());
		org.junit.Assert.assertTrue(StringOps.sameSeq("abc", lst.get(0)));

		lst = parseTerms("abc|xyz");
		org.junit.Assert.assertEquals(2, lst.size());
		org.junit.Assert.assertTrue(StringOps.sameSeq("abc", lst.get(0)));
		org.junit.Assert.assertTrue(StringOps.sameSeq("xyz", lst.get(1)));
		lst = parseTerms("|abc|||xyz|");
		org.junit.Assert.assertEquals(2, lst.size());
		org.junit.Assert.assertTrue(StringOps.sameSeq("abc", lst.get(0)));
		org.junit.Assert.assertTrue(StringOps.sameSeq("xyz", lst.get(1)));

		lst = parseTerms("abc|middle|xyz");
		org.junit.Assert.assertEquals(3, lst.size());
		org.junit.Assert.assertTrue(StringOps.sameSeq("abc", lst.get(0)));
		org.junit.Assert.assertTrue(StringOps.sameSeq("middle", lst.get(1)));
		org.junit.Assert.assertTrue(StringOps.sameSeq("xyz", lst.get(2)));
	}

	private static java.util.ArrayList<ByteChars> parseTerms(CharSequence str)
	{
		return ByteChars.parseTerms(str, 0, str==null?0:str.length(), (byte)'|', null, null, null);
	}

	private static void verify(ByteChars ah, int off, int len, int cap)
	{
		ArrayRefTest.verify(ah, off, len, cap);
		org.junit.Assert.assertEquals(ah.size(), ah.length());
	}
}
