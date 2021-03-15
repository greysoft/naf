/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.integration;

import com.grey.base.utils.StringOps;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;
import com.grey.naf.reactor.Dispatcher;

import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.DispatcherDef;
import com.grey.naf.NAFConfig;
import com.grey.naf.TestUtils;
import com.grey.naf.dns.client.DNSClient;
import com.grey.naf.dns.resolver.ResolverAnswer;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;

import org.junit.Assert;

public class SynchronousTest
	extends ResolverTester
{
	private static final String rootdir = TestUtils.initPaths(SynchronousTest.class);
	private static final String DNAME = "TEST-SyncDNS";

	private DNSClient resolver;
	private Dispatcher dsptch; //this is the Dispatcher the DNSClient references
	private Dispatcher dsptchOther; //the other Dispatcher for a pair of DistributedResolvers, may be the master

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
	public void testResolverLocal() throws java.io.IOException
	{
		init("std", true, true, true);
		testResolver();
	}

	@org.junit.Test
	public void testResolverRemote() throws java.io.IOException
	{
		init("std", false, true, true);
		testResolver();
	}

	private void testResolver() throws java.io.IOException
	{
		init("std", true, true, true);
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
		init("delegation", true, false, false);
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
		init("avoidloop", true, false, false);
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
		init("authredirect", true, false, false);
		ResolverAnswer answer = resolver.resolveMailDomain("415digital.com"); //comes with glue records
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_MX, 0, answer);
		answer = resolver.resolveMailDomain("anglia.nl"); //response has no glue
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_MX, 0, answer);
	}

	private void init(String tag, boolean master, boolean recursive, boolean use_mockserver) throws java.io.IOException
	{
		String nafcfg_path = createConfig(tag, recursive, use_mockserver);
		ApplicationContextNAF appctx = TestUtils.createApplicationContext(null, nafcfg_path, true);
		NAFConfig nafcfg = appctx.getConfig();
		String d1name = DNAME+"-"+tag;
		String d2name = d1name;
		if (master) {
			d1name += "_master";
			d2name += "_slave";
		} else {
			d1name += "_slave";
			d2name += "_master";
		}
		DispatcherDef def = new DispatcherDef.Builder()
				.withName(d1name)
				.withSurviveHandlers(false)
				.build();
		ResolverConfig rcfg = new ResolverConfig.Builder()
				.withXmlConfig(nafcfg.getNode("dnsresolver"))
				.build();
		dsptch = Dispatcher.create(appctx, def, logger);
		def = new DispatcherDef.Builder(def).withName(d2name).build();
		dsptchOther = Dispatcher.create(appctx, def, logger);
		ResolverDNS r1;
		if (master) {
			r1 = ResolverDNS.create(dsptch, rcfg);
			ResolverDNS r2 = ResolverDNS.create(dsptchOther, rcfg);
			Assert.assertSame(dsptch, r1.getDispatcher());
			Assert.assertSame(dsptch, r1.getMasterDispatcher());
			Assert.assertSame(dsptchOther, r2.getDispatcher());
			Assert.assertSame(dsptch, r2.getMasterDispatcher());
		} else {
			ResolverDNS r2 = ResolverDNS.create(dsptchOther, rcfg); //this one becomes the DistributedResolver master
			r1 = ResolverDNS.create(dsptch, rcfg);
			Assert.assertSame(dsptch, r1.getDispatcher());
			Assert.assertSame(dsptchOther, r1.getMasterDispatcher());
			Assert.assertSame(dsptchOther, r2.getDispatcher());
			Assert.assertSame(dsptchOther, r2.getMasterDispatcher());
		}
		resolver = new DNSClient(r1, null);
		dsptch.start();
		dsptchOther.start();
	}

	private void shutdown() throws java.io.IOException
	{
		resolver.shutdown();
		dsptch.stop();
		dsptchOther.stop();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(5_000, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		ResolverDNS r = dsptch.getNamedItem(ResolverDNS.class.getName(), null);
		validateFinalState(r);
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
		sb.append("</naf>");
		String pthnam = rootdir+"/naf.xml";
		FileOps.writeTextFile(pthnam, sb.toString());
		return pthnam;
	}
}