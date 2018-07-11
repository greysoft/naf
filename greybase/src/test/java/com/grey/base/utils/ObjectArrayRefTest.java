/*
 * Copyright 2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

public class ObjectArrayRefTest {
	@Test
	public void testArrayConstructor() {
		verifyArrayConstructor(false);
		verifyArrayConstructor(true);
	}

	@Test
	public void testCollectionConstructor() {
		verifyListConstructor(false);
		verifyListConstructor(true);
	}

	@Test
	public void testAllocation() {
		ObjectArrayRef<String> lst = new ObjectArrayRef<>(0, (n) -> new String[n]);
		Assert.assertNull(lst.buffer());
		Assert.assertEquals(0, lst.totalBufferSize());

		lst = new ObjectArrayRef<>(5, (n) -> new String[n]);
		Assert.assertEquals(0, lst.size());
		Assert.assertEquals(5, lst.totalBufferSize());
		Assert.assertEquals(5, Array.getLength(lst.buffer()));

		lst.ensureCapacity(7);
		Assert.assertEquals(0, lst.size());
		Assert.assertEquals(7, lst.totalBufferSize());
		Assert.assertEquals(7, Array.getLength(lst.buffer()));

		// test null allocator
		lst = new ObjectArrayRef<>(0, null);
		Assert.assertEquals(0, lst.size());
		Assert.assertEquals(0, lst.totalBufferSize());
		Assert.assertNull(lst.buffer());
		try {
			lst.ensureCapacity(1);
			Assert.fail("Expected growth to fail without Allocator");
		} catch (UnsupportedOperationException ex) {} //expected
		try {
			lst = new ObjectArrayRef<>(5, null);
			Assert.fail("Expected allocation to fail without Allocator");
		} catch (UnsupportedOperationException ex) {} //expected
	}

	private static void verifyArrayConstructor(boolean subset) {
		String[] src = {"elem0", "elem1", "elem2", "elem3", "elem4"};
		int off = 0;
		int len = src.length;
		ObjectArrayRef<String> ref;
		if (subset) {
			ref = new ObjectArrayRef<>(src, off = 1, len = 2, null);
		} else {
			ref = new ObjectArrayRef<>(src, null);
		}
		Assert.assertEquals(off, ref.offset());
		Assert.assertEquals(src.length - off, ref.capacity());
		Assert.assertEquals(src.length - off - len, ref.spareCapacity());
		verifyConstructor(ref, src.length, src.length, off, len, (n) -> src[n]);
	}

	private static void verifyListConstructor(boolean subset) {
		String[] arr = new String[]{"elem0", "elem1", "elem2", "elem3", "elem4"};
		ObjectArrayRef.Allocator<String[]> allocator = (n) -> new String[n];
		List<String> src = Arrays.asList(arr);
		int off = 0;
		int len = src.size();
		ObjectArrayRef<String> ref;
		if (subset) {
			ref = new ObjectArrayRef<>(src, off = 1, len = 2, allocator);
		} else {
			ref = new ObjectArrayRef<>(src, allocator);
		}
		Assert.assertEquals(ref.toString(), 0, ref.offset());
		Assert.assertEquals(ref.toString(), len, ref.capacity());
		Assert.assertEquals(ref.toString(), 0, ref.spareCapacity());
		verifyConstructor(ref, arr.length, src.size(), off, len, (n) -> src.get(n));
	}

	private static void verifyConstructor(ObjectArrayRef<String> ref, int expectedSrcSize, int srcSize, int off, int len, SourceAccessor srcAccessor) {
		Assert.assertEquals(expectedSrcSize, srcSize); //verify source is unchanged
		Assert.assertEquals(len, ref.size());
		for (int idx = 0; idx != ref.size(); idx++) {
			int idx_src = off + idx;
			String elem = srcAccessor.getSourceElement(idx_src);
			Assert.assertTrue(ref.getElement(idx)+" vs "+elem, ref.getElement(idx) == elem);
			Assert.assertEquals("elem"+idx_src, elem); //verify source is unchanged
		}
	}

	private static interface SourceAccessor {
		String getSourceElement(int idx);
	}
}