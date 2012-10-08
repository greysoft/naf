/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.config;

import java.io.IOException;

import com.grey.base.ConfigException;

public class XmlConfigTest
{
	private static final String tag_minimal1 = "min1";
	private static final String tag_minimal2 = "min2";
	private static final String cfgxml_minimal1 = "<"+tag_minimal1+"></"+tag_minimal1+">";
	private static final String cfgxml_minimal2 = "<"+tag_minimal2+"/>";

	private static final String cfgxml =
		"<root>"
			+"<tag1 attr1=\" attrval1 \"> tagval1 </tag1>"
			+"<tag2>10</tag2>"
			+"<tag3>N</tag3>"
			+"<tag14>"+XmlConfig.NULLMARKER+"</tag14>"
			+"<blanktag></blanktag>"
			+"<innernode>"
				+"<tag1 attr1=\"attrval21\">tagval21</tag1>"
			+"</innernode>"
			+"<list1>"
				+"<innernodes>"
					+"<tag1 attr1=\"attrval31\">tagval31</tag1>"
				+"</innernodes>"
				+"<innernodes>"
					+"<tag1 attr1=\"attrval41\">tagval41</tag1>"
				+"</innernodes>"
			+"</list1>"
			+"<tuple1> val1 | val2 | val3 </tuple1>"
			+"<tuple2>|val1|</tuple2>"
			+"<tuple3></tuple3>"
			+"<tuple4>|</tuple4>"
			+"<tuple5> | | </tuple5>"
			+"<time1>2m</time1>"
			+"<size1>2K</size1>"
			+cfgxml_minimal1
			+cfgxml_minimal2
		+"</root>";

	private static final String cfgdflts1 = 
		"<root1>"
			+"<tag1>tagval91</tag1>"
			+"<tag11>tagval11a</tag11>"
			+"<tag13>-</tag13>"
			+"<tag14>tagval14a</tag14>"
			+"<list1>"
				+"<innernodes2>"
					+"<tag1 attr1=\"attrval31\">tagval31</tag1>"
				+"</innernodes2>"
			+"</list1>"
		+"</root1>";

	private static final String cfgdflts2 = 
		"<root2>"
			+"<tag1>tagval91</tag1>"
			+"<tag11>tagval11b</tag11>"
			+"<tag12>tagval12b</tag12>"
			+"<tag13>tagval13b</tag13>"
		+"</root2>";

