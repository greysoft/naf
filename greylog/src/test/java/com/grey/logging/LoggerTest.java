/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.logging.Interop.LEVEL;
import com.grey.base.config.SysProps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.ScheduledTime;

public class LoggerTest
{
	static {
		SysProps.set(Logger.SYSPROP_DIAG, true);
	}

	@org.junit.Test
	public void testGeneral() throws com.grey.base.ConfigException, java.io.IOException
	{
		String logfile = "./gen_utest.log";
		Class<?> clss = MTCharLogger.class;
		Parameters params = new Parameters(LEVEL.INFO, logfile);
		params.logclass = clss.getName();
		params.withTID = true;
		params.withDelta = true;
		Logger log = Factory.getLogger(params, null);
		org.junit.Assert.assertEquals(clss, log.getClass());
		org.junit.Assert.assertNull(log.getName());
		org.junit.Assert.assertEquals(log.getActivePath(), log.getPathTemplate());
		java.io.File fh = new java.io.File(log.getActivePath());
		log.flush();
		long prevlen = fh.length();
		org.junit.Assert.assertTrue(prevlen != 0);
		org.junit.Assert.assertEquals(LEVEL.INFO, log.setLevel(LEVEL.TRC));
		org.junit.Assert.assertEquals(LEVEL.TRC, log.getLevel());
		org.junit.Assert.assertEquals(LEVEL.TRC, log.setLevel(LEVEL.TRC));
		log.flush();
		org.junit.Assert.assertTrue(fh.length() > prevlen);
		prevlen = fh.length();
		log.log(LEVEL.TRC2, "This message should not come out");
		log.flush();
		org.junit.Assert.assertEquals(prevlen, fh.length());
		log.log(LEVEL.TRC, "This message IS expected to come out");
		log.flush();
		org.junit.Assert.assertTrue(fh.length() > prevlen);
		prevlen = fh.length();
		log.log(LEVEL.INFO, null, true, "So is this");
		log.flush();
		org.junit.Assert.assertTrue(fh.length() > prevlen);
		prevlen = fh.length();
		log.log(LEVEL.TRC2, null, true, "  but not this");
		log.flush();
		org.junit.Assert.assertEquals(prevlen, fh.length());
		log.close();
		org.junit.Assert.assertEquals(prevlen, fh.length());
		fh.delete();
		org.junit.Assert.assertFalse(fh.exists());
	}

