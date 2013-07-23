/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.utils.DynLoader;
import com.grey.base.config.XmlConfig;

public class ConfigTest
{
	static {
		com.grey.naf.reactor.DispatcherTest.initPaths(ConfigTest.class);
	}

	@org.junit.Test
	public void testSynthesise() throws com.grey.base.ConfigException, java.io.IOException
	{
		String cfgxml = "<naf>"
				+"<baseport>68000</baseport>"
				+"<nafman>blah</nafman>"
				+"<dnsresolver>blah</dnsresolver>"
				+"<dispatchers>"
					+"<dispatcher name=\"testdispatcher9\" nafman=\"N\" dns=\"y\">"
						+"<naflets>"
							+"<naflet name=\"app1\">blah</naflet>"
							+"<naflet name=\"app2\">blah</naflet>"
						+"</naflets>"
					+"</dispatcher>"
				+"</dispatchers>"
			+"</naf>";
		Config cfg = Config.synthesise(cfgxml);
		org.junit.Assert.assertEquals(68000, cfg.getPort(Config.RSVPORT_NAFMAN));
		org.junit.Assert.assertTrue(cfg.getNafman().exists());
		org.junit.Assert.assertTrue(cfg.getDNS().exists());

		String dname = "testdispatcher9";
		XmlConfig dcfg = cfg.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dcfg);
		DispatcherDef def = new DispatcherDef(dcfg);
		org.junit.Assert.assertEquals(dname, def.name);
		org.junit.Assert.assertFalse(def.hasNafman);
		org.junit.Assert.assertTrue(def.hasDNS);
		org.junit.Assert.assertTrue(def.zeroNafletsOK);
		org.junit.Assert.assertTrue(def.surviveDownstream);
		org.junit.Assert.assertTrue(def.surviveHandlers);
		org.junit.Assert.assertEquals(2, def.naflets.length);
	}

	@org.junit.Test
	public void testLoad() throws com.grey.base.ConfigException, java.io.IOException, java.net.URISyntaxException
	{
		String path = getResourcePath("/naf.xml", ConfigTest.class);
		String dname = "testdispatcher1";

		Config cfg = Config.load(path);
		org.junit.Assert.assertEquals(21000, cfg.getPort(Config.RSVPORT_NAFMAN));
		org.junit.Assert.assertFalse(cfg.getNafman().exists());
		org.junit.Assert.assertFalse(cfg.getDNS().exists());
		XmlConfig dcfg = cfg.getDispatcher("no-such-dispatcher");
		org.junit.Assert.assertNull(dcfg);

		dcfg = cfg.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dcfg);
		DispatcherDef def = new DispatcherDef(dcfg);
		verifyConfig(def, dname);
	}

	private static void verifyConfig(DispatcherDef def, String dname)
	{
		org.junit.Assert.assertEquals(dname, def.name);
		org.junit.Assert.assertTrue(def.hasNafman);
		org.junit.Assert.assertFalse(def.hasDNS);
		org.junit.Assert.assertTrue(def.zeroNafletsOK);
		org.junit.Assert.assertTrue(def.surviveDownstream);
		org.junit.Assert.assertFalse(def.surviveHandlers);
		org.junit.Assert.assertNull(def.naflets);
	}

	// NB: The concept of mapping a resource URL to a File is inherently flawed, but this utility works
	// beecause the resources we're looking up are in the same build tree.
	private static String getResourcePath(String path, Class<?> clss) throws java.io.IOException, java.net.URISyntaxException
	{
		java.net.URL url = DynLoader.getResource(path, clss);
		if (url == null) return null;
		return new java.io.File(url.toURI()).getCanonicalPath();
	}
}