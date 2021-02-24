/*
 * Copyright 2011-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.base.config.SysProps;
import com.grey.base.utils.ScheduledTime;

/*
 * Make sure grey.logger.configfile system property is not set, when running these tests.
 */
public class FactoryTest
{
	static {
		SysProps.set(Logger.SYSPROP_DIAG, true);
	}

	@org.junit.Test
	public void testParameters()
	{
		Parameters params = new Parameters.Builder().build();
		org.junit.Assert.assertNotNull(params.getLogClass());
		org.junit.Assert.assertNull(params.getPathname());
		org.junit.Assert.assertNotNull(params.getStream());
		org.junit.Assert.assertEquals(0, params.getMaxSize());
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.getRotFreq());
		org.junit.Assert.assertFalse(params.getBufSize() == 0);

		params = new Parameters.Builder(params)
				.withMaxSize(1024)
				.build();
		org.junit.Assert.assertNull(params.getPathname());
		org.junit.Assert.assertEquals(0, params.getMaxSize());
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.getRotFreq());

		params = new Parameters.Builder(params)
				.withRotFreq(ScheduledTime.FREQ.DAILY)
				.build();
		org.junit.Assert.assertNull(params.getPathname());
		org.junit.Assert.assertEquals(0, params.getMaxSize());
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.getRotFreq()); //due to null pthnam

		params = new Parameters.Builder(params)
				.withPathname("blah")
				.build();
		org.junit.Assert.assertNotNull(params.getPathname());
		org.junit.Assert.assertNull(params.getStream());
		org.junit.Assert.assertEquals(0, params.getMaxSize());
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.getRotFreq());

		params = new Parameters.Builder(params)
				.withMaxSize(1024)
				.withRotFreq(ScheduledTime.FREQ.DAILY)
				.build();
		org.junit.Assert.assertEquals(1024, params.getMaxSize());
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.getRotFreq()); //due to maxsize

		params = new Parameters.Builder(params)
				.withPathname("blah")
				.withMaxSize(0)
				.withRotFreq(ScheduledTime.FREQ.DAILY)
				.build();
		org.junit.Assert.assertEquals(0, params.getMaxSize());
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.DAILY, params.getRotFreq());
		org.junit.Assert.assertFalse(params.getBufSize() == 0);

		params = new Parameters.Builder(params)
				.withBufferSize(0)
				.withFlushInterval(9)
				.build();
		org.junit.Assert.assertEquals(0, params.getBufSize());
		org.junit.Assert.assertEquals(0, params.getFlushInterval());

		params = new Parameters.Builder(params)
				.withBufferSize(1024)
				.build();
		org.junit.Assert.assertEquals(1024, params.getBufSize());
		org.junit.Assert.assertEquals(0, params.getFlushInterval());
	}
}