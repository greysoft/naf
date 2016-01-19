/*
 * Copyright 2010-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

// This class is a mutable alternative to the JDK's Integer class, allowing us to reuse its objects for different values, and is therefore
// particularly useful for storing integers in generic containers (especially for short-lived container members).
// That requirement is somewhat lessened by the HashedIntList (which lets us use Int primitives as keys), but this class also provides some
// static integer utilities.
// If nothing else, this class provides an integer object that can be used as a return parameter.
public final class IntValue
	implements Comparable<IntValue>
{
	public static final int SIZE_INT = Integer.toHexString(Integer.MAX_VALUE).length();
	public static final int SIZE_LONG = Long.toHexString(Long.MAX_VALUE).length();
	public static final int SIZE_SHORT = SIZE_INT / 2;
	public static final int SIZE_BYTE = SIZE_SHORT / 2;

	private static final char[] HEXDIGITS_L = "0123456789abcdef".toCharArray();
	private static final char[] HEXDIGITS_U = new String(HEXDIGITS_L).toUpperCase().toCharArray();

	public int val;

	public IntValue(){}
	public IntValue(int v) {val = v;}

	// We need equals() and hashCode() to be able to act as a Collections key
	@Override
	public boolean equals(Object obj)
	{
		if (obj == null || obj.getClass() != IntValue.class) return false;
		IntValue iv2 = (IntValue)obj;
		return (val == iv2.val);
	}

	@Override
	public int hashCode()
	{
		return val;
	}
	
	// convenience method which can be called with same syntax as a constructor
	public IntValue set(int v)
	{
		val = v;
		return this;
	}

	@Override
	public int compareTo(IntValue iv2)
	{
		if (iv2 == null) return 1;
		return val - iv2.val;
	}

	public int compareTo(Integer int2)
	{
		return val - int2.intValue();
	}


	public static long parseDecimal(CharSequence str)
	{
		return parseDecimal(str, 0, str.length());
	}

	public static long parseHex(CharSequence str)
	{
		return parseHex(str, 0, str.length());
	}

	public static long parseDecimal(CharSequence str, int off, int len)
	{
		return StringOps.parseNumber(str, off, len, 10);
	}

	public static long parseHex(CharSequence str, int off, int len)
	{
		return StringOps.parseNumber(str, off, len, 16);
	}

	public static StringBuilder encodeHex(byte numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval & 0xFF, SIZE_BYTE, upper, false, sb);
	}

	public static StringBuilder encodeHexLeading(byte numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval & 0xFF, SIZE_BYTE, upper, true, sb);
	}

	public static StringBuilder encodeHex(short numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval & ByteOps.SHORTMASK, SIZE_SHORT, upper, false, sb);
	}

	public static StringBuilder encodeHexLeading(short numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval & ByteOps.SHORTMASK, SIZE_SHORT, upper, true, sb);
	}

	public static StringBuilder encodeHex(int numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval & ByteOps.INTMASK, SIZE_INT, upper, false, sb);
	}

	public static StringBuilder encodeHexLeading(int numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval & ByteOps.INTMASK, SIZE_INT, upper, true, sb);
	}

	public static StringBuilder encodeHex(long numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval, SIZE_LONG, upper, false, sb);
	}

	public static StringBuilder encodeHexLeading(long numval, boolean upper, StringBuilder sb)
	{
		return encodeHex(numval, SIZE_LONG, upper, true, sb);
	}

	// More efficient alternative to Long.toHexString(), with no object allocation
	private static StringBuilder encodeHex(long numval, int maxdigits, boolean upper, boolean leading, StringBuilder sb)
	{
		final char[] digits = (upper ? HEXDIGITS_U : HEXDIGITS_L);
		if (sb == null) sb = new StringBuilder(maxdigits);
		if (numval == 0) {
			if (leading) {
				for (int loop = 0; loop != maxdigits; loop++) {
					sb.append('0');
				}
			} else {
				sb.append('0');
			}
			return sb;
		}
		for (int digitpos = maxdigits - 1; digitpos != -1; digitpos--) {
			int digitval = (int)(numval >> (digitpos << 2)) & 0xf; //shift down by digitpos*4
			if (!leading && digitval == 0) continue;
			leading = true;
			sb.append(digits[digitval]);
		}
		return sb;
	}
}
