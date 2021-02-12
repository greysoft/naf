/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class Tuple3ImmutableTest {
	@Test
	public void testAccessors() {
		Tuple3Immutable<String, Integer, Boolean> tuple = new Tuple3Immutable<>("One", 1, true);
		Assert.assertEquals(3, tuple.getDimension());
		String s = tuple.getValue1();
		Integer n = tuple.getValue2();
		Boolean b = tuple.getValue3();
		Assert.assertEquals("One", s);
		Assert.assertEquals(Integer.valueOf(1), n);
		Assert.assertEquals(true, b);

		List<Object> terms = tuple.getValues();
		Assert.assertEquals(3, terms.size());
		Assert.assertEquals("One", terms.get(0));
		Assert.assertEquals(Integer.valueOf(1), terms.get(1));
		Assert.assertEquals(true, terms.get(2));
	}

	@Test
	public void testNulls() {
		Tuple3Immutable<String, Integer, Boolean> tuple = new Tuple3Immutable<>(null, null, null);
		verifyNulls(tuple);
	}

	@Test
	public void testEquals() {
		Tuple3Immutable<String, Integer, Boolean> tuple1 = new Tuple3Immutable<>("One", 1, true);
		Tuple3Immutable<String, Integer, Boolean> tuple2 = new Tuple3Immutable<>("One", 1, true);
		Tuple3Immutable<String, Integer, Boolean> tuple3 = new Tuple3Immutable<>("One", 2, true);
		Tuple3Immutable<String, Integer, Boolean> tuple4 = new Tuple3Immutable<>("One", 1, false);
		Tuple3Immutable<String, Integer, Boolean> tuple5 = new Tuple3Immutable<>("Two", 1, true);
		Assert.assertEquals(tuple1, tuple2);
		Assert.assertNotEquals(tuple1, tuple3);
		Assert.assertNotEquals(tuple1, tuple4);
		Assert.assertNotEquals(tuple1, tuple5);
		Assert.assertNotEquals(tuple3, tuple4);
		Assert.assertNotEquals(tuple3, tuple5);
		Assert.assertNotEquals(tuple4, tuple5);

		Tuple3Immutable<String, Integer, Boolean> null1 = new Tuple3Immutable<>(null, null, null);
		Tuple3Immutable<String, Integer, Boolean> null2 = new Tuple3Immutable<>(null, null, null);
		verifyNulls(null1);
		Assert.assertEquals(null1, null2);
		Assert.assertNotEquals(tuple1, null1);
	}

	@Test
	public void testOutOfRange() {
		Tuple3Immutable<String, Integer, Boolean> tuple = new Tuple3Immutable<>("One", 1, true);
		try {
			Object v = tuple.getValue(-1);
			Assert.fail("Expected Get to fail on negative index - "+v);
		} catch (IllegalArgumentException ex) {}
		try {
			Object v = tuple.getValue(3);
			Assert.fail("Expected Get to fail on large index - "+v);
		} catch (IllegalArgumentException ex) {}
	}

	@Test
	public void testNonMutable() {
		Tuple3Immutable<String, Integer, Boolean> tuple = new Tuple3Immutable<>("One", 1, true);
		try {
			tuple.setValue(1, "Two");
		} catch (UnsupportedOperationException ex) {}
		Assert.assertEquals("One", tuple.getValue1());

		try {
			tuple.setValue(2, 2);
		} catch (UnsupportedOperationException ex) {}
		Assert.assertEquals(Integer.valueOf(1), tuple.getValue2());

		try {
			tuple.setValue(3, null);
		} catch (UnsupportedOperationException ex) {}
		Assert.assertEquals(true, tuple.getValue3());
	}

	private static void verifyNulls(Tuple3Immutable<String, Integer, Boolean> tuple) {
		String s = tuple.getValue1();
		Integer n = tuple.getValue2();
		Boolean b = tuple.getValue3();
		Assert.assertNull(s);
		Assert.assertNull(n);
		Assert.assertNull(b);

		List<Object> terms = tuple.getValues();
		Assert.assertEquals(3, terms.size());
		Assert.assertNull(terms.get(0));
		Assert.assertNull(terms.get(1));
		Assert.assertNull(terms.get(2));
	}
}
