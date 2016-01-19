/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

public class ObjectQueueTest
{
	@org.junit.Test
	public void lifecycle()
	{
		int initcap = 5;
		int incr = 2;
		ObjectQueue<String> strq = new ObjectQueue<String>(String.class, initcap, incr);
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertNull(strq.peek());
		org.junit.Assert.assertTrue(strq.toString().contains("=0/head="));
		String str = strq.remove();
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertNull(str);
		// initial setup, populating to capacity
		strq.add("One");
		strq.add("Two");
		strq.add("Three");
		strq.add("Four");
		strq.add("Five");
		org.junit.Assert.assertEquals(strq.capacity(), strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertEquals("One", strq.peek());
		org.junit.Assert.assertEquals("Two", strq.peek(1));
		org.junit.Assert.assertTrue(strq.toString().contains("="+strq.size()+"/head="));
		int prevsize = strq.size();
		// several dequeue ops to leave head in mid-buffer
		int num = 3;
		for (int idx = 0; idx != num; idx++) {
			str  = strq.remove();
		}
		org.junit.Assert.assertEquals(prevsize - num, strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertEquals("Three", str);
		org.junit.Assert.assertEquals("Four", strq.peek());
		prevsize = strq.size();
		// queue wraps around at tail
		strq.add("3a");
		org.junit.Assert.assertEquals(prevsize + 1, strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertEquals("Four", strq.peek());
		prevsize = strq.size();
		// go back to full capacity again
		strq.add("4a");
		strq.add("4b");
		org.junit.Assert.assertEquals(prevsize+2, strq.size());
		org.junit.Assert.assertEquals(strq.capacity(), strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertEquals("Four", strq.peek());
		prevsize = strq.size();
		//grow the buffer
		strq.add("5a");
		org.junit.Assert.assertEquals(initcap+incr, strq.capacity());
		org.junit.Assert.assertEquals(prevsize+1, strq.size());
		org.junit.Assert.assertEquals("Four", strq.peek());
		//test withdraw
		org.junit.Assert.assertTrue(strq.withdraw("5a"));
		org.junit.Assert.assertEquals(prevsize, strq.size());
		org.junit.Assert.assertFalse(strq.withdraw("5a"));
		org.junit.Assert.assertEquals(prevsize, strq.size());
		//test clear
		strq.clear();
		org.junit.Assert.assertEquals(initcap+incr, strq.capacity());
		org.junit.Assert.assertEquals(0, strq.size());
	}

	@org.junit.Test
	public void wraparound()
	{
		ObjectQueue<String> strq = new ObjectQueue<String>(String.class);
		int initcap = strq.capacity();
		for (int idx = 0; idx != strq.capacity(); idx++) {
			strq.add(Integer.toString(idx));
		}
		org.junit.Assert.assertEquals(strq.capacity(), strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		// leave just two elements in queue
		int remainder = 2;
		while (strq.size() != remainder) {
			strq.remove();
		}
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		int prevsize = strq.size();
		// add a few more elements so that tail wraps around by more than one
		int num = 3;
		for (int idx = 0; idx != num; idx++) {
			strq.add(Integer.toString(idx)+"b");
		}
		org.junit.Assert.assertEquals(prevsize+num, strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		prevsize = strq.size();
		// delete just enough elements to make head wrap around
		for (int idx = 0; idx != remainder; idx++) {
			strq.remove();
		}
		org.junit.Assert.assertEquals(prevsize - remainder, strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		prevsize = strq.size();
		// carry on deleting till queue is empty, and throw in a few excess deletes too
		for (int idx = 0; idx != prevsize + 2; idx++) {
			strq.remove();
			if (idx == prevsize - 1) org.junit.Assert.assertEquals(0, strq.size());
		}
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertEquals(initcap, strq.capacity());
	}

	@org.junit.Test
	public void arrays()
	{
		ObjectQueue<String> strq = new ObjectQueue<String>(String.class);
		// test empty array conversion
		String[] arr = strq.toArray();
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertEquals(arr.length, 0);
		// with some content
		for (int idx = 0; idx != 5; idx++) {
			strq.add(Integer.toString(idx));
		}
		int prevsize = strq.size();
		arr = strq.toArray();
		org.junit.Assert.assertEquals(prevsize, strq.size());
		org.junit.Assert.assertEquals(arr.length, strq.size());
		for (int idx = 0; idx != arr.length; idx++) {
			org.junit.Assert.assertEquals(Integer.toString(idx), arr[idx]);
		}

		// test the other toArray() variant
		String[] arr2 = new String[arr.length];
		String[] arr3 = strq.toArray(arr2);
		org.junit.Assert.assertSame(arr2, arr3);
		for (int idx = 0; idx != arr.length; idx++) {
			org.junit.Assert.assertEquals(arr[idx], arr2[idx]);
		}

		// make sure toArray() works on a wrapped queue
		strq = new ObjectQueue<String>(String.class, 4, 10);
		strq.add("One");
		strq.add("Two");
		strq.add("Three");
		strq.add("Four");
		org.junit.Assert.assertEquals(4, strq.size());
		org.junit.Assert.assertEquals(4, strq.capacity());
		org.junit.Assert.assertEquals("One", strq.peek());
		String str = strq.remove();
		org.junit.Assert.assertEquals(3, strq.size());
		org.junit.Assert.assertEquals(4, strq.capacity());
		org.junit.Assert.assertEquals("Two", strq.peek());
		org.junit.Assert.assertEquals("One", str);
		// make the queue wrap
		strq.add("Five");
		org.junit.Assert.assertEquals(4, strq.size());
		org.junit.Assert.assertEquals(4, strq.capacity());
		org.junit.Assert.assertEquals("Two", strq.peek());
		arr = strq.toArray();
		org.junit.Assert.assertEquals(strq.size(), arr.length);
		org.junit.Assert.assertEquals("Two", arr[0]);
		org.junit.Assert.assertEquals("Three", arr[1]);
		org.junit.Assert.assertEquals("Four", arr[2]);
		org.junit.Assert.assertEquals("Five", arr[3]);
	}

	@org.junit.Test
	public void specialConstructors()
	{
		int incr = 2;
		ObjectQueue<String> strq = new ObjectQueue<String>(String.class, 0, incr);
		org.junit.Assert.assertEquals(0, strq.capacity());
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertNull(strq.peek());
		String str = strq.remove();
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertEquals(0, strq.capacity());
		org.junit.Assert.assertNull(str);
		//adding first element will cause first allocation
		strq.add("One");
		org.junit.Assert.assertEquals(incr, strq.capacity());
		org.junit.Assert.assertEquals(1, strq.size());
		org.junit.Assert.assertEquals("One", strq.peek());
		strq.add("Two");
		org.junit.Assert.assertEquals(strq.capacity(), incr);
		org.junit.Assert.assertEquals(2, strq.size());
		org.junit.Assert.assertEquals("One", strq.peek());

		// test a queue with a fixed capacity
		int initcap = 2;
		strq = new ObjectQueue<String>(String.class, initcap, 0);
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertNull(strq.peek());
		//populate to capacity
		strq.add("One");
		strq.add("Two");
		org.junit.Assert.assertEquals(initcap, strq.capacity());
		org.junit.Assert.assertEquals(initcap, strq.size());
		org.junit.Assert.assertEquals("One", strq.peek());
		//... and beyond -this should fail
		try {
			strq.add("Three");
			org.junit.Assert.fail("Exceeded max capacity");
		} catch (UnsupportedOperationException ex) {}

		// test a constant empty queue
		strq = new ObjectQueue<String>(String.class, 0, 0);
		org.junit.Assert.assertEquals(0, strq.capacity());
		org.junit.Assert.assertEquals(0, strq.size());
		org.junit.Assert.assertNull(strq.peek());
		//should not be able to add anything
		try {
			strq.add("One");
			org.junit.Assert.fail("Added element to fixed empty queue");
		} catch (UnsupportedOperationException ex) {}
	}
}
