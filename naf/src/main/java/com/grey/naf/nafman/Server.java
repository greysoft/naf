/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;

/*
 * This class represents an embedded HTTP server, which serves live NAFMAN data formatted according to
 * preloaded presentation resources.
 * It is not a formally compliant web-server (eg. we don't issue Date: headers, since they would need
 * to be regenerated per request and thus defeat our caching strategy) but it performs its narrowly
 * prescribed function very well, and works with all known browsers.
 */
public final class Server
	extends com.grey.naf.reactor.ConcurrentListener.Server
	implements com.grey.naf.reactor.Timer.Handler
{
	private static final int S_PREHEADERS = 1;  //initial state, upon new connection
	private static final int S_HEADERS = 2;  //receiving headers
	private static final int S_BODY = 3;  //receiving content
	private static final int S_PROC = 4; //command has been accepted and is being processed

	private static final class SharedFields
	{
		final Primary primary;
		final HTTP http;
		final ResourceManager rsrcmgr;
		final com.grey.base.utils.ObjectWell<Command> cmdstore;
		final com.grey.naf.BufferSpec bufspec;
		final java.nio.ByteBuffer httprsp400;
		final java.nio.ByteBuffer httprsp404;
		final java.nio.ByteBuffer httprsp405;
		final long tmt_idle;
		java.nio.ByteBuffer tmpniobuf; //temp work area, pre-allocated for efficiency

		SharedFields(Primary p, com.grey.base.config.XmlConfig cfg)
				throws com.grey.base.ConfigException, java.io.IOException, java.net.URISyntaxException,
					javax.xml.transform.TransformerConfigurationException
		{
			primary = p;
			com.grey.base.config.XmlConfig cfg_rsrc = new com.grey.base.config.XmlConfig(cfg, "resources");
			tmt_idle = cfg.getTime("timeout", com.grey.base.utils.TimeOps.parseMilliTime("30s"));
			long permcache = cfg.getTime("permcache", com.grey.base.utils.TimeOps.parseMilliTime("1d"));
			long dyncache = cfg.getTime("dyncache", "5s");
			bufspec = new com.grey.naf.BufferSpec(cfg, "niobuffers", 1024, 4*1024);
			cmdstore = new com.grey.base.utils.ObjectWell<Command>(Command.class, "NAFMAN_"+primary.dsptch.name);
			http = new HTTP(bufspec, permcache);
			rsrcmgr = new ResourceManager(cfg_rsrc, primary, http, dyncache);
			httprsp400 = http.buildErrorResponse("400 Unexpected body");
			httprsp404 = http.buildErrorResponse("404 Unknown resource");
			httprsp405 = http.buildErrorResponse("405 Method not supported");
			String pfx = "NAFMAN-Server: ";
			p.dsptch.logger.info(pfx+"DynCache="+TimeOps.expandMilliTime(dyncache)+"; PermCache="+TimeOps.expandMilliTime(permcache)
					+"; Timeout="+TimeOps.expandMilliTime(tmt_idle));
			p.dsptch.logger.trace(pfx+bufspec);
		}
	}

	private final SharedFields shared;
	private com.grey.naf.reactor.Timer tmr_idle;
	private Command cmd;
	private int state;
	private String http_method;
	private String cmdcode; //need this as well as cmd.def, in case latter is null
	private String ctype;
	private int contlen;

	// This is the prototype object which the Listener uses to create the rest
	public Server(com.grey.naf.reactor.ConcurrentListener l, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException, java.io.IOException, java.net.URISyntaxException,
				javax.xml.transform.TransformerConfigurationException
	{
		super(l);
		shared = new SharedFields(Primary.class.cast(lstnr.controller), cfg);
	}

	// This is (or will be) an active Server object
	private Server(Server proto)
	{
		super(proto.lstnr);
		shared = proto.shared;
		chanreader = new com.grey.naf.reactor.IOExecReader(shared.bufspec);
		chanwriter = new com.grey.naf.reactor.IOExecWriter(shared.bufspec);
	}

	@Override
	public com.grey.base.utils.PrototypeFactory.PrototypeObject prototype_create()
	{
		return new Server(this);
	}

	// This is called by the Listener - we will signal it when we naturally complete this connection
	@Override
	public boolean stopServer()
	{
		return false;
	}

	@Override
	protected void connected() throws com.grey.base.FaultException, java.io.IOException
	{
		state = S_PREHEADERS;
		contlen = 0;
		ctype = null;
		cmd = null;
		tmr_idle = dsptch.setTimer(shared.tmt_idle, 0, this);
		chanreader.receiveDelimited((byte)'\n');
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
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data) throws com.grey.base.FaultException, java.io.IOException
	{
		int len_eol = (data.ar_len > 1 && data.ar_buf[data.ar_off+data.ar_len-2] == '\r' ? 2 : 1);
		data.ar_len -= len_eol;
		if (state == S_PREHEADERS) {
			//the headers start with the method-and-URL line
			if (data.ar_len == 0) return; //discard leading blank lines (not legal, but tolerate - see RFC-2616 4.1)
			if ((http_method = shared.http.parseMethod(data)) == null) {
				sendResponse(shared.httprsp405); //probably garbage, no point continuing
				return;
			}
			String url = shared.http.parseURL(data);
			cmdcode = shared.http.getURLPath(url);
			Registry.DefCommand def = Registry.get().getCommand(cmdcode);
			if (def == null) def = Registry.get().getCommand(cmdcode.toUpperCase()); //just to be nice
			cmd = shared.cmdstore.extract().init(def, this);
			shared.http.parseQS(url, cmd, true);
			state = S_HEADERS;
			LEVEL lvl = LEVEL.TRC;
			if (dsptch.logger.isActive(lvl)) {
				dsptch.logger.log(lvl, "NAFMAN Server E"+cm_id+" received "+http_method+" "+url);
			}
		} else if (state == S_HEADERS) {
			if (data.ar_len == 0) {
				//headers are now complete
				if (contlen != 0) {
					//... and next comes the request body
					if (!http_method.equals(HTTP.METHOD_POST) || !HTTP.CTYPE_URLFORM.equals(ctype)) {
						sendResponse(shared.httprsp400);
						return;
					}
					state = S_BODY;
					chanreader.receive(contlen); //issue a counted read
					return;
				}
				commandReceived();
				return;
			}
			com.grey.base.utils.ByteChars hdrval;
			if ((hdrval = shared.http.parseHeaderValue(HTTP.HDR_CLEN, data)) != null) {
				contlen = (int)hdrval.parseDecimal();
			} else if ((hdrval = shared.http.parseHeaderValue(HTTP.HDR_CTYPE, data)) != null) {
				ctype = hdrval.toString();
			}
		} else if (state == S_BODY) {
			// we will in fact receive the request body in a single callback, as we issued a counted read
			data.ar_len += len_eol; //not line-oriented
			shared.http.parseQS(data.ar_buf, data.ar_off, data.ar_len, cmd);
			contlen -= data.ar_len;
			if (contlen <= 0) commandReceived();
		}
	}

	private void commandReceived() throws com.grey.base.FaultException, java.io.IOException
	{
		if (cmd.def == null) {
			java.nio.ByteBuffer httprsp = shared.rsrcmgr.getContent(cmdcode);
			if (httprsp == null) httprsp = shared.httprsp404;
			LEVEL lvl = LEVEL.TRC2;
			if (dsptch.logger.isActive(lvl)) {
				dsptch.logger.log(lvl, "NAFMAN Server E"+cm_id+" response="+(httprsp==shared.httprsp404?"404":"resource"));
			}
			sendResponse(httprsp);
			return;
		}
		if (tmr_idle != null) {
			tmr_idle.cancel();
			tmr_idle = null;
		}
		chanreader.endReceive();
		state = S_PROC;
		shared.primary.handleCommand(cmd);
	}

	void commandCompleted(com.grey.base.utils.ByteChars rspbody) throws java.io.IOException, com.grey.base.FaultException
	{
		boolean omit_body = http_method.equals(HTTP.METHOD_HEAD);
		byte[] finaldata = null;
		if (!omit_body) {
			finaldata = rspbody.toByteArray();
			String xsl = cmd.getArg(Command.ATTR_XSL);
			if (xsl != null) {
				byte[] fmtdata = shared.rsrcmgr.formatData(xsl, finaldata, cmd.getArgs());
				if (fmtdata != null) finaldata = fmtdata;
			}
		}
		if (StringOps.stringAsBool(cmd.getArg(com.grey.naf.nafman.Command.ATTR_NOHTTP))) {
			shared.tmpniobuf = com.grey.base.utils.NIOBuffers.encode(finaldata, shared.tmpniobuf, shared.bufspec.directbufs);
		} else {
			shared.tmpniobuf = shared.http.buildDynamicResponse(finaldata, shared.tmpniobuf);
		}
		sendResponse(shared.tmpniobuf);
	}

	private void sendResponse(java.nio.ByteBuffer niobuf) throws java.io.IOException
	{
		if (niobuf != null) {
			niobuf.position(0);
			chanwriter.transmit(niobuf);
		}
		endConnection();
	}

	@Override
	public void timerIndication(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d)
	{
		tmr_idle = null;
		endConnection();
	}

	// already logged by Dispatcher, we have nothing more to add
	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex) {}

	@Override
	public void dumpAppState(StringBuilder sb)
	{
		sb.append("Command=").append(cmdcode).append("/state=").append(state);
		sb.append(" - ").append(http_method).append('/').append(ctype).append('/').append(contlen);
	}
}