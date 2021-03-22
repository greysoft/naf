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
import com.grey.naf.dns.resolver.PacketDNS;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.ResourceData;
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
		public void dnsResolveQuestion(int qid, byte qtype, ByteChars qname, boolean recursion_desired, java.net.InetSocketAddress remote_addr, Object cbparam) throws java.io.IOException;
		public boolean dnsRecursionAvailable();
	}

	// use same NIO/socket property names as the Resolver API
	static final int PKTSIZ_UDP = SysProps.get("greynaf.dns.maxudp", PacketDNS.UDPMAXMSG);
	static final int PKTSIZ_TCP = SysProps.get("greynaf.dns.maxtcp", 5*PKTSIZ_UDP);
	static final int UDPSOCKBUFSIZ = SysProps.get("greynaf.dns.sockbuf", PacketDNS.UDPMAXMSG * 128);
	static final boolean DIRECTNIOBUFS = com.grey.naf.BufferSpec.directniobufs;
	static final Logger.LEVEL DEBUGLVL = Logger.LEVEL.TRC2;

	private static final boolean IGNORE_QTRAIL = SysProps.get("greynaf.dns.server.ignoretrailing", false);
	private static final boolean COMPRESS_NAMES = SysProps.get("greynaf.dns.server.compressnames", true);

	private final Dispatcher dsptch;
	private final ServerDNS.DNSQuestionResolver responder;
	private final Handlers handlers;
	private final ConcurrentListener listener_tcp;
	private final TransportUDP transport_udp;
	private final boolean recursion_offered;

	// this is just a temporary work area, pre-allocated for efficiency
	private final PacketDNS dnspkt = new PacketDNS(Math.max(PKTSIZ_TCP, PKTSIZ_UDP), DIRECTNIOBUFS, 0);

	public java.net.InetAddress getLocalIP() {return transport_udp.getLocalIP();}
	public int getLocalPort() {return transport_udp.getLocalPort();}
	@Override
	public String getName() {return "DNS-Server";}
	@Override
	public Dispatcher getDispatcher() {return dsptch;}

	public ServerDNS(Dispatcher d, DNSQuestionResolver r, DnsServerConfig cfg) throws java.io.IOException {
		dsptch = d;
		responder = r;
		handlers = new Handlers(this);
		recursion_offered = responder.dnsRecursionAvailable();

		listener_tcp = ConcurrentListener.create(dsptch, this, null, cfg.getListenerConfig());
		transport_udp = new TransportUDP(d, this, listener_tcp.getIP(),listener_tcp.getPort());
		dsptch.getLogger().info("DNS-Server: Port="+cfg.getListenerConfig().getPort()+", directbufs="+DIRECTNIOBUFS+"; udpmax="+PKTSIZ_UDP+"; tcpmax="+PKTSIZ_TCP);
		if (IGNORE_QTRAIL) dsptch.getLogger().info("DNS-Server: Will ignore trailing bytes in incoming queries");
	}

	@Override
	public void startDispatcherRunnable() throws java.io.IOException {
		listener_tcp.startDispatcherRunnable();
		transport_udp.startDispatcherRunnable();
	}

	// NB: No need to defend against buffer overflow during query decoding, as Dispatcher will simply catch and log the
	// ArrayIndexOutOfBoundsException for us. No need to send an error response in that situation either, as the sender
	// was obviously just being rude.
	void queryReceived(ByteArrayRef rcvdata, java.net.InetSocketAddress remote_addr, TransportTCP tcp) throws java.io.IOException {
		handlers.qry_qtype = 0;
		handlers.qry_qname = null;
		boolean is_auth = false; //this method only sends a response on failure, so don't claim to be authoritative.
		dnspkt.resetDecoder(rcvdata.buffer(), rcvdata.offset(), rcvdata.size());
		int off = dnspkt.decodeHeader();
		if (off != -1) off = dnspkt.parseQuestion(off, dnspkt.getQuestionCount(), remote_addr, handlers);
		if (off == -1 || dnspkt.getQuestionCount() == 0) {
			sendResponse(dnspkt.getQID(), handlers.qry_qtype, handlers.qry_qname, PacketDNS.RCODE_BADFMT, is_auth, dnspkt.recursionDesired(),
					null, null, null, remote_addr, tcp);
			return;
		}
		if (dsptch.getLogger().isActive(ServerDNS.DEBUGLVL)) {
			dsptch.getLogger().log(ServerDNS.DEBUGLVL, "DNS-Server received "+(tcp==null?"UDP":"TCP")+" query="
					+dnspkt.getQID()+"/"+ResolverDNS.getQTYPE(handlers.qry_qtype)+"/"+handlers.qry_qname
					+" from "+remote_addr+" - size="+rcvdata.size()+", recurse="+dnspkt.recursionDesired());
		}
		if (dnspkt.opcode() != PacketDNS.OPCODE_QRY) {
			dsptch.getLogger().info("DNS-Server rejecting unsupported packet="+dnspkt.getQID()+" with opcode="+dnspkt.opcode()
				+" - rcode="+dnspkt.rcode()+"/rsp="+dnspkt.isResponse()
				+" - query="+ResolverDNS.getQTYPE(handlers.qry_qtype)+"/"+handlers.qry_qname+" from "+remote_addr);
			sendResponse(dnspkt.getQID(), handlers.qry_qtype, handlers.qry_qname, PacketDNS.RCODE_NOTIMPL, is_auth, dnspkt.recursionDesired(),
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
				+" - query="+ResolverDNS.getQTYPE(handlers.qry_qtype)+"/"+handlers.qry_qname+" from "+remote_addr);
			sendResponse(dnspkt.getQID(), handlers.qry_qtype, handlers.qry_qname, PacketDNS.RCODE_BADFMT, is_auth, dnspkt.recursionDesired(),
					null, null, null, remote_addr, tcp);
			return;
		}
		if (dnspkt.getInfoCount() != 0) off = dnspkt.skipSection(off, PacketDNS.SECT_INFO, dnspkt.getInfoCount());

		if (!IGNORE_QTRAIL) {
			int excess = rcvdata.limit() - off;
			if (excess != 0) {
				dsptch.getLogger().info("DNS-Server rejecting packet="+dnspkt.getQID()+" due to excess bytes="+excess
					+" - query="+ResolverDNS.getQTYPE(handlers.qry_qtype)+"/"+handlers.qry_qname+" from "+remote_addr);
				sendResponse(dnspkt.getQID(), handlers.qry_qtype, handlers.qry_qname, PacketDNS.RCODE_BADFMT, is_auth, dnspkt.recursionDesired(),
						null, null, null, remote_addr, tcp);
				return;
			}
		}

		//the question appears valid, so answer it
		try {
			responder.dnsResolveQuestion(dnspkt.getQID(), handlers.qry_qtype, handlers.qry_qname, dnspkt.recursionDesired(), remote_addr, tcp);
		} catch (Throwable ex) {
			dsptch.getLogger().log(Logger.LEVEL.ERR, ex, true, "DNS-Server failed to handle DNS query="+dnspkt.getQID()
				+"/"+handlers.qry_qtype+"/"+handlers.qry_qname+" from "+remote_addr);
			if (dsptch.isActive()) {
				sendResponse(dnspkt.getQID(), handlers.qry_qtype, handlers.qry_qname, PacketDNS.RCODE_SRVFAIL, is_auth, dnspkt.recursionDesired(),
						null, null, null, remote_addr, tcp);
			}
		}
	}

	public boolean sendResponse(int qid, byte qtype, ByteChars qname, int rcode,
			boolean isauth, boolean recursion_desired,
			ResourceData[] ans, ResourceData[] auth, ResourceData[] info,
			java.net.InetSocketAddress remote_addr, Object tcpconn) throws java.io.IOException
	{
		boolean is_tcp = (tcpconn != null);
		dnspkt.resetEncoder(is_tcp, COMPRESS_NAMES);
		dnspkt.setResponse();
		dnspkt.opcode(PacketDNS.OPCODE_QRY);
		dnspkt.rcode(rcode);
		if (recursion_offered) dnspkt.setRecursionAvailable();
		if (isauth) dnspkt.setAuth();
		if (recursion_desired) dnspkt.setRecursionDesired();
		int anscnt = (ans==null?0:ans.length);
		int authcnt = (auth==null?0:auth.length);
		int infocnt = (info==null?0:info.length);
		dnspkt.setHeader(qid, 1, anscnt, authcnt, infocnt);
		int off = dnspkt.encodeHeader();
		off = dnspkt.encodeQuestion(off, qtype, qname);
		int off_ans = off;
		if (ans != null) off = dnspkt.encodeSection(off, PacketDNS.SECT_ANSWERS, ans, null);
		if (auth != null) off = dnspkt.encodeSection(off, PacketDNS.SECT_AUTH, auth, null);
		if (info != null) off = dnspkt.encodeSection(off, PacketDNS.SECT_INFO, info, null);
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
					+" with answer="+anscnt+"/"+authcnt+"/"+infocnt+"/rcode="+rcode+" to "+remote_addr
					+" - size="+niobuf.limit()+", auth="+isauth+(dnspkt.isTruncated()?"/truncated":""));
		}
		if (is_tcp) {
			((TransportTCP)tcpconn).sendResponse(niobuf);
		} else {
			transport_udp.sendResponse(niobuf, remote_addr);
		}
		return dnspkt.isTruncated();
	}

	
	// Move the override methods into an inner class, purely so as not to be externally visible
	private static final class Handlers
		implements PacketDNS.MessageCallback
	{
		private final ServerDNS srvr;
		private final Dispatcher dsptch;

		//current question - these are only valid within the scope of a single queryReceived() call
		byte qry_qtype;
		ByteChars qry_qname;

		Handlers(ServerDNS s) {srvr=s; dsptch=srvr.getDispatcher();}

		@Override
		public long getSystemTime() {return dsptch.getSystemTime();}

		@Override
		public boolean handleMessageQuestion(int qid, int qnum, int qcnt, byte qt, byte qclass, ByteChars qn, java.net.InetSocketAddress remote_addr) {
			if (qclass != PacketDNS.QCLASS_INET) {
				dsptch.getLogger().info("DNS-Server rejecting class="+qclass+" for query="+qt+"/"+qn+" from "+remote_addr);
				return false;
			}
			if (qnum != 0) {
				dsptch.getLogger().info("DNS-Server rejecting excess question #"+qnum+"/"+qcnt+" = "+qt+"/"+qn+" from "+remote_addr);
				return false;
			}
			qry_qtype = qt;
			qry_qname = new ByteChars(qn);
			return true;
		}

		@Override
		public boolean handleMessageRR(int qid, int sectiontype, int rrnum, int rrcnt, ByteChars rrname, ResourceData rr, java.net.InetSocketAddress remote_addr) {
			throw new UnsupportedOperationException(getClass().getPackage().getName()+" package does not expect to receive DNS responses - "+rr);
		}
	}
}