	@org.junit.Test
	public void testFilenames() throws com.grey.base.ConfigException, java.io.IOException
	{
		String mainpart = "AAA"+ScheduledTime.TOKEN_YEAR+"BBB"+ScheduledTime.TOKEN_MONTH+"CCC";
		String template = mainpart+".log";
		Class<?> clss = MTLatinLogger.class;
		String logname = "myname1";
		Parameters params = new Parameters(LEVEL.INFO, template);
		params.logclass = clss.getName();
		params.mode = "Bad-Mode";
		params.withDelta = true;
		params.flush_interval = 0;
		params.reconcile();
		Logger log = Factory.getLogger(params, logname);
		org.junit.Assert.assertEquals(clss, log.getClass());
		org.junit.Assert.assertEquals(logname, log.getName());
		org.junit.Assert.assertEquals(log.getActivePath(), log.getPathTemplate());
		org.junit.Assert.assertEquals(-1, log.getActivePath().indexOf(ScheduledTime.TOKEN_YEAR));
		org.junit.Assert.assertEquals(-1, log.getActivePath().indexOf(ScheduledTime.TOKEN_MONTH));
		org.junit.Assert.assertEquals(-1, log.getActivePath().indexOf(ScheduledTime.TOKEN_DT));
		org.junit.Assert.assertTrue(log.getActivePath().indexOf("CCC.log") != -1);
		// make sure our logging output goes there
		java.io.File fh = new java.io.File(log.getActivePath());
		org.junit.Assert.assertTrue(fh.exists());
		long prevlen = fh.length();
		log.log(LEVEL.INFO, "blah");
		log.flush();
		org.junit.Assert.assertTrue(fh.length() > prevlen);
		// clean up
		log.close();
		fh.delete();
		org.junit.Assert.assertFalse(fh.exists());

		params.pthnam = template;
		params.rotfreq = ScheduledTime.FREQ.HOURLY;
		params.reconcile();
		log = Factory.getLogger(params, logname);
		org.junit.Assert.assertEquals(clss, log.getClass());
		org.junit.Assert.assertEquals(logname, log.getName());
		org.junit.Assert.assertFalse(log.getActivePath().equals(log.getPathTemplate()));
		fh = new java.io.File(log.getPathTemplate());
		org.junit.Assert.assertEquals(mainpart+"-"+ScheduledTime.TOKEN_DT+".log", fh.getName());
		org.junit.Assert.assertEquals(-1, log.getActivePath().indexOf(ScheduledTime.TOKEN_YEAR));
		org.junit.Assert.assertEquals(-1, log.getActivePath().indexOf(ScheduledTime.TOKEN_MONTH));
		org.junit.Assert.assertEquals(-1, log.getActivePath().indexOf(ScheduledTime.TOKEN_DT));
		org.junit.Assert.assertEquals(-1, log.getActivePath().indexOf("CCC.log")); //TOKEN_DT will break this up
		// make sure our logging output goes there
		fh = new java.io.File(log.getActivePath());
		org.junit.Assert.assertTrue(fh.exists());
		prevlen = fh.length();
		log.log(LEVEL.INFO, "blah");
		log.flush();
		org.junit.Assert.assertTrue(fh.length() > prevlen);
		log.close();
		fh.delete();
		org.junit.Assert.assertFalse(fh.exists());
	}

	@org.junit.Test
	public void testMemLogger() throws com.grey.base.ConfigException, java.io.IOException
	{
		Class<?> clss = MemLogger.class;
		Parameters params = new Parameters(LEVEL.WARN, "any-old-name-as-will-discard-it");
		params.logclass = clss.getName();
		params.flush_interval = TimeOps.MSECS_PER_DAY; //just to prove flushing is meaningless here
		Logger log = Factory.getLogger(params, null);
		org.junit.Assert.assertEquals(clss, log.getClass());
		org.junit.Assert.assertNull(log.getName());
		org.junit.Assert.assertNull(log.getActivePath());
		org.junit.Assert.assertNull(log.getPathTemplate());
		MemLogger mlog = MemLogger.class.cast(log);
		long prevlen = mlog.length();
		log.log(LEVEL.INFO, "This message should not come out");
		org.junit.Assert.assertEquals(prevlen, mlog.length());
		log.log(LEVEL.WARN, "Test message - this should come out");
		org.junit.Assert.assertTrue(mlog.length() > prevlen);
		org.junit.Assert.assertEquals(mlog.length(), mlog.get().length());
		prevlen = mlog.length();
		log.flush();
		org.junit.Assert.assertEquals(prevlen, mlog.length());
		log.log(LEVEL.WARN, "Another test message which comes out");
		org.junit.Assert.assertTrue(mlog.length() > prevlen);
		log.close();
		org.junit.Assert.assertEquals(0, mlog.length());
		org.junit.Assert.assertEquals(mlog.length(), mlog.get().length());
	}

	// All we're looking for here is proof it doesn't crash!
	@org.junit.Test
	public void testSinkLogger() throws com.grey.base.ConfigException, java.io.IOException
	{
		Parameters params = new Parameters(LEVEL.INFO, (java.io.OutputStream)null);
		params.logclass = SinkLogger.class.getName();
		Logger log = Factory.getLogger(params, null);
		org.junit.Assert.assertEquals(SinkLogger.class, log.getClass());
		log.log(LEVEL.WARN, null, true, "This message should not come out");
		log.close();
	}
}
