/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/*
 * This test class doesn't actually test any of our own classes. It tests the JDK HashMap class, to verify that we're tracking its behaviour
 * correctly.
 * This file should be kept aligned with the test case for our HashedList class. it is derived from that by copying it, replacing all occurrences
 * of "HashedList" with HashMap (and adding package qualifier where necessary for compiler) and the stripping away any HashedList-specific calls,
 * that are not part of the java.util.Map contract. That last step simply consists of removing any bits from this class that don't compile.
 * By applying the same assertions to HashMap as to our own HashedList map, we can gain more assurance of its correctness.
 */
public class HashMapTest
{
    private java.util.Map<String,String> hmap = new java.util.HashMap<String,String>(3, 3);

    @org.junit.Test
    final public void testSize()
    {
    	String key = "testkey";
    	verifySize(0);
    	hmap.put(key, "val");
    	verifySize(1);
    	hmap.remove(key);
    	verifySize(0);
    }

    @org.junit.Test
    final public void testAdd()
    {
    	String key = "testkey";
    	String key2 = "key2";
    	String val = "testval";
    	String val2 = "val2";
    	String val3 = "val3";
		org.junit.Assert.assertFalse(hmap.containsValue(val));
		addEntry(key, val, true, null);
		addEntry(key, val2, false, val);
		addEntry(key2, val3, true, null);
		
		// test keys which are equal but not identical
		int size = hmap.size();
		String key2b = new String(key2);
		org.junit.Assert.assertNotSame(key2, key2b);
		org.junit.Assert.assertSame(hmap.get(key2), hmap.get(key2b));
		String val3b = hmap.put(key2b, "samekey_newval");
		verifySize(size);
		org.junit.Assert.assertSame(hmap.get(key2), hmap.get(key2b));
		org.junit.Assert.assertSame(val3, val3b);
    }

    @org.junit.Test
    final public void testRemove()
    {
    	String key = "testkey";
    	String val = "testval";
		addEntry(key, val, true, null);
		addEntry("key2", val, true, null);
		deleteEntry(key, true, val);
		deleteEntry("nosuchkey", false, null);
    }

    @org.junit.Test
    final public void testNullKey()
    {
    	String val = "testval";
    	String val2 = "testval2";
		addEntry(null, val, true, null);
		addEntry(null, val2, false, val);
		deleteEntry(null, true, val2);
		deleteEntry(null, false, null);
    }

    @org.junit.Test
    final public void testNullValue()
    {
		org.junit.Assert.assertFalse(hmap.containsValue(null));
		String key1 = "key1";
		String key2 = "key2";
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
    	hmap.put("1", "v1");
    	verifySize(1);
    	hmap.clear();
    	verifySize(0);
    	hmap.put("2", "v2");
    	hmap.put("3", "v2");
    	verifySize(2);
    	hmap.clear();
    	verifySize(0);
    	hmap.clear();
    	verifySize(0);
    }

    @org.junit.Test
    final public void testPutAll()
    {
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
    	verifySize(3);

		HashedMap<String,String> map2 = new HashedMap<String,String>();
        String one2 = "one2";
		String two2 = "two2";
        map2.put("7", one2);
		map2.put(null, two2);
    	hmap.putAll(map2);
		org.junit.Assert.assertSame(one2, hmap.get("7"));
		org.junit.Assert.assertSame(two2, hmap.get(null));
    	verifySize(5);

    	map2.clear();
    	verifySize(5);
    	hmap.putAll(map2);
    	verifySize(5);
    }

    @org.junit.Test
    final public void testKeysView()
    {
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
    	verifySize(5);

		java.util.Iterator<String> iter = collview.iterator();
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
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it1);
		org.junit.Assert.assertTrue(found_it2);

