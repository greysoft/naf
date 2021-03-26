/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

import com.grey.base.config.SysProps;
import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteChars;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.DispatcherRunnable;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.engine.PacketDNS;
import com.grey.naf.dns.resolver.engine.ResourceData;
import com.grey.naf.reactor.ConcurrentListener;
import com.grey.logging.Logger;

/**
 * This is the skeleton for a very basic DNS server, where the zone-info backend is left to be implemented by
 * application-specific sub-classes.
 * It was conceived mainly as a test harness for the NAF DNS-Resolver.
 */
public class ServerDNS implements DispatcherRunnable
{
	public interface DNSQuestionResolver
	{
		public void dnsResolveQuestion(int qid, byte qtype, ByteChars qname, boolean recursion_desired, java.net.InetSocketAddress remote_addr, Object questionCallbackParam) throws java.io.IOException;
	}

	// use same NIO/socket property names as the Resolver API
	static final int PKTSIZ_UDP = SysProps.get("greynaf.dns.maxudp", PacketDNS.UDPMAXMSG);
	static final int PKTSIZ_TCP = SysProps.get("greynaf.dns.maxtcp", 5*PKTSIZ_UDP);
	static final int UDPSOCKBUFSIZ = SysProps.get("greynaf.dns.sockbuf", PacketDNS.UDPMAXMSG * 128);
	static final boolean DIRECTNIOBUFS = com.grey.naf.BufferGenerator.directniobufs;
	static final Logger.LEVEL DEBUGLVL = Logger.LEVEL.TRC2;

	private static final boolean IGNORE_QTRAIL = SysProps.get("greynaf.dns.server.ignoretrailing", false);
	private static final boolean COMPRESS_NAMES = SysProps.get("greynaf.dns.server.compressnames", true);

	private final Dispatcher dsptch;
	private final ServerDNS.DNSQuestionResolver responder;
	private final QueryParser queryParser;
	private final ConcurrentListener listenerTCP;
	private final TransportUDP transportUDP;
	private final boolean recursionOffered;

	// this is just a temporary work area, pre-allocated for efficiency
	private final PacketDNS dnspkt;

	public java.net.InetAddress getLocalIP() {return transportUDP.getLocalIP();}
	public int getLocalPort() {return transportUDP.getLocalPort();}
	@Override
	public String getName() {return "DNS-Server";}
	@Override
	public Dispatcher getDispatcher() {return dsptch;}

	public ServerDNS(Dispatcher d, DNSQuestionResolver r, DnsServerConfig cfg) throws java.io.IOException {
		dsptch = d;
		responder = r;
		queryParser = new QueryParser(d.getLogger());
		recursionOffered = cfg.getRecursionOffered();
		dnspkt = new PacketDNS(Math.max(PKTSIZ_TCP, PKTSIZ_UDP), DIRECTNIOBUFS, 0, dsptch);

		listenerTCP = ConcurrentListener.create(dsptch, this, null, cfg.getListenerConfig());
		transportUDP = new TransportUDP(d, this, listenerTCP.getIP(),listenerTCP.getPort());
		dsptch.getLogger().info("DNS-Server: Port="+cfg.getListenerConfig().getPort()+", directbufs="+DIRECTNIOBUFS+"; udpmax="+PKTSIZ_UDP+"; tcpmax="+PKTSIZ_TCP);

		if (IGNORE_QTRAIL) dsptch.getLogger().info("DNS-Server: Will ignore trailing bytes in incoming queries");
	}

	@Override
	public void startDispatcherRunnable() throws java.io.IOException {
		listenerTCP.startDispatcherRunnable();
		transportUDP.startDispatcherRunnable();
	}

	@Override
	public boolean stopDispatcherRunnable() {
		boolean done = listenerTCP.stopDispatcherRunnable();
		done = done && transportUDP.stopDispatcherRunnable();
		return done;
	}

