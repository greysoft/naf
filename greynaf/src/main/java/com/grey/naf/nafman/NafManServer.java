/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import java.util.function.Supplier;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.NIOBuffers;
import com.grey.base.collections.ObjectPool;
import com.grey.naf.BufferGenerator;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.CM_Listener;
import com.grey.naf.reactor.TimerNAF;

/*
 * This class represents an embedded HTTP server, which serves live NAFMAN data formatted according to
 * preloaded presentation resources.
 * It is not a formally compliant web-server (eg. we don't issue Date: headers, since they would need
 * to be regenerated per request and thus defeat our caching strategy) but it performs its narrowly
 * prescribed function very well, and works with all known browsers.
 */
public class NafManServer
	extends com.grey.naf.reactor.CM_Server
	implements TimerNAF.Handler
{
	private static final int S_PREHEADERS = 1;  //initial state, upon new connection
	private static final int S_HEADERS = 2;  //receiving headers
	private static final int S_BODY = 3;  //receiving content
	private static final int S_PROC = 4; //command has been accepted and is being processed

	static final class Factory
		implements com.grey.naf.reactor.CM_Listener.ServerFactory
	{
		private final CM_Listener lstnr;
		private final SharedFields shared;
		@Override
		public NafManServer createServer() {return new NafManServer(lstnr, shared);}

		public Factory(CM_Listener l, Object cfg)
			throws java.io.IOException, javax.xml.transform.TransformerConfigurationException
		{
			lstnr = l;
			@SuppressWarnings("unchecked") Supplier<NafManConfig> srvcfg = (Supplier<NafManConfig>)cfg;
			PrimaryAgent agent = PrimaryAgent.class.cast(l.getController());
			shared = new SharedFields(agent, srvcfg.get());
		}
	}

	private static final class SharedFields
	{
		final PrimaryAgent primary;
		final HTTP http;
		final ResourceManager rsrcmgr;
		final ObjectPool<NafManCommand> cmdstore;
		final BufferGenerator bufspec;
		final java.nio.ByteBuffer httprsp400;
		final java.nio.ByteBuffer httprsp404;
		final java.nio.ByteBuffer httprsp405;
		final long tmt_idle;
		java.nio.ByteBuffer tmpniobuf; //temp work area, pre-allocated for efficiency

		SharedFields(PrimaryAgent p, NafManConfig cfg)
				throws java.io.IOException, javax.xml.transform.TransformerConfigurationException
		{
			primary = p;
			Dispatcher dsptch = primary.getDispatcher();
			tmt_idle = cfg.getIdleConnectionTimeout();
			long permcache = cfg.getDeclaredStaticTTL();
			long dyncache = cfg.getDynamicResourceTTL();
			bufspec = new BufferGenerator(cfg.getBufferConfig());
			cmdstore = new ObjectPool<>(() -> new NafManCommand());
			http = new HTTP(bufspec, permcache);
			rsrcmgr = new ResourceManager(primary, http, dyncache);
			httprsp400 = http.buildErrorResponse("400 Bad request");
			httprsp404 = http.buildErrorResponse("404 Unknown resource");
			httprsp405 = http.buildErrorResponse("405 Method not supported");
			String pfx = "NAFMAN-Server: ";
			dsptch.getLogger().info(pfx+"DynCache="+TimeOps.expandMilliTime(dyncache)+"; PermCache="+TimeOps.expandMilliTime(permcache)
					+"; Timeout="+TimeOps.expandMilliTime(tmt_idle));
			dsptch.getLogger().trace(pfx+bufspec);
		}
	}

	private final SharedFields shared;
	private TimerNAF tmr_idle;
	private NafManCommand cmd;
	private int state;
	private String http_method;
	private String cmdcode; //need this as well as cmd.def, in case latter is null
	private String ctype;
	private int contlen;

	NafManServer(CM_Listener l, SharedFields s)
	{
		super(l, s.bufspec, s.bufspec);
		shared = s;
	}

	@Override
	protected void connected() throws java.io.IOException
	{
		state = S_PREHEADERS;
		contlen = 0;
		ctype = null;
		cmd = null;
		tmr_idle = getDispatcher().setTimer(shared.tmt_idle, 0, this);
		getReader().receiveDelimited((byte)'\n');
	}

	@Override
	public void ioDisconnected(CharSequence diagnostic)
	{
		if (state == S_PROC) {
			// the command is already in play, so wait for the Dispatchers to finish processing it
			return;
		}
		endConnection();
	}

	void endConnection()
	{
		if (cmd != null) shared.cmdstore.store(cmd.clear());
		if (tmr_idle != null) tmr_idle.cancel();
		disconnect();
	}

	@Override
	public void ioReceived(ByteArrayRef data) throws java.io.IOException
	{
		LEVEL lvl = LEVEL.TRC;
		int len_eol = (data.size() > 1 && data.byteAt(data.size()-2) == '\r' ? 2 : 1);
		data.incrementSize(-len_eol);
		if (state == S_PREHEADERS) {
			//the headers start with the method-and-URL line
			if (data.size() == 0) return; //discard leading blank lines (not legal, but tolerate - see RFC-2616 4.1)
			if ((http_method = shared.http.parseMethod(data)) == null) {
				sendResponse(shared.httprsp405); //probably garbage, no point continuing
				return;
			}
			String url;
			try {
				url = shared.http.parseURL(data);
				cmdcode = shared.http.getURLPath(url);
			} catch (Exception e) {
				//most likely cause is an invalid hex number in HTTP.decodeURL() - doesn't merit an ugly stack dump
				if (getLogger().isActive(lvl)) {
					getLogger().log(lvl, "NAFMAN Server E"+getCMID()+" rejecting bad URL - "+new ByteChars(data,false));
				}
				sendResponse(shared.httprsp400);
				return;
			}
			NafManRegistry reg = shared.primary.getRegistry();
			NafManRegistry.DefCommand def = reg.getCommand(cmdcode);
			if (def == null) def = reg.getCommand(cmdcode.toUpperCase()); //just to be nice
			cmd = shared.cmdstore.extract().init(def, this);
			shared.http.parseQS(url, cmd, true);
			state = S_HEADERS;
			if (getLogger().isActive(lvl)) {
				getLogger().log(lvl, "NAFMAN Server E"+getCMID()+" received "+http_method+"="+url+" - cmd="+def);
			}
		} else if (state == S_HEADERS) {
			if (data.size() == 0) {
				//headers are now complete
				if (contlen != 0) {
					//... and next comes the request body
					if (!http_method.equals(HTTP.METHOD_POST) || !HTTP.CTYPE_URLFORM.equals(ctype)) {
						sendResponse(shared.httprsp400);
						return;
					}
					state = S_BODY;
					getReader().receive(contlen); //issue a counted read
					return;
				}
				commandReceived();
				return;
			}
			ByteChars hdrval;
			if ((hdrval = shared.http.parseHeaderValue(HTTP.HDR_CLEN, data)) != null) {
				contlen = (int)hdrval.parseDecimal();
			} else if ((hdrval = shared.http.parseHeaderValue(HTTP.HDR_CTYPE, data)) != null) {
				ctype = hdrval.toString();
			}
		} else if (state == S_BODY) {
			// we will in fact receive the request body in a single callback, as we issued a counted read
			data.incrementSize(len_eol); //not line-oriented
			shared.http.parseQS(data.buffer(), data.offset(), data.size(), cmd);
			contlen -= data.size();
			if (contlen <= 0) commandReceived();
		}
	}

	private void commandReceived() throws java.io.IOException
	{
		if (cmd.getCommandDef() == null) {
			java.nio.ByteBuffer httprsp = shared.rsrcmgr.getContent(cmdcode, cmd);
			if (httprsp == null) httprsp = shared.httprsp404;
			LEVEL lvl = LEVEL.TRC2;
			if (getLogger().isActive(lvl)) {
				getLogger().log(lvl, "NAFMAN Server E"+getCMID()+" response="+(httprsp==shared.httprsp404?"404":"resource"));
			}
			sendResponse(httprsp);
			return;
		}
		if (tmr_idle != null) {
			tmr_idle.cancel();
			tmr_idle = null;
		}
		getReader().endReceive();
		state = S_PROC;
		shared.primary.handleCommand(cmd);
	}

	void commandCompleted(ByteChars rspbody) throws java.io.IOException
	{
		boolean omit_body = http_method.equals(HTTP.METHOD_HEAD);
		byte[] finaldata = null;
		if (!omit_body) {
			finaldata = rspbody.toArray();
			String xsl = cmd.getArg(NafManCommand.ATTR_XSL);
			if (xsl != null && !StringOps.stringAsBool(cmd.getArg(NafManCommand.ATTR_NOXSL))) {
				byte[] fmtdata = shared.rsrcmgr.formatData(xsl, finaldata, cmd.getArgs());
				if (fmtdata != null) finaldata = fmtdata;
			}
		}
		if (StringOps.stringAsBool(cmd.getArg(com.grey.naf.nafman.NafManCommand.ATTR_NOHTTP))) {
			shared.tmpniobuf = NIOBuffers.encode(finaldata, shared.tmpniobuf, shared.bufspec.directbufs);
		} else {
			shared.tmpniobuf = shared.http.buildDynamicResponse(finaldata, shared.tmpniobuf);
		}
		sendResponse(shared.tmpniobuf);
	}

	private void sendResponse(java.nio.ByteBuffer niobuf) throws java.io.IOException
	{
		if (niobuf != null) {
			niobuf.position(0);
			getWriter().transmit(niobuf);
		}
		endConnection();
	}

	@Override
	public void timerIndication(TimerNAF tmr, Dispatcher d)
	{
		tmr_idle = null;
		endConnection();
	}

	@Override
	public StringBuilder dumpAppState(StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder();
		sb.append("Command=").append(cmdcode).append("/state=").append(state);
		sb.append(" - ").append(http_method).append('/').append(ctype).append('/').append(contlen);
		return sb;
	}
}