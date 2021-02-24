/*
 * Copyright 2011-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.DynLoader;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.DispatcherDef;
import com.grey.naf.NAFConfig;
import com.grey.naf.errors.NAFConfigException;

public class DispatcherTest
{
	private static final String rootdir = initPaths(DispatcherTest.class);
	private static final com.grey.logging.Logger bootlog = com.grey.logging.Factory.getLoggerNoEx("");

	public static String initPaths(Class<?> clss)
	{
		String rootpath = SysProps.TMPDIR+"/utest/naf/"+clss.getPackage().getName()+"/"+clss.getSimpleName();
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_ROOT, rootpath);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_CONF, null);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_VAR, null);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_LOGS, null);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_TMP, null);
		try {
			FileOps.deleteDirectory(rootpath);
		} catch (Exception ex) {
			throw new RuntimeException("DispatcherTest.initPaths failed to remove root="+rootpath+" - "+ex, ex);
		}
		return rootpath;
	}

	// Completion is enough to satisfy these tests
	@org.junit.Test
	public void testConfig() throws java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		String dname = "testdispatcher1";
		String cfgpath = getResourcePath("/naf.xml", getClass());
		NAFConfig nafcfg = NAFConfig.load(cfgpath);
		ApplicationContextNAF appctx = ApplicationContextNAF.create("DispatcherTest-Config", nafcfg);
		Dispatcher.launchConfigured(appctx, bootlog);
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
		String dname = "testdispatcher1";
		String cfgpath = getResourcePath("/naf.xml", getClass());
		NAFConfig nafcfg = NAFConfig.load(cfgpath);
		ApplicationContextNAF appctx = ApplicationContextNAF.create("DispatcherTest-NamedConfig", nafcfg);
		Dispatcher dsptch = Dispatcher.createConfigured(appctx, dname, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.getName());
		org.junit.Assert.assertFalse(dsptch.isRunning());
		try {
			Dispatcher.createConfigured(appctx, dname, bootlog);
			org.junit.Assert.fail("Failed to trap duplicate Dispatcher name");
		} catch (NAFConfigException ex) {}
		org.junit.Assert.assertFalse(dsptch.isRunning());
		boolean sts = dsptch.stop();
		org.junit.Assert.assertTrue(sts);
		org.junit.Assert.assertFalse(dsptch.isRunning());

		dsptch = Dispatcher.createConfigured(appctx, "x"+dname, bootlog);
		org.junit.Assert.assertNull(dsptch);
	}

	@org.junit.Test
	public void testDynamic() throws java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		ApplicationContextNAF appctx = ApplicationContextNAF.create("DispatcherTest-Dynamic");
		String dname = "utest_dynamic1";
		DispatcherDef def = new DispatcherDef.Builder()
				.withName(dname)
				.withDNS(true)
				.withNafman(true)
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

	// NB: The concept of mapping a resource URL to a File is inherently flawed, but this utility works
	// beecause the resources we're looking up are in the same build tree.
	public static String getResourcePath(String path, Class<?> clss) throws java.io.IOException, java.net.URISyntaxException
	{
		java.net.URL url = DynLoader.getResource(path, clss);
		if (url == null) return null;
		return new java.io.File(url.toURI()).getCanonicalPath();
	}
	
	private static void waitStopped(Dispatcher dsptch) {
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}
}
