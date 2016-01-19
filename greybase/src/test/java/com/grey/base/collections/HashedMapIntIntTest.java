/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

public class HashedMapIntIntTest
{
	private static final int SOAK_SIZE = 10*1000;
	private static final int SOAK_RUNS = 5;
	private static final int SOAK_WARMUPS = 20;

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
    	int cap = bucketcount(hmap);
    	int key = 11;
    	int key2 = 12;
    	int val = 22;
		org.junit.Assert.assertFalse(hmap.containsValue(val));
		addEntry(key, val, true, 0);
		addEntry(key, 22, false, val);
		addEntry(key2, 23, true, 0);
		addEntry(0, 24, true, 0);
		org.junit.Assert.assertEquals(cap, bucketcount(hmap));
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

		int delkey = 12;
		org.junit.Assert.assertTrue(hmap.containsKey(delkey));
		int itercnt_exp = hmap.size();
		int itercnt = 0;
		IteratorInt iter = hmap.keysIterator();
        try {
        	iter.remove();
        	org.junit.Assert.fail("Failed to trap Iterator remove() before next() on Keys");
        } catch (IllegalStateException ex) {}
		while (iter.hasNext())
		{
			int num = iter.next();
			if (num == delkey) iter.remove();
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Failed to trap extra next() on Keys");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(itercnt_exp-1, hmap.size());
		org.junit.Assert.assertFalse(hmap.containsKey(delkey));
		org.junit.Assert.assertEquals(0, hmap.put(delkey, delkey+10));
		org.junit.Assert.assertTrue(hmap.containsKey(delkey));

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
		IteratorInt iter = hmap.valuesIterator();
        try {
        	iter.remove();
        	org.junit.Assert.fail("Failed to trap Iterator remove() before next() on Values");
        } catch (IllegalStateException ex) {}
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
    final public void testRecycledIterators()
    {
		hmap.put(1, 11);
		hmap.put(2, 12);
		hmap.put(3, 13);
		int itercnt_exp = hmap.size();

		IteratorInt iterv = hmap.recycledValuesIterator();
		int itercnt = 0;
		boolean found_it = false;
		while (iterv.hasNext()) {
			int v = iterv.next();
			if (v == 12) found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		IteratorInt iterv2 = hmap.recycledValuesIterator();
		org.junit.Assert.assertSame(iterv, iterv2);
		org.junit.Assert.assertTrue(iterv2.hasNext());

		IteratorInt iterk = hmap.recycledKeysIterator();
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
		IteratorInt iterk2 = hmap.recycledKeysIterator();
		org.junit.Assert.assertSame(iterk, iterk2);
		org.junit.Assert.assertTrue(iterk2.hasNext());
    }

    @org.junit.Test
    final public void testArray()
    {
    	hmap.put(1, 101);
    	hmap.put(2, 102);
    	hmap.put(3, 103);
		org.junit.Assert.assertEquals(3, hmap.size());

		int keys[] = hmap.getKeys(null);
		org.junit.Assert.assertEquals(3, keys.length);
		java.util.Arrays.sort(keys);
		org.junit.Assert.assertEquals(1, keys[0]);
		org.junit.Assert.assertEquals(2, keys[1]);
		org.junit.Assert.assertEquals(3, keys[2]);
		int keys2[] = hmap.getKeys(keys);
		org.junit.Assert.assertTrue(keys2 == keys);

		hmap.clear();
		keys2 = hmap.getKeys(keys);
		org.junit.Assert.assertTrue(keys2 == keys);
		keys2 = hmap.getKeys(null);
		org.junit.Assert.assertEquals(0, keys2.length);
    }

    @org.junit.Test
    final public void testGrow()
    {
    	int cap = bucketcount(hmap);
    	int cnt = 0;
    	int key1 = -1;
    	int keylast = -1;

    	while (bucketcount(hmap) == cap)
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
		org.junit.Assert.assertEquals(newcap, bucketcount(hmap));
		org.junit.Assert.assertEquals(newcap, cap);

		newcap = hmap.trimToSize();
    	verifySize(size);
		org.junit.Assert.assertEquals(newcap, bucketcount(hmap));
		org.junit.Assert.assertEquals(newcap, cap);
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
    	java.text.DecimalFormat formatter = new java.text.DecimalFormat("#,###");
    	long total_duration = 0;
    	long[] duration = new long[SOAK_RUNS];
    	int warmuploops = SOAK_WARMUPS;
    	for (int loop = 0; loop != warmuploops + duration.length; loop++) {
    		long time1 = System.nanoTime();
    		runSoak();
    		if (loop < warmuploops) continue;
    		int idx = loop - warmuploops;
    		duration[idx] = System.nanoTime() - time1;
    		total_duration += duration[idx];
    	}
    	System.out.print("HashedMapIntInt bulktest: ");
    	String dlm = "";
    	for (int loop = 0; loop != duration.length; loop++) {
    		System.out.print(dlm+formatter.format(duration[loop]));
    		dlm = ", ";
    	}
    	System.out.println(" - Avg="+formatter.format(total_duration/duration.length));
    }

    private void runSoak()
    {
    	final int cap = SOAK_SIZE;
    	final int incr = 2 * cap;
        HashedMapIntInt map = new HashedMapIntInt(0, 5);
        // general put-get
        for (int v = 0; v != cap; v++) org.junit.Assert.assertEquals(0, map.put(v, v+incr));
        verifySize(cap, map);
        for (int v = 0; v != cap; v++) {
        	org.junit.Assert.assertTrue(map.containsKey(v));
        	org.junit.Assert.assertEquals(v+incr, map.get(v));
        	org.junit.Assert.assertEquals(v+incr, map.remove(v));
        	org.junit.Assert.assertFalse(map.containsKey(v));
        	org.junit.Assert.assertEquals(0, map.remove(v));
        	org.junit.Assert.assertFalse(map.containsKey(v));
        }
        verifySize(0, map);
        // slow linear scan for values
        for (int v = 0; v != cap; v++) org.junit.Assert.assertEquals(0, map.put(v, v+incr));
        for (int v = incr; v != incr+10; v++) org.junit.Assert.assertTrue(map.containsValue(v));
        for (int v = cap + incr - 10; v != cap + incr; v++) org.junit.Assert.assertTrue(map.containsValue(v));
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
		if (isnew) org.junit.Assert.assertEquals(0, hmap.get(key));
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
		org.junit.Assert.assertEquals(0, hmap.get(key));
		org.junit.Assert.assertEquals(delval, val);
    	verifySize(size);
		return delval;
    }

    private void verifySize(int size)
    {
    	verifySize(size, hmap);
    }

    private void verifySize(int size, HashedMapIntInt map)
    {
		org.junit.Assert.assertEquals(size, map.size());
		org.junit.Assert.assertEquals(size == 0, map.isEmpty());
    	int[][] buckets = (int[][])com.grey.base.utils.DynLoader.getField(map, "buckets");
		org.junit.Assert.assertEquals(bucketcount(map), buckets.length);
    	int cnt = 0;
   
    	for (int idx = 0; idx != buckets.length; idx++) {
    		int[] bucket = buckets[idx];
    		if (bucket == null) continue;
			int cnt2 = bucket[0] + 1;
			if (cnt2 % 2 != 0) org.junit.Assert.fail("Uneven Key/Value counts - "+cnt2+"/"+bucket.length);
			cnt += (cnt2 / 2);
    	}
		org.junit.Assert.assertEquals(size, cnt);
    }

    private int bucketcount(HashedMapIntInt map)
    {
    	return Integer.class.cast(com.grey.base.utils.DynLoader.getField(map, "capacity"));
    }
}
