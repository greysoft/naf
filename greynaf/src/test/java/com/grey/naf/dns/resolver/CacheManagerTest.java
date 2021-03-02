/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver;

import com.grey.base.config.XmlConfig;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.base.collections.HashedMap;
import com.grey.base.collections.HashedMapIntKey;
import com.grey.base.utils.IP;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.dns.integration.ResolverTester;
import com.grey.naf.dns.resolver.CacheManager;
import com.grey.naf.dns.resolver.PacketDNS;
import com.grey.naf.dns.resolver.ResolverAnswer;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.ResourceData;
import com.grey.naf.reactor.Dispatcher;

public class CacheManagerTest
{
	private static final String rootdir = com.grey.naf.reactor.DispatcherTest.initPaths(CacheManagerTest.class);
	private static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("no-such-logger");
	private static final java.io.File CFGFILE_ROOTS = new java.io.File(rootdir+"/rootservers");

	private static final ApplicationContextNAF appctx = ApplicationContextNAF.create(null);
	private Dispatcher dsptch;
	private CacheManager cmgr;

	@org.junit.Before
	public void setup() throws Exception
	{
		FileOps.deleteDirectory(rootdir);
		FileOps.ensureDirExists(rootdir);
	}

	@org.junit.After
	public void teardown() throws Exception
	{
		if (dsptch != null) dsptch.stop();
	}

