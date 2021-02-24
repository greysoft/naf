/*
 * Copyright 2011-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.config.SysProps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.ScheduledTime;

public class LoggerTest
{
	private final String rootpath = SysProps.TMPDIR+"/utest/greylog/"+getClass().getName();
	static {
		SysProps.set(Logger.SYSPROP_DIAG, true);
	}

	public LoggerTest() throws java.io.IOException {
		FileOps.deleteDirectory(rootpath);
	}

	@org.junit.Test
	public void testGeneral() throws java.io.IOException
	{
		String logfile = rootpath+"gen_utest.log";
		Class<?> clss = MTCharLogger.class;
		Parameters params = new Parameters.Builder()
				.withLogClass(clss)
				.withLogLevel(LEVEL.INFO)
				.withPathname(logfile)
				.withTID(true)
				.withDelta(true)
				.build();
		Logger log = Factory.getLogger(params, "general1");
		org.junit.Assert.assertEquals(clss, log.getClass());
		org.junit.Assert.assertEquals("general1", log.getName());
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
		org.junit.Assert.assertTrue(fh.delete());
		org.junit.Assert.assertFalse(fh.exists());
	}

	@org.junit.Test
	public void testFilenames() throws java.io.IOException
	{
		String mainpart = "AAA"+ScheduledTime.TOKEN_YEAR+"BBB"+ScheduledTime.TOKEN_MONTH+"CCC";
		String template = rootpath+"/"+mainpart+".log";
		Class<?> clss = MTCharLogger.class;
		String logname = "myname1";
		Parameters params = new Parameters.Builder()
				.withLogClass(clss)
				.withLogLevel(LEVEL.INFO)
				.withPathname(template)
				.withDelta(true)
				.build();
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
		org.junit.Assert.assertTrue(fh.delete());
		org.junit.Assert.assertFalse(fh.exists());

		params = new Parameters.Builder(params)
				.withPathname(template.replace("CCC.log", "CCC-"+ScheduledTime.TOKEN_DT+".log"))
				.withRotFreq(ScheduledTime.FREQ.HOURLY)
				.build();
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
		org.junit.Assert.assertTrue(fh.delete());
		org.junit.Assert.assertFalse(fh.exists());
	}

	@org.junit.Test
	public void testMemLogger() throws java.io.IOException
	{
		Class<?> clss = MemLogger.class;
		Parameters params = new Parameters.Builder()
				.withLogClass(clss)
				.withLogLevel(LEVEL.WARN)
				.withPathname("any-old-name-as-will-discard-it")
				.withFlushInterval(TimeOps.MSECS_PER_DAY) //just to prove flushing is meaningless here
				.build();
		Logger log = Factory.getLogger(params, "mem1");
		org.junit.Assert.assertEquals(clss, log.getClass());
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
	public void testSinkLogger() throws java.io.IOException
	{
		Class<?> clss = SinkLogger.class;
		Parameters params = new Parameters.Builder()
				.withLogClass(clss)
				.withLogLevel(LEVEL.INFO)
				.withStream(null)
				.build();
		Logger log = Factory.getLogger(params, "sink1");
		org.junit.Assert.assertEquals(clss, log.getClass());
		log.log(LEVEL.WARN, null, true, "This message should not come out");
		log.close();
	}

	@org.junit.Test
	public void testThreadLoggers() throws java.io.IOException {
		Logger log1 = Factory.getLogger();
		Logger log2 = Factory.getLogger();
		org.junit.Assert.assertFalse(log1 == log2);

		Logger.setThreadLogger(log1);
		Logger.setThreadLogger(log1, 1001L);
		Logger.setThreadLogger(log2, 1002L);
		org.junit.Assert.assertTrue(log1 == Logger.getThreadLogger());
		org.junit.Assert.assertTrue(log1 == Logger.getThreadLogger(Thread.currentThread().getId()));
		org.junit.Assert.assertTrue(log1 == Logger.getThreadLogger(1001L));
		org.junit.Assert.assertTrue(log2 == Logger.getThreadLogger(1002L));

		log1.close();
		org.junit.Assert.assertNull(Logger.getThreadLogger());
		org.junit.Assert.assertNull(Logger.getThreadLogger(Thread.currentThread().getId()));
		org.junit.Assert.assertNull(Logger.getThreadLogger(1001L));
		org.junit.Assert.assertTrue(log2 == Logger.getThreadLogger(1002L));

		log2.close();
		org.junit.Assert.assertNull(Logger.getThreadLogger());
		org.junit.Assert.assertNull(Logger.getThreadLogger(Thread.currentThread().getId()));
		org.junit.Assert.assertNull(Logger.getThreadLogger(1001L));
		org.junit.Assert.assertNull(Logger.getThreadLogger(1002L));
	}

	// All we're looking for here is proof it doesn't crash!
	@org.junit.Test
	public void testAdapters() throws java.io.IOException
	{
		Class<?> clss = com.grey.logging.adapters.AdapterSLF4J.class;
		Parameters params = new Parameters.Builder()
				.withLogClass(clss)
				.withLogLevel(LEVEL.INFO)
				.withStream(null)
				.build();
		Logger log = Factory.getLogger(params, "logname-slf4j");
		org.junit.Assert.assertEquals(clss, log.getClass());
		StringBuilder sb = new StringBuilder("This won't come  out unless an SLF4J logger is on our classpath and is configured");
		log.trace(sb);
		log.close();

		clss = com.grey.logging.adapters.AdapterJCL.class;
		params = new Parameters.Builder()
				.withLogClass(clss)
				.withLogLevel(LEVEL.INFO)
				.withStream(null)
				.build();
		log = Factory.getLogger(params, "logname-jcl");
		org.junit.Assert.assertEquals(clss, log.getClass());
		sb = new StringBuilder("This is the JCL adapter");
		log.trace(sb);
		log.close();
	}
}