/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.integration;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.IP;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.dns.ResolverAnswer;
import com.grey.naf.dns.ResolverDNS;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.DispatcherTest;

public class ResolverTest
	extends ResolverTester
	implements com.grey.naf.dns.ResolverDNS.Client
{
	private static final String rootdir = DispatcherTest.initPaths(ResolverTest.class);
	private static final java.io.File CFGFILE_ROOTS = new java.io.File(rootdir+"/rootservers");

	private static final String NoSuchDom = "nonsuchdomain6812.com";

	private static final String CBFLAG_DISTRIBREMOTE = "DistributedRemote";
	private static final String CBFLAG_NODOM = "nodom";
	private static final String CBFLAG_TMT = "timeout";
	private static final String CBFLAG_PIGGY = "piggyback";
	private static final String CBDLM = ":";

	private static final int CFG_TCP = 1 << 0;
	private static final int CFG_BADSERVER = 1 << 1;
	private static final int CFG_NONRECURSIVE = 1 << 2;
	private static final int CFG_NONAUTO = 1 << 3;
	private static final int CFG_NOMOCK = 1 << 4;

	private Class<?> clss_resolver;
	private int cnt_dnsrequests;
	private int cnt_dnscallbacks;
	private boolean callback_error;
	protected String dispatcher_name;

	@org.junit.Rule
	public final org.junit.rules.TestRule testwatcher = new org.junit.rules.TestWatcher() {
		@Override public void starting(org.junit.runner.Description d) {
			dispatcher_name = d.getMethodName();
			if (dispatcher_name.startsWith("test")) dispatcher_name = dispatcher_name.substring("test".length());
			dispatcher_name = "DNSTEST_"+dispatcher_name;
			System.out.println("Starting test="+d.getMethodName()+" - "+d.getClassName());
		}
	};

	@org.junit.Before
	public void beforeTest() throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		FileOps.ensureDirExists(rootdir);
	}

	@org.junit.Test
	public void testEmbedded() throws java.io.IOException
	{
		execColocated(0);
	}

	@org.junit.Test
	public void testTCP() throws java.io.IOException
	{
		execColocated(CFG_TCP);
	}

	@org.junit.Test
	public void testDistributedLocal() throws java.io.IOException
	{
		Dispatcher[] dispatchers = createDistributedResolver(0, true);
		execColocated(dispatchers[0], dispatchers[1]);
	}

	@org.junit.Test
	public void testDistributedRemote() throws java.io.IOException
	{
		Dispatcher[] dispatchers = createDistributedResolver(0, false);
		execDNS(dispatchers[0], dispatchers[1], true);
	}

	@org.junit.Test
	public void testNonRecursive() throws java.io.IOException
	{
		org.junit.Assume.assumeTrue(HAVE_DNS_SERVICE);
		String cfgroots = ". : 999.999.999.999" //parsing failure ignored due to Auto setting
				+"\nfictional.private.domain : private-root-server"; //lookup failure ignored due to Auto setting
		FileOps.writeTextFile(CFGFILE_ROOTS, cfgroots, false);
		execColocated(CFG_NONRECURSIVE);
	}

	@org.junit.Test
	public void testNonRecursive_NoAuto() throws java.io.IOException
	{
		String cfgroots = ". : bad-nameserver" //ok because Resolver should try the next one
				+"\n. : 999.999.999.999" //ok because Resolver should try the next one
				+"\n. : L.ROOT-SERVERS.NET." //even if lookup fails on this, the dotted IP below will suffice
				+"\n. : 201.150.99.1 : manual"
				+"\nfictional.private.domain : 192.168.250.201 : MANUAL";
		FileOps.writeTextFile(CFGFILE_ROOTS, cfgroots, false);
		execColocated(CFG_NONRECURSIVE | CFG_NONAUTO);
	}

	@org.junit.Test
	public void testNonRecursive_TCP() throws java.io.IOException
	{
		org.junit.Assume.assumeTrue(HAVE_DNS_SERVICE);
		CFGFILE_ROOTS.delete();
		execColocated(CFG_NONRECURSIVE | CFG_TCP);
	}

	@org.junit.Test
	public void testTimeoutUDP() throws java.io.IOException
	{
		execTimeout(false);
	}

	// In TCP's case, the timeout test actually simulates a failure to connect
	@org.junit.Test
	public void testTimeoutTCP() throws java.io.IOException
	{
		execTimeout(true);
	}

	@org.junit.Test
	public void testCancel() throws java.io.IOException
	{
		//Specify a bad port because we're not interested in reponses, and don't want to spam an actual server
		Dispatcher dsptch = createResolver(CFG_BADSERVER);
		ResolverDNS resolver = dsptch.getResolverDNS();

		ByteChars bc = new ByteChars("any-old-badname.cancel");
		resolver.resolveHostname(bc, this, null, 0);
		resolver.resolveHostname(bc, this, null, 0);
		bc = new ByteChars("another-badname.cancel");
		resolver.resolveHostname(bc, this, null, 0);
		validatePendingRequests(resolver, 2, 3); //2 distinct requests, one of which was repeated
		int cnt = resolver.cancel(this);
		org.junit.Assert.assertEquals(3, cnt);
		cnt = resolver.cancel(this);
		org.junit.Assert.assertEquals(0, cnt);
		validatePendingRequests(resolver, 2, 0); //the requests themselves aren't cancelled, just the callback
		dsptch.stop();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertEquals(0, cnt_dnscallbacks); //no responses because we never started Dispatcher
		org.junit.Assert.assertFalse(callback_error);
		//can't call validateFinalState() because two QueryHandles still pending
		Object rs = getResolverService(resolver);
		validateObjectWell(rs, "bcstore", false);
		validateObjectWell(rs, "anstore", false);
		int caller_errors = ((Integer)DynLoader.getField(rs, "caller_errors")).intValue();
		org.junit.Assert.assertEquals(0, caller_errors);
	}

	@org.junit.Test
	public void testCancel_Distributed() throws java.io.IOException
	{
		Dispatcher[] dispatchers = createDistributedResolver(CFG_BADSERVER, true);
		Dispatcher d1 = dispatchers[0];
		Dispatcher d2 = dispatchers[1];
		ResolverDNS resolver1 = d1.getResolverDNS();
		ResolverDNS resolver2 = d2.getResolverDNS();

		ByteChars bc = new ByteChars("any-old-badname.canceldist");
		int cnt = resolver2.cancel(this);
		org.junit.Assert.assertEquals(-1, cnt);
		resolver2.resolveHostname(bc, this, null, 0);
		resolver2.resolveHostname(bc, this, null, 0);
		bc = new ByteChars("another-badname.canceldist");
		resolver2.resolveHostname(bc, this, null, 0);
		validatePendingRequests(resolver1, 2, 3);
		// Run master Dispatcher briefly, but long enough to allow any DNS responses to be received (should be none)
		d1.start();
		com.grey.naf.reactor.TimerNAF.sleep(4 * 1000);
		d1.stop();
		boolean stopped = d2.stop();
		Dispatcher.STOPSTATUS stopsts = d1.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(d1.completedOK());
		org.junit.Assert.assertTrue(stopped);
		stopsts = d2.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertEquals(0, cnt_dnscallbacks); //no responses becaise we cancelled beforehand
		org.junit.Assert.assertFalse(callback_error);
		validateFinalState(resolver1);
	}

	private void execTimeout(boolean tcp) throws java.io.IOException
	{
		int cfgflags = CFG_BADSERVER;
		if (tcp) cfgflags |= CFG_TCP;
		String cbflags = CBFLAG_TMT;
		cnt_dnsrequests = 4;
		Dispatcher dsptch = createResolver(cfgflags);
		ResolverDNS resolver = dsptch.getResolverDNS();

		ByteChars bc = new ByteChars(queryTargetA);
		ResolverAnswer answer = resolver.resolveHostname(bc, this, cbflags, 0);
		org.junit.Assert.assertNull(answer);
		int ip = IP.convertDottedIP(queryTargetPTR);
		answer = resolver.resolveIP(ip, this, cbflags, 0);
		org.junit.Assert.assertNull(answer);
		bc = new ByteChars(queryTargetMX);
		answer = resolver.resolveMailDomain(bc, this, cbflags, 0);
		org.junit.Assert.assertNull(answer);
		bc = new ByteChars(queryTargetNS);
		answer = resolver.resolveNameServer(bc, this, cbflags, 0);
		org.junit.Assert.assertNull(answer);

		dsptch.start();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_MINUTE * 2, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertEquals(cnt_dnsrequests, cnt_dnscallbacks);
		org.junit.Assert.assertFalse(callback_error);
		org.junit.Assert.assertEquals(cnt_dnsrequests, rspcnt_tmt);
		org.junit.Assert.assertEquals(cnt_dnsrequests, rspcnt_nodom); //because dnsResolved() does repeat query
		validateFinalState(resolver);
	}

	// This runs against a Resolver located in the same Dispatcher thread, so we can make blocking NOQRY calls on it.
	// These are queries which do get sent as far as the resolver service but do not result in DNS queries, and so they
	// will be resolved synchronously.
	private void execColocated(Dispatcher dsptch, Dispatcher d2) throws java.io.IOException
	{
		ByteChars bc = new ByteChars("noncached.no.such.domain2");
		ResolverDNS resolver = dsptch.getResolverDNS();
		ResolverAnswer answer = resolver.resolveHostname(bc, this, null, ResolverDNS.FLAG_NOQRY);
		logger.info("UTEST: Answer for query-only: "+answer);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.NODOMAIN, answer.result);
		org.junit.Assert.assertEquals(0, answer.size());
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_A, answer.qtype);
		org.junit.Assert.assertTrue(StringOps.sameSeq(bc, answer.qname));

		int ip = IP.convertDottedIP("192.168.240.1");
		answer = resolver.resolveIP(ip, this, null, ResolverDNS.FLAG_NOQRY);
		logger.info("UTEST: Answer for IP-query-only: "+answer);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.NODOMAIN, answer.result);
		org.junit.Assert.assertEquals(0, answer.size());
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_PTR, answer.qtype);
		org.junit.Assert.assertEquals(ip, answer.qip);

		//now kick off the DNS queries
		execDNS(dsptch, d2, false);
	}

	private void execColocated(int flags) throws java.io.IOException
	{
		Dispatcher dsptch = createResolver(flags);
		execColocated(dsptch, null);
	}

	private void execDNS(Dispatcher dsptch, Dispatcher d2, boolean remote_resolver) throws java.io.IOException
	{
		//First test a few Resolver calls that should not even get sent to the resolver service
		ByteChars bc = new ByteChars("101.25.32.1"); //any old IP
		int ip = IP.convertDottedIP(bc);
		ResolverDNS resolver = dsptch.getResolverDNS();
		ResolverAnswer answer = resolver.resolveHostname(bc, this, null, 0);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, ip, answer);

		bc.populate("localhost");
		answer = resolver.resolveHostname(bc, this, null, 0);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, IP.IP_LOCALHOST, answer);

		answer = resolver.resolveIP(IP.IP_LOCALHOST, this, null, 0);
		assertAnswer(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_PTR, "localhost", answer);

		bc.populate("legalsyntax.but.no.such.domain.");
		answer = resolver.resolveHostname(bc, this, null, ResolverDNS.FLAG_SYNTAXONLY);
		logger.info("UTEST: Answer for syntax-only: "+answer);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.OK, answer.result);
		org.junit.Assert.assertEquals(0, answer.size());
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_A, answer.qtype);
		org.junit.Assert.assertTrue(StringOps.sameSeq(bc, answer.qname));

		bc.populate("bad.domain..");
		answer = resolver.resolveHostname(bc, this, null, 0);
		logger.info("UTEST: Answer for bad syntax: "+answer);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.BADNAME, answer.result);
		org.junit.Assert.assertEquals(0, answer.size());
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_A, answer.qtype);
		org.junit.Assert.assertTrue(StringOps.sameSeq(bc, answer.qname));

		//now issue Resolver calls that will result in DNS queries with async results
		Object[][] queries = new Object[][]{{ResolverDNS.QTYPE_A, queryTargetA},
			{ResolverDNS.QTYPE_PTR, queryTargetPTR, "192.168.240.2"},
			{ResolverDNS.QTYPE_NS, queryTargetNS}, {ResolverDNS.QTYPE_SOA, queryTargetSOA},
			{ResolverDNS.QTYPE_MX, queryTargetMX}, {ResolverDNS.QTYPE_SRV, queryTargetSRV},
			{ResolverDNS.QTYPE_TXT, queryTargetTXT}, {ResolverDNS.QTYPE_AAAA, queryTargetAAAA}};
		boolean nopiggy = remote_resolver; //can't piggyback remote requests as response-callbacks not synchronous
		String cbdata = (remote_resolver ? CBFLAG_DISTRIBREMOTE : null);
		for (int idx = 0; idx != queries.length; idx++) {
			byte qtype = (byte)queries[idx][0];
			String qname = (String)queries[idx][1];
			qname = Character.toUpperCase(qname.charAt(0)) + qname.substring(1).toLowerCase();
			String nonsuch = (queries[idx].length > 2 ? (String)queries[idx][2] : "no-such-"+ResolverDNS.getQTYPE(qtype)+"."+NoSuchDom);
			issueQuery(dsptch, qname, qtype, cbdata, nopiggy);
			issueQuery(dsptch, nonsuch, qtype, addFlag(cbdata,CBFLAG_NODOM), nopiggy);
		}
		issueQuery(dsptch, queryTargetCNAME, ResolverDNS.QTYPE_A, cbdata, nopiggy);
		issueQuery(dsptch, "www."+queryTargetMX, ResolverDNS.QTYPE_MX, addFlag(cbdata,CBFLAG_NODOM), nopiggy);

		dsptch.start();
		if (d2 != null) d2.start();
		long maxtime = dsptch.getSystemTime() + (TimeOps.MSECS_PER_MINUTE * 2);
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(maxtime - dsptch.getSystemTime(), true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		if (d2 != null) {
			stopsts = d2.waitStopped(maxtime - dsptch.getSystemTime(), true);
			org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
			org.junit.Assert.assertTrue(d2.completedOK());
			org.junit.Assert.assertTrue(getResolverService(resolver) == getResolverService(d2.getResolverDNS()));
		}
		org.junit.Assert.assertEquals(cnt_dnsrequests, cnt_dnscallbacks);
		org.junit.Assert.assertFalse(callback_error);
		validateFinalState(resolver);
	}

	// We issue an immediate repeat query to exercise the code for piggybacking on the earlier pending one.
	// We can't actually verify it happens though (short of examining the Resolver's debug traces).
	private void issueQuery(Dispatcher d, CharSequence qname, int qtype, String cbdata, boolean nopiggy) throws java.io.IOException
	{
		ResolverDNS resolver = d.getResolverDNS();
		ResolverAnswer answer;
		if (qtype == ResolverDNS.QTYPE_A) {
			ByteChars bc = new ByteChars(qname);
			answer = resolver.resolveHostname(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveHostname(bc, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else if (qtype == ResolverDNS.QTYPE_PTR) {
			int ip = IP.convertDottedIP(qname);
			answer = resolver.resolveIP(ip, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveIP(ip, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else if (qtype == ResolverDNS.QTYPE_NS) {
			ByteChars bc = new ByteChars(qname);
			answer = resolver.resolveNameServer(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveNameServer(bc, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else if (qtype == ResolverDNS.QTYPE_MX) {
			ByteChars bc = new ByteChars(qname);
			answer = resolver.resolveMailDomain(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveMailDomain(bc, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else if (qtype == ResolverDNS.QTYPE_SOA) {
			ByteChars bc = new ByteChars(qname);
			answer = resolver.resolveSOA(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveSOA(bc, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else if (qtype == ResolverDNS.QTYPE_SRV) {
			ByteChars bc = new ByteChars(qname);
			answer = resolver.resolveSRV(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveSRV(bc, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else if (qtype == ResolverDNS.QTYPE_TXT) {
			ByteChars bc = new ByteChars(qname);
			answer = resolver.resolveTXT(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveTXT(bc, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else if (qtype == ResolverDNS.QTYPE_AAAA) {
			ByteChars bc = new ByteChars(qname);
			answer = resolver.resolveAAAA(bc, this, cbdata, 0);
			org.junit.Assert.assertNull(answer);
			if (!nopiggy) answer = resolver.resolveAAAA(bc, this, addFlag(cbdata,CBFLAG_PIGGY), 0);
		} else {
			throw new RuntimeException("Missing issueQuery() case for qtype="+qtype);
		}
		org.junit.Assert.assertNull(answer);
		cnt_dnsrequests++;
		if (!nopiggy) cnt_dnsrequests++; //and there was one more
	}

	@Override
	public void dnsResolved(Dispatcher dsptch, ResolverAnswer answer, Object cbdata)
	{
		try {
			handleDnsResult(dsptch, answer, cbdata);
		} catch (Throwable ex) {
			logger.log(LEVEL.ERR, ex, true, "Failed to handle DNS response - "+answer+" with cbdata="+cbdata);
			callback_error = true;
			dsptch.stop();
		}
	}

	// The main aim of this method is to repeat the query that has just been answered, to make sure it's cached. The NOQRY
	// flag results in NODOMAIN for any uncached answers.
	private void handleDnsResult(Dispatcher dsptch, ResolverAnswer answer, Object cbdata) throws java.io.IOException
	{
		boolean halt = (++cnt_dnscallbacks == cnt_dnsrequests);
		String[] parts = (cbdata == null ? new String[0] : String.class.cast(cbdata).split(CBDLM));
		java.util.List<String> cbflags = java.util.Arrays.asList(parts);
		ResolverAnswer.STATUS exp_sts = ResolverAnswer.STATUS.OK;
		if (cbflags.contains(CBFLAG_TMT)) {
			exp_sts = ResolverAnswer.STATUS.TIMEOUT;
		} else if (cbflags.contains(CBFLAG_NODOM)) {
			exp_sts = ResolverAnswer.STATUS.NODOMAIN;
		}
		ResolverAnswer.STATUS exp_sts2 = (exp_sts == ResolverAnswer.STATUS.TIMEOUT ? ResolverAnswer.STATUS.NODOMAIN : exp_sts);
		ResolverDNS resolver = dsptch.getResolverDNS();
		logger.info("UTEST: utest.dnsResolved="+cnt_dnscallbacks+"/"+cnt_dnsrequests+": "+answer+" - cbdata="+cbdata);

		if (answer.qtype == ResolverDNS.QTYPE_PTR) {
			assertAnswer(exp_sts, answer.qtype, null, answer);
		} else {
			assertAnswer(exp_sts, answer.qtype, 0, answer);
		}
		//can't do repeat query for Distributed-Remote as it doesn't get a synchronous answer
		boolean repeatqry = (!cbflags.contains(CBFLAG_PIGGY) && !cbflags.contains(CBFLAG_DISTRIBREMOTE));

		// We don't know what results to expect, so just do some minimal assertions
		if (answer.qtype == ResolverDNS.QTYPE_A) {
			if (repeatqry) {
				answer = resolver.resolveHostname(answer.qname, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_A, 0, answer);
			}
		} else if (answer.qtype == ResolverDNS.QTYPE_PTR) {
			if (repeatqry) {
				answer = resolver.resolveIP(answer.qip, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_PTR, null, answer);
			}
		} else if (answer.qtype == ResolverDNS.QTYPE_MX) {
			if (repeatqry) {
				answer = resolver.resolveMailDomain(answer.qname, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_MX, 0, answer);
			}
		} else if (answer.qtype == ResolverDNS.QTYPE_NS) {
			if (repeatqry) {
				answer = resolver.resolveNameServer(answer.qname, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_NS, 0, answer);
			}
		} else if (answer.qtype == ResolverDNS.QTYPE_SOA) {
			if (repeatqry) {
				answer = resolver.resolveSOA(answer.qname, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_SOA, 0, answer);
			}
		} else if (answer.qtype == ResolverDNS.QTYPE_SRV) {
			if (repeatqry) {
				answer = resolver.resolveSRV(answer.qname, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_SRV, 0, answer);
			}
		} else if (answer.qtype == ResolverDNS.QTYPE_TXT) {
			if (repeatqry) {
				answer = resolver.resolveTXT(answer.qname, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_TXT, 0, answer);
			}
		} else if (answer.qtype == ResolverDNS.QTYPE_AAAA) {
			if (repeatqry) {
				answer = resolver.resolveAAAA(answer.qname, this, cbdata, ResolverDNS.FLAG_NOQRY);
				assertAnswer(exp_sts2, ResolverDNS.QTYPE_AAAA, 0, answer);
			}
		} else {
			throw new RuntimeException("Missing dnsResolved() case for qtype="+answer.qtype);
		}

		if (halt) {
			int cnt = resolver.cancel(this);
			int expect = (cbflags.contains(CBFLAG_DISTRIBREMOTE) ? -1 : 0); //unknowable for this case
			org.junit.Assert.assertEquals(expect, cnt);
			dsptch.stop();
		}
	}

	private Dispatcher createResolver(int flags) throws java.io.IOException
	{
		clss_resolver = com.grey.naf.dns.embedded.EmbeddedResolver.class;
		com.grey.naf.NAFConfig nafcfg = setConfig(null, flags);
		ApplicationContextNAF appctx = ApplicationContextNAF.create(null, nafcfg);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = dispatcher_name;
		def.hasDNS = true;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(appctx, def, logger);
		org.junit.Assert.assertEquals(clss_resolver, dsptch.getResolverDNS().getClass());
		return dsptch;
	}

	// The Resolver will reside in the Dispatcher tagged as the master, and we regard d1 as the local Dispatcher
	private Dispatcher[] createDistributedResolver(int flags, boolean local_master)
			throws java.io.IOException
	{
		clss_resolver = com.grey.naf.dns.distributedresolver.Client.class;
		String d1name;
		String d2name;
		com.grey.naf.NAFConfig nafcfg;
		if (local_master) {
			d1name = dispatcher_name+"_master";
			d2name = dispatcher_name+"_slave";
			nafcfg = setConfig(d1name, flags);
		} else {
			d1name = dispatcher_name+"_slave";
			d2name = dispatcher_name+"_master";
			nafcfg = setConfig(d2name, flags);
		}
		ApplicationContextNAF appctx = ApplicationContextNAF.create(null, nafcfg);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = d1name;
		def.hasDNS = true;
		def.hasNafman = true;
		def.surviveHandlers = false;
		def.surviveDownstream = false;
		Dispatcher d1 = Dispatcher.create(appctx, def, logger);
		def.name = d2name;
		Dispatcher d2 = Dispatcher.create(appctx, def, logger);
		org.junit.Assert.assertEquals(clss_resolver, d1.getResolverDNS().getClass());
		org.junit.Assert.assertEquals(clss_resolver, d2.getResolverDNS().getClass());
		org.junit.Assert.assertTrue(getResolverService(d1.getResolverDNS()) == getResolverService(d2.getResolverDNS()));
		return new Dispatcher[]{d1, d2};
	}

	private com.grey.naf.NAFConfig setConfig(String master, int flags) throws java.io.IOException
	{
		int maxrr_ns = 9;
		int maxrr_mx = 10;
		if ((flags & CFG_NONRECURSIVE) != 0) {
			maxrr_ns = 0;
			maxrr_mx = 0;
		}
		String override_servers = ((flags & CFG_BADSERVER) == 0 ? dnsservers : "127.0.0.1:1");
		StringBuilder sb = new StringBuilder();
		sb.append("<naf><baseport>"+com.grey.naf.NAFConfig.RSVPORT_ANON+"</baseport><dnsresolver");
		if (clss_resolver != null) sb.append(" class=\""+clss_resolver.getName()+"\"");
		if (master != null) sb.append(" master=\""+master+"\"");
		if ((flags & CFG_TCP) != 0) sb.append(" alwaystcp=\"Y\"");
		if ((flags & CFG_NONRECURSIVE) != 0) sb.append(" recursive=\"N\"");
		sb.append(" exitdump=\"Y\">");
		sb.append("<rootservers auto=\""+((flags & CFG_NONAUTO) == 0)+"\">");
		sb.append(CFGFILE_ROOTS.exists()?CFGFILE_ROOTS.getAbsolutePath():"").append("</rootservers>");
		sb.append("<cache_mx maxrr=\""+maxrr_mx+"\"/>");
		sb.append("<cache_ns maxrr=\""+maxrr_ns+"\"/>");
		if (override_servers != null) sb.append("<localservers>").append(override_servers).append("</localservers>");
		if (mockserver != null && (flags & (CFG_BADSERVER | CFG_NOMOCK)) == 0) {
			is_mockserver = true;
			sb.append("<interceptor host=\"127.0.0.1\" port=\""+mockserver.getPort()+"\"/>");
		}
		if (is_mockserver || (flags & CFG_BADSERVER) != 0) {
			sb.append("<retry timeout=\"2s\" timeout_tcp=\"2s\" max=\"1\" backoff=\"200\"/>");
		} else {
			sb.append("<retry timeout=\"3s\" timeout_tcp=\"10s\" max=\"2\" backoff=\"200\"/>");
		}
		sb.append("</dnsresolver></naf>");
		return com.grey.naf.NAFConfig.synthesise(sb.toString());
	}

	private static String addFlag(String cbflags, String newflag)
	{
		if (cbflags == null) return newflag;
		return cbflags+CBDLM+newflag;
	}
}
