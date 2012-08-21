/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.config;

/**This class treats an XML file as a structured config file.
 * <br/>
 * Calling applications just see this as a config structure, so to spare them from the underlying XML intricacies, we
 * map all exceptions to ConfigException
 * Note that we can retrieve attributes and values at the top-level node of an XmlConfig element using XPath dot notation
 * Eg. "./@attrname" or "." for Text
 */

public class XmlConfig
{
	public static final String XPATH_ENABLED = "[@enabled='Y' or not(@enabled)]";
	public static final String NULLMARKER = "-";  // gets translated to null, and prevents us traversing the chain of defaults

	private static final boolean trace_stdout = SysProps.get("grey.config.trace", false);
	private static final String XPATH_SEP = "/";
	private static final String SECT_SEP = "::";
	private static final String ELEM_SEP = "##";

	public static final XmlConfig NULLCFG = new XmlConfig();  // exists() returns False
	private static XmlConfig _blank_cfg;
	static {
		try {
			_blank_cfg = makeSection("<x/>", XPATH_SEP+"x");
		} catch (Exception ex) {
			throw new RuntimeException("XmlConfig failed to initiaise", ex);
		}
	}
	public static final XmlConfig BLANKCFG = _blank_cfg;  // exists() returns True

	private final javax.xml.xpath.XPath xpathproc;
	private org.w3c.dom.Node cfgsect;
	private XmlConfig cfgDefaults;
	private String label;

	public boolean exists() {return (cfgsect != null);}

	public static XmlConfig getSection(CharSequence pthnam, String node_xpath) throws com.grey.base.ConfigException
	{
		org.w3c.dom.Document xmldoc = null;
		try {
			xmldoc = com.grey.base.utils.XML.getDOM(pthnam);
		} catch (Exception ex) {
			throw new com.grey.base.ConfigException(ex, "Failed to build DOM for config-file="+pthnam);
		}
		return getSection(xmldoc, node_xpath);
	}

	private static XmlConfig getSection(org.w3c.dom.Document xmldoc, String node_xpath) throws com.grey.base.ConfigException
	{
		javax.xml.xpath.XPath xpathproc = com.grey.base.utils.XML.getXpathProcessor();
		return new XmlConfig(xpathproc, xmldoc, node_xpath);
	}

	public static XmlConfig makeSection(CharSequence xmltxt, String node_xpath) throws com.grey.base.ConfigException
	{
		org.w3c.dom.Document xmldoc = null;
		try {
			xmldoc = com.grey.base.utils.XML.makeDOM(xmltxt);
		} catch (Exception ex) {
			throw new com.grey.base.ConfigException(ex, "Failed to build DOM for config-section ["+xmltxt+"] - "+com.grey.base.GreyException.summary(ex));
		}
		return getSection(xmldoc, node_xpath);
	}


	public XmlConfig(XmlConfig cfg, String node_xpath) throws com.grey.base.ConfigException
	{
		xpathproc = cfg.xpathproc;
		label = cfg.label;
		setup(cfg.cfgsect, node_xpath);
	}

	// Only used for NULLCFG
	private XmlConfig()
	{
		xpathproc = com.grey.base.utils.XML.getXpathProcessor();
		try {
			setup(null, "");
		} catch (Exception ex) {
			throw new IllegalArgumentException("Mapping exception to unchecked", ex);
		}
	}

	// NB: node_xpath is the associated XPath of 'node', but 'node' has already been looked up, so node_xpath is merely used to annotate the
	// descriptive label
	private XmlConfig(XmlConfig cfg, org.w3c.dom.Node node, String node_xpath) throws com.grey.base.ConfigException
	{
		xpathproc = cfg.xpathproc;
		label = cfg.label;
		cfgsect = node;
		setup(null, node_xpath);
	}

	// We could make sure that parentNode is of type Document or Node but we gain nothing by that compared to waiting for JAXP do the rejection,
	// and doing our own pre-vetting might produce false positives.
	private XmlConfig(javax.xml.xpath.XPath xpathproc_p, Object parentNode, String node_xpath) throws com.grey.base.ConfigException
	{
		xpathproc = xpathproc_p;
		setup(parentNode, node_xpath);
	}

	public void setDefaults(XmlConfig dflts)
	{
		cfgDefaults = dflts;
	}

	// Null parentNode means cfgsect has already been looked up (or else not required)
	private void setup(Object parentNode, String node_xpath) throws com.grey.base.ConfigException
	{
		if (parentNode != null)
		{
			// The evaluate() method returns null if the node specified by the XPath expression does not exist (which is
			// exactly what we want) and only throws if the Xpath syntax is valid or some other irrecoverable bug in our code/
			try {
				cfgsect = (org.w3c.dom.Node)xpathproc.evaluate(node_xpath, parentNode, javax.xml.xpath.XPathConstants.NODE);
			} catch (Exception ex) {
				throw new com.grey.base.ConfigException(ex, "XML-Config: evaluate() failed on XPath="+node_xpath);
			}
		}

		if (label == null)
		{
			label = "";
		}
		else
		{
			label += SECT_SEP;
		}
		label += node_xpath;
		if (trace_stdout) System.out.println("Config section [" + label + "] " + (cfgsect==null?"absent":"present"));
	}

