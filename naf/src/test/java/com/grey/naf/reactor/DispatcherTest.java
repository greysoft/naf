/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.naf.reactor.Dispatcher;

public class DispatcherTest
{
	static {
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_VAR, SysProps.TMPDIR+"/utest/dispatcher");
	}
	private static final com.grey.logging.Logger bootlog = com.grey.logging.Factory.getLoggerNoEx("");

	// Completion is enough to satisfy these tests
	@org.junit.Test
	public void testConfig() throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException
	{
		String dname = "testdispatcher1";
		String cfgpath = com.grey.base.utils.FileOps.getResourcePath("/naf.xml", getClass());
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgpath);
		Dispatcher.launchConfigured(nafcfg, bootlog);
		org.junit.Assert.assertEquals(1, Dispatcher.getDispatchers().length);
		Dispatcher dsptch = Dispatcher.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dsptch);
		org.junit.Assert.assertEquals(dname, dsptch.name);
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop(null);
		dsptch.waitStopped();
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}

	@org.junit.Test
	public void testNamedConfig() throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException
	{
		String dname = "testdispatcher1";
		String cfgpath = com.grey.base.utils.FileOps.getResourcePath("/naf.xml", getClass());
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgpath);
		Dispatcher dsptch = Dispatcher.createConfigured(dname, nafcfg, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.name);
		org.junit.Assert.assertFalse(dsptch.isRunning());
		try {
			Dispatcher.createConfigured(dname, nafcfg, bootlog);
			org.junit.Assert.fail("Failed to trap duplicate Dispatcher name");
		} catch (com.grey.base.ConfigException ex) {}
		dsptch.stop(dsptch);
		org.junit.Assert.assertFalse(dsptch.isRunning());

		dsptch = Dispatcher.createConfigured("x"+dname, nafcfg, bootlog);
		org.junit.Assert.assertNull(dsptch);
	}

	@org.junit.Test
	public void testDynamic() throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException
	{
		String dname = "utest_dynamic1";
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = dname;
		def.hasDNS = true;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, null, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.name);
		dsptch.start();
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop(null);
		dsptch.waitStopped();
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}

	@org.junit.Test
	public void testBasePort() throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException
	{
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "utest_baseport";
		Dispatcher dsptch = Dispatcher.create(def, null, bootlog);
		int baseport = dsptch.nafcfg.getPort(0);
		dsptch.stop(dsptch);

		dsptch = Dispatcher.create(def, baseport+1, bootlog);
		org.junit.Assert.assertEquals(baseport+1, dsptch.nafcfg.getPort(0));
		dsptch.stop(dsptch);

		dsptch = Dispatcher.create(def, 0, bootlog);
		org.junit.Assert.assertEquals(baseport, dsptch.nafcfg.getPort(0));
		dsptch.stop(dsptch);
	}
}
