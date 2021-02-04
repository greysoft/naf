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
		Parameters params = new Parameters();
		params.reconcile();
		org.junit.Assert.assertNotNull(params.logclass);
		org.junit.Assert.assertNull(params.pthnam);
		org.junit.Assert.assertNotNull(params.strm);
		org.junit.Assert.assertEquals(0, params.maxsize);
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.rotfreq);
		org.junit.Assert.assertFalse(params.bufsiz == 0);

		params.maxsize = 1024;
		params.rotfreq = null;
		params.reconcile();
		org.junit.Assert.assertNull(params.pthnam);
		org.junit.Assert.assertEquals(0, params.maxsize);
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.rotfreq);

		params.rotfreq = ScheduledTime.FREQ.DAILY;
		params.reconcile();
		org.junit.Assert.assertNull(params.pthnam);
		org.junit.Assert.assertEquals(0, params.maxsize);
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.rotfreq); //due to null pthnam

		params.pthnam = "blah";
		params.reconcile();
		org.junit.Assert.assertNotNull(params.pthnam);
		org.junit.Assert.assertNull(params.strm);
		org.junit.Assert.assertEquals(0, params.maxsize);
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.rotfreq);

		params.maxsize = 1024;
		params.rotfreq = ScheduledTime.FREQ.DAILY;
		params.reconcile();
		org.junit.Assert.assertEquals(1024, params.maxsize);
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.NEVER, params.rotfreq); //due to maxsize

		params.pthnam = "blah";
		params.maxsize = 0;
		params.rotfreq = ScheduledTime.FREQ.DAILY;
		params.reconcile();
		org.junit.Assert.assertEquals(0, params.maxsize);
		org.junit.Assert.assertEquals(ScheduledTime.FREQ.DAILY, params.rotfreq);
		org.junit.Assert.assertFalse(params.bufsiz == 0);

		params.bufsiz = 0;
		params.flush_interval = 9;
		params.reconcile();
		org.junit.Assert.assertEquals(0, params.bufsiz);
		org.junit.Assert.assertEquals(0, params.flush_interval);

		params.bufsiz = 1024;
		params.reconcile();
		org.junit.Assert.assertEquals(1024, params.bufsiz);
		org.junit.Assert.assertEquals(0, params.flush_interval);
	}
}