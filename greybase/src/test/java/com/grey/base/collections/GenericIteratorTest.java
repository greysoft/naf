/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

public class GenericIteratorTest
{
	private static final int COLLSIZE = 3;
	private GenericIterator<String> iter;
	private String single = "SingleItem";
	private String[] arr = new String[]{"Arr1", "Arr2", "Arr3"};
	private java.util.ArrayList<String> lst = new java.util.ArrayList<String>();
	private java.util.Map<String, Integer> map = new HashedMap<String, Integer>();

	@org.junit.Before
	public void init()
	{
		lst.add("List1");
		lst.add("List2");
		lst.add("List3");
		map.put("Key1", 1);
		map.put("Key2", 2);
		map.put("Key3", 3);
		iter = new GenericIterator<String>();
		org.junit.Assert.assertFalse(iter.hasNext());
	}

	@org.junit.Test
	public void testSingleFirst()
	{
		testSingle();
		testList();
		testArray();
		testMap();
		testSingle();
	}

	@org.junit.Test
	public void testArrayFirst()
	{
		testArray();
		testList();
		testSingle();
		testMap();
		testArray();
	}

	@org.junit.Test
	public void testListFirst()
	{
		testList();
		testMap();
		testArray();
		testSingle();
		testList();
	}

	@org.junit.Test
	public void testMapFirst()
	{
		testMap();
		testList();
		testSingle();
		testArray();
		testMap();
	}

	private void testSingle()
	{
		iter.reset(single);
		org.junit.Assert.assertTrue(iter.hasNext());
		String str = iter.next();
		org.junit.Assert.assertTrue(str == single);
		org.junit.Assert.assertFalse(iter.hasNext());
		try {
			iter.next();
			org.junit.Assert.fail("Failed to detect excess Single iteration");
		} catch (java.util.NoSuchElementException ex) {}
	}

	private void testArray()
	{
		iter.reset(arr);
		for (int idx = 0; idx != arr.length; idx++)
		{
			org.junit.Assert.assertTrue(iter.hasNext());
			String str = iter.next();
			org.junit.Assert.assertTrue(str == arr[idx]);
		}
		org.junit.Assert.assertFalse(iter.hasNext());
		try {
			iter.next();
			org.junit.Assert.fail("Failed to detect excess Array iteration");
		} catch (java.util.NoSuchElementException ex) {}
	}

	private void testList()
	{
		int size = lst.size();
		org.junit.Assert.assertEquals(COLLSIZE, size);
		iter.reset(lst);

		for (int idx = 0; idx != size; idx++)
		{
			org.junit.Assert.assertTrue(iter.hasNext());
			String str = iter.next();
			org.junit.Assert.assertTrue(str == lst.get(idx));
		}
		org.junit.Assert.assertFalse(iter.hasNext());
		org.junit.Assert.assertEquals(size, lst.size());
		try {
			iter.next();
			org.junit.Assert.fail("Failed to detect excess List iteration");
		} catch (java.util.NoSuchElementException ex) {}
	}

	private void testMap()
	{
		int size = map.size();
		org.junit.Assert.assertEquals(COLLSIZE, size);
		java.util.Map<String, Integer> unseen = new java.util.HashMap<String, Integer>();
		unseen.putAll(map);
		org.junit.Assert.assertEquals(size, unseen.size());
		org.junit.Assert.assertEquals(size, map.size());
		iter.reset(map);

		for (int idx = 0; idx != size; idx++)
		{
			org.junit.Assert.assertTrue(iter.hasNext());
			String str = iter.next();
			unseen.remove(str);
		}
		org.junit.Assert.assertFalse(iter.hasNext());
		org.junit.Assert.assertEquals(0, unseen.size());
		org.junit.Assert.assertEquals(size, map.size());
		try {
			iter.next();
			org.junit.Assert.fail("Failed to detect excess Map iteration");
		} catch (java.util.NoSuchElementException ex) {}
	}
}
