/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class ByteOpsTest
{
	@org.junit.Test
	public void testSerialiseInt()
	{
		for (int size = 1; size <= 8; size++) {
			int[] numvals = new int[]{0, 10, 127, 128, 255};
			for (int idx = 0; idx != numvals.length; idx++) {
				byte[] barr = ByteOps.encodeInt(numvals[idx], size);
				int val = ByteOps.decodeInt(barr, 0, size);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, numvals[idx], val);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, size, barr.length);
			}
		}

		for (int size = 2; size <= 8; size++) {
			int[] numvals = new int[]{256, 0x0102, 0x8192, 0xffff, 0x7fff, 0x8000};
			for (int idx = 0; idx != numvals.length; idx++) {
				byte[] barr = ByteOps.encodeInt(numvals[idx], size);
				int val = ByteOps.decodeInt(barr, 0, size);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, numvals[idx], val);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, size, barr.length);
			}
		}

		for (int size = 4; size <= 8; size++) {
			int[] numvals = new int[]{-1, Integer.MAX_VALUE, 0xef028104, 0x12345678,
				Integer.MIN_VALUE, Integer.MIN_VALUE+500, Integer.MAX_VALUE-500};
			for (int idx = 0; idx != numvals.length; idx++) {
				byte[] barr = ByteOps.encodeInt(numvals[idx], size);
				int val = ByteOps.decodeInt(barr, 0, size);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, numvals[idx], val);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, size, barr.length);
			}
		}

		for (int size = 4; size <= 8; size++) {
			long[] numvals = new long[]{ByteOps.INTMASK, Integer.MAX_VALUE, Integer.MIN_VALUE&ByteOps.INTMASK,
				0xef028104L, 0x12345678L, Integer.MAX_VALUE+1L, Integer.MAX_VALUE+500L};
			for (int idx = 0; idx != numvals.length; idx++) {
				byte[] barr = ByteOps.encodeInt(numvals[idx], size);
				long val = ByteOps.decodeLong(barr, 0, size);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, numvals[idx], val);
				org.junit.Assert.assertEquals("size="+size+"/idx="+idx, size, barr.length);
			}
		}

		//Values with non-zero upper byte set require the full 8-byte encoding
		long[] numvals = new long[]{-1L, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE+500L, Long.MAX_VALUE-500L};
		int size = 8;
		for (int idx = 0; idx != numvals.length; idx++) {
			byte[] barr = ByteOps.encodeInt(numvals[idx], size);
			long val = ByteOps.decodeLong(barr, 0, size);
			org.junit.Assert.assertEquals("size="+size+"/idx="+idx, numvals[idx], val);
			org.junit.Assert.assertEquals("size="+size+"/idx="+idx, size, barr.length);
		}

		int numval = 0xef028104;
		byte[] barr = new byte[6];
		size = 4;
		int off = 1;
		int off2 = ByteOps.encodeInt(numval, barr, off, size);
		int val = ByteOps.decodeInt(barr, off, size);
		org.junit.Assert.assertEquals(numval, val);
		org.junit.Assert.assertEquals(off + size, off2);
	}

	@org.junit.Test
	public void testCharConv() throws java.io.UnsupportedEncodingException
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
		barr = ByteOps.getBytesUTF16(carr, 0, carr.length);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		//... and back again
		char[] carr2 = ByteOps.getCharsUTF16(barr);
		org.junit.Assert.assertArrayEquals(carr, carr2);

		// now try non-ASCII text (8-bit chars - Sterling symbol)
		str = "Sterling"+new String(new char[]{163})+"Symbol"; //can't type in literally because Java source is UTF-8
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
		barr = ByteOps.getBytesUTF16(carr, 0, carr.length);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		//... and back again
		carr2 = ByteOps.getCharsUTF16(barr, 0, barr.length);
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
		barr = ByteOps.getBytesUTF16(carr, 0, carr.length);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		//... and back again
		carr2 = ByteOps.getCharsUTF16(barr, 0, barr.length);
		org.junit.Assert.assertArrayEquals(carr, carr2);
		//test the CharSequence variant of getbytes
		barr = ByteOps.getBytesUTF16(str);
		str2 = new String(barr, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);

		// test offset receiving arrays
		str = "Just some text - the char widths don't matter";
		int coff = 5;
		int clen = str.length() - coff - 2;
		carr = str.toCharArray();
		str = str.substring(coff, coff + clen);
		byte[] barr2 = new byte[256]; //more than big enough
		java.util.Arrays.fill(barr2, (byte)'x');
		int boff = 3;
		int blen = ByteOps.byteSizeUTF16(carr, coff, clen);
		barr = ByteOps.getBytesUTF16(carr, coff, clen, barr2, boff);
		org.junit.Assert.assertSame(barr, barr2);
		org.junit.Assert.assertEquals(carr[coff], barr[boff+1]); //leading char is 8-bit, so goes in 2nd byte
		org.junit.Assert.assertEquals(0, barr[boff]);
		org.junit.Assert.assertEquals('x', barr[0]);
		org.junit.Assert.assertEquals('x', barr[boff+blen]);
		str2 = new String(barr, boff, blen, "UTF-16");
		org.junit.Assert.assertEquals(str, str2);
		barr2 = new byte[carr.length-2]; //too small
		barr = ByteOps.getBytesUTF16(carr, coff, clen, barr2, boff);
		org.junit.Assert.assertNotSame(barr, barr2);
		org.junit.Assert.assertEquals(carr[coff], barr[1]); //leading char is 8-bit, so goes in 2nd byte
		org.junit.Assert.assertEquals(0, barr[0]);
		org.junit.Assert.assertEquals(barr.length, ByteOps.byteSizeUTF16(carr, coff, clen));
		carr2 = new char[256]; //more than big enough
		coff = 2;
		carr = ByteOps.getCharsUTF16(barr, 0, barr.length, carr2, coff);
		org.junit.Assert.assertSame(carr, carr2);
		str2 = new String(carr2, coff, blen/2);
		org.junit.Assert.assertEquals(str, str2);
		carr2 = new char[5]; //too small
		carr = ByteOps.getCharsUTF16(barr, 0, barr.length, carr2, coff);
		org.junit.Assert.assertNotSame(carr, carr2);
		str2 = new String(carr, 0, blen/2);
		org.junit.Assert.assertEquals(str, str2);
	}

	@org.junit.Test
	public void testCount()
	{
		byte[] arr = new byte[]{1, 0, (byte)200};
		int cnt = ByteOps.count(1, arr);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = ByteOps.count(0, arr);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = ByteOps.count(200, arr);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = ByteOps.count(2, arr);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(201, arr);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(260, arr);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(260, new byte[0]);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(260, null);
		org.junit.Assert.assertEquals(0, cnt);

		arr = ByteOps.getBytes8("abc123abcabc12ab");
		byte[] arr2 = ByteOps.getBytes8("abc");
		cnt = ByteOps.count(arr, arr2);
		org.junit.Assert.assertEquals(3, cnt);
		cnt = ByteOps.count(arr, 1, arr.length-1, arr2, 0, arr2.length);
		org.junit.Assert.assertEquals(2, cnt);
		cnt = ByteOps.count(arr, 2, arr.length-2, arr2, 1, arr2.length-1);
		org.junit.Assert.assertEquals(2, cnt);
		//test a sequence which does have a leading match, but doesn't match in full
		arr2 = ByteOps.getBytes8("123x");
		cnt = ByteOps.count(arr, arr2);
		org.junit.Assert.assertEquals(0, cnt);
		//make sure we can find a target that is aligned with the end of the container too
		arr2 = ByteOps.getBytes8("2ab");
		cnt = ByteOps.count(arr, arr2);
		org.junit.Assert.assertEquals(1, cnt);

		cnt = ByteOps.count(new byte[0], new byte[0]);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(null, null);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(arr, new byte[0]);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(new byte[0], arr2);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(arr, null);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = ByteOps.count(null, arr2);
		org.junit.Assert.assertEquals(0, cnt);
	}

	@org.junit.Test
	public void testIndexOf()
	{
		byte[] arr = new byte[]{1, 0, (byte)200};
		org.junit.Assert.assertEquals(0, ByteOps.indexOf(arr, 1));
		org.junit.Assert.assertEquals(1, ByteOps.indexOf(arr, 0));
		org.junit.Assert.assertEquals(2, ByteOps.indexOf(arr, 200));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(arr, 3));
		// with offsets
		org.junit.Assert.assertEquals(1, ByteOps.indexOf(arr, 1, arr.length - 1, 0));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(arr, 2, arr.length - 2, 0));
		org.junit.Assert.assertEquals(2, ByteOps.indexOf(arr, 1, arr.length - 1, 200));
		org.junit.Assert.assertEquals(2, ByteOps.indexOf(arr, 2, arr.length - 2, 200));
		//edge cases
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(arr, 0, 0, 200));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(null, 0, 0, 200));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(new byte[0], 0, 0, 200));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(null, 200));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(new byte[0], 200));

		arr = ByteOps.getBytes8("abc123abcabc12ab");
		byte[] arr2 = ByteOps.getBytes8("abc");
		int idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(0, idx);
		idx = ByteOps.indexOf(arr, 1, arr.length-1, arr2, 0, arr2.length);
		org.junit.Assert.assertEquals(6, idx);
		org.junit.Assert.assertEquals('a', arr[idx]); //sanity-check my index counting
		idx = ByteOps.indexOf(arr, 0, arr.length, arr2, 1, arr2.length-1);
		org.junit.Assert.assertEquals(1, idx);
		//edge cases
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(null, null));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(null, arr2));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(arr, null));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(new byte[0], new byte[0]));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(new byte[0], arr2));
		org.junit.Assert.assertEquals(-1, ByteOps.indexOf(arr, new byte[0]));

		//make sure we can find a target that is aligned with the end of the container too
		arr2 = ByteOps.getBytes8("2ab");
		idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(arr.length-3, idx);
		org.junit.Assert.assertEquals('2', arr[idx]); //sanity-check my index counting
		//test a sequence which does have a leading match, but doesn't match in full
		arr2 = ByteOps.getBytes8("123x");
		idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(-1, idx);
		//check longer target
		arr = ByteOps.getBytes8("abc");
		arr2 = ByteOps.getBytes8("abcdef");
		idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(-1, idx);
		//with a target whose initial character doesn't match
		arr2 = ByteOps.getBytes8("xa");
		idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(-1, idx);
		//single-byte target at end
		arr2 = ByteOps.getBytes8("c");
		idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(2, idx);
		//some more single-byte cases
		arr = ByteOps.getBytes8("c");
		idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(0, idx);
		arr = ByteOps.getBytes8("x");
		idx = ByteOps.indexOf(arr, arr2);
		org.junit.Assert.assertEquals(-1, idx);
		//empty-array edge cases
		idx = ByteOps.indexOf(arr, new byte[0]);
		org.junit.Assert.assertEquals(-1, idx);
		idx = ByteOps.indexOf(new byte[0], arr2);
		org.junit.Assert.assertEquals(-1, idx);
		idx = ByteOps.indexOf(new byte[0], new byte[0]);
		org.junit.Assert.assertEquals(-1, idx);
	}

	@org.junit.Test
	public void testCompare()
	{
		byte[] data1 = new byte[]{0, 1, (byte)254, (byte)255, 10, 17, 0, 0};
		byte[] data2 = new byte[]{99, 99, 1, (byte)254, (byte)255, 10, 17, 0};
		org.junit.Assert.assertEquals(data1.length, data2.length); //sanity-test the above defs
		boolean ok = ByteOps.cmp(data1, 1, data2, 2, data1.length-3);
		org.junit.Assert.assertTrue(ok);
		ok = ByteOps.cmp(data1, 0, data2, 0, data1.length);
		org.junit.Assert.assertFalse(ok);
		data1[1] = 2;
		ok = ByteOps.cmp(data1, 1, data2, 2, data1.length-3);
		org.junit.Assert.assertFalse(ok);
		ok = ByteOps.cmp(data1, data1);
		org.junit.Assert.assertTrue(ok);
		ok = ByteOps.cmp(data1, data2);
		org.junit.Assert.assertFalse(ok);
		ok = ByteOps.cmp(data1, new byte[]{0, 1});
		org.junit.Assert.assertFalse(ok);

		org.junit.Assert.assertTrue(ByteOps.cmp(null, null));
		org.junit.Assert.assertFalse(ByteOps.cmp(data1, null));
		org.junit.Assert.assertFalse(ByteOps.cmp(null, data1));
		org.junit.Assert.assertTrue(ByteOps.cmp(new byte[0], new byte[0]));
		org.junit.Assert.assertFalse(ByteOps.cmp(data1, new byte[0]));
		org.junit.Assert.assertFalse(ByteOps.cmp(new byte[0], data1));
	}

	@org.junit.Test
	public void testFind()
	{
		byte[] seq = "end".getBytes();
		byte[] data1 = "We draw blanken on this data".getBytes();
		byte[] data2 = "This contains the start of the en".getBytes();
		byte[] data3a = "d".getBytes();
		byte[] data3b = "delight".getBytes();
		String str4 = "This contains the end";
		byte[] data4 = (str4+" in midstream").getBytes();
		byte[] data5 = "False start immediately followed by the seq - enend".getBytes();

		int off = ByteOps.find(data1, seq, 0);
		org.junit.Assert.assertEquals(0, off);
		off = ByteOps.find(data2, seq, off);
		org.junit.Assert.assertEquals(2, off);
		int next = ByteOps.find(data3a, seq, off);
		org.junit.Assert.assertEquals(-1, next);
		next = ByteOps.find(data3b, seq, off);
		org.junit.Assert.assertEquals(-1, next);

		off = ByteOps.find(data4, seq, 0);
		org.junit.Assert.assertEquals(-str4.length(), off);

		off = ByteOps.find(seq, seq, 0);
		org.junit.Assert.assertEquals(-seq.length, off);
		off = ByteOps.find(seq, 1, seq.length - 1, seq, 0);
		org.junit.Assert.assertEquals(0, off);

		off = ByteOps.find(data5, seq, 0);
		org.junit.Assert.assertEquals(-data5.length, off);

		off = ByteOps.find(null, seq, 0);
		org.junit.Assert.assertEquals(0, off);
		off = ByteOps.find(null, seq, 1);
		org.junit.Assert.assertEquals(1, off);
		off = ByteOps.find(new byte[0], seq, 0);
		org.junit.Assert.assertEquals(0, off);
		off = ByteOps.find(new byte[0], seq, 1);
		org.junit.Assert.assertEquals(1, off);

		off = ByteOps.find(data5, null, 0);
		org.junit.Assert.assertEquals(0, off);
		off = ByteOps.find(data5, null, 1);
		org.junit.Assert.assertEquals(1, off);
		off = ByteOps.find(data5, new byte[0], 0);
		org.junit.Assert.assertEquals(0, off);
		off = ByteOps.find(data5, new byte[0], 1);
		org.junit.Assert.assertEquals(1, off);
	}

	@org.junit.Test
	public void testParseSize()
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
	public void testExpandSize()
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
