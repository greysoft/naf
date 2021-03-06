/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.XmlConfig;
import com.grey.naf.reactor.DispatcherTest;

public class ConfigTest
{
	static {
		com.grey.naf.reactor.DispatcherTest.initPaths(ConfigTest.class);
	}

	@org.junit.Test
	public void testSynthesise() throws java.io.IOException
	{
		String cfgxml = "<naf>"
				+"<baseport>"+NAFConfig.RSVPORT_ANON+"</baseport>"
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
		NAFConfig cfg = NAFConfig.synthesise(cfgxml);
		org.junit.Assert.assertTrue(cfg.isAnonymousBasePort());
		org.junit.Assert.assertTrue(cfg.getNafman().exists());
		org.junit.Assert.assertTrue(cfg.getDNS().exists());

		String dname = "testdispatcher9";
		XmlConfig dcfg = cfg.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dcfg);
		DispatcherDef def = new DispatcherDef.Builder(dcfg).build();
		org.junit.Assert.assertEquals(dname, def.getName());
		org.junit.Assert.assertFalse(def.hasNafman());
		org.junit.Assert.assertTrue(def.hasDNS());
		org.junit.Assert.assertTrue(def.isZeroNafletsOK());
		org.junit.Assert.assertTrue(def.isSurviveHandlers());
		org.junit.Assert.assertEquals(2, def.getNafletsConfig().length);
	}

	@org.junit.Test
	public void testLoad() throws java.io.IOException, java.net.URISyntaxException
	{
		String path = DispatcherTest.getResourcePath("/naf.xml", ConfigTest.class);
		String dname = "testdispatcher1";

		NAFConfig cfg = NAFConfig.load(path);
		org.junit.Assert.assertTrue(cfg.isAnonymousBasePort());
		org.junit.Assert.assertFalse(cfg.getNafman().exists());
		org.junit.Assert.assertFalse(cfg.getDNS().exists());
		XmlConfig dcfg = cfg.getDispatcher("no-such-dispatcher");
		org.junit.Assert.assertNull(dcfg);

		dcfg = cfg.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dcfg);
		DispatcherDef def = new DispatcherDef.Builder(dcfg).build();
		verifyConfig(def, dname);
	}

	private static void verifyConfig(DispatcherDef def, String dname)
	{
		org.junit.Assert.assertEquals(dname, def.getName());
		org.junit.Assert.assertTrue(def.hasNafman());
		org.junit.Assert.assertFalse(def.hasDNS());
		org.junit.Assert.assertTrue(def.isZeroNafletsOK());
		org.junit.Assert.assertFalse(def.isSurviveHandlers());
		org.junit.Assert.assertNull(def.getNafletsConfig());
	}
}