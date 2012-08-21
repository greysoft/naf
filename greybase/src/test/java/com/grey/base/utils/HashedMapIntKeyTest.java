/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class HashedMapIntKeyTest
{
    private HashedMapIntKey<String> hmap = new HashedMapIntKey<String>(3, 3);

    @org.junit.Test
    final public void testConstructors()
    {
    	hmap = new HashedMapIntKey<String>();
    	testSize();
    }

    @org.junit.Test
    final public void testSize()
    {
    	int key = 11;
    	verifySize(0);
    	hmap.put(key, "val");
    	verifySize(1);
    	hmap.toString(); //for sake of code coverage
    	hmap.remove(key);
    	verifySize(0);
    }

    @org.junit.Test
    final public void testAdd()
    {
    	int cap = hmap.bucketCount();
    	int key = 11;
    	int key2 = 12;
    	String val = "testval";
		org.junit.Assert.assertFalse(hmap.containsValue(val));
		addEntry(key, val, true, null);
		addEntry(key, "val2", false, val);
		addEntry(key2, "val3", true, null);
		addEntry(0, "val4", true, null);
		org.junit.Assert.assertEquals(cap, hmap.bucketCount());
    }

    @org.junit.Test
    final public void testRemove()
    {
    	int key = 11;
    	String val = "testval";
    	String val2 = "zero";
		addEntry(key, val, true, null);
		addEntry(12, val, true, null);
		deleteEntry(key, true, val);
		addEntry(0, val2, true, null);
		deleteEntry(0, true, val2);
		deleteEntry(0, false, null);
		deleteEntry(99, false, null);
    }

    @org.junit.Test
    final public void testNullValue()
    {
		org.junit.Assert.assertFalse(hmap.containsValue(null));
		int key1 = 11;
		int key2 = 12;
		addEntry(key1, null, true, null);
		addEntry(key1, "val2", false, null);
		org.junit.Assert.assertFalse(hmap.containsValue(null));
		addEntry(key2, null, true, null);
		deleteEntry(key2, true, null);
		org.junit.Assert.assertFalse(hmap.containsValue(null));
    }

    @org.junit.Test
    final public void testClear()
    {
    	hmap.clear();
    	verifySize(0);
    	hmap.put(1, "1");
    	verifySize(1);
    	hmap.clear();
    	verifySize(0);
    	hmap.put(2, "2");
    	hmap.put(3, "2");
    	verifySize(2);
    	hmap.clear();
    	verifySize(0);
    }

    @org.junit.Test
    final public void testKeysView()
    {
		hmap.put(11, "11");
		hmap.put(12, "12");
		hmap.put(13, "13");
		hmap.put(14, "14");
		hmap.put(15, "15");
		int itercnt_exp = hmap.size();

		com.grey.base.utils.IteratorInt iter = hmap.keysIterator();
		int itercnt = 0;
		boolean found_it = false;
		while (iter.hasNext())
		{
			int num = iter.next();
			if (num == 12) found_it = true;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);

		iter = hmap.keysIterator();
		itercnt = 0;
		int sum = 0;
		while (iter.hasNext())
		{
			int num = iter.next();
			sum += num;
			org.junit.Assert.assertEquals(Integer.toString(num), hmap.get(num));
			iter.remove();
			itercnt++;
		}
    	verifySize(0);
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(sum, 11+12+13+14+15);

		iter = hmap.keysIterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testValuesView()
    {
		hmap.put(11, "11");
		hmap.put(12, "12");
		hmap.put(13, "13");
		hmap.put(14, null);
		hmap.put(15, "15");
		hmap.put(16, "16");
		int itercnt_exp = hmap.size();

		java.util.Iterator<String> iter = hmap.valuesIterator();
		int itercnt = 0;
		boolean found_it1 = false;
		boolean found_it2 = false;
		while (iter.hasNext()) {
			String str = iter.next();
			if (str == "11") found_it1 = true;
			if (str == null) found_it2 = true;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it1);
		org.junit.Assert.assertTrue(found_it2);

		iter = hmap.valuesIterator();
		itercnt = 0;
		while (iter.hasNext()) {
			String str = iter.next();
			org.junit.Assert.assertTrue(hmap.containsValue(str));
			iter.remove();
			itercnt++;
		}
    	verifySize(0);
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);

		iter = hmap.valuesIterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testRecycledIterators()
    {
		hmap.put(1, "1");
		hmap.put(2, "2");
		hmap.put(3, "3");
		int itercnt_exp = hmap.size();

		java.util.Iterator<String> iterv = hmap.recycledValuesIterator();
		int itercnt = 0;
		boolean found_it = false;
		while (iterv.hasNext()) {
			String str = iterv.next();
			if (str == "2") found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		java.util.Iterator<String> iterv2 = hmap.recycledValuesIterator();
		org.junit.Assert.assertSame(iterv, iterv2);
		org.junit.Assert.assertTrue(iterv2.hasNext());

		com.grey.base.utils.IteratorInt iterk = hmap.recycledKeysIterator();
		itercnt = 0;
		found_it = false;
		while (iterk.hasNext())
		{
			int num = iterk.next();
			if (num == 2) found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		com.grey.base.utils.IteratorInt iterk2 = hmap.recycledKeysIterator();
		org.junit.Assert.assertSame(iterk, iterk2);
		org.junit.Assert.assertTrue(iterk2.hasNext());
    }

    @org.junit.Test
    final public void testGrow()
    {
    	int cap = hmap.bucketCount();
    	int cnt = 0;
    	String str1 = null;
    	String strlast = null;

    	while (hmap.bucketCount() == cap)
    	{
    		String str = Integer.toString(cnt);
    		hmap.put(cnt, str);
    		cnt++;
    		if (str1 == null) str1 = str;
    		strlast = str;
    	}
    	verifySize(cnt);
		org.junit.Assert.assertTrue(hmap.containsKey(Integer.parseInt(str1)));
		org.junit.Assert.assertTrue(hmap.containsValue(str1));
		org.junit.Assert.assertSame(str1, hmap.get(Integer.parseInt(str1)));
		org.junit.Assert.assertTrue(hmap.containsKey(Integer.parseInt(strlast)));
		org.junit.Assert.assertTrue(hmap.containsValue(strlast));
		org.junit.Assert.assertSame(strlast, hmap.get(Integer.parseInt(strlast)));

		hmap.remove(2);
		hmap.remove(3);
    	verifySize(cnt - 2);
		int size = hmap.size();
		int newcap = hmap.trimToSize();
    	verifySize(size);
		org.junit.Assert.assertEquals(newcap, hmap.bucketCount());
		org.junit.Assert.assertEquals(newcap, cap);

		newcap = hmap.trimToSize();
    	verifySize(size);
		org.junit.Assert.assertEquals(newcap, hmap.bucketCount());
		org.junit.Assert.assertEquals(newcap, cap);

		// call this just for the sake of code coverage
		hmap.getBucketStats(false, 1);
    }

    // This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
    @org.junit.Test
    final public void testGrowBucket()
    {
    	hmap = new HashedMapIntKey<String>(2, 512);
    	int cnt = 512;
    	for (int idx = 0; idx != cnt; idx++)
    	{
    		String str = Integer.toString(idx);
    		hmap.put(idx, str);
    	}
    	verifySize(cnt);
		org.junit.Assert.assertTrue(hmap.containsKey(0));
		org.junit.Assert.assertTrue(hmap.containsKey(cnt - 1));
		org.junit.Assert.assertTrue(hmap.containsValue("0"));
		org.junit.Assert.assertTrue(hmap.containsValue(Integer.toString(cnt - 1)));
    }

    private String addEntry(int key, String val, boolean isnew, String oldval_exp)
    {
    	int size = hmap.size();
    	if (isnew)
    	{
    		org.junit.Assert.assertNull(hmap.get(key));
    		size++;  // size will increase
    	}
		org.junit.Assert.assertEquals(!isnew, hmap.containsKey(key));
    	String oldval = hmap.put(key, val);
    	String newval = hmap.get(key);

    	if (isnew) {
    		org.junit.Assert.assertSame(oldval, null);
    	} else {
    		org.junit.Assert.assertSame(oldval, oldval_exp);
    	}
		org.junit.Assert.assertSame(newval, val);
		org.junit.Assert.assertTrue(hmap.containsKey(key));
		org.junit.Assert.assertTrue(hmap.containsValue(val));
		verifySize(size);
		return oldval;
    }

    private String deleteEntry(int key, boolean exists, String val)
    {
    	int size = hmap.size();
    	if (exists) size--;
		String delstr = hmap.remove(key);
		org.junit.Assert.assertFalse(hmap.containsKey(key));
		org.junit.Assert.assertSame(delstr, val);
		org.junit.Assert.assertNull(hmap.get(key));
    	verifySize(size);
		return delstr;
    }

    private void verifySize(int size)
    {
		org.junit.Assert.assertEquals(size, hmap.size());
		org.junit.Assert.assertEquals(size == 0, hmap.isEmpty());
    }
}
