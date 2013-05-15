/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.config;

import com.grey.base.utils.TimeOps;

public class SysPropsTest
{
	@org.junit.Test
	public void testGetSet()
	{
		final String key = "grey.test.SysProps.getset";
		System.getProperties().remove(key);
		org.junit.Assert.assertEquals(0, SysProps.set(key, 99));
		org.junit.Assert.assertEquals(99, SysProps.get(key, 1));
		org.junit.Assert.assertEquals(99, SysProps.set(key, 2));
		org.junit.Assert.assertEquals(2, SysProps.get(key, 3));
		org.junit.Assert.assertEquals("2", System.getProperty(key));
		org.junit.Assert.assertEquals("2", SysProps.set(key, null));
		org.junit.Assert.assertFalse(System.getProperties().containsKey(key));
	
		org.junit.Assert.assertFalse(SysProps.set(key, true));
		org.junit.Assert.assertTrue(SysProps.get(key, false));
		org.junit.Assert.assertTrue(SysProps.set(key, false));
		org.junit.Assert.assertFalse(SysProps.get(key, true));
		System.getProperties().remove(key);
		org.junit.Assert.assertTrue(SysProps.get(key, true));

		String time1str = "1h";
		long time1 = TimeOps.parseMilliTime(time1str);
		long time2 = TimeOps.parseMilliTime("2h");
		System.setProperty(key, time1str);
		org.junit.Assert.assertEquals(time1, SysProps.getTime(key, 5L));
		SysProps.setTime(key, time1);
		org.junit.Assert.assertEquals(time1, SysProps.setTime(key, time2));
		org.junit.Assert.assertEquals(time2, SysProps.getTime(key, 5L));
		org.junit.Assert.assertEquals(Long.toString(time2), System.getProperty(key));
		System.getProperties().remove(key);
		org.junit.Assert.assertEquals(0L, SysProps.setTime(key, time1));
		org.junit.Assert.assertEquals(time1, SysProps.getTime(key, 5L));
	}

	// It has been externally verified that the natural iteration order of the properties created here differs from the sorted order
	@org.junit.Test
	public void testSort()
	{
		java.util.Properties props = new java.util.Properties();
		props.put("key1", "val1");
		props.put("key2", "val2");
		props.put("key3", "val3");
		String[] keys = SysProps.dump(props, null);
		org.junit.Assert.assertEquals(props.size(), keys.length);
		org.junit.Assert.assertEquals("key1", keys[0]);
		org.junit.Assert.assertEquals("key2", keys[1]);
		org.junit.Assert.assertEquals("key3", keys[2]);
	}

	@org.junit.Test
	public void miscellaneous() throws java.io.IOException
	{
		String pthnam = "/no/such/props/file";
		org.junit.Assert.assertFalse(new java.io.File(pthnam).exists()); //sanity check
		org.junit.Assert.assertNull(SysProps.load(pthnam));
	}
}
