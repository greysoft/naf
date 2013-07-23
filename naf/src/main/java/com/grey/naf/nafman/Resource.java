/*
 * Copyright 2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;

final class Resource
	implements Registry.DefResource.DataGenerator
{
	public final Registry.DefResource def;
	private final HTTP http;
	private final javax.xml.transform.Transformer xslproc;
	private final boolean staticrsp; //statically configured response

	private java.nio.ByteBuffer httprsp;
	private byte[] srcdata;
	private long srctime;

	//preallocated purely for efficiency
	private final java.util.Date dt = new java.util.Date();

	public Resource(Registry.DefResource d, HTTP h, javax.xml.transform.Transformer xsl, java.nio.ByteBuffer buf)
	{
		def = d;
		http = h;
		xslproc = xsl;
		httprsp = buf;
		staticrsp = (httprsp != null); //httprsp has to be non-null to start with
	}

	public java.nio.ByteBuffer getContent(long cachetime, Primary primary) throws com.grey.base.FaultException, java.io.IOException
	{
		if (httprsp != null) {
			if (staticrsp) return httprsp;
			if (srctime + cachetime > primary.dsptch.systime()) return httprsp; //cached response
		}

		// not cached, but check if source data has changed before we have to go to the expense of XSL transforms
		Registry.DefResource.DataGenerator gen = (def.gen == null ? this : def.gen);
		byte[] newdata = gen.generateResourceData(def, primary.dsptch);
		if (java.util.Arrays.equals(newdata, srcdata)) return httprsp; //source-data unchanged

		// we need to generate the resource data anew - unless 'newdata' is already the finished article
		byte[] rspdata = (xslproc == null ? newdata : formatData(newdata, null));

		// create the NIO response buffer
		httprsp = http.buildDynamicResponse(rspdata, httprsp);
		srcdata = newdata;
		srctime = primary.dsptch.systime();
		return httprsp;
	}

	public byte[] formatData(byte[] indata, com.grey.base.utils.HashedMap<String, String> params) throws com.grey.base.FaultException
	{
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
		} catch (Exception ex) {
			throw new com.grey.base.FaultException(ex, "XSLT transform failed for resource="+def.name+"- "+ex);
		}
		return obstrm.toByteArray();
	}

	// Whatever Registry.DefResource def context we were invoked in, we generate a standard data block
	// that is not specific to it.
	@Override
	public byte[] generateResourceData(Registry.DefResource rd, com.grey.naf.reactor.Dispatcher d)
	{
		java.util.Collection<Registry.DefCommand> cmds = Registry.get().getCommands();
		CharSequence xml_dispatchers = d.nafman.listDispatchers();
		com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars();
		long timeboot = d.timeboot;
		long uptime = d.systime() - timeboot;
		dt.setTime(timeboot);
		StringBuilder sb = TimeOps.expandMilliTime(uptime, null, true, " ");
		bc.set("<nafman>");
		bc.append("<timeboot>").append(dt.toString()).append("</timeboot>");
		bc.append("<uptime>").append(sb).append("</uptime>");
		bc.append(xml_dispatchers);
		bc.append("<commands>");
		java.util.Iterator<Registry.DefCommand> it = cmds.iterator();
		while (it.hasNext()) {
			Registry.DefCommand cmd = it.next();
			if (cmd.autopublish == null || !Registry.get().isCommandRegistered(cmd.code)) continue;
			bc.append("<command name=\"").append(cmd.code).append("\"");
			bc.append(" family=\"").append(cmd.family).append("\"");
			bc.append(" xsl=\"").append(cmd.autopublish).append("\"");
			bc.append(" neutral=\"").append(StringOps.boolAsString(cmd.neutral)).append("\"");
			bc.append("><![CDATA[").append(cmd.descr).append("]]>");
			bc.append("</command>");
		}
		bc.append("</commands></nafman>");
		return bc.toByteArray();
	}
}