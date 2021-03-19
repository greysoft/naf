/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.XmlConfig;

public class ConfigTest
{
	static {
		com.grey.naf.TestUtils.initPaths(ConfigTest.class);
	}

	@org.junit.Test
	public void testSynthesise() throws java.io.IOException
	{
		String cfgxml = "<naf>"
				+"<baseport>"+NAFConfig.RSVPORT_ANON+"</baseport>"
				+"<dispatchers>"
					+"<dispatcher name=\"testdispatcher9\">"
						+"<naflets>"
							+"<naflet name=\"app1\">blah</naflet>"
							+"<naflet name=\"app2\">blah</naflet>"
						+"</naflets>"
					+"</dispatcher>"
				+"</dispatchers>"
			+"</naf>";
		XmlConfig xmlcfg = XmlConfig.makeSection(cfgxml, "/naf");
		NAFConfig cfg = new NAFConfig.Builder().withXmlConfig(xmlcfg).build();
		org.junit.Assert.assertTrue(cfg.isAnonymousBasePort());

		String dname = "testdispatcher9";
		XmlConfig dcfg = cfg.getDispatcher(dname);
		XmlConfig[] nafletsConfig = dcfg.getSections("naflets/naflet"+XmlConfig.XPATH_ENABLED);
		org.junit.Assert.assertNotNull(dcfg);
		DispatcherDef def = new DispatcherDef.Builder().withXmlConfig(dcfg).build();
		org.junit.Assert.assertEquals(dname, def.getName());
		org.junit.Assert.assertTrue(def.isSurviveHandlers());
		org.junit.Assert.assertEquals(2, nafletsConfig.length);
	}

	@org.junit.Test
	public void testLoad() throws java.io.IOException, java.net.URISyntaxException
	{
		String path = TestUtils.getResourcePath("/naf.xml", ConfigTest.class);
		String dname = "testdispatcher1";

		NAFConfig cfg = new NAFConfig.Builder().withConfigFile(path).build();
		org.junit.Assert.assertTrue(cfg.isAnonymousBasePort());
		XmlConfig dcfg = cfg.getDispatcher("no-such-dispatcher");
		org.junit.Assert.assertNull(dcfg);

		dcfg = cfg.getDispatcher(dname);
		org.junit.Assert.assertNotNull(dcfg);
		DispatcherDef def = new DispatcherDef.Builder().withXmlConfig(dcfg).build();
		verifyConfig(def, dname);
	}

	private static void verifyConfig(DispatcherDef def, String dname)
	{
		org.junit.Assert.assertEquals(dname, def.getName());
		org.junit.Assert.assertFalse(def.isSurviveHandlers());
	}
}