	@org.junit.Test
	public void testRootsManual() throws Exception
	{
		String cfgroots = ". : 68.253.72.177:MANUAL"
				+"\nDomain1:192.168.200.1. : MANUAL"
				+"\n domain1\t : 192.168.200.2 : MANUAL "
				+"\ndoMain2. : \t192.168.200.1 : MANUAL"
				+"\ndomain2 : \t192.168.200.3 : MANUAL";
		FileOps.writeTextFile(CFGFILE_ROOTS, cfgroots, false);
		String cfgtxt = "<dnsresolver recursive=\"N\">"
				+"\n\t<rootservers auto=\"N\">"+CFGFILE_ROOTS.getAbsolutePath()+"</rootservers>"
				+"\n\t</dnsresolver>";
		XmlConfig dnscfg = XmlConfig.makeSection(cfgtxt, "dnsresolver");
		createManager(dnscfg);
		java.util.Set<?> rootdomains = (java.util.Set<?>)DynLoader.getField(cmgr, "ns_roots");
		java.util.Set<?> rootservers = (java.util.Set<?>)DynLoader.getField(cmgr, "ns_roots_a");
		HashedMap<?, ?> nscache = (HashedMap<?, ?>)DynLoader.getField(cmgr, "cache_ns");
		HashedMapIntKey<?> srvcache = (HashedMapIntKey<?>)DynLoader.getField(cmgr, "cache_nameservers");
		ByteChars domroot = new ByteChars(".");
		ByteChars domnam1 = new ByteChars("domain1"); //NB: the configured names should get lower-cased
		ByteChars domnam2 = new ByteChars("domain2");
		ByteChars rootdom_ip = new ByteChars("68.253.72.177");
		ByteChars dom1_ip1 = new ByteChars("192.168.200.1");
		ByteChars dom1_ip2 = new ByteChars("192.168.200.2");
		ByteChars dom2_ip1 = dom1_ip1;
		ByteChars dom2_ip2 = new ByteChars("192.168.200.3");

		org.junit.Assert.assertEquals(rootdomains.toString(), 3, rootdomains.size());
		org.junit.Assert.assertEquals(rootservers.toString(), 4, rootservers.size());
		org.junit.Assert.assertEquals(nscache.toString(), rootdomains.size(), nscache.size());
		org.junit.Assert.assertEquals(srvcache.toString(), 0, srvcache.size());
		org.junit.Assert.assertTrue(rootdomains.toString(), rootdomains.contains(domroot));
		org.junit.Assert.assertTrue(rootdomains.toString(), rootdomains.contains(domnam1));
		org.junit.Assert.assertTrue(rootdomains.toString(), rootdomains.contains(domnam2));
		org.junit.Assert.assertTrue(rootservers.toString(), rootservers.contains(rootdom_ip));
		org.junit.Assert.assertTrue(rootservers.toString(), rootservers.contains(dom1_ip1));
		org.junit.Assert.assertTrue(rootservers.toString(), rootservers.contains(dom1_ip2));
		org.junit.Assert.assertTrue(rootservers.toString(), rootservers.contains(dom2_ip2));

		java.util.ArrayList<?> lst = (java.util.ArrayList<?>)nscache.get(domroot);
		org.junit.Assert.assertEquals(lst.toString(), 1, lst.size());
		ResourceData.RR_NS rr = (ResourceData.RR_NS)lst.get(0);
		org.junit.Assert.assertEquals(domroot, rr.getName());
		org.junit.Assert.assertEquals(rootdom_ip, rr.getHostname());
		org.junit.Assert.assertEquals(IP.convertDottedIP(rr.getHostname()), rr.getIP());
		org.junit.Assert.assertEquals(Long.MAX_VALUE, rr.getExpiry());
		org.junit.Assert.assertEquals(PacketDNS.QCLASS_INET, rr.rrClass());

		lst = (java.util.ArrayList<?>)nscache.get(domnam1);
		org.junit.Assert.assertEquals(lst.toString(), 2, lst.size());
		rr = (ResourceData.RR_NS)lst.get(0);
		ResourceData.RR_NS rr2 = (ResourceData.RR_NS)lst.get(1);
		org.junit.Assert.assertEquals(domnam1, rr.getName());
		org.junit.Assert.assertEquals(domnam1, rr2.getName());
		org.junit.Assert.assertEquals(IP.convertDottedIP(rr.getHostname()), rr.getIP());
		org.junit.Assert.assertEquals(IP.convertDottedIP(rr2.getHostname()), rr2.getIP());
		if (rr.getHostname().equals(dom1_ip1)) {
			org.junit.Assert.assertEquals(dom1_ip2, rr2.getHostname());
		} else {
			org.junit.Assert.assertEquals(dom1_ip2, rr.getHostname());
			org.junit.Assert.assertEquals(dom1_ip1, rr2.getHostname());
		}

		lst = (java.util.ArrayList<?>)nscache.get(domnam2);
		org.junit.Assert.assertEquals(lst.toString(), 2, lst.size());
		rr = (ResourceData.RR_NS)lst.get(0);
		rr2 = (ResourceData.RR_NS)lst.get(1);
		org.junit.Assert.assertEquals(domnam2, rr.getName());
		org.junit.Assert.assertEquals(domnam2, rr2.getName());
		org.junit.Assert.assertEquals(IP.convertDottedIP(rr.getHostname()), rr.getIP());
		org.junit.Assert.assertEquals(IP.convertDottedIP(rr2.getHostname()), rr2.getIP());
		if (rr.getHostname().equals(dom2_ip1)) {
			org.junit.Assert.assertEquals(dom2_ip2, rr2.getHostname());
		} else {
			org.junit.Assert.assertEquals(dom2_ip2, rr.getHostname());
			org.junit.Assert.assertEquals(dom2_ip1, rr2.getHostname());
		}

		//test a reload
		cmgr.loadRootServers();
		org.junit.Assert.assertEquals(rootdomains.toString(), 3, rootdomains.size());
		org.junit.Assert.assertEquals(rootservers.toString(), 4, rootservers.size());
		org.junit.Assert.assertEquals(nscache.toString(), rootdomains.size(), nscache.size());
		org.junit.Assert.assertEquals(srvcache.toString(), 0, srvcache.size());
	}

