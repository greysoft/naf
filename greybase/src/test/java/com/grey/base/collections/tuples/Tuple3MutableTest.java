/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class Tuple3MutableTest {
	private final boolean isMT;

	public Tuple3MutableTest() {
		this(false);
	}

	protected Tuple3MutableTest(boolean mt) {
		isMT = mt;
	}

	@Test
	public void testAccessors() {
		Tuple3Mutable<String, Integer, Boolean> tuple = new Tuple3Mutable<>(isMT, "One", 1, false);
		Assert.assertEquals(3, tuple.getDimension());
		String s = tuple.getValue1();
		Integer n = tuple.getValue2();
		Boolean b = tuple.getValue3();
		Assert.assertEquals("One", s);
		Assert.assertEquals(Integer.valueOf(1), n);
		Assert.assertEquals(false, b.booleanValue());

		s = tuple.getValue(0);
		n = tuple.getValue(1);
		b = tuple.getValue(2);
		Assert.assertEquals("One", s);
		Assert.assertEquals(Integer.valueOf(1), n);
		Assert.assertEquals(false, b.booleanValue());

		String prev1 = tuple.setValue1("Two");
		Integer prev2 = tuple.setValue2(2);
		Boolean prev3 = tuple.setValue3(true);
		Assert.assertEquals("Two", tuple.getValue(0));
		Assert.assertEquals(Integer.valueOf(2), tuple.getValue(1));
		Assert.assertEquals(true, tuple.getValue(2));
		Assert.assertEquals("One", prev1);
		Assert.assertEquals(Integer.valueOf(1), prev2);
		Assert.assertEquals(false, prev3.booleanValue());

		prev1 = tuple.setValue(0, "Three");
		prev2 = tuple.setValue(1, 3);
		prev3 = tuple.setValue(2, false);
		Assert.assertEquals("Three", tuple.getValue(0));
		Assert.assertEquals(Integer.valueOf(3), tuple.getValue(1));
		Assert.assertEquals(false, tuple.getValue(2));
		Assert.assertEquals("Two", prev1);
		Assert.assertEquals(Integer.valueOf(2), prev2);
		Assert.assertEquals(true, prev3);

		List<Object> terms = tuple.getValues();
		Assert.assertEquals(3, terms.size());
		Assert.assertEquals("Three", terms.get(0));
		Assert.assertEquals(Integer.valueOf(3), terms.get(1));
		Assert.assertEquals(false, terms.get(2));
	}

	@Test
	public void testNulls() {
		Tuple3Mutable<String, Integer, Boolean> tuple = new Tuple3Mutable<>(isMT, "One", 1, true);
		tuple.setValue1(null);
		tuple.setValue2(null);
		tuple.setValue3(null);
		verifyNulls(tuple);

		tuple = new Tuple3Mutable<>(isMT, "One", 1, true);
		tuple.setValue(0, null);
		tuple.setValue(1, null);
		tuple.setValue(2, null);
		verifyNulls(tuple);

		tuple = new Tuple3Mutable<>(isMT, null, null, null);
		verifyNulls(tuple);

		tuple = new Tuple3Mutable<>();
		verifyNulls(tuple);
	}

	@Test
	public void testEquals() {
		Tuple3Mutable<String, Integer, Boolean> tuple1 = new Tuple3Mutable<>(isMT, "One", 1, true);
		Tuple3Mutable<String, Integer, Boolean> tuple2 = new Tuple3Mutable<>(isMT, "One", 1, true);
		Tuple3Mutable<String, Integer, Boolean> tuple3 = new Tuple3Mutable<>(isMT, "One", 2, true);
		Tuple3Mutable<String, Integer, Boolean> tuple4 = new Tuple3Mutable<>(isMT, "One", 1, false);
		Tuple3Mutable<String, Integer, Boolean> tuple5 = new Tuple3Mutable<>(isMT, "Two", 1, true);
		Assert.assertEquals(tuple1, tuple2);
		Assert.assertNotEquals(tuple1, tuple3);
		Assert.assertNotEquals(tuple1, tuple4);
		Assert.assertNotEquals(tuple1, tuple5);
		Assert.assertNotEquals(tuple3, tuple4);
		Assert.assertNotEquals(tuple3, tuple5);
		Assert.assertNotEquals(tuple4, tuple5);

		Tuple3Mutable<String, Integer, Boolean> null1 = new Tuple3Mutable<>(isMT, null, null, null);
		Tuple3Mutable<String, Integer, Boolean> null2 = new Tuple3Mutable<>();
		verifyNulls(null1);
		verifyNulls(null2);
		Assert.assertEquals(null1, null2);
		Assert.assertNotEquals(tuple1, null1);
	}

	@Test
	public void testOutOfRange() {
		Tuple3Mutable<String, Integer, Boolean> tuple = new Tuple3Mutable<>(isMT, "One", 1, true);
		try {
			Object v = tuple.getValue(-1);
			Assert.fail("Expected Get to fail on negative index - "+v);
		} catch (IllegalArgumentException ex) {}
		try {
			Object v = tuple.getValue(3);
			Assert.fail("Expected Get to fail on large index - "+v);
		} catch (IllegalArgumentException ex) {}

		try {
			tuple.setValue(-1, "Bad");
			Assert.fail("Expected Set to fail on negative index - "+tuple);
		} catch (IllegalArgumentException ex) {}
		try {
			tuple.setValue(3, "Bad");
			Assert.fail("Expected Set to fail on large index - "+tuple);
		} catch (IllegalArgumentException ex) {}
	}

	private static void verifyNulls(Tuple3Mutable<String, Integer, Boolean> tuple) {
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
