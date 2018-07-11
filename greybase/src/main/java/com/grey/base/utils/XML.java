/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.io.StringWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;

public class XML
{
	public static org.w3c.dom.Document getDOM(CharSequence pthnam)
			throws javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException, java.io.IOException
	{
		javax.xml.parsers.DocumentBuilderFactory domfact = javax.xml.parsers.DocumentBuilderFactory.newInstance();
		domfact.setIgnoringComments(true);
		domfact.setNamespaceAware(true);
		domfact.setCoalescing(true);
		javax.xml.parsers.DocumentBuilder bldr = domfact.newDocumentBuilder();
		return bldr.parse(pthnam.toString());
	}

	public static org.w3c.dom.Document makeDOM(CharSequence xmltxt)
			throws javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException, java.io.IOException
	{	
		javax.xml.parsers.DocumentBuilderFactory domfact = javax.xml.parsers.DocumentBuilderFactory.newInstance();
		domfact.setIgnoringComments(true);
		domfact.setNamespaceAware(true);
		domfact.setCoalescing(true);
		javax.xml.parsers.DocumentBuilder bldr = domfact.newDocumentBuilder();
		org.xml.sax.InputSource src = new org.xml.sax.InputSource(new java.io.StringReader(xmltxt.toString()));
		return bldr.parse(src);
	}

	public static javax.xml.xpath.XPath getXpathProcessor()
	{
		javax.xml.xpath.XPathFactory xpathfact = javax.xml.xpath.XPathFactory.newInstance();
		return xpathfact.newXPath();
	}

	// Neither TransformerFactory nor Transformer is MT-safe, hence must create anew in here
	public static String toString(org.w3c.dom.Node node) {
		try {
			Transformer transformer =  TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(node), new StreamResult(writer));
			return writer.toString();
		} catch (Throwable ex) {
			return "Failed to serialise node="+node+" - "+ex;
		}
	}
}
