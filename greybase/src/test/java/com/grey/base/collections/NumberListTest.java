/*
 * Copyright 2015-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

import java.lang.reflect.Array;

public class NumberListTest
{
	@org.junit.Test
	public void testSimple() {
		NumberList lst = new NumberList();
		lst.append(10);
		lst.append(5);
		lst.append(13);
		lst.append(7);
		org.junit.Assert.assertEquals(4, lst.size());
		org.junit.Assert.assertEquals(10, lst.get(0));
		org.junit.Assert.assertEquals(5, lst.get(1));
		org.junit.Assert.assertEquals(13, lst.get(2));
		org.junit.Assert.assertEquals(7, lst.get(3));
		lst.sort();
		org.junit.Assert.assertEquals(4, lst.size());
		org.junit.Assert.assertEquals(5, lst.get(0));
		org.junit.Assert.assertEquals(7, lst.get(1));
		org.junit.Assert.assertEquals(10, lst.get(2));
		org.junit.Assert.assertEquals(13, lst.get(3));
		lst.clear();
		org.junit.Assert.assertEquals(0, lst.size());
	}

	@org.junit.Test
	public void testCompoundAdd() {
		NumberList lst = new NumberList();
		lst.append(11);
		lst.append(12);
		NumberList lst2 = new NumberList();
		lst2.append(101);
		lst2.append(102);
		lst.append(lst2);
		org.junit.Assert.assertEquals(4, lst.size());
		org.junit.Assert.assertEquals(11, lst.get(0));
		org.junit.Assert.assertEquals(12, lst.get(1));
		org.junit.Assert.assertEquals(101, lst.get(2));
		org.junit.Assert.assertEquals(102, lst.get(3));
		org.junit.Assert.assertEquals(2, lst2.size());
		org.junit.Assert.assertEquals(101, lst2.get(0));
		org.junit.Assert.assertEquals(102, lst2.get(1));
	}

	@org.junit.Test
	public void testGrow() {
		NumberList lst = new NumberList();
		int cap = Array.getLength(lst.buffer());
		for (int idx = 0; idx != cap; idx++) {
			lst.append(idx+100);
		}
		org.junit.Assert.assertEquals(cap, lst.size());
		org.junit.Assert.assertEquals(cap, Array.getLength(lst.buffer()));
		org.junit.Assert.assertEquals(100, lst.get(0));
		org.junit.Assert.assertEquals(cap + 99, lst.get(cap - 1));
		lst.append(cap + 101);
		int cap2 = Array.getLength(lst.buffer());
		org.junit.Assert.assertTrue(cap2 > cap);
		org.junit.Assert.assertEquals(cap+1, lst.size());
		org.junit.Assert.assertEquals(100, lst.get(0));
		org.junit.Assert.assertEquals(cap + 99, lst.get(cap - 1));
		org.junit.Assert.assertEquals(cap + 101, lst.get(cap));
		lst.clear();
		org.junit.Assert.assertEquals(0, lst.size());
		org.junit.Assert.assertEquals(cap2, Array.getLength(lst.buffer()));
	}
}
