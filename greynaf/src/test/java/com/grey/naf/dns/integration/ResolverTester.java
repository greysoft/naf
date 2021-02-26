/*
 * Copyright 2015-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.integration;

import java.util.HashSet;

import com.grey.base.collections.HashedMapIntKey;
import com.grey.base.config.SysProps;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.IP;
import com.grey.base.utils.DynLoader;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.dns.resolver.PacketDNS;
import com.grey.naf.dns.resolver.ResolverAnswer;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.ResourceData;

public class ResolverTester
{
	// check if DNS queries are possible
	private static final String SYSPROP_SKIPDNS = "greynaf.test.skipdns";
	public static final boolean HAVE_DNS_SERVICE;
	static {
		String reason = null;
		if (SysProps.get(SYSPROP_SKIPDNS, true)) {
			reason = "Disabled by config: "+SYSPROP_SKIPDNS;
		} else {
			// we could simply look up www.google.com, but this root-nameservers lookup seems a bit more neutral
			System.out.println("Probing for DNS service ..."); //failure can be quite slow
			java.util.Hashtable<String, String> envinput = new java.util.Hashtable<String, String>();
			envinput.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
			javax.naming.directory.DirContext ctx = null;
			try {
				ctx = new javax.naming.directory.InitialDirContext(envinput);
				javax.naming.directory.Attributes attrs = ctx.getAttributes(".", new String[] {"NS"});
				javax.naming.directory.Attribute attr = attrs.get("NS");
				if (attr == null || attr.size() == 0) reason = attrs+" - "+attr;
			} catch (javax.naming.NamingException ex) {
				reason = "probe lookup failed - "+ex;
			} finally {
				try {
					if (ctx != null) ctx.close();
				} catch (javax.naming.NamingException ex) {
					System.out.println("WARNING: Failed to close JNDI context - "+ex);
				}
			}
		}
		HAVE_DNS_SERVICE = (reason == null);
		if (!HAVE_DNS_SERVICE) System.out.println("WARNING: DNS-dependent tests cannot be performed - "+reason);
	}

	public static final String queryTargetA = "www.google.com";
	public static final String queryTargetCNAME = "mail.google.com";
	public static final String queryTargetPTR = "158.152.1.65";
	public static final String queryTargetNS = "net";
	public static final String queryTargetMX = "ibm.com"; //triggers QTYPE_A follow-on
	public static final String queryTargetSOA = "google.com";
	public static final String queryTargetTXT = queryTargetSOA;
	public static final String queryTargetSRV = "_xmpp-client._tcp.google.com";
	public static final String queryTargetAAAA = "ns1-99.akam.net";

	protected static final boolean USE_REAL_DNS = SysProps.get("greynaf.dns.test.realdns", false);
	protected static final String dnsservers = SysProps.get("greynaf.dns.test.localservers");
	protected static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("no-such-logger");
	protected static TestServerDNS mockserver;

	protected boolean is_mockserver;
	protected int rspcnt_nodom;
	protected int rspcnt_tmt;

	@org.junit.BeforeClass
	public static void beforeClass() throws java.io.IOException
	{
		if (!USE_REAL_DNS) {
			mockserver = new TestServerDNS(ApplicationContextNAF.create(null));
			mockserver.start();
		}
	}

	@org.junit.AfterClass
	public static void afterClass()
	{
		if (mockserver != null) mockserver.stop();
	}

	protected void assertAnswer(ResolverAnswer.STATUS exp_sts, byte exp_qtype, int exp_ip, ResolverAnswer answer)
	{
		org.junit.Assert.assertNotNull(answer);
		logger.info("UTEST: Answer for "+ResolverDNS.getQTYPE(exp_qtype)+"="+answer.qname+": "+answer);
		int rrsize = 1;
		if (exp_qtype == ResolverDNS.QTYPE_NS || exp_qtype == ResolverDNS.QTYPE_SRV) {
			rrsize = -2;
		} else if (exp_qtype == ResolverDNS.QTYPE_MX) {
			rrsize = -1;
		}
		assertAnswer(answer, exp_sts, exp_qtype, rrsize);
		org.junit.Assert.assertNotNull(answer.qname);
		org.junit.Assert.assertEquals(0, answer.qip);
		if (answer.result == ResolverAnswer.STATUS.OK && exp_qtype == ResolverDNS.QTYPE_A) {
			if (answer.qname.toString().equals(queryTargetCNAME)) {
				org.junit.Assert.assertFalse(answer.toString(), answer.qname.equals(answer.get(0).getName()));
			} else {
				org.junit.Assert.assertEquals(answer.toString(), answer.qname, answer.get(0).getName());
			}
		}
		if (exp_ip != 0) org.junit.Assert.assertEquals(exp_ip, answer.get(0).getIP());
	}

	protected void assertAnswer(ResolverAnswer.STATUS exp_sts, byte exp_qtype, CharSequence exp_domnam, ResolverAnswer answer)
	{
		org.junit.Assert.assertNotNull(answer);
		logger.info("UTEST: Answer for IP/"+ResolverDNS.getQTYPE(exp_qtype)+"="+IP.displayDottedIP(answer.qip)+": "+answer);
		assertAnswer(answer, exp_sts, exp_qtype, 1);
		org.junit.Assert.assertNull(answer.qname);
		org.junit.Assert.assertFalse(answer.qip == 0);
		if (answer.result == ResolverAnswer.STATUS.OK) {
			org.junit.Assert.assertEquals(answer.toString(), answer.qip, answer.get(0).getIP());
		}
		if (exp_domnam != null) {
			org.junit.Assert.assertTrue(StringOps.sameSeq(exp_domnam, answer.get(0).getName()));
		}
	}

	private void assertAnswer(ResolverAnswer answer, ResolverAnswer.STATUS exp_sts, byte exp_qtype, int exp_rrsize)
	{
		org.junit.Assert.assertEquals(exp_sts, answer.result);
		org.junit.Assert.assertEquals(exp_qtype, answer.qtype);
		if (answer.result == ResolverAnswer.STATUS.NODOMAIN) rspcnt_nodom++;
		if (answer.result == ResolverAnswer.STATUS.TIMEOUT) rspcnt_tmt++;

		if (exp_sts != ResolverAnswer.STATUS.OK) exp_rrsize = 0;
		if (exp_rrsize < 0) {
			org.junit.Assert.assertTrue(answer.toString(), answer.size() >= -exp_rrsize);
		} else {
			org.junit.Assert.assertEquals(answer.toString(), exp_rrsize, answer.size());
		}

		for (int idx = 0; idx != answer.size(); idx++) {
			ResourceData rr = answer.get(idx);
			org.junit.Assert.assertEquals(PacketDNS.QCLASS_INET, rr.rrClass());
			org.junit.Assert.assertEquals(answer.qtype, rr.rrType());
			if (answer.result == ResolverAnswer.STATUS.OK) {
				if (exp_qtype == ResolverDNS.QTYPE_SOA || exp_qtype == ResolverDNS.QTYPE_TXT || exp_qtype == ResolverDNS.QTYPE_AAAA) {
					org.junit.Assert.assertTrue(answer.toString(), rr.getIP() == 0);
				} else {
					org.junit.Assert.assertTrue(answer.toString(), rr.getIP() != 0);
				}
				org.junit.Assert.assertNotNull(rr.toString(), rr.getName());
			} else {
				org.junit.Assert.assertTrue(answer.toString(), rr.getIP() == 0);
				org.junit.Assert.assertNull(rr.toString(), rr.getName());
			}
		}

		if (is_mockserver && answer.result == ResolverAnswer.STATUS.OK) {
			ResourceData rr0 = answer.get(0);
			HashSet<String> hostnames = new HashSet<String>();
			switch (answer.qtype) {
			case ResolverDNS.QTYPE_A:
				org.junit.Assert.assertSame(rr0, answer.getA());
				if (StringOps.sameSeq(queryTargetA, answer.qname)) {
					org.junit.Assert.assertEquals(answer.qname, rr0.getName());
					org.junit.Assert.assertEquals("62.253.72.173", IP.displayDottedIP(rr0.getIP()).toString());
				} else if (StringOps.sameSeq(queryTargetCNAME, answer.qname)) {
					org.junit.Assert.assertEquals("googlemail.l.google.com", rr0.getName().toString());
					org.junit.Assert.assertEquals("216.58.208.69", IP.displayDottedIP(rr0.getIP()).toString());
				} else if (StringOps.sameSeq("simulate-timeout.net", answer.qname)) {
					org.junit.Assert.assertEquals(answer.qname, rr0.getName());
					org.junit.Assert.assertEquals("192.168.201.1", IP.displayDottedIP(rr0.getIP()).toString());
				}
				break;
			case ResolverDNS.QTYPE_PTR:
				if (answer.qip == IP.convertDottedIP(queryTargetPTR)) {
					org.junit.Assert.assertEquals("ns0.demon.co.uk", rr0.getName().toString());
					org.junit.Assert.assertEquals(queryTargetPTR, IP.displayDottedIP(rr0.getIP()).toString());
				}
				break;
			case ResolverDNS.QTYPE_SOA:
				if (StringOps.sameSeq(queryTargetSOA, answer.qname)) {
					ResourceData.RR_SOA rr = answer.getSOA();
					org.junit.Assert.assertSame(rr0, rr);
					org.junit.Assert.assertEquals(answer.qname, rr0.getName());
					org.junit.Assert.assertEquals(0, rr0.getIP());
					org.junit.Assert.assertEquals("ns4.google.com", rr.getMNAME().toString());
					org.junit.Assert.assertEquals("dns-admin.google.com", rr.getRNAME().toString());
					org.junit.Assert.assertEquals(107620502, rr.getSerial());
					org.junit.Assert.assertEquals(900, rr.getRefresh());
					org.junit.Assert.assertEquals(900, rr.getRetry());
					org.junit.Assert.assertEquals(1800, rr.getZoneExpiry());
					org.junit.Assert.assertEquals(60, rr.getMinimumTTL());
				}
				break;
			case ResolverDNS.QTYPE_TXT:
				if (StringOps.sameSeq(queryTargetTXT, answer.qname)) {
					ResourceData.RR_TXT rr = answer.getTXT();
					org.junit.Assert.assertSame(rr0, rr);
					org.junit.Assert.assertEquals(answer.qname, rr0.getName());
					org.junit.Assert.assertEquals(0, rr0.getIP());
					org.junit.Assert.assertEquals(2, rr.count());
					org.junit.Assert.assertEquals("Text Rec 1", rr.getData(0));
					org.junit.Assert.assertEquals("Text Rec 2", rr.getData(1));
				}
				break;
			case ResolverDNS.QTYPE_AAAA:
				if (StringOps.sameSeq(queryTargetAAAA, answer.qname)) {
					ResourceData.RR_AAAA rr = answer.getAAAA();
					org.junit.Assert.assertSame(rr0, rr);
					org.junit.Assert.assertEquals(answer.qname, rr0.getName());
					org.junit.Assert.assertEquals(0, rr0.getIP());
					org.junit.Assert.assertEquals("2600:1401:2::63", IP.displayIP6(rr.getIP6(), 0).toString());
				}
				break;
			case ResolverDNS.QTYPE_NS:
				if (StringOps.sameSeq(queryTargetNS, answer.qname)) {
					org.junit.Assert.assertEquals(answer.toString(), 4, answer.size());
					for (int idx = 0; idx != answer.size(); idx++) {
						ResourceData.RR_NS rr = answer.getNS(idx);
						org.junit.Assert.assertEquals(answer.qname, rr.getName());
						String hostname = rr.getHostname().toString();
						hostnames.add(hostname);
						if (hostname.equals("a.gtld-servers.net")) {
							org.junit.Assert.assertEquals("192.5.6.30", IP.displayDottedIP(rr.getIP()).toString());
						} else if (hostname.equals("b.gtld-servers.net")) {
							org.junit.Assert.assertEquals("192.33.14.30", IP.displayDottedIP(rr.getIP()).toString());
						} else if (hostname.equals("c.gtld-servers.net")) {
							org.junit.Assert.assertEquals("192.26.92.30", IP.displayDottedIP(rr.getIP()).toString());
						} else if (hostname.equals("192.31.80.30")) {
							org.junit.Assert.assertEquals("192.31.80.30", IP.displayDottedIP(rr.getIP()).toString());
						} else {
							org.junit.Assert.fail("Unexpected NS answer RR - "+rr);
						}
					}
					org.junit.Assert.assertEquals(hostnames.toString(), answer.size(), hostnames.size());
				} else if (StringOps.sameSeq("forced-truncation.net", answer.qname)) {
					org.junit.Assert.assertEquals(8, answer.size());
					ResourceData.RR_NS rr = answer.getNS(0);
					org.junit.Assert.assertEquals("nameserver0.truncnameservers.net", rr.getHostname().toString());
					org.junit.Assert.assertEquals("192.33.14.0", IP.displayDottedIP(rr.getIP()).toString());
					rr = answer.getNS(7);
					org.junit.Assert.assertEquals("nameserver7.truncnameservers.net", rr.getHostname().toString());
					org.junit.Assert.assertEquals("192.33.14.7", IP.displayDottedIP(rr.getIP()).toString());
				}
				break;
			case ResolverDNS.QTYPE_MX:
				if (StringOps.sameSeq(queryTargetMX, answer.qname)) {
					org.junit.Assert.assertEquals(answer.toString(), 3, answer.size());
					for (int idx = 0; idx != answer.size(); idx++) {
						ResourceData.RR_MX rr = answer.getMX(idx);
						org.junit.Assert.assertEquals(answer.qname, rr.getName());
						String hostname = rr.getRelay().toString();
						hostnames.add(hostname);
						if (hostname.equals("e11.ny.us.ibm.com")) {
							org.junit.Assert.assertEquals("129.33.205.201", IP.displayDottedIP(rr.getIP()).toString());
						} else if (hostname.equals("e12.ny.us.ibm.com")) {
							org.junit.Assert.assertEquals("129.33.205.202", IP.displayDottedIP(rr.getIP()).toString());
						} else if (hostname.equals("e13.ny.us.ibm.com")) { //retrieved by follow-on subquery
							org.junit.Assert.assertEquals("129.33.205.203", IP.displayDottedIP(rr.getIP()).toString());
						} else {
							org.junit.Assert.fail("Unexpected MX answer RR - "+rr);
						}
						org.junit.Assert.assertEquals(10, rr.getPreference());
					}
					org.junit.Assert.assertEquals(hostnames.toString(), answer.size(), hostnames.size());
				}
				break;
			case ResolverDNS.QTYPE_SRV:
				if (StringOps.sameSeq(queryTargetSRV, answer.qname)) {
					org.junit.Assert.assertEquals(answer.toString(), 3, answer.size());
					for (int idx = 0; idx != answer.size(); idx++) {
						ResourceData.RR_SRV rr = answer.getSRV(idx);
						org.junit.Assert.assertEquals(answer.qname, rr.getName());
						String hostname = rr.getTarget().toString();
						hostnames.add(hostname);
						if (hostname.equals("alt2.xmpp.l.google.com")) {
							org.junit.Assert.assertEquals("74.125.141.125", IP.displayDottedIP(rr.getIP()).toString());
						} else if (hostname.equals("alt4.xmpp.l.google.com")) {
							org.junit.Assert.assertEquals("74.125.141.125", IP.displayDottedIP(rr.getIP()).toString());
						} else if (hostname.equals("xmpp.l.google.com")) {
							org.junit.Assert.assertEquals("74.125.141.125", IP.displayDottedIP(rr.getIP()).toString());
						} else {
							org.junit.Assert.fail("Unexpected SRV answer RR - "+rr);
						}
						org.junit.Assert.assertEquals("xmpp-client", rr.getService());
						org.junit.Assert.assertEquals("tcp", rr.getProtocol());
						org.junit.Assert.assertEquals("google.com", rr.getDomain());
						org.junit.Assert.assertEquals(5222, rr.getPort());
						org.junit.Assert.assertEquals(20, rr.getPriority());
						org.junit.Assert.assertEquals(0, rr.getWeight());
					}
					org.junit.Assert.assertEquals(hostnames.toString(), answer.size(), hostnames.size());
				}
				break;
			}
		}
	}

	protected static void validateFinalState(ResolverDNS r)
	{
		Object rs = getResolverService(r);
		Object xmtmgr = DynLoader.getField(rs, "xmtmgr");
		validateObjectWell(rs, "anstore", false);
		validateObjectWell(rs, "bcstore", false);
		verifyEmptyMap(rs, "pendingdoms_mx", false);
		verifyEmptyMap(rs, "pendingdoms_soa", false);
		verifyEmptyMap(rs, "pendingdoms_srv", false);
		verifyEmptyMap(rs, "pendingdoms_txt", false);
		verifyEmptyMap(rs, "pendingdoms_aaaa", false);
		verifyEmptyIntMap(rs, "pendingdoms_ptr", false);
		verifyEmptySet(rs, "wrapblocked", false);
		validateObjectWell(xmtmgr, "tcpstore", false);
		int caller_errors = ((Integer)DynLoader.getField(rs, "caller_errors")).intValue();
		org.junit.Assert.assertEquals(0, caller_errors);
		// these can be intermittently non-empty, due to the parents completing after partial-notify
		verifyEmptySet(rs, "activereqs", true);
		verifyEmptyIntMap(rs, "pendingreqs", true);
		verifyEmptyMap(rs, "pendingdoms_a", true);
		verifyEmptyMap(rs, "pendingdoms_ns", true);
		validateObjectWell(rs, "qrystore", true);
		validateObjectWell(rs, "rrwstore", true);
	}

	protected static void validatePendingRequests(ResolverDNS r, int exp_reqs, int exp_callers)
	{
		Object rs = getResolverService(r);
		HashedMapIntKey<?> pendingreqs = (HashedMapIntKey<?>)DynLoader.getField(rs, "pendingreqs");
		int num_callers = 0;
		java.util.Iterator<?> it = pendingreqs.valuesIterator();
		while (it.hasNext()) {
			Object qh = it.next();
			java.util.ArrayList<?> callers = (java.util.ArrayList<?>)DynLoader.getField(qh, "callers");
			num_callers += callers.size();
		}
		org.junit.Assert.assertEquals(exp_reqs, pendingreqs.size());
		org.junit.Assert.assertEquals(exp_callers, num_callers);
	}

	protected static void validateObjectWell(Object rs, String fldnam, boolean allow)
	{
		com.grey.base.collections.ObjectWell<?> ow = (com.grey.base.collections.ObjectWell<?>)DynLoader.getField(rs, fldnam);
		String txt = "ObjectWell="+ow.name;
		if (allow) {
			if (ow.size() != ow.population()) System.out.println("REMNANT COLLECTION: "+txt+" - Size="+ow.size()+" vs pop="+ow.population());
		} else {
			org.junit.Assert.assertEquals(txt, ow.size(), ow.population());
		}
	}

	private static void verifyEmptySet(Object rs, String fldnam, boolean allow)
	{
		com.grey.base.collections.HashedSet<?> coll = (com.grey.base.collections.HashedSet<?>)DynLoader.getField(rs, fldnam);
		String txt = fldnam+"="+coll.toString();
		if (allow) {
			if (coll.size() != 0) System.out.println("REMNANT COLLECTION: "+txt);
		} else {
			org.junit.Assert.assertEquals(fldnam+"="+coll.toString(), 0, coll.size());
		}
	}

	private static void verifyEmptyMap(Object rs, String fldnam, boolean allow)
	{
		com.grey.base.collections.HashedMap<?, ?> coll = (com.grey.base.collections.HashedMap<?, ?>)DynLoader.getField(rs, fldnam);
		String txt = fldnam+"="+coll.toString();
		if (allow) {
			if (coll.size() != 0) System.out.println("REMNANT COLLECTION: "+txt);
		} else {
			org.junit.Assert.assertEquals(fldnam+"="+coll.toString(), 0, coll.size());
		}
	}

	private static void verifyEmptyIntMap(Object rs, String fldnam, boolean allow)
	{
		HashedMapIntKey<?> coll = (HashedMapIntKey<?>)DynLoader.getField(rs, fldnam);
		String txt = fldnam+"="+coll.toString();
		if (allow) {
			if (coll.size() != 0) System.out.println("REMNANT COLLECTION: "+txt);
		} else {
			org.junit.Assert.assertEquals(fldnam+"="+coll.toString(), 0, coll.size());
		}
	}

	protected static Object getResolverService(ResolverDNS r)
	{
		if (r.getClass() == com.grey.naf.dns.resolver.embedded.EmbeddedResolver.class) {
			return DynLoader.getField(r, "rslvr");
		}
		if (r.getClass() == com.grey.naf.dns.resolver.distributed.Client.class) {
			Object proxy = DynLoader.getField(r, "proxy");
			return DynLoader.getField(proxy, "rslvr");
		}
		throw new UnsupportedOperationException("Unrecognised Resolver type="+r.getClass().getName());
	}
}