/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class HashedMapIntValueTest
{
    private HashedMapIntValue<String> hmap = new HashedMapIntValue<String>(3, 3);

    @org.junit.Test
    final public void testConstructors()
    {
    	hmap = new HashedMapIntValue<String>();
    	testSize();
    	hmap.toString(); //for sake of code coverage
    }

    @org.junit.Test
    final public void testSize()
    {
    	String key = "testkey";
    	verifySize(0);
    	hmap.put(key, 11);
    	verifySize(1);
    	hmap.toString(); //for sake of code coverage
    	hmap.remove(key);
    	verifySize(0);
    }

    @org.junit.Test
    final public void testAdd()
    {
    	int cap = hmap.bucketCount();
    	String key = "testkey";
    	String key2 = "key2";
    	int val = 11;
    	int val2 = 12;
    	int val3 = 0;
		org.junit.Assert.assertFalse(hmap.containsValue(val));
		addEntry(key, val, true, 0);
		addEntry(key, val2, false, val);
		addEntry(key2, val3, true, 0);
		org.junit.Assert.assertEquals(cap, hmap.bucketCount());
		
		// test keys which are equal but not identical
		int size = hmap.size();
		String key2b = new String(key2);
		org.junit.Assert.assertNotSame(key2, key2b);
		org.junit.Assert.assertSame(hmap.get(key2), hmap.get(key2b));
		int val3b = hmap.put(key2b, 22);
		verifySize(size);
		org.junit.Assert.assertEquals(hmap.get(key2), hmap.get(key2b));
		org.junit.Assert.assertEquals(val3, val3b);
    }

    @org.junit.Test
    final public void testRemove()
    {
    	String key = "testkey";
    	int val = 11;
		addEntry(key, val, true, 0);
		addEntry("key2", val, true, 0);
		deleteEntry(key, true, val);
		deleteEntry("nosuchkey", false, 0);

		// This is for the sake of code coverage. Want to traverse a bucket to the end without finding a match - so look for deleted key
		hmap.clear();
		for (int idx = 0; idx != 10; idx++) addEntry(Integer.toString(idx), idx, true, 0);
		for (int idx = 0; idx != 10; idx++) deleteEntry(Integer.toString(idx), true, idx);
		for (int idx = 0; idx != 10; idx++) hmap.get(Integer.toString(idx));
		verifySize(0);
    }

    @org.junit.Test
    final public void testNullKey()
    {
    	int val = 11;
    	int val2 = 12;
		addEntry(null, val, true, 0);
		addEntry(null, val2, false, val);
		deleteEntry(null, true, val2);
		deleteEntry(null, false, 0);
    }

    @org.junit.Test
    final public void testClear()
    {
    	hmap.clear();
    	verifySize(0);
    	hmap.put("1", 1);
    	verifySize(1);
    	hmap.clear();
    	verifySize(0);
    	hmap.put("2", 2);
    	hmap.put("3", 2);
    	verifySize(2);
    	hmap.clear();
    	verifySize(0);
    }

    @org.junit.Test
    final public void testKeysView()
    {
    	int nullval = 50;
		hmap.put(null, nullval);
		hmap.put("11", 11);
		hmap.put("12", 12);
		hmap.put("13", 13);
		hmap.put("14", 14);
		hmap.put("15", 15);
		
		int itercnt_exp = hmap.size();
		int itercnt = 0;
		boolean found_it = false;
		java.util.Iterator<String> iter = hmap.keysIterator();
		while (iter.hasNext())
		{
			String str = iter.next();
			if (str == "12") found_it = true;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);

		iter = hmap.keysIterator();
		itercnt = 0;
		while (iter.hasNext())
		{
			String str = iter.next();
			if (str == null) {
				org.junit.Assert.assertEquals(nullval, hmap.get(str));
			} else {
				org.junit.Assert.assertEquals(Integer.parseInt(str), hmap.get(str));
			}
			iter.remove();
			itercnt++;
		}
    	verifySize(0);
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
   
    	iter = hmap.keysIterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testValuesView()
    {
		hmap.put("11", 11);
		hmap.put("12", 12);
		hmap.put("13", 13);
		hmap.put("14", 14);
		hmap.put("15", 15);

		int itercnt_exp = hmap.size();
		int itercnt = 0;
		boolean found_it = false;
		com.grey.base.utils.IteratorInt iter = hmap.valuesIterator();
		while (iter.hasNext())
		{
			int num = iter.next();
			if (num == 12) found_it = true;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);

		iter = hmap.valuesIterator();
		itercnt = 0;
		int sum = 0;
		while (iter.hasNext())
		{
			int num = iter.next();
			sum += num;
			iter.remove();
			itercnt++;
		}
    	verifySize(0);
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(sum, 11+12+13+14+15);

    	iter = hmap.valuesIterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testRecycledIterators()
    {
		hmap.put("1", 1);
		hmap.put("2", 2);
		hmap.put("3", 3);
		int itercnt_exp = hmap.size();

		java.util.Iterator<String> iterk = hmap.recycledKeysIterator();
		int itercnt = 0;
		boolean found_it = false;
		while (iterk.hasNext()) {
			String str = iterk.next();
			if (str == "2") found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		java.util.Iterator<String> iterv2 = hmap.recycledKeysIterator();
		org.junit.Assert.assertSame(iterk, iterv2);
		org.junit.Assert.assertTrue(iterv2.hasNext());

		com.grey.base.utils.IteratorInt iterv = hmap.recycledValuesIterator();
		itercnt = 0;
		found_it = false;
		while (iterv.hasNext())
		{
			int num = iterv.next();
			if (num == 2) found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		com.grey.base.utils.IteratorInt iterk2 = hmap.recycledValuesIterator();
		org.junit.Assert.assertSame(iterv, iterk2);
		org.junit.Assert.assertTrue(iterk2.hasNext());
    }

    @org.junit.Test
    final public void testGrow()
    {
    	int cap = hmap.bucketCount();
    	int cnt = 0;
    	int nullval = 37;
    	String str1 = null;
    	String strlast = null;

    	while (hmap.bucketCount() == cap)
    	{
    		String str = Integer.toString(cnt);
    		hmap.put(str, cnt);
    		cnt++;
    		if (str1 == null) str1 = str;
    		strlast = str;
    	}
		hmap.put(null, nullval);
    	verifySize(++cnt);
		org.junit.Assert.assertTrue(hmap.containsKey(str1));
		org.junit.Assert.assertTrue(hmap.containsValue(Integer.parseInt(str1)));
		org.junit.Assert.assertEquals(Integer.parseInt(str1), hmap.get(str1));
		org.junit.Assert.assertTrue(hmap.containsKey(strlast));
		org.junit.Assert.assertTrue(hmap.containsValue(Integer.parseInt(strlast)));
		org.junit.Assert.assertSame(Integer.parseInt(strlast), hmap.get(strlast));

		hmap.remove(Integer.toString(2));
		hmap.remove(Integer.toString(3));
		hmap.remove(Integer.toString(4));
    	verifySize(cnt - 3);
		int size = hmap.size();
		int newcap = hmap.trimToSize();
    	verifySize(size);
		org.junit.Assert.assertEquals(newcap, hmap.bucketCount());
		org.junit.Assert.assertEquals(newcap, cap);

		org.junit.Assert.assertTrue(hmap.containsKey(null));
		org.junit.Assert.assertTrue(hmap.containsValue(nullval));
		org.junit.Assert.assertEquals(nullval, hmap.get(null));

		hmap.remove(null);
		size--;
		newcap = hmap.trimToSize();
    	verifySize(size);
		org.junit.Assert.assertEquals(newcap, hmap.bucketCount());
		org.junit.Assert.assertEquals(newcap, cap);

		// call this just for the sake of code coverage
		hmap.getBucketStats(false, 0);
    }

    // This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
    @org.junit.Test
    final public void testGrowBucket()
    {
    	hmap = new HashedMapIntValue<String>(2, 512);
    	int cnt = 512;
    	for (int idx = 0; idx != cnt; idx++)
    	{
    		String str = Integer.toString(idx);
    		hmap.put(str, Integer.parseInt(str));
    	}
    	verifySize(cnt);
		org.junit.Assert.assertTrue(hmap.containsKey("0"));
		org.junit.Assert.assertTrue(hmap.containsKey(Integer.toString(cnt - 1)));
		org.junit.Assert.assertTrue(hmap.containsValue(0));
		org.junit.Assert.assertTrue(hmap.containsValue(cnt - 1));
    }

    private int addEntry(String key, int val, boolean isnew, int oldval_exp)
    {
    	int size = hmap.size();
    	if (isnew) size++;  // size will increase
		org.junit.Assert.assertEquals(!isnew, hmap.containsKey(key));
    	int oldval = hmap.put(key, val);
    	int newval = hmap.get(key);

    	if (!isnew) org.junit.Assert.assertEquals(oldval, oldval_exp);
		org.junit.Assert.assertEquals(newval, val);
		org.junit.Assert.assertTrue(hmap.containsKey(key));
		org.junit.Assert.assertTrue(hmap.containsValue(val));
		verifySize(size);
		return oldval;
    }

    private int deleteEntry(String key, boolean exists, int val)
    {
    	int size = hmap.size();
    	if (exists) size--;
		int delval = hmap.remove(key);
		org.junit.Assert.assertFalse(hmap.containsKey(key));
		if (exists) org.junit.Assert.assertEquals(delval, val);
    	verifySize(size);
		return delval;
    }

    private void verifySize(int size)
    {
		org.junit.Assert.assertEquals(size, hmap.size());
		org.junit.Assert.assertEquals(size == 0, hmap.isEmpty());
    }
}
