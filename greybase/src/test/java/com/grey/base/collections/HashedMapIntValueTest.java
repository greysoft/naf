/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

public class HashedMapIntValueTest
{
	private static final int SOAK_SIZE = 10*1000;
	private static final int SOAK_RUNS = 5;
	private static final int SOAK_WARMUPS = 20;

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
    	hmap = new HashedMapIntValue<String>();
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
    	hmap = new HashedMapIntValue<String>();
		org.junit.Assert.assertEquals(0, hmap.get("nosuchkey"));
		org.junit.Assert.assertEquals(0, hmap.get(null));
    	int cap = bucketcount(hmap);
    	String key = "testkey";
    	String key2 = "key2";
    	int val = 11;
    	int val2 = 12;
    	int val3 = 0;
		org.junit.Assert.assertFalse(hmap.containsValue(val));
		addEntry(key, val, true, 0);
		addEntry(key, val2, false, val);
		addEntry(new String(key), val2, false, val2);
		addEntry(key2, val3, true, 0);
		org.junit.Assert.assertEquals(cap, bucketcount(hmap));
		
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
    	hmap = new HashedMapIntValue<String>();
    	String key = "testkey";
    	int val = 11;
		addEntry(key, val, true, 0);
		addEntry("key2", val, true, 0);
		org.junit.Assert.assertEquals(0, hmap.remove(null));
		org.junit.Assert.assertEquals(0, hmap.remove("nosuchkey"));
		org.junit.Assert.assertEquals(2, hmap.size());
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
    	hmap = new HashedMapIntValue<String>();
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
    	hmap = new HashedMapIntValue<String>();
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
    	hmap = new HashedMapIntValue<String>();
    	int nullval = 50;
		hmap.put(null, nullval);
		hmap.put("11", 11);
		hmap.put("12", 12);
		hmap.put("13", 13);
		hmap.put("14", 14);
		hmap.put("15", 15);
		int sum_values = 11+12+13+14+15+50;