	// NB: No need to defend against buffer overflow during query decoding, as Dispatcher will simply catch and log the
	// ArrayIndexOutOfBoundsException for us. No need to send an error response in that situation either, as the sender
	// was obviously just being rude.
	void queryReceived(ByteArrayRef rcvdata, java.net.InetSocketAddress remote_addr, TransportTCP tcp) throws java.io.IOException {
		boolean is_auth = false; //this method only sends a response on failure, so don't claim to be authoritative.

		// parse the DNS query
		queryParser.reset();
		dnspkt.resetDecoder(rcvdata.buffer(), rcvdata.offset(), rcvdata.size());
		int off = dnspkt.decodeHeader();
		if (off != -1) off = dnspkt.parseQuestion(off, dnspkt.getQuestionCount(), remote_addr, queryParser);
		byte qtype = queryParser.getQueryType();
		ByteChars qname = queryParser.getQueryName();

		if (off == -1 || dnspkt.getQuestionCount() == 0) {
			sendResponse(dnspkt.getQID(), qtype, qname, PacketDNS.RCODE_BADFMT, is_auth, dnspkt.recursionDesired(),
					null, null, null, remote_addr, tcp);
			return;
		}
		if (dsptch.getLogger().isActive(ServerDNS.DEBUGLVL)) {
			dsptch.getLogger().log(ServerDNS.DEBUGLVL, "DNS-Server received "+(tcp==null?"UDP":"TCP")+" query="
					+dnspkt.getQID()+"/"+ResolverDNS.getQTYPE(qtype)+"/"+qname
					+" from "+remote_addr+" - size="+rcvdata.size()+", recurse="+dnspkt.recursionDesired());
		}
		if (dnspkt.opcode() != PacketDNS.OPCODE_QRY) {
			dsptch.getLogger().info("DNS-Server rejecting unsupported packet="+dnspkt.getQID()+" with opcode="+dnspkt.opcode()
				+" - rcode="+dnspkt.rcode()+"/rsp="+dnspkt.isResponse()
				+" - query="+ResolverDNS.getQTYPE(qtype)+"/"+qname+" from "+remote_addr);
			sendResponse(dnspkt.getQID(), qtype,qname, PacketDNS.RCODE_NOTIMPL, is_auth, dnspkt.recursionDesired(),
					null, null, null, remote_addr, tcp);
			return;
		}

		//ignore AdditionalInfo RRs (a common occurrence is qtype=41, for EDNS OPT)
		if (dnspkt.getAnswerCount() + dnspkt.getAuthorityCount() != 0
				|| dnspkt.getQuestionCount() != 1
				|| dnspkt.rcode() != 0
				|| dnspkt.isResponse() || dnspkt.isTruncated()) {
			dsptch.getLogger().info("DNS-Server rejecting invalid packet="+dnspkt.getQID()+"/"+dnspkt.getQuestionCount()
				+"/"+dnspkt.getAnswerCount()+"/"+dnspkt.getAuthorityCount()+"/"+dnspkt.getInfoCount()
				+" with rcode="+dnspkt.rcode()+"/opcode="+dnspkt.opcode()+"/rsp="+dnspkt.isResponse()
				+"/trunc="+dnspkt.isTruncated()+"/auth="+dnspkt.isAuth()
				+" - query="+ResolverDNS.getQTYPE(qtype)+"/"+qname+" from "+remote_addr);
			sendResponse(dnspkt.getQID(), qtype, qname, PacketDNS.RCODE_BADFMT, is_auth, dnspkt.recursionDesired(),
					null, null, null, remote_addr, tcp);
			return;
		}
		if (dnspkt.getInfoCount() != 0) off = dnspkt.skipSection(off, PacketDNS.SECT_INFO, dnspkt.getInfoCount());

		if (!IGNORE_QTRAIL) {
			int excess = rcvdata.limit() - off;
			if (excess != 0) {
				dsptch.getLogger().info("DNS-Server rejecting packet="+dnspkt.getQID()+" due to excess bytes="+excess
					+" - query="+ResolverDNS.getQTYPE(qtype)+"/"+qname+" from "+remote_addr);
				sendResponse(dnspkt.getQID(), qtype, qname, PacketDNS.RCODE_BADFMT, is_auth, dnspkt.recursionDesired(),
						null, null, null, remote_addr, tcp);
				return;
			}
		}

		//the question appears valid, so answer it
		try {
			responder.dnsResolveQuestion(dnspkt.getQID(), qtype, qname, dnspkt.recursionDesired(), remote_addr, tcp);
		} catch (Throwable ex) {
			dsptch.getLogger().log(Logger.LEVEL.ERR, ex, true, "DNS-Server failed to handle DNS query="+dnspkt.getQID()
				+"/"+qtype+"/"+qname+" from "+remote_addr);
			if (dsptch.isActive()) {
				sendResponse(dnspkt.getQID(), qtype, qname, PacketDNS.RCODE_SRVFAIL, is_auth, dnspkt.recursionDesired(),
						null, null, null, remote_addr, tcp);
			}
		}
	}

