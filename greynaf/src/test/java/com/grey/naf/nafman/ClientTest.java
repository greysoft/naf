/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.DispatcherDef;
import com.grey.naf.NAFConfig;
import com.grey.naf.reactor.Dispatcher;

public class ClientTest
{
	private static final NafManRegistry nafreg = NafManRegistry.get(ApplicationContextNAF.create(null));
	private static final String rootdir = com.grey.naf.reactor.DispatcherTest.initPaths(ClientTest.class);
	private static final NafManRegistry.DefCommand fakecmd1 = new NafManRegistry.DefCommand("fake-cmd-1", "utest", "fake1", null, false);
	private static final NafManRegistry.DefCommand fakecmd2 = new NafManRegistry.DefCommand("fake-cmd-2", "utest", "fake2", null, false);
	private static final NafManRegistry.DefCommand stopcmd = nafreg.getCommand(NafManRegistry.CMD_STOP);
	private static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("");
	private static final int cfgbaseport = NAFConfig.RSVPORT_ANON; //same as in resources:naf.xml

	public ClientTest() throws java.io.IOException {
		FileOps.deleteDirectory(rootdir);
	}

	// For this to complete at all is proof of correctness. It would hang if shutdown failed
	@org.junit.Test
	public void testStopSolo() throws Exception
	{
		java.net.URL url = DynLoader.getResource("/naf.xml", getClass());
		String cfgpath = new java.io.File(url.toURI()).getCanonicalPath();
		com.grey.naf.NAFConfig nafcfg = com.grey.naf.NAFConfig.load(cfgpath);
		ApplicationContextNAF appctx = ApplicationContextNAF.create(null, nafcfg);
		String dname = "testdispatcher1";

		Dispatcher dsptch = Dispatcher.createConfigured(appctx, dname, logger);
		NafManAgent agent = dsptch.getAgent();
		org.junit.Assert.assertTrue(agent.isPrimary());
		org.junit.Assert.assertSame(agent, appctx.getPrimaryAgent());
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code, "-remote", String.valueOf(agent.getPort())});
		waitStopped(dsptch);

		dsptch = Dispatcher.createConfigured(appctx, dname, logger);
		agent = dsptch.getAgent();
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code,
				"-remote", "localhost:"+String.valueOf(agent.getPort())});
		waitStopped(dsptch);

		dsptch = Dispatcher.createConfigured(appctx, dname, logger);
		agent = dsptch.getAgent();
		dsptch.start();
		// this should be ignored by the Dispatcher as it has no handlers
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", fakecmd1.code, "-c", cfgpath,
				"-remote", "localhost:"+String.valueOf(agent.getPort()), "arg1", "arg2"});
		// this should be rejected by the Dispatcher as unrecognised
		NafManClient.submitCommand(fakecmd2.code, null, agent.getPort(), logger);
		// this should be ignored by the live Dispatcher as not addressed to it
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code+"?"+NafManCommand.ATTR_DISPATCHER+"=no-such-dispatcher",
				"-c", cfgpath, "-remote", "localhost:"+String.valueOf(agent.getPort())});
		//... and the fact this doesn't error is proof that the above was ignored and Dispatcher still alive
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code+"?"+NafManCommand.ATTR_DISPATCHER+"="+dsptch.name,
				"-c", cfgpath, "-remote", "localhost:"+String.valueOf(agent.getPort())});
		waitStopped(dsptch);

		try {
			NafManClient.submitLocalCommand(stopcmd.code, agent.getPort(), null);
			org.junit.Assert.fail("Failed to trap command submission to stopped Dispatcher");
		} catch (java.io.IOException ex) {}
	}

	@org.junit.Test
	public void testStopMulti() throws java.io.IOException
	{
		ApplicationContextNAF appctx = ApplicationContextNAF.create(null, new NAFConfig.Defs(cfgbaseport));
		DispatcherDef def = new DispatcherDef();
		def.name = "utest_d1";
		def.hasNafman = true;
		def.surviveHandlers = false;
		Dispatcher dp = Dispatcher.create(appctx, def, logger);
		def.name = "utest_d2";
		Dispatcher ds1 = Dispatcher.create(appctx, def, logger);
		def.name = "utest_d3";
		Dispatcher ds2 = Dispatcher.create(appctx, def, logger);
		dp.start();
		ds1.start();
		ds2.start();
		org.junit.Assert.assertTrue(dp.getAgent().isPrimary());
		org.junit.Assert.assertFalse(ds1.getAgent().isPrimary());
		org.junit.Assert.assertFalse(ds2.getAgent().isPrimary());
		NafManClient.submitCommand(stopcmd.code+"?"+NafManCommand.ATTR_DISPATCHER+"="+ds2.name, null, ds1.getAgent().getPort(), logger);
		waitStopped(ds2);
		org.junit.Assert.assertFalse(ds2.isRunning());
		com.grey.naf.reactor.TimerNAF.sleep(100);
		org.junit.Assert.assertTrue(dp.isRunning());
		org.junit.Assert.assertTrue(ds1.isRunning());
		NafManClient.submitCommand(stopcmd.code, null, ds1.getAgent().getPort(), logger);
		waitStopped(dp);
		waitStopped(ds1);
	}

	// Just exercise the code for various commands. All we're looking for is that it doesn't crash ...
	@org.junit.Test
	public void testCommands() throws java.io.IOException
	{
		ApplicationContextNAF appctx = ApplicationContextNAF.create(null, new NAFConfig.Defs(cfgbaseport));
		DispatcherDef def = new DispatcherDef();
		def.name = "utest_allcmds";
		def.hasNafman = true;
		def.hasDNS = true;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(appctx, def, logger);
		dsptch.start();
		NafManRegistry reg = NafManRegistry.get(appctx);
		int port = dsptch.getAgent().getPort();
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_DLIST).code, null, port, dsptch.getLogger());
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_DSHOW).code, null, port, dsptch.getLogger());
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_FLUSH).code, null, port, dsptch.getLogger());
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_SHOWCMDS).code, null, port, dsptch.getLogger());
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_DNSDUMP).code, null, port, dsptch.getLogger());
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_DNSPRUNE).code, null, port, dsptch.getLogger());
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_DNSQUERY).code, null, port, dsptch.getLogger());
		NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_APPSTOP).code, null, port, dsptch.getLogger()); //missing args
		String cmd = NafManRegistry.CMD_APPSTOP+"?"+NafManCommand.ATTR_DISPATCHER+"=no-such-disp&"+NafManCommand.ATTR_NAFLET+"=no-such-app";
		NafManClient.submitCommand(cmd, null, port, dsptch.getLogger()); //missing args
		cmd = reg.getCommand(NafManRegistry.CMD_LOGLVL).code+"?"+NafManCommand.ATTR_LOGLVL+"=";
		Logger.LEVEL lvl = dsptch.getLogger().getLevel();
		NafManClient.submitCommand(cmd+"info", null, port, dsptch.getLogger());
		if (lvl != Logger.LEVEL.INFO) NafManClient.submitCommand(cmd+lvl.toString(), null, port, dsptch.getLogger());
		NafManClient.submitCommand(cmd+"badlevel", null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(dsptch.isRunning());
		NafManClient.submitCommand(stopcmd.code, null, port, dsptch.getLogger());
		waitStopped(dsptch);
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}
	
	private static void waitStopped(Dispatcher dsptch) {
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
	}
}