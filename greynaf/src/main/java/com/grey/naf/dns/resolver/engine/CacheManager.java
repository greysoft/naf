/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.engine;

import com.grey.base.config.SysProps;
import com.grey.base.utils.ByteChars;
import com.grey.base.collections.HashedMap;
import com.grey.base.collections.HashedMapIntKey;
import com.grey.base.collections.HashedSet;
import com.grey.base.utils.IP;
import com.grey.base.utils.TSAP;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.errors.NAFConfigException;

class CacheManager
{
	private static final java.net.InetSocketAddress NEGATIVE_TSAP = TSAP.createSocketAddress(0, 0, null);
	private static final long PRUNE_INTERVAL = SysProps.getTime("greynaf.dns.cache.intervalprune", "1h");
	private static final String LOGLBL = "DNS-Resolver: ";
	private static final ByteChars NULLRRNAME = new ByteChars("-");

	// cache_a simply maps domain-name to type-A RR (contains its IP address)
	private final HashedMap<ByteChars, ResourceData> cache_a = new HashedMap<ByteChars, ResourceData>(0, 2f);

	// cache_aaaa simply maps domain-name to type-AAAA RR
	private final HashedMap<ByteChars, ResourceData> cache_aaaa = new HashedMap<ByteChars, ResourceData>(0, 2f);

	// maps IP address to type-PTR RR (contains its domain name)
	private final HashedMapIntKey<ResourceData> cache_ptr = new HashedMapIntKey<ResourceData>(0, 10f);

	// maps domain name to list of type-MX RR records
	private final HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache_mx
								= new HashedMap<ByteChars, java.util.ArrayList<ResourceData>>(0, 2f);

	// maps domain name to list of type-NS RR records
	private final HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache_ns
								= new HashedMap<ByteChars, java.util.ArrayList<ResourceData>>(0, 2f);
	// maps nameserver IP address to its TSAP
	private final HashedMapIntKey<java.net.InetSocketAddress> cache_nameservers
									= new HashedMapIntKey<java.net.InetSocketAddress>(0, 10f);
	//root domains - may include private roots, not just the global "." root
	private final HashedSet<ByteChars> ns_roots = new HashedSet<ByteChars>();
	//nameservers for the domains in ns_roots
	private final HashedSet<ByteChars> ns_roots_a = new HashedSet<ByteChars>();

	// maps domain name to type-SOA RR
	private final HashedMap<ByteChars, ResourceData> cache_soa = new HashedMap<ByteChars, ResourceData>(0, 2f);

	// maps domain name to list of type-SRV RR records
	private final HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache_srv
									= new HashedMap<ByteChars, java.util.ArrayList<ResourceData>>(0, 2f);

	// maps domain name to type-TXT RR
	private final HashedMap<ByteChars, ResourceData> cache_txt = new HashedMap<ByteChars, ResourceData>(0, 2f);

	private final java.util.Random rndgen = new java.util.Random(System.nanoTime());
	private final com.grey.naf.reactor.Dispatcher dsptch;
	private final String[] localNameServers;
	private final ResolverConfig config;
	private long systime_prune;
	private final com.grey.logging.Logger logger;

	// temporary work areas, pre-allocated merely for efficiency
	private final byte[] tmp_ipaddr = new byte[IP.IPADDR_OCTETS];

	public CacheManager(com.grey.naf.reactor.Dispatcher d, ResolverConfig c, String[] nameServers)
	{
		dsptch = d;
		config = c;
		localNameServers = nameServers;
		logger = dsptch.getLogger();
		if (!config.isRecursive()) loadRootServers();
	}

	public java.net.InetSocketAddress lookupNameServer(ByteChars domnam)
	{
		java.util.ArrayList<ResourceData> rrlst = lookupList(ResolverDNS.QTYPE_NS, domnam);
		if (rrlst == null) return null;
		if (rrlst.get(0).isNegative()) return NEGATIVE_TSAP;
		int idx = rndgen.nextInt(rrlst.size());
		int ip = rrlst.get(idx).getIP();
		java.net.InetSocketAddress tsap = cache_nameservers.get(ip);
		if (tsap == null) {
			tsap = createServerTSAP(ip);
			cache_nameservers.put(ip, tsap);
		}
		return tsap;
	}

