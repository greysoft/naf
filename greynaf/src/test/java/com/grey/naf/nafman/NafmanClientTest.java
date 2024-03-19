/*
 * Copyright 2012-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.config.XmlConfig;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.TestUtils;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.config.DispatcherConfig;

public class NafmanClientTest
{
	private static final String rootdir = com.grey.naf.TestUtils.initPaths(NafmanClientTest.class);
	private static final NafManRegistry.DefCommand fakecmd1 = new NafManRegistry.DefCommand("fake-cmd-1", "utest", "fake1", null, false);
	private static final NafManRegistry.DefCommand fakecmd2 = new NafManRegistry.DefCommand("fake-cmd-2", "utest", "fake2", null, false);
	private static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("");

	public NafmanClientTest() throws java.io.IOException {
		FileOps.deleteDirectory(rootdir);
	}

	// For this to complete at all is proof of correctness. It would hang if shutdown failed
	@org.junit.Test
	public void testStopSolo() throws Exception
	{
		java.net.URL url = DynLoader.getResource("/naf.xml", getClass());
		String cfgpath = new java.io.File(url.toURI()).getCanonicalPath();
		ApplicationContextNAF appctx = TestUtils.createApplicationContext(null, cfgpath, true, logger);
		NafManRegistry nafreg = NafManRegistry.get(appctx);
		NafManRegistry.DefCommand stopcmd = nafreg.getCommand(NafManRegistry.CMD_STOP);

		String dname = "testdispatcher1";
		XmlConfig dcfg = appctx.getNafConfig().getDispatcherConfigNode(dname);
		DispatcherConfig def = DispatcherConfig.builder()
				.withXmlConfig(dcfg)
				.withAppContext(appctx)
				.build();

		Dispatcher dsptch = Dispatcher.create(def);
		NafManAgent agent = dsptch.getNafManAgent();
		org.junit.Assert.assertTrue(agent.isPrimary());
		org.junit.Assert.assertSame(agent, appctx.getNamedItem(PrimaryAgent.class.getName(), null));
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code, "-remote", String.valueOf(agent.getPort())});
		waitStopped(dsptch);

		dsptch = Dispatcher.create(def);
		agent = dsptch.getNafManAgent();
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code,
				"-remote", "localhost:"+String.valueOf(agent.getPort())});
		waitStopped(dsptch);

		dsptch = Dispatcher.create(def);
		agent = dsptch.getNafManAgent();
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
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.code+"?"+NafManCommand.ATTR_DISPATCHER+"="+dsptch.getName(),
				"-c", cfgpath, "-remote", "localhost:"+String.valueOf(agent.getPort())});
		waitStopped(dsptch);

		try {
			NafManClient.submitCommand(stopcmd.code, null, agent.getPort(), null);
			org.junit.Assert.fail("Failed to trap command submission to stopped Dispatcher");
		} catch (java.io.IOException ex) {}
	}

	@org.junit.Test
	public void testStopMulti() throws Exception
	{
		ApplicationContextNAF appctx = TestUtils.createApplicationContext(null, true, logger);
		NafManRegistry nafreg = NafManRegistry.get(appctx);
		NafManRegistry.DefCommand stopcmd = nafreg.getCommand(NafManRegistry.CMD_STOP);
		DispatcherConfig def = com.grey.naf.reactor.config.DispatcherConfig.builder()
				.withName("utest_d1")
				.withAppContext(appctx)
				.withSurviveHandlers(false)
				.build();
		Dispatcher dp = Dispatcher.create(def);
		def = def.mutate().withName("utest_d2").build();
		Dispatcher ds1 = Dispatcher.create(def);
		def = def.mutate().withName("utest_d3").build();
		Dispatcher ds2 = Dispatcher.create(def);
		dp.start();
		ds1.start();
		ds2.start();
		org.junit.Assert.assertTrue(dp.getNafManAgent().isPrimary());
		org.junit.Assert.assertFalse(ds1.getNafManAgent().isPrimary());
		org.junit.Assert.assertFalse(ds2.getNafManAgent().isPrimary());
		Thread.sleep(1_000); //give secondaruies time to register
		NafManClient.submitCommand(stopcmd.code+"?"+NafManCommand.ATTR_DISPATCHER+"="+ds2.getName(), null, ds1.getNafManAgent().getPort(), logger);
		waitStopped(ds2);
		com.grey.naf.reactor.TimerNAF.sleep(100);
		org.junit.Assert.assertTrue(dp.isRunning());
		org.junit.Assert.assertTrue(ds1.isRunning());
		NafManClient.submitCommand(stopcmd.code, null, ds1.getNafManAgent().getPort(), logger);
		waitStopped(dp);
		waitStopped(ds1);
	}

	// Just exercise the code for various commands. All we're looking for is rough evidence of responsiveness.
	@org.junit.Test
	public void testCommands() throws Exception
	{
		ApplicationContextNAF appctx = TestUtils.createApplicationContext(null, true, logger);
		NafManRegistry reg = NafManRegistry.get(appctx);
		NafManRegistry.DefCommand stopcmd = reg.getCommand(NafManRegistry.CMD_STOP);
		DispatcherConfig def = com.grey.naf.reactor.config.DispatcherConfig.builder()
				.withName("utest_allcmds")
				.withSurviveHandlers(false)
				.withAppContext(appctx)
				.build();
		Dispatcher dsptch = Dispatcher.create(def);
		dsptch.start();
		int port = dsptch.getNafManAgent().getPort();

		String rsp = NafManClient.submitCommand(null, null, port, dsptch.getLogger()); //home page
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		rsp = NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_DLIST).code, null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		rsp = NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_DSHOW).code, null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		rsp = NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_FLUSH).code, null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		rsp = NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_SHOWCMDS).code, null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		rsp = NafManClient.submitCommand(reg.getCommand(NafManRegistry.CMD_APPSTOP).code, null, port, dsptch.getLogger()); //missing args
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));

		String cmd = NafManRegistry.CMD_APPSTOP+"?"+NafManCommand.ATTR_DISPATCHER+"=no-such-disp&"+NafManCommand.ATTR_NAFLET+"=no-such-app";
		rsp = NafManClient.submitCommand(cmd, null, port, dsptch.getLogger()); //missing args
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));

		cmd = reg.getCommand(NafManRegistry.CMD_LOGLVL).code+"?"+NafManCommand.ATTR_LOGLVL+"=";
		rsp = NafManClient.submitCommand(cmd+"info", null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));

		Logger.LEVEL lvl = dsptch.getLogger().getLevel();
		if (lvl != Logger.LEVEL.INFO) {
			rsp = NafManClient.submitCommand(cmd+lvl.toString(), null, port, dsptch.getLogger());
			org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		}
		rsp = NafManClient.submitCommand(cmd+"badlevel", null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		org.junit.Assert.assertTrue(dsptch.isRunning());

		rsp = NafManClient.submitCommand(stopcmd.code, null, port, dsptch.getLogger());
		org.junit.Assert.assertTrue(rsp, rsp.startsWith("HTTP/1.1 200 OK"));
		waitStopped(dsptch);
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}

	private static void waitStopped(Dispatcher dsptch) {
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertFalse(dsptch.isRunning());
		org.junit.Assert.assertTrue(dsptch.completedOK());
	}
}