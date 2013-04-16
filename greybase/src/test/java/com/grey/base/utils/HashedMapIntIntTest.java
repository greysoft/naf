/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class HashedMapIntIntTest
{
    private HashedMapIntInt hmap = new HashedMapIntInt(3, 3);

    @org.junit.Test
    final public void testConstructors()
    {
    	hmap = new HashedMapIntInt();
    	testSize();
    }

    @org.junit.Test
    final public void testSize()
    {
    	int key = 11;
    	verifySize(0);
    	hmap.put(key, key);
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
    	int val = 22;
		org.junit.Assert.assertFalse(hmap.containsValue(val));
		addEntry(key, val, true, 0);
		addEntry(key, 22, false, val);
		addEntry(key2, 23, true, 0);
		addEntry(0, 24, true, 0);
		org.junit.Assert.assertEquals(cap, hmap.bucketCount());
    }

    @org.junit.Test
    final public void testRemove()
    {
    	int key = 11;
    	int val = 22;
    	int val2 = 0;
		addEntry(key, val, true, 0);
		addEntry(12, val, true, 0);
		deleteEntry(key, true, val);
		addEntry(0, val2, true, 0);
		deleteEntry(0, true, val2);
		deleteEntry(0, false, 0);
		deleteEntry(99, false, 0);

		// This is for the sake of code coverage. Want to traverse a bucket to the end without finding a match - so look for deleted key
		hmap.clear();
		for (int idx = 0; idx != 10; idx++) addEntry(idx, idx, true, 0);
		for (int idx = 0; idx != 10; idx++) deleteEntry(idx, true, idx);
		for (int idx = 0; idx != 10; idx++) hmap.get(idx);
		verifySize(0);
    }

    @org.junit.Test
    final public void testClear()
    {
    	hmap.clear();
    	verifySize(0);
    	hmap.put(1, 11);
    	verifySize(1);
    	hmap.clear();
    	verifySize(0);
    	hmap.put(2, 12);
    	hmap.put(3, 12);
    	verifySize(2);
    	hmap.clear();
    	verifySize(0);
    }

    @org.junit.Test
    final public void testKeysView()
    {
		hmap.put(11, 21);
		hmap.put(12, 22);
		hmap.put(13, 23);
		hmap.put(14, 24);
		hmap.put(15, 25);

		int itercnt_exp = hmap.size();
		int itercnt = 0;
		boolean found_it = false;
		com.grey.base.utils.IteratorInt iter = hmap.keysIterator();
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
			org.junit.Assert.assertEquals(num + 10, hmap.get(num));
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
		hmap.put(11, 21);
		hmap.put(12, 22);
		hmap.put(13, 23);
		hmap.put(14, 24);
		hmap.put(15, 25);

		int itercnt_exp = hmap.size();
		int itercnt = 0;
		boolean found_it = false;
		com.grey.base.utils.IteratorInt iter = hmap.valuesIterator();
		while (iter.hasNext())
		{
			int num = iter.next();
			if (num == 22) found_it = true;
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
		org.junit.Assert.assertEquals(sum, 21+22+23+24+25);
		iter = hmap.valuesIterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testGrow()
    {
    	int cap = hmap.bucketCount();
    	int cnt = 0;
    	int key1 = -1;
    	int keylast = -1;

    	while (hmap.bucketCount() == cap)
    	{
    		hmap.put(cnt, cnt+10);
    		if (key1 == -1) key1 = cnt;
    		keylast = cnt;
    		cnt++;
    	}
    	verifySize(cnt);
		org.junit.Assert.assertTrue(hmap.containsKey(key1));
		org.junit.Assert.assertTrue(hmap.containsValue(key1 + 10));
		org.junit.Assert.assertEquals(key1 + 10, hmap.get(key1));
		org.junit.Assert.assertTrue(hmap.containsKey(keylast));
		org.junit.Assert.assertTrue(hmap.containsValue(keylast + 10));
		org.junit.Assert.assertEquals(keylast + 10, hmap.get(keylast));

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
		hmap.getBucketStats();
    }

    // This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
    @org.junit.Test
    final public void testGrowBucket()
    {
    	hmap = new HashedMapIntInt(2, 512);
    	int cnt = 512;
    	for (int idx = 0; idx != cnt; idx++)
    	{
    		hmap.put(idx, idx + 10);
    	}
    	verifySize(cnt);
		org.junit.Assert.assertTrue(hmap.containsKey(0));
		org.junit.Assert.assertTrue(hmap.containsKey(cnt - 1));
		org.junit.Assert.assertTrue(hmap.containsValue(10));
		org.junit.Assert.assertTrue(hmap.containsValue(cnt + 9));
    }

    @org.junit.Test
    final public void bulktest()
    {
    	final int cap = 10*1000; //ramp up to investigate manually
    	final int incr = 1000;
        HashedMapIntInt map = new HashedMapIntInt(0, 5);
        // general put-get
        for (int v = 0; v != cap; v++) org.junit.Assert.assertEquals(0, map.put(v, v+incr));
		org.junit.Assert.assertEquals(cap, map.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertEquals(v+incr, map.get(v));
        // iterators
		java.util.HashSet<Integer> jset = new java.util.HashSet<Integer>();
        IteratorInt it = map.keysIterator();
		while (it.hasNext()) {
			jset.add(it.next());
			it.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(cap, jset.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(jset.contains(v));
    }

    private int addEntry(int key, int val, boolean isnew, int oldval_exp)
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

    private int deleteEntry(int key, boolean exists, int val)
    {
    	int size = hmap.size();
    	if (exists) size--;
		int delval = hmap.remove(key);
		org.junit.Assert.assertFalse(hmap.containsKey(key));
		org.junit.Assert.assertEquals(delval, val);
    	verifySize(size);
		return delval;
    }

    private void verifySize(int size)
    {
		org.junit.Assert.assertEquals(size, hmap.size());
		org.junit.Assert.assertEquals(size == 0, hmap.isEmpty());
    }
}
