/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.logging.Logger;
import com.grey.naf.DispatcherDef;
import com.grey.naf.reactor.Dispatcher;

public class ClientTest
{
	private static final String rootdir = com.grey.naf.reactor.DispatcherTest.initPaths(ClientTest.class);
	private static final Registry.DefCommand fakecmd1 = new Registry.DefCommand("fake-cmd-1", "utest", "fake1", null, false);
	private static final Registry.DefCommand fakecmd2 = new Registry.DefCommand("fake-cmd-2", "utest", "fake2", null, false);
	private static final Registry.DefCommand stopcmd = Registry.get().getCommand(Registry.CMD_STOP);
	private static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("");

	@org.junit.Test
	public void testDefs() throws com.grey.base.ConfigException, java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		Registry.get().loadCommands(new Registry.DefCommand[]{fakecmd1});
		org.junit.Assert.assertSame(fakecmd1, Registry.get().getCommand(fakecmd1.code));
		org.junit.Assert.assertEquals(Registry.CMD_STOP, stopcmd.code);
		org.junit.Assert.assertNull(Primary.get());

		Registry.DefCommand stop2 = new Registry.DefCommand(stopcmd.code, "utest", "dup stop", null, false);
		try {
			Registry.get().loadCommands(new Registry.DefCommand[]{stop2});
			org.junit.Assert.fail("Failed to detect command redefinition");
		} catch (com.grey.base.ConfigException ex) {}
	}

	// For this to complete at all is proof of correctness. It would hang if shutdown failed
	@org.junit.Test
	public void testStopSolo() throws Exception
	{
		FileOps.deleteDirectory(rootdir);
		java.net.URL url = DynLoader.getResource("/naf.xml", getClass());
		String cfgpath = new java.io.File(url.toURI()).getCanonicalPath();
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgpath);
		String dname = "testdispatcher1";

		Dispatcher dsptch = Dispatcher.createConfigured(dname, nafcfg, logger);
		org.junit.Assert.assertTrue(dsptch.nafman.isPrimary());
		org.junit.Assert.assertSame(dsptch.nafman, Primary.get());
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code, "-remote", String.valueOf(dsptch.nafman.getPort())});
		dsptch.waitStopped();

		dsptch = Dispatcher.createConfigured(dname, nafcfg, logger);
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code, "-remote", "localhost:"+String.valueOf(dsptch.nafman.getPort())});
		dsptch.waitStopped();

		dsptch = Dispatcher.createConfigured(dname, nafcfg, logger);
		dsptch.start();
		// this should be ignored by the Dispatcher as it has no handlers
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", fakecmd1.code, "-c", cfgpath, "arg1", "arg2"});
		// this should be rejected by the Dispatcher as unrecognised
		Client.submitCommand(fakecmd2.code, null, dsptch.nafman.getPort(), logger);
		// this should be ignored by the live Dispatcher as not addressed to it
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code+"?"+Command.ATTR_DISPATCHER+"=no-such-dispatcher", "-c", cfgpath});
		//... and the fact this doesn't error is proof that the above was ignored and Dispatcher still alive
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code+"?"+Command.ATTR_DISPATCHER+"="+dsptch.name, "-c", cfgpath});
		dsptch.waitStopped();

		try {
			Client.submitLocalCommand(stopcmd.code, cfgpath, null);
			org.junit.Assert.fail("Expected this to fail - and to detect its failure");
		} catch (java.io.IOException ex) {}
	}

	@org.junit.Test
	public void testStopMulti() throws com.grey.base.GreyException, java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "utest_d1";
		def.surviveHandlers = false;
		Dispatcher dp = Dispatcher.create(def, null, logger);
		def.name = "utest_d2";
		Dispatcher ds1 = Dispatcher.create(def, null, logger);
		def.name = "utest_d3";
		Dispatcher ds2 = Dispatcher.create(def, null, logger);
		dp.start();
		ds1.start();
		ds2.start();
		org.junit.Assert.assertTrue(dp.nafman.isPrimary());
		org.junit.Assert.assertFalse(ds1.nafman.isPrimary());
		org.junit.Assert.assertFalse(ds2.nafman.isPrimary());
		Client.submitCommand(stopcmd.code+"?"+Command.ATTR_DISPATCHER+"="+ds2.name, null, ds1.nafman.getPort(), logger);
		ds2.waitStopped();
		org.junit.Assert.assertFalse(ds2.isRunning());
		com.grey.naf.reactor.Timer.sleep(100);
		org.junit.Assert.assertTrue(dp.isRunning());
		org.junit.Assert.assertTrue(ds1.isRunning());
		Client.submitCommand(stopcmd.code, null, ds1.nafman.getPort(), logger);
		dp.waitStopped();
		ds1.waitStopped();
	}

	// Just exercise the code for various commands. All we're looking for is that it doesn't crash ...
	@org.junit.Test
	public void testCommands() throws com.grey.base.GreyException, java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		DispatcherDef def = new DispatcherDef();
		def.name = "utest_allcmds";
		def.hasDNS = true;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, null, logger);
		dsptch.start();
		Registry reg = Registry.get();
		int port =  dsptch.nafman.getPort();
		Client.submitCommand(reg.getCommand(Registry.CMD_DLIST).code, null, port, dsptch.logger);
		Client.submitCommand(reg.getCommand(Registry.CMD_DSHOW).code, null, port, dsptch.logger);
		Client.submitCommand(reg.getCommand(Registry.CMD_FLUSH).code, null, port, dsptch.logger);
		Client.submitCommand(reg.getCommand(Registry.CMD_SHOWCMDS).code, null, port, dsptch.logger);
		Client.submitCommand(reg.getCommand(Registry.CMD_DNSDUMP).code, null, port, dsptch.logger);
		Client.submitCommand(reg.getCommand(Registry.CMD_DNSPRUNE).code, null, port, dsptch.logger);
		Client.submitCommand(reg.getCommand(Registry.CMD_DNSQUERY).code, null, port, dsptch.logger);
		Client.submitCommand(reg.getCommand(Registry.CMD_APPSTOP).code, null, port, dsptch.logger); //missing args
		String cmd = Registry.CMD_APPSTOP+"?"+Command.ATTR_DISPATCHER+"=no-such-disp&"+Command.ATTR_NAFLET+"=no-such-app";
		Client.submitCommand(cmd, null, port, dsptch.logger); //missing args
		cmd = reg.getCommand(Registry.CMD_LOGLVL).code+"?"+Command.ATTR_LOGLVL+"=";
		Logger.LEVEL lvl = dsptch.logger.getLevel();
		Client.submitCommand(cmd+"info", null, port, dsptch.logger);
		if (lvl != Logger.LEVEL.INFO) Client.submitCommand(cmd+lvl.toString(), null, port, dsptch.logger);
		Client.submitCommand(cmd+"badlevel", null, port, dsptch.logger);
		org.junit.Assert.assertTrue(dsptch.isRunning());
		Client.submitCommand(stopcmd.code, null, port, dsptch.logger);
		dsptch.waitStopped();
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}
}