/*
 * Copyright 2013-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.reactor.Dispatcher;

class NafManResource
	implements NafManRegistry.DefResource.DataGenerator
{
	private final NafManRegistry.DefResource def;
	private final HTTP http;
	private final boolean staticrsp; //statically configured response
	private final javax.xml.transform.TransformerFactory xslfact;
	private final byte[] rsrcdata;
	private javax.xml.transform.Transformer xslproc;

	private java.nio.ByteBuffer httprsp;
	private byte[] srcdata;
	private long srctime;

	//preallocated purely for efficiency
	private final java.util.Date dt = new java.util.Date();

	public NafManResource(NafManRegistry.DefResource d, HTTP h, java.nio.ByteBuffer buf,
			javax.xml.transform.TransformerFactory fact, byte[] data)
		throws javax.xml.transform.TransformerConfigurationException
	{
		def = d;
		http = h;
		httprsp = buf;
		staticrsp = (httprsp != null);
		xslfact = fact;
		rsrcdata = data;
		if (xslfact != null) createXSL();
	}

	public java.nio.ByteBuffer getContent(long cachetime, PrimaryAgent primary) throws java.io.IOException
	{
		Dispatcher dsptch = primary.getDispatcher();
		if (httprsp != null) {
			if (staticrsp) return httprsp;
			if (srctime + cachetime > dsptch.getSystemTime()) return httprsp; //cached response
		}

		// not cached, but check if source data has changed before we have to go to the expense of XSL transforms
		NafManRegistry.DefResource.DataGenerator gen = (def.gen == null ? this : def.gen);
		byte[] newdata = gen.generateResourceData(dsptch);
		if (java.util.Arrays.equals(newdata, srcdata)) return httprsp; //source-data unchanged

		// we need to generate the resource data anew - unless 'newdata' is already the finished article
		byte[] rspdata = (xslproc == null ? newdata : formatData(newdata, null));

		// create the NIO response buffer
		httprsp = http.buildDynamicResponse(rspdata, httprsp);
		srcdata = newdata;
		srctime = dsptch.getSystemTime();
		return httprsp;
	}

	public byte[] formatData(byte[] indata, com.grey.base.collections.HashedMap<String, String> params)
	{
		if (xslproc == null) {
			throw new NAFConfigException("XSL processor not available for resource="+def.name);
		}

		//transform the data via our XSLT stylesheet - we are expected to have one if this method is called
		java.io.ByteArrayInputStream ibstrm = new java.io.ByteArrayInputStream(indata);
		java.io.ByteArrayOutputStream obstrm = new java.io.ByteArrayOutputStream();
		javax.xml.transform.stream.StreamSource istrm = new javax.xml.transform.stream.StreamSource(ibstrm);
		javax.xml.transform.stream.StreamResult ostrm = new javax.xml.transform.stream.StreamResult(obstrm);
		xslproc.clearParameters();
		if (params != null) {
			java.util.Iterator<String> it = params.keysIterator();
			while (it.hasNext()) {
				String pnam = it.next();
				xslproc.setParameter(pnam, params.get(pnam));
			}
		}
		try {
			xslproc.transform(istrm, ostrm);
		} catch (Throwable ex) {
			//have observed XSL processor getting stuck on next call after out-of-mem errors
			String errmsg = "XSLT transform failed for resource="+def.name+"- "+ex;
			try {
				createXSL();
			} catch (Throwable ex2) {
				errmsg += " ... and we failed to recreate XSL processor - "+ex2;
				xslproc = null;
			}
			throw new NAFConfigException(errmsg, ex);
		}
		return obstrm.toByteArray();
	}

	// Whatever Registry.DefResource def context we were invoked in, we generate a standard data block
	// that is not specific to it.
	@Override
	public byte[] generateResourceData(com.grey.naf.reactor.Dispatcher d)
	{
		NafManRegistry reg = d.getAgent().getRegistry();
		java.util.Collection<NafManRegistry.DefCommand> cmds = reg.getCommands();
		CharSequence xml_dispatchers = d.getAgent().listDispatchers();
		com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars();
		long timeboot = d.getTimeBoot();
		long uptime = d.getSystemTime() - timeboot;
		dt.setTime(timeboot);
		StringBuilder sb = TimeOps.expandMilliTime(uptime, null, true, " ");
		bc.populate("<nafman>");
		bc.append("<timeboot>").append(dt.toString()).append("</timeboot>");
		bc.append("<uptime>").append(sb).append("</uptime>");
		bc.append(xml_dispatchers);
		bc.append("<commands>");
		java.util.Iterator<NafManRegistry.DefCommand> it = cmds.iterator();
		while (it.hasNext()) {
			NafManRegistry.DefCommand cmd = it.next();
			if (cmd.autopublish == null || !reg.isCommandRegistered(cmd.code)) continue;
			bc.append("<command name=\"").append(cmd.code).append("\"");
			bc.append(" family=\"").append(cmd.family).append("\"");
			bc.append(" xsl=\"").append(cmd.autopublish).append("\"");
			bc.append(" neutral=\"").append(StringOps.boolAsString(cmd.neutral)).append("\"");
			bc.append("><![CDATA[").append(cmd.descr).append("]]>");
			bc.append("</command>");
		}
		bc.append("</commands></nafman>");
		return bc.toArray();
	}

	private void createXSL() throws javax.xml.transform.TransformerConfigurationException
	{
		java.io.ByteArrayInputStream bstrm = new java.io.ByteArrayInputStream(rsrcdata);
		javax.xml.transform.stream.StreamSource src = new javax.xml.transform.stream.StreamSource(bstrm);
		xslproc = xslfact.newTransformer(src);
	}
}