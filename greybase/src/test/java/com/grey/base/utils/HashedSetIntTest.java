/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class HashedSetIntTest
{
    private HashedSetInt hset = new HashedSetInt(3, 3);

    @org.junit.Test
    final public void testConstructors()
    {
    	hset = new HashedSetInt();
    	testSize();
    }

    @org.junit.Test
    final public void testSize()
    {
    	int key = 11;
    	verifySize(0);
    	hset.add(key);
    	verifySize(1);
    	hset.toString(); //for sake of code coverage
    	hset.remove(key);
    	verifySize(0);
    }

    @org.junit.Test
    final public void testAdd()
    {
		addEntry(11, true);
		addEntry(11, false);
		addEntry(12, true);
		addEntry(0, true);
		org.junit.Assert.assertEquals(3, hset.size());
    }

    @org.junit.Test
    final public void testRemove()
    {
    	int key = 11;
		addEntry(key, true);
		addEntry(12, true);
		deleteEntry(key, true);
		addEntry(0, true);
		deleteEntry(0, true);
		deleteEntry(0, false);
		deleteEntry(99, false);
    }

    @org.junit.Test
    final public void testClear()
    {
    	hset.clear();
    	verifySize(0);
    	hset.add(1);
    	verifySize(1);
    	hset.clear();
    	verifySize(0);
    	hset.add(2);
    	hset.add(3);
    	verifySize(2);
    	hset.clear();
    	verifySize(0);
    }

    @org.junit.Test
    final public void testIterator()
    {
		hset.add(11);
		hset.add(12);
		hset.add(13);
		hset.add(14);
		hset.add(15);
		int itercnt_exp = hset.size();

		int itercnt = 0;
		boolean found_it = false;
		com.grey.base.utils.IteratorInt iter = hset.iterator();
		while (iter.hasNext())
		{
			int num = iter.next();
			if (num == 12) found_it = true;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);

		iter = hset.iterator();
		itercnt = 0;
		int num = 0;
		while (iter.hasNext())
		{
			num += iter.next();
			iter.remove();
			itercnt++;
		}
    	verifySize(0);
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(num, 11+12+13+14+15);
		iter = hset.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testRecycledIterators()
    {
		hset.add(1);
		hset.add(2);
		hset.add(3);
		int itercnt_exp = hset.size();

		com.grey.base.utils.IteratorInt iter = hset.recycledIterator();
		int itercnt = 0;
		boolean found_it = false;
		while (iter.hasNext()) {
			int num = iter.next();
			if (num == 2) found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		com.grey.base.utils.IteratorInt iter2 = hset.recycledIterator();
		org.junit.Assert.assertSame(iter, iter2);
		org.junit.Assert.assertTrue(iter2.hasNext());
    }

    @org.junit.Test
    final public void testArray()
    {
		hset.add(1);
		hset.add(2);
		hset.add(3);
		int[] arr = hset.toArray();
    	verifySize(3);
		org.junit.Assert.assertEquals(arr.length, hset.size());

		// should be same as above
		arr = hset.toArray(null);
    	verifySize(3);
		org.junit.Assert.assertEquals(arr.length, hset.size());

		// set should still fit in same array
		int[] arr2 = hset.toArray(arr);
    	verifySize(3);
		org.junit.Assert.assertSame(arr, arr2);
		org.junit.Assert.assertEquals(arr2.length, hset.size());

		// set no longer fits in same array
		hset.add(4);
		arr2 = hset.toArray(arr);
    	verifySize(4);
		org.junit.Assert.assertNotSame(arr, arr2);
		org.junit.Assert.assertEquals(arr2.length, hset.size());
    }

    // This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
    @org.junit.Test
    final public void testGrowBucket()
    {
    	hset = new HashedSetInt(2, 512);
    	int cnt = 512;
    	for (int idx = 0; idx != cnt; idx++) {
    		hset.add(idx);
    	}
    	verifySize(cnt);
		org.junit.Assert.assertTrue(hset.contains(0));
		org.junit.Assert.assertTrue(hset.contains(cnt - 1));
    }

    private void addEntry(int key, boolean isnew)
    {
    	int size = hset.size();
    	if (isnew) {
    		org.junit.Assert.assertFalse(hset.contains(key));
    		size++;  // size will increase
    	}
    	boolean wasnew = hset.add(key);
		org.junit.Assert.assertTrue(hset.contains(key));
		org.junit.Assert.assertEquals(isnew, wasnew);
		verifySize(size);
    }

    private void deleteEntry(int key, boolean exists)
    {
    	int size = hset.size();
    	if (exists) size--;  // size will decrease
		boolean existed = hset.remove(key);
		org.junit.Assert.assertFalse(hset.contains(key));
		org.junit.Assert.assertEquals(exists, existed);
    	verifySize(size);
    }

    private void verifySize(int size)
    {
		org.junit.Assert.assertEquals(size, hset.size());
		org.junit.Assert.assertEquals(size == 0, hset.isEmpty());
    }
}
