/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

public class CirculistTest
{
	private Circulist<String> clst;
	private final java.lang.reflect.Field fld_head = getClassField("head");
	private final java.lang.reflect.Field fld_tail = getClassField("tail");
	private final java.lang.reflect.Field fld_count = getClassField("count");
	private final java.lang.reflect.Field fld_arr = getClassField("buffer");

	@org.junit.Test
	public void testOrdering()
	{
		clst = new Circulist<>();
		org.junit.Assert.assertEquals(0, clst.size());
		org.junit.Assert.assertTrue(clst.toString(), clst.toString().contains("=0/cap=64/head=0/tail=-1"));
		clst.prepend("1a");
		clst.append("1b");
		clst.prepend("1c");
		verifyOrder(new String[]{"1c", "1a", "1b"});
		org.junit.Assert.assertEquals(0, clst.indexOf("1c"));
		org.junit.Assert.assertEquals(1, clst.indexOf("1a"));
		org.junit.Assert.assertEquals(2, clst.indexOf("1b"));
		org.junit.Assert.assertEquals(-1, clst.indexOf("1"));
		org.junit.Assert.assertEquals(-1, clst.indexOf(1, "1c"));
		org.junit.Assert.assertEquals(1, clst.indexOf(1, "1a"));
		org.junit.Assert.assertEquals(2, clst.indexOf(1, "1b"));
		org.junit.Assert.assertTrue(clst.toString(), clst.toString().contains("="+clst.size()+"/cap=64/head=63/tail=1"));
		clst.insert(0, "2a");
		clst.insert(clst.size(), "2b");
		clst.insert(2, "2c");
		verifyOrder(new String[]{"2a", "1c", "2c", "1a", "1b", "2b"});
		String str = clst.remove(0);
		org.junit.Assert.assertEquals("2a", str);
		org.junit.Assert.assertEquals("1c", clst.get(0));
		str = clst.remove(clst.size() - 1);
		org.junit.Assert.assertEquals("2b", str);
		org.junit.Assert.assertEquals("1b", clst.get(clst.size() - 1));
		str = clst.remove(2);
		org.junit.Assert.assertEquals("1a", str);
		verifyOrder(new String[]{"1c", "2c", "1b"});
		clst.append("3a");
		clst.append("3b");
		clst.append("3c"); //pad out population a bit - enough to wrap
		clst.removeRange(1, 3);
		verifyOrder(new String[]{"1c", "3b", "3c"});
		str = clst.remove();
		org.junit.Assert.assertEquals("1c", str);
		verifyOrder(new String[]{"3b", "3c"});
	}

	@org.junit.Test
	public void testWrap() throws Exception
	{
		clst = new Circulist<>(5, 2);
		org.junit.Assert.assertEquals(0, clst.size());
		//populate to capacity
		clst.append("One");
		clst.insert(1, "Two");
		clst.append("Three");
		clst.insert(1, "Two-B");
		clst.append("Four");
		clst.set(clst.indexOf("Two-B"), "Two-C");
		verifyState(5, 5, false);
		org.junit.Assert.assertTrue(clst.get(0).equals("One"));
		org.junit.Assert.assertTrue(clst.get(1).equals("Two-C"));
		org.junit.Assert.assertTrue(clst.get(2).equals("Two"));
		org.junit.Assert.assertTrue(clst.get(3).equals("Three"));
		org.junit.Assert.assertTrue(clst.get(4).equals("Four"));
		clst.remove();
		verifyState(4, 5, false);
		org.junit.Assert.assertTrue(clst.get(0).equals("Two-C"));
		clst.insert(0, "One-B");
		verifyState(5, 5, false);
		org.junit.Assert.assertTrue(clst.get(0).equals("One-B"));
		verifyOrder(new String[]{"One-B", "Two-C", "Two", "Three", "Four"});
		String str = clst.remove(4);
		org.junit.Assert.assertEquals("Four", str);
		str = clst.remove(1);
		org.junit.Assert.assertEquals("Two-C", str);
		verifyState(3, 5, false);
		verifyOrder(new String[]{"One-B", "Two", "Three"});
		str = clst.remove(0);
		org.junit.Assert.assertEquals("One-B", str);
		str = clst.remove(0);
		org.junit.Assert.assertEquals("Two", str);
		verifyState(1, 5, false);
		org.junit.Assert.assertEquals("Three", clst.get(0));
		clst.append("Four-B");
		clst.append("Five-B");
		verifyState(3, 5, false);
		clst.append("Seven");  //tail advances, and we wrap
		verifyState(4, 5, true);
		clst.insert(3, "Six");
		verifyState(5, 5, true);
		verifyOrder(new String[]{"Three", "Four-B", "Five-B", "Six", "Seven"});
		clst.prepend("One-C");  //head retreats after grow-shift, and we wrap again
		clst.append("Eight");
		verifyOrder(new String[]{"One-C", "Three", "Four-B", "Five-B", "Six", "Seven", "Eight"});
		verifyState(7, 7, true);
		clst.append("Ten"); //list grows, and wrapped condition ends
		clst.insert(clst.size() - 2, "Nine");
		verifyState(9, 9, false);
		verifyOrder(new String[]{"One-C", "Three", "Four-B", "Five-B", "Six", "Seven", "Nine", "Eight", "Ten"});
		clst.insert(7, "Eight-B");
		verifyState(10, 11, false);
		verifyOrder(new String[]{"One-C", "Three", "Four-B", "Five-B", "Six", "Seven", "Nine", "Eight-B", "Eight", "Ten"});
		clst.removeRange(2, 4);
		verifyState(7, 11, false);
		verifyOrder(new String[]{"One-C", "Three", "Seven", "Nine", "Eight-B", "Eight", "Ten"});
		clst.removeRange(0, 0);
		verifyState(6, 11, false);
		verifyOrder(new String[]{"Three", "Seven", "Nine", "Eight-B", "Eight", "Ten"});
		clst.clear();
		verifyState(0, 11, false);
		verifyOrder(new String[0]);
		clst.insert(0, "One");
		verifyState(1, 11, false);
		clst.insert(0, "Two");
		verifyState(2, 11, true);
		clst.insert(0, "Three");
		clst.insert(0, "Four");
		clst.insert(0, "Five");
		clst.insert(0, "Six");
		clst.insert(0, "Seven");
		clst.insert(0, "Eight");
		clst.insert(1, "Nine");
		verifyState(9, 11, true);
		verifyOrder(new String[]{"Eight", "Nine", "Seven", "Six", "Five", "Four", "Three", "Two", "One"});
		boolean b = clst.remove("NoSuchMember");
		org.junit.Assert.assertFalse(b);
		verifyState(9, 11, true);
		b = clst.remove("Nine");
		org.junit.Assert.assertTrue(b);
		verifyState(8, 11, true);
		verifyOrder(new String[]{"Eight", "Seven", "Six", "Five", "Four", "Three", "Two", "One"});
		b = clst.remove("One");  //tail was at 0, so now it wraps back to end of array
		org.junit.Assert.assertTrue(b);
		verifyState(7, 11, false);
		verifyOrder(new String[]{"Eight", "Seven", "Six", "Five", "Four", "Three", "Two"});
	}

