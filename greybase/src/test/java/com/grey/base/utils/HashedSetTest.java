/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class HashedSetTest
{
	@org.junit.Test
	public void testEmpty()
	{
		HashedSet<String> hset = new HashedSet<String>();
		org.junit.Assert.assertEquals(0, hset.size());
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertFalse(hset.remove("nonsuch"));
		org.junit.Assert.assertFalse(hset.contains("nonsuch"));
		org.junit.Assert.assertFalse(hset.remove(null));
		org.junit.Assert.assertFalse(hset.contains(null));
		hset.clear();
		org.junit.Assert.assertEquals(0, hset.size());
		org.junit.Assert.assertTrue(hset.isEmpty());

		java.util.Iterator<String> it = hset.iterator();
		int cnt = 0;
		while (it.hasNext()) {
			cnt++;
			it.next();
		}
		org.junit.Assert.assertEquals(0, cnt);

		Object[] arr = hset.toArray();
		org.junit.Assert.assertEquals(0, arr.length);

		String[] arr2 = hset.toArray(new String[hset.size()]);
		org.junit.Assert.assertEquals(0, arr2.length);
	}

	@org.junit.Test
	public void testRegular()
	{
		HashedSet<String> hset = new HashedSet<String>(1, 1);
		org.junit.Assert.assertTrue(hset.add("one"));
		org.junit.Assert.assertFalse(hset.isEmpty());
		org.junit.Assert.assertTrue(hset.add("two"));
		org.junit.Assert.assertTrue(hset.add("three"));
		org.junit.Assert.assertEquals(3, hset.size());
		org.junit.Assert.assertFalse(hset.add("one"));
		org.junit.Assert.assertEquals(3, hset.size());
    	hset.toString(); //for sake of code coverage

		org.junit.Assert.assertTrue(hset.remove("one"));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertFalse(hset.remove("one"));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertTrue(hset.contains("two"));
		org.junit.Assert.assertFalse(hset.contains("one"));

		java.util.Set<String> jset_orig = new java.util.HashSet<String>();
		jset_orig.add("two");
		jset_orig.add("three");

		java.util.Set<String> jset = new java.util.HashSet<String>();
		jset.addAll(jset_orig);
		java.util.Iterator<String> it = hset.iterator();
		int cnt = 0;
		while (it.hasNext()) {
			cnt++;
			jset.remove(it.next());
		}
		org.junit.Assert.assertEquals(2, cnt);
		org.junit.Assert.assertEquals(0, jset.size());

		jset.addAll(jset_orig);
		Object[] arr = hset.toArray();
		org.junit.Assert.assertEquals(2, arr.length);
		for (int idx = 0; idx != arr.length; idx++) {
			jset.remove(arr[idx]);
		}
		org.junit.Assert.assertEquals(0, jset.size());

		jset.addAll(jset_orig);
		String[] arr2 = hset.toArray(new String[hset.size()]);
		org.junit.Assert.assertEquals(2, arr2.length);
		for (int idx = 0; idx != arr.length; idx++) {
			jset.remove(arr[idx]);
		}
		org.junit.Assert.assertEquals(0, jset.size());

		hset.clear();
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertEquals(0, hset.size());
	}

	@org.junit.Test
	public void testNull()
	{
		HashedSet<String> hset = new HashedSet<String>();
		org.junit.Assert.assertTrue(hset.add(null));
		org.junit.Assert.assertFalse(hset.isEmpty());
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertTrue(hset.contains(null));
		org.junit.Assert.assertFalse(hset.add(null));
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertTrue(hset.contains(null));
		org.junit.Assert.assertTrue(hset.add("two"));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertTrue(hset.contains("two"));
		org.junit.Assert.assertTrue(hset.contains(null));

		org.junit.Assert.assertTrue(hset.remove(null));
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertFalse(hset.contains(null));
		org.junit.Assert.assertFalse(hset.remove(null));
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertTrue(hset.contains("two"));
		org.junit.Assert.assertTrue(hset.remove("two"));
		org.junit.Assert.assertFalse(hset.contains("two"));
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertEquals(0, hset.size());

		org.junit.Assert.assertTrue(hset.add(null));
		org.junit.Assert.assertFalse(hset.isEmpty());
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertTrue(hset.contains(null));

		java.util.Iterator<String> it = hset.iterator();
		int cnt = 0;
		while (it.hasNext()) {
			cnt++;
			org.junit.Assert.assertNull(it.next());
		}
		org.junit.Assert.assertEquals(1, cnt);

		Object[] arr = hset.toArray();
		org.junit.Assert.assertEquals(1, arr.length);
		for (int idx = 0; idx != arr.length; idx++) {
			org.junit.Assert.assertNull(arr[idx]);
		}

		String[] arr2 = hset.toArray(new String[hset.size()]);
		org.junit.Assert.assertEquals(1, arr2.length);
		for (int idx = 0; idx != arr.length; idx++) {
			org.junit.Assert.assertNull(arr[idx]);
		}

		hset.clear();
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertEquals(0, hset.size());
	}

	@org.junit.Test
	public void testCollections()
	{
		java.util.Set<String> jset_orig = new java.util.HashSet<String>();
		jset_orig.add("one");
		jset_orig.add("two");
		jset_orig.add("three");

		HashedSet<String> hset = new HashedSet<String>();
		java.util.Set<String> jset = new java.util.HashSet<String>();
		jset.addAll(jset_orig);
		org.junit.Assert.assertTrue(hset.addAll(jset));
		org.junit.Assert.assertEquals(3, hset.size());
		org.junit.Assert.assertTrue(hset.containsAll(jset));
		org.junit.Assert.assertFalse(hset.addAll(jset));
		org.junit.Assert.assertEquals(3, hset.size());
		org.junit.Assert.assertTrue(hset.containsAll(jset));

		jset.remove("one");
		org.junit.Assert.assertTrue(hset.retainAll(jset));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertTrue(hset.containsAll(jset));
		org.junit.Assert.assertFalse(hset.retainAll(jset));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertTrue(hset.containsAll(jset));

		hset.add("extra");
		org.junit.Assert.assertTrue(hset.removeAll(jset));
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertTrue(hset.contains("extra"));
		org.junit.Assert.assertFalse(hset.containsAll(jset));
		org.junit.Assert.assertFalse(hset.removeAll(jset));
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertTrue(hset.contains("extra"));
	}
}