		String delkey = "13";
		int itercnt_exp = hmap.size();
		int itercnt = 0;
		int sum = 0;
		boolean found_null = false;
		java.util.Iterator<String> iter = hmap.keysIterator();
        try {
        	iter.remove();
        	org.junit.Assert.fail("Failed to trap Iterator remove() before next() on Keys");
        } catch (IllegalStateException ex) {}
		while (iter.hasNext()) {
			String str = iter.next();
			sum += hmap.get(str);
			if (str == null) found_null = true;
			if (str == delkey) iter.remove();
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Failed to trap extra next() on Keys");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(sum, sum_values);
		org.junit.Assert.assertTrue(found_null);
		org.junit.Assert.assertFalse(hmap.containsKey(delkey));
		org.junit.Assert.assertEquals(0, hmap.put(delkey, Integer.parseInt(delkey)));
		org.junit.Assert.assertTrue(hmap.containsKey(delkey));
		verifySize(itercnt_exp);

		itercnt = 0;
		sum = 0;
		found_null = false;
		iter = hmap.keysIterator();
		while (iter.hasNext()) {
			String str = iter.next();
			int val = hmap.get(str);
			if (str == null) {
				org.junit.Assert.assertEquals(nullval, val);
				found_null = true;
			} else {
				org.junit.Assert.assertEquals(Integer.parseInt(str), val);
			}
			sum += val;
			iter.remove();
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
    	verifySize(0);
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(sum, sum_values);
		org.junit.Assert.assertTrue(found_null);
   
    	iter = hmap.keysIterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testValuesView()
    {
    	hmap = new HashedMapIntValue<String>();
		hmap.put("11", 11);
		hmap.put("12", 12);
		hmap.put("13", 13);
		hmap.put("14", 14);
		hmap.put("15", 15);
		int sum_values = 11+12+13+14+15;

		int itercnt_exp = hmap.size();
		int itercnt = 0;
		int sum = 0;
		IteratorInt iter = hmap.valuesIterator();
        try {
        	iter.remove();
        	org.junit.Assert.fail("Failed to trap Iterator remove() before next() on Valus");
        } catch (IllegalStateException ex) {}
		while (iter.hasNext()) {
			int num = iter.next();
			sum += num;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
    	verifySize(itercnt_exp);
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(sum, sum_values);

		itercnt = 0;
		sum = 0;
		iter = hmap.valuesIterator();
		while (iter.hasNext()) {
			int num = iter.next();
			sum += num;
			iter.remove();
			itercnt++;
		}
    	verifySize(0);
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(sum, sum_values);

    	iter = hmap.valuesIterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testRecycledIterators()
    {
    	hmap = new HashedMapIntValue<String>();
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

		IteratorInt iterv = hmap.recycledValuesIterator();
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
		IteratorInt iterk2 = hmap.recycledValuesIterator();
		org.junit.Assert.assertSame(iterv, iterk2);
		org.junit.Assert.assertTrue(iterk2.hasNext());
    }

    @org.junit.Test
    final public void testGrow()
    {
    	hmap = new HashedMapIntValue<String>();
    	int cap = bucketcount(hmap);
    	int cnt = 0;
    	int nullval = 37;
    	String str1 = null;
    	String strlast = null;

    	while (bucketcount(hmap) == cap)
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
		org.junit.Assert.assertEquals(newcap, bucketcount(hmap));
		org.junit.Assert.assertEquals(newcap, cap);

		org.junit.Assert.assertTrue(hmap.containsKey(null));
		org.junit.Assert.assertTrue(hmap.containsValue(nullval));
		org.junit.Assert.assertEquals(nullval, hmap.get(null));

		hmap.remove(null);
		size--;
		newcap = hmap.trimToSize();
    	verifySize(size);
		org.junit.Assert.assertEquals(newcap, bucketcount(hmap));
		org.junit.Assert.assertEquals(newcap, cap);
    }

    // This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
    @org.junit.Test
    final public void testGrowBucket()
    {
    	hmap = new HashedMapIntValue<String>(2, 256);
    	int cnt = 512;
    	String str1 = null;
    	String strlast = null;
    	for (int idx = 0; idx != cnt; idx++) {
    		String str = Integer.toString(idx);
    		hmap.put(str, Integer.parseInt(str));
    		if (str1 == null) str1 = str;
    		strlast = str;
    	}
    	verifySize(cnt);
		org.junit.Assert.assertTrue(hmap.containsKey("0"));
		org.junit.Assert.assertTrue(hmap.containsKey(Integer.toString(cnt/2)));
		org.junit.Assert.assertTrue(hmap.containsKey(str1));
		org.junit.Assert.assertTrue(hmap.containsKey(strlast));
		org.junit.Assert.assertTrue(hmap.containsValue(0));
		org.junit.Assert.assertTrue(hmap.containsValue(cnt - 1));
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
    	System.out.print("HashedMapIntValue bulktest: ");
    	String dlm = "";
    	for (int loop = 0; loop != duration.length; loop++) {
    		System.out.print(dlm+formatter.format(duration[loop]));
    		dlm = ", ";
    	}
    	System.out.println(" - Avg="+formatter.format(total_duration/duration.length));
    }

    private void runSoak()
    {
    	final int cap = SOAK_SIZE; //ramp up to investigate manually
    	final int valincr = 10 * cap;
        HashedMapIntValue<String> map = new HashedMapIntValue<String>(0, 5);
        // general put-get
        for (int k = 0; k != cap; k++) {
        	String ks = String.valueOf(k);
        	org.junit.Assert.assertEquals(0, map.put(ks, k+valincr));
        	org.junit.Assert.assertEquals(k+valincr, map.put(ks, k+valincr));
        }
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertEquals(0, map.remove(null));
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertEquals(0, map.remove("nosuchkey"));
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertEquals(0, map.put(null, -1));
		org.junit.Assert.assertEquals(cap+1, map.size());
        for (int v = 0; v != cap; v++) {
        	String s = String.valueOf(v);
        	org.junit.Assert.assertEquals(v+valincr, map.get(s));
        	org.junit.Assert.assertTrue(map.containsKey(s));
        	org.junit.Assert.assertFalse(new String(s) == map.getKey(s));
        }
        org.junit.Assert.assertEquals(-1, map.get(null));
        verifySize(cap+1, map);
        // remove
        for (int k = 0; k != cap; k++) {
        	String ks = String.valueOf(k);
        	org.junit.Assert.assertTrue(map.containsKey(ks));
        	org.junit.Assert.assertEquals(k+valincr, map.get(ks));
        	org.junit.Assert.assertEquals(k+valincr, map.remove(ks));
        	org.junit.Assert.assertFalse(map.containsKey(ks));
        	org.junit.Assert.assertEquals(0, map.remove(ks));
        	org.junit.Assert.assertFalse(map.containsKey(ks));
        }
        verifySize(1, map);
    	org.junit.Assert.assertTrue(map.containsKey(null));
    	org.junit.Assert.assertEquals(-1, map.get(null));
    	org.junit.Assert.assertEquals(-1, map.remove(null));
    	org.junit.Assert.assertFalse(map.containsKey(null));
    	org.junit.Assert.assertEquals(0, map.remove(null));
    	org.junit.Assert.assertFalse(map.containsKey(null));
        verifySize(0, map);
        // iterators
        for (int k = 0; k != cap; k++) org.junit.Assert.assertEquals(0, map.put(String.valueOf(k), k+valincr));
		org.junit.Assert.assertEquals(0, map.put(null, -1));
		java.util.HashSet<String> jset = new java.util.HashSet<String>();
		java.util.Iterator<String> it = map.keysIterator();
		while (it.hasNext()) {
			jset.add(it.next());
			it.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(cap+1, jset.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(jset.contains(String.valueOf(v)));
        org.junit.Assert.assertTrue(jset.contains(null));
        verifySize(0, map);
    }

    private int addEntry(String key, int val, boolean isnew, int oldval_exp)
    {
    	int size = hmap.size();
		if (isnew) {
			size++;  // size will increase
			org.junit.Assert.assertEquals(0, hmap.get(key));
			org.junit.Assert.assertNull(hmap.getKey(key));
		}
		org.junit.Assert.assertEquals(!isnew, hmap.containsKey(key));
    	int oldval = hmap.put(key, val);
    	int newval = hmap.get(key);

    	if (!isnew) {
    		org.junit.Assert.assertEquals(oldval, oldval_exp);
			org.junit.Assert.assertEquals(key, hmap.getKey(key));
    	} else {
			org.junit.Assert.assertTrue(key == hmap.getKey(key));
    	}
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
		org.junit.Assert.assertEquals(0, hmap.get(key));
		if (exists) org.junit.Assert.assertEquals(delval, val);
    	verifySize(size);
		return delval;
    }

    private void verifySize(int size)
    {
    	verifySize(size, hmap);
    }

    private void verifySize(int size, HashedMapIntValue<?> map)
    {
		org.junit.Assert.assertEquals(size, map.size());
		org.junit.Assert.assertEquals(size == 0, map.isEmpty());
    	Object[][] keytbl = (Object[][])com.grey.base.utils.DynLoader.getField(map, "keytbl");
    	int[][] valtbl = (int[][])com.grey.base.utils.DynLoader.getField(map, "valtbl");
    	int[] bucketsizes = (int[])com.grey.base.utils.DynLoader.getField(map, "bucketsizes");
		org.junit.Assert.assertEquals(bucketcount(map), keytbl.length);
		org.junit.Assert.assertEquals(keytbl.length, valtbl.length);
		org.junit.Assert.assertEquals(keytbl.length, bucketsizes.length);
    	int cnt = 0;
   
    	for (int idx = 0; idx != keytbl.length; idx++) {
    		Object[] bucket = keytbl[idx];
			int cnt2 = bucketsizes[idx];
    		if (bucket == null) {
    			org.junit.Assert.assertEquals(0, cnt2);
    			continue;
    		}
			for (int idx2 = 0; idx2 != bucket.length; idx2++) {
				if (idx2 > cnt2) org.junit.Assert.assertNull(bucket[idx2]);
			}
			cnt += cnt2;
    	}
		org.junit.Assert.assertEquals(size, cnt);
    }

    private int bucketcount(HashedMapIntValue<?> map)
    {
    	return Integer.class.cast(com.grey.base.utils.DynLoader.getField(map, "capacity"));
    }
}
