/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
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
		org.junit.Assert.assertFalse(iv1.equals(null));
		org.junit.Assert.assertFalse(iv1.equals(Integer.valueOf(iv1.val)));
	}

	@org.junit.Test
	public void testCompare() {
		IntValue iv1 = new IntValue(5);
		IntValue iv2 = new IntValue(9);
		org.junit.Assert.assertTrue("Verifying CompareTo", iv1.compareTo(iv2) < 0);
		org.junit.Assert.assertTrue("Verifying CompareTo", iv1.compareTo(Integer.valueOf(3)) > 0);
		org.junit.Assert.assertTrue(iv1.compareTo((IntValue)null) > 0);
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

		str = "+9";
		num = IntValue.parseDecimal(str);
		org.junit.Assert.assertEquals(9, num);
		str = "-9";
		num = IntValue.parseDecimal(str);
		org.junit.Assert.assertEquals(-9, num);
		str = "-98";
		num = IntValue.parseDecimal(str);
		org.junit.Assert.assertEquals(-98, num);
		str = "";
		num = IntValue.parseDecimal(str);
		org.junit.Assert.assertEquals(0, num);
		str = "+";
		num = IntValue.parseDecimal(str);
		org.junit.Assert.assertEquals(0, num);
		str = "-";
		num = IntValue.parseDecimal(str);
		org.junit.Assert.assertEquals(0, num);

		try {
			char[] arr = new char[]{'1', '0'-1, '3'};
			IntValue.parseDecimal(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Decimal digit - low: "+new String(arr));
		} catch (NumberFormatException ex) {System.out.println("Expected error - "+ex);}
		try {
			char[] arr = new char[]{'1', '9'+1, '3'};
			IntValue.parseDecimal(new String(arr));
			org.junit.Assert.fail("Failed to trap bad Decimal digit - high: "+new String(arr));
		} catch (NumberFormatException ex) {System.out.println("Expected error - "+ex);}
		try {
			IntValue.parseDecimal("x");
			org.junit.Assert.fail("Failed to trap bad sole char");
		} catch (NumberFormatException ex) {System.out.println("Expected error - "+ex);}
		try {
			IntValue.parseDecimal("x9");
			org.junit.Assert.fail("Failed to trap bad char in leading position");
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

	@org.junit.Test
	public void testEncodeHex() {
		StringBuilder sb = IntValue.encodeHex(0x987e0f, false, null);
		org.junit.Assert.assertEquals("987e0f", sb.toString());
		sb = IntValue.encodeHex(0x987e0f, true, null);
		org.junit.Assert.assertEquals("987E0F", sb.toString());
		sb = IntValue.encodeHexLeading(0x987e0f, true, null);
		org.junit.Assert.assertEquals("00987E0F", sb.toString());
		sb = IntValue.encodeHex(0, true, null);
		org.junit.Assert.assertEquals("0", sb.toString());
		sb = IntValue.encodeHex(1, true, null);
		org.junit.Assert.assertEquals("1", sb.toString());
		
		sb = IntValue.encodeHex(Short.MAX_VALUE, true, null);
		org.junit.Assert.assertEquals("7FFF", sb.toString());
		sb = IntValue.encodeHex(Short.MAX_VALUE-1, true, null);
		org.junit.Assert.assertEquals("7FFE", sb.toString());
		sb = IntValue.encodeHex(Short.MIN_VALUE, true, null);
		org.junit.Assert.assertEquals("8000", sb.toString());
		sb = IntValue.encodeHex((short)-1, true, null);
		org.junit.Assert.assertEquals("FFFF", sb.toString());		
		sb = IntValue.encodeHex((short)0, true, null);
		org.junit.Assert.assertEquals("0", sb.toString());
		sb = IntValue.encodeHex((short)1, true, null);
		org.junit.Assert.assertEquals("1", sb.toString());	
		sb = IntValue.encodeHexLeading((short)0, true, null);
		org.junit.Assert.assertEquals("0000", sb.toString());
		sb = IntValue.encodeHexLeading((short)1, true, null);
		org.junit.Assert.assertEquals("0001", sb.toString());

		sb = IntValue.encodeHex((byte)-1, true, null);
		org.junit.Assert.assertEquals("FF", sb.toString());
		sb = IntValue.encodeHex((byte)1, true, null);
		org.junit.Assert.assertEquals("1", sb.toString());
		sb = IntValue.encodeHex((byte)0xad, true, null);
		org.junit.Assert.assertEquals("AD", sb.toString());
		sb = IntValue.encodeHexLeading((byte)0xad, true, null);
		org.junit.Assert.assertEquals("AD", sb.toString());

		String long_fs = "0xffffffffffffffff";
		sb = new StringBuilder("0x");
		StringBuilder sb2 = IntValue.encodeHex((long)-1, false, sb);
		org.junit.Assert.assertTrue(sb2 == sb);
		org.junit.Assert.assertEquals(long_fs, sb.toString());
		org.junit.Assert.assertEquals("0x"+Long.toHexString(-1), sb.toString()); //just to sanity-check the above Fs
		sb = IntValue.encodeHexLeading((long)-1, false, null);
		org.junit.Assert.assertEquals(long_fs.substring(2), sb.toString());
	}
}