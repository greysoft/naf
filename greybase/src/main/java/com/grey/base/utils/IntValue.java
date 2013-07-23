/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

// This class is a mutable alternative to the JDK's Integer class, allowing us to reuse its objects for different values, and is therefore
// particularly useful for storing integers in generic containers (especially for short-lived container members).
// That requirement is somewhat lessened by the HashedIntList (which lets us use Int primitives as keys), but this class also provides some
// static integer utilities
public final class IntValue
	implements Comparable<IntValue>
{   
	public int val;

	public IntValue(){}
	public IntValue(int v) {val = v;}

	// We need equals() and hashCode() to be able to act as a Collections key
	public boolean equals(Object obj)
	{
		if (obj == null || obj.getClass() != IntValue.class) return false;
		IntValue iv2 = (IntValue)obj;
		return (val == iv2.val);
	}
	
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

	public int compareTo(IntValue iv2)
	{
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
		long numval = 0;
		long mult = 1;
		int pos = off + len - 1;
		
		while (pos >= off) {
			char digit = str.charAt(pos--);
			long digitval = digit - '0';
			if (digitval < 0 || digitval > 9) throw new NumberFormatException("Bad char@"+(pos+1)+" in Dec="+str.subSequence(off, off+len));
			numval += (digitval * mult);
			mult *= 10;
		}
		return numval;
	}

	public static long parseHex(CharSequence str, int off, int len)
	{
		long numval = 0;
		long mult = 1;
		int pos = off + len - 1;
		
		while (pos >= off) {
			char digit = str.charAt(pos--);
			long digitval;
			if (digit >= '0' && digit <= '9') {
				digitval = digit - '0';
			} else {
				digit = Character.toUpperCase(digit);
				if (digit >= 'A' && digit <= 'F') {
					digitval = digit - 'A' + 10;
				} else {
					throw new NumberFormatException("Bad char@"+(pos+1)+" in Hex="+str.subSequence(off, off+len));
				}
			}
			numval += (digitval * mult);
			mult *= 16;
		}
		return numval;
	}
}
