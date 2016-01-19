/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class HashedSetTest
{
	private static final int SOAK_SIZE = 10*1000;
	private static final int SOAK_RUNS = 5;
	private static final int SOAK_WARMUPS = 20;

	private enum SetTypeID {STYP_JDK, STYP_GREY}

	@org.junit.runners.Parameterized.Parameters(name="{0}")
	public static java.util.Collection<SetTypeID[]> generateParams() {
		return java.util.Arrays.asList(new SetTypeID[][] {{SetTypeID.STYP_GREY}, {SetTypeID.STYP_JDK}});
	}

	private final SetTypeID stype;
	public HashedSetTest(SetTypeID t) {stype = t;}

	@org.junit.Test
	public void testEmpty()
	{
		java.util.Set<String> hset = allocSet(-1, 0);
		org.junit.Assert.assertEquals(0, hset.size());
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertFalse(hset.remove("nonsuch"));
		org.junit.Assert.assertFalse(hset.contains("nonsuch"));
		org.junit.Assert.assertFalse(hset.remove(null));
		org.junit.Assert.assertFalse(hset.contains(null));
		org.junit.Assert.assertFalse(hset.remove(new Exception())); //test bad type
		org.junit.Assert.assertFalse(hset.contains(new Exception()));
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
		org.junit.Assert.assertFalse(arr2 == arr);

		String[] arr3 = hset.toArray(new String[2]);
		java.util.Arrays.fill(arr3, "marker");
		String[] arr3b = hset.toArray(arr3);
		org.junit.Assert.assertTrue(arr3 == arr3b);
		org.junit.Assert.assertNull(arr3[0]);
		org.junit.Assert.assertFalse(arr2 == arr3);
	}

	@org.junit.Test
	public void testRegular()
	{
		java.util.Set<String> hset = allocSet(1, 1);
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertNotNull(hset.toString()); //for sake of code coverage
		org.junit.Assert.assertTrue(hset.add("one"));
		org.junit.Assert.assertFalse(hset.isEmpty());
		org.junit.Assert.assertTrue(hset.add("two"));
		org.junit.Assert.assertTrue(hset.add("three"));
		org.junit.Assert.assertEquals(3, hset.size());
		org.junit.Assert.assertFalse(hset.add("one"));
		org.junit.Assert.assertEquals(3, hset.size());
		org.junit.Assert.assertNotNull(hset.toString());

		org.junit.Assert.assertTrue(hset.remove("one"));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertFalse(hset.remove("one"));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertFalse(hset.contains("one"));
		org.junit.Assert.assertTrue(hset.contains("two"));
		org.junit.Assert.assertTrue(hset.contains("three"));

		//test the HashedSet-only get() method
		if (stype == SetTypeID.STYP_GREY) {
			HashedSet<String> hs = (HashedSet<String>)hset;
			String str = hs.get("two");
			org.junit.Assert.assertTrue(str == "two");
			String dup = new String("two");
			str = hs.get(dup);
			org.junit.Assert.assertFalse(str == dup);
			org.junit.Assert.assertTrue(str == "two");
			str = hs.get("nosuchvalue");
			org.junit.Assert.assertNull(str);
		}

		final java.util.Set<String> jset_orig = new java.util.HashSet<String>();
		jset_orig.addAll(hset);
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertEquals(hset.size(), jset_orig.size());
		org.junit.Assert.assertTrue(jset_orig.contains("two"));
		org.junit.Assert.assertTrue(jset_orig.contains("three"));

		String[] arr1 = hset.toArray(new String[5]);
		java.util.Arrays.fill(arr1, "marker");
		String[] arr1b = hset.toArray(arr1);
		org.junit.Assert.assertTrue(arr1 == arr1b);
		if ("two".equals(arr1[0])) {
			org.junit.Assert.assertSame("three", arr1[1]);
		} else {
			org.junit.Assert.assertSame("three", arr1[0]);
			org.junit.Assert.assertSame("two", arr1[1]);
		}
		org.junit.Assert.assertNull(arr1[2]);

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
			boolean exists = jset.remove(arr[idx]);
			org.junit.Assert.assertTrue(exists);
		}
		org.junit.Assert.assertEquals(0, jset.size());

		jset.addAll(jset_orig);
		String[] arr2 = hset.toArray(new String[0]);
		org.junit.Assert.assertEquals(2, arr2.length);
		for (int idx = 0; idx != arr.length; idx++) {
			boolean exists = jset.remove(arr[idx]);
			org.junit.Assert.assertTrue(exists);
		}
		org.junit.Assert.assertEquals(0, jset.size());

		hset.clear();
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertEquals(0, hset.size());
	}

	@org.junit.Test
	public void testNullMember()
	{
		java.util.Set<String> hset = allocSet(-1, 0);
		org.junit.Assert.assertTrue(hset.isEmpty());
		org.junit.Assert.assertTrue(hset.add(null));
		org.junit.Assert.assertFalse(hset.isEmpty());
		org.junit.Assert.assertEquals(1, hset.size());
		org.junit.Assert.assertTrue(hset.contains(null));
		org.junit.Assert.assertNotNull(hset.toString());
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

		org.junit.Assert.assertTrue(hset.add("one"));
		org.junit.Assert.assertTrue(hset.add(null));
		org.junit.Assert.assertEquals(2, hset.size());
		org.junit.Assert.assertTrue(hset.contains("one"));
		org.junit.Assert.assertTrue(hset.contains(null));

		if (stype == SetTypeID.STYP_GREY) {
			HashedSet<String> hs = (HashedSet<String>)hset;
			org.junit.Assert.assertNull(hs.get(null));
		}
	}

	@org.junit.Test
	public void testCollections()
	{
		java.util.Set<String> jset_orig = new java.util.HashSet<String>();
		jset_orig.add("one");
		jset_orig.add("two");
		jset_orig.add("three");

		java.util.Set<String> hset = allocSet(-1, 0);
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

	@org.junit.Test
	final public void testRecycledIterators()
	{
		if (stype != SetTypeID.STYP_GREY) return;
		HashedSet<Integer> hset = new HashedSet<Integer>();
		hset.add(1);
		hset.add(2);
		hset.add(3);
		int itercnt_exp = hset.size();

		java.util.Iterator<Integer> iter = hset.recycledIterator();
		int itercnt = 0;
		boolean found_it = false;
		while (iter.hasNext()) {
			int num = iter.next();
			if (num == 2) found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);

		java.util.Iterator<Integer> iter2 = hset.recycledIterator();
		org.junit.Assert.assertSame(iter, iter2);
		itercnt = 0;
		found_it = false;
		while (iter.hasNext()) {
			int num = iter.next();
			if (num == 2) found_it = true;
			itercnt++;
		}
		org.junit.Assert.assertEquals(itercnt, itercnt_exp);
		org.junit.Assert.assertTrue(found_it);
	}

	@org.junit.Test
	final public void testGrow()
	{
		if (stype == SetTypeID.STYP_JDK) return;
		final HashedSet<String> hset = new HashedSet<String>(3, 10);
		final int cap1 = getCapacity(hset);
		int cnt = 0;
		String str1 = null;
		String strlast = null;

		while (getCapacity(hset) == cap1) {
			String str = String.valueOf(++cnt);
			boolean isnew = hset.add(str);
			org.junit.Assert.assertTrue(isnew);
			if (str1 == null) str1 = str;
			strlast = str;
		}
		int cap2 = getCapacity(hset);
		int cap3 = hset.trimToSize();
		org.junit.Assert.assertEquals(cap2, cap3);
		org.junit.Assert.assertEquals(cap3, getCapacity(hset));
		org.junit.Assert.assertEquals(cap3, hset.trimToSize());
		org.junit.Assert.assertTrue(hset.contains(str1));
		org.junit.Assert.assertTrue(hset.contains(strlast));
		hset.clear();
		org.junit.Assert.assertEquals(1, hset.trimToSize());
		org.junit.Assert.assertEquals(1, getCapacity(hset));
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
		System.out.print("Set="+stype+" bulktest: ");
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
		java.util.Set<String> set = allocSet(0, 5);
		// general put-get
		for (int v = 0; v != cap; v++) {
			String s = String.valueOf(v);
			org.junit.Assert.assertTrue(set.add(s));
			org.junit.Assert.assertFalse(set.add(s));
		}
		org.junit.Assert.assertEquals(cap, set.size());
		for (int v = 0; v != cap; v++) org.junit.Assert.assertFalse(set.add(String.valueOf(v)));
		org.junit.Assert.assertEquals(cap, set.size());
		org.junit.Assert.assertFalse(set.remove(null));
		org.junit.Assert.assertEquals(cap, set.size());
		org.junit.Assert.assertFalse(set.remove(cap+1));
		org.junit.Assert.assertEquals(cap, set.size());
		org.junit.Assert.assertTrue(set.add(null));
		org.junit.Assert.assertEquals(cap+1, set.size());
		for (int v = 0; v != cap; v++) org.junit.Assert.assertTrue(set.contains(String.valueOf(v)));
		org.junit.Assert.assertTrue(set.contains(null));
		// iterators
		java.util.HashSet<String> jset = new java.util.HashSet<String>();
		java.util.Iterator<String> it = set.iterator();
		while (it.hasNext()) {
			jset.add(it.next());
			it.remove();
		}
		org.junit.Assert.assertEquals(0, set.size());
		org.junit.Assert.assertEquals(cap+1, jset.size());
		for (int v = 0; v != cap; v++) {
			String s = String.valueOf(v);
			org.junit.Assert.assertFalse(set.contains(s));
			org.junit.Assert.assertTrue(jset.contains(s));
		}
		org.junit.Assert.assertTrue(jset.contains(null));
		org.junit.Assert.assertFalse(set.contains(null));
		//restore and remove
		set.addAll(jset);
		org.junit.Assert.assertEquals(cap+1, jset.size());
		org.junit.Assert.assertEquals(cap+1, set.size());
		org.junit.Assert.assertTrue(set.contains(null));
		for (int v = 0; v != cap; v++) {
			String s = String.valueOf(v);
			org.junit.Assert.assertTrue(set.contains(s));
			org.junit.Assert.assertTrue(set.remove(s));
			org.junit.Assert.assertFalse(set.contains(s));
			org.junit.Assert.assertFalse(set.remove(s));
			org.junit.Assert.assertFalse(set.contains(s));
		}
		org.junit.Assert.assertEquals(1, set.size());
		org.junit.Assert.assertTrue(set.contains(null));
		org.junit.Assert.assertTrue(set.remove(null));
		org.junit.Assert.assertFalse(set.contains(null));
		org.junit.Assert.assertEquals(0, set.size());
		org.junit.Assert.assertEquals(cap+1, jset.size());
	}

	private java.util.Set<String> allocSet(int initcap, float factor) {
		switch (stype) {
		case STYP_JDK:
			if (initcap == -1) return new java.util.HashSet<String>();
			return new java.util.HashSet<String>(initcap, factor);
		case STYP_GREY:
			if (initcap == -1) return new HashedSet<String>();
			return new HashedSet<String>(initcap, factor);
		default:
			throw new IllegalStateException("Missing case for set-type="+stype);
		}
	}

	private static int getCapacity(HashedSet<?> hset)
	{
		HashedMapIntValue<?> map = (HashedMapIntValue<?>)com.grey.base.utils.DynLoader.getField(hset, "map");
		return Integer.class.cast(com.grey.base.utils.DynLoader.getField(map, "capacity")).intValue();
	}
}