	public ResourceData lookup(byte qtype, ByteChars qname)
	{
		HashedMap<ByteChars, ResourceData> cache;
		if (qtype == ResolverDNS.QTYPE_A) {
			cache = cache_a;
		} else if (qtype == ResolverDNS.QTYPE_AAAA) {
			cache = cache_aaaa;
		} else if (qtype == ResolverDNS.QTYPE_SOA) {
			cache = cache_soa;
		} else if (qtype == ResolverDNS.QTYPE_TXT) {
			cache = cache_txt;
		} else {
			throw new UnsupportedOperationException(LOGLBL+"lookup qtype="+qtype+" - "+qname);
		}
		ResourceData rr = cache.get(qname);
		long min_age = dsptch.getSystemTime() - config.getLookupMinTTL();

		if (rr != null && rr.isExpired(min_age)) {
			// stale data, so remove it and say we found nothing
			cache.remove(qname);
			rr = null;
		}
		return rr;
	}

	public ResourceData lookup(byte qtype, int qip)
	{
		if (qtype != ResolverDNS.QTYPE_PTR) throw new UnsupportedOperationException(LOGLBL+"lookup qtype="+qtype+" - "+qip);
		ResourceData rr = cache_ptr.get(qip);
		long min_age = dsptch.getSystemTime() - config.getLookupMinTTL();

		if (rr != null && rr.isExpired(min_age)) {
			// stale data, so remove it and say we found nothing
			cache_ptr.remove(qip);
			rr = null;
		}
		return rr;
	}

	public java.util.ArrayList<ResourceData> lookupList(byte qtype, ByteChars qname)
	{
		HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache;
		if (qtype == ResolverDNS.QTYPE_MX) {
			cache = cache_mx;
		} else if (qtype == ResolverDNS.QTYPE_NS) {
			cache = cache_ns;
		} else if (qtype == ResolverDNS.QTYPE_SRV) {
			cache = cache_srv;
		} else {
			throw new UnsupportedOperationException(LOGLBL+"lookup qtype="+qtype+" - "+qname);
		}
		long min_age = dsptch.getSystemTime() - config.getLookupMinTTL();
		return pruneList(cache, qname, null, min_age);
	}

	// Answer.result can only be OK or NODOMAIN, and if the latter Answer.rrdata must be empty.
	// A nominally OK result can have empty rrdata, but in that case it gets converted to NODOMAIN.
	public ResolverAnswer.STATUS storeResult(ResolverAnswer ans)
	{
		if (ans.result == ResolverAnswer.STATUS.NODOMAIN || ans.rrdata.size() == 0) {
			ans.result = ResolverAnswer.STATUS.NODOMAIN;
			long ttl = dsptch.getSystemTime() + config.getNegativeTTL();
			ResourceData rr = ResourceData.createNegativeRR(ans.qtype, ttl);
			ans.rrdata.add(rr);
		}

		if (dsptch.getSystemTime() >= systime_prune + PRUNE_INTERVAL) {
			// we need this, else if hiwater was unlimited we'd never even remove most expired RRs
			prune(null);
		}

		if (ans.qtype == ResolverDNS.QTYPE_A) {
			storeSingleResult("A", ans, cache_a, config.getCacheLoWaterA(), config.getCacheHiWaterA());
		} else if (ans.qtype == ResolverDNS.QTYPE_AAAA) {
			storeSingleResult("AAAA", ans, cache_aaaa, config.getCacheLoWaterA(), config.getCacheHiWaterA());
		} else if (ans.qtype == ResolverDNS.QTYPE_NS) {
			storeListResult("NS", ans, cache_ns, config.getCacheLoWaterNS(), config.getCacheHiWaterNS());
		} else if (ans.qtype == ResolverDNS.QTYPE_MX) {
			storeListResult("MX", ans, cache_mx, config.getCacheLoWaterMX(), config.getCacheHiWaterMX());
		} else if (ans.qtype == ResolverDNS.QTYPE_SOA) {
			storeSingleResult("SOA", ans, cache_soa, config.getCacheLoWaterSOA(), config.getCacheHiWaterSOA());
		} else if (ans.qtype == ResolverDNS.QTYPE_SRV) {
			storeListResult("SRV", ans, cache_srv, config.getCacheLoWaterSOA(), config.getCacheHiWaterSOA());
		} else if (ans.qtype == ResolverDNS.QTYPE_TXT) {
			storeSingleResult("TXT", ans, cache_txt, config.getCacheLoWaterSOA(), config.getCacheHiWaterSOA());
		} else if (ans.qtype == ResolverDNS.QTYPE_PTR) {
			// the RR's domnam field is the hostname that was resolved for the IP
			if (config.getCacheHiWaterPTR() != 0 && cache_ptr.size() >= config.getCacheHiWaterPTR()) {
				prune("PTR", cache_ptr, config.getCacheLoWaterPTR(), config.getCacheHiWaterPTR());
			}
			cache_ptr.put(ans.qip, ans.getPTR());
		} else {
			throw new UnsupportedOperationException(LOGLBL+"store qtype="+ans.qtype);
		}
		if (ans.result == ResolverAnswer.STATUS.NODOMAIN) ans.rrdata.clear();
		return ans.result;
	}

