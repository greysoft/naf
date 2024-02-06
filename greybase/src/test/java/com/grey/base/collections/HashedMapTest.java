/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class HashedMapTest
{
	private static final int SOAK_SIZE = 10*1000;
	private static final int SOAK_RUNS = 5;
	private static final int SOAK_WARMUPS = 20;

	private enum MapTypeID {MAPTYP_HMKV, MAPTYP_HMENT, MAPTYP_JDK}
	private enum ModType {MODTYP_ADD, MODTYP_DEL, MODTYP_CLR, MODTYP_TRIM}

	@org.junit.runners.Parameterized.Parameters(name="{0}")
	public static java.util.Collection<MapTypeID[]> generateParams() {
		return java.util.Arrays.asList(new MapTypeID[][] {
			{MapTypeID.MAPTYP_JDK},
			{MapTypeID.MAPTYP_HMENT},
			{MapTypeID.MAPTYP_HMKV}});
	}

	private final MapTypeID maptype;

	public HashedMapTest(MapTypeID mtype) {
		maptype = mtype;
	}

	@org.junit.Test
	final public void testSize()
	{
		java.util.Map<String,String> hmap = allocMap(3, 3);
		String key = "testkey";
		String val = "val";
		verifySize(hmap, 0);
		org.junit.Assert.assertFalse(hmap.containsKey(key));
		org.junit.Assert.assertNull(hmap.get(key));
		org.junit.Assert.assertNull(hmap.remove(key));
		hmap.put(key, val);
		org.junit.Assert.assertTrue(hmap.containsKey(key));
		org.junit.Assert.assertTrue(hmap.get(key) == val);
		org.junit.Assert.assertFalse(hmap.containsKey(key+"x"));
		verifySize(hmap, 1);
		hmap.toString(); //for sake of code coverage
		String oldval = hmap.remove(key);
		org.junit.Assert.assertTrue(oldval == val);
		verifySize(hmap, 0);
		hmap.toString(); //for sake of code coverage

		//for sake of code coverage
		hmap = allocMap(-1, 0);
		verifySize(hmap, 0);
		org.junit.Assert.assertTrue(HashedMap.compareObjects(null, null));
		org.junit.Assert.assertTrue(HashedMap.compareObjects("1", "1"));
		org.junit.Assert.assertTrue(HashedMap.compareObjects("1", new String("1")));
		org.junit.Assert.assertFalse(HashedMap.compareObjects("1", null));
		org.junit.Assert.assertFalse(HashedMap.compareObjects(null, "1"));
	}

	@org.junit.Test
	final public void testAdd()
	{
		java.util.Map<String,String> hmap = allocMap(3, 3);
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
		java.util.Map<String,String> hmap = allocMap(3, 3);
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
		java.util.Map<String,String> hmap = allocMap(3, 3);
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
		java.util.Map<String,String> hmap = allocMap(3, 3);
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
		java.util.Map<String,String> hmap = allocMap(3, 3);
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
		java.util.Map<String,String> hmap = allocMap(3, 3);
		java.util.Map<String,String> stdmap = new java.util.HashMap<String,String>();
		String one = "one";
		String two = "two";
		String three = "three";
		stdmap.put("1", one);
		stdmap.put("2", two);
		stdmap.put("3", three);
		hmap.putAll(stdmap);
		org.junit.Assert.assertSame(one, hmap.get("1"));
		org.junit.Assert.assertSame(two, hmap.get("2"));
		org.junit.Assert.assertSame(three, hmap.get("3"));
		verifySize(hmap, 3);

		java.util.Map<String,String> map2 = allocMap(-1, 0);
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
		java.util.Map<String,String> hmap = allocMap(3, 3);
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

		if (maptype != MapTypeID.MAPTYP_JDK) {
			java.util.Set<String> view2 = hmap.keySet();
			org.junit.Assert.assertSame(collview, view2);
		}

		java.util.Iterator<String> iter = collview.iterator();
		try {
			iter.remove();
			org.junit.Assert.fail("Failed to trap remove() before next() on Keys");
		} catch (IllegalStateException ex) {}
		iter = collview.iterator();
		int itercnt_exp = collview.size();
		int itercnt = 0;
		boolean found_it1 = false;
		boolean found_itnull = false;
		while (iter.hasNext()) {
			String str = iter.next();
			if (str == one) found_it1 = true;
			if (str == null) found_itnull = true;
			if (str == three) iter.remove();
			itercnt++;
		}
		try {iter.next(); org.junit.Assert.fail("Failed to trap extra next() on Keys");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertFalse(iter.hasNext());
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it1);
		org.junit.Assert.assertTrue(found_itnull);
		org.junit.Assert.assertEquals(itercnt_exp-1, hmap.size());
		org.junit.Assert.assertFalse(hmap.containsKey(three));
		org.junit.Assert.assertNull(hmap.put(three, three+"v"));
		org.junit.Assert.assertTrue(hmap.containsKey(three));

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
		org.junit.Assert.assertEquals(0, collview.size());
		collview = hmap.keySet();
		org.junit.Assert.assertEquals(0, collview.size());
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

		//test toArray() as well
		hmap.put("1b", "1bv");
		Object[] arr1 = new Object[3];
		java.util.Arrays.fill(arr1, "marker");
		Object[] arr1b = hmap.keySet().toArray(arr1);
		org.junit.Assert.assertSame(arr1, arr1b);
		org.junit.Assert.assertEquals("1b", arr1[0]);
		org.junit.Assert.assertNull(arr1[1]);

		arr1 = hmap.keySet().toArray();
		org.junit.Assert.assertEquals(1, arr1.length);
		org.junit.Assert.assertEquals("1b", arr1[0]);
	}

	@org.junit.Test
	final public void testValuesView()
	{
		java.util.Map<String,String> hmap = allocMap(3, 3);
		String one = "one";
		String two = "two";
		String three = "three";
		hmap.put("1", one);
		hmap.put("2", one); // We will manifest both instances of 'one' in collection - so does JDK HashMap
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

		if (maptype != MapTypeID.MAPTYP_JDK) {
			java.util.Collection<String> view2 = hmap.values();
			org.junit.Assert.assertSame(collview, view2);
		}

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
		org.junit.Assert.assertFalse(iter.hasNext());
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

		iter = collview.iterator();
		itercnt_exp = collview.size();
		itercnt = 0;
		while (iter.hasNext()) {
			iter.next();
			iter.remove();
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		verifySize(hmap, 0);
		org.junit.Assert.assertEquals(0, collview.size());
		collview = hmap.values();
		org.junit.Assert.assertEquals(0, collview.size());
		iter = collview.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());

		hmap.put("1", "1v");
		hmap.put("2", "2v");
		hmap.put("3", "3v");
		collview = hmap.values();
		org.junit.Assert.assertEquals(3, collview.size());
		collview.clear();
		verifySize(hmap, 0);
		org.junit.Assert.assertEquals(0, collview.size());

		//test toArray() as well
		hmap.put("1b", "1bv");
		Object[] arr1 = new Object[3];
		java.util.Arrays.fill(arr1, "marker");
		Object[] arr1b = hmap.values().toArray(arr1);
		org.junit.Assert.assertSame(arr1, arr1b);
		org.junit.Assert.assertEquals("1bv", arr1[0]);
		org.junit.Assert.assertNull(arr1[1]);

		arr1 = hmap.values().toArray();
		org.junit.Assert.assertEquals(1, arr1.length);
		org.junit.Assert.assertEquals("1bv", arr1[0]);
	}

	@org.junit.Test
	final public void testEntriesView()
	{
		java.util.Map<String,String> hmap = allocMap(3, 3);
		final String one = "one";
		final String two = "two";
		final String three = "three";
		hmap.put(one, "1");
		hmap.put(two, "2");
		hmap.put(three, "3");
		hmap.put("four", "4");
		hmap.put("five", "5");
		hmap.put(null, "nullkeyval");
		hmap.put("9", null);
		int mapsize = 7;
		java.util.Set<java.util.Map.Entry<String,String>> collview = hmap.entrySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		verifySize(hmap, mapsize);

		if (maptype != MapTypeID.MAPTYP_JDK) {
			java.util.Set<java.util.Map.Entry<String,String>> view2 = hmap.entrySet();
			org.junit.Assert.assertSame(collview, view2);
		}

		java.util.Map.Entry<String,String> entr1=null, entr2=null, entr3=null, entr9=null, entrnull=null;
		java.util.Iterator<java.util.Map.Entry<String,String>> iter = collview.iterator();
		int itercnt_exp = collview.size();
		try {
			iter.remove();
			org.junit.Assert.fail("Failed to trap remove() before next() on Entries");
		} catch (IllegalStateException ex) {}
		org.junit.Assert.assertEquals(collview.size(), itercnt_exp);
		iter = collview.iterator();
		int itercnt = 0;
		while (iter.hasNext()) {
			java.util.Map.Entry<String,String> entr = iter.next();
			itercnt++;
			if (entr.getKey() == one) {
				entr1 = new MyMapEntry(entr);
				org.junit.Assert.assertTrue(entr.getValue() == "1");
				org.junit.Assert.assertTrue(entr1.getValue() == "1");
				org.junit.Assert.assertTrue(entr.equals(entr1));
				org.junit.Assert.assertTrue(entr.equals(entr));
				org.junit.Assert.assertTrue(entr.equals(new MyMapEntry(new String(entr1.getKey()), new String(entr1.getValue()))));
				org.junit.Assert.assertFalse(entr.equals(null));
				org.junit.Assert.assertFalse(entr.equals("bad type"));
				org.junit.Assert.assertFalse(entr.equals(new MyMapEntry("key1b", "val1b")));
				org.junit.Assert.assertFalse(entr.equals(new MyMapEntry(null, null)));
				if (maptype != MapTypeID.MAPTYP_JDK) org.junit.Assert.assertEquals(entr.getKey().hashCode(), entr.hashCode()); //for sake of code coverage
			} else if (entr.getKey() == two) {
				entr2 = new MyMapEntry(entr);
				org.junit.Assert.assertTrue(entr2.getValue() == "2");
				org.junit.Assert.assertTrue(entr2.getKey() == two);
				iter.remove(); //entr2.getValue() is now undefined, so don't test again
				mapsize--;
				entr2.setValue("2b"); //should have no effect on map
				org.junit.Assert.assertTrue(entr2.getKey() == two);
				org.junit.Assert.assertFalse(hmap.containsKey(entr2.getKey()));
				org.junit.Assert.assertNull(hmap.get(entr2.getKey()));
				org.junit.Assert.assertEquals(mapsize, hmap.size());
			} else if (entr.getKey() == three) {
				String newval = "3b";
				String oldval = entr.setValue(newval);
				org.junit.Assert.assertTrue(oldval == "3");
				org.junit.Assert.assertTrue(newval == entr.getValue());
				org.junit.Assert.assertTrue(three == entr.getKey());
				org.junit.Assert.assertTrue(hmap.get(three), hmap.get(three) == newval);
				newval = "3c";
				hmap.put(three, newval);
				org.junit.Assert.assertTrue(newval == entr.getValue());
				org.junit.Assert.assertTrue(three == entr.getKey());
				entr3 = new MyMapEntry(entr);
			} else if (entr.getKey() == null) {
				String newval = "nullkeyval2";
				String oldval = entr.setValue(newval);
				entrnull = new MyMapEntry(entr);
				org.junit.Assert.assertTrue(oldval == "nullkeyval");
				org.junit.Assert.assertTrue(newval == entr.getValue());
				org.junit.Assert.assertTrue(null == entr.getKey());
				org.junit.Assert.assertTrue(entr.equals(entrnull));
				org.junit.Assert.assertTrue(hmap.get(null), hmap.get(null) == newval);
				if (maptype != MapTypeID.MAPTYP_JDK) org.junit.Assert.assertEquals(0, entr.hashCode()); //for sake of code coverage
			} else if (entr.getValue() == null) {
				entr9 = new MyMapEntry(entr);
				org.junit.Assert.assertTrue(entr.getKey() == "9");
				org.junit.Assert.assertTrue(entr.equals(entr9));
			}
			org.junit.Assert.assertTrue(entr.toString().contains(""+entr.getKey())); //empty string concat turns null to "null"
			org.junit.Assert.assertTrue(entr.toString().contains(""+entr.getValue()));
		}
		try {iter.next(); org.junit.Assert.fail("Failed to trap extra next() on Entries");} catch (java.util.NoSuchElementException ex) {}
		org.junit.Assert.assertFalse(iter.hasNext());
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		verifySize(hmap, mapsize);

		org.junit.Assert.assertFalse(hmap.containsKey(entr2.getKey()));
		org.junit.Assert.assertFalse(collview.contains(entr2));
		String newval = "newvalue2";
		entr2.setValue(newval);
		org.junit.Assert.assertTrue(entr2.getValue() == newval);
		verifySize(hmap, 6);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		entr2.setValue(null);
		org.junit.Assert.assertFalse(collview.contains(entr2));

		org.junit.Assert.assertTrue(entr1.getValue() == "1");
		org.junit.Assert.assertTrue(entr3.getValue() == "3c");
		org.junit.Assert.assertTrue(entr9.getValue() == null);
		org.junit.Assert.assertTrue(entrnull.getValue() == "nullkeyval2");
		org.junit.Assert.assertTrue(hmap.containsKey(entr1.getKey()));
		org.junit.Assert.assertTrue(hmap.containsKey(entr3.getKey()));
		org.junit.Assert.assertTrue(hmap.get(null) == "nullkeyval2");
		org.junit.Assert.assertTrue(collview.contains(entr1));
		org.junit.Assert.assertTrue(collview.contains(entr3));
		org.junit.Assert.assertTrue(collview.contains(entr9));
		org.junit.Assert.assertTrue(collview.contains(entrnull));
		org.junit.Assert.assertFalse(collview.contains(null));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		try {
			collview.add(entr1);
			org.junit.Assert.fail("Failed to trap add() on Entries view");
		} catch (UnsupportedOperationException ex) {}
		try {
			collview.add(null);
			org.junit.Assert.fail("Failed to trap add(null) on Entries view");
		} catch (UnsupportedOperationException ex) {}
		try {
			java.util.Set<java.util.Map.Entry<String,String>> s = new java.util.HashSet<java.util.Map.Entry<String,String>>();
			s.add(entr1);
			collview.addAll(s);
			org.junit.Assert.fail("Failed to trap addAll() on Entries View");
		} catch (UnsupportedOperationException ex) {}
		verifySize(hmap, 6);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());

		MyMapEntry ent_alt = new MyMapEntry(new String(entr1.getKey()), new String(entr1.getValue()));
		org.junit.Assert.assertTrue(collview.contains(ent_alt));
		ent_alt = new MyMapEntry(null, entr1.getValue());
		org.junit.Assert.assertFalse(collview.contains(ent_alt));
		org.junit.Assert.assertFalse(collview.remove(ent_alt));
		ent_alt = new MyMapEntry(entr1.getKey(), null);
		org.junit.Assert.assertFalse(collview.contains(ent_alt));
		org.junit.Assert.assertFalse(collview.remove(ent_alt));
		ent_alt = new MyMapEntry(entr1.getKey(), entr1.getValue()+"x");
		org.junit.Assert.assertFalse(collview.contains(ent_alt));
		org.junit.Assert.assertFalse(collview.remove(ent_alt));
		ent_alt = new MyMapEntry("nosuchkey", entr1.getValue());
		org.junit.Assert.assertFalse(collview.contains(ent_alt));
		org.junit.Assert.assertFalse(collview.remove(ent_alt));
		ent_alt = new MyMapEntry(null, null);
		org.junit.Assert.assertFalse(collview.contains(ent_alt));
		org.junit.Assert.assertFalse(collview.remove(ent_alt));

		org.junit.Assert.assertFalse(collview.remove(null));
		verifySize(hmap, 6);
		org.junit.Assert.assertEquals(hmap.size(), collview.size());

		org.junit.Assert.assertTrue(collview.contains(entr1));
		org.junit.Assert.assertTrue(collview.remove(entr1));
		mapsize--;
		verifySize(hmap, mapsize);
		org.junit.Assert.assertFalse(collview.contains(entr1));
		org.junit.Assert.assertFalse(collview.remove(entr1));
		org.junit.Assert.assertFalse(hmap.containsKey(entr1.getKey()));
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		verifySize(hmap, mapsize);

		collview = hmap.entrySet();
		iter = collview.iterator();
		itercnt_exp = collview.size();
		org.junit.Assert.assertEquals(itercnt_exp, hmap.size());
		itercnt = 0;
		while (iter.hasNext()) {
			java.util.Map.Entry<String,String> entr = iter.next();
			String ekey = entr.getKey();
			org.junit.Assert.assertSame(entr.getValue(), hmap.get(ekey));
			iter.remove();
			if (maptype == MapTypeID.MAPTYP_HMKV) org.junit.Assert.assertSame(ekey, entr.getKey());
			org.junit.Assert.assertFalse(hmap.containsKey(ekey));
			int msize = hmap.size();
			entr.setValue(entr.getValue()+"2");
			org.junit.Assert.assertEquals(msize, hmap.size());
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		verifySize(hmap, 0);
		org.junit.Assert.assertEquals(0, collview.size());
		collview = hmap.entrySet();
		org.junit.Assert.assertEquals(0, collview.size());
		iter = collview.iterator();
		org.junit.Assert.assertFalse(iter.hasNext());

		hmap.put("1", "1v");
		hmap.put("2", "2v");
		hmap.put("3", "3v");
		collview = hmap.entrySet();
		org.junit.Assert.assertEquals(hmap.size(), collview.size());
		java.util.Set<java.util.Map.Entry<String,String>> collview2 = hmap.entrySet();
		if (maptype != MapTypeID.MAPTYP_JDK) org.junit.Assert.assertSame(collview, collview2);
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
	final public void testEntriesArrays()
	{
		java.util.Map<Integer,String> hmap = allocMapIntKey(3, 3);
		hmap.put(1, "one");
		hmap.put(2, "two");
		hmap.put(3, "three");
		Object[] arr = hmap.entrySet().toArray();
		org.junit.Assert.assertEquals(3, arr.length);
		java.util.Map.Entry<Integer,String> ent1 = null;
		java.util.Map.Entry<Integer,String> ent2 = null;
		java.util.Map.Entry<Integer,String> ent3 = null;
		for (int idx = 0; idx != arr.length; idx++) {
			@SuppressWarnings("unchecked")
			java.util.Map.Entry<Integer,String> ent = (java.util.Map.Entry<Integer,String>)arr[idx];
			if (ent.getKey() == 1) {
				ent1 = ent;
			} else if (ent.getKey() == 2) {
				ent2 = ent;
			} else if (ent.getKey() == 3) {
				ent3 = ent;
			} else {
				org.junit.Assert.fail("Unexpected element="+ent.getKey()+" - "+ent);
			}
		}
		org.junit.Assert.assertEquals("one", ent1.getValue());
		org.junit.Assert.assertEquals("two", ent2.getValue());
		org.junit.Assert.assertEquals("three", ent3.getValue());

		//verify that changes to array members are reflected in the map
		org.junit.Assert.assertTrue(hmap.containsKey(1));
		String ent1val = "arrayval";
		String oldval = ent1.setValue("arrayval");
		org.junit.Assert.assertEquals(ent1val, ent1.getValue());
		org.junit.Assert.assertEquals(ent1val, hmap.get(1));
		org.junit.Assert.assertEquals("one", oldval);
		//... and changes to the map are reflected in the array members
		ent1val = "x1";
		oldval = hmap.put(1, ent1val);
		org.junit.Assert.assertEquals(ent1val, ent1.getValue());
		org.junit.Assert.assertEquals(ent1val, hmap.get(1));
		org.junit.Assert.assertEquals("arrayval", oldval);
		hmap.remove(1); //NB: ent1.getValue() is now undefined, so don't test it again
		org.junit.Assert.assertEquals(1, ent1.getKey().intValue()); //but getKey() should be constant
		org.junit.Assert.assertFalse(hmap.containsKey(1));
		org.junit.Assert.assertNull(hmap.get(1));
		// ... and changes to an array element that's been removed from the map have no effect on map
		ent1val = "x2";
		int siz1 = hmap.size();
		ent1.setValue(ent1val); //effect on Map.Entry is undefined, so don't test it
		org.junit.Assert.assertFalse(hmap.containsKey(1));
		org.junit.Assert.assertNull(hmap.get(1));
		org.junit.Assert.assertEquals(siz1, hmap.size());
		org.junit.Assert.assertEquals(1, ent1.getKey().intValue());

		// do another toArray() call and loop on original array again to verify it's unmodified
		hmap.put(4, "four");
		hmap.put(5, "five");
		hmap.put(6, "six");
		Object[] arr2 = hmap.entrySet().toArray();
		org.junit.Assert.assertEquals(5, arr2.length);
		org.junit.Assert.assertEquals(3, arr.length);
		org.junit.Assert.assertFalse(arr2 == arr);
		ent1 = null;
		ent2 = null;
		ent3 = null;
		for (int idx = 0; idx != arr.length; idx++) {
			@SuppressWarnings("unchecked")
			java.util.Map.Entry<Integer,String> ent = (java.util.Map.Entry<Integer,String>)arr[idx];
			if (ent.getKey() == 1) {
				ent1 = ent;
			} else if (ent.getKey() == 2) {
				ent2 = ent;
			} else if (ent.getKey() == 3) {
				ent3 = ent;
			} else {
				org.junit.Assert.fail("Unexpected element="+ent.getKey()+" - "+ent);
			}
		}
		org.junit.Assert.assertEquals("two", ent2.getValue());
		org.junit.Assert.assertEquals("three", ent3.getValue());

		Object[] arr3 = hmap.entrySet().toArray(new Object[1]);
		org.junit.Assert.assertEquals(5, arr3.length);
		org.junit.Assert.assertFalse(arr3 == arr2);

		// call toArray() with a larger array than required
		hmap.remove(3); hmap.remove(4); hmap.remove(5); hmap.remove(6);
		org.junit.Assert.assertEquals(1, hmap.size());
		org.junit.Assert.assertTrue(hmap.containsKey(2));
		Object[] arr4 = new Object[3];
		java.util.Arrays.fill(arr4, "marker");
		Object[] arr4b = hmap.entrySet().toArray(arr4);
		org.junit.Assert.assertSame(arr4, arr4b);
		@SuppressWarnings("unchecked") int key0 = ((java.util.Map.Entry<Integer, String>)arr4[0]).getKey().intValue();
		@SuppressWarnings("unchecked") String val0 = ((java.util.Map.Entry<Integer, String>)arr4[0]).getValue();
		org.junit.Assert.assertEquals(2, key0);
		org.junit.Assert.assertEquals(hmap.get(2), val0);
		org.junit.Assert.assertNull(arr4[1]);
	}

	@org.junit.Test
	final public void testConcurrentModification()
	{
		verifyConcurrentModification(ModType.MODTYP_ADD);
		verifyConcurrentModification(ModType.MODTYP_DEL);
		verifyConcurrentModification(ModType.MODTYP_CLR);
		verifyConcurrentModification(ModType.MODTYP_TRIM);
	}

	private void verifyConcurrentModification(ModType modtype)
	{
		final java.util.Map<String,String> hmap = allocMap(-1, 0);
		hmap.put("1", "one");
		hmap.put("2", "two");
		hmap.put("3", "three");
		hmap.put("4", "four");
		int expsize = hmap.size();

		java.util.Set<java.util.Map.Entry<String,String>> ecoll = hmap.entrySet();
		java.util.Set<String> kcoll = hmap.keySet();
		java.util.Collection<String> vcoll = hmap.values();
		java.util.Iterator<java.util.Map.Entry<String,String>> eit = ecoll.iterator();
		java.util.Iterator<String> kit = kcoll.iterator();
		java.util.Iterator<String> vit = vcoll.iterator();

		eit.next(); kit.next(); vit.next();
		hmap.put("3", "newval1"); //allowed
		eit.next(); kit.next(); vit.next();

		switch (modtype) {
		case MODTYP_ADD:
			hmap.put("5", "five");
			expsize++;
			break;
		case MODTYP_DEL:
			hmap.remove("3");
			expsize--;
			break;
		case MODTYP_CLR:
			hmap.clear();
			expsize = 0;
			break;
		case MODTYP_TRIM:
			if (trimToSize(hmap) == -1) return;
			break;
		}

		try {
			eit.next();
			org.junit.Assert.fail("Failed to trap concurrent "+modtype+" on entries iterator");
		} catch (java.util.ConcurrentModificationException ex) {}
		try {
			eit.remove();
			org.junit.Assert.fail("Failed to trap concurrent "+modtype+" on entries iterator-remove");
		} catch (java.util.ConcurrentModificationException ex) {}

		try {
			kit.next();
			org.junit.Assert.fail("Failed to trap concurrent "+modtype+" on keys iterator");
		} catch (java.util.ConcurrentModificationException ex) {}
		try {
			kit.remove();
			org.junit.Assert.fail("Failed to trap concurrent "+modtype+" on keys iterator-remove");
		} catch (java.util.ConcurrentModificationException ex) {}

		try {
			vit.next();
			org.junit.Assert.fail("Failed to trap concurrent "+modtype+" on values iterator");
		} catch (java.util.ConcurrentModificationException ex) {}
		try {
			vit.remove();
			org.junit.Assert.fail("Failed to trap concurrent "+modtype+" on values iterator-remove");
		} catch (java.util.ConcurrentModificationException ex) {}

		org.junit.Assert.assertTrue(eit.hasNext());
		org.junit.Assert.assertTrue(kit.hasNext());
		org.junit.Assert.assertTrue(vit.hasNext());

		org.junit.Assert.assertEquals(expsize, hmap.size());
		org.junit.Assert.assertEquals(expsize, ecoll.size());
		org.junit.Assert.assertEquals(expsize, kcoll.size());
		org.junit.Assert.assertEquals(expsize, vcoll.size());
	}

	@org.junit.Test
	final public void testGrow()
	{
		org.junit.Assume.assumeTrue(maptype != MapTypeID.MAPTYP_JDK);
		final java.util.Map<String,String> hmap = allocMap(3, 3);
		final int cap1 = getCapacity(hmap);
		int cnt = 0;
		String str1 = null;
		String strlast = null;

		while (getCapacity(hmap) == cap1) {
			String str = String.valueOf(++cnt);
			hmap.put(str, str+"v");
			if (str1 == null) str1 = str;
			strlast = str;
		}
		final int cap2 = getCapacity(hmap);
		org.junit.Assert.assertTrue(hmap.containsKey(str1));
		org.junit.Assert.assertTrue(hmap.containsValue(str1+"v"));
		org.junit.Assert.assertEquals(str1+"v", hmap.get(str1));
		org.junit.Assert.assertTrue(hmap.containsKey(strlast));
		org.junit.Assert.assertTrue(hmap.containsValue(strlast+"v"));
		org.junit.Assert.assertEquals(strlast+"v", hmap.get(strlast));

		int cap3 = trimToSize(hmap);
		if (cap3 != -1) org.junit.Assert.assertEquals(cap2, cap3);

		hmap.remove(String.valueOf(1));
		verifySize(hmap, cnt - 1);
		org.junit.Assert.assertEquals(getCapacity(hmap), cap2);

		hmap.clear();
		verifySize(hmap, 0);
		org.junit.Assert.assertEquals(getCapacity(hmap), cap2);
		cap3 = trimToSize(hmap);
		if (cap3 != -1) org.junit.Assert.assertEquals(1, cap3);
	}

	// This is a code-coverage test, to test growth of a bucket, rather than growth in the number of buckets
	@org.junit.Test
	final public void testGrowBucket()
	{
		int cnt = 512;
		java.util.Map<String,String> hmap = allocMap(2, cnt);
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
		java.util.Map<String,String> hmap = allocMap(3, 3);
		hmap.put("1", "1v");
		hmap.put("2", "2v");
		hmap.put("3", "3v");
		int itercnt_exp = hmap.size();

		java.util.Iterator<String> iter = getRecycledKeysIterator(hmap);
		if (iter != null) {
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
			java.util.Iterator<String> iter2 = getRecycledKeysIterator(hmap);
			org.junit.Assert.assertTrue(iter == iter2);
			org.junit.Assert.assertTrue(iter2.hasNext());
		}

		iter = getRecycledValuesIterator(hmap);
		if (iter != null) {
			org.junit.Assert.assertFalse(iter == hmap.values().iterator());
			int itercnt = 0;
			boolean found_it = false;
			while (iter.hasNext()) {
				String str = iter.next();
				if (str == "2v") found_it = true;
				itercnt++;
			}
			org.junit.Assert.assertEquals(itercnt, itercnt_exp);
			org.junit.Assert.assertTrue(found_it);
			java.util.Iterator<String> iter2 = getRecycledValuesIterator(hmap);
			org.junit.Assert.assertTrue(iter == iter2);
			org.junit.Assert.assertTrue(iter2.hasNext());
		}

		java.util.Iterator<java.util.Map.Entry<String,String>> it = getRecycledEntriesIterator(hmap);
		if (it != null) {
			org.junit.Assert.assertFalse(it == hmap.entrySet().iterator());
			int itercnt = 0;
			boolean found_it = false;
			while (it.hasNext()) {
				java.util.Map.Entry<String,String> ent = it.next();
				if (ent.getKey() == "2") {
					found_it = true;
					org.junit.Assert.assertTrue(ent.getValue() == "2v");
				}
				itercnt++;
			}
			org.junit.Assert.assertEquals(itercnt, itercnt_exp);
			org.junit.Assert.assertTrue(found_it);
			java.util.Iterator<java.util.Map.Entry<String,String>> it2 = getRecycledEntriesIterator(hmap);
			org.junit.Assert.assertTrue(it == it2);
			org.junit.Assert.assertTrue(it2.hasNext());
		}
	}

	@org.junit.Test
	final public void bulktest()
	{
		java.text.DecimalFormat formatter = new java.text.DecimalFormat("#,###");
		long total_duration = 0;
		long min_duration = Long.MAX_VALUE;
		long[] duration = new long[SOAK_RUNS];
		int warmuploops = SOAK_WARMUPS;
		for (int loop = 0; loop != warmuploops + duration.length; loop++) {
			long time1 = System.nanoTime();
			runSoak();
			if (loop < warmuploops) continue;
			int idx = loop - warmuploops;
			duration[idx] = System.nanoTime() - time1;
			if (duration[idx] < min_duration) min_duration = duration[idx];
			total_duration += duration[idx];
		}
		System.out.print("Map="+maptype+" bulktest: ");
		String dlm = "";
		for (int loop = 0; loop != duration.length; loop++) {
			System.out.print(dlm+formatter.format(duration[loop]));
			dlm = " : ";
		}
		System.out.println(" - Avg="+formatter.format(total_duration/duration.length)
								+", Min="+formatter.format(min_duration));
	}

	private void runSoak()
	{
		final int cap = SOAK_SIZE; //ramp up to investigate manually
		final String nullvalkey = "0";
		final Integer nullval = Integer.valueOf(cap * 2);
		final java.util.Map<String,Integer> map = allocMapIntValue(0, 5);
		int alloc_cnt = -1;
		// general put-get
		map.put(nullvalkey, null);
		for (int k = 1; k != cap; k++) {
			String ks = String.valueOf(k);
			Integer v = Integer.valueOf(k);
			Integer old = map.put(ks, v);
			org.junit.Assert.assertNull(old);
			old = map.put(ks, v);
			org.junit.Assert.assertTrue(old == v);
		}
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertEquals(null, map.remove(null));
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertEquals(null, map.remove(String.valueOf(cap+100)));
		org.junit.Assert.assertEquals(cap, map.size());
		org.junit.Assert.assertNull(map.put(null, nullval));
		org.junit.Assert.assertEquals(cap+1, map.size());
		org.junit.Assert.assertEquals(cap+1, map.size());
		if (maptype == MapTypeID.MAPTYP_HMENT) {
			alloc_cnt = (Integer)com.grey.base.utils.DynLoader.getField(map, "alloc_count");
			org.junit.Assert.assertEquals(cap+1, alloc_cnt);
		}
		org.junit.Assert.assertTrue(map.containsKey(null));
		org.junit.Assert.assertTrue(map.containsKey(nullvalkey));
		org.junit.Assert.assertTrue(nullval == map.get(null));
		org.junit.Assert.assertNull(map.get(nullvalkey));
		verifySize(map, cap+1);
		// remove
		for (int k = 1; k != cap; k++) {
			String ks = String.valueOf(k);
			org.junit.Assert.assertTrue(map.containsKey(ks));
			org.junit.Assert.assertEquals(Integer.valueOf(k), map.get(ks));
			org.junit.Assert.assertEquals(Integer.valueOf(k), map.remove(ks));
			org.junit.Assert.assertFalse(map.containsKey(ks));
			org.junit.Assert.assertNull(map.remove(ks));
			org.junit.Assert.assertFalse(map.containsKey(ks));
		}
		verifySize(map, 2);
		org.junit.Assert.assertTrue(nullval == map.remove(null));
		org.junit.Assert.assertFalse(map.containsKey(null));
		org.junit.Assert.assertNull(map.remove(null));
		org.junit.Assert.assertFalse(map.containsKey(null));
		org.junit.Assert.assertNull(map.remove(nullvalkey));
		org.junit.Assert.assertFalse(map.containsKey(nullvalkey));
		org.junit.Assert.assertNull(map.remove(nullvalkey));
		org.junit.Assert.assertFalse(map.containsKey(nullvalkey));
		verifySize(map, 0);
		// clear
		for (int k = 1; k != cap; k++) map.put(String.valueOf(k), Integer.valueOf(k));
		map.put(null, nullval);
		map.put(nullvalkey, null);
		map.clear();
		verifySize(map, 0);
		// Keys view
		for (int k = 1; k != cap; k++) map.put(String.valueOf(k), Integer.valueOf(k));
		map.put(null, nullval);
		map.put(nullvalkey, null);
		java.util.HashSet<String> jset = new java.util.HashSet<String>();
		java.util.Set<String> kset = map.keySet();
		java.util.Iterator<String> kit = kset.iterator();
		org.junit.Assert.assertEquals(cap+1, kset.size());
		org.junit.Assert.assertEquals(cap+1, map.size());
		while (kit.hasNext()) {
			jset.add(kit.next());
			kit.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(0, kset.size());
		org.junit.Assert.assertEquals(cap+1, jset.size());
		for (int k = 0; k != cap; k++) org.junit.Assert.assertTrue(jset.contains(String.valueOf(k)));
		org.junit.Assert.assertTrue(jset.contains(null));
		verifySize(map, 0);
		// Map.Entry view
		for (int k = 1; k != cap; k++) map.put(String.valueOf(k), Integer.valueOf(k));
		map.put(null, nullval);
		map.put(nullvalkey, null);
		java.util.Set<java.util.Map.Entry<String, Integer>> eset = map.entrySet();
		java.util.Iterator<java.util.Map.Entry<String,Integer>> mit = eset.iterator();
		org.junit.Assert.assertEquals(cap+1, eset.size());
		org.junit.Assert.assertEquals(cap+1, map.size());
		jset.clear();
		while (mit.hasNext()) {
			jset.add(mit.next().getKey());
			mit.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(0, eset.size());
		org.junit.Assert.assertEquals(cap+1, jset.size());
		for (int k = 0; k != cap; k++) org.junit.Assert.assertTrue(jset.contains(String.valueOf(k)));
		org.junit.Assert.assertTrue(jset.contains(null));
		org.junit.Assert.assertTrue(jset.contains(nullvalkey));
		// Values view
		for (int k = 1; k != cap; k++) map.put(String.valueOf(k), Integer.valueOf(k));
		map.put(null, nullval);
		map.put(nullvalkey, null);
		java.util.Collection<Integer> vcoll = map.values();
		java.util.Iterator<Integer> vit = vcoll.iterator();
		org.junit.Assert.assertEquals(cap+1, vcoll.size());
		org.junit.Assert.assertEquals(cap+1, map.size());
		for (int v = 1; v != 10; v++) org.junit.Assert.assertTrue(vcoll.contains(Integer.valueOf(v))); //slow linear scan
		for (int v = cap - 10; v != cap; v++) org.junit.Assert.assertTrue(vcoll.contains(Integer.valueOf(v)));
		org.junit.Assert.assertTrue(vcoll.contains(null));
		org.junit.Assert.assertTrue(vcoll.contains(nullval));
		org.junit.Assert.assertFalse(vcoll.contains(Integer.valueOf(0)));
		java.util.HashSet<Integer> jvset = new java.util.HashSet<Integer>();
		while (vit.hasNext()) {
			jvset.add(vit.next());
			vit.remove();
		}
		org.junit.Assert.assertEquals(0, map.size());
		org.junit.Assert.assertEquals(0, vcoll.size());
		org.junit.Assert.assertEquals(cap+1, jvset.size());
		for (int v = 1; v != cap; v++) org.junit.Assert.assertTrue(jvset.contains(Integer.valueOf(v)));
		org.junit.Assert.assertTrue(jvset.contains(null));
		org.junit.Assert.assertTrue(jvset.contains(nullval));
		org.junit.Assert.assertFalse(jvset.contains(Integer.valueOf(0)));
		//final check to make sure HMENT map doesn't leak entries
		if (maptype == MapTypeID.MAPTYP_HMENT) {
			int alloc_cnt2 = (Integer)com.grey.base.utils.DynLoader.getField(map, "alloc_count");
			org.junit.Assert.assertEquals(alloc_cnt, alloc_cnt2);
		}
	}

	private String addEntry(java.util.Map<String,String> hmap, String key, String val, boolean isnew, String oldval_exp)
	{
		int size = hmap.size();
		if (isnew) {
			org.junit.Assert.assertFalse(hmap.containsKey(key));
			org.junit.Assert.assertNull(hmap.get(key));
			size++; // size will increase
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

	private String deleteEntry(java.util.Map<String,String> hmap, String key, boolean exists, String val)
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

	private void verifySize(java.util.Map<?, ?> map, int size)
	{
		org.junit.Assert.assertEquals(size, map.size());
		org.junit.Assert.assertEquals(size == 0, map.isEmpty());
		if (maptype == MapTypeID.MAPTYP_HMKV) {
			verifySize((HashedMap<?, ?>)map);
		} else if (maptype == MapTypeID.MAPTYP_HMENT) {
			verifySize((HashedMapEntries<?, ?>)map);
		}
	}

	private void verifySize(HashedMap<?, ?> map)
	{
		Object[][] buckets = (Object[][])com.grey.base.utils.DynLoader.getField(map, "buckets");
		int[] bucketsizes = (int[])com.grey.base.utils.DynLoader.getField(map, "bucketsizes");
		org.junit.Assert.assertEquals(getCapacity(map), buckets.length);
		org.junit.Assert.assertEquals(buckets.length, bucketsizes.length);
		int cnt = 0;

		for (int idx = 0; idx != buckets.length; idx++) {
			Object[] bucket = buckets[idx];
			int cnt2 = bucketsizes[idx];
			if (bucket == null) {
				org.junit.Assert.assertEquals(0, cnt2);
				continue;
			}
			for (int idx2 = 0; idx2 != bucket.length; idx2++) {
				if (idx2 > cnt2) org.junit.Assert.assertNull(bucket[idx2]);
			}
			if (cnt2 % 2 != 0) org.junit.Assert.fail("Uneven Key/Value counts - "+cnt2+"/"+bucket.length);
			cnt += (cnt2 / 2);
		}
		org.junit.Assert.assertEquals(map.size(), cnt);
	}

	private void verifySize(HashedMapEntries<?, ?> map)
	{
		java.util.Map.Entry<?,?>[][] buckets = (java.util.Map.Entry<?,?>[][])com.grey.base.utils.DynLoader.getField(map, "buckets");
		int[] bucketsizes = (int[])com.grey.base.utils.DynLoader.getField(map, "bucketsizes");
		org.junit.Assert.assertEquals(getCapacity(map), buckets.length);
		org.junit.Assert.assertEquals(buckets.length, bucketsizes.length);
		int cnt = 0;

		for (int idx = 0; idx != buckets.length; idx++) {
			java.util.Map.Entry<?,?>[] bucket = buckets[idx];
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
		org.junit.Assert.assertEquals(map.size(), cnt);
	}

	private int getCapacity(java.util.Map<?, ?> hmap)
	{
		if (maptype == MapTypeID.MAPTYP_JDK) return hmap.size();
		return Integer.class.cast(com.grey.base.utils.DynLoader.getField(hmap, "capacity")).intValue();
	}

	private java.util.Map<String,String> allocMap(int initcap, float factor) {
		switch (maptype) {
		case MAPTYP_JDK:
			if (initcap == -1) return new java.util.HashMap<String,String>();
			return new java.util.HashMap<String,String>(initcap, factor);
		case MAPTYP_HMKV:
			if (initcap == -1) return new HashedMap<String,String>();
			return new HashedMap<String,String>(initcap, factor);
		case MAPTYP_HMENT:
			if (initcap == -1) return new HashedMapEntries<String,String>();
			return new HashedMapEntries<String,String>(initcap, factor);
		default:
			throw new IllegalStateException("Missing case for maptype="+maptype);
		}
	}

	private java.util.Map<Integer,String> allocMapIntKey(int initcap, float factor) {
		switch (maptype) {
		case MAPTYP_JDK:
			return new java.util.HashMap<Integer,String>(initcap, factor);
		case MAPTYP_HMKV:
			return new HashedMap<Integer,String>(initcap, factor);
		case MAPTYP_HMENT:
			return new HashedMapEntries<Integer,String>(initcap, factor);
		default:
			throw new IllegalStateException("Missing case for maptype="+maptype);
		}
	}

	private java.util.Map<String,Integer> allocMapIntValue(int initcap, float factor) {
		switch (maptype) {
		case MAPTYP_JDK:
			return new java.util.HashMap<String,Integer>(initcap, factor);
		case MAPTYP_HMKV:
			return new HashedMap<String,Integer>(initcap, factor);
		case MAPTYP_HMENT:
			return new HashedMapEntries<String,Integer>(initcap, factor);
		default:
			throw new IllegalStateException("Missing case for maptype="+maptype);
		}
	}

	private java.util.Iterator<String> getRecycledKeysIterator(java.util.Map<String,String> map) {
		switch (maptype) {
		case MAPTYP_HMKV:
			return ((HashedMap<String,String>)map).keysIterator();
		case MAPTYP_HMENT:
			return ((HashedMapEntries<String,String>)map).keysIterator();
		default:
			return null;
		}
	}

	private java.util.Iterator<String> getRecycledValuesIterator(java.util.Map<String,String> map) {
		switch (maptype) {
		case MAPTYP_HMKV:
			return ((HashedMap<String,String>)map).valuesIterator();
		case MAPTYP_HMENT:
			return ((HashedMapEntries<String,String>)map).valuesIterator();
		default:
			return null;
		}
	}

	private java.util.Iterator<java.util.Map.Entry<String,String>> getRecycledEntriesIterator(java.util.Map<String,String> map) {
		switch (maptype) {
		case MAPTYP_HMKV:
			return ((HashedMap<String,String>)map).entriesIterator();
		case MAPTYP_HMENT:
			return ((HashedMapEntries<String,String>)map).entriesIterator();
		default:
			return null;
		}
	}

	private int trimToSize(java.util.Map<String,String> map) {
		switch (maptype) {
		case MAPTYP_HMKV:
			return ((HashedMap<String,String>)map).trimToSize();
		case MAPTYP_HMENT:
			return ((HashedMapEntries<String,String>)map).trimToSize();
		default:
			return -1;
		}
	}

	private static final class MyMapEntry
		extends java.util.AbstractMap.SimpleEntry<String, String>
	{
		private static final long serialVersionUID = 1L;
		MyMapEntry(String k, String v) {super(k,v);}
		MyMapEntry(java.util.Map.Entry<String,String> e) {super(e);}
	}
}
