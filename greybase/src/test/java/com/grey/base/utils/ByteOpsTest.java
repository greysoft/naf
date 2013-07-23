/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.io.UnsupportedEncodingException;

public class ByteOpsTest
{
	@org.junit.Test
	public void serialiseInt()
	{
		for (int size = 1; size <= 4; size++) {
			int[] numvals = new int[]{0, 10, 127, 128, 255};
			for (int idx = 0; idx != numvals.length; idx++) {
				byte[] barr = ByteOps.encodeInt(numvals[idx], size);
				int val = ByteOps.decodeInt(barr, 0, size);
				org.junit.Assert.assertEquals(numvals[idx], val);
				org.junit.Assert.assertEquals(size, barr.length);
			}
		}

		for (int size = 2; size <= 4; size++) {
			int[] numvals = new int[]{256, 0x0102, 0x8192, 0xffff, 0x7fff, 0x8000};
			for (int idx = 0; idx != numvals.length; idx++) {
				byte[] barr = ByteOps.encodeInt(numvals[idx], size);
				int val = ByteOps.decodeInt(barr, 0, size);
				org.junit.Assert.assertEquals(numvals[idx], val);
				org.junit.Assert.assertEquals(size, barr.length);
			}
		}

		int[] numvals = new int[]{0xffffffff, 0x7fffffff, 0x80000000, 0xef028104, 0x12345678};
		int size = 4;
		for (int idx = 0; idx != numvals.length; idx++) {
			byte[] barr = ByteOps.encodeInt(numvals[idx], size);
			int val = ByteOps.decodeInt(barr, 0, size);
			org.junit.Assert.assertEquals(numvals[idx], val);
			org.junit.Assert.assertEquals(size, barr.length);
		}

		int numval = 0xef028104;
		byte[] barr = new byte[6];
		ByteOps.encodeInt(numval, barr, 1, size);
		int val = ByteOps.decodeInt(barr, 1, size);
		org.junit.Assert.assertEquals(numval, val);
	}

	@org.junit.Test
	public void charconv() throws UnsupportedEncodingException
	{
		// try a simple 8-bit string first
		String str = "I am ASCII";
		char[] carr = str.toCharArray();
		//verify that 8-bit converter can handle this
		byte[] barr = ByteOps.getBytes8(str);
		String str2 = new String(barr, "UTF-8");
		org.junit.Assert.assertEquals(str, str2);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertFalse(str.equals(str2));
		str2 = new String(barr, "ISO-8859-1");
		org.junit.Assert.assertEquals(str, str2);
		//now verify that native converter can as well - it is exclusively 16-bit
		barr = ByteOps.getBytes(carr, 0, carr.length);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		//... and back again
		char[] carr2 = ByteOps.getChars(barr, 0, barr.length);
		org.junit.Assert.assertArrayEquals(carr, carr2);

		// now try non-ASCII text (8-bit chars - Sterling symbol)
		str = "Sterling"+new String(new char[]{163})+"Symbol";  //can't type in literally because Java source is UTF-8
		//System.out.println("Sterling="+str.charAt(8)+" - "+(int)str.charAt(8));
		carr = str.toCharArray();
		//verify that 8-bit converter can't handle this
		barr = ByteOps.getBytes8(str);
		str2 = new String(barr, "UTF-8");
		org.junit.Assert.assertFalse(str.equals(str2));
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertFalse(str.equals(str2));
		//... unless you pick the right charset
		str2 = new String(barr, "ISO-8859-1");
		org.junit.Assert.assertEquals(str, str2);
		//now verify that native converter works
		barr = ByteOps.getBytes(carr, 0, carr.length);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		//... and back again
		carr2 = ByteOps.getChars(barr, 0, barr.length);
		org.junit.Assert.assertArrayEquals(carr, carr2);

		// now try chars larger than 8 bits (Aleph)
		str = "\u0041\uFB50\u0042";
		carr = str.toCharArray();
		//verify that 8-bit converter can't handle this
		barr = ByteOps.getBytes8(str);
		str2 = new String(barr, "UTF-8");
		org.junit.Assert.assertFalse(str.equals(str2));
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertFalse(str.equals(str2));
		str2 = new String(barr, "ISO-8859-1");
		org.junit.Assert.assertFalse(str.equals(str2));
		//now verify that native converter works
		barr = ByteOps.getBytes(carr, 0, carr.length);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		//... and back again
		carr2 = ByteOps.getChars(barr, 0, barr.length);
		org.junit.Assert.assertArrayEquals(carr, carr2);
		//test the CharSequence variant of getbytes
		barr = ByteOps.getBytes(str);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);

