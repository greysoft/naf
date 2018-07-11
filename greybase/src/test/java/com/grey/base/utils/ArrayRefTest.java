/*
 * Copyright 2011-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class ArrayRefTest
{
	@org.junit.Test
	public void testConstructors()
	{
		String[] src_arr = new String[]{"One", "Two", "Three", "Four", "Five"};
		int cap = 10;
		int off = 1;
		int len;

		// test minimal constructor
		ArrayRef<String[]> ah = new ArrayRef<String[]>();
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.buffer());

		ah = new ArrayRef<String[]>(cap, (n) -> new String[n]);
		verify(ah, 0, 0, cap);

		// test array constructor
		ah = new ArrayRef<String[]>(src_arr, 0, src_arr.length);
		verify(ah, 0, src_arr.length, src_arr.length);
		org.junit.Assert.assertTrue(ah.buffer() == src_arr);
		org.junit.Assert.assertTrue(src_arr[0] == ah.buffer()[ah.offset()]);
		org.junit.Assert.assertTrue(src_arr[src_arr.length-1] == ah.buffer()[ah.offset()+src_arr.length-1]);
		// now the with-copy version
		ah = new ArrayRef<String[]>(src_arr, 0, src_arr.length, (n) -> new String[n]);
		verify(ah, 0, src_arr.length, src_arr.length);
		org.junit.Assert.assertFalse(ah.buffer() == src_arr);
		org.junit.Assert.assertTrue(src_arr[0] == ah.buffer()[ah.offset()]);
		org.junit.Assert.assertTrue(src_arr[src_arr.length-1] == ah.buffer()[ah.offset(src_arr.length-1)]);

		ah = new ArrayRef<String[]>(src_arr, off, len = src_arr.length - off - 1);
		verify(ah, off, len, len + 1);
		org.junit.Assert.assertTrue(ah.buffer() == src_arr);

		// test ArrayRef constructor
		ArrayRef<String[]> src_ah = ah;
		src_ah.advance(1);
		org.junit.Assert.assertFalse(src_ah.size() == 0);  //just verify we haven't gotten our test data in a twist
		ah = new ArrayRef<String[]>(src_ah);
		org.junit.Assert.assertTrue(ah.equals(src_ah));
		// now the with-copy version
		ah = new ArrayRef<String[]>(src_ah, (n) -> new String[n]);
		verify(ah, 0, src_ah.size(), src_ah.size());
		org.junit.Assert.assertFalse(ah.buffer() == src_ah.buffer());
		org.junit.Assert.assertTrue(src_ah.buffer()[src_ah.offset()] == ah.buffer()[ah.offset()]);
		org.junit.Assert.assertTrue(src_ah.buffer()[src_ah.offset(src_ah.size()-1)] == ah.buffer()[ah.offset(ah.size()-1)]);
	}

	// Note that in general, we can't guarantee that hashcodes will ever be unequal, but we can guarantee when they are equal.
	@org.junit.Test
	public void testEquals()
	{
		String str = "Test Me";
		char[] arr1 = str.toCharArray();
		char[] arr2 = str.toCharArray();
		org.junit.Assert.assertFalse(arr1 == arr2);  //sanity-check how we expect the JDK to work

		// test pointers to same content in different memory buffers
		ArrayRef<char[]> ah1 = new ArrayRef<char[]>(arr1, 0, arr1.length);
		ArrayRef<char[]> ah2 = new ArrayRef<char[]>(arr2, 0, arr1.length);
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// move over to same buffer
		ah1.set(ah2.buffer(), ah1.offset(), ah1.size());
		org.junit.Assert.assertTrue(ah1.equals(ah2));
		org.junit.Assert.assertTrue(ah1.hashCode() == ah2.hashCode());

		// vary the offset
        ah1.set(ah1.buffer(), ah1.offset()+1, ah1.size());
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// vary the length as well
		ah1.incrementSize(1);
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// now vary just the length
        ah1.set(ah1.buffer(), ah2.offset(), ah1.size());
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// miscellaneous
		org.junit.Assert.assertTrue(ah1.equals(ah1));
		org.junit.Assert.assertFalse(ah1.equals(null));
		org.junit.Assert.assertFalse(ah2.equals(arr2));
	}

	public static void verify(ArrayRef<?> ah, int off, int len, int cap)
	{
		if (ah.buffer() == null) {
			org.junit.Assert.assertEquals(0, cap);
		} else {
			org.junit.Assert.assertEquals(cap, java.lang.reflect.Array.getLength(ah.buffer()) - ah.offset());
		}
		org.junit.Assert.assertEquals(off, ah.offset());
		org.junit.Assert.assertEquals(len, ah.size());
	}
}