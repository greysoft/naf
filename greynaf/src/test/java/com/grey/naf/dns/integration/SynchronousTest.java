/*
 * Copyright 2014-2019 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.integration;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.DispatcherTest;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.NAFConfig;
import com.grey.naf.dns.ResolverAnswer;
import com.grey.naf.dns.ResolverDNS;
import com.grey.naf.dns.client.DNSClient;

public class SynchronousTest
	extends ResolverTester
{
	private static final String rootdir = DispatcherTest.initPaths(SynchronousTest.class);
	private static final String DNAME = "TEST-SyncDNS";

	private DNSClient resolver;

	@org.junit.Before
	public void beforeTest() throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		FileOps.ensureDirExists(rootdir);
	}

	@org.junit.After
	public void afterTest() throws java.io.IOException
	{
		if (resolver != null) shutdown();
	}

	@org.junit.Test
	public void testResolver() throws java.io.IOException
	{
		init("std", true, true);
		String hostname = "101.25.32.1"; //any old IP
		int ip = IP.convertDottedIP(hostname);
		ResolverAnswer answer = resolver.resolveHostname(hostname);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, ip, answer);

		hostname = "localhost";
		answer = resolver.resolveHostname(hostname);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, IP.IP_LOCALHOST, answer);

		answer = resolver.resolveIP(IP.IP_LOCALHOST);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_PTR, "localhost", answer);

		hostname = "bad.domain..";
		answer = resolver.resolveHostname(hostname);
		logger.info("UTEST: Answer for bad syntax: "+answer);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.BADNAME, answer.result);
		org.junit.Assert.assertEquals(0, answer.size());
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_A, answer.qtype);
		org.junit.Assert.assertTrue(StringOps.sameSeq(hostname, answer.qname));

		long time1 = System.nanoTime();
		answer = resolver.resolveHostname(queryTargetA);
		long time_a = System.nanoTime() - time1;
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, 0, answer);
		answer = resolver.resolveHostname(queryTargetCNAME);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, 0, answer);
		ip = IP.convertDottedIP(queryTargetPTR);
		answer = resolver.resolveIP(ip);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_PTR, null, answer);
		answer = resolver.resolveNameServer(queryTargetNS);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_NS, 0, answer);
		answer = resolver.resolveMailDomain(queryTargetMX);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_MX, 0, answer);
		answer = resolver.resolveSOA(queryTargetSOA);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_SOA, 0, answer);
		answer = resolver.resolveSRV(queryTargetSRV);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_SRV, 0, answer);
		answer = resolver.resolveTXT(queryTargetTXT);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_TXT, 0, answer);
		answer = resolver.resolveAAAA(queryTargetAAAA);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_AAAA, 0, answer);

		answer = resolver.resolveHostname("no-such-host.nosuchdomain1");
		assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_A, 0, answer);
		answer = resolver.resolveNameServer("nosuchdomain2a.nosuchdomain2b");
		assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_NS, 0, answer);
		answer = resolver.resolveMailDomain("nosuchdomain3");
		assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_MX, 0, answer);

		if (mockserver != null) {
			answer = resolver.resolveNameServer("forced-truncation.net"); //must not be the last query
			assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_NS, 0, answer);
			answer = resolver.resolveNameServer("no-resolvable-nameservers.net");
			assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_NS, 0, answer);
			answer = resolver.resolveMailDomain("no-resolvable-mailservers.net");
			assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_MX, 0, answer);
			time1 = System.nanoTime();
			answer = resolver.resolveHostname("simulate-timeout.net");
			long time_a2 = System.nanoTime() - time1;
			assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, 0, answer);
			org.junit.Assert.assertTrue(time_a2 > time_a);
			org.junit.Assert.assertTrue(time_a2 > 18L*100L*1000L*1000L); //1.8 secs, allowing margin for 2-sec timeout
		}
	}

	@org.junit.Test
	public void testDelegationDetection() throws java.io.IOException
	{
		org.junit.Assume.assumeTrue(HAVE_DNS_SERVICE || USE_REAL_DNS);
		init("delegation", false, false);
		ResolverAnswer answer = resolver.resolveNameServer("ny.us.ibm.com"); //there are no more delegations under ibm.com
		assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_NS, 0, answer);
		answer = resolver.resolveHostname("e31.co.us.ibm.com");
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, 0, answer);
		answer = resolver.resolveNameServer("e31.co.us.ibm.com");
		assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_NS, 0, answer);
		answer = resolver.resolveMailDomain("jcom.home.ne.jp"); //delegation at home, after gap at ne.jp
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_MX, 0, answer);
		answer = resolver.resolveMailDomain("mipunto.com"); //there are delegation gaps in the MX hostnames
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_MX, 0, answer);
		answer = resolver.resolveHostname("no-such-host.home.ne.jp");
		assertAnswer(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_A, 0, answer);
	}

	// An NS query on dns.pipex.net returns some nested nameservers, with no glue records in Additional-Info
	@org.junit.Ignore //May 2018: This test has become too flaky, DNS queries time out
	@org.junit.Test
	public void testDeadlockAvoidance() throws java.io.IOException
	{
		org.junit.Assume.assumeTrue(HAVE_DNS_SERVICE || USE_REAL_DNS);
		init("avoidloop", false, false);
		ResolverAnswer answer = resolver.resolveNameServer("dns.pipex.net");
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_NS, 0, answer);
	}

	// This exercises the Auth-Redirect hack.
	// tsa.ac.za would exercise the endless redirect-loop scenario and return ERROR.
	// The domains that return such responses tend to be flaky by their nature, so don't include in th regular test runs,
	// but uncomment to troubleshoot.
	// Update 09-Oct-2015: anglia.nl has gone bad/worse, as it nominates mx=anglianetwork.org which is an NXDOMAIN
	//@org.junit.Test
	public void testAuthorityRedirect() throws java.io.IOException
	{
		org.junit.Assume.assumeTrue(HAVE_DNS_SERVICE || USE_REAL_DNS);
		init("authredirect", false, false);
		ResolverAnswer answer = resolver.resolveMailDomain("415digital.com"); //comes with glue records
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_MX, 0, answer);
		answer = resolver.resolveMailDomain("anglia.nl"); //response has no glue
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_MX, 0, answer);
	}

	private void init(String tag, boolean recursive, boolean use_mockserver) throws java.io.IOException
	{
		String nafcfg_path = createConfig(tag, recursive, use_mockserver);
		NAFConfig nafcfg = NAFConfig.load(nafcfg_path);
		ApplicationContextNAF appctx = ApplicationContextNAF.create(null, nafcfg);
		resolver = new DNSClient(null, appctx, DNAME+"-"+tag, false, logger);
		resolver.init();
	}

	private void shutdown() throws java.io.IOException
	{
		Dispatcher d = (Dispatcher)DynLoader.getField(resolver, "dsptch");
		Dispatcher.STOPSTATUS stopsts = resolver.shutdown();
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(d.completedOK());
		stopsts = resolver.shutdown(); //verify excess calls return same result
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		validateFinalState(d.getResolverDNS());
	}

	private String createConfig(String tag, boolean recursive, boolean use_mockserver) throws java.io.IOException
	{
		int maxrr_ns = 0;
		int maxrr_mx = 0;
		StringBuilder sb = new StringBuilder();
		sb.append("<naf><baseport>"+NAFConfig.RSVPORT_ANON+"</baseport>");
		sb.append("<dnsresolver");
		sb.append(" recursive=\""+recursive+"\" exitdump=\"Y\">");
		sb.append("<cache_mx maxrr=\""+maxrr_mx+"\"/>");
		sb.append("<cache_ns maxrr=\""+maxrr_ns+"\"/>");
		if (dnsservers != null) sb.append("<localservers>").append(dnsservers).append("</localservers>");
		if (use_mockserver && mockserver != null) {
			is_mockserver = true;
			sb.append("<interceptor host=\"127.0.0.1\" port=\""+mockserver.getPort()+"\"/>");
		}
		if (is_mockserver) {
			sb.append("<retry timeout=\"2s\" timeout_tcp=\"2s\" max=\"1\" backoff=\"200\"/>");
		} else {
			sb.append("<retry timeout=\"15s\" timeout_tcp=\"10s\" max=\"2\" backoff=\"200\"/>");
		}
		sb.append("</dnsresolver>");
		sb.append("<dispatchers><dispatcher name=\""+DNAME+"-"+tag+"\" survive_handlers=\"N\"/>");
		sb.append("</dispatchers>");
		sb.append("</naf>");
		String pthnam = rootdir+"/naf.xml";
		FileOps.writeTextFile(pthnam, sb.toString());
		return pthnam;
	}
}