	public void storeHostAddress(ByteChars qname, ResourceData rr)
	{
		storeResult("A", qname, rr, cache_a, config.getCacheLoWaterA(), config.getCacheHiWaterA());
	}

	private void storeSingleResult(String desc, ResolverAnswer ans, HashedMap<ByteChars, ResourceData> cache, int lowater, int hiwater)
	{
		storeResult(desc, ans.qname, ans.rrdata.get(0), cache, lowater, hiwater);
	}

	private void storeResult(String desc, ByteChars qname, ResourceData rr, HashedMap<ByteChars, ResourceData> cache,
			int lowater, int hiwater)
	{
		if (hiwater != 0 && cache.size() >= hiwater) prune(desc, cache, lowater, hiwater);
		cache.put(qname, rr);
	}

	private void storeListResult(String desc, ResolverAnswer ans, HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache,
			int lowater, int hiwater)
	{
		if (hiwater != 0 && cache.size() >= hiwater) pruneLists(desc, cache, lowater, hiwater);
		java.util.ArrayList<ResourceData> rrdata = new java.util.ArrayList<ResourceData>(ans.rrdata);
		cache.put(ans.qname, rrdata);
	}

	public void loadRootServers()
	{
		HashedMap<ByteChars, java.util.ArrayList<ResourceData>> newroots;
		try {
			// NB: This can reorder the localNameServers array
			newroots = NameServerUtils.loadRootServers(localNameServers, config.getPathnameRootServers(), config.isAutoRoots(), logger);
		} catch (Exception ex) {
			if (ns_roots.size()==0) throw new NAFConfigException(LOGLBL+"Failed to bootstrap root servers", ex);
			logger.warn(LOGLBL+"Keeping existing root-servers due to reload failure - "+ex);
			return;
		}

		if (ns_roots.size() != 0) {
			// clean out our existing roots first
			java.util.Iterator<ByteChars> it = ns_roots.iterator();
			while (it.hasNext()) {
				ByteChars domnam = it.next();
				java.util.ArrayList<ResourceData> lst = cache_ns.remove(domnam);
				for (int idx = 0; idx != lst.size(); idx++) {
					ResourceData.RR_NS rr_ns = (ResourceData.RR_NS)lst.get(idx);
					cache_nameservers.remove(rr_ns.getIP());
					cache_a.remove(rr_ns.getHostname());
				}
			}
		}
		ns_roots.clear();
		ns_roots_a.clear();

		ns_roots.addAll(newroots.keySet());
		cache_ns.putAll(newroots);
		java.util.Iterator<java.util.ArrayList<ResourceData>> it = newroots.values().iterator();
		while (it.hasNext()) {
			java.util.ArrayList<ResourceData> lst = it.next();
			for (int idx = 0; idx != lst.size(); idx++) {
				ResourceData.RR_NS rr_ns = (ResourceData.RR_NS)lst.get(idx);
				ResourceData rr_a = new ResourceData.RR_A(rr_ns.getHostname(), rr_ns.getIP(), rr_ns.getExpiry());
				cache_a.put(rr_a.getName(), rr_a);
				ns_roots_a.add(rr_a.getName());
			}
		}
		java.util.Iterator<ByteChars> itbc = ns_roots.iterator();
		String txt = "";
		while (itbc.hasNext()) {
			ByteChars root = itbc.next();
			java.util.ArrayList<ResourceData> lst = cache_ns.get(root);
			txt += "\n\t- Root="+root+" has servers="+lst.size()+"/"+lst;
		}
		logger.info(LOGLBL+"Loaded nameservers for roots="+ns_roots.size()+"/"+ns_roots+txt);
	}

	public java.net.InetSocketAddress createServerTSAP(int ip) {
		return createServerTSAP(ip, config.getDnsPort());
	}

	public java.net.InetSocketAddress createServerTSAP(int ip, int port) {
		if (config.getDnsInterceptor() != null) return config.getDnsInterceptor();
		return TSAP.createSocketAddress(ip, port, tmp_ipaddr);
	}