		// test offset receiving arrays
		str = "Just some text - the char widths don't matter";
		int coff = 5;
		int clen = str.length() - coff - 2;
		carr = str.toCharArray();
		str = str.substring(coff,  coff + clen);
		byte[] barr2 = new byte[256];  //more than big enough
		int boff = 3;
		int blen = ByteOps.byteSize(carr, coff, clen);
		barr = ByteOps.getBytes(carr, coff, clen, barr2, boff);
		org.junit.Assert.assertSame(barr, barr2);
		str2 = new String(barr, boff, blen, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		carr2 = new char[256];  //more than big enough
		coff = 2;
		carr = ByteOps.getChars(barr, boff, blen, carr2, coff);
		org.junit.Assert.assertSame(carr, carr2);
		str2 = new String(carr2, coff, blen/2);
		org.junit.Assert.assertEquals(str, str2);
	}

	@org.junit.Test
	public void index()
	{
		byte[] arr = new byte[]{1, 0, (byte)200};
		org.junit.Assert.assertEquals(0, ByteOps.indexOf(arr, (byte)1));
		org.junit.Assert.assertEquals(1, ByteOps.indexOf(arr, (byte)0));
		org.junit.Assert.assertEquals(2, ByteOps.indexOf(arr, (byte)200));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(arr, (byte)3));
		// with offsets
		org.junit.Assert.assertEquals(1, ByteOps.indexOf(arr, 1, arr.length - 1, (byte)0));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(arr, 2, arr.length - 2, (byte)0));
		org.junit.Assert.assertEquals(2, ByteOps.indexOf(arr, 1, arr.length - 1, (byte)200));
		org.junit.Assert.assertEquals(2, ByteOps.indexOf(arr, 2, arr.length - 2, (byte)200));
	}

	@org.junit.Test
	public void parseSize()
	{
		org.junit.Assert.assertEquals(0, ByteOps.parseByteSize(null));
		org.junit.Assert.assertEquals(0, ByteOps.parseByteSize(""));
		org.junit.Assert.assertEquals(9, ByteOps.parseByteSize("9"));
		org.junit.Assert.assertEquals(1029, ByteOps.parseByteSize("1029B"));
		org.junit.Assert.assertEquals(2048, ByteOps.parseByteSize("2K"));
		org.junit.Assert.assertEquals(2L*ByteOps.MEGA, ByteOps.parseByteSize("2M"));
		org.junit.Assert.assertEquals(2L*ByteOps.GIGA, ByteOps.parseByteSize("2G"));
		try {
			long b = ByteOps.parseByteSize("2X");
			org.junit.Assert.fail("Failed to detect bad size unit - "+b);
		} catch (NumberFormatException ex) {}
	}

	@org.junit.Test
	public void expandSize()
	{
		StringBuilder sb = ByteOps.expandByteSize(0, null, false);
		org.junit.Assert.assertEquals("0", sb.toString());
		StringBuilder sb2 = ByteOps.expandByteSize(1023, sb, true);
		org.junit.Assert.assertEquals("1023", sb2.toString());
		org.junit.Assert.assertSame(sb, sb2);
		sb2 = ByteOps.expandByteSize(9, sb, false);
		org.junit.Assert.assertEquals("10239", sb2.toString());
		org.junit.Assert.assertSame(sb, sb2);
		ByteOps.expandByteSize(100, sb, true);
		org.junit.Assert.assertEquals("100", sb.toString());

		ByteOps.expandByteSize(ByteOps.KILO, sb, true);
		org.junit.Assert.assertEquals("1KB", sb.toString());
		ByteOps.expandByteSize(9 * ByteOps.KILO, sb, true);
		org.junit.Assert.assertEquals("9KB", sb.toString());
		ByteOps.expandByteSize(ByteOps.KILO + 512, sb, true);
		org.junit.Assert.assertEquals("1.5KB", sb.toString());
		ByteOps.expandByteSize(ByteOps.KILO + 723, sb, true);
		org.junit.Assert.assertEquals("1.706KB", sb.toString());
		ByteOps.expandByteSize(100 * ByteOps.KILO, sb, true);
		org.junit.Assert.assertEquals("100KB", sb.toString());

		ByteOps.expandByteSize(ByteOps.MEGA, sb, true);
		org.junit.Assert.assertEquals("1MB", sb.toString());
		ByteOps.expandByteSize(90 * ByteOps.MEGA, sb, true);
		org.junit.Assert.assertEquals("90MB", sb.toString());
		ByteOps.expandByteSize(ByteOps.MEGA + (127 * ByteOps.KILO), sb, true);
		org.junit.Assert.assertEquals("1.124MB", sb.toString());

		ByteOps.expandByteSize(ByteOps.GIGA, sb, true);
		org.junit.Assert.assertEquals("1GB", sb.toString());
		ByteOps.expandByteSize(90 * ByteOps.GIGA, sb, true);
		org.junit.Assert.assertEquals("90GB", sb.toString());
		ByteOps.expandByteSize(ByteOps.GIGA + (723 * ByteOps.MEGA), sb, true);
		org.junit.Assert.assertEquals("1.706GB", sb.toString());
	}
}