	public XmlConfig[] subSections(String xpath) throws com.grey.base.ConfigException
	{
		org.w3c.dom.NodeList nodes = null;
		if (cfgsect != null)
		{
			try {
				nodes = (org.w3c.dom.NodeList)xpathproc.evaluate(xpath, cfgsect, javax.xml.xpath.XPathConstants.NODESET);
			} catch (Exception ex) {
				throw new com.grey.base.ConfigException(ex, "XML-Config: evaluate() failed on XPath="+xpath);
			}
		}

		if (nodes == null || nodes.getLength() == 0)
		{
			if (cfgDefaults != null) return cfgDefaults.subSections(xpath);
			return null;
		}
		XmlConfig[] sects = new  XmlConfig[nodes.getLength()];
		
		for (int idx = 0; idx != sects.length; idx++)
		{
			sects[idx] = new XmlConfig(this, nodes.item(idx), xpath+"["+idx+"]");
		}
		return sects;
	}
	
	public String getValue(String xpath, boolean mdty, String dflt) throws com.grey.base.ConfigException
	{
		if (cfgDefaults != null) dflt = cfgDefaults.getValue(xpath, false, dflt);
		String cfgval = getValue(cfgsect, xpath, mdty, dflt);
		if (cfgval != null) cfgval = cfgval.trim().intern();
		if (trace_stdout) System.out.println("Config item [" + label + ELEM_SEP + xpath + " = " + cfgval + "]");
		return cfgval;
	}

	// if mdty is true, then dflt=0 indicates the absence of a default
	public int getInt(String xpath, boolean mdty, int dflt) throws com.grey.base.ConfigException
	{
		String str = getValue(xpath, mdty, (mdty && dflt == 0) ? null : String.valueOf(dflt));
		return Integer.parseInt(str);
	}
	
	public boolean getBool(String xpath, boolean dflt) throws com.grey.base.ConfigException
	{
		String str = getValue(xpath, false, com.grey.base.utils.StringOps.boolAsString(dflt));
		return com.grey.base.utils.StringOps.stringAsBool(str);
	}

	public long getTime(String xpath, String dflt) throws com.grey.base.ConfigException
	{
		return getTime(xpath, com.grey.base.utils.TimeOps.parseMilliTime(dflt));
	}

	public long getTime(String xpath, long dflt) throws com.grey.base.ConfigException
	{
		String str = getValue(xpath, false, Long.toString(dflt));
		return com.grey.base.utils.TimeOps.parseMilliTime(str);
	}

	public long getSize(String xpath, String dflt) throws com.grey.base.ConfigException
	{
		return getSize(xpath, com.grey.base.utils.ByteOps.parseByteSize(dflt));
	}

	public long getSize(String xpath, long dflt) throws com.grey.base.ConfigException
	{
		String str = getValue(xpath, false, Long.toString(dflt));
		return com.grey.base.utils.ByteOps.parseByteSize(str);
	}

	public String[] getTuple(String xpath, String dlm, boolean mdty, String dflt, int min, int max) throws com.grey.base.ConfigException
	{
		String[] arr = null;
		String str = getValue(xpath, mdty, dflt);
		int pos;

		if (str != null)
		{
			// strip leading and trailing delimiters
			if ((pos = str.lastIndexOf(dlm)) != -1)
			{
				if (str.length() - pos == dlm.length()) str = str.substring(0, pos).trim();
			}
			if (str.indexOf(dlm) == 0) str = str.substring(dlm.length()).trim();
			if (dlm.equals("|")) dlm = "\\|";  // commonly used separator, so prevent regex interpreting it as a special character
			arr = str.split(dlm);
			
			for (int idx = 0; idx != arr.length; idx++)
			{
				arr[idx] = arr[idx].trim().intern();
			}

			// split() always returns at least one element, so make sure there was something
			if (arr.length == 1 && arr[0].length() == 0)
			{
				if (mdty) configError(xpath, "Missing mandatory tuple");
				arr = null;
			}
			else
			{
				if (min != 0 && arr.length < min) configError(xpath, "Insufficient tuple elements (" + arr.length + " vs " + min + ")");
				if (max != 0 && arr.length > max) configError(xpath, "Excess tuple elements (" + arr.length + " vs " + max + ")");
			}
		}
		return arr;
	}
	
	public String[] getTuple(String xpath, String dlm, boolean mdty, String dflt) throws com.grey.base.ConfigException
	{
		return getTuple(xpath, dlm, mdty, dflt, 0, 0);
	}
	
	private String getValue(Object cfg, String xpath, boolean mdty, String dflt) throws com.grey.base.ConfigException
	{
		org.w3c.dom.Node elem = null;
		String cfgval = null;

		if (cfg != null)
		{
			try {
				elem = (org.w3c.dom.Node)xpathproc.evaluate(xpath, cfg, javax.xml.xpath.XPathConstants.NODE);
			} catch (Exception ex) {
				throw new com.grey.base.ConfigException(ex, "XML-Config: evaluate() failed on XPath="+xpath);
			}
		}

		if (elem != null)
		{
			cfgval = elem.getTextContent();
			if (cfgval != null) cfgval = cfgval.trim();
		}

		if (cfgval == null || cfgval.length() == 0)
		{
			cfgval = dflt;
		}
		else
		{
			if (cfgval.equals(NULLMARKER)) cfgval = null;
		}
		if (mdty && (cfgval == null || cfgval.length() == 0)) configError(xpath, "Missing mandatory item");

		return cfgval;
	}

	private void configError(String xpath, String msg) throws com.grey.base.ConfigException
	{
		String str = "CONFIG ERROR: "+msg+" - "+label+ELEM_SEP+xpath;
		throw new com.grey.base.ConfigException(str);
	}
}
