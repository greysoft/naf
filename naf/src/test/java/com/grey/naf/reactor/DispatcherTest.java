/*
 * Copyright 2011-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.util.Arrays;

import com.grey.base.config.SysProps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.DynLoader;

public class DispatcherTest
{
	public static final String SYSPROP_SKIPDNS = "greynaf.test.skipdns";

	private static final String rootdir = initPaths(DispatcherTest.class);
	private static final com.grey.logging.Logger bootlog = com.grey.logging.Factory.getLoggerNoEx("");
	private static final int cfgbaseport = 21000; //same as in resources:naf.xml

	// for the sake of any DNS-dependent tests, check if DNS queries are even possible
	public static final boolean HAVE_DNS_SERVICE;
	static {
		String reason = null;
		if (SysProps.get(SYSPROP_SKIPDNS, true)) {
			reason = "Disabled by config: "+SYSPROP_SKIPDNS;
		} else {
			// we could simply look up www.google.com, but this root-nameservers lookup seems a bit more neutral
			System.out.println("Probing for DNS service ..."); //failure can be quite slow
			java.util.Hashtable<String, String> envinput = new java.util.Hashtable<String, String>();
			envinput.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
			javax.naming.directory.DirContext ctx = null;
			try {
				ctx = new javax.naming.directory.InitialDirContext(envinput);
				javax.naming.directory.Attributes attrs = ctx.getAttributes(".", new String[] {"NS"});
				javax.naming.directory.Attribute attr = attrs.get("NS");
				if (attr == null || attr.size() == 0) reason = attrs+" - "+attr;
			} catch (javax.naming.NamingException ex) {
				reason = "probe lookup failed - "+ex;
			} finally {
				try {
					if (ctx != null) ctx.close();
				} catch (javax.naming.NamingException ex) {
					System.out.println("WARNING: Failed to close JNDI context - "+ex);
				}
			}
		}
		HAVE_DNS_SERVICE = (reason == null);
		if (!HAVE_DNS_SERVICE) System.out.println("WARNING: DNS-dependent tests cannot be performed - "+reason);
	}

	public static String initPaths(Class<?> clss)
	{
		String rootpath = SysProps.TMPDIR+"/utest/naf/"+clss.getPackage().getName()+"/"+clss.getSimpleName();
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_ROOT, rootpath);
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_CONF, null);
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_VAR, null);
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_LOGS, null);
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_TMP, null);
		try {
			FileOps.deleteDirectory(rootpath);
		} catch (Exception ex) {
			throw new RuntimeException("DispatcherTest.initPaths failed to remove root="+rootpath+" - "+ex, ex);
		}
		return rootpath;
	}

	// Completion is enough to satisfy these tests
	@org.junit.Test
	public void testConfig() throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		String dname = "testdispatcher1";
		String cfgpath = getResourcePath("/naf.xml", getClass());
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgpath);
		Dispatcher.launchConfigured(nafcfg, bootlog);
		org.junit.Assert.assertEquals(Arrays.asList(Dispatcher.getDispatchers()).toString(), 1, Dispatcher.getDispatchers().length);
		Dispatcher dsptch = Dispatcher.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dsptch);
		org.junit.Assert.assertEquals(dname, dsptch.name);
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertFalse(dsptch.isRunning());
		org.junit.Assert.assertEquals(Arrays.asList(Dispatcher.getDispatchers()).toString(), 0, Dispatcher.getDispatchers().length);
	}

	@org.junit.Test
	public void testNamedConfig() throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		String dname = "testdispatcher1";
		String cfgpath = getResourcePath("/naf.xml", getClass());
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgpath);
		Dispatcher dsptch = Dispatcher.createConfigured(dname, nafcfg, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.name);
		org.junit.Assert.assertFalse(dsptch.isRunning());
		try {
			Dispatcher.createConfigured(dname, nafcfg, bootlog);
			org.junit.Assert.fail("Failed to trap duplicate Dispatcher name");
		} catch (com.grey.base.ConfigException ex) {}
		org.junit.Assert.assertFalse(dsptch.isRunning());
		boolean sts = dsptch.stop();
		org.junit.Assert.assertTrue(sts);
		org.junit.Assert.assertFalse(dsptch.isRunning());

		dsptch = Dispatcher.createConfigured("x"+dname, nafcfg, bootlog);
		org.junit.Assert.assertNull(dsptch);
	}

	@org.junit.Test
	public void testDynamic() throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException
	{
		FileOps.deleteDirectory(rootdir);
		String dname = "utest_dynamic1";
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = dname;
		def.hasDNS = true;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, cfgbaseport, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.name);
		org.junit.Assert.assertEquals(cfgbaseport, dsptch.nafcfg.getPort(0));
		dsptch.start();
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertFalse(dsptch.isRunning());

		//make sure it can be run again
		dsptch = Dispatcher.create(def, cfgbaseport, bootlog);
		org.junit.Assert.assertEquals(dname, dsptch.name);
		org.junit.Assert.assertEquals(cfgbaseport, dsptch.nafcfg.getPort(0));
		dsptch.start();
		org.junit.Assert.assertTrue(dsptch.isRunning());
		dsptch.stop();
		dsptch.waitStopped();
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}

	// NB: The concept of mapping a resource URL to a File is inherently flawed, but this utility works
	// beecause the resources we're looking up are in the same build tree.
	public static String getResourcePath(String path, Class<?> clss) throws java.io.IOException, java.net.URISyntaxException
	{
		java.net.URL url = DynLoader.getResource(path, clss);
		if (url == null) return null;
		return new java.io.File(url.toURI()).getCanonicalPath();
	}
}