	@org.junit.Test
	public void testRootsAuto() throws Exception
	{
		org.junit.Assume.assumeTrue(ResolverTester.HAVE_DNS_SERVICE);
		String cfgtxt = "<dnsresolver recursive=\"N\"/>";
		XmlConfig dnscfg = XmlConfig.makeSection(cfgtxt, "dnsresolver");
		createManager(dnscfg);
		java.util.Set<?> rootdomains = (java.util.Set<?>)DynLoader.getField(cmgr, "ns_roots");
		java.util.Set<?> rootservers = (java.util.Set<?>)DynLoader.getField(cmgr, "ns_roots_a");
		HashedMap<?, ?> nscache = (HashedMap<?, ?>)DynLoader.getField(cmgr, "cache_ns");
		HashedMapIntKey<?> srvcache = (HashedMapIntKey<?>)DynLoader.getField(cmgr, "cache_nameservers");
		final int siz1_roots = rootdomains.size();
		final int siz1_roots_a = rootservers.size();
		final int siz1_nscache = nscache.size();
		java.util.ArrayList<?> lst = (java.util.ArrayList<?>)nscache.values().iterator().next();
		org.junit.Assert.assertEquals(1, siz1_roots);
		org.junit.Assert.assertEquals(1, siz1_nscache);
		org.junit.Assert.assertEquals(0, srvcache.size());
		org.junit.Assert.assertNotEquals(0, siz1_roots_a);
		org.junit.Assert.assertEquals(siz1_roots_a, lst.size());

		//verify that caching a regular NS record doesn't affect the other stores
		ByteChars ns1 = new ByteChars("ns1.dom.org");
		int ip1 = 101;
		java.util.ArrayList<ResourceData.RR_NS> nslst = new java.util.ArrayList<ResourceData.RR_NS>();
		nslst.add(new ResourceData.RR_NS(ns1, new ByteChars("host1.dom.org"), ip1, dsptch.getSystemTime()+5000));
		storeNameServers(ns1, nslst);
		org.junit.Assert.assertEquals(siz1_nscache+1, nscache.size());
		ByteChars ns2 = new ByteChars("ns2.dom.org");
		storeNameServers(ns2, null);
		org.junit.Assert.assertEquals(siz1_nscache+2, nscache.size());
		org.junit.Assert.assertEquals(siz1_roots, rootdomains.size());
		org.junit.Assert.assertEquals(siz1_roots_a, rootservers.size());
		org.junit.Assert.assertEquals(0, srvcache.size());

		//verify that the nameserver-addresses cache doesn't affect the others
		java.net.InetSocketAddress tsap1 = cmgr.lookupNameServer(ns1);
		org.junit.Assert.assertEquals(PacketDNS.INETPORT, tsap1.getPort());
		org.junit.Assert.assertEquals(1, srvcache.size());
		java.net.InetSocketAddress tsap = cmgr.lookupNameServer(ns2);
		org.junit.Assert.assertEquals(0, tsap.getPort()); //because ns2 was cached as an NXDOM
		org.junit.Assert.assertEquals(1, srvcache.size());
		org.junit.Assert.assertEquals(siz1_roots, rootdomains.size());
		org.junit.Assert.assertEquals(siz1_roots_a, rootservers.size());
		org.junit.Assert.assertEquals(siz1_nscache+2, nscache.size());

		//test a reload
		cmgr.loadRootServers();
		org.junit.Assert.assertEquals(1, srvcache.size());
		org.junit.Assert.assertEquals(siz1_roots, rootdomains.size());
		org.junit.Assert.assertEquals(siz1_roots_a, rootservers.size());
		org.junit.Assert.assertEquals(siz1_nscache+2, nscache.size());
		org.junit.Assert.assertEquals(ip1, srvcache.keysIterator().next());

		tsap = cmgr.lookupNameServer((ByteChars)rootdomains.iterator().next());
		org.junit.Assert.assertEquals(PacketDNS.INETPORT, tsap.getPort());
		org.junit.Assert.assertEquals(2, srvcache.size());
		org.junit.Assert.assertEquals(siz1_roots, rootdomains.size());
		org.junit.Assert.assertEquals(siz1_roots_a, rootservers.size());
		org.junit.Assert.assertEquals(siz1_nscache+2, nscache.size());
		cmgr.loadRootServers();
		org.junit.Assert.assertEquals(1, srvcache.size()); //because it pruned the rootserver TSAP we looked up above
		org.junit.Assert.assertEquals(siz1_roots, rootdomains.size());
		org.junit.Assert.assertEquals(siz1_roots_a, rootservers.size());
		org.junit.Assert.assertEquals(siz1_nscache+2, nscache.size());
		org.junit.Assert.assertEquals(ip1, srvcache.keysIterator().next());
	}

