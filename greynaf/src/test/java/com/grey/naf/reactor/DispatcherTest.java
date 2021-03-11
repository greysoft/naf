/*
 * Copyright 2011-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.XmlConfig;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.FileOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.DispatcherDef;
import com.grey.naf.Launcher;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.TestUtils;

public class DispatcherTest
{
	private static final String rootdir = TestUtils.initPaths(DispatcherTest.class);
	private static final com.grey.logging.Logger bootlog = com.grey.logging.Factory.getLoggerNoEx("");

	// Completion is enough to satisfy these tests
	@org.junit.Test
	public void testConfig() throws java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		String dname = "testdispatcher1";
		String cfgpath = TestUtils.getResourcePath("/naf.xml", getClass());
		ApplicationContextNAF appctx = TestUtils.createApplicationContext("DispatcherTest-Config", cfgpath, true);
		Launcher.launchConfiguredDispatchers(appctx, bootlog);
		org.junit.Assert.assertEquals(appctx.getDispatchers().toString(), 1, appctx.getDispatchers().size());
		Dispatcher dsptch = appctx.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dsptch);
		org.junit.Assert.assertEquals(dname, dsptch.getName());
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop();
		waitStopped(dsptch);
		org.junit.Assert.assertEquals(appctx.getDispatchers().toString(), 0, appctx.getDispatchers().size());
		org.junit.Assert.assertNull(appctx.getDispatcher(dname));
	}

	@org.junit.Test
	public void testNamedConfig() throws java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		String cfgpath = TestUtils.getResourcePath("/naf.xml", getClass());
		ApplicationContextNAF appctx = TestUtils.createApplicationContext("DispatcherTest-NamedConfig", cfgpath, true);

		String dname = "testdispatcher1";
		XmlConfig dcfg = appctx.getConfig().getDispatcher(dname);
		DispatcherDef def = new DispatcherDef.Builder().withXmlConfig(dcfg).build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.getName());
		org.junit.Assert.assertFalse(dsptch.isRunning());
		try {
			Dispatcher.create(appctx, def, bootlog);
			org.junit.Assert.fail("Failed to trap duplicate Dispatcher name");
		} catch (NAFConfigException ex) {}
		org.junit.Assert.assertFalse(dsptch.isRunning());
		boolean sts = dsptch.stop();
		org.junit.Assert.assertTrue(sts);
		org.junit.Assert.assertFalse(dsptch.isRunning());

		dcfg = appctx.getConfig().getDispatcher("x"+dname);
		org.junit.Assert.assertNull(dcfg);
	}

	@org.junit.Test
	public void testDynamic() throws java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		ApplicationContextNAF appctx = TestUtils.createApplicationContext("DispatcherTest-Dynamic", true);
		String dname = "utest_dynamic1";
		DispatcherDef def = new DispatcherDef.Builder()
				.withName(dname)
				.withDNS(true)
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.getName());
		org.junit.Assert.assertTrue(dsptch.getApplicationContext().getConfig().isAnonymousBasePort());
		dsptch.start();
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop();
		waitStopped(dsptch);

		//make sure it can be run again
		dsptch = Dispatcher.create(appctx, def, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.getName());
		org.junit.Assert.assertTrue(dsptch.getApplicationContext().getConfig().isAnonymousBasePort());
		dsptch.start();
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop();
		waitStopped(dsptch);
	}

	private static void waitStopped(Dispatcher dsptch) {
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}
}
