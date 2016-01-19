/*
 * Copyright 2011-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.base.config.SysProps;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.ScheduledTime;

/*
 * Make sure grey.logger.configfile system property is not set, when running these tests.
 */
public class FactoryTest
{
	static {
		SysProps.set(Logger.SYSPROP_DIAG, true);
	}
	private static final String RSRCFILE = "logging.xml";
	private static final String PATH_LOG5 = SysProps.TMPDIR+"/utest/greylog/factorytest.log";  //must match log5 entry in RSRCFILE
	private static final String NO_SUCH_LOG = "log99";  //must not match any entries in RSRCFILE

	@org.junit.Test
	public void testMappings() throws com.grey.base.ConfigException, java.io.IOException, java.net.URISyntaxException
	{
		java.net.URL url = DynLoader.getResource(RSRCFILE, getClass());
		String cfgpath = new java.io.File(url.toURI()).getCanonicalPath();

		// create the absolute default logger - ie. where no config exists and no params are specified at all
		Logger dlog = Factory.getLogger();
		org.junit.Assert.assertNotNull(dlog);  //default logger always exists
		org.junit.Assert.assertEquals(Parameters.DFLTCLASS, dlog.getClass());
		org.junit.Assert.assertEquals(Factory.DFLT_LOGNAME, dlog.getName());

		// repeat call should get back same logger
		Logger log = Factory.getLogger();
		org.junit.Assert.assertTrue(log == dlog);

		// default logger in a different factory-config
		String cfgfile = "NoSuchConfig";
		log = Factory.getLogger(cfgfile, null);
		org.junit.Assert.assertNotNull(log);
		org.junit.Assert.assertTrue(log == dlog);
		// same default logger specified by name
		Logger log2 = Factory.getLogger(cfgfile, Factory.DFLT_LOGNAME);
		org.junit.Assert.assertTrue(log2 == log);

		// But if we look up a non-default logger via config, it must exist else we fall back to Sink
		Class<?> clss = (Factory.sinkstdio ? Parameters.DFLTCLASS : SinkLogger.class);
		log = Factory.getLogger(cfgpath, NO_SUCH_LOG);
		org.junit.Assert.assertEquals(clss, log.getClass());
		// ... regardless of config file's existence
		log = Factory.getLogger(NO_SUCH_LOG);
		org.junit.Assert.assertEquals(clss, log.getClass());
		log = Factory.getLogger("NoSuchConfigFile", NO_SUCH_LOG);
		org.junit.Assert.assertEquals(clss, log.getClass());

		// This is not the default factory-config, so even logger with same name (default) won't match dlog
		Logger dlog_cfg = Factory.getLogger(cfgpath, null);
		org.junit.Assert.assertNotNull(dlog);
		org.junit.Assert.assertTrue(dlog_cfg != dlog);
		org.junit.Assert.assertEquals(Parameters.DFLTCLASS, dlog.getClass());
		org.junit.Assert.assertEquals(Factory.DFLT_LOGNAME, dlog_cfg.getName());
		log = Factory.getLogger(cfgpath, Factory.DFLT_LOGNAME);  //same meaning as above
		org.junit.Assert.assertTrue(log == dlog_cfg);

		log = Factory.getLogger(cfgpath, "log2");
		org.junit.Assert.assertTrue(log == dlog_cfg);  //log2 is aliased to default

		String log3name = "log3";
		Logger log3 = Factory.getLogger(cfgpath, log3name);  //log3 points at same stream as default
		org.junit.Assert.assertTrue(log3 == dlog_cfg);
		log = Factory.getLogger(cfgpath, "log4");  //log4 points at same stream as absolute default
		org.junit.Assert.assertTrue(log == dlog);

		// Use a config file on the classpath
		String cp = getClass().getPackage().getName().replace('.', '/')+"/"+RSRCFILE;
		log = Factory.getLogger(cp, log3name);
		org.junit.Assert.assertTrue(log == log3);

		// make sure creating a new logfile works
		String name_log5 = "log5";
		java.io.File fh = new java.io.File(PATH_LOG5);
		fh.delete();
		org.junit.Assert.assertFalse(fh.exists());
		log = Factory.getLogger(cfgpath, name_log5);
		log.flush();
		org.junit.Assert.assertTrue(log != dlog);
		org.junit.Assert.assertTrue(log != dlog_cfg);
		org.junit.Assert.assertEquals(name_log5, log.getName());
		org.junit.Assert.assertTrue(fh.exists() && fh.length() != 0);
		// make sure opening an already open logfile has no effect
		long prevlen = fh.length();
		log2 = Factory.getLogger(cfgpath, "log5");
		log.flush();
		org.junit.Assert.assertTrue(log2 == log);
		org.junit.Assert.assertEquals(prevlen, fh.length());
		log.close();
		prevlen = fh.length();

		//make sure opening an existing logfile works
		log2 = Factory.getLogger(cfgpath, "log5");
		org.junit.Assert.assertTrue(log2 != log);
		org.junit.Assert.assertTrue(fh.exists() && fh.length() > prevlen);
		log2.close();

		dlog.close();
		dlog_cfg.close();
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
		org.junit.Assert.assertTrue(params.flush_interval == 0);

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