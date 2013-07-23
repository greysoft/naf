/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.util.Arrays;

import com.grey.base.config.SysProps;

public class ObjectPileTest
{
	@org.junit.Test
	public void lifecycle()
	{
		ObjectPile<String> pile = new ObjectPile<String>();
		org.junit.Assert.assertEquals(0, pile.size());
		org.junit.Assert.assertNull(pile.extract());
		String str = "One";
		pile.store(str);
		org.junit.Assert.assertEquals(1, pile.size());
		String str2 = pile.extract();
		org.junit.Assert.assertEquals(0, pile.size());
		org.junit.Assert.assertSame(str, str2);
		org.junit.Assert.assertNull(pile.extract());

		try {
			pile.store(null);
			org.junit.Assert.fail("ObjectPile failed to trap null object");
		} catch (NullPointerException ex) {}
		org.junit.Assert.assertEquals(0, pile.size());

		pile.store(str);
		org.junit.Assert.assertEquals(1, pile.size());
		try {
			pile.store(str);
			if (!SysProps.get(ObjectPile.SYSPROP_UNIQUE, false)) {
				System.out.println("Set SystemProperty "+ObjectPile.SYSPROP_UNIQUE+"="+StringOps.boolAsString(true)+" to test uniqueness checks");
			} else {
				org.junit.Assert.fail("ObjectPile failed to trap duplicate object");
			}
		} catch (IllegalArgumentException ex) {}	

		pile.clear();
		org.junit.Assert.assertEquals(0, pile.size());
		pile.clear();
		org.junit.Assert.assertEquals(0, pile.size());
	}

	@org.junit.Test
	public void storeMultiple()
	{
		ObjectPile<String> pile = new ObjectPile<String>();
		String[] arr = new String[]{"zero", "one", "two", "three"};
		int off = 1;
		pile.bulkStore(arr, off, arr.length - off);
		org.junit.Assert.assertEquals(arr.length - off, pile.size());
		java.util.Set<String> stored = new java.util.HashSet<String>();
		for (int idx = off; idx != arr.length; idx++) stored.add(arr[idx]);
		while (pile.size() != 0) {
			String str = pile.extract();
			org.junit.Assert.assertTrue(stored.remove(str));
		}
		org.junit.Assert.assertEquals(0, pile.size());
		org.junit.Assert.assertEquals(0, stored.size());

		java.util.List<String> lst = Arrays.asList(arr);
		pile.bulkStore(lst);
		for (int idx = 0; idx != arr.length; idx++) stored.add(arr[idx]);
		while (pile.size() != 0) {
			String str = pile.extract();
			org.junit.Assert.assertTrue(stored.remove(str));
		}
		org.junit.Assert.assertEquals(0, pile.size());
		org.junit.Assert.assertEquals(0, stored.size());

	}
}