		boolean sts = collview.remove(three);
		org.junit.Assert.assertTrue(sts);
		sts = collview.remove("nosuchkey");
		org.junit.Assert.assertFalse(sts);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(4);
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
		verifySize(0);
		collview = hmap.keySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		iter = collview.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());

		hmap.put("1", "1v");
		hmap.put("2", "2v");
		hmap.put("3", "3v");
		collview = hmap.keySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		collview.clear();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(0);
    }

    @org.junit.Test
    final public void testValuesView()
    {
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
    	verifySize(5);

		java.util.Iterator<String> iter = collview.iterator();
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
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it1);
		org.junit.Assert.assertTrue(found_it2);

		boolean sts = collview.remove(three);
		org.junit.Assert.assertTrue(sts);
		sts = collview.remove("nosuchvalue");
		org.junit.Assert.assertFalse(sts);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(4);
		org.junit.Assert.assertFalse(collview.contains(three));
		org.junit.Assert.assertFalse(hmap.containsValue(three));
		org.junit.Assert.assertFalse(hmap.containsKey(null));

		sts = collview.remove(null);
		org.junit.Assert.assertTrue(sts);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(3);
		org.junit.Assert.assertFalse(collview.contains(null));
		org.junit.Assert.assertFalse(hmap.containsValue(null));
		org.junit.Assert.assertFalse(hmap.containsKey("4"));

		collview.clear();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(0);
 
		collview = hmap.values();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		iter = collview.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());
    }

    @org.junit.Test
    final public void testEntriesView()
    {
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
				org.junit.Assert.assertTrue(entr.getValue() == "3");
				entr.setValue("3b");
			} else if (entr.getKey() == null) {
				found_nullkey = true;
				org.junit.Assert.assertTrue(entr.getValue() == "nullkeyval");
				entr.setValue("nullval2");
			} else if (entr.getValue() == null) {
				found_nullval = true;
			}
		}
		try {iter.next(); org.junit.Assert.fail("Iterator out of bounds");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(6);
		org.junit.Assert.assertTrue(collview.contains(entr1));
		org.junit.Assert.assertFalse(collview.contains(entr2));
		org.junit.Assert.assertTrue(collview.contains(entr3));
		org.junit.Assert.assertTrue(entr1.getValue() == "1");
		org.junit.Assert.assertTrue(entr3.getValue() == "3b");
		org.junit.Assert.assertTrue(hmap.get(entr3.getKey()) == "3b");
		org.junit.Assert.assertTrue(hmap.containsKey(entr1.getKey()));
		org.junit.Assert.assertFalse(hmap.containsKey(entr2.getKey()));
		org.junit.Assert.assertTrue(hmap.containsKey(entr3.getKey()));
		org.junit.Assert.assertTrue(found_nullkey);
		org.junit.Assert.assertTrue(found_nullval);
		org.junit.Assert.assertTrue(hmap.get(null) == "nullval2");
		org.junit.Assert.assertFalse(collview.contains(null));
		org.junit.Assert.assertFalse(collview.contains("bad type"));

		collview.remove(entr1);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(5);
		org.junit.Assert.assertFalse(collview.contains(entr1));
		org.junit.Assert.assertFalse(hmap.containsKey(entr1.getKey()));
		collview.remove(entr1);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(5);

		clearWithEntriesView();
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
		org.junit.Assert.assertSame(collview, collview2);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(3);
		collview.clear();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(0);

    	// test set-remove on single-element map
		hmap.put("key1", "val1");
		collview = hmap.entrySet();
		iter = collview.iterator();
		boolean found = collview.remove(iter.next());
		org.junit.Assert.assertTrue(found);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
    	verifySize(0);
    }

    @org.junit.Test
    final public void testGrow()
    {
    	int cnt = 0;
    	String nullval = "nullval";
    	String str1 = null;
    	String strlast = null;

    	while (hmap.size() <= 512)
    	{
    		String str = Integer.toString(++cnt);
    		hmap.put(str, str+"v");
    		if (str1 == null) str1 = str;
    		strlast = str;
    	}
		hmap.put(null, nullval);
    	verifySize(++cnt);
		org.junit.Assert.assertTrue(hmap.containsKey(str1));
		org.junit.Assert.assertTrue(hmap.containsValue(str1+"v"));
		org.junit.Assert.assertEquals(str1+"v", hmap.get(str1));
		org.junit.Assert.assertTrue(hmap.containsKey(strlast));
		org.junit.Assert.assertTrue(hmap.containsValue(strlast+"v"));
		org.junit.Assert.assertEquals(strlast+"v", hmap.get(strlast));

		hmap.remove(Integer.toString(2));
		hmap.remove(Integer.toString(3));
    	verifySize(cnt - 2);

		org.junit.Assert.assertTrue(hmap.containsKey(null));
		org.junit.Assert.assertTrue(hmap.containsValue(nullval));
		org.junit.Assert.assertSame(nullval, hmap.get(null));
    }

    // This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
    @org.junit.Test
    final public void testGrowBucket()
    {
    	int cnt = 512;
    	hmap = new java.util.HashMap<String,String>(2, cnt);
    	for (int idx = 0; idx != cnt; idx++)
    	{
    		String str = Integer.toString(idx);
    		hmap.put(str, str+"v");
    	}
    	verifySize(cnt);
    	String last = Integer.toString(cnt - 1);
		org.junit.Assert.assertTrue(hmap.containsKey("0"));
		org.junit.Assert.assertTrue(hmap.containsKey(last));
		org.junit.Assert.assertTrue(hmap.containsValue("0v"));
		org.junit.Assert.assertTrue(hmap.containsValue(last+"v"));
    }

    private void clearWithEntriesView()
    {
		java.util.Set<java.util.Map.Entry<String,String>> collview = hmap.entrySet();
		java.util.Iterator<java.util.Map.Entry<String,String>> iter = collview.iterator();
		int itercnt_exp = collview.size();
		org.junit.Assert.assertEquals(itercnt_exp, hmap.size());
		int itercnt = 0;
		while (iter.hasNext()) {
			java.util.Map.Entry<String,String> entr = iter.next();
			org.junit.Assert.assertSame(entr.getValue(), hmap.get(entr.getKey()));
			iter.remove();
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		verifySize(0);
    }

    private String addEntry(String key, String val, boolean isnew, String oldval_exp)
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

    private String deleteEntry(String key, boolean exists, String val)
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
