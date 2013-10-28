/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.config.SysProps;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;
import com.grey.base.utils.StringOps;
import com.grey.naf.reactor.Dispatcher;

public class ResolverTest
	implements Resolver.Client
{
	private static final String rootdir = com.grey.naf.reactor.DispatcherTest.initPaths(ResolverTest.class);
	private static final com.grey.logging.Logger junklogger = com.grey.logging.Factory.getLoggerNoEx("no-such-logger");

	private static final String SYSPROP_SKIP = "greynaf.test.skipdns";
	private static final boolean skiptests = SysProps.get(SYSPROP_SKIP, true); //test setup is too specific to my Dev environment
	private static final String dnsservers = SysProps.get("greynaf.dns.test.servers");
	private static final String queryTargetA = SysProps.get("greynaf.dns.test.targetA", "www.google.com");
	private static final String queryTargetMX = SysProps.get("greynaf.dns.test.targetMX", "ibm.com"); //triggers QTYPE_A follow-on
	private static final String queryTargetPTR = SysProps.get("greynaf.dns.test.targetPTR", "192.168.101.12");
	private static final String NoSuchDom = "nonsuchdomain6812.com";

	static {
		if (skiptests) {
			System.out.println("WARNING: Skipping all DNS tests due to sysprop "+SYSPROP_SKIP);
		} else {
			if (queryTargetA.equals("-")) System.out.println("WARNING: Skipping type-A lookup");
			if (queryTargetMX.equals("-")) System.out.println("WARNING: Skipping type-MX lookup");
			if (queryTargetPTR.equals("-")) System.out.println("WARNING: Skipping type-PTR lookup");
		}
	}
	private static final int CFG_TCP = 1 << 0;
	private static final int CFG_FALLBACKA = 1 << 1;

	private int dnscallbacks;
	private int dnsrequests;
	private int opencallbacks; //detects exceptions in dnsResolved()

	// This does some generic Resolver-interface tests, as well as testing the specific embedded Resolver
	@org.junit.Test
	public void testEmbedded() throws com.grey.base.GreyException, java.io.IOException
	{
		if (skiptests) return;
		com.grey.naf.Config nafcfg = setConfig(null, 0);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "utest_Embedded";
		def.hasDNS = true;
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, nafcfg, junklogger);
		org.junit.Assert.assertEquals(com.grey.naf.dns.embedded.EmbeddedResolver.class, dsptch.dnsresolv.getClass());
		org.junit.Assert.assertNull(com.grey.naf.nafman.Primary.get());

		ByteChars bc = new ByteChars("101.25.32.1");
		int ip = IP.convertDottedIP(bc);
		Answer answer = dsptch.dnsresolv.resolveHostname(bc, this, null, 0);
		assertAnswer(dsptch, Answer.STATUS.OK, Resolver.QTYPE_A, ip, answer);

		bc.set("localhost");
		answer = dsptch.dnsresolv.resolveHostname(bc, this, null, 0);
		assertAnswer(dsptch, Answer.STATUS.OK, Resolver.QTYPE_A, IP.IP_LOCALHOST, answer);

		answer = dsptch.dnsresolv.resolveIP(IP.IP_LOCALHOST, this, null, 0);
		assertAnswer(dsptch, Answer.STATUS.OK, Resolver.QTYPE_PTR, "localhost", answer);

		bc.set("legalsyntax.no.such.domain");
		answer = dsptch.dnsresolv.resolveHostname(bc, this, null, Resolver.FLAG_SYNTAXONLY);
		dsptch.logger.info("UTEST: Answer for syntax-only: "+answer);
		org.junit.Assert.assertEquals(Answer.STATUS.OK, answer.result);
		org.junit.Assert.assertEquals(0, answer.rrdata.size());
		org.junit.Assert.assertEquals(Resolver.QTYPE_A, answer.qtype);
		org.junit.Assert.assertTrue(StringOps.sameSeq(bc, answer.qname));

		bc.set("noncached.no.such.domain2");
		answer = dsptch.dnsresolv.resolveHostname(bc, this, null, Resolver.FLAG_NOQRY);
		dsptch.logger.info("UTEST: Answer for query-only: "+answer);
		org.junit.Assert.assertEquals(Answer.STATUS.NODOMAIN, answer.result);
		org.junit.Assert.assertEquals(0, answer.rrdata.size());
		org.junit.Assert.assertEquals(Resolver.QTYPE_A, answer.qtype);
		org.junit.Assert.assertTrue(StringOps.sameSeq(bc, answer.qname));
	
		ip = IP.convertDottedIP("192.168.250.1");
		answer = dsptch.dnsresolv.resolveIP(ip, this, null, Resolver.FLAG_NOQRY);
		dsptch.logger.info("UTEST: Answer for IP-query-only: "+answer);
		org.junit.Assert.assertEquals(Answer.STATUS.NODOMAIN, answer.result);
		org.junit.Assert.assertEquals(0, answer.rrdata.size());
		org.junit.Assert.assertEquals(Resolver.QTYPE_PTR, answer.qtype);
		org.junit.Assert.assertEquals(ip, answer.qip);

		bc.set("bad.domain.");
		answer = dsptch.dnsresolv.resolveHostname(bc, this, null, 0);
		dsptch.logger.info("UTEST: Answer for bad syntax: "+answer);
		org.junit.Assert.assertEquals(Answer.STATUS.BADNAME, answer.result);
		org.junit.Assert.assertEquals(0, answer.rrdata.size());
		org.junit.Assert.assertEquals(Resolver.QTYPE_A, answer.qtype);
		org.junit.Assert.assertTrue(StringOps.sameSeq(bc, answer.qname));

		exec(dsptch, "embedded", false, null, false);
	}

	@org.junit.Test
	public void testDistributed() throws com.grey.base.GreyException, java.io.IOException
	{
		if (skiptests) return;
		com.grey.naf.Config nafcfg = setConfig("com.grey.naf.dns.distributedresolver.Client", 0);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "utest_DistribLocal";
		def.hasDNS = true;
		def.hasNafman = true;
		def.surviveHandlers = false;
		def.surviveDownstream = false;
		Dispatcher d1 = Dispatcher.create(def, nafcfg, junklogger);
		org.junit.Assert.assertEquals(com.grey.naf.dns.distributedresolver.Client.class, d1.dnsresolv.getClass());
		def.name = "utest_DistribRemote";
		Dispatcher d2 = Dispatcher.create(def, nafcfg, junklogger);
		org.junit.Assert.assertEquals(com.grey.naf.dns.distributedresolver.Client.class, d2.dnsresolv.getClass());
		exec(d1, "distributed-local", true, null, false);
		exec(d2, "distributed-remote", false, d1, true);
	}

	@org.junit.Test
	public void testTCP() throws com.grey.base.GreyException, java.io.IOException
	{
		if (skiptests) return;
		com.grey.naf.Config nafcfg = setConfig(null, CFG_TCP);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "utest_AlwaysTCP";
		def.hasDNS = true;
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, nafcfg, junklogger);
		org.junit.Assert.assertEquals(com.grey.naf.dns.embedded.EmbeddedResolver.class, dsptch.dnsresolv.getClass());
		exec(dsptch, "TCP", false, null, false);
	}

	@org.junit.Test
	public void testFallbackA() throws com.grey.base.GreyException, java.io.IOException
	{
		if (skiptests) return;
		if (queryTargetMX.equals("-")) return;
		com.grey.naf.Config nafcfg = setConfig(null, CFG_FALLBACKA);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "utest_Fallback_A";
		def.hasDNS = true;
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, nafcfg, junklogger);
		exec(dsptch, "fallbackA", false, null, true);
	}

	@org.junit.Test
	public void testCancel() throws com.grey.base.GreyException, java.io.IOException
	{
		if (skiptests) return;
		com.grey.naf.Config nafcfg = setConfig(null, 0);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "utest_Cancel";
		def.hasDNS = true;
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, nafcfg, junklogger);
		ByteChars bc = new ByteChars("any-old-name");
		dsptch.dnsresolv.resolveHostname(bc, this, null, 0);
		dsptch.dnsresolv.resolveHostname(bc, this, null, 0);
		bc = new ByteChars("another-name");
		dsptch.dnsresolv.resolveHostname(bc, this, null, 0);
		int cnt = dsptch.dnsresolv.cancel(this);
		org.junit.Assert.assertEquals(3, cnt);
		cnt = dsptch.dnsresolv.cancel(this);
		org.junit.Assert.assertEquals(0, cnt);
		dsptch.stop();
	}

	private void exec(Dispatcher dsptch, String cbdata, boolean nowait, Dispatcher d2, boolean nopiggy) throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		synchronized (this) {
			// we haven't launched our threads yet, but this keeps FindBugs happy
			opencallbacks = 0;
		}
		if (cbdata.equals("fallbackA")) {
			issueQuery(dsptch, "www."+queryTargetMX, Resolver.QTYPE_MX, cbdata, nopiggy);
		} else {
			if (!queryTargetA.equals("-")) issueQuery(dsptch, queryTargetA, Resolver.QTYPE_A, cbdata, nopiggy);
			issueQuery(dsptch, "no-such-host"+NoSuchDom, Resolver.QTYPE_A, cbdata+":nodom", nopiggy);
			if (!queryTargetPTR.equals("-")) issueQuery(dsptch, queryTargetPTR, Resolver.QTYPE_PTR, cbdata, nopiggy);
			issueQuery(dsptch, "192.168.250.2", Resolver.QTYPE_PTR, cbdata+":nodom", nopiggy);
			if (!queryTargetMX.equals("-")) {
				issueQuery(dsptch, queryTargetMX, Resolver.QTYPE_MX, cbdata, nopiggy);
				if (cbdata.contains("embedded")) issueQuery(dsptch, "www."+queryTargetMX, Resolver.QTYPE_MX, cbdata, nopiggy);
			}
			issueQuery(dsptch, "no-such-mailserver"+NoSuchDom, Resolver.QTYPE_MX, cbdata+":nodom", nopiggy);
		}
		if (nowait) return;

		if (d2 != null) d2.start();
		dsptch.start();
		dsptch.waitStopped();
		if (d2 != null) d2.waitStopped();
		synchronized (this) {
			// joining on Dispatcher thread will have synchronized our memory view, but this keeps FindBugs happy
			org.junit.Assert.assertEquals(dnsrequests, dnscallbacks);
			org.junit.Assert.assertEquals(0, opencallbacks);
		}
	}

	// We issue an immediate repeat query to exercise the code for piggybacking on the earlier pending one.
	// We can't actually verify it happens though (short of examining the Resolver's debug traces).
	private void issueQuery(Dispatcher d, CharSequence qname, int qtype, String cbdata, boolean nopiggy) throws java.io.IOException
	{
		Answer answer;
		if (qtype == Resolver.QTYPE_A) {
			ByteChars bc = new ByteChars(qname);
			answer = d.dnsresolv.resolveHostname(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = d.dnsresolv.resolveHostname(bc, this, cbdata+":piggyback", 0);
		} else if (qtype == Resolver.QTYPE_PTR)  {
			int ip = IP.convertDottedIP(qname);
			answer = d.dnsresolv.resolveIP(ip, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = d.dnsresolv.resolveIP(ip, this, cbdata+":piggyback", 0);
		} else if (qtype == Resolver.QTYPE_MX)  {
			ByteChars bc = new ByteChars(qname);
			answer = d.dnsresolv.resolveMailDomain(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = d.dnsresolv.resolveMailDomain(bc, this, cbdata+":piggyback", 0);
		} else {
			throw new RuntimeException("Missing issueQuery() case for qtype="+qtype);
		}
		org.junit.Assert.assertNull(answer);
		dnsrequests++;
		if (!nopiggy) dnsrequests++; //and there was one more
	}

	@Override
	public void dnsResolved(com.grey.naf.reactor.Dispatcher dsptch, Answer answer, Object cbdata) throws java.io.IOException
	{
		boolean halt = false;
		synchronized (this) {
			halt = (++dnscallbacks == dnsrequests);
			opencallbacks++;
			dsptch.logger.info("UTEST: Callback="+dnscallbacks+"/"+dnsrequests+": "+answer+" - cbdata="+cbdata);
		}
		String[] parts = String.class.cast(cbdata).split(":");
		java.util.List<String> cbflags = java.util.Arrays.asList(parts);
		Answer.STATUS expsts;
		if (answer.qtype == Resolver.QTYPE_MX && answer.qname.toString().startsWith("www.") && !cbflags.contains("fallbackA")) {
			expsts = Answer.STATUS.NODOMAIN;
		} else {
			expsts = (cbflags.contains("nodom") ? Answer.STATUS.NODOMAIN : Answer.STATUS.OK);
		}
		org.junit.Assert.assertEquals(expsts, answer.result);

		// We don't know what results to expect, so just do some minimal assertions
		if (answer.qtype == Resolver.QTYPE_A) {
			if (answer.result == Answer.STATUS.OK) org.junit.Assert.assertFalse(answer.rrdata.get(0).ipaddr == 0);
			if (!cbflags.contains("piggyback") && !cbflags.contains("distributed-remote")) {
				answer = dsptch.dnsresolv.resolveHostname(answer.qname, this, null, Resolver.FLAG_NOQRY);
				assertAnswer(dsptch, answer.result, Resolver.QTYPE_A, 0, answer);
			}
		} else if (answer.qtype == Resolver.QTYPE_PTR)  {
			if (answer.result == Answer.STATUS.OK) org.junit.Assert.assertNotNull(answer.rrdata.get(0).domnam);
			if (!cbflags.contains("piggyback") && !cbflags.contains("distributed-remote")) {
				answer = dsptch.dnsresolv.resolveIP(answer.qip, this, null, Resolver.FLAG_NOQRY);
				assertAnswer(dsptch, answer.result, Resolver.QTYPE_PTR, null, answer);
			}
		} else if (answer.qtype == Resolver.QTYPE_MX)  {
			if (answer.result == Answer.STATUS.OK) org.junit.Assert.assertFalse(answer.rrdata.get(0).ipaddr == 0);
			if (!cbflags.contains("piggyback") && !cbflags.contains("distributed-remote")) {
				ByteChars bc = new ByteChars(answer.qname);
				answer = dsptch.dnsresolv.resolveMailDomain(bc, this, null, Resolver.FLAG_NOQRY);
				assertAnswer(dsptch, answer.result, Resolver.QTYPE_MX, 0, answer);
			}
		} else {
			throw new RuntimeException("Missing dnsResolved() case for qtype="+answer.qtype);
		}

		if (halt) {
			int cnt = dsptch.dnsresolv.cancel(this);
			int expect = (cbflags.contains("distributed-remote") ? -1 : 0); //unknowable for this case
			org.junit.Assert.assertEquals(expect, cnt);
			dsptch.stop();
		}
		synchronized (this) {
			opencallbacks--;
		}
	}

	private static void assertAnswer(Dispatcher d, Answer.STATUS sts, byte qtype, CharSequence domnam, Answer answer)
	{
		d.logger.info("UTEST: Answer for IP="+IP.displayDottedIP(answer.qip, null)+": "+answer);
		if (sts != Answer.STATUS.NODOMAIN) {
			if (domnam != null) org.junit.Assert.assertTrue(StringOps.sameSeq(domnam, answer.rrdata.get(0).domnam));
			org.junit.Assert.assertTrue(answer.rrdata.get(0).ipaddr == 0);
		}
		org.junit.Assert.assertNull(answer.qname);
		org.junit.Assert.assertFalse(answer.qip == 0);
		assertAnswer(sts, qtype, answer);
	}

	private static void assertAnswer(Dispatcher d, Answer.STATUS sts, byte qtype, int ip, Answer answer)
	{
		String qnametype = (qtype == Resolver.QTYPE_MX ? "Domain" : "Host");
		d.logger.info("UTEST: Answer for "+qnametype+"="+answer.qname+": "+answer);
		if (sts != Answer.STATUS.NODOMAIN) {
			if (ip != 0) org.junit.Assert.assertEquals(ip, answer.rrdata.get(0).ipaddr);
			if (answer.qtype != Resolver.QTYPE_A) {
				// might or might not be null for RR-A, depending on how cache entry got created
				org.junit.Assert.assertNotNull(answer.rrdata.get(0).domnam);
			}
		}
		org.junit.Assert.assertNotNull(answer.qname);
		org.junit.Assert.assertTrue(answer.qip == 0);
		assertAnswer(sts, qtype, answer);
	}

	private static void assertAnswer(Answer.STATUS sts, byte qtype, Answer answer)
	{
		org.junit.Assert.assertEquals(sts, answer.result);
		org.junit.Assert.assertEquals(qtype, answer.qtype);
		if (sts == Answer.STATUS.NODOMAIN) {
			org.junit.Assert.assertEquals(0, answer.rrdata.size());
		} else {
			//MX answer is made up of type-A RRs, not type-MX as per its answer.qtype field
			if (qtype == Resolver.QTYPE_MX) qtype = Resolver.QTYPE_A;
			org.junit.Assert.assertEquals(qtype, answer.rrdata.get(0).rrtype);
			org.junit.Assert.assertEquals(Packet.QCLASS_INET, answer.rrdata.get(0).rrclass);
		}
	}

	private com.grey.naf.Config setConfig(String clss, int flags) throws com.grey.base.ConfigException, java.io.IOException
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<naf><dnsresolver");
		if (clss != null) sb.append(" class=\""+clss+"\"");
		if ((flags & CFG_TCP) != 0) sb.append(" alwaystcp=\"Y\"");
		sb.append('>');
		sb.append("<query_mx maxrr=\"10\" fallback_a=\""+((flags & CFG_FALLBACKA) != 0)+"\"/>");
		sb.append("<cache exitdump=\"Y\"/>");
		if (dnsservers != null) sb.append("<servers>").append(dnsservers).append("</servers>");
		sb.append("<retry timeout=\"5s\" step=\"0\"/>");
		sb.append("</dnsresolver></naf>");
		return com.grey.naf.Config.synthesise(sb.toString());
	}
}