	@org.junit.Test
	public void testInitialEmpty() throws Exception
	{
		int incr = 5;
		clst = new Circulist<>(0, incr);
		org.junit.Assert.assertNull(clst.remove());
		org.junit.Assert.assertEquals(0, clst.size());
		org.junit.Assert.assertEquals(-1, clst.indexOf("fred"));
		org.junit.Assert.assertFalse(clst.remove("fred"));
		org.junit.Assert.assertEquals(0, clst.size());
		org.junit.Assert.assertEquals(0, clst.capacity());
		clst.append("bob");
		org.junit.Assert.assertEquals(1, clst.size());
		org.junit.Assert.assertTrue(clst.remove("bob"));
		org.junit.Assert.assertFalse(clst.remove("bob"));
		org.junit.Assert.assertEquals(0, clst.size());
		org.junit.Assert.assertEquals(incr, clst.capacity());
	}

	@org.junit.Test
	public void testViolations() throws Exception
	{
		clst = new Circulist<>();
		clst.append("One");
		try {
			clst.get(-1);
			org.junit.Assert.fail("Failed to trap negative index");
		} catch (IllegalArgumentException ex) {}
		try {
			clst.get(clst.size());
			org.junit.Assert.fail("Failed to trap large index");
		} catch (IllegalArgumentException ex) {}
		clst.clear();
		try {
			clst.get(0);
			org.junit.Assert.fail("Failed to trap get-0 on empty");
		} catch (IllegalArgumentException ex) {}
		try {
			clst.remove(0);
			org.junit.Assert.fail("Failed to trap remove-0 on empty");
		} catch (IllegalArgumentException ex) {}
		// but this remove() variant is allowed
		org.junit.Assert.assertNull(clst.remove());
	}

	private void verifyOrder(String[] items)
	{
		org.junit.Assert.assertEquals(items.length, clst.size());
		for (int idx = 0; idx != items.length; idx++) {
			org.junit.Assert.assertEquals(items[idx], clst.get(idx));
		}
		String[] arr = clst.toArray(new String[0]);
		org.junit.Assert.assertEquals(items.length, arr.length);
		for (int idx = 0; idx != items.length; idx++) {
			org.junit.Assert.assertEquals(items[idx], arr[idx]);
		}
	}

	private void verifyState(int size, int cap, boolean expectWrapped) throws IllegalAccessException
	{
		int head = fld_head.getInt(clst);
		int tail = fld_tail.getInt(clst);
		int count = fld_count.getInt(clst);
		Object[] arr = (Object[])fld_arr.get(clst);
		boolean isWrapped = (count != 0 && tail < head);
		String context = "count="+count+"/"+arr.length+", head="+head+", tail="+tail+"- wrapped="+isWrapped;

		org.junit.Assert.assertEquals(context, size, count);
		org.junit.Assert.assertEquals(context, arr.length, cap);
		if (expectWrapped) org.junit.Assert.assertTrue(context, isWrapped);
		if (!expectWrapped) org.junit.Assert.assertFalse(context, isWrapped);

		// vacant slots need to be nulled, so verify this is the case
		for (int idx = 0; idx != arr.length; idx++)
		{
			boolean vacant = true;
			if (count != 0) {
				if (idx >= head) {
					int lmt = head + count;
					if (lmt > arr.length) lmt = arr.length;
					if (idx < lmt) vacant = false;
				} else if (idx <= tail && tail < head) {
					vacant = false;
				}
			}
			String context2 = "@idx="+idx+": "+context;
			if (vacant) {
				org.junit.Assert.assertNull("Stale ref"+context2, arr[idx]);
			} else {
				org.junit.Assert.assertNotNull("Null ref"+context2, arr[idx]);
			}
		}
	}

	private java.lang.reflect.Field getClassField(String name)
	{
		try {
			java.lang.reflect.Field fld = Circulist.class.getDeclaredField(name);
			fld.setAccessible(true);
			return fld;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get field="+name, ex);
		}
	}
}
