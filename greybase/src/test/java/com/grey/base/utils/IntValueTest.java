/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class IntValueTest
{
	private static final int TESTVAL = 9;

	@org.junit.Test
	public void testIntValueConstructor() {
		IntValue iv = new IntValue(TESTVAL);
		org.junit.Assert.assertEquals(TESTVAL, iv.val);
		iv = new IntValue();
		org.junit.Assert.assertEquals(0, iv.val);
	}

	@org.junit.Test
	public void testSet() {
		IntValue iv1 = new IntValue(TESTVAL + 99);
		IntValue iv2 = iv1.set(TESTVAL);
		org.junit.Assert.assertEquals(TESTVAL, iv1.val);
		org.junit.Assert.assertTrue("set() created new Object", iv1 == iv2);
	}

	@org.junit.Test
	public void testHashCode() {
		IntValue iv = new IntValue(TESTVAL);
		org.junit.Assert.assertEquals(TESTVAL, iv.hashCode());
	}

	@org.junit.Test
	public void testEquals() {
		IntValue iv1 = new IntValue(TESTVAL);
		IntValue iv2 = new IntValue(TESTVAL);
		IntValue iv3 = new IntValue(TESTVAL + 99);
		org.junit.Assert.assertTrue("Verifying Equals", iv1.equals(iv2));
		org.junit.Assert.assertFalse("Verifying Not-Equals", iv1.equals(iv3));
		org.junit.Assert.assertFalse("Verifying Not-Equals-ByClass", iv1.equals("blah"));
	}

	@org.junit.Test
	public void testCompare() {
		IntValue iv1 = new IntValue(5);
		IntValue iv2 = new IntValue(9);
		org.junit.Assert.assertTrue("Verifying CompareTo", iv1.compareTo(iv2) < 0);
		org.junit.Assert.assertTrue("Verifying CompareTo", iv1.compareTo(Integer.valueOf(3)) > 0);
	}

	@org.junit.Test
	public void testParseDecimal() {
		long orignum = 3423980; //contains both the lowest and highest digits (0 & 9)
		String str = Long.toString(orignum);
		long num = IntValue.parseDecimal(str);
		org.junit.Assert.assertEquals(orignum, num);

		int len = str.length();
		String pfx = "abc";
		str = pfx + str + "xyz";
		num = IntValue.parseDecimal(str, pfx.length(), len);
		org.junit.Assert.assertEquals(orignum, num);

		// make sure number larger than Int works
		orignum = 1336728068644L;
		org.junit.Assert.assertTrue(orignum > Integer.MAX_VALUE);//sanity check
		num = IntValue.parseDecimal(Long.toString(orignum));
		org.junit.Assert.assertEquals(orignum, num);

		try {
			char[] arr = new char[]{'1', '0'-1, '3'};
			IntValue.parseDecimal(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Decimal digit - low: "+new String(arr));
		} catch (NumberFormatException ex) {}
		try {
			char[] arr = new char[]{'1', '9'+1, '3'};
			IntValue.parseDecimal(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Decimal digit - high: "+new String(arr));
		} catch (NumberFormatException ex) {}
	}

	@org.junit.Test
	public void testParseHex() {
		long orignum = 0x2a3F906; //contains all the boundary digits (0,9,A,F)
		String str = "2a3F906"; //don't use Long.toHexString() because it will normalise the case
		long num = IntValue.parseHex(str);
		org.junit.Assert.assertEquals(orignum, num);

		int len = str.length();
		String pfx = "uvw";
		str = pfx + str + "xyz";
		num = IntValue.parseHex(str, pfx.length(), len);
		org.junit.Assert.assertEquals(orignum, num);

		orignum = 0x123456789L;
		org.junit.Assert.assertTrue(orignum > Integer.MAX_VALUE);//sanity check
		num = IntValue.parseHex(Long.toHexString(orignum));
		org.junit.Assert.assertEquals(orignum, num);

		try {
			char[] arr = new char[]{'1', '0'-1, '3'};
			IntValue.parseHex(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Hex digit - below Dec: "+new String(arr));
		} catch (NumberFormatException ex) {}
		try {
			char[] arr = new char[]{'1', '9'+1, '3'};
			IntValue.parseHex(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Hex digit - above Dec: "+new String(arr));
		} catch (NumberFormatException ex) {}
		try {
			char[] arr = new char[]{'1', 'A'-1, '3'};
			IntValue.parseHex(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Hex digit - below Hex: "+new String(arr));
		} catch (NumberFormatException ex) {}
		try {
			char[] arr = new char[]{'1', 'F'+1, '3'};
			IntValue.parseHex(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Hex digit - above Hex: "+new String(arr));
		} catch (NumberFormatException ex) {}
	}
}
