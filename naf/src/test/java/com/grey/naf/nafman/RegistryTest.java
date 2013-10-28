/*
 * Copyright 2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.naf.DispatcherDef;
import com.grey.naf.reactor.Dispatcher;

public class RegistryTest
{
	private static final String rootdir = com.grey.naf.reactor.DispatcherTest.initPaths(RegistryTest.class);
	private static final Registry.DefCommand fakecmd1 = new Registry.DefCommand("fake-cmd-1", "utest", "fake1", null, false);
	private static final Registry.DefCommand fakecmd2 = new Registry.DefCommand("fake-cmd-2", "utest", "fake2", null, false);
	private static final Registry.DefCommand stopcmd = Registry.get().getCommand(Registry.CMD_STOP);
	private static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("");

	public RegistryTest()
		throws InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException,
			IllegalArgumentException, java.lang.reflect.InvocationTargetException, java.io.IOException
	{
		Class<?>[] nested = Registry.class.getDeclaredClasses();
		int cnt = (nested == null ? 0 : nested.length);
		for (int idx = 0; idx != cnt; idx++) {
			if (nested[idx].getSimpleName().equals("SingletonHolder")) {
				java.lang.reflect.Constructor<Registry> ctor = Registry.class.getDeclaredConstructor(new Class<?>[]{});
				ctor.setAccessible(true);
				Registry reg = ctor.newInstance();
				DynLoader.setField(nested[idx], "instance", reg, null);
				break;
			}
		}
		FileOps.deleteDirectory(rootdir);
	}

	@org.junit.Test
	public void testResources() throws com.grey.base.ConfigException, java.io.IOException
	{
		String homepage = "nafhome";
		org.junit.Assert.assertEquals(homepage, Registry.get().getHomePage());
		Registry.get().setHomePage(Registry.RSRC_CMDSTATUS);
		org.junit.Assert.assertEquals(Registry.RSRC_CMDSTATUS, Registry.get().getHomePage());
		try {
			Registry.get().setHomePage("no-such-resource");
			org.junit.Assert.fail("Failed to trap invalid home page");
		} catch (com.grey.base.ConfigException ex) {}
		org.junit.Assert.assertEquals(Registry.RSRC_CMDSTATUS, Registry.get().getHomePage());

		Registry.DefResource rsrc = new Registry.DefResource(Registry.CMD_STOP, "cmdstatus.xsl", null, null);
		try {
			Registry.get().loadResources(new Registry.DefResource[]{rsrc});
			org.junit.Assert.fail("Failed to trap resource that conflicts with commands");
		} catch (com.grey.base.ConfigException ex) {}

		Registry.DefCommand cmd = new Registry.DefCommand(homepage, "utest", "rsrc-conflict", null, false);
		try {
			Registry.get().loadCommands(new Registry.DefCommand[]{cmd});
			org.junit.Assert.fail("Failed to trap command that conflicts with resources");
		} catch (com.grey.base.ConfigException ex) {}
	}

	@org.junit.Test
	public void testCommands() throws com.grey.base.ConfigException
	{
		int cnt1 = Registry.get().getCommands().size();
		Registry.get().loadCommands(new Registry.DefCommand[]{fakecmd1});
		org.junit.Assert.assertSame(fakecmd1, Registry.get().getCommand(fakecmd1.code));
		org.junit.Assert.assertEquals(Registry.CMD_STOP, stopcmd.code);
		org.junit.Assert.assertNull(Primary.get());
		org.junit.Assert.assertEquals(cnt1+1, Registry.get().getCommands().size());

		Registry.DefCommand stop2 = new Registry.DefCommand(stopcmd.code, "utest", "dup stop", null, false);
		try {
			Registry.get().loadCommands(new Registry.DefCommand[]{stop2});
			org.junit.Assert.fail("Failed to trap command redefinition");
		} catch (com.grey.base.ConfigException ex) {}
		org.junit.Assert.assertEquals(cnt1+1, Registry.get().getCommands().size());
	}

	@org.junit.Test
	public void testHandlerDefs() throws com.grey.base.GreyException, java.io.IOException {
		@SuppressWarnings("unchecked")
		java.util.HashSet<Dispatcher> reg_inviolate
			= (java.util.HashSet<Dispatcher>)DynLoader.getField(Registry.get(), "inviolate_handlers");
		@SuppressWarnings("unchecked")
		java.util.HashMap<String, java.util.List<Object>> reg_handlers
			= (java.util.HashMap<String, java.util.List<Object>>)DynLoader.getField(Registry.get(), "cmd_handlers");
		com.grey.base.utils.HashedMap<String, java.util.ArrayList<Command.Handler>> dsptch_handlers
			= new com.grey.base.utils.HashedMap<String, java.util.ArrayList<Command.Handler>>();
		Dispatcher dsptch1 = Dispatcher.create(new DispatcherDef(), 0, logger);
		Dispatcher dsptch2 = Dispatcher.create(new DispatcherDef(), 0, logger);
		Registry.get().loadCommands(new Registry.DefCommand[]{fakecmd1});
		Registry.get().loadCommands(new Registry.DefCommand[]{fakecmd2});
		reg_handlers.clear();

		org.junit.Assert.assertFalse(Registry.get().isCommandRegistered(fakecmd1.code));
		org.junit.Assert.assertFalse(Registry.get().isCommandRegistered(fakecmd1.code, dsptch1));
		dsptch_handlers.clear();
		Registry.get().getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(0, dsptch_handlers.size());

		reg_inviolate.clear();
		org.junit.Assert.assertEquals(0, reg_handlers.size());
		Boolean mod = Registry.get().registerHandler(fakecmd1.code, 12, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());
		org.junit.Assert.assertTrue(Registry.get().isCommandRegistered(fakecmd1.code));
		org.junit.Assert.assertTrue(Registry.get().isCommandRegistered(fakecmd1.code, dsptch1));
		org.junit.Assert.assertFalse(Registry.get().isCommandRegistered(fakecmd1.code, dsptch2));
		dsptch_handlers.clear();
		Registry.get().getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(1, dsptch_handlers.get(fakecmd1.code).size());
		mod = Registry.get().registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertFalse("Failed to trap changes to involate Dispatcher", mod);

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd1.code, 11, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd1.code, 13, new DummyHandler(), dsptch1);
		org.junit.Assert.assertFalse(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());
		dsptch_handlers.clear();
		Registry.get().getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(1, dsptch_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(2, reg_handlers.get(fakecmd1.code).size());
		dsptch_handlers.clear();
		Registry.get().getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(2, dsptch_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch2);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(3, reg_handlers.get(fakecmd1.code).size());
		dsptch_handlers.clear();
		Registry.get().getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(2, dsptch_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd2.code, 2, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(3, reg_handlers.get(fakecmd1.code).size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());
		dsptch_handlers.clear();
		Registry.get().getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(2, dsptch_handlers.size());
		org.junit.Assert.assertEquals(2, dsptch_handlers.get(fakecmd1.code).size());
		org.junit.Assert.assertEquals(1, dsptch_handlers.get(fakecmd2.code).size());

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd2.code, 3, new DummyHandler(), dsptch2);
		org.junit.Assert.assertFalse(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());

		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd2.code, 1, new DummyHandler(), dsptch2);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());

		Command.Handler h = new DummyHandler();
		reg_inviolate.clear();
		mod = Registry.get().registerHandler(fakecmd2.code, 0, h, dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());

		reg_inviolate.clear();
		try {
			Registry.get().registerHandler(fakecmd2.code, 0, h, dsptch1);
			org.junit.Assert.fail("Failed to trap duplicate handler");
		} catch (com.grey.base.ConfigException ex) {}

		String cmdcode = "no-such-command";
		mod = Registry.get().registerHandler(cmdcode, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertFalse(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertNull(reg_handlers.get(cmdcode));

		dsptch1.stop();
		dsptch2.stop();
	}

	private static class DummyHandler implements Command.Handler
	{
		@Override
		public CharSequence handleNAFManCommand(Command cmd) {return null;}
	}
}