/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

public class HashedSetIntTest
{
    private HashedSetInt hset = new HashedSetInt();

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
    	int max = 100;
    	for (int val = 0; val != max; val++) {
    		addEntry(val, true);
    	}
    	for (int val = 0; val != max; val++) {
    		deleteEntry(val, true);
    	}
		deleteEntry(0, false);
		deleteEntry(max - 1, false);
		deleteEntry(max + 1, false);
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
		IteratorInt iter = hset.iterator();
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

		IteratorInt iter = hset.recycledIterator();
		int itercnt = 0;
		boolean found_it = false;
		while (iter.hasNext()) {
			int num = iter.next();
			if (num == 2) found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		IteratorInt iter2 = hset.recycledIterator();
		org.junit.Assert.assertSame(iter, iter2);
		org.junit.Assert.assertTrue(iter2.hasNext());
    }

    @org.junit.Test
    final public void testArray()
    {
		hset.add(1);
		hset.add(10);
		hset.add(20);
		int[] arr = hset.toArray();
    	verifySize(3);
		org.junit.Assert.assertEquals(arr.length, hset.size());
		int sum = 0;
		for (int idx = 0; idx != arr.length; idx++) sum += arr[idx];
		org.junit.Assert.assertEquals(1+10+20, sum);

		// should be same as above
		arr = hset.toArray(null);
    	verifySize(3);
		org.junit.Assert.assertEquals(arr.length, hset.size());
		sum = 0;
		for (int idx = 0; idx != arr.length; idx++) sum += arr[idx];
		org.junit.Assert.assertEquals(1+10+20, sum);

		// set should still fit in same array
		java.util.Arrays.fill(arr, 0);
		int[] arr2 = hset.toArray(arr);
    	verifySize(3);
		org.junit.Assert.assertTrue(arr2 == arr);
		org.junit.Assert.assertEquals(arr2.length, hset.size());
		sum = 0;
		for (int idx = 0; idx != arr.length; idx++) sum += arr[idx];
		org.junit.Assert.assertEquals(1+10+20, sum);

		// set no longer fits in same array
		hset.add(40);
		arr2 = hset.toArray(arr);
    	verifySize(4);
		org.junit.Assert.assertFalse(arr2 == arr);
		org.junit.Assert.assertEquals(arr2.length, hset.size());
		sum = 0;
		for (int idx = 0; idx != arr2.length; idx++) sum += arr2[idx];
		org.junit.Assert.assertEquals(1+10+20+40, sum);
    }

    @org.junit.Test
    final public void testGrow()
    {
    	hset = new HashedSetInt(2, 512);
    	final int cap1 = getCapacity(hset);
    	int cnt = 0;
    	int elem1 = -1;
    	int elemlast = -1;

    	while (getCapacity(hset) == cap1) {
    		hset.add(++cnt);
    		if (elem1 == -1) elem1 = cnt;
    		elemlast = cnt;
    	}
    	int cap2 = getCapacity(hset);
		org.junit.Assert.assertEquals(cap2, hset.trimToSize());
		org.junit.Assert.assertEquals(cap2, getCapacity(hset));
		org.junit.Assert.assertTrue(hset.contains(elem1));
		org.junit.Assert.assertTrue(hset.contains(elemlast));
		hset.clear();
		org.junit.Assert.assertEquals(1, hset.trimToSize());
		org.junit.Assert.assertEquals(1, getCapacity(hset));
    }

    @org.junit.Test
    final public void bulktest()
    {
    	java.text.DecimalFormat formatter = new java.text.DecimalFormat("#,###");
    	long total_duration = 0;
    	long[] duration = new long[5];
    	int warmuploops = 20;
    	for (int loop = 0; loop != warmuploops + duration.length; loop++) {
    		long time1 = System.nanoTime();
    		runSoak();
    		if (loop < warmuploops) continue;
    		int idx = loop - warmuploops;
    		duration[idx] = System.nanoTime() - time1;
    		total_duration += duration[idx];
    	}
    	System.out.print("HashedSetInt bulktest: ");
    	String dlm = "";
    	for (int loop = 0; loop != duration.length; loop++) {
    		System.out.print(dlm+formatter.format(duration[loop]));
    		dlm = ", ";
    	}
    	System.out.println(" - Avg="+formatter.format(total_duration/duration.length));
    }

    private void runSoak()
    {
    	final int cap = 10*1000;
    	HashedSetInt set = new HashedSetInt(0, 5);
        // general put-get
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(set.add(v));
		org.junit.Assert.assertEquals(cap, set.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertFalse(set.add(v));
		org.junit.Assert.assertEquals(cap, set.size());
		org.junit.Assert.assertFalse(set.remove(cap+1));
		org.junit.Assert.assertEquals(cap, set.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(set.contains(v));
        // iterators
		java.util.HashSet<Integer> jset = new java.util.HashSet<Integer>();
		IteratorInt it = set.iterator();
		while (it.hasNext()) {
			jset.add(it.next());
			it.remove();
		}
		org.junit.Assert.assertEquals(0, set.size());
		org.junit.Assert.assertEquals(cap, jset.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(jset.contains(v));
        org.junit.Assert.assertFalse(jset.contains(null));
        for (int v = 0; v != cap; v++) org.junit.Assert.assertFalse(set.contains(v));
        //restore and remove
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(set.add(v));
		org.junit.Assert.assertEquals(cap, set.size());
		org.junit.Assert.assertEquals(cap, jset.size());
        for (int v = 0; v != cap; v++) {
        	org.junit.Assert.assertTrue(set.contains(v));
        	org.junit.Assert.assertTrue(set.remove(v));
        	org.junit.Assert.assertFalse(set.contains(v));
        	org.junit.Assert.assertFalse(set.remove(v));
        	org.junit.Assert.assertFalse(set.contains(v));
        }
		org.junit.Assert.assertEquals(0, set.size());
		org.junit.Assert.assertEquals(cap, jset.size());
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

    private static int getCapacity(HashedSetInt hset)
    {
    	HashedMapIntKey<?> map = (HashedMapIntKey<?>)com.grey.base.utils.DynLoader.getField(hset, "map");
    	return Integer.class.cast(com.grey.base.utils.DynLoader.getField(map, "capacity")).intValue();
    }
}
