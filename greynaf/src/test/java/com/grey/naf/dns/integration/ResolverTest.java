/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.integration;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.IP;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.DispatcherDef;
import com.grey.naf.NAFConfig;
import com.grey.naf.dns.resolver.ResolverAnswer;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.TimerNAF;
import com.grey.naf.TestUtils;

public class ResolverTest
	extends ResolverTester
	implements ResolverDNS.Client
{
	private static final String rootdir = TestUtils.initPaths(ResolverTest.class);
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

	private final AtomicInteger cnt_dnsrequests = new AtomicInteger();
	private final AtomicInteger cnt_dnscallbacks = new AtomicInteger();
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
		ResolverDNS resolver = dsptch.getNamedItem(ResolverDNS.class.getName(), null);

		ByteChars bc = new ByteChars("any-old-badname.cancel");
		resolver.resolveHostname(bc, this, null, 0);
		resolver.resolveHostname(bc, this, null, 0);
		bc = new ByteChars("another-badname.cancel");
		resolver.resolveHostname(bc, this, null, 0);
		int cnt = resolver.cancel(this);
		org.junit.Assert.assertEquals(3, cnt);
		cnt = resolver.cancel(this);
		org.junit.Assert.assertEquals(0, cnt);
		org.junit.Assert.assertEquals(0, cnt_dnscallbacks.get()); //no responses because we never started Dispatcher
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
		ResolverDNS resolver1 = d1.getNamedItem(ResolverDNS.class.getName(), null);
		ResolverDNS resolver2 = d2.getNamedItem(ResolverDNS.class.getName(), null);

		ByteChars bc = new ByteChars("any-old-badname.canceldist");
		int cnt = resolver2.cancel(this);
		org.junit.Assert.assertEquals(-1, cnt);
		resolver2.resolveHostname(bc, this, null, 0);
		resolver2.resolveHostname(bc, this, null, 0);
		bc = new ByteChars("another-badname.canceldist");
		resolver2.resolveHostname(bc, this, null, 0);
		// Run master Dispatcher briefly, but long enough to allow any DNS responses to be received (should be none)
		d1.start();
		TimerNAF.sleep(4 * 1000);
		d1.stop();
		boolean stopped = d2.stop();
		Dispatcher.STOPSTATUS stopsts = d1.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(d1.completedOK());
		org.junit.Assert.assertTrue(stopped);
		stopsts = d2.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertEquals(0, cnt_dnscallbacks.get()); //no responses becaise we cancelled beforehand
		org.junit.Assert.assertFalse(callback_error);
		validateFinalState(resolver1);
	}

	private void execTimeout(boolean tcp) throws java.io.IOException
	{
		int cfgflags = CFG_BADSERVER;
		if (tcp) cfgflags |= CFG_TCP;
		String cbflags = CBFLAG_TMT;
		cnt_dnsrequests.set(4);
		Dispatcher dsptch = createResolver(cfgflags);
		ResolverDNS resolver = dsptch.getNamedItem(ResolverDNS.class.getName(), null);
		ResolverDNS.Client client = this;

		TimerNAF.Handler runner = new TimerNAF.Handler() {
			@Override
			public void timerIndication(TimerNAF tmr, Dispatcher d) throws IOException {
				ByteChars bc = new ByteChars(queryTargetA);
				ResolverAnswer answer = resolver.resolveHostname(bc, client, cbflags, 0);
				org.junit.Assert.assertNull(answer);
				int ip = IP.convertDottedIP(queryTargetPTR);
				answer = resolver.resolveIP(ip, client, cbflags, 0);
				org.junit.Assert.assertNull(answer);
				bc = new ByteChars(queryTargetMX);
				answer = resolver.resolveMailDomain(bc, client, cbflags, 0);
				org.junit.Assert.assertNull(answer);
				bc = new ByteChars(queryTargetNS);
				answer = resolver.resolveNameServer(bc, client, cbflags, 0);
				org.junit.Assert.assertNull(answer);
			}
		};
		dsptch.setTimer(0, 0, runner);

		dsptch.start();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_MINUTE * 2, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertEquals(cnt_dnsrequests.get(), cnt_dnscallbacks.get());
		org.junit.Assert.assertFalse(callback_error);
		org.junit.Assert.assertEquals(cnt_dnsrequests.get(), rspcnt_tmt);
		org.junit.Assert.assertEquals(cnt_dnsrequests.get(), rspcnt_nodom); //because dnsResolved() does repeat query
		validateFinalState(resolver);
	}

	// This runs against a Resolver located in the same Dispatcher thread, so we can make blocking NOQRY calls on it.
	// These are queries which do get sent as far as the resolver service but do not result in DNS queries, and so they
	// will be resolved synchronously.
	private void execColocated(Dispatcher dsptch, Dispatcher d2) throws java.io.IOException
	{
		ByteChars bc = new ByteChars("noncached.no.such.domain2");
		ResolverDNS resolver = dsptch.getNamedItem(ResolverDNS.class.getName(), null);
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

	// The dsptch arg is our local Dispatcher.
	// If running a distributed resolver, then d2 is the other Dispatcher and is the master if remote_resolver is true
	private void execDNS(Dispatcher dsptch, Dispatcher d2, boolean remote_resolver) throws java.io.IOException
	{
		//First test a few Resolver calls that should not even get sent to the resolver service
		ByteChars bc = new ByteChars("101.25.32.1"); //any old IP
		int ip = IP.convertDottedIP(bc);
		ResolverDNS resolver = dsptch.getNamedItem(ResolverDNS.class.getName(), null);
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

		//now issue Resolver calls that will result in DNS queries with async results - must do in Dispatcher callback after it starts
		TimerNAF.Handler issuer = new ResolverQueryIssuer(this, remote_resolver, cnt_dnsrequests);
		dsptch.setTimer(0, 0, issuer);

		long stoptmt = TimeOps.MSECS_PER_MINUTE * 2;
		dsptch.start();
		if (d2 != null) d2.start();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(stoptmt, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		if (d2 != null) {
			stopsts = d2.waitStopped(stoptmt, true);
			org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
			org.junit.Assert.assertTrue(d2.completedOK());
		}
		org.junit.Assert.assertEquals(cnt_dnsrequests.get(), cnt_dnscallbacks.get());
		org.junit.Assert.assertFalse(callback_error);
		validateFinalState(resolver);
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
		boolean halt = (cnt_dnscallbacks.incrementAndGet() == cnt_dnsrequests.get());
		String[] parts = (cbdata == null ? new String[0] : String.class.cast(cbdata).split(CBDLM));
		java.util.List<String> cbflags = java.util.Arrays.asList(parts);
		ResolverAnswer.STATUS exp_sts = ResolverAnswer.STATUS.OK;
		if (cbflags.contains(CBFLAG_TMT)) {
			exp_sts = ResolverAnswer.STATUS.TIMEOUT;
		} else if (cbflags.contains(CBFLAG_NODOM)) {
			exp_sts = ResolverAnswer.STATUS.NODOMAIN;
		}
		ResolverAnswer.STATUS exp_sts2 = (exp_sts == ResolverAnswer.STATUS.TIMEOUT ? ResolverAnswer.STATUS.NODOMAIN : exp_sts);
		ResolverDNS resolver = dsptch.getNamedItem(ResolverDNS.class.getName(), null);
		logger.info("UTEST: utest.dnsResolved="+cnt_dnscallbacks.get()+"/"+cnt_dnsrequests.get()+": "+answer+" - cbdata="+cbdata);

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
		Class<?> clss_resolver = com.grey.naf.dns.resolver.embedded.EmbeddedResolver.class;
		com.grey.naf.NAFConfig nafcfg = setConfig(null, clss_resolver, flags);
		ApplicationContextNAF appctx = TestUtils.createApplicationContext(null, nafcfg, true);
		DispatcherDef def = new DispatcherDef.Builder()
				.withName(dispatcher_name)
				.withSurviveHandlers(false)
				.build();
		ResolverConfig rcfg = new ResolverConfig.Builder()
				.withXmlConfig(nafcfg.getNode("dnsresolver"))
				.build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, logger);
		ResolverDNS.create(dsptch, rcfg);
		return dsptch;
	}

	// The Resolver will reside in the Dispatcher tagged as the master, and we regard d1 as the local Dispatcher
	private Dispatcher[] createDistributedResolver(int flags, boolean local_master)
			throws java.io.IOException
	{
		Class<?> clss_resolver = com.grey.naf.dns.resolver.distributed.DistributedResolver.class;
		String d1name;
		String d2name;
		com.grey.naf.NAFConfig nafcfg;
		if (local_master) {
			d1name = dispatcher_name+"_master";
			d2name = dispatcher_name+"_slave";
			nafcfg = setConfig(d1name, clss_resolver, flags);
		} else {
			d1name = dispatcher_name+"_slave";
			d2name = dispatcher_name+"_master";
			nafcfg = setConfig(d2name, clss_resolver, flags);
		}
		//we need NAFMAN (enabled by default) to propagate the dsptch.stop() in handleDnsResult() to the other Dispatcher
		ApplicationContextNAF appctx = TestUtils.createApplicationContext(null, nafcfg, true);
		DispatcherDef def = new DispatcherDef.Builder()
				.withName(d1name)
				.withSurviveHandlers(false)
				.build();
		ResolverConfig rcfg = new ResolverConfig.Builder()
				.withXmlConfig(nafcfg.getNode("dnsresolver"))
				.build();
		Dispatcher d1 = Dispatcher.create(appctx, def, logger);
		def = new DispatcherDef.Builder(def).withName(d2name).build();
		Dispatcher d2 = Dispatcher.create(appctx, def, logger);
		if (local_master) { //first resolver to be created becomes the master
			ResolverDNS.create(d1, rcfg);
			ResolverDNS.create(d2, rcfg);
		} else {
			ResolverDNS.create(d2, rcfg);
			ResolverDNS.create(d1, rcfg);
		}
		return new Dispatcher[]{d1, d2};
	}

	private com.grey.naf.NAFConfig setConfig(String master, Class<?> clss_resolver, int flags) throws java.io.IOException
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
		XmlConfig xmlcfg = XmlConfig.makeSection(sb.toString(), "/naf");
		return new NAFConfig.Builder().withXmlConfig(xmlcfg).build();
	}

	private static String addFlag(String cbflags, String newflag)
	{
		if (cbflags == null) return newflag;
		return cbflags+CBDLM+newflag;
	}


	private static class ResolverQueryIssuer implements TimerNAF.Handler {
		private final ResolverDNS.Client resolverClient;
		private final boolean remoteResolver;
		private final AtomicInteger requestsCounter;

		public ResolverQueryIssuer(ResolverDNS.Client resolverClient, boolean remoteResolver, AtomicInteger requestsCounter) {
			this.resolverClient = resolverClient;
			this.remoteResolver = remoteResolver;
			this.requestsCounter = requestsCounter;
		}

		@Override
		public void timerIndication(TimerNAF tmr, Dispatcher dsptch) throws IOException {
			Object[][] queries = new Object[][]{{ResolverDNS.QTYPE_A, queryTargetA},
				{ResolverDNS.QTYPE_PTR, queryTargetPTR, "192.168.240.2"},
				{ResolverDNS.QTYPE_NS, queryTargetNS}, {ResolverDNS.QTYPE_SOA, queryTargetSOA},
				{ResolverDNS.QTYPE_MX, queryTargetMX}, {ResolverDNS.QTYPE_SRV, queryTargetSRV},
				{ResolverDNS.QTYPE_TXT, queryTargetTXT}, {ResolverDNS.QTYPE_AAAA, queryTargetAAAA}};
			boolean nopiggy = remoteResolver; //can't piggyback remote requests as response-callbacks not synchronous
			String cbdata = (remoteResolver ? CBFLAG_DISTRIBREMOTE : null);
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
		}

		@Override
		public void eventError(TimerNAF tmr, Dispatcher d, Throwable ex) throws IOException {
			logger.log(LEVEL.ERR, ex, true, "Query-Issuer time error");
		}

		// We issue an immediate repeat query to exercise the code for piggybacking on the earlier pending one.
		// We can't actually verify it happens though (short of examining the Resolver's debug traces).
		private void issueQuery(Dispatcher d, CharSequence qname, int qtype, String cbdata, boolean nopiggy) throws java.io.IOException
		{
			ResolverDNS resolver = d.getNamedItem(ResolverDNS.class.getName(), null);
			ResolverAnswer answer;
			if (qtype == ResolverDNS.QTYPE_A) {
				ByteChars bc = new ByteChars(qname);
				answer = resolver.resolveHostname(bc, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveHostname(bc, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else if (qtype == ResolverDNS.QTYPE_PTR) {
				int ip = IP.convertDottedIP(qname);
				answer = resolver.resolveIP(ip, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveIP(ip, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else if (qtype == ResolverDNS.QTYPE_NS) {
				ByteChars bc = new ByteChars(qname);
				answer = resolver.resolveNameServer(bc, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveNameServer(bc, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else if (qtype == ResolverDNS.QTYPE_MX) {
				ByteChars bc = new ByteChars(qname);
				answer = resolver.resolveMailDomain(bc, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveMailDomain(bc, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else if (qtype == ResolverDNS.QTYPE_SOA) {
				ByteChars bc = new ByteChars(qname);
				answer = resolver.resolveSOA(bc, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveSOA(bc, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else if (qtype == ResolverDNS.QTYPE_SRV) {
				ByteChars bc = new ByteChars(qname);
				answer = resolver.resolveSRV(bc, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveSRV(bc, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else if (qtype == ResolverDNS.QTYPE_TXT) {
				ByteChars bc = new ByteChars(qname);
				answer = resolver.resolveTXT(bc, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveTXT(bc, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else if (qtype == ResolverDNS.QTYPE_AAAA) {
				ByteChars bc = new ByteChars(qname);
				answer = resolver.resolveAAAA(bc, resolverClient, cbdata, 0);
				org.junit.Assert.assertNull(answer);
				if (!nopiggy) answer = resolver.resolveAAAA(bc, resolverClient, addFlag(cbdata,CBFLAG_PIGGY), 0);
			} else {
				throw new RuntimeException("Missing issueQuery() case for qtype="+qtype);
			}
			org.junit.Assert.assertNull(answer);
			requestsCounter.incrementAndGet();
			if (!nopiggy) requestsCounter.incrementAndGet(); //and there was one more
		}
	}
}
