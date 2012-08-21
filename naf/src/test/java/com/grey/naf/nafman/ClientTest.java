/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.FileOps;
import com.grey.naf.DispatcherDef;
import com.grey.naf.reactor.Dispatcher;

public class ClientTest
{
	private static final Command.Def fakecmd1 = new Command.Def(254, "fake-cmd-1", 2, 2, false, null);
	private static final Command.Def fakecmd2 = new Command.Def(253, "fake-cmd-2", 0, 0, true, null);
	private static final Command.Def stopcmd = Registry.get().getCommand(Registry.CMD_STOP);
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("");

	@org.junit.Test
	public void testDefs() throws com.grey.base.ConfigException
	{
		Registry.get().loadCommand(fakecmd1);
		org.junit.Assert.assertSame(fakecmd1, Registry.get().getCommand(fakecmd1.code));
		org.junit.Assert.assertEquals(Registry.CMD_STOP, stopcmd.code);
		org.junit.Assert.assertEquals("stop", stopcmd.name);
		org.junit.Assert.assertNull(Primary.get());

		Command.Def stop2 = new Command.Def(stopcmd.code, "anothername", stopcmd.min_args, stopcmd.max_args,
				stopcmd.dispatcher1, stopcmd.usage);
		try {
			Registry.get().loadCommand(stop2);
			org.junit.Assert.fail("Failed to detect command redefinition");
		} catch (com.grey.base.ConfigException ex) {}
		Command.Def dupstop = new Command.Def(stopcmd.code, stopcmd.name, stopcmd.min_args, stopcmd.max_args,
				stopcmd.dispatcher1, stopcmd.usage);
		stop2 = Registry.get().loadCommand(dupstop);
		org.junit.Assert.assertSame(stopcmd, stop2);
	}

	// For this to complete at all is proof of correctness. It would hang if shutdown failed
	@org.junit.Test
	public void testStopSolo() throws Exception
	{
		String cfgpath = FileOps.getResourcePath("/naf.xml", getClass());
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgpath);
		String dname = "testdispatcher1";

		Dispatcher dsptch = Dispatcher.createConfigured(dname, nafcfg, logger);
		org.junit.Assert.assertTrue(dsptch.nafman.isPrimary());
		org.junit.Assert.assertSame(dsptch.nafman, Primary.get());
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.name, "-remote", String.valueOf(dsptch.nafman.getPort())});
		dsptch.waitStopped();

		dsptch = Dispatcher.createConfigured(dname, nafcfg, logger);
		dsptch.start();
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.name, "-remote", "localhost:"+String.valueOf(dsptch.nafman.getPort())});
		dsptch.waitStopped();

		dsptch = Dispatcher.createConfigured(dname, nafcfg, logger);
		dsptch.start();
		// this should be ignored by the Dispatcher as it has no handlers
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", fakecmd1.name, "-c", cfgpath, "arg1", "arg2"});
		// this should be rejected by the Dispatcher as unrecognised
		Client.submitCommand(null, dsptch.nafman.getPort(), fakecmd2, null, logger);
		// this should be ignored by the live Dispatcher as not addressed to it
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.name, "-c", cfgpath, "no-such-dispatcher"});
		//... and the fact this doesn't error is proof that the above was ignored and Dispatcher still alive
		com.grey.naf.Launcher.main(new String[] {"-q", "-cmd", stopcmd.name, "-c", cfgpath, dsptch.name});
		dsptch.waitStopped();

		try {
			Client.submitLocalCommand(cfgpath, stopcmd, null, null);
			org.junit.Assert.fail("Expected this to fail - and to detect its failure");
		} catch (java.io.IOException ex) {}
	}

	@org.junit.Test
	public void testStopMulti() throws com.grey.base.GreyException, java.io.IOException
	{
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "d1";
		def.surviveHandlers = false;
		Dispatcher dp = Dispatcher.create(def, null, logger);
		def.name = "d2";
		Dispatcher ds1 = Dispatcher.create(def, null, logger);
		def.name = "d3";
		Dispatcher ds2 = Dispatcher.create(def, null, logger);
		dp.start();
		ds1.start();
		ds2.start();
		org.junit.Assert.assertTrue(dp.nafman.isPrimary());
		org.junit.Assert.assertFalse(ds1.nafman.isPrimary());
		org.junit.Assert.assertFalse(ds2.nafman.isPrimary());
		Client.submitCommand(null, ds1.nafman.getPort(), stopcmd, new String[]{ds2.name}, logger);
		ds2.waitStopped();
		org.junit.Assert.assertFalse(ds2.isRunning());
		try {Thread.sleep(100);} catch (Exception ex) {}
		org.junit.Assert.assertTrue(dp.isRunning());
		org.junit.Assert.assertTrue(ds1.isRunning());
		Client.submitCommand(null, ds1.nafman.getPort(), stopcmd, null, logger);
		dp.waitStopped();
		ds1.waitStopped();
	}

	// Just exercise the code for various commands. All we're looking for is that it doesn't crash ...
	@org.junit.Test
	public void testCommands() throws com.grey.base.GreyException, java.io.IOException
	{
		DispatcherDef def = new DispatcherDef();
		def.hasDNS = true;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, null, logger);
		dsptch.start();
		Registry reg = Registry.get();
		int port =  dsptch.nafman.getPort();
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_DLIST), null, dsptch.logger);
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_DSHOW), null, dsptch.logger);
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_FLUSH), null, dsptch.logger);
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_SHOWCMDS), null, dsptch.logger);
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_DNSDUMP), null, dsptch.logger);
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_DNSPRUNE), null, dsptch.logger);
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_APPSTOP), null, dsptch.logger); //invalid args
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_APPSTOP), new String[]{dsptch.name, "no-such-app"}, dsptch.logger);
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_APPSTOP), new String[]{"no-such-dispatcher", "no-such-app"}, dsptch.logger);
		org.junit.Assert.assertTrue(dsptch.isRunning());
		Client.submitCommand(null, port, reg.getCommand(Registry.CMD_STOP), null, dsptch.logger);
		dsptch.waitStopped();
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}
}
