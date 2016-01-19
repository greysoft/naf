/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class ArrayRefTest
{
	@org.junit.Test
	public void testConstructors_Object()
	{
		String[] src_arr = new String[]{"One", "Two", "Three", "Four", "Five"};
		int cap = 10;
		int off = 1;
		int len;

		// test minimal constructor
		ArrayRef<String[]> ah = new ArrayRef<String[]>(String.class, -1);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);

		ah = new ArrayRef<String[]>(String.class, cap);
		verify(ah, 0, 0, cap);

		// test array constructor
		ah = new ArrayRef<String[]>(src_arr);
		verify(ah, 0, src_arr.length, src_arr.length);
		org.junit.Assert.assertTrue(ah.ar_buf == src_arr);
		org.junit.Assert.assertTrue(src_arr[0] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_arr[src_arr.length-1] == ah.ar_buf[ah.ar_off+src_arr.length-1]);
		// now the with-copy version
		ah = new ArrayRef<String[]>(src_arr, true);
		verify(ah, 0, src_arr.length, src_arr.length);
		org.junit.Assert.assertFalse(ah.ar_buf == src_arr);
		org.junit.Assert.assertTrue(src_arr[0] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_arr[src_arr.length-1] == ah.ar_buf[ah.ar_off+src_arr.length-1]);

		ah = new ArrayRef<String[]>(src_arr, off, len = src_arr.length - off - 1, false);
		verify(ah, off, len, len + 1);
		org.junit.Assert.assertTrue(ah.ar_buf == src_arr);

		// test ArrayRef constructor
		ArrayRef<String[]> src_ah = ah;
		src_ah.advance(1);
		org.junit.Assert.assertFalse(src_ah.size() == 0);  //just verify we haven't gotten our test data in a twist
		ah = new ArrayRef<String[]>(src_ah);
		org.junit.Assert.assertTrue(ah.equals(src_ah));
		// now the with-copy version
		ah = new ArrayRef<String[]>(src_ah, true);
		verify(ah, 0, src_ah.size(), src_ah.size());
		org.junit.Assert.assertFalse(ah.ar_buf == src_ah.ar_buf);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off+src_ah.ar_len-1] == ah.ar_buf[ah.ar_off+ah.ar_len-1]);

		src_ah = new ArrayRef<String[]>(src_arr);
		ah = new ArrayRef<String[]>(src_ah, off, len = src_ah.size() - off, false);
		verify(ah, off, len, len);
		org.junit.Assert.assertTrue(ah.ar_buf == src_ah.ar_buf);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off+off] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off+off+ah.ar_len-1] == ah.ar_buf[ah.ar_off+ah.ar_len-1]);

		// test copy-construction from an empty source
		src_ah.advance(1);
		src_ah.truncateBy(src_ah.size());
		ah = new ArrayRef<String[]>(src_ah, true);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);

		// exercise some final cases
		ah = new ArrayRef<String[]>(String.class, 0);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNotNull(ah.ar_buf);
		ah = new ArrayRef<String[]>(String.class, -1);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);
	}

	// This method can be automatically composed by pasting in the above Object version, and replacing all "String" with "byte"
	@org.junit.Test
	public void testConstructors_Primitive()
	{
		byte[] src_arr = new byte[]{11, 12, 13, 14, 15};
		int cap = 10;
		int off = 1;
		int len;

		// test minimal constructor
		ArrayRef<byte[]> ah = new ArrayRef<byte[]>(byte.class, -1);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);

		ah = new ArrayRef<byte[]>(byte.class, cap);
		verify(ah, 0, 0, cap);

		// test array constructor
		ah = new ArrayRef<byte[]>(src_arr);
		verify(ah, 0, src_arr.length, src_arr.length);
		org.junit.Assert.assertTrue(ah.ar_buf == src_arr);
		org.junit.Assert.assertTrue(src_arr[0] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_arr[src_arr.length-1] == ah.ar_buf[ah.ar_off+src_arr.length-1]);
		// now the with-copy version
		ah = new ArrayRef<byte[]>(src_arr, true);
		verify(ah, 0, src_arr.length, src_arr.length);
		org.junit.Assert.assertFalse(ah.ar_buf == src_arr);
		org.junit.Assert.assertTrue(src_arr[0] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_arr[src_arr.length-1] == ah.ar_buf[ah.ar_off+src_arr.length-1]);

		ah = new ArrayRef<byte[]>(src_arr, off, len = src_arr.length - off - 1, false);
		verify(ah, off, len, len + 1);
		org.junit.Assert.assertTrue(ah.ar_buf == src_arr);

		// test ArrayRef constructor
		ArrayRef<byte[]> src_ah = ah;
		src_ah.advance(1);
		org.junit.Assert.assertFalse(src_ah.size() == 0);  //just verify we haven't gotten our test data in a twist
		ah = new ArrayRef<byte[]>(src_ah);
		org.junit.Assert.assertTrue(ah.equals(src_ah));
		// now the with-copy version
		ah = new ArrayRef<byte[]>(src_ah, true);
		verify(ah, 0, src_ah.size(), src_ah.size());
		org.junit.Assert.assertFalse(ah.ar_buf == src_ah.ar_buf);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off+src_ah.ar_len-1] == ah.ar_buf[ah.ar_off+ah.ar_len-1]);

		src_ah = new ArrayRef<byte[]>(src_arr);
		ah = new ArrayRef<byte[]>(src_ah, off, len = src_ah.size() - off, false);
		verify(ah, off, len, len);
		org.junit.Assert.assertTrue(ah.ar_buf == src_ah.ar_buf);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off+off] == ah.ar_buf[ah.ar_off]);
		org.junit.Assert.assertTrue(src_ah.ar_buf[src_ah.ar_off+off+ah.ar_len-1] == ah.ar_buf[ah.ar_off+ah.ar_len-1]);

		// test copy-construction from an empty source
		src_ah.advance(1);
		src_ah.truncateBy(src_ah.size());
		ah = new ArrayRef<byte[]>(src_ah, true);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);

		// exercise some final cases
		ah = new ArrayRef<byte[]>(byte.class, 0);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNotNull(ah.ar_buf);
		ah = new ArrayRef<byte[]>(byte.class, -1);
		verify(ah, 0, 0, 0);
		org.junit.Assert.assertNull(ah.ar_buf);
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
		ArrayRef<char[]> ah1 = new ArrayRef<char[]>(arr1);
		ArrayRef<char[]> ah2 = new ArrayRef<char[]>(arr2);
		org.junit.Assert.assertFalse(ah1.ar_buf == ah2.ar_buf);
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// move over to same buffer
		ah1.ar_buf = ah2.ar_buf;
		org.junit.Assert.assertTrue(ah1.equals(ah2));
		org.junit.Assert.assertTrue(ah1.hashCode() == ah2.hashCode());

		// vary the offset
		ah1.ar_off++;
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// vary the length as well
		ah1.ar_len++;
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// now vary just the length
		ah1.ar_off = ah2.ar_off;
		org.junit.Assert.assertFalse(ah1.equals(ah2));

		// miscellaneous
		org.junit.Assert.assertTrue(ah1.equals(ah1));
		org.junit.Assert.assertFalse(ah1.equals(null));
		org.junit.Assert.assertFalse(ah2.equals(arr2));
		ah1.ar_buf = null;
		org.junit.Assert.assertFalse(ah1.equals(null));
		org.junit.Assert.assertFalse(ah1.hashCode() == ah2.hashCode());
	}

	@org.junit.Test
	public void testScan()
	{
		String str = "Test Me";
		char[] arr = str.toCharArray();
		ArrayRef<char[]> ah = new ArrayRef<char[]>(arr);
		verify(ah, 0, arr.length, arr.length);

		int off = ah.ar_off;
		int incr = 1;
		ah.advance(incr);
		off += incr;
		verify(ah, off, arr.length - off, arr.length - off);
		incr = 2;
		ah.advance(incr);
		off += incr;
		verify(ah, off, arr.length - off, arr.length - off);

		ah.truncateBy(1);
		verify(ah, off, arr.length - off - 1, arr.length - off);
		org.junit.Assert.assertFalse(ah.size() == 0);  //just verify we haven't gotten our test data in a twist
		ah.truncateBy(ah.size());
		verify(ah, off, 0, arr.length - off);
	}

	public static void verify(ArrayRef<?> ah, int off, int len, int cap)
	{
		if (ah.ar_buf == null)
		{
			org.junit.Assert.assertEquals(0, cap);
		}
		else
		{
			org.junit.Assert.assertEquals(cap, java.lang.reflect.Array.getLength(ah.ar_buf) - ah.ar_off);
		}
		org.junit.Assert.assertEquals(off, ah.ar_off);
		org.junit.Assert.assertEquals(len, ah.ar_len);
		org.junit.Assert.assertEquals(cap, ah.capacity());
		org.junit.Assert.assertEquals(ah.ar_len, ah.size());
	}
}