	public StringBuilder dump(String eol, StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder();
		sb.append("Cache Sizes: A=").append(cache_a.size());
		sb.append("; AAAA=").append(cache_aaaa.size());
		sb.append("; PTR=").append(cache_ptr.size());
		sb.append("; SOA=").append(cache_soa.size());
		sb.append("; NS=").append(cache_ns.size()).append('/').append(countRRs(cache_ns)).append("/IP=").append(cache_nameservers.size());
		sb.append("; MX=").append(cache_mx.size()).append('/').append(countRRs(cache_mx));
		sb.append("; SRV=").append(cache_srv.size()).append('/').append(countRRs(cache_srv));
		sb.append("; TXT=").append(cache_txt.size());
		sb.append(eol);
		dump("A", sb, eol, cache_a);
		dump("AAAA", sb, eol, cache_aaaa);

		if (cache_ptr.size() != 0) sb.append(eol);
		int[] ip = cache_ptr.getKeys(null);
		java.util.Arrays.sort(ip);
		for (int idx = 0; idx != ip.length; idx++) {
			sb.append("PTR: ");
			IP.displayDottedIP(ip[idx], sb);
			sb.append(" => ");
			cache_ptr.get(ip[idx]).toString(sb).append(eol);
		}

		dump("SOA", sb, eol, cache_soa);
		dumpLists("NS", sb, eol, cache_ns);
		dumpLists("MX", sb, eol, cache_mx);
		dumpLists("SRV", sb, eol, cache_srv);
		dump("TXT", sb, eol, cache_txt);
		return sb;
	}

	private static void dump(String desc, StringBuilder sb, String eol, HashedMap<ByteChars, ResourceData> cache)
	{
		if (cache.size() == 0) return;
		ByteChars[] keys = cache.keySet().toArray(new ByteChars[cache.size()]);
		for (int idx = 0; idx != keys.length; idx++) if (keys[idx] == null) keys[idx] = NULLRRNAME; //sort() throws on null
		java.util.Arrays.sort(keys);
		sb.append(eol);
		for (int idx = 0; idx != keys.length; idx++) {
			ByteChars bc = keys[idx];
			if (bc == NULLRRNAME) bc = null;
			sb.append(desc+": ").append(bc).append(" => ");
			cache.get(bc).toString(sb).append(eol);
		}
	}

	private static void dumpLists(String desc, StringBuilder sb, String eol,
								HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache)
	{
		if (cache.size() == 0) return;
		ByteChars[] keys = cache.keySet().toArray(new ByteChars[cache.size()]);
		for (int idx = 0; idx != keys.length; idx++) if (keys[idx] == null) keys[idx] = NULLRRNAME; //sort() throws on null
		java.util.Arrays.sort(keys);
		sb.append(eol);
		for (int idx = 0; idx != keys.length; idx++) {
			ByteChars bc = keys[idx];
			if (bc == NULLRRNAME) bc = null;
			java.util.ArrayList<ResourceData> lst = cache.get(bc);
			sb.append(desc).append(": ").append(bc).append(" => ").append(lst.size());
			String dlm = ": ";
			for (int idx2 = 0; idx2 != lst.size(); idx2++) {
				sb.append(dlm);
				lst.get(idx2).toString(sb);
				dlm = "; ";
			}
			sb.append(eol);
		}
	}

	public void prune(StringBuilder sbrsp)
	{
		systime_prune = dsptch.getSystemTime();
		int delcnt = 0;
		delcnt += prune("A", cache_a, config.getCacheLoWaterA(), config.getCacheHiWaterA());
		delcnt += prune("AAAA", cache_aaaa, config.getCacheLoWaterA(), config.getCacheHiWaterA());
		delcnt += prune("PTR", cache_ptr, config.getCacheLoWaterPTR(), config.getCacheHiWaterPTR());
		delcnt += pruneLists("NS", cache_ns, config.getCacheLoWaterNS(), config.getCacheHiWaterNS());
		delcnt += prune("SOA", cache_soa, config.getCacheLoWaterSOA(), config.getCacheHiWaterSOA());
		delcnt += pruneLists("MX", cache_mx, config.getCacheLoWaterMX(), config.getCacheHiWaterMX());
		delcnt += pruneLists("SRV", cache_srv, config.getCacheLoWaterSOA(), config.getCacheHiWaterSOA());
		delcnt += prune("TXT", cache_txt, config.getCacheLoWaterSOA(), config.getCacheHiWaterSOA());
		if (sbrsp != null) sbrsp.append("Cache prune removed entries=").append(delcnt);
	}

