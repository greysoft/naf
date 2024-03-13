/*
 * Copyright 2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ByteArrayRefTest {
	@Test
	public void testByteArrayRefToString() {
		String str = "abcde";
		byte[] arr = str.getBytes(StandardCharsets.UTF_8);
		assertEquals(str.length(), arr.length);

		ByteArrayRef bar = new ByteArrayRef(arr);
		assertEquals(str, bar.toString(null).toString());

		bar = new ByteArrayRef(arr, 1, str.length() - 2);
		assertEquals("bcd", bar.toString(null).toString());

		bar = new ByteArrayRef(arr, 1, str.length() - 2);
		assertEquals("c", bar.toString(null, 1, 1).toString());
		bar = new ByteArrayRef(arr, 1, str.length() - 2);
		assertEquals("cd", bar.toString(null, 1, 2).toString());
		bar = new ByteArrayRef(arr, 1, str.length() - 2);
		assertEquals("d", bar.toString(null, 2, 1).toString());
		bar = new ByteArrayRef(arr, 1, str.length() - 2);
		assertEquals("", bar.toString(null, 2, 0).toString());
		try {
			CharSequence s = bar.toString(null, 3, 1);
			fail("Expected to fail - "+s);
		} catch (ArrayIndexOutOfBoundsException ex) {} //expected
		
		bar = new ByteArrayRef(new byte[0]);
		assertTrue(bar.toString(null).toString().isEmpty());
	}
}
