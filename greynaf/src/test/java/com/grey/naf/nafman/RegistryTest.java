/*
 * Copyright 2013-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import java.util.List;
import java.util.Map;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.config.DispatcherConfig;
import com.grey.naf.TestUtils;

public class RegistryTest
{
	private final ApplicationContextNAF appctx = TestUtils.createApplicationContext(null, true, null);
	private final NafManRegistry nafreg = NafManRegistry.get(appctx);
	private final String rootdir = com.grey.naf.TestUtils.initPaths(RegistryTest.class);
	private final NafManRegistry.DefCommand fakecmd1 = new NafManRegistry.DefCommand("fake-cmd-1", "utest", "fake1", null, false);
	private final NafManRegistry.DefCommand fakecmd2 = new NafManRegistry.DefCommand("fake-cmd-2", "utest", "fake2", null, false);
	private final NafManRegistry.DefCommand stopcmd = nafreg.getCommand(NafManRegistry.CMD_STOP);

	public RegistryTest()
		throws InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException,
			IllegalArgumentException, java.lang.reflect.InvocationTargetException, java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
	}

	@org.junit.Test
	public void testResources() throws java.io.IOException
	{
		String homepage = "nafhome";
		org.junit.Assert.assertEquals(homepage, nafreg.getHomePage());
		nafreg.setHomePage(NafManRegistry.RSRC_CMDSTATUS);
		org.junit.Assert.assertEquals(NafManRegistry.RSRC_CMDSTATUS, nafreg.getHomePage());
		try {
			nafreg.setHomePage("no-such-resource");
			org.junit.Assert.fail("Failed to trap invalid home page");
		} catch (NAFConfigException ex) {}
		org.junit.Assert.assertEquals(NafManRegistry.RSRC_CMDSTATUS, nafreg.getHomePage());

		NafManRegistry.DefResource rsrc = new NafManRegistry.DefResource(NafManRegistry.CMD_STOP, "cmdstatus.xsl", null, null);
		try {
			nafreg.loadResources(new NafManRegistry.DefResource[]{rsrc});
			org.junit.Assert.fail("Failed to trap resource that conflicts with commands");
		} catch (NAFConfigException ex) {}

		NafManRegistry.DefCommand cmd = new NafManRegistry.DefCommand(homepage, "utest", "rsrc-conflict", null, false);
		try {
			nafreg.loadCommands(new NafManRegistry.DefCommand[]{cmd});
			org.junit.Assert.fail("Failed to trap command that conflicts with resources");
		} catch (NAFConfigException ex) {}
	}

	@org.junit.Test
	public void testCommands()
	{
		int cnt1 = nafreg.getCommands().size();
		nafreg.loadCommands(new NafManRegistry.DefCommand[]{fakecmd1});
		org.junit.Assert.assertSame(fakecmd1, nafreg.getCommand(fakecmd1.code));
		org.junit.Assert.assertEquals(NafManRegistry.CMD_STOP, stopcmd.code);
		org.junit.Assert.assertNull(appctx.getNamedItem(PrimaryAgent.class.getName(), null));
		org.junit.Assert.assertEquals(cnt1+1, nafreg.getCommands().size());

		// make sure duplicate command defs are discarded
		NafManRegistry.DefCommand stop2 = new NafManRegistry.DefCommand(stopcmd.code, "utest", "dup stop", null, false);
		nafreg.loadCommands(new NafManRegistry.DefCommand[]{stop2});
		NafManRegistry.DefCommand stop2b = nafreg.getCommand(stopcmd.code);
		org.junit.Assert.assertNotEquals("dup stop", stop2b.descr);
		org.junit.Assert.assertEquals(stop2b.descr, stopcmd.descr);
		org.junit.Assert.assertEquals(cnt1+1, nafreg.getCommands().size());
	}

	@org.junit.Test
	public void testHandlerDefs() throws java.io.IOException {
		@SuppressWarnings("unchecked")
		java.util.Set<Dispatcher> reg_inviolate = (java.util.Set<Dispatcher>)DynLoader.getField(nafreg, "inviolateHandlers");
		@SuppressWarnings("unchecked")
		Map<String, List<?>> reg_handlers = (Map<String, List<?>>)DynLoader.getField(nafreg, "commandHandlers");
		com.grey.base.collections.HashedMap<String, java.util.List<NafManCommand.Handler>> dsptch_handlers = new com.grey.base.collections.HashedMap<>();
		DispatcherConfig def = DispatcherConfig.builder().withAppContext(appctx).build();
		Dispatcher dsptch1 = Dispatcher.create(def);
		def = def.mutate().withName(null).build();
		Dispatcher dsptch2 = Dispatcher.create(def);
		nafreg.loadCommands(new NafManRegistry.DefCommand[]{fakecmd1});
		nafreg.loadCommands(new NafManRegistry.DefCommand[]{fakecmd2});
		reg_handlers.clear();

		org.junit.Assert.assertFalse(nafreg.isCommandRegistered(fakecmd1.code));
		org.junit.Assert.assertFalse(nafreg.isCommandRegistered(fakecmd1.code, dsptch1));
		dsptch_handlers.clear();
		nafreg.getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(0, dsptch_handlers.size());

		reg_inviolate.clear();
		org.junit.Assert.assertEquals(0, reg_handlers.size());
		boolean mod = nafreg.registerHandler(fakecmd1.code, 12, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());
		org.junit.Assert.assertTrue(nafreg.isCommandRegistered(fakecmd1.code));
		org.junit.Assert.assertTrue(nafreg.isCommandRegistered(fakecmd1.code, dsptch1));
		org.junit.Assert.assertFalse(nafreg.isCommandRegistered(fakecmd1.code, dsptch2));
		dsptch_handlers.clear();
		nafreg.getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(1, dsptch_handlers.get(fakecmd1.code).size());
		mod = nafreg.registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertFalse("Failed to trap changes to involate Dispatcher", mod);

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd1.code, 11, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd1.code, 13, new DummyHandler(), dsptch1);
		org.junit.Assert.assertFalse(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd1.code).size());
		dsptch_handlers.clear();
		nafreg.getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(1, dsptch_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(2, reg_handlers.get(fakecmd1.code).size());
		dsptch_handlers.clear();
		nafreg.getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(2, dsptch_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd1.code, 0, new DummyHandler(), dsptch2);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(1, reg_handlers.size());
		org.junit.Assert.assertEquals(3, reg_handlers.get(fakecmd1.code).size());
		dsptch_handlers.clear();
		nafreg.getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(1, dsptch_handlers.size());
		org.junit.Assert.assertEquals(2, dsptch_handlers.get(fakecmd1.code).size());

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd2.code, 2, new DummyHandler(), dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(3, reg_handlers.get(fakecmd1.code).size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());
		dsptch_handlers.clear();
		nafreg.getHandlers(dsptch1, dsptch_handlers);
		org.junit.Assert.assertEquals(2, dsptch_handlers.size());
		org.junit.Assert.assertEquals(2, dsptch_handlers.get(fakecmd1.code).size());
		org.junit.Assert.assertEquals(1, dsptch_handlers.get(fakecmd2.code).size());

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd2.code, 3, new DummyHandler(), dsptch2);
		org.junit.Assert.assertFalse(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());

		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd2.code, 1, new DummyHandler(), dsptch2);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());

		NafManCommand.Handler h = new DummyHandler();
		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd2.code, 0, h, dsptch1);
		org.junit.Assert.assertTrue(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertEquals(1, reg_handlers.get(fakecmd2.code).size());

		// ensure that duplicate hander is skipped wothout erroring
		reg_inviolate.clear();
		mod = nafreg.registerHandler(fakecmd2.code, 0, h, dsptch1);
		org.junit.Assert.assertFalse(mod);

		String cmdcode = "no-such-command";
		mod = nafreg.registerHandler(cmdcode, 0, new DummyHandler(), dsptch1);
		org.junit.Assert.assertFalse(mod);
		org.junit.Assert.assertEquals(2, reg_handlers.size());
		org.junit.Assert.assertNull(reg_handlers.get(cmdcode));
	}

	private static class DummyHandler implements NafManCommand.Handler
	{
		DummyHandler() {} //make explicit with non-private access, to eliminate synthetic accessor
		@Override
		public CharSequence handleNAFManCommand(NafManCommand cmd) {return null;}
		@Override
		public CharSequence nafmanHandlerID() {return "Dummy";}
	}
}