	private int prune(String desc, HashedMap<ByteChars, ResourceData> cache, int lowater, int hiwater)
	{
		// delete all expired entries
		long min_age = dsptch.getSystemTime() - config.getLookupMinTTL();
		int oldsize = cache.size();
		java.util.Iterator<ByteChars> itbc = cache.keysIterator();
		while (itbc.hasNext()) {
			ByteChars k = itbc.next();
			if (cache.get(k).isExpired(min_age)) itbc.remove();
		}
		int delcnt = (oldsize - cache.size());

		// now delete excess entries
		if (hiwater != 0 && lowater != 0 && cache.size() >= hiwater) {
			// delete random entries to bring us down to the low-water limit
			int excess = cache.size() - lowater;
			itbc = cache.keysIterator();
			while (itbc.hasNext()) {
				if (excess == 0) break;
				ByteChars k = itbc.next();
				if (cache == cache_a && ns_roots_a.contains(k)) continue;
				itbc.remove();
				delcnt++;
				excess--;
			}
		}
		if (delcnt != 0) logger.trace(LOGLBL+"pruned cache="+desc+" - deleted entries="+delcnt);
		return delcnt;
	}

	private int prune(String desc, HashedMapIntKey<ResourceData> cache, int lowater, int hiwater)
	{
		long min_age = dsptch.getSystemTime() - config.getLookupMinTTL();
		int oldsize = cache.size();
		java.util.Iterator<ResourceData> itrr = cache.valuesIterator();
		while (itrr.hasNext()) {
			ResourceData rr = itrr.next();
			if (rr.isExpired(min_age)) itrr.remove();
		}
		int delcnt = (oldsize - cache.size());

		if (hiwater != 0 && lowater != 0 && cache.size() >= hiwater) {
			int excess = cache.size() - lowater;
			com.grey.base.collections.IteratorInt itnum = cache.keysIterator();
			while (itnum.hasNext()) {
				if (excess == 0) break;
				itnum.next();
				itnum.remove();
				delcnt++;
				excess--;
			}
		}
		if (delcnt != 0) logger.trace(LOGLBL+"pruned cache="+desc+" - deleted entries="+delcnt);
		return delcnt;
	}

	private int pruneLists(String desc, HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache,
			int lowater, int hiwater)
	{
		long min_age = dsptch.getSystemTime() - config.getLookupMinTTL();
		int delcnt = 0;
		java.util.Iterator<ByteChars> it = cache.keysIterator();
		while (it.hasNext()) {
			if (pruneList(cache, null, it, min_age) == null) delcnt++;
		}

		if (hiwater != 0 && lowater != 0 && cache.size() >= hiwater) {
			int excess = cache.size() - lowater;
			it = cache.keysIterator();
			while (it.hasNext()) {
				if (excess == 0) break;
				if (pruneList(cache, null, it, 0) != null) continue;
				delcnt++;
				excess--;
			}
		}
		if (delcnt != 0) logger.trace(LOGLBL+"pruned cache="+desc+" - deleted entries="+delcnt);
		return delcnt;
	}

	private java.util.ArrayList<ResourceData> pruneList(HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache,
			ByteChars domnam, java.util.Iterator<ByteChars> it, long min_age)
	{
		if (it != null) domnam = it.next();
		java.util.ArrayList<ResourceData> lst = cache.get(domnam);
		if (lst == null) return null;
		if (cache == cache_ns && ns_roots.contains(domnam)) return lst;
		boolean pruned = false;

		if (min_age != 0) {
			for (int idx = lst.size() - 1; idx != -1; idx--) {
				if (lst.get(idx).isExpired(min_age)) {
					if (cache == cache_ns) cache_nameservers.remove(lst.get(idx).getIP());
					lst.remove(idx);
					pruned = true;
				}
			}
		}

		if (min_age == 0 || (pruned && !config.isPartialPrune())) {
			//remove the remainder of this List entry
			if (cache == cache_ns) {
				for (int idx = lst.size() - 1; idx != -1; idx--) {
					cache_nameservers.remove(lst.get(idx).getIP());
				}
			}
			lst.clear();
		}

		if (lst.size() == 0) {
			if (it == null) {
				cache.remove(domnam);
			} else {
				it.remove();
			}
			lst = null;
		}
		return lst;
	}

	private static int countRRs(HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache)
	{
		int cnt = 0;
		java.util.Iterator<java.util.ArrayList<ResourceData>> it = cache.valuesIterator();
		while (it.hasNext()) {
			java.util.ArrayList<ResourceData> lst = it.next();
			cnt += lst.size();
		}
		return cnt;
	}
}