	// Just do one simple test to establish that we've been able to parse a valid config block from a file.
	// Once read in, it's the same as one created from the literal string above, and we do all the other tests on that.
	@org.junit.Test
	public void testFile() throws IOException, ConfigException
	{
		java.io.File fh = new java.io.File("temptest_xmlconfig.xml");
		String pthnam = fh.getAbsolutePath();
		com.grey.base.utils.FileOps.writeTextFile(fh, cfgxml, false);

		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.getSection(pthnam, "/root");
		org.junit.Assert.assertTrue(cfg.exists());
		String strval = cfg.getValue("tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));

		cfg = com.grey.base.config.XmlConfig.getSection(pthnam, "/badroot");
		org.junit.Assert.assertFalse(cfg.exists());
		if (!fh.delete()) throw new java.io.IOException("Failed to delete tempfile - "+fh.getAbsolutePath());
	}

	@org.junit.Test
	public void testPrimitives() throws ConfigException
	{
		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/root");
		org.junit.Assert.assertTrue(cfg.exists());

		String strval = cfg.getValue("tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));
		strval = cfg.getValue("tag1", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));
		strval = cfg.getValue("tag11", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("tag11", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("blanktag", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("blanktag", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		int intval = cfg.getInt("tag2", true, 20);
		org.junit.Assert.assertEquals(10, intval);
		intval = cfg.getInt("tag2", false, 20);
		org.junit.Assert.assertEquals(10, intval);
		intval = cfg.getInt("tag12", false, 20);
		org.junit.Assert.assertEquals(20, intval);
		intval = cfg.getInt("tag12", true, 20);
		org.junit.Assert.assertEquals(20, intval);

		boolean boolval = cfg.getBool("tag3", true);
		org.junit.Assert.assertFalse(boolval);
		boolval = cfg.getBool("tag3", false);
		org.junit.Assert.assertFalse(boolval);
		boolval = cfg.getBool("tag13", true);
		org.junit.Assert.assertTrue(boolval);
		boolval = cfg.getBool("tag13", false);
		org.junit.Assert.assertFalse(boolval);

		try {
			cfg.getValue("tag11", true, null);
			org.junit.Assert.fail("Absent mandatory string not detected");
		} catch (ConfigException ex) {}

		try {
			cfg.getInt("tag12", true, 0);
			org.junit.Assert.fail("Absent mandatory int not detected");
		} catch (ConfigException ex) {}
	}

	@org.junit.Test
	public void testAbsentConfig()throws ConfigException
	{
		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/badroot");
		org.junit.Assert.assertFalse(cfg.exists());

		String strval = cfg.getValue("tag11", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("tag11", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("tag11", false, null);
		org.junit.Assert.assertNull(strval);
		strval = cfg.getValue("tag11", false, "");
		org.junit.Assert.assertTrue(strval.isEmpty());

		int intval = cfg.getInt("tag12", false, 20);
		org.junit.Assert.assertEquals(20, intval);
		intval = cfg.getInt("tag12", true, 20);
		org.junit.Assert.assertEquals(20, intval);

		boolean boolval = cfg.getBool("tag13", true);
		org.junit.Assert.assertTrue(boolval);
		boolval = cfg.getBool("tag13", false);
		org.junit.Assert.assertFalse(boolval);

		try {
			cfg.getValue("tag11", true, null);
			org.junit.Assert.fail("Absent mandatory string not detected");
		} catch (com.grey.base.ConfigException ex) {}

		try {
			cfg.getValue("tag11", true, "");
			org.junit.Assert.fail("Blank mandatory string not detected");
		} catch (com.grey.base.ConfigException ex) {}

		try {
			cfg.getInt("tag12", true, 0);
			org.junit.Assert.fail("Absent mandatory int not detected");
		} catch (com.grey.base.ConfigException ex) {}
	}

	@org.junit.Test
	public void testDefaultBlocks() throws ConfigException
	{
		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/root");
		String strval = cfg.getValue("tag11", false, null);
		org.junit.Assert.assertNull(strval);

		com.grey.base.config.XmlConfig dflts1 = com.grey.base.config.XmlConfig.makeSection(cfgdflts1, "/root1");
		com.grey.base.config.XmlConfig dflts2 = com.grey.base.config.XmlConfig.makeSection(cfgdflts2, "/root2");
		dflts1.setDefaults(dflts2);
		cfg.setDefaults(dflts1);

		strval = cfg.getValue("tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));
		strval = cfg.getValue("tag11", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval11a"));
		strval = cfg.getValue("tag12", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval12b"));
		strval = cfg.getValue("tag13", false, "dflt1");
		org.junit.Assert.assertNull(strval);
		strval = cfg.getValue("tag14", false, "dflt1");
		org.junit.Assert.assertNull(strval);
		strval = cfg.getValue("tag15", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		com.grey.base.config.XmlConfig[] arrcfg = cfg.subSections("list1/innernodes2");
		org.junit.Assert.assertEquals(1, arrcfg.length);

	}

	@org.junit.Test
	public void testNested() throws ConfigException
	{
		com.grey.base.config.XmlConfig maincfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/root");

		com.grey.base.config.XmlConfig cfg = new com.grey.base.config.XmlConfig(maincfg, "innernode");
		org.junit.Assert.assertTrue(cfg.exists());
		String strval = cfg.getValue("tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval21"));

		cfg = new com.grey.base.config.XmlConfig(maincfg, "MissingBlock");
		org.junit.Assert.assertFalse(cfg.exists());
		cfg = new com.grey.base.config.XmlConfig(cfg, "MissingBlock2");
		org.junit.Assert.assertFalse(cfg.exists());

		com.grey.base.config.XmlConfig[] arrcfg = maincfg.subSections("list1/innernodes");
		org.junit.Assert.assertEquals(2, arrcfg.length);
		strval = arrcfg[0].getValue("tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval31"));
		strval = arrcfg[1].getValue("tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval41"));

		arrcfg = maincfg.subSections("list1/innernodes2");
		org.junit.Assert.assertNull(arrcfg);

		arrcfg = maincfg.subSections("list1");
		org.junit.Assert.assertEquals(1, arrcfg.length);
		arrcfg = maincfg.subSections("tag2");
		org.junit.Assert.assertEquals(1, arrcfg.length);

		arrcfg = XmlConfig.BLANKCFG.subSections("list1");
		org.junit.Assert.assertNull(arrcfg);
		arrcfg = XmlConfig.NULLCFG.subSections("list1");
		org.junit.Assert.assertNull(arrcfg);
	}

	@org.junit.Test
	public void testTuples() throws ConfigException
	{
		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/root");

		String[] str = cfg.getTuple("tuple1", "|", true, null);
		org.junit.Assert.assertEquals(3, str.length);
		org.junit.Assert.assertTrue(str[0].equals("val1"));
		org.junit.Assert.assertTrue(str[1].equals("val2"));
		org.junit.Assert.assertTrue(str[2].equals("val3"));

		str = cfg.getTuple("tuple2", "|", true, null);
		org.junit.Assert.assertEquals(1, str.length);
		org.junit.Assert.assertTrue(str[0].equals("val1"));

		str = cfg.getTuple("tuple3", "|", false, null);
		org.junit.Assert.assertNull(str);
		str = cfg.getTuple("tuple4", "|", false, null);
		org.junit.Assert.assertNull(str);
		str = cfg.getTuple("tuple5", "|", false, null);
		org.junit.Assert.assertNull(str);

		str = cfg.getTuple("tuple1", "|", true, null, 3, 3);
		org.junit.Assert.assertEquals(3, str.length);
		str = cfg.getTuple("tuple1", "|", true, null, 2, 4);
		org.junit.Assert.assertEquals(3, str.length);

		try {
			cfg.getTuple("tuple1", "|", true, null, 4, 3);
			org.junit.Assert.fail("Failed to detect min-tuple violations");
		} catch (com.grey.base.ConfigException ex) {}

		try {
			cfg.getTuple("tuple1", "|", true, null, 3, 2);
			org.junit.Assert.fail("Failed to detect max-tuple violations");
		} catch (com.grey.base.ConfigException ex) {}

		try {
			cfg.getTuple("tuple3", "|", true, null);
			org.junit.Assert.fail("Absent mandatory tuple not detected");
		} catch (com.grey.base.ConfigException ex) {}

		try {
			cfg.getTuple("tuple4", "|", true, null);
			org.junit.Assert.fail("Blank mandatory tuple not detected");
		} catch (com.grey.base.ConfigException ex) {}

		str = cfg.getTuple("tuple1", ";", true, null);
		org.junit.Assert.assertEquals(1, str.length);  // would have gotten back entire element content as one string

		str = cfg.getTuple("tuple3", ";", true, " dflt1 ; dflt2 ;");
		org.junit.Assert.assertEquals(2, str.length);
		org.junit.Assert.assertTrue(str[0].equals("dflt1"));
		org.junit.Assert.assertTrue(str[1].equals("dflt2"));
	}

	// NB: We're not testing the Time and Size parsers here, we're merely verifying that XmlConfig does call them correctly
	@org.junit.Test
	public void testTypedData() throws ConfigException
	{
		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/root");

		long msecs = cfg.getTime("time1", "3h");
		org.junit.Assert.assertEquals(com.grey.base.utils.TimeOps.MSECS_PER_MINUTE * 2, msecs);
		msecs = cfg.getTime("time2", "3h");
		org.junit.Assert.assertEquals(com.grey.base.utils.TimeOps.MSECS_PER_HOUR * 3, msecs);

		long nbytes = cfg.getSize("size1", "3M");
		org.junit.Assert.assertEquals(com.grey.base.utils.ByteOps.KILO * 2, nbytes);
		nbytes = cfg.getSize("size2", "3M");
		org.junit.Assert.assertEquals(com.grey.base.utils.ByteOps.MEGA * 3, nbytes);
	}

	@org.junit.Test
	public void testSpecialCases() throws ConfigException
	{
		com.grey.base.config.XmlConfig maincfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/root");

		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml_minimal1, "/");
		org.junit.Assert.assertTrue(cfg.exists());

		cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml_minimal2, "/");
		org.junit.Assert.assertTrue(cfg.exists());

		cfg = new com.grey.base.config.XmlConfig(maincfg, tag_minimal1);
		org.junit.Assert.assertTrue(cfg.exists());
		cfg = new com.grey.base.config.XmlConfig(maincfg, tag_minimal2);
		org.junit.Assert.assertTrue(cfg.exists());

		try {
			com.grey.base.config.XmlConfig.makeSection(cfgxml, "");
			org.junit.Assert.fail("Illegal blank XPath not detected");
		} catch (com.grey.base.ConfigException ex) {}

		try {
			com.grey.base.config.XmlConfig.makeSection(cfgxml, null);
			org.junit.Assert.fail("Illegal null XPath not detected");
		} catch (Exception ex) {}

		org.junit.Assert.assertTrue(XmlConfig.BLANKCFG.exists());
		String strval = XmlConfig.BLANKCFG.getValue("tag1", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = XmlConfig.BLANKCFG.getValue("x", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		org.junit.Assert.assertFalse(XmlConfig.NULLCFG.exists());
		strval = XmlConfig.NULLCFG.getValue("tag1", false, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		try {
			// beware the the XML parser writes an error messages to stdout when this fails
			com.grey.base.config.XmlConfig.makeSection("<root><tag1>x</tag2></root>", "");
			org.junit.Assert.fail("Illegal XML not detected");
		} catch (com.grey.base.ConfigException ex)  {}
	}

	// Assuming our defaults-handling works as expected (that is verified by testPrimitives), this verifies our understanding of
	// how XPath expressions will be interpreted.
	@org.junit.Test
	public void testXPath() throws ConfigException
	{
		com.grey.base.config.XmlConfig cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/");
		String strval = cfg.getValue("/root/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));
		strval = cfg.getValue("root/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));
		strval = cfg.getValue("//tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));
		strval = cfg.getValue("/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		strval = cfg.getValue("/root/tag1/@attr1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("attrval1"));
		strval = cfg.getValue("/root/tag1/@attr2", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		strval = cfg.getValue("/root/list1/innernodes[1]/tag1", true, "dflt1");  // yep, nodes are not indexed from zero!
		org.junit.Assert.assertTrue(strval.equals("tagval31"));
		strval = cfg.getValue("/root/list1/innernodes[1]/tag1/@attr1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("attrval31"));
		strval = cfg.getValue("/root/list1/innernodes[2]/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval41"));
		strval = cfg.getValue("/root/list1/innernodes[2]/tag1/@attr1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("attrval41"));
		strval = cfg.getValue("/root/list1/innernodes[0]/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("/root/list1/innernodes[3]/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		strval = cfg.getValue("/root/list1/innernodes/tag1[@attr1='attrval31']", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval31"));
		strval = cfg.getValue("/root/list1/innernodes/tag1[@attr1='attrval99']", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("/root/list1/innernodes/tag1[@attr99='attrval99']", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
		strval = cfg.getValue("/root/list1/innernodes[@attr1='attrval31']/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));

		cfg = com.grey.base.config.XmlConfig.makeSection(cfgxml, "/root");
		strval = cfg.getValue("tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("tagval1"));
		strval = cfg.getValue("/tag1", true, "dflt1");
		org.junit.Assert.assertTrue(strval.equals("dflt1"));
	}
}
