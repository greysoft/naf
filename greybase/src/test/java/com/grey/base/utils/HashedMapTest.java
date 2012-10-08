/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class HashedMapTest
{
    @org.junit.Test
    final public void testSize()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
    	String key = "testkey";
    	verifySize(hmap, 0);
    	hmap.put(key, "val");
    	verifySize(hmap, 1);
    	hmap.toString(); //for sake of code coverage
    	hmap.remove(key);
    	verifySize(hmap, 0);
    	hmap.toString(); //for sake of code coverage
    }

    @org.junit.Test
    final public void testAdd()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
    	int cap = hmap.bucketCount();
    	String key = "testkey";
    	String key2 = "key2";
    	String val = "testval";
    	String val2 = "val2";
    	String val3 = "val3";
		org.junit.Assert.assertFalse(hmap.containsValue(val));
		addEntry(hmap, key, val, true, null);
		addEntry(hmap, key, val, false, val);
		addEntry(hmap, key, val2, false, val);
		addEntry(hmap, key2, val3, true, null);
		org.junit.Assert.assertEquals(cap, hmap.bucketCount());
		
		// test keys which are equal but not identical
		int size = hmap.size();
		String key2b = new String(key2);
		org.junit.Assert.assertNotSame(key2, key2b);
		org.junit.Assert.assertSame(hmap.get(key2), hmap.get(key2b));
		String val3b = hmap.put(key2b, "samekey_newval");
		verifySize(hmap, size);
		org.junit.Assert.assertSame(hmap.get(key2), hmap.get(key2b));
		org.junit.Assert.assertSame(val3, val3b);
    }

    @org.junit.Test
    final public void testRemove()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
    	String key = "testkey";
    	String val = "testval";
		addEntry(hmap, key, val, true, null);
		addEntry(hmap, "key2", val, true, null);
		deleteEntry(hmap, key, true, val);
		deleteEntry(hmap, "nosuchkey", false, null);
    }

    @org.junit.Test
    final public void testNullKey()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
    	String val = "testval";
    	String val2 = "testval2";
		addEntry(hmap, null, val, true, null);
		addEntry(hmap, null, val2, false, val);
		deleteEntry(hmap, null, true, val2);
		deleteEntry(hmap, null, false, null);
    }

    @org.junit.Test
    final public void testNullValue()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
		org.junit.Assert.assertFalse(hmap.containsValue(null));
		String key1 = "key1";
		String key2 = "key2";
		addEntry(hmap, key1, null, true, null);
		addEntry(hmap, key1, "val2", false, null);
		org.junit.Assert.assertFalse(hmap.containsValue(null));
		addEntry(hmap, key2, null, true, null);
		deleteEntry(hmap, key2, true, null);
		org.junit.Assert.assertFalse(hmap.containsValue(null));
    }

    @org.junit.Test
    final public void testClear()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
    	verifySize(hmap, 0);
    	hmap.put("1", "v1");
    	verifySize(hmap, 1);
    	hmap.clear();
    	verifySize(hmap, 0);
    	hmap.put("2", "v2");
    	hmap.put("3", "v2");
    	verifySize(hmap, 2);
    	hmap.clear();
    	verifySize(hmap, 0);
    	hmap.clear();
    	verifySize(hmap, 0);
    }

    @org.junit.Test
    final public void testPutAll()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
		java.util.Map<String,String> map = new java.util.HashMap<String,String>();
        String one = "one";
		String two = "two";
		String three = "three";
        map.put("1", one);
		map.put("2", two);
		map.put("3", three);
    	hmap.putAll(map);
		org.junit.Assert.assertSame(one, hmap.get("1"));
		org.junit.Assert.assertSame(two, hmap.get("2"));
		org.junit.Assert.assertSame(three, hmap.get("3"));
    	verifySize(hmap, 3);

		HashedMap<String,String> map2 = new HashedMap<String,String>();
        String one2 = "one2";
		String two2 = "two2";
        map2.put("7", one2);
		map2.put(null, two2);
    	hmap.putAll(map2);
		org.junit.Assert.assertSame(one2, hmap.get("7"));
		org.junit.Assert.assertSame(two2, hmap.get(null));
    	verifySize(hmap, 5);

    	map2.clear();
    	verifySize(hmap, 5);
    	hmap.putAll(map2);
    	verifySize(hmap, 5);
    }

    @org.junit.Test
    final public void testKeysView()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
        String one = "one";
		String two = "two";
		String three = "three";
		hmap.put(one, one+"v");
		hmap.put(two, two+"v");
		hmap.put(three, three+"v");
		hmap.put("four", "fourv");
		java.util.Set<String> collview = hmap.keySet();
		org.junit.Assert.assertTrue(collview.contains(one));
		org.junit.Assert.assertTrue(collview.contains(two));
		org.junit.Assert.assertTrue(collview.contains(three));
		org.junit.Assert.assertFalse(collview.contains(one + two));
		org.junit.Assert.assertFalse(collview.contains(null));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		hmap.put(null, "nullval");
		collview = hmap.keySet();
		org.junit.Assert.assertTrue(collview.contains(null));
		org.junit.Assert.assertTrue(collview.contains(one));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 5);

		java.util.Iterator<String> iter = collview.iterator();
        try {
        	iter.remove();
        	org.junit.Assert.fail("Failed to trap remove() before next() on Keys");
        } catch (IllegalStateException ex) {}
        iter = collview.iterator();
		int itercnt_exp = collview.size();
		int itercnt = 0;
		boolean found_it1 = false;
		boolean found_it2 = false;
		while (iter.hasNext()) {
			String str = iter.next();
			if (str == one) found_it1 = true;
			if (str == null) found_it2 = true;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Failed to trap extra next() on Keys");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it1);
		org.junit.Assert.assertTrue(found_it2);

		boolean sts = collview.remove(three);
		org.junit.Assert.assertTrue(sts);
		sts = collview.remove("nosuchkey");
		org.junit.Assert.assertFalse(sts);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 4);
		org.junit.Assert.assertFalse(collview.contains(three));
		org.junit.Assert.assertFalse(hmap.containsKey(three));
		org.junit.Assert.assertFalse(hmap.containsValue(three+"v"));

		collview = hmap.keySet();
		iter = collview.iterator();
		itercnt_exp = collview.size();
		org.junit.Assert.assertEquals(itercnt_exp, hmap.size());
		itercnt = 0;
		while (iter.hasNext()) {
			String key = iter.next();
			String val = (key == null ? "nullval" : key+"v");
			org.junit.Assert.assertEquals(val, hmap.get(key));
			iter.remove();
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		verifySize(hmap, 0);
		collview = hmap.keySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		iter = collview.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());

		hmap.put("1", "1v");
		hmap.put("2", "2v");
		hmap.put("3", "3v");
		collview = hmap.keySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		try {
			collview.add("another key");
			org.junit.Assert.fail("Failed to trap add() on Keys view");
		} catch (UnsupportedOperationException ex) {}
		try {
			java.util.HashSet<String> tmpset = new java.util.HashSet<String>();
			tmpset.add("blah");
			collview.addAll(tmpset);
			org.junit.Assert.fail("Failed to trap addAll() on Keys View");
		} catch (UnsupportedOperationException ex) {}
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		org.junit.Assert.assertEquals(3, collview.size());
		collview.clear();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 0);
    }

    @org.junit.Test
    final public void testValuesView()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
        String one = "one";
		String two = "two";
		String three = "three";
		hmap.put("1", one);
		hmap.put("2", one);  // We will manifest both instances of 'one' in collection - so does JDK HashMap
		hmap.put("3", two);
    	java.util.Collection<String> collview = hmap.values();
		org.junit.Assert.assertTrue(collview.contains(one));
		org.junit.Assert.assertTrue(collview.contains(two));
		org.junit.Assert.assertFalse(collview.contains(three));
		org.junit.Assert.assertFalse(collview.contains(null));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		hmap.put(null, three);
		hmap.put("4", null);
		collview = hmap.values();
		org.junit.Assert.assertTrue(collview.contains(three));
		org.junit.Assert.assertTrue(collview.contains(null));
		org.junit.Assert.assertTrue(collview.contains(one));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 5);
		try {
			collview.add("another value");
			org.junit.Assert.fail("Failed to trap add() on Values view");
		} catch (UnsupportedOperationException ex) {}
		try {
			java.util.HashSet<String> tmpset = new java.util.HashSet<String>();
			tmpset.add("blah");
			collview.addAll(tmpset);
			org.junit.Assert.fail("Failed to trap addAll() on Values view");
		} catch (UnsupportedOperationException ex) {}
    	verifySize(hmap, 5);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());

		java.util.Iterator<String> iter = collview.iterator();
        try {
        	iter.remove();
        	org.junit.Assert.fail("Failed to trap remove() before next() on Values");
        } catch (IllegalStateException ex) {}
        iter = collview.iterator();
		int itercnt_exp = collview.size();
		int itercnt = 0;
		boolean found_it1 = false;
		boolean found_it2 = false;
		while (iter.hasNext()) {
			String str = iter.next();
			if (str == one) found_it1 = true;
			if (str == null) found_it2 = true;
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Failed to trap extra next() on Values");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it1);
		org.junit.Assert.assertTrue(found_it2);

		boolean sts = collview.remove(three);
		org.junit.Assert.assertTrue(sts);
		sts = collview.remove("nosuchvalue");
		org.junit.Assert.assertFalse(sts);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 4);
		org.junit.Assert.assertFalse(collview.contains(three));
		org.junit.Assert.assertFalse(hmap.containsValue(three));
		org.junit.Assert.assertFalse(hmap.containsKey(null));

		sts = collview.remove(null);
		org.junit.Assert.assertTrue(sts);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 3);
		org.junit.Assert.assertFalse(collview.contains(null));
		org.junit.Assert.assertFalse(hmap.containsValue(null));
		org.junit.Assert.assertFalse(hmap.containsKey("4"));

		collview.clear();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 0);
 
		collview = hmap.values();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		iter = collview.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testEntriesView()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
        String one = "one";
		String two = "two";
		String three = "three";
		hmap.put(one, "1");
		hmap.put(two, "2");
		hmap.put(three, "3");
		hmap.put("four", "4");
		hmap.put("five", "5");
		hmap.put(null, "nullkeyval");
		hmap.put("9", null);
		java.util.Set<java.util.Map.Entry<String,String>> collview = hmap.entrySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());

		java.util.Map.Entry<String,String> entr1=null, entr2=null, entr3=null;
		boolean found_nullkey=false, found_nullval=false;
		java.util.Iterator<java.util.Map.Entry<String,String>> iter = collview.iterator();
        try {
        	iter.remove();
        	org.junit.Assert.fail("Failed to trap remove() before next() on Entries");
        } catch (IllegalStateException ex) {}
        iter = collview.iterator();
		int itercnt_exp = collview.size();
		int itercnt = 0;
		while (iter.hasNext()) {
			java.util.Map.Entry<String,String> entr = iter.next();
			itercnt++;
			if (entr.getKey() == one) {
				entr1 = entr;
			} else if (entr.getKey() == two) {
				entr2 = entr;
				org.junit.Assert.assertTrue(entr2.getValue() == "2");
				iter.remove();
			} else if (entr.getKey() == three) {
				entr3 = entr;
				String oldval = entr.setValue("3b");
				org.junit.Assert.assertTrue(oldval == "3");
			} else if (entr.getKey() == null) {
				found_nullkey = true;
				String oldval = entr.setValue("nullval2");
				org.junit.Assert.assertTrue(oldval == "nullkeyval");
			} else if (entr.getValue() == null) {
				found_nullval = true;
			}
		}
		try {iter.next(); org.junit.Assert.fail("Failed to trap extra next() on Entries");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 6);
		org.junit.Assert.assertFalse(collview.contains(entr2));
		org.junit.Assert.assertFalse(hmap.containsKey(entr2.getKey()));
		org.junit.Assert.assertTrue(collview.contains(entr1));
		org.junit.Assert.assertTrue(collview.contains(entr3));
		org.junit.Assert.assertTrue(entr1.getValue() == "1");
		org.junit.Assert.assertTrue(entr3.getValue() == "3b");
		org.junit.Assert.assertTrue(hmap.get(entr3.getKey()) == "3b");
		org.junit.Assert.assertTrue(hmap.containsKey(entr1.getKey()));
		org.junit.Assert.assertTrue(hmap.containsKey(entr3.getKey()));
		org.junit.Assert.assertTrue(found_nullkey);
		org.junit.Assert.assertTrue(found_nullval);
		org.junit.Assert.assertTrue(hmap.get(null) == "nullval2");
		org.junit.Assert.assertFalse(collview.contains(null));
		org.junit.Assert.assertFalse(collview.contains("bad type"));
    	verifySize(hmap, 6);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		try {
			collview.add(null);
			org.junit.Assert.fail("Failed to trap add() on Entries view");
		} catch (UnsupportedOperationException ex) {}
		try {
			collview.addAll(collview);
			org.junit.Assert.fail("Failed to trap addAll() on Entries View");
		} catch (UnsupportedOperationException ex) {}
    	verifySize(hmap, 6);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());

		String k = entr1.getKey();
		org.junit.Assert.assertTrue(collview.remove(entr1));
    	verifySize(hmap, 5);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 5);
		org.junit.Assert.assertTrue(entr1.getKey() == k);
		org.junit.Assert.assertEquals("1", entr1.getValue());
		org.junit.Assert.assertFalse(collview.contains(entr1));
		org.junit.Assert.assertFalse(hmap.containsKey(entr1.getKey()));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		org.junit.Assert.assertFalse(collview.remove(entr1));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 5);
    	String newval = "1b";
    	entr1.setValue(newval);
		org.junit.Assert.assertTrue(newval == entr1.getValue());
		org.junit.Assert.assertTrue(k == entr1.getKey());
		org.junit.Assert.assertFalse(hmap.containsValue(newval));
		org.junit.Assert.assertFalse(hmap.containsKey(entr1.getKey()));
		entr1.toString(); //purely for sake of test coveerage

		collview = hmap.entrySet();
		iter = collview.iterator();
		itercnt_exp = collview.size();
		org.junit.Assert.assertEquals(itercnt_exp, hmap.size());
		itercnt = 0;
		while (iter.hasNext()) {
			java.util.Map.Entry<String,String> entr = iter.next();
			org.junit.Assert.assertSame(entr.getValue(), hmap.get(entr.getKey()));
			iter.remove();
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		verifySize(hmap, 0);
		collview = hmap.entrySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		iter = collview.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());

		hmap.put("1", "1v");
		hmap.put("2", "2v");
		hmap.put("3", "3v");
		collview = hmap.entrySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		java.util.Set<java.util.Map.Entry<String,String>> collview2 = hmap.entrySet();
		org.junit.Assert.assertNotSame(collview, collview2);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 3);
		collview.clear();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 0);

    	// test set-remove on single-element map
		hmap.put("key1", "val1");
		collview = hmap.entrySet();
		iter = collview.iterator();
		boolean found = collview.remove(iter.next());
		org.junit.Assert.assertTrue(found);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(hmap, 0);
    }

    @org.junit.Test
    final public void testGrow()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
    	int cap = hmap.bucketCount();
    	int cnt = 0;
    	String str1 = null;
    	String strlast = null;

    	while (hmap.bucketCount() == cap) {
    		String str = Integer.toString(++cnt);
    		hmap.put(str, str+"v");
    		if (str1 == null) str1 = str;
    		strlast = str;
    	}
		org.junit.Assert.assertTrue(hmap.containsKey(str1));
		org.junit.Assert.assertTrue(hmap.containsValue(str1+"v"));
		org.junit.Assert.assertEquals(str1+"v", hmap.get(str1));
		org.junit.Assert.assertTrue(hmap.containsKey(strlast));
		org.junit.Assert.assertTrue(hmap.containsValue(strlast+"v"));
		org.junit.Assert.assertEquals(strlast+"v", hmap.get(strlast));
    	int cap2 = hmap.bucketCount();
		int newcap = hmap.trimToSize();
		org.junit.Assert.assertEquals(cap2, newcap);

		hmap.remove(Integer.toString(2));
		hmap.remove(Integer.toString(3));
    	verifySize(hmap, cnt - 2);
		int size = hmap.size();
		newcap = hmap.trimToSize();
    	verifySize(hmap, size);
		org.junit.Assert.assertEquals(hmap.bucketCount(), newcap);
		org.junit.Assert.assertEquals(cap, newcap);

		// call this just for the sake of code coverage
		hmap.getStats();
    }

    // This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
    @org.junit.Test
    final public void testGrowBucket()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
    	int cnt = 512;
    	hmap = new HashedMap<String,String>(2, cnt);
    	for (int idx = 0; idx != cnt; idx++) {
    		String str = Integer.toString(idx);
    		hmap.put(str, str+"v");
    	}
    	verifySize(hmap, cnt);
    	String last = Integer.toString(cnt - 1);
		org.junit.Assert.assertTrue(hmap.containsKey("0"));
		org.junit.Assert.assertTrue(hmap.containsKey(last));
		org.junit.Assert.assertTrue(hmap.containsValue("0v"));
		org.junit.Assert.assertTrue(hmap.containsValue(last+"v"));
    }

    @org.junit.Test
    final public void testRecycledIterators()
    {
        HashedMap<String,String> hmap = new HashedMap<String,String>(3, 3);
		hmap.put("1", "1v");
		hmap.put("2", "2v");
		hmap.put("3", "3v");
		int itercnt_exp = hmap.size();

		java.util.Iterator<String> iter = hmap.keysIterator();
		org.junit.Assert.assertFalse(iter == hmap.keySet().iterator());
		int itercnt = 0;
		boolean found_it = false;
		while (iter.hasNext()) {
			String str = iter.next();
			if (str == "2") found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		java.util.Iterator<String> iter2 = hmap.keysIterator();
		org.junit.Assert.assertTrue(iter == iter2);
		org.junit.Assert.assertTrue(iter2.hasNext());

		iter = hmap.valuesIterator();
		itercnt = 0;
		found_it = false;
		while (iter.hasNext()) {
			String str = iter.next();
			if (str == "2v") found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
		iter2 = hmap.valuesIterator();
		org.junit.Assert.assertTrue(iter == iter2);
		org.junit.Assert.assertTrue(iter2.hasNext());
    }

    // Always verify that this test also works if we swap in java.util.HashMap
    @org.junit.Test
    final public void bulktest()
    {
    	final int cap = 10*1000; //ramp up to investigate manually
    	HashedMap<Integer,String> map = new HashedMap<Integer,String>(0, 5);
    	//java.util.HashMap<Integer, String> map = new java.util.HashMap<Integer, String>();
        // general put-get
        for (int v = 0; v != cap; v++) {
        	String s = (v == 0 ? null : String.valueOf(v));
        	String old = map.put(v, s);
        	org.junit.Assert.assertNull(v+" held "+old, old);
        }
		//System.out.println(map.getStats());
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertEquals(null, map.remove(null));
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertEquals(null, map.remove(cap+100));
		org.junit.Assert.assertEquals(cap, map.size());
        org.junit.Assert.assertNull(map.put(null, "nought"));
		org.junit.Assert.assertEquals(cap+1, map.size());
        for (int v = 1; v != cap; v++) org.junit.Assert.assertEquals(v, Integer.parseInt(map.get(v)));
        org.junit.Assert.assertEquals("nought", map.get(null));
        org.junit.Assert.assertNull(map.get(0));
        // Keys view
		java.util.HashSet<Integer> jset = new java.util.HashSet<Integer>();
		java.util.Set<Integer> kset = map.keySet();
		java.util.Iterator<Integer> kit = kset.iterator();
		org.junit.Assert.assertEquals(cap+1, kset.size());
		org.junit.Assert.assertEquals(cap+1, map.size());
		while (kit.hasNext()) {
			jset.add(kit.next());
			kit.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(cap+1, jset.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(jset.contains(v));
        org.junit.Assert.assertTrue(jset.contains(null));
        org.junit.Assert.assertTrue(jset.contains(0));
		// Map.Entry view
        for (int v = 1; v != cap; v++) map.put(v, String.valueOf(v));
        map.put(null, "nought");
        map.put(0, null);
        java.util.Set<java.util.Map.Entry<Integer,String>> eset = map.entrySet();
        java.util.Iterator<java.util.Map.Entry<Integer,String>> mit = eset.iterator();
		org.junit.Assert.assertEquals(cap+1, eset.size());
		org.junit.Assert.assertEquals(cap+1, map.size());
        jset.clear();
		while (mit.hasNext()) {
			jset.add(mit.next().getKey());
			mit.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(cap+1, jset.size());
        for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(jset.contains(v));
        org.junit.Assert.assertTrue(jset.contains(null));
        org.junit.Assert.assertTrue(jset.contains(0));
        // Values view
        for (int v = 1; v != cap; v++) map.put(v, String.valueOf(v));
        map.put(null, "nought");
        map.put(0, null);
		java.util.Collection<String> vcoll = map.values();
		java.util.Iterator<String> vit = vcoll.iterator();
		org.junit.Assert.assertEquals(cap+1, vcoll.size());
		org.junit.Assert.assertEquals(cap+1, map.size());
        for (int v = 1; v != 10; v++) org.junit.Assert.assertTrue(vcoll.contains(String.valueOf(v))); //slow linear scan
        for (int v = cap - 10; v != cap; v++) org.junit.Assert.assertTrue(vcoll.contains(String.valueOf(v)));
        org.junit.Assert.assertTrue(vcoll.contains(null));
        org.junit.Assert.assertTrue(vcoll.contains("nought"));
        org.junit.Assert.assertFalse(vcoll.contains("0"));
		java.util.HashSet<String> jvset = new java.util.HashSet<String>();
		while (vit.hasNext()) {
			jvset.add(vit.next());
			vit.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(cap+1, jvset.size());
        for (int v = 1; v != cap; v++) org.junit.Assert.assertTrue(jvset.contains(String.valueOf(v)));
        org.junit.Assert.assertTrue(jvset.contains(null));
        org.junit.Assert.assertTrue(jvset.contains("nought"));
        org.junit.Assert.assertFalse(jvset.contains("0"));
    }

    private String addEntry(HashedMap<String,String> hmap, String key, String val, boolean isnew, String oldval_exp)
    {
    	int size = hmap.size();
    	if (isnew) {
    		org.junit.Assert.assertFalse(hmap.containsKey(key));
    		org.junit.Assert.assertNull(hmap.get(key));
    		size++;  // size will increase
    	} else {
    		org.junit.Assert.assertTrue(hmap.containsKey(key));
    	}
    	String oldval = hmap.put(key, val);
    	String newval = hmap.get(key);

    	if (isnew) {
    		org.junit.Assert.assertNull(oldval);
    	} else {
    		org.junit.Assert.assertSame(oldval, oldval_exp);
    	}
		org.junit.Assert.assertSame(newval, val);
		org.junit.Assert.assertTrue(hmap.containsKey(key));
		org.junit.Assert.assertTrue(hmap.containsValue(val));
		verifySize(hmap, size);
		return oldval;
    }

    private String deleteEntry(HashedMap<String,String> hmap, String key, boolean exists, String val)
    {
    	int size = hmap.size();
    	if (exists) size--;
		String delstr = hmap.remove(key);
		org.junit.Assert.assertFalse(hmap.containsKey(key));
		org.junit.Assert.assertSame(delstr, val);
		org.junit.Assert.assertNull(hmap.get(key));
    	verifySize(hmap, size);
		return delstr;
    }

    private void verifySize(HashedMap<String,String> hmap, int size)
    {
		org.junit.Assert.assertEquals(size, hmap.size());
		org.junit.Assert.assertEquals(size == 0, hmap.isEmpty());
    }
}