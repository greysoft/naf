/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.integration;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.IP;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger;
import com.grey.base.collections.HashedMap;
import com.grey.base.collections.HashedMapIntKey;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.engine.PacketDNS;
import com.grey.naf.dns.resolver.engine.ResourceData;
import com.grey.naf.dns.server.DnsServerConfig;
import com.grey.naf.reactor.Dispatcher;

import java.util.HashSet;

class TestServerDNS
	implements com.grey.naf.dns.server.ServerDNS.DNSQuestionResolver
{
	private static final String TMTDOMAIN = "simulate-timeout.net";
	private static final String TRUNCDOMAIN = "forced-truncation.net";

	private final Dispatcher dsptch;
	private final com.grey.naf.dns.server.ServerDNS srvr;
	private final HashedMapIntKey<HashedMap<String,ResourceData[][]>> answers= new HashedMapIntKey<>();
	private final HashSet<String> unused_answers = new HashSet<String>();
	private final int total_answers;
	private final java.net.DatagramSocket rawsock; //used for sending custom responses that bypass our DNS API
	private int tmtdomain_qrycnt;

	public int getPort() {return srvr.getLocalPort();}
	@Override public boolean dnsRecursionAvailable() {return false;}

	public TestServerDNS(ApplicationContextNAF appctx) throws java.io.IOException {
		populateAnswers();
		total_answers = unused_answers.size();
		com.grey.naf.reactor.config.DispatcherConfig def = new com.grey.naf.reactor.config.DispatcherConfig.Builder()
				.withName("Mock-DNS-Server")
				.withSurviveHandlers(false)
				.build();
		Logger log = com.grey.logging.Factory.getLoggerNoEx("no-such-initlogger");
		dsptch = Dispatcher.create(appctx, def, log);

		DnsServerConfig.Builder bldr = new DnsServerConfig.Builder();
		bldr.getListenerConfig().withPort(0).withInterface("127.0.0.1");
		srvr = new com.grey.naf.dns.server.ServerDNS(dsptch, this, bldr.build());
		dsptch.loadRunnable(srvr);

		if (srvr.getLocalPort() == PacketDNS.INETPORT) throw new IllegalStateException("DNS server not on ephemeral port");
		if (IP.convertIP(srvr.getLocalIP()) != IP.IP_LOCALHOST) throw new IllegalStateException("DNS server not on localhost");
		rawsock = new java.net.DatagramSocket();
	}

	public void start() throws java.io.IOException {
		dsptch.start(); //launches server in another thread
	}

	public void stop() {
		dsptch.stop();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND*10L, true);
		if (stopsts != Dispatcher.STOPSTATUS.STOPPED) throw new IllegalStateException("Failed to stop Server thread - "+stopsts);

		if (unused_answers.size() != 0) {
			java.util.ArrayList<String> lst = new java.util.ArrayList<String>(unused_answers);
			java.util.Collections.sort(lst);
			System.out.println("DNS Mock Server has "+unused_answers.size()+"/"+total_answers+" unused answers - "+lst);
		}
	}

	@Override
	public void dnsResolveQuestion(int qid, byte qtype, ByteChars qn, boolean recursion_desired,
		java.net.InetSocketAddress remote_addr, Object cbparam) throws java.io.IOException
	{
		unused_answers.remove(ResolverDNS.getQTYPE(qtype)+"="+qn);
		if (qtype == ResolverDNS.QTYPE_A && StringOps.sameSeq(TMTDOMAIN, qn)) {
			if (tmtdomain_qrycnt++ % 2 == 0) {
				//discard every second query, starting with the first - but do so by exercising discard of invalid responses
				byte[] buf = new byte[PacketDNS.PKTHDRSIZ-1];
				sendRaw(buf, buf.length, remote_addr);
				return;
			}
		}
		ResourceData[][] answer = null;
		HashedMap<String,ResourceData[][]> map = answers.get(qtype);
		if (map != null) answer = map.get(qn.toString());

		if (answer == null) {
			srvr.sendResponse(qid, qtype, qn, PacketDNS.RCODE_NXDOM, true, recursion_desired, null, null, null,
					remote_addr, cbparam);
		} else {
			ResourceData[] auth = (answer.length > 1 ? answer[1] : null);
			ResourceData[] info = (answer.length > 2 ? answer[2] : null);
			boolean truncated = srvr.sendResponse(qid, qtype, qn, PacketDNS.RCODE_OK, true, recursion_desired,
					answer[0], auth, info, remote_addr, cbparam);
			if (!truncated && cbparam == null && StringOps.sameSeq(TRUNCDOMAIN, qn)) {
				//sanity check failed on our test data - answer not long enough to force truncation
				stop();
				throw new IllegalStateException("Truncation didn't occur for "+TRUNCDOMAIN+" - Increase results data!");
			}
		}
	}

	private void sendRaw(byte[] buf, int len, java.net.InetSocketAddress remote_addr) throws java.io.IOException {
		java.net.DatagramPacket pkt = new java.net.DatagramPacket(buf, len, remote_addr);
		rawsock.send(pkt);
	}

	private void store(int rrtype, String name, ResourceData[][] data) {
		HashedMap<String,ResourceData[][]> map = answers.get(rrtype);
		if (map == null) {
			map = new HashedMap<String,ResourceData[][]>();
			answers.put(rrtype, map);
		}
		map.put(name, data);
		unused_answers.add(ResolverDNS.getQTYPE(rrtype)+"="+name);
	}

	private void storeA(String name, ResourceData[][] data) {store(ResolverDNS.QTYPE_A, name, data);}
	private void storeNS(String name, ResourceData[][] data) {store(ResolverDNS.QTYPE_NS, name, data);}
	private void storeSOA(String name, ResourceData[][] data) {store(ResolverDNS.QTYPE_SOA, name, data);}
	private void storeMX(String name, ResourceData[][] data) {store(ResolverDNS.QTYPE_MX, name, data);}
	private void storeSRV(String name, ResourceData[][] data) {store(ResolverDNS.QTYPE_SRV, name, data);}
	private void storeTXT(String name, ResourceData[][] data) {store(ResolverDNS.QTYPE_TXT, name, data);}
	private void storeAAAA(String name, ResourceData[][] data) {store(ResolverDNS.QTYPE_AAAA, name, data);}

	private void storePTR(String dotted_ip, ResourceData[][] data) {
		int ip = IP.convertDottedIP(dotted_ip);
		String qname = IP.displayArpaDomain(ip).toString();
		store(ResolverDNS.QTYPE_PTR, qname, data);
	}

	private static ResourceData rrCreateA(String hostname, String ip, int ttlsecs) {
		return new ResourceData.RR_A(new ByteChars(hostname), ip==null?0:IP.convertDottedIP(ip), ttl2expiry(ttlsecs));
	}

	private static ResourceData rrCreatePTR(String hostname, String ip, int ttlsecs) {
		return new ResourceData.RR_PTR(new ByteChars(hostname), ip==null?0:IP.convertDottedIP(ip), ttl2expiry(ttlsecs));
	}

	private static ResourceData rrCreateNS(String domain, String server, int ttlsecs) {
		return new ResourceData.RR_NS(new ByteChars(domain), new ByteChars(server), ttl2expiry(ttlsecs));
	}

	private static ResourceData rrCreateMX(String domain, String relay, int pref, int ttlsecs) {
		return new ResourceData.RR_MX(new ByteChars(domain), new ByteChars(relay), pref, ttl2expiry(ttlsecs));
	}

	private static ResourceData rrCreateSRV(String domain, int ttlsecs, String target, int pri, int weight, int port) {
		return new ResourceData.RR_SRV(new ByteChars(domain), ttl2expiry(ttlsecs),
				new ByteChars(target), pri, weight, port);
	}

	private static ResourceData rrCreateSOA(String domain, int ttlsecs, String mname, String rname,
			int serial, int refresh, int retry, int expire, int minttl) {
		return new ResourceData.RR_SOA(new ByteChars(domain), ttl2expiry(ttlsecs),
				new ByteChars(mname), new ByteChars(rname), serial, refresh, retry, expire, minttl);
	}

	private static ResourceData.RR_TXT rrCreateTXT(String name, int ttlsecs, String[] recs) {
		return new ResourceData.RR_TXT(new ByteChars(name), ttl2expiry(ttlsecs),
				recs==null ? null : java.util.Arrays.asList(recs));
	}

	private static ResourceData rrCreateAAAA(String hostname, String ip, int ttlsecs) {
		byte[] addr = new byte[IP.IPV6ADDR_OCTETS];
		String[] hexgroups = ip.split(":");
		int off = 0;
		for (int idx = 0; idx != hexgroups.length; idx++) {
			int grpval = Integer.parseInt(hexgroups[idx], 16);
			addr[off++] = (byte)(grpval >> 8);
			addr[off++] = (byte)(grpval & 0xFF);
		}
		return new ResourceData.RR_AAAA(new ByteChars(hostname), addr, ttl2expiry(ttlsecs));
	}

	private static ResourceData rrCreateCNAME(String alias, String hostname, int ttlsecs) {
		return new ResourceData.RR_CNAME(new ByteChars(alias), new ByteChars(hostname), ttl2expiry(ttlsecs));
	}

	private static long ttl2expiry(int secs) {
		return System.currentTimeMillis()+(secs * 1000L);
	}

	private void populateAnswers()
	{
		//primary queries
		storeA("www.google.com", new ResourceData[][]{
			{rrCreateA("www.google.com", "62.253.72.173", 5),
				rrCreateA("www.google.com", "62.253.72.177", 500)},
			{rrCreateNS("google.com", "ns1.google.com", 40000),
				rrCreateNS("google.com", "ns2.google.com", 40000)},
			{rrCreateA("ns1.google.com", "216.239.32.10", 30000),
				rrCreateA("ns2.google.com", "216.239.34.10", 30000)}});
		storeA("mail.google.com", new ResourceData[][]{
			{rrCreateCNAME("www.google.com", "62.253.72.173", 5),
				rrCreateA("googlemail.l.google.com", "216.58.208.69", 500)},
			{rrCreateNS("google.com", "ns1.google.com", 40000)},
			{rrCreateA("ns1.google.com", "216.239.32.10", 30000)}});
		storePTR("158.152.1.65", new ResourceData[][]{ //this response has no Info RRs
			{rrCreatePTR("ns0.demon.co.uk", "158.152.1.65", 600)},
			{rrCreateNS("152.158.in-addr.arpa", "ns1.demon.co.uk", 40000)}});
		storeNS("net", new ResourceData[][]{ //this response has no Auth RRs
			{rrCreateNS("net", "a.gtld-servers.net", 24000),
				rrCreateNS("net", "b.gtld-servers.net", 24000),
				rrCreateNS("net", "c.gtld-servers.net", 24000),
				rrCreateNS("net", "192.31.80.30", 24000),
				rrCreateNS("net", "e.gtld-servers.net", 24000)},
			null,
			{rrCreateA("b.gtld-servers.net", "192.33.14.30", 99000),
				rrCreateA("a.gtld-servers.net", "192.5.6.30", 99000)}});
		storeMX("ibm.com", new ResourceData[][]{
			{rrCreateMX("ibm.com", "e11.ny.us.ibm.com", 10, 250),
				rrCreateMX("ibm.com", "e12.ny.us.ibm.com", 10, 250),
				rrCreateMX("ibm.com", "e13.ny.us.ibm.com", 10, 250),
				rrCreateMX("ibm.com", "e14.ny.us.ibm.com", 10, 250)},
			{rrCreateNS("ibm.com", "ns1-99.akam.net", 99000),
				rrCreateNS("ibm.com", "eur5.akam.net", 99000)},
			{rrCreateA("ns1-99.akam.net", "193.108.91.99", 51000),
				rrCreateA("eur5.akam.net", "23.74.25.64", 51000),
				rrCreateA("e11.ny.us.ibm.com", "129.33.205.201", 3300),
				rrCreateA("e12.ny.us.ibm.com", "129.33.205.202", 3300)}});
		storeSOA("google.com", new ResourceData[][]{
			{rrCreateSOA("google.com", 60, "ns4.google.com", "dns-admin.google.com", 107620502, 900, 900, 1800, 60)},
			{rrCreateNS("google.com", "ns1.google.com", 40000),
				rrCreateNS("google.com", "ns2.google.com", 40000)},
			{rrCreateA("ns1.google.com", "216.239.32.10", 30000),
				rrCreateA("ns2.google.com", "216.239.34.10", 30000)}});
		storeSRV("_xmpp-client._tcp.google.com", new ResourceData[][]{
			{rrCreateSRV("_xmpp-client._tcp.google.com", 900, "alt2.xmpp.l.google.com", 20, 0, 5222),
				rrCreateSRV("_xmpp-client._tcp.google.com", 900, "alt4.xmpp.l.google.com", 20, 0, 5222),
				rrCreateSRV("_xmpp-client._tcp.google.com", 900, "xmpp.l.google.com", 20, 0, 5222)},
			{rrCreateNS("google.com", "ns1.google.com", 40000),
				rrCreateNS("google.com", "ns2.google.com", 40000)},
			{rrCreateA("ns1.google.com", "216.239.32.10", 30000),
				rrCreateA("ns2.google.com", "216.239.34.10", 30000),
				rrCreateA("alt2.xmpp.l.google.com", "74.125.141.125", 300),
				rrCreateA("alt4.xmpp.l.google.com", "74.125.141.125", 300),
				rrCreateA("xmpp.l.google.com", "74.125.141.125", 300)}});
		storeTXT("google.com", new ResourceData[][]{
			{rrCreateTXT("google.com", 5000, new String[]{"Text Rec 1", "Text Rec 2"})}});
		storeAAAA("ns1-99.akam.net", new ResourceData[][]{
			{rrCreateAAAA("ns1-99.akam.net", "2600:1401:2:0:0:0:0:63", 107767)},
			{rrCreateNS("akam.net", "a13-67.akam.net", 115000)},
			{rrCreateA("a13-67.akam.net", "2.22.230.67", 115000)}});
		//follow-on queries and supporting data
		storeA("e13.ny.us.ibm.com", new ResourceData[][]{
			{rrCreateA("e13.ny.us.ibm.com", "129.33.205.203", 260)}});
		storeA("c.gtld-servers.net", new ResourceData[][]{
			{rrCreateA("c.gtld-servers.net", "192.26.92.30", 260)}});
		storeNS("ibm.com", new ResourceData[][]{
			null,
			{rrCreateNS("ibm.com", "ns1-99.akam.net", 99000),
				rrCreateNS("ibm.com", "eur5.akam.net", 99000)},
			{rrCreateA("ns1-99.akam.net", "193.108.91.99", 51000),
				rrCreateA("eur5.akam.net", "23.74.25.64", 51000)}});
		//edge-case tests
		storeNS("no-resolvable-nameservers.net", new ResourceData[][]{
			{rrCreateNS("no-resolvable-nameservers.net", "nameserver1.net", 24000),
				rrCreateNS("no-resolvable-nameservers.net", "nameserver2.net", 24000),
				rrCreateNS("no-resolvable-nameservers.net", "nameserver3.net", 24000)},
			null, null});
		storeMX("no-resolvable-mailservers.net", new ResourceData[][]{
			{rrCreateMX("no-resolvable-mailservers.net", "mailserver1.net", 10, 24000),
				rrCreateMX("no-resolvable-mailservers.net", "mailserver2.net", 20, 24000)},
			null, null});
		storeA(TMTDOMAIN, new ResourceData[][]{
			{rrCreateA(TMTDOMAIN, "192.168.201.1", 60)},
			null, null});

		// create a response intended to be too large enough to force UDP truncation, and a TCP retry
		String domnam = TRUNCDOMAIN;
		String srvdom = "truncnameservers.net";
		ResourceData[] ans = new ResourceData[8];
		ResourceData[] auth = new ResourceData[9];
		ResourceData[] info = new ResourceData[10];
		for (int idx = 0; idx != ans.length; idx++) {
			ans[idx] = rrCreateNS(domnam, "nameserver"+idx+"."+srvdom, 24000);
		}
		for (int idx = 0; idx != auth.length; idx++) {
			auth[idx] = rrCreateNS(srvdom, "nameserver"+idx+".truncnameservers2.net", 24000);
		}
		for (int idx = 0; idx != info.length; idx++) {
			info[idx] = rrCreateA("nameserver"+idx+"."+srvdom, "192.33.14."+idx, 24000);
		}
		ResourceData[][] answer = new ResourceData[][]{ans, auth, info};
		storeNS(domnam, answer);
	}
}