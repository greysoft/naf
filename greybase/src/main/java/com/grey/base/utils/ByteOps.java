/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class ByteOps
{
	public static final long KILO = 1024L;
	public static final long MEGA = 1024L * KILO;
	public static final long GIGA = 1024L * MEGA;

	// only suitable for 8-bit characters that map to bytes 1-to-1
	public static byte[] getBytes8(CharSequence str)
	{
		int len = str.length();
		byte[] buf = new byte[len];

		for (int idx = 0; idx != len; idx++)
		{
			buf[idx] = (byte)str.charAt(idx);
		}
		return buf;
	}

	public static byte[] getBytes(CharSequence str)
	{
		return getBytes(str, 0, str.length());
	}

	public static byte[] getBytes(CharSequence str, int off, int len)
	{
		return getBytes(str, off, len, null, 0);
	}

	public static byte[] getBytes(char[] arr, int off, int len)
	{
		return getBytes(arr, off, len, null, 0);
	}

	public static byte[] getBytes(CharSequence str, int coff, int clen, byte[] barr, int boff)
	{
		return getBytes(str, null, coff, clen, barr, boff);
	}

	public static byte[] getBytes(char[] carr, int coff, int clen, byte[] barr, int boff)
	{
		return getBytes(null, carr, coff, clen, barr, boff);
	}

	// Utilise the fact that Java chars are UTF-16, to encode them in their native 16-bit form without using
	// the String-based encodings - which are slower and require you to pick a charset.
	private static byte[] getBytes(CharSequence str, char[] carr, int coff, int clen, byte[] barr, int boff)
	{
		int blen = byteSize(carr, coff, clen);
		if (barr == null || barr.length < boff + blen) {
			barr = new byte[blen];
			boff = 0;
		}
		int lmt = coff + clen;
		int bidx = boff;
		for (int idx = coff; idx != lmt; idx++)
		{
			char chval = (str == null) ? carr[idx] : str.charAt(idx);
			barr[bidx++] = (byte)((chval & 0xFF00)>>8);
			barr[bidx++] = (byte)(chval & 0x00FF);
		}
		return barr;
	}

	public static char[] getChars(byte[] arr, int off, int len)
	{
		return getChars(arr, off, len, null, 0);
	}

	// Assumes the bytes represent a UTF-16 encoding
	// ... which also means we assume 'len' is an even number
	public static char[] getChars(byte[] barr, int boff, int blen, char[] carr, int coff)
	{
		int clen = charSize(barr, boff, blen);
		if (carr == null || carr.length < coff + clen) {
			carr = new char[clen];
			coff = 0;
		}
		int lmt = boff + blen;
		int cidx = coff;
		for (int idx = boff; idx != lmt; idx += 2)
		{
			int hi = (barr[idx] << 8) & 0xFF00;
			int lo = barr[idx+1] & 0xFF;
			carr[cidx++] = (char)(hi + lo);
		}
		return carr;
	}

	// number of bytes required by getBytes() to represent the given array
	public static int byteSize(char[] arr, int off, int len)
	{
		return len << 1;  //efficient multiply-by-2
	}

	// number of chars required by getChars() to represent the given array
	public static int charSize(byte[] arr, int off, int len)
	{
		return len >> 1;  //efficient divide-by-2
	}

	public static boolean cmp(byte[] arr1, int off1, byte[] arr2, int off2, int len)
	{
		for (int idx = 0; idx != len; idx++)
		{
			if (arr1[off1 + idx] != arr2[off2 + idx]) return false;
		}
		return true;
	}

	public static int indexOf(byte[] arr, int off, int len, byte val)
	{
		int lmt = off + len;
		for (int idx = off; idx != lmt; idx++)
		{
			if (arr[idx] == val) return idx;
		}
		return -1;
	}

	public static int indexOf(byte[] arr, byte val)
	{
		return indexOf(arr, 0, arr.length, val);
	}

	// decode big-endian number
	public static int decodeInt(byte[] buf, int off, int len)
	{
		int intval = 0;
		int shift = 8 * (len - 1);

		for (int idx = 0; idx != len; idx++)
		{
			intval += ((buf[off + idx] & 0xFF) << shift);
			shift -= 8;
		}
		return intval;
	}

	// encode numeric value in big-endian format
	public static void encodeInt(int intval, byte[] buf, int off, int len)
	{
		int shift = 8 * (len - 1);

		for (int idx = 0; idx != len; idx++)
		{
			buf[off + idx] = (byte)(intval >> shift);
			shift -= 8;
		}
	}

	public static byte[] encodeInt(int intval, int len)
	{
		byte[] buf = new byte[len];
		encodeInt(intval, buf, 0, len);
		return buf;
	}

	public static long parseByteSize(CharSequence str)
	{
		return parseByteSize(str, 0, str == null ? 0 : str.length());
	}

	public static long parseByteSize(CharSequence str, final int off, final int len)
	{
		if (str == null || len == 0) return 0;
		char chfmt = 'B';  // default format is bytes
		long mult = 1;
		int lmt = off + len;

		char chlast = str.charAt(lmt - 1);
		if (!Character.isDigit(chlast)) {
			chfmt = chlast;
			lmt--;
		}

		if (chfmt == 'G') {
			mult = GIGA;
		} else if (chfmt == 'M') {
			mult = MEGA;
		} else if (chfmt == 'K') {
			mult = KILO;
		} else if (chfmt != 'B') {
			throw new NumberFormatException("Invalid ByteSize spec - "+str.subSequence(off, off+len));
		}
		return mult * IntValue.parseDecimal(str, off, lmt - off);
	}

	public static StringBuilder expandByteSize(long size, StringBuilder sb, boolean reset)
	{
		if (sb == null) {
			sb = new StringBuilder();
		} else if (reset) {
			sb.setLength(0);
		}
		float fval = size;
		String units;
		float base;

		if (size >= (base = GIGA)) {
			units = "GB";
		} else if (size >= (base = MEGA)) {
			units = "MB";
		} else if (size >= (base = KILO)) {
			units = "KB";
		} else {
			units = "";
			base = 0;
		}
		int places = 0;

		if (base != 0) {
			if (fval % base != 0) places = 3;
			fval = fval / base;
		}
		String fstr = String.format("%."+places+"f", fval);
		int lmt = fstr.length();
		if (fstr.indexOf('.') != -1) {
			while (fstr.charAt(lmt - 1) == '0') lmt--;
		}
		sb.append(fstr, 0, lmt);
		sb.append(units);
		return sb;
	}
}
