/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class ByteOps
{
	public static final long KILO = 1024L;
	public static final long MEGA = 1024L * KILO;
	public static final long GIGA = 1024L * MEGA;

	public static final int LONGBYTES = Long.SIZE/8; //number of bytes required to hold a 64-bit long
	public static final int INTBYTES = Integer.SIZE/8; //32-bit int
	public static final int SHORTMASK = (-1 & 0xffff); //can be used to prevent sign-extension when casting short to larger
	public static final long INTMASK = (1L << 32) - 1L; //can be used to prevent sign-extension when casting int to larger

	public static final byte[] EMPTYBUF = new byte[0];

	public static byte[] getBytesUTF16(CharSequence str) {return getBytesUTF16(str, 0, str.length());}
	public static byte[] getBytesUTF16(CharSequence str, int off, int len) {return getBytesUTF16(str, off, len, null, 0);}
	public static byte[] getBytesUTF16(CharSequence str, int coff, int clen, byte[] barr, int boff) {return getBytesUTF16(str, null, coff, clen, barr, boff);}
	public static byte[] getBytesUTF16(char[] arr, int off, int len) {return getBytesUTF16(arr, off, len, null, 0);}
	public static byte[] getBytesUTF16(char[] carr, int coff, int clen, byte[] barr, int boff) {return getBytesUTF16(null, carr, coff, clen, barr, boff);}
	public static char[] getCharsUTF16(byte[] arr) {return getCharsUTF16(arr, 0, arr.length);}
	public static char[] getCharsUTF16(byte[] arr, int off, int len) {return getCharsUTF16(arr, off, len, null, 0);}
	public static int count(int val, byte[] container) {return count(val, container, 0, container==null?0:container.length);}
	public static int count(byte[] container, byte[] seq) {return count(container, 0, container==null?0:container.length, seq, 0, seq==null?0:seq.length);}
	public static int indexOf(byte[] container, int val) {return indexOf(container, 0, container==null?0:container.length, val);}
	public static int indexOf(byte[] container, byte[] seq) {return indexOf(container, 0, container==null?0:container.length, seq, 0, seq==null?0:seq.length);}
	public static int find(byte[] container, byte[] seq, int soff) {return find(container, 0, container==null?0:container.length, seq, soff);}
	public static StringBuilder expandByteSize(long size) {return expandByteSize(size, null, false);}

	// only suitable for 8-bit characters that map to bytes 1-to-1
	public static byte[] getBytes8(CharSequence str)
	{
		int len = str.length();
		byte[] buf = new byte[len];
		for (int idx = 0; idx != len; idx++) {
			buf[idx] = (byte)str.charAt(idx);
		}
		return buf;
	}

	// Utilise the fact that Java chars are UTF-16, to encode them in their native 16-bit form without using
	// the String-based encodings - which are slower and require you to pick a charset.
	// Either str or carr should be non-null, but not both.
	private static byte[] getBytesUTF16(CharSequence str, char[] carr, int coff, int clen, byte[] barr, int boff)
	{
		int blen = byteSizeUTF16(carr, coff, clen);
		if (barr == null || barr.length < boff + blen) {
			barr = new byte[blen];
			boff = 0;
		}
		int lmt = coff + clen;
		int bidx = boff;
		for (int idx = coff; idx != lmt; idx++) {
			char chval = (str == null) ? carr[idx] : str.charAt(idx);
			barr[bidx++] = (byte)((chval & 0xFF00)>>8);
			barr[bidx++] = (byte)(chval & 0x00FF);
		}
		return barr;
	}

	// Assumes the bytes represent a UTF-16 encoding
	// ... which also means we assume 'len' is an even number
	public static char[] getCharsUTF16(byte[] barr, int boff, int blen, char[] carr, int coff)
	{
		int clen = charSizeUTF16(barr, boff, blen);
		if (carr == null || carr.length < coff + clen) {
			carr = new char[clen];
			coff = 0;
		}
		int lmt = boff + blen;
		int cidx = coff;
		for (int idx = boff; idx != lmt; idx += 2) {
			int hi = (barr[idx] << 8) & 0xFF00;
			int lo = barr[idx+1] & 0xFF;
			carr[cidx++] = (char)(hi + lo);
		}
		return carr;
	}

	// number of bytes required by getBytesUTF16() to represent the given array
	static int byteSizeUTF16(char[] arr, int off, int len)
	{
		return len << 1; //efficient multiply-by-2
	}

	// number of chars required by getCharsUTF16() to represent the given array
	static int charSizeUTF16(byte[] arr, int off, int len)
	{
		return len >> 1; //efficient divide-by-2
	}

	public static boolean cmp(byte[] arr1, byte[] arr2)
	{
		if (arr1 == null || arr2 == null) return (arr1 == null && arr2 == null);
		if (arr1.length != arr2.length) return false;
		return cmp(arr1, 0, arr2, 0, arr1.length);
	}

	public static boolean cmp(byte[] arr1, int off1, byte[] arr2, int off2, int len)
	{
		for (int idx = 0; idx != len; idx++) {
			if (arr1[off1 + idx] != arr2[off2 + idx]) return false;
		}
		return true;
	}

	// take a value argument of int type just to simplify the calling syntax
	public static int count(int val, byte[] container, int coff, int clen)
	{
		byte bval = (byte)(val);
		int cnt = 0;
		int clmt = coff + clen;
		while (coff != clmt) {
			if (container[coff++] == bval) cnt++;
		}
		return cnt;
	}

	public static int count(byte[] container, int coff, int clen, byte[] seq, int soff, int slen)
	{
		int cnt = 0;
		while (clen != 0) {
			int off1 = coff;
			coff = indexOf(container, coff, clen, seq, soff, slen);
			if (coff == -1) break;
			cnt++;
			coff += slen;
			clen -= (coff - off1);
		}
		return cnt;
	}

	// take a value argument of int type just to simplify the calling syntax
	public static int indexOf(byte[] container, int coff, int clen, int val)
	{
		byte bval = (byte)(val);
		int lmt = coff + clen;
		for (int idx = coff; idx != lmt; idx++) {
			if (container[idx] == bval) return idx;
		}
		return -1;
	}

	public static int indexOf(byte[] container, int coff, int clen, byte[] seq, int soff, int slen)
	{
		if (slen == 0) return -1;
		int clmt = coff + clen;
		while (coff != clmt) {
			int off1 = coff;
			coff = indexOf(container, coff, clen, seq[soff]);
			if (coff == -1) return -1;
			clen -= (coff - off1);
			if (clen < slen) return -1;
			boolean found = true;
			for (int idx = 0; idx != slen; idx++) {
				if (container[coff+idx] != seq[soff+idx]) {
					found = false;
					break;
				}
			}
			if (found) return coff;
			coff++;
			clen--;
		}
		return -1;
	}

	/*
	 * Finds an occurence of the specified sequence in the specified data buffer, including cases
	 * where the beginning of the sequence was found in a previous data buffer and allowing us to
	 * keep track of our position in 'seq' for scanning a subsequent data buffer.
	 * This method is not the same as indexOf (which does behave in the intuitive way) as it is intended
	 * to remember context across multiple scans.
	 * @return If we found the end of the sequence, return the index of the next position in 'data'
	 * in negative form. Else return our current position in 'seq' (in positive form).
	 */
	public static int find(byte[] container, int coff, int clen, byte[] seq, int soff)
	{
		if (seq == null || seq.length == 0) return soff;
		int clmt = coff + clen;
		while (coff != clmt) {
			byte val = container[coff++];
			if (val == seq[soff]) {
				if (++soff == seq.length) {
					return -coff;
				}
			} else {
				if (val == seq[0]) {
					soff = 1;
				} else {
					soff = 0;
				}
			}
		}
		return soff;
	}

	// decode big-endian number
	public static long decodeLong(byte[] buf, int off, int len)
	{
		long intval = 0;
		int shift = 8 * (len - 1);

		for (int idx = 0; idx != len; idx++) {
			intval += ((buf[off + idx] & 0xFFL) << shift);
			shift -= 8;
		}
		return intval;
	}

	// encode numeric value in big-endian format
	public static int encodeInt(long intval, byte[] buf, int off, int len)
	{
		int shift = 8 * (len - 1);

		for (int loop = 0; loop != len; loop++) {
			buf[off++] = (byte)(intval >> shift);
			shift -= 8;
		}
		return off;
	}

	public static byte[] encodeInt(long intval, int len)
	{
		byte[] buf = new byte[len];
		encodeInt(intval, buf, 0, len);
		return buf;
	}

	public static int decodeInt(byte[] buf, int off, int len)
	{
		return (int)decodeLong(buf, off, len);
	}

	public static int encodeInt(int intval, byte[] buf, int off, int len)
	{
		return encodeInt(intval & ByteOps.INTMASK, buf, off, len);
	}

	public static byte[] encodeInt(int intval, int len)
	{
		return encodeInt(intval & ByteOps.INTMASK, len);
	}

	public static long parseByteSize(CharSequence str)
	{
		return parseByteSize(str, 0, str == null ? 0 : str.length());
	}

	public static long parseByteSize(CharSequence str, final int off, final int len)
	{
		if (str == null || len == 0) return 0;
		char chfmt = 'B'; // default format is bytes
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
		Float fval;

		if (base == 0) {
			fval = Float.valueOf(size);
		} else {
			fval = Float.valueOf(size / base);
		}
		String fstr = String.format("%.3f", fval);
		int lmt = fstr.length();
		while (fstr.charAt(lmt - 1) == '0') lmt--;
		if (fstr.charAt(lmt - 1) == '.') lmt--;
		sb.append(fstr, 0, lmt);
		sb.append(units);
		return sb;
	}
}
