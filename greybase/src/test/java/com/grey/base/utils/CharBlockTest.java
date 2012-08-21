/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class CharBlockTest
{
	@org.junit.Test
	public void testConstructors()
	{
		String src_str = "ABCDE";
		char[] src_arr = src_str.toCharArray();
		int off = 1;
		int len = 2;
		int cap = 10;

		CharBlock ah = new CharBlock(-1);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);
		ah = new CharBlock(cap);
		verify(ah, 0, 0, cap);
		ah = new CharBlock();
		org.junit.Assert.assertNotNull(ah.ar_buf);
		org.junit.Assert.assertEquals(0, ah.ar_off);
		org.junit.Assert.assertEquals(0, ah.ar_len);
		org.junit.Assert.assertTrue(ah.capacity() > 0);

		ah = new CharBlock(src_arr, off, len, false);
		verify(ah, off, len, src_arr.length - off);
		org.junit.Assert.assertTrue(src_arr == ah.ar_buf);
		org.junit.Assert.assertEquals(src_arr[off], ah.charAt(0));
		org.junit.Assert.assertEquals(src_arr[off+1], ah.charAt(1));
		ah = new CharBlock(src_arr, off, len, true);
		verify(ah, 0, len, len);
		org.junit.Assert.assertFalse(src_arr == ah.ar_buf);
		org.junit.Assert.assertEquals(src_arr[off], ah.charAt(0));
		org.junit.Assert.assertEquals(src_arr[off+1], ah.charAt(1));
		ah = new CharBlock(src_arr);
		off = 0; len = src_arr.length;
		verify(ah, off, len, len);
		org.junit.Assert.assertTrue(src_arr == ah.ar_buf);
		org.junit.Assert.assertEquals(src_arr[off], ah.charAt(0));
		org.junit.Assert.assertEquals(src_arr[len - 1], ah.charAt(len - 1));

		ah = new CharBlock(src_str);
		verify(ah, 0, src_str.length(), src_str.length());
		org.junit.Assert.assertEquals(src_str.charAt(0), ah.charAt(0));
		org.junit.Assert.assertEquals(src_str.charAt(src_str.length()-1), ah.charAt(src_str.length()-1));
		off = 1; len = src_str.length() - 2;
		ah = new CharBlock(src_str, off, len);
		verify(ah, 0, len, len);
		org.junit.Assert.assertEquals(src_str.charAt(1), ah.charAt(0));
		org.junit.Assert.assertEquals(src_str.charAt(len), ah.charAt(len-1));

		CharBlock src_ah = new CharBlock(src_str);
		ah = new CharBlock(src_ah);
		verify(ah, src_ah.ar_off, src_ah.size(), src_ah.capacity());
		org.junit.Assert.assertTrue(src_ah.ar_buf == ah.ar_buf);
		ah = new CharBlock(src_ah, off, len, true);
		verify(ah, 0, len, len);
		org.junit.Assert.assertFalse(src_ah.ar_buf == ah.ar_buf);
		org.junit.Assert.assertEquals(src_ah.charAt(1), ah.charAt(0));
		org.junit.Assert.assertEquals(src_ah.charAt(len), ah.charAt(len-1));

		// This constructor call doesn't compile!  Just pass in ArrayRef<char[]> via the char[] constructors
		//ArrayRef<char[]> ahchar = new ArrayRef<char[]>(src_arr);
		//ah = new CharBlock(ahchar);
	}

	@org.junit.Test
	public void testSequences()
	{
		String src = "ABCDE";
		CharSequence src_seq1 = src.subSequence(0, src.length());
		CharSequence src_seq2 = src.subSequence(1, src.length() - 1);
		org.junit.Assert.assertEquals(src_seq1.toString(), src);
		org.junit.Assert.assertEquals(src_seq1.length(), src_seq2.length() + 2);
		CharBlock ah = new CharBlock(src);

		CharSequence seq = ah.subSequence(0, ah.length());
		org.junit.Assert.assertEquals(seq.toString(), src_seq1);
		seq = ah.subSequence(1, ah.length() - 1);
		org.junit.Assert.assertEquals(seq.toString(), src_seq2);
		org.junit.Assert.assertFalse(((CharBlock)seq).ar_buf == ah.ar_buf);

		seq = ah.subSequence(0, ah.length(), false);
		org.junit.Assert.assertEquals(seq.toString(), src_seq1);
		seq = ah.subSequence(1, ah.length() - 1, false);
		org.junit.Assert.assertEquals(seq.toString(), src_seq2);
		org.junit.Assert.assertTrue(((CharBlock)seq).ar_buf == ah.ar_buf);

		ah.advance(1);
		org.junit.Assert.assertEquals(src.length() - 1, ah.length());
		char[] arr = ah.toCharArray();
		org.junit.Assert.assertEquals(ah.length(), arr.length);
		org.junit.Assert.assertEquals(ah.charAt(0), arr[0]);
		org.junit.Assert.assertEquals(ah.charAt(ah.length() - 1), arr[ah.length() - 1]);
		org.junit.Assert.assertFalse(ah.ar_buf == arr);
		arr = ah.toCharArray(1, ah.length() - 1);
		org.junit.Assert.assertEquals(ah.length() - 1, arr.length);
		org.junit.Assert.assertEquals(ah.charAt(1), arr[0]);
		org.junit.Assert.assertEquals(ah.charAt(arr.length), arr[arr.length - 1]);
		org.junit.Assert.assertFalse(ah.ar_buf == arr);
	}

	@org.junit.Test
	public void testCapacity()
	{
		int cap = 10;
		CharBlock ah = new CharBlock(cap);
		verify(ah, 0, 0, cap);
		char[] arr = ah.ar_buf;

		boolean sts = ah.ensureCapacity(cap);
		verify(ah, 0, 0, cap);
		org.junit.Assert.assertFalse(sts);
		org.junit.Assert.assertTrue(ah.ar_buf == arr);

		sts = ah.ensureCapacity(cap+1);
		verify(ah, 0, 0, cap+1);
		org.junit.Assert.assertTrue(sts);
		org.junit.Assert.assertFalse(ah.ar_buf == arr);
	}

	private static void verify(CharBlock ah, int off, int len, int cap)
	{
		ArrayRefTest.verify(ah, off, len, cap);
		org.junit.Assert.assertEquals(ah.size(), ah.length());
	}
}