	@org.junit.Test
	public void testCacheSingular() throws Exception
	{
		createManager(null);
		HashedMap<?, ?> cache = (HashedMap<?, ?>)DynLoader.getField(cmgr, "cache_a");
		ByteChars host1 = new ByteChars("host1.dom.org");
		int ip1 = IP.convertDottedIP("192.168.99.1");
		long ttl1 = 10;
		ByteChars host2 = new ByteChars("host2.dom.org");
		int ip2 = IP.convertDottedIP("192.168.99.2");
		long ttl2 = 20;
		ByteChars host3 = new ByteChars("nosuchhost.dom.org");

		org.junit.Assert.assertEquals(0, cache.size());
		storeHost(host1, ip1, ttl1);
		storeHost(host2, ip2, ttl2);
		storeHost(host3, 0, -ttl2);
		org.junit.Assert.assertEquals(3, cache.size());

		DynLoader.setField(dsptch, "systime_msecs", dsptch.getSystemTime() + ttl1 + 1);
		ResourceData rr = cmgr.lookup(ResolverDNS.QTYPE_A, host1);
		org.junit.Assert.assertNull(rr);
		org.junit.Assert.assertEquals(2, cache.size());
		rr = cmgr.lookup(ResolverDNS.QTYPE_A, host2);
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_A, rr.rrType());
		rr = cmgr.lookup(ResolverDNS.QTYPE_A, host3);
		org.junit.Assert.assertTrue(rr.isNegative());
		org.junit.Assert.assertEquals(2, cache.size());