	public boolean sendResponse(int qid, byte qtype, ByteChars qname, int rcode,
			boolean isAuth, boolean recursionDesired,
			ResourceData[] ans, ResourceData[] auth, ResourceData[] info,
			java.net.InetSocketAddress remoteAddr, Object questionCallbackParam) throws java.io.IOException
	{
		boolean is_tcp = (questionCallbackParam != null);
		dnspkt.resetEncoder(is_tcp, COMPRESS_NAMES);
		dnspkt.setResponse();
		dnspkt.opcode(PacketDNS.OPCODE_QRY);
		dnspkt.rcode(rcode);
		if (recursionOffered) dnspkt.setRecursionAvailable();
		if (isAuth) dnspkt.setAuth();
		if (recursionDesired) dnspkt.setRecursionDesired();
		int anscnt = (ans==null?0:ans.length);
		int authcnt = (auth==null?0:auth.length);
		int infocnt = (info==null?0:info.length);
		dnspkt.setHeader(qid, 1, anscnt, authcnt, infocnt);
		int off = dnspkt.encodeHeader();
		off = dnspkt.encodeQuestion(off, qtype, qname);
		int off_ans = off;
		if (ans != null) off = dnspkt.encodeSection(off, PacketDNS.SECT_ANSWERS, ans);
		if (auth != null) off = dnspkt.encodeSection(off, PacketDNS.SECT_AUTH, auth);
		if (info != null) off = dnspkt.encodeSection(off, PacketDNS.SECT_INFO, info);
		java.nio.ByteBuffer niobuf = dnspkt.completeEncoding(off);
		int rsplen = niobuf.limit();

		// check if a UDP response needs to be truncated, and if so strip all the result data for simplicity
		if (!is_tcp && rsplen > PKTSIZ_UDP) {
			dnspkt.setTruncated();
			anscnt = authcnt = infocnt = 0;
			dnspkt.setHeader(qid, 1, anscnt, authcnt, infocnt);
			dnspkt.encodeHeader(); //re-encode the header
			niobuf = dnspkt.completeEncoding(off_ans); //discard everything after the question
			rsplen = niobuf.limit();
		}

		if (dsptch.getLogger().isActive(ServerDNS.DEBUGLVL)) {
			dsptch.getLogger().log(ServerDNS.DEBUGLVL, "DNS-Server sending "+(is_tcp?"TCP":"UDP")+" response="
					+dnspkt.getQID()+"/"+ResolverDNS.getQTYPE(qtype)+"/"+qname
					+" with answer="+anscnt+"/"+authcnt+"/"+infocnt+"/rcode="+rcode+" to "+remoteAddr
					+" - size="+niobuf.limit()+", auth="+isAuth+(dnspkt.isTruncated()?"/truncated":""));
		}
		if (is_tcp) {
			((TransportTCP)questionCallbackParam).sendResponse(niobuf);
		} else {
			transportUDP.sendResponse(niobuf, remoteAddr);
		}
		return dnspkt.isTruncated();
	}


	private static final class QueryParser
		implements PacketDNS.MessageCallback
	{
		private final Logger logger;

		//current question - these are only valid within the scope of a single queryReceived() call
		private byte qtype;
		private ByteChars qname;

		public byte getQueryType() {return qtype;}
		public ByteChars getQueryName() {return qname;}

		public QueryParser(Logger logger) {
			this.logger = logger;
		}

		public void reset() {
			qtype = 0;
			qname = null;
		}

		@Override
		public boolean handleMessageQuestion(int qid, int qnum, int qcnt, byte qt, byte qclass, ByteChars qn, java.net.InetSocketAddress remote_addr) {
			if (qclass != PacketDNS.QCLASS_INET) {
				logger.info("DNS-Server rejecting class="+qclass+" for query="+qt+"/"+qn+" from "+remote_addr);
				return false;
			}
			if (qnum != 0) {
				logger.info("DNS-Server rejecting excess question #"+qnum+"/"+qcnt+" = "+qt+"/"+qn+" from "+remote_addr);
				return false;
			}
			qtype = qt;
			qname = new ByteChars(qn);
			return true;
		}

		@Override
		public boolean handleMessageRR(int qid, int sectiontype, int rrnum, int rrcnt, ByteChars rrname, ResourceData rr, java.net.InetSocketAddress remote_addr) {
			throw new IllegalStateException("DNS server does not expect to receive sectiontype="+sectiontype+" - "+rr);
		}
	}
}