		storeHost(new ByteChars("host3.lohitest"), 103, ttl2);
		storeHost(new ByteChars("host4.lohitest"), 104, ttl2);
		storeHost(new ByteChars("host5.lohitest"), 105, ttl2);
		storeHost(new ByteChars("host6.lohitest"), 106, ttl2);
		storeHost(new ByteChars("host7.lohitest"), 107, ttl2);
		org.junit.Assert.assertEquals(7, cache.size()); //at hiwater level
		ByteChars host8 = new ByteChars("host8.lohitest");
		storeHost(host8, 108, ttl2);
		org.junit.Assert.assertEquals(5, cache.size()); //pruned back to lowater and then added host8
		rr = cmgr.lookup(ResolverDNS.QTYPE_A, host8);
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_A, rr.rrType());
		org.junit.Assert.assertTrue(rr.getName() == host8);
		org.junit.Assert.assertEquals(108, rr.getIP());
		org.junit.Assert.assertEquals(5, cache.size());

		// this exercises the case where STATUS=OK converts to NODOMAIN due to absence of RRs
		ByteChars host99 = new ByteChars("host99.dom.org");
		ResolverAnswer ans = new ResolverAnswer().set(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_A, host99);
		ResolverAnswer.STATUS result = cmgr.storeResult(ans);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.NODOMAIN, result);
		org.junit.Assert.assertEquals(0, ans.rrdata.size());
		org.junit.Assert.assertEquals(6, cache.size());
		rr = cmgr.lookup(ResolverDNS.QTYPE_A, host99);
		org.junit.Assert.assertEquals(6, cache.size());
		org.junit.Assert.assertTrue(rr.toString(), rr.isNegative());
		org.junit.Assert.assertNull(rr.toString(), rr.getName());
		org.junit.Assert.assertEquals(rr.toString(), 0, rr.getIP());
		org.junit.Assert.assertEquals(rr.toString(), PacketDNS.QCLASS_INET, rr.rrClass());

		// this exercises the case where negative Answer already contains RR, before storeResult()
		ByteChars host99b = new ByteChars("host99b.dom.org");
		ans = new ResolverAnswer().set(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_A, host99b);
		rr = ResourceData.createNegativeRR(ResolverDNS.QTYPE_A, ttl2);
		ans.rrdata.add(rr);
		result = cmgr.storeResult(ans);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.NODOMAIN, result);
		org.junit.Assert.assertEquals(0, ans.rrdata.size());
		org.junit.Assert.assertEquals(7, cache.size());
		rr = cmgr.lookup(ResolverDNS.QTYPE_A, host99);
		org.junit.Assert.assertEquals(7, cache.size());
		org.junit.Assert.assertTrue(rr.toString(), rr.isNegative());
		org.junit.Assert.assertNull(rr.toString(), rr.getName());
		org.junit.Assert.assertEquals(rr.toString(), 0, rr.getIP());
		org.junit.Assert.assertEquals(rr.toString(), PacketDNS.QCLASS_INET, rr.rrClass());

		try {
			cmgr.lookup(ResolverDNS.QTYPE_NS, host1);
			org.junit.Assert.fail("Did not trap invalid type-NS singular lookup");
		} catch (UnsupportedOperationException ex) {}
		org.junit.Assert.assertEquals(7, cache.size());

		ans = new ResolverAnswer().set(ResolverAnswer.STATUS.NODOMAIN, ResolverDNS.QTYPE_ALL, new ByteChars("anyname"));
		try {
			cmgr.storeResult(ans);
			org.junit.Assert.fail("Did not trap invalid negative store qtype");
		} catch (IllegalArgumentException ex) {}
		ans = new ResolverAnswer().set(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_ALL, new ByteChars("anyname"));
		ans.rrdata.add(rr);
		try {
			cmgr.storeResult(ans);
			org.junit.Assert.fail("Did not trap invalid store qtype");
		} catch (UnsupportedOperationException ex) {}
		org.junit.Assert.assertEquals(7, cache.size());
	}

	@org.junit.Test
	public void testCacheList() throws Exception
	{
		createManager(null);
		HashedMap<?, ?> cache = (HashedMap<?, ?>)DynLoader.getField(cmgr, "cache_ns");
		org.junit.Assert.assertEquals(0, cache.size());
		java.util.ArrayList<ResourceData.RR_NS> lst = new java.util.ArrayList<ResourceData.RR_NS>();
		long exptim = dsptch.getSystemTime() + 20;
		ByteChars ns1 = new ByteChars("ns1.dom.org");
		lst.add(new ResourceData.RR_NS(ns1, new ByteChars("host1a.dom.org"), 101, exptim));
		lst.add(new ResourceData.RR_NS(ns1, new ByteChars("host1b.dom.org"), 102, exptim-10));
		lst.add(new ResourceData.RR_NS(ns1, new ByteChars("host1c.dom.org"), 103, exptim));
		java.util.ArrayList<ResourceData.RR_NS> lst1sav = new java.util.ArrayList<ResourceData.RR_NS>(lst);
		storeNameServers(ns1, lst);
		ByteChars ns2 = new ByteChars("ns2.dom.org");
		lst.add(new ResourceData.RR_NS(ns2, new ByteChars("host2a.dom.org"), 201, exptim+10));
		java.util.ArrayList<ResourceData.RR_NS> lst2sav = new java.util.ArrayList<ResourceData.RR_NS>(lst);
		storeNameServers(ns2, lst);
		ByteChars ns3 = new ByteChars("ns3.dom.org");
		storeNameServers(ns3, null);
		org.junit.Assert.assertEquals(3, cache.size());

		DynLoader.setField(dsptch, "systime_msecs", exptim - 1);
		java.util.ArrayList<ResourceData> qrylst = cmgr.lookupList(ResolverDNS.QTYPE_NS, ns1);
		org.junit.Assert.assertEquals(2, qrylst.size());
		org.junit.Assert.assertEquals(3, cache.size());
		ResourceData.RR_NS qryrr = (ResourceData.RR_NS)qrylst.get(0);
		org.junit.Assert.assertTrue(qrylst.toString(), lst1sav.get(0).getName() == qryrr.getName());
		org.junit.Assert.assertTrue(qrylst.toString(), lst1sav.get(0).hostname == qryrr.hostname);
		org.junit.Assert.assertTrue(qrylst.toString(), lst1sav.get(0).getIP() == qryrr.getIP());
		qryrr = (ResourceData.RR_NS)qrylst.get(1);
		org.junit.Assert.assertTrue(qrylst.toString(), lst1sav.get(2).getName() == qryrr.getName());
		org.junit.Assert.assertTrue(qrylst.toString(), lst1sav.get(2).hostname == qryrr.hostname);
		org.junit.Assert.assertTrue(qrylst.toString(), lst1sav.get(2).getIP() == qryrr.getIP());

		DynLoader.setField(dsptch, "systime_msecs", exptim + 1);
		qrylst = cmgr.lookupList(ResolverDNS.QTYPE_NS, ns1);
		org.junit.Assert.assertNull(qrylst);
		org.junit.Assert.assertEquals(2, cache.size());

		qrylst = cmgr.lookupList(ResolverDNS.QTYPE_NS, ns2);
		org.junit.Assert.assertEquals(1, qrylst.size());
		org.junit.Assert.assertEquals(2, cache.size());
		qryrr = (ResourceData.RR_NS)qrylst.get(0);
		org.junit.Assert.assertTrue(qrylst.toString(), lst2sav.get(0).getName() == qryrr.getName());
		org.junit.Assert.assertTrue(qrylst.toString(), lst2sav.get(0).hostname == qryrr.hostname);
		org.junit.Assert.assertTrue(qrylst.toString(), lst2sav.get(0).getIP() == qryrr.getIP());

		storeNameServers(new ByteChars("ns3.lohitest"), null);
		storeNameServers(new ByteChars("ns4.lohitest"), null);
		storeNameServers(new ByteChars("ns5.lohitest"), null);
		storeNameServers(new ByteChars("ns6.lohitest"), null);
		storeNameServers(new ByteChars("ns7.lohitest"), null);
		org.junit.Assert.assertEquals(7, cache.size()); //at hiwater level
		ByteChars ns8 = new ByteChars("ns8.lohitest");
		lst.add(new ResourceData.RR_NS(ns8, new ByteChars("host8.dom.org"), 101, exptim+100));
		storeNameServers(ns8, lst);
		org.junit.Assert.assertEquals(5, cache.size()); //pruned back to lowater and then added ns8
		qrylst = cmgr.lookupList(ResolverDNS.QTYPE_NS, ns8);
		org.junit.Assert.assertEquals(1, qrylst.size());
		org.junit.Assert.assertEquals(5, cache.size());

		ByteChars ns99 = new ByteChars("host99.dom.org");
		ResolverAnswer ans = new ResolverAnswer().set(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_NS, ns99);
		ResolverAnswer.STATUS result = cmgr.storeResult(ans);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.NODOMAIN, result);
		org.junit.Assert.assertEquals(6, cache.size());
		qrylst = cmgr.lookupList(ResolverDNS.QTYPE_NS, ns99);
		org.junit.Assert.assertEquals(6, cache.size());
		org.junit.Assert.assertEquals(1, qrylst.size());
		ResourceData rr = qrylst.get(0);
		org.junit.Assert.assertTrue(rr.toString(), rr.isNegative());
		org.junit.Assert.assertNull(rr.toString(), rr.getName());
		org.junit.Assert.assertEquals(rr.toString(), 0, rr.getIP());
		org.junit.Assert.assertEquals(rr.toString(), PacketDNS.QCLASS_INET, rr.rrClass());

		java.net.InetSocketAddress tsap = cmgr.lookupNameServer(ns8);
		org.junit.Assert.assertEquals(PacketDNS.INETPORT, tsap.getPort());
		tsap = cmgr.lookupNameServer(new ByteChars("noncached-domain"));
		org.junit.Assert.assertNull(tsap);
		tsap = cmgr.lookupNameServer(ns99);
		org.junit.Assert.assertEquals(0, tsap.getPort()); //negatively cached

		try {
			cmgr.lookupList(ResolverDNS.QTYPE_A, ns8);
			org.junit.Assert.fail("Did not trap invalid type-A list lookup");
		} catch (UnsupportedOperationException ex) {}
		org.junit.Assert.assertEquals(6, cache.size());
	}

	@org.junit.Test
	public void testCacheIP() throws Exception
	{
		createManager(null);
		HashedMapIntKey<?> cache = (HashedMapIntKey<?>)DynLoader.getField(cmgr, "cache_ptr");
		ByteChars host1 = new ByteChars("host1.dom.org");
		int ip1 = IP.convertDottedIP("192.168.99.1");
		long ttl1 = 10;
		ByteChars host2 = new ByteChars("host2.dom.org");
		int ip2 = IP.convertDottedIP("192.168.99.2");
		long ttl2 = 20;
		int ip3 = 99;

		org.junit.Assert.assertEquals(0, cache.size());
		storeIP(ip1, host1, ttl1);
		storeIP(ip2, host2, ttl2);
		storeIP(ip3, null, ttl2);
		org.junit.Assert.assertEquals(3, cache.size());

		DynLoader.setField(dsptch, "systime_msecs", dsptch.getSystemTime() + ttl1 + 1);
		ResourceData rr = cmgr.lookup(ResolverDNS.QTYPE_PTR, ip1);
		org.junit.Assert.assertNull(rr);
		org.junit.Assert.assertEquals(2, cache.size());
		rr = cmgr.lookup(ResolverDNS.QTYPE_PTR, ip2);
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_PTR, rr.rrType());
		rr = cmgr.lookup(ResolverDNS.QTYPE_PTR, ip3);
		org.junit.Assert.assertTrue(rr.toString(), rr.isNegative());
		org.junit.Assert.assertEquals(2, cache.size());

		storeIP(103, null, ttl2);
		storeIP(104, null, ttl2);
		storeIP(105, null, ttl2);
		storeIP(106, null, ttl2);
		storeIP(107, null, ttl2);
		org.junit.Assert.assertEquals(7, cache.size()); //at hiwater level
		ByteChars host108 = new ByteChars("host108.lohitest");
		storeIP(108, host108, ttl2);
		org.junit.Assert.assertEquals(5, cache.size()); //pruned back to lowater and then added host8
		rr = cmgr.lookup(ResolverDNS.QTYPE_PTR, 108);
		org.junit.Assert.assertEquals(ResolverDNS.QTYPE_PTR, rr.rrType());
		org.junit.Assert.assertTrue(rr.getName() == host108);
		org.junit.Assert.assertEquals(108, rr.getIP());
		org.junit.Assert.assertEquals(5, cache.size());

		rr = cmgr.lookup(ResolverDNS.QTYPE_PTR, ip3+1);
		org.junit.Assert.assertNull(rr);

		ResolverAnswer ans = new ResolverAnswer().set(ResolverAnswer.STATUS.OK, ResolverDNS.QTYPE_PTR, 999);
		ResolverAnswer.STATUS result = cmgr.storeResult(ans);
		org.junit.Assert.assertEquals(ResolverAnswer.STATUS.NODOMAIN, result);
		org.junit.Assert.assertEquals(6, cache.size());
		rr = cmgr.lookup(ResolverDNS.QTYPE_PTR, 999);
		org.junit.Assert.assertEquals(6, cache.size());
		org.junit.Assert.assertTrue(rr.toString(), rr.isNegative());
		org.junit.Assert.assertNull(rr.toString(), rr.getName());
		org.junit.Assert.assertEquals(rr.toString(), 0, rr.getIP());
		org.junit.Assert.assertEquals(rr.toString(), PacketDNS.QCLASS_INET, rr.rrClass());

		try {
			cmgr.lookup(ResolverDNS.QTYPE_A, 0);
			org.junit.Assert.fail("Did not trap invalid type-A IP lookup");
		} catch (UnsupportedOperationException ex) {}
		org.junit.Assert.assertEquals(6, cache.size());
	}

	private ResolverConfig createManager(XmlConfig dnscfg)
		throws java.io.IOException, javax.naming.NamingException
	{
		if (dnscfg == null) dnscfg = XmlConfig.NULLCFG;
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef.Builder().withName("CacheManagerTest").build();
		dsptch = Dispatcher.create(appctx, def, logger);
		ResolverConfig config = new ResolverConfig.Builder(dnscfg)
				.withCacheLoWaterA(4)
				.withCacheHiWaterA(7)
				.withCacheLoWaterNS(4)
				.withCacheHiWaterNS(7)
				.withCacheLoWaterPTR(4)
				.withCacheHiWaterPTR(7)
				.withPartialPrune(true)
				.withInitialMinTTL(0)
				.withLookupMinTTL(0)
				.build();
		ResolverService rslvr = new ResolverService(dsptch, config);
		cmgr = new CacheManager(dsptch, config, rslvr.getLocalNameServers());
		return config;
	}

	private void storeHost(ByteChars hname, int ip, long ttl)
	{
		ResolverAnswer.STATUS result = (ttl < 0 ? ResolverAnswer.STATUS.NODOMAIN : ResolverAnswer.STATUS.OK);
		long expiry = dsptch.getSystemTime() + Math.abs(ttl);
		ResolverAnswer ans = new ResolverAnswer().set(result, ResolverDNS.QTYPE_A, hname);
		ResourceData rr = null;
		if (result == ResolverAnswer.STATUS.OK) {
			rr = new ResourceData.RR_A(ans.qname, ip, expiry);
			ans.rrdata.add(rr);
		}
		ResolverAnswer.STATUS result2 = cmgr.storeResult(ans);
		org.junit.Assert.assertEquals(result, result2);
		ans.rrdata.clear(); //to make sure cache storage is independent of this
		ResourceData rr2 = cmgr.lookup(ResolverDNS.QTYPE_A, hname);
		org.junit.Assert.assertEquals(rr2.toString(), PacketDNS.QCLASS_INET, rr2.rrClass());
		if (result == ResolverAnswer.STATUS.OK) {
			org.junit.Assert.assertTrue(rr2 == rr);
			org.junit.Assert.assertEquals(rr2.toString(), expiry, rr2.getExpiry());
			org.junit.Assert.assertEquals(rr2.toString(), ResolverDNS.QTYPE_A, rr2.rrType());
			org.junit.Assert.assertTrue(rr2.toString(), rr2.getName() == hname);
			org.junit.Assert.assertEquals(rr2.toString(), ip, rr2.getIP());
		} else {
			org.junit.Assert.assertTrue(rr2.toString(), rr2.isNegative());
			org.junit.Assert.assertNull(rr2.toString(), rr2.getName());
			org.junit.Assert.assertEquals(rr2.toString(), 0, rr2.getIP());
		}
		rr2 = cmgr.lookup(ResolverDNS.QTYPE_A, new ByteChars(hname).append('x'));
		org.junit.Assert.assertNull(rr2);
	}

	private void storeNameServers(ByteChars domnam, java.util.ArrayList<ResourceData.RR_NS> lst)
	{
		ResolverAnswer.STATUS result = (lst == null ? ResolverAnswer.STATUS.NODOMAIN : ResolverAnswer.STATUS.OK);
		java.util.ArrayList<ResourceData> lst_sav = null;
		ResolverAnswer ans = new ResolverAnswer().set(result, ResolverDNS.QTYPE_NS, domnam);
		if (result == ResolverAnswer.STATUS.OK) {
			ans.rrdata.addAll(lst);
			lst_sav = new java.util.ArrayList<ResourceData>(lst);
			lst.clear();
		}
		ResolverAnswer.STATUS result2 = cmgr.storeResult(ans);
		org.junit.Assert.assertEquals(result, result2);
		ans.rrdata.clear(); //to make sure cache storage is independent of this
		java.util.ArrayList<ResourceData> qlst = cmgr.lookupList(ResolverDNS.QTYPE_NS, domnam);
		org.junit.Assert.assertEquals(result == ResolverAnswer.STATUS.NODOMAIN ? 1 : lst_sav.size(), qlst.size());
		for (int idx = 0; idx != qlst.size(); idx++) {
			ResourceData rr = qlst.get(idx);
			org.junit.Assert.assertEquals(rr.toString(), PacketDNS.QCLASS_INET, rr.rrClass());
			if (result == ResolverAnswer.STATUS.NODOMAIN) {
				org.junit.Assert.assertTrue(rr.toString(), rr.isNegative());
			} else {
				org.junit.Assert.assertEquals(rr.toString(), ResolverDNS.QTYPE_NS, rr.rrType());
				org.junit.Assert.assertTrue(rr.toString(), rr == lst_sav.get(idx));
			}
		}
		qlst = cmgr.lookupList(ResolverDNS.QTYPE_NS, new ByteChars(domnam).append('x'));
		org.junit.Assert.assertNull(qlst);
	}

	private void storeIP(int ip, ByteChars hname, long ttl)
	{
		ResolverAnswer.STATUS result = (hname == null ? ResolverAnswer.STATUS.NODOMAIN : ResolverAnswer.STATUS.OK);
		long expiry = dsptch.getSystemTime() + ttl;
		ResolverAnswer ans = new ResolverAnswer().set(result, ResolverDNS.QTYPE_PTR, ip);
		ResourceData rr = null;
		if (result == ResolverAnswer.STATUS.OK) {
			rr = new ResourceData.RR_PTR(hname, ip, expiry);
			ans.rrdata.add(rr);
		}
		ResolverAnswer.STATUS result2 = cmgr.storeResult(ans);
		org.junit.Assert.assertEquals(result, result2);
		ans.rrdata.clear(); //to make sure cache storage is independent of this
		ResourceData rr2 = cmgr.lookup(ResolverDNS.QTYPE_PTR, ip);
		org.junit.Assert.assertEquals(rr2.toString(), PacketDNS.QCLASS_INET, rr2.rrClass());
		if (result == ResolverAnswer.STATUS.OK) {
			org.junit.Assert.assertTrue(rr2 == rr);
			org.junit.Assert.assertEquals(rr2.toString(), expiry, rr2.getExpiry());
			org.junit.Assert.assertEquals(rr2.toString(), ResolverDNS.QTYPE_PTR, rr2.rrType());
			org.junit.Assert.assertTrue(rr2.toString(), rr2.getName() == hname);
			org.junit.Assert.assertEquals(rr2.toString(), ip, rr2.getIP());
		} else {
			org.junit.Assert.assertTrue(rr2.toString(), rr2.isNegative());
			org.junit.Assert.assertNull(rr2.toString(), rr2.getName());
			org.junit.Assert.assertEquals(rr2.toString(), 0, rr2.getIP());
		}
	}
}
