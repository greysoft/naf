/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.config.SysProps;
import com.grey.logging.Logger.LEVEL;

/*
 * As a general principle, this class seeks to reclaim stale objects for future use, such that it doesn't generate any garbage for the GC to collect.
 * The one area where that principle has to be abandoned is the RR caches, but the objects there expire on a timescale of hours and days rather than
 * milliseconds, so that's not the sort of memory churn that would affect performance.
 * Furthermore, unless we implement a complicated reference-counting scheme, obtaining RRDATA objects from an ObjectWell would probably result in double
 * the storage requirements, as the cache_mx (and CNAME) entries currently point at cache_a entries, so we'd need to generate independent RRDATA objects
 * for each reference, in order for it to be safe to return them to the ObjectWell.
 * TCP channels and sockets are also churned into garbage, but there's absolutely no choice about that, as we have to obtain them anew from the JDK for
 * each connection.
 */
public final class ResolverService
	implements com.grey.naf.reactor.Timer.Handler, com.grey.naf.nafman.Registry.CommandHandler
{
	// UDP max: add a small buffer at end to allow for sloppy encoding by remote host (NB: no reason to suspect that)
	private static final int pktsizudp = SysProps.get("greynaf.dns.maxudp", Packet.UDPMAXMSG + 64);
	// TCP max: allow for larger TCP messages (but we only really expect a fraction larger, not 4-fold)
	private static final int pktsiztcp = SysProps.get("greynaf.dns.maxtcp", Packet.UDPMAXMSG * 64);
	// max number of consecutive UDP reads, before yielding control back to framework (zero means unlimited)
	private static final int udprcvmax = SysProps.get("greynaf.dns.udprcvmax", 100);

	private static final int TMRTYPE_PRUNECACHE = 1;  // the routine DNS-request timeouts leave timer type set to zero

	private final int retrymax;  // max UDP retries - 0 means try once, with no retries
	private final long retrytmt;		// timeout on DNS/UDP requests
	private final long retrytmt_tcp;  // UDP/TCP - make it long enough that we don't preempt server's idle-connection close
	private final long retrystep;  // number of milliseconds to increment DNS timeout by on each retry
	private final long negttl;	// how long to cache DNS no-domain answers (negative TTL)
	private final long tmtcacheprune;   // interval for pruning expired RRs from DNS cache
	private final int cache_hiwater;  //soft limit for A & PTR RR caches - they can get temporarily larger, but we prune them back
	private final int cache_hiwater_mx;
	private final boolean mx_fallback_a;  // MX queries fall back to simple hostname lookup (QTYPE=A) if no MX RRs exist - Default is No
	private final int mx_maxrr;
	private final boolean always_tcp;
	private final boolean dump_on_exit;

	public final com.grey.naf.reactor.Dispatcher dsptch;
	private final ServerHandle[] dnsservers;

	private com.grey.naf.reactor.Timer tmr_prunecache;
	private int next_qryid = new java.util.Random(System.nanoTime()).nextInt(0xffff);
	private int next_dnssrv;

	// cache_a simply maps domain-name to type-A RR (contains IP address)
	// cache_ptr maps IP address (as an integer value) to type-PTR RR (contains domain name)
	// cache_mx maps domain name to list of type-A RR records, ie. it records the addresses of the MX hosts, not their MX RRs
	// pendingreqs, pendingdoms_a, pendingdoms_ptr and pendingdoms_mx are short-lived caches tracking current requests
	// The long-lived caches may grow very large, so use huge load factor to save on space - lookup time will still be more than fast enough
	private final com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, ResourceData> cache_a
								= new com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, ResourceData>(0, 10f);
	private final com.grey.base.utils.HashedMapIntKey<ResourceData> cache_ptr
								= new com.grey.base.utils.HashedMapIntKey<ResourceData>(0, 10f);
	private final com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, java.util.ArrayList<ResourceData>> cache_mx
								= new com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, java.util.ArrayList<ResourceData>>(0, 10f);

	private final com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_a
								= new com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();
	private final com.grey.base.utils.HashedMapIntKey<QueryHandle> pendingdoms_ptr
								= new com.grey.base.utils.HashedMapIntKey<QueryHandle>();
	private final com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_mx
								= new com.grey.base.utils.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();

	// Note that despite its name, this only tracks UDP requests
	private final com.grey.base.utils.HashedMapIntKey<QueryHandle> pendingreqs
								= new com.grey.base.utils.HashedMapIntKey<QueryHandle>();

	// state which is required to persist across one callout from Dispatcher (or user)
	private Packet udpdnspkt;

	// these represent non-persistent global state shared with subroutines - safe because w're single-threaded
	private boolean inUserCall;  // True means we're still in the resolv() call chain
	public boolean badquestion;

	// We pre-allocate spare instances of these objects, for efficiency
	final com.grey.base.utils.ObjectWell<com.grey.base.utils.ByteChars> bcstore;
	final com.grey.base.utils.ObjectWell<Packet> pktstore;  //DNS/TCP packets
	private final com.grey.base.utils.ObjectWell<ResourceData> rrstore;
	private final com.grey.base.utils.ObjectWell<QueryHandle> qrystore;

	// these are just temporary work areas, pre-allocated for efficiency
	private final Answer dnsAnswer = new Answer();
	private final ResourceData tmprr = new ResourceData();
	private final com.grey.base.utils.ByteChars tmpnam = new com.grey.base.utils.ByteChars(Resolver.MAXDOMAIN);
	private final StringBuilder sbtmp = new StringBuilder();

	// already logged by Dispatcher, nothing more for us to do
	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex) {}


	public ResolverService(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg)
		throws com.grey.base.ConfigException, java.io.IOException, javax.naming.NamingException
	{
		dsptch = d;
		bcstore = new com.grey.base.utils.ObjectWell<com.grey.base.utils.ByteChars>(com.grey.base.utils.ByteChars.class, "DNS_BC_"+dsptch.name);
		rrstore = new com.grey.base.utils.ObjectWell<ResourceData>(ResourceData.class, "DNS_RR_"+dsptch.name);

		String selectedservers = "";
		String srvnames_sys = getSystemDnsServers();
		if (srvnames_sys == null) srvnames_sys = "127.0.0.1";
		String[] srvnames = cfg.getTuple("servers", "|", false, srvnames_sys);
		dnsservers = new ServerHandle[srvnames.length];

		always_tcp = cfg.getBool("@alwaystcp", false);
		mx_fallback_a = cfg.getBool("query_mx/@fallback_a", false);
		mx_maxrr = cfg.getInt("query_mx/@maxrr", false, 5);
		retrymax = cfg.getInt("retry/@max", false, 3);
		retrytmt = cfg.getTime("retry/@timeout", "15s");
		retrytmt_tcp = cfg.getTime("retry/@timeout_tcp", "60s");
		retrystep = cfg.getTime("retry/@step", "20s");
		negttl = cfg.getTime("cache/@negttl", "1h");
		cache_hiwater = cfg.getInt("cache/@hiwater", true, 5000);
		cache_hiwater_mx = cfg.getInt("cache/@hiwater_mx", true, 2500);
		tmtcacheprune = cfg.getTime("cache/@prune", "4h");
		dump_on_exit = cfg.getBool("cache/@exitdump", false);
		
		com.grey.naf.BufferSpec bufspec_tcp = new com.grey.naf.BufferSpec(pktsiztcp, pktsiztcp, true);
		com.grey.naf.BufferSpec bufspec_udp = new com.grey.naf.BufferSpec(pktsizudp, pktsizudp, true);

		if (!always_tcp) {
			udpdnspkt = new Packet(false, bufspec_udp);
		}
		Packet.Factory pktfact = new Packet.Factory(true, bufspec_tcp);
		pktstore = new com.grey.base.utils.ObjectWell<Packet>(pktfact, "DNS_TCP_packets_"+dsptch.name);

		QueryHandle.Factory qryfact = new QueryHandle.Factory(dsptch);
		qrystore = new com.grey.base.utils.ObjectWell<QueryHandle>(qryfact, "DNS_QH_"+dsptch.name);

		String dlm = "";
		for (int idx = 0; idx != dnsservers.length; idx++) {
			dnsservers[idx] = new ServerHandle(srvnames[idx], Packet.INETPORT, this, dsptch, always_tcp, bufspec_udp, cfg);
			selectedservers = selectedservers + dlm + srvnames[idx];
			dlm = " | ";
		}
		long tmt = 0;

		for (int idx = 0; idx <= retrymax; idx++) {
			tmt += retrytmt + (retrystep * idx);
		}
		com.grey.naf.nafman.Registry reg = com.grey.naf.nafman.Registry.get();
		reg.registerHandler(com.grey.naf.nafman.Registry.CMD_DNSDUMP, this, dsptch);
		reg.registerHandler(com.grey.naf.nafman.Registry.CMD_DNSPRUNE, this, dsptch);

		dsptch.logger.info("DNS-Resolver: Selected DNS servers: "+dnsservers.length+" ["+selectedservers+"]");
		if (always_tcp) dsptch.logger.info("DNS-Resolver: In always-TCP mode");
		dsptch.logger.info("DNS-Resolver: MX-A-fallback="+mx_fallback_a+"; MX-maxrr="+mx_maxrr);
		dsptch.logger.trace("DNS-Resolver: retry-timeout="+com.grey.base.utils.TimeOps.expandMilliTime(retrytmt)
				+"/"+com.grey.base.utils.TimeOps.expandMilliTime(retrystep)
				+"; max-retries="+retrymax+" (window="+com.grey.base.utils.TimeOps.expandMilliTime(tmt)
				+") - timeout-TCP="+com.grey.base.utils.TimeOps.expandMilliTime(retrytmt_tcp));
		dsptch.logger.trace("DNS-Resolver: negative-TTL="+com.grey.base.utils.TimeOps.expandMilliTime(negttl)
				+"; prune-interval="+com.grey.base.utils.TimeOps.expandMilliTime(tmtcacheprune)
				+"; hiwater="+cache_hiwater+"/MX="+cache_hiwater_mx);
		dsptch.logger.trace("DNS-Resolver: direct-bufs="+bufspec_udp.directbufs+"; recv-limit="+udprcvmax
				+"; udpsiz="+pktsizudp+"; tcpsiz="+pktsiztcp);
	}

	public void start() throws java.io.IOException
	{
		for (int idx = 0; idx != dnsservers.length; idx++) {
			dnsservers[idx].start();
		}
		tmr_prunecache = dsptch.setTimer(tmtcacheprune, TMRTYPE_PRUNECACHE, this);
	}

	// We abort all outstanding requests without notifying the callers.
	// It's up to whatever intelligence is stopping us to handle the implications of that (but this is probably being done as part of
	// a system shutdown, so there'll be no other entities left to care).
	public void stop()
	{
		dsptch.logger.info("DNS-Resolver received Stop request - pending="+pendingreqs.size());

		if (dump_on_exit) {
			pruneCache();
			String pthnam = dumpCache("Dumping cache on exit");
			dsptch.logger.info("Dumped final cache to "+pthnam);
		}
		com.grey.base.utils.IteratorInt iter = pendingreqs.keysIterator();
		while (iter.hasNext()) {
			int qid = iter.next();
			QueryHandle qh = pendingreqs.get(qid);
			if (qid == qh.qid) qrystore.store(qh.clear());  //main pendingreqs entry - be careful not to double-count for prevqids entries
		}
		pendingreqs.clear();
		pendingdoms_a.clear();
		pendingdoms_ptr.clear();
		pendingdoms_mx.clear();
		cache_a.clear();
		cache_ptr.clear();
		cache_mx.clear();

		for (int idx = 0; idx != dnsservers.length; idx++) {
			dnsservers[idx].stop();
			dnsservers[idx] = null;
		}
		if (tmr_prunecache != null) tmr_prunecache.cancel();
		tmr_prunecache = null;
	}

	@Override
	public void handleNAFManCommand(com.grey.naf.nafman.Command cmd)
	{
		sbtmp.setLength(0);

		switch (cmd.def.code)
		{
		case com.grey.naf.nafman.Registry.CMD_DNSDUMP:
			String pthnam = dumpCache("NAFMAN-initiated dump");
			if (pthnam == null) {
				sbtmp.append("Failed to dump DNS cache");
			} else {
				sbtmp.append("Dumped DNS cache to ").append(pthnam);
			}
			break;
		case com.grey.naf.nafman.Registry.CMD_DNSPRUNE:
			pruneCache();
			break;
		default:
			// we've obviously registered for this command, so we must be missing a Case label - clearly a bug
			dsptch.logger.error("DNS NAFMAN Handler: Missing case for cmd="+cmd);
			return;
		}
		cmd.sendResponse(dsptch, sbtmp);
	}

	public Answer resolve(byte qtype, com.grey.base.utils.ByteChars qname, Resolver.Client caller, Object callerparam, int flags)
	{
		// try to satisfy query from our local cache first
		inUserCall = true;
		Answer answer = lookupCache(qtype, qname);

		if (answer == null) {
			if ((flags & com.grey.naf.dns.Resolver.FLAG_NOQRY) != 0) {
				answer = dnsAnswer.set(Answer.STATUS.NODOMAIN, qtype, qname);
			} else {
				// A DNS request is required to satisfy this query, and the caller's dnsResolved() method will be called back later.
				// If we encounter an error though, we return that to the user now as our final answer.
				answer = resolveDNS(qtype, qname, caller, callerparam);
			}
		}
		inUserCall = false;
		return answer;
	}

	public Answer resolve(byte qtype, int qip, Resolver.Client caller, Object callerparam, int flags)
	{
		// try to satisfy query from our local cache first
		inUserCall = true;
		Answer answer = lookupCache(qtype, qip);

		if (answer == null) {
			if ((flags & com.grey.naf.dns.Resolver.FLAG_NOQRY) != 0) {
				answer = dnsAnswer.set(Answer.STATUS.NODOMAIN, qtype, qip);
			} else {
				// A DNS request is required to satisfy this query, and the caller's dnsResolved() method will be called back later.
				// If we encounter an error though, we return that to the user now as our final answer.
				answer = resolveDNS(qtype, qip, caller, callerparam);
			}
		}
		inUserCall = false;
		return answer;
	}

	private Answer resolveDNS(byte qtype, com.grey.base.utils.ByteChars qname, Resolver.Client caller, Object callerparam)
	{
		Answer answer = null;
		QueryHandle qryh = null;

		// If a DNS request for this domain is already underway, then rather than duplicate that, we simply add this caller to the list of those
		// waiting on the request.
		if (qtype == Resolver.QTYPE_MX) {
			qryh = pendingdoms_mx.get(qname);
		} else {
			qryh = pendingdoms_a.get(qname);
		}

		if (qryh == null) {
			// we need to issue a new DNS request
			qryh = qrystore.extract().init(dsptch, this, caller, callerparam, nextServer(), qtype, qname);

			if (issueRequest(qryh, qryh.qtype, qryh.qname) != 0) {
				answer = dnsAnswer.set(Answer.STATUS.ERROR, qtype, qname);
			} else {
				if (qtype == Resolver.QTYPE_MX) {
					pendingdoms_mx.put(qryh.qname, qryh);
				} else {
					pendingdoms_a.put(qryh.qname, qryh);
				}
			}
		} else {
			// Null caller means we want the request to happen, but don't want to be informed of result (of dubious usefulness)
			if (caller != null) qryh.addCaller(caller, callerparam);
		}
		return answer;
	}

	private Answer resolveDNS(byte qtype, int qip, Resolver.Client caller, Object callerparam)
	{
		Answer answer = null;
		QueryHandle qryh = pendingdoms_ptr.get(qip);

		// If a DNS request for this domain is already underway, then rather than duplicate that, we simply add this caller to the list of those
		// waiting on the request.
		if (qryh == null) {
			// we need to issue a new DNS request
			qryh = qrystore.extract().init(dsptch, this, caller, callerparam, nextServer(), qtype, qip);

			if (issueRequest(qryh, qryh.qtype, qryh.qname) != 0) {
				answer = dnsAnswer.set(Answer.STATUS.ERROR, qtype, qip);
			} else {
				pendingdoms_ptr.put(qip, qryh);
			}
		} else {
			// Null caller means we want the request to happen, but don't want to be informed of result (useful for testing)
			if (caller != null) qryh.addCaller(caller, callerparam);
		}
		return answer;
	}

	// This is expected to be a relatively rare event, and the number of pending requests is never expected to be very large, so make do with
	// a simple iteration.
	// We only cancel the caller, not the DNS requests themselves. Since we've already issued them, we might as well cache the results when they
	// arrive.
	public int cancel(Resolver.Client caller)
	{
		int reqs = 0;
		java.util.Iterator<QueryHandle> it = pendingreqs.valuesIterator();

		while (it.hasNext()) {
			QueryHandle qh = it.next();
			reqs += qh.removeCaller(caller);
		}
		return reqs;
	}

	void endQuery(QueryHandle qryh, Answer.STATUS result)
	{
		if (result == Answer.STATUS.OK) {
			if (qryh.rrdata.size() == 0) {
				// this is different to other failures, as we will cache it
				result = Answer.STATUS.NODOMAIN;
				ResourceData negRR = new ResourceData(null, 0, Resolver.QTYPE_NEGATIVE, (byte)0, dsptch.systime() + negttl, 0, null);
				qryh.rrdata.add(negRR);
			}
		} else {
			qryh.rrdata.clear();
		}
		Answer answer = null;

		if (qryh.qtype == Resolver.QTYPE_PTR) {
			answer = dnsAnswer.set(result, qryh.qtype, qryh.qip);
		} else {
			answer = dnsAnswer.set(result, qryh.qtype, qryh.qname);
		}

		if (qryh.rrdata.size() != 0) {
			// now cache the result
			if (qryh.qtype == Resolver.QTYPE_MX) {
				java.util.ArrayList<ResourceData> rrdata = new java.util.ArrayList<ResourceData>(qryh.rrdata);
				cache_mx.put(qryh.qname, rrdata);
			} else if (qryh.qtype == Resolver.QTYPE_PTR) {
				cache_ptr.put(qryh.qip, qryh.rrdata.get(0));
			} else {
				// RR's domnam is left as null in this case.
				// NB: RR's domnam might not even be the same as qryh.qname, if we've taken a CNAME as the answer.
				cache_a.put(qryh.qname, qryh.rrdata.get(0));
			}
			answer.rrdata.addAll(qryh.rrdata);
		}

		if (qryh.qtype == Resolver.QTYPE_MX) {
			pendingdoms_mx.remove(qryh.qname);
		} else if (qryh.qtype == Resolver.QTYPE_PTR) {
			pendingdoms_ptr.remove(qryh.qip);
		} else {
			pendingdoms_a.remove(qryh.qname);
		}
		queryCompleted(qryh, answer);
	}

	// Note that if inUserCall is true here, that invariably means we ended up in here because we failed to
	// issue the request, so the top-level caller will get an error code back and synchronously return the
	// bad result to the external caller.
	private void queryCompleted(QueryHandle qryh, Answer answer)
	{
		if (answer.result != Answer.STATUS.OK) clearPendingQuery(qryh);  // ought to have been removed already, in non-error case
		if (!inUserCall) qryh.notifyCallers(answer);
		qrystore.store(qryh.clear());
	}

	private Answer lookupCache(byte qtype, com.grey.base.utils.ByteChars qname)
	{
		java.util.ArrayList<ResourceData> mxips = null;
		ResourceData rr = null;

		if (qtype == Resolver.QTYPE_MX) {
			if ((mxips = cache_mx.get(qname)) != null) {
				rr = mxips.get(0);
				if (rr.ttl < dsptch.systime()) {
					cache_mx.remove(qname);
					rr = null;
				}
			}
		} else {
			rr = ipcacheGet(qname);
		}
		Answer answer = null;

		if (rr != null) {
			if (rr.rrtype == Resolver.QTYPE_NEGATIVE) {
				answer = dnsAnswer.set(Answer.STATUS.NODOMAIN, qtype, qname);
			} else {
				answer = dnsAnswer.set(Answer.STATUS.OK, qtype, qname);
				if (qtype == Resolver.QTYPE_MX) {
					answer.rrdata.addAll(mxips);
				} else {
					answer.rrdata.add(rr);
				}
			}
		}
		return answer;
	}

	private Answer lookupCache(byte qtype, int qip)
	{
		ResourceData rr = cache_ptr.get(qip);

		if (rr != null && rr.ttl < dsptch.systime()) {
			// stale data, so remove it and say we found nothing
			cache_ptr.remove(qip);
			rr = null;
		}
		if (rr == null) return null;
		Answer answer = dnsAnswer.set(Answer.STATUS.OK, qtype, qip);

		if (rr.rrtype == Resolver.QTYPE_NEGATIVE) {
			answer.result = Answer.STATUS.NODOMAIN;
		} else {
			answer.rrdata.add(rr);
		}
		return answer;
	}

	private ResourceData ipcacheGet(com.grey.base.utils.ByteChars domnam)
	{
		ResourceData rr = cache_a.get(domnam);

		if (rr != null && rr.ttl < dsptch.systime()) {
			// stale data, so remove it and act as if we never found it
			cache_a.remove(domnam);
			rr = null;
		}
		return rr;
	}

	// Unlike cache_a entries created by endQuery(), this sets the domnam field (since it also becomes the key)
	private ResourceData ipcacheAdd(ResourceData rrtmp)
	{
		ResourceData rr = new ResourceData(rrtmp, true);
		cache_a.put(rr.domnam, rr);
		return rr;
	}

	void udpResponseReceived(ServerHandle srvr) throws java.io.IOException
	{
		int pktcnt = 0;
		while (udpdnspkt.receive(true, srvr, null) > 0) {
			processResponse(null);
			if (++pktcnt == udprcvmax) break;
		}
	}

	@Override
	public void timerIndication(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d)
	{
		if (tmr.type == TMRTYPE_PRUNECACHE) {
			tmr_prunecache = null;
			pruneCache();
			tmr_prunecache = dsptch.setTimer(tmtcacheprune, TMRTYPE_PRUNECACHE, this);
			return;
		}
		QueryHandle qryh = (QueryHandle)tmr.attachment;
		qryh.tmr = null;  // this timer is now expired, so we must not access it again

		// one timeout is enough to fail a TCP query
		if (qryh.isTCP() || (qryh.retrycnt == retrymax)) {
			endQuery(qryh, Answer.STATUS.TIMEOUT);
			return;
		}

		// record timed-out QID before issueRequest() sets new one, so that we can still recognise late responses that arrive before entire query ends
		if (qryh.prevqid == null) qryh.prevqid = new int[retrymax];
		qryh.prevqid[qryh.retrycnt++] = qryh.qid;

		// now reissue previous query
		if (dsptch.logger.isActive(LEVEL.TRC2)) {
			dsptch.logger.log(LEVEL.TRC2, "DNS-Resolver timeout "+qryh.retrycnt+"/"+retrymax+" on "+qryh.pktqtype+"/"+qryh.pktqname);
		}
		issueRequest(qryh, qryh.pktqtype, qryh.pktqname);
	}

	private int switchTcp(QueryHandle qryh)
	{
		int errcod = 0;  // any non-zero value means error

		try {
			qryh.connect(retrytmt_tcp);  // TCP should time itself out long before retrytmt_tcp expires
		} catch (Throwable ex) {
			dsptch.logger.log(LEVEL.TRC2, ex, false, "Resolver: TCP connect failed");
			endQuery(qryh, Answer.STATUS.ERROR);
			errcod = 1;
		}
		return errcod;
	}

	// Regarding AXFR:
	// This method is not exposed to callers outside this package, and the Resolver interface provides no means of calling
	// in here with qtype=AXFR, so the facility is strictly experimental and its code path is not even complete. For instance,
	// endQuery() makes no provision for it and would wrongly treat it as a type-A query.
	// Strictly speaking, the Resolver interface only supports A, PRT and MX queries.
	int issueRequest(QueryHandle qryh, byte qtype, com.grey.base.utils.ByteChars qname)
	{
		Packet pkt = qryh.getPacket(udpdnspkt);
		long tmt = retrytmt + (retrystep * qryh.retrycnt);

		if (qryh.isTCP()) {
			tmt = retrytmt_tcp;
		} else {
			if (qryh.qtype == Resolver.QTYPE_AXFR || always_tcp) return switchTcp(qryh);
		}
		qryh.pktqtype = qtype;
		qryh.pktqname = qname;

		pkt.reset();
		pkt.qid = ((next_qryid++) & 0xFFFF);
		pkt.qcnt = 1;
		if (qryh.qtype != Resolver.QTYPE_AXFR) pkt.wantRecursion();  //turning this bit on doesn't seem to upset BIND AXFR, but it's not strictly allowed
		int off = pkt.encodeHeader();
		off = pkt.encodeQuestion(off, qtype, qname);
		pkt.endMessage(off);

		// set the expected Response ID - for UDP responses, we rely on this to identify the associated query, but for TCP it's just a sanity check
		qryh.qid = pkt.qid;
		if (!pkt.isTCP) pendingreqs.put(qryh.qid, qryh);
		qryh.startTimer(this, tmt);

		int errcod = qryh.send(pkt);
		if (errcod != 0) endQuery(qryh, Answer.STATUS.ERROR);
		return errcod;
	}

	void processResponse(QueryHandle qryh)
	{
		boolean validresponse = false;
		Packet pkt = (qryh == null ? udpdnspkt : qryh.getPacket(null));  //non-null qryh means TCP, so getPacket() param will be ignored
		int off = pkt.decodeHeader();  // Parse packet header. Don't bother checking RCODE, as presence or absence of answers is enough

		if (pkt.isResponse()) {
			if (pkt.isTCP) {
				validresponse = (pkt.qid == qryh.qid);
			} else {
				validresponse = ((qryh = pendingreqs.get(pkt.qid)) != null);
			}
		}

		if (validresponse) {
			// Not quite done yet. Need to parse the question to be completely assured of response's validity.
			// It can only fail this test due to QID wrap-around, a vanishingly rare event in normal circumstances.
			badquestion = false;
			off = pkt.parseSection(off, Packet.SECT_QUESTIONS, pkt.qcnt, qryh, tmprr, tmpnam);
			validresponse = !badquestion;
		}

		if (!validresponse) {
			if (dsptch.logger.isActive(LEVEL.TRC)) dsptch.logger.trace("DNS Resolver discarding unexpected packet: Response="+pkt.isResponse()
					+", QID="+pkt.qid+" vs "+(qryh==null?-1:qryh.qid)+" - TCP="+pkt.isTCP);
			if (pkt.isTCP) endQuery(qryh, Answer.STATUS.ERROR);  // no excuse for TCP connections, so abort it with failure
			return;
		}
		clearPendingQuery(qryh);  // this is definitely the expected response for this QID, so clear from pending cache

		if (pkt.isTrunc()) {
			// Response is truncated, so reissue request via TCP.
			// NB: We've already parsed the question, but that's ok because we always expect it to be present, even in a truncated packet.
			if (pkt.isTCP) {
				// already in TCP mode, so truncation shouldn't happen
				endQuery(qryh, Answer.STATUS.ERROR);
				return;
			}
			switchTcp(qryh);
			return;
		}
		
		// valid response packet - parse Answers section
		if (pkt.anscnt != 0) off = pkt.parseSection(off, Packet.SECT_ANSWERS, pkt.anscnt, qryh, tmprr, tmpnam);
		boolean completed = true;

		switch (qryh.qtype)
		{
		case Resolver.QTYPE_MX:
			// If there's too much data, many DNS servers will send fewer Additional A RRs than Answer MX records, rather than marking the packet
			// truncated and sending nothing (on the basis that something is better than nothing). Quite apart from that, the Additional Info section
			// is only intended as a helpful supplement, and there's never a guarantee it will contain all the required info.
			// We may therefore need to issue follow-on queries to retrieve the addresses of the outstanding MX hosts. "Queries" in the plural, because
			// DNS only supports 1 question per query packet.
			// I have considered simply repeating the query in TCP mode to get all the required data back in one response, but while that would enable
			// this query to complete fractionally sooner, it would actually require more network traffic than a few more UDP requests, so overall
			// system throughput would tend to be reduced.
			if (qryh.mxquery == -1) {
				// initial MX query
				if (mx_fallback_a) {
					// handle falling back to a simple A query (as per RFC-2821 Section 5 - the Implicit MX rule)
					if (qryh.fallback_mx_a) {
						// we've already fallen back to an A query, and this is the response
						qryh.fallback_mx_a = false;
					} else if (qryh.rrdata.size() == 0) {
						// No MX answers (there might be a CNAME, which we ignore, so don't check pkt.anscnt), so
						// fall back to a simple A query now.
						qryh.fallback_mx_a = true;
						issueRequest(qryh, Resolver.QTYPE_A, qryh.qname);
					}
				}

				// Additional-Info section is usually required to qualify any MX answers we received
				if (qryh.mxcount != 0) {
					if (pkt.infocnt != 0) {
						off = pkt.skipSection(off, Packet.SECT_AUTH, pkt.authcnt);
						off = pkt.parseSection(off, Packet.SECT_INFO, pkt.infocnt, qryh, tmprr, tmpnam);
					}
					if (qryh.mxcount != 0) qryh.mxquery = 0; // still some unresolved MX answers - will have to issue follow-on A queries
				}
			} else {
				// this is a follow-on query - if we didn't find the address of the current MX host, just discard it
				if (qryh.rrdata.get(qryh.mxquery).rrtype == Resolver.QTYPE_MX) {
					// mxquery still points at original MX record, so the A lookup must have failed
					ResourceData rr = qryh.rrdata.remove(qryh.mxquery);
					rrstore.store(rr);
					qryh.mxcount--;
				}
			}
			completed = (qryh.mxcount == 0 && !qryh.fallback_mx_a);

			if (completed) {
				if (mx_maxrr != 0) {
					// if a limit was configured on how many MX RRs to store, discard the excess ones now
					boolean trimmed = false;
					while (qryh.rrdata.size() > mx_maxrr) {
						qryh.rrdata.remove(qryh.rrdata.size() - 1);
						trimmed = true;
					}
					if (trimmed) qryh.rrdata.trimToSize();
				}
			} else if (!qryh.fallback_mx_a) {
				// scan forward to next unresolved MX host, and issue a follow-on A query
				while (qryh.rrdata.get(qryh.mxquery).rrtype != Resolver.QTYPE_MX) qryh.mxquery++;
				issueRequest(qryh, Resolver.QTYPE_A, qryh.rrdata.get(qryh.mxquery).domnam);
			}
			break;

		case Resolver.QTYPE_AXFR:
			// We can't tell when server has finished TCP responses, so wait for it to close the connection, or our own timer to fire.
			completed = false;
			break;

		default: //no-op
			break;
		}
		if (completed) endQuery(qryh, Answer.STATUS.OK);

	}

	// NB: despite the impression that may be given by the 'qnum' parameter, DNS only permits one question per query
	void loadQuestion(QueryHandle qryh, byte qtype, byte qclass, int qnum, com.grey.base.utils.ByteChars qname)
	{
		if (qtype != qryh.pktqtype || !qname.equals(qryh.pktqname)) badquestion = true;
	}

	// If we made a type-A query for what turns out to be a CNAME, then this method depends on the CNAME's A record
	// also being included in the answer. This is because we ignore CNAME RRs, but blindly accept any A RR in the Answer
	// section of a type-A query.
	// The cache entries will be long-lived, so it's not wasteful to create new RRs for them in here.
	// Note that pktrr represents a temp object that will be reused after this call returns, so we need to take a copy
	// of its fields if we want to keep them longer term.
	void loadRR(QueryHandle qryh, ResourceData pktrr, int sectiontype, int rrnum)
	{
		if (pktrr.domnam.length() == 0) return;  //discard invalid RR domain names
		ResourceData rr;

		switch (pktrr.rrtype)
		{
		case Resolver.QTYPE_A:
			// For hostname queries, we take the first A RR answer we get back as our final answer, and cache it.
			// Cache it under the query name rather than it's own, as our query name may have been an alias, and if so we want to cache the A RR
			// under that name, in order to satisfy the request. This means we would also get a CNAME RR elsewhere in this Answer section, and we
			// will ignore it.
			if (qryh.qtype == Resolver.QTYPE_A) {
				if (qryh.rrdata.size() == 0) {
					rr = new ResourceData(pktrr, false);
					qryh.rrdata.add(rr);
				}
			} else if (qryh.qtype == Resolver.QTYPE_MX) {
				int mxnum = qryh.mxquery;
				if (mxnum == -1) {
					// initial MX response
					if (qryh.fallback_mx_a) {
						// we've fallen back to an A query, so take the first A RR we get as the answer - it will be the sole entry in rrdata
						if (qryh.rrdata.size() == 0) {
							rr = ipcacheAdd(pktrr);
							qryh.rrdata.add(rr);
						}
						break;
					}

					// this must be the Additional-Info section, so check if current A RR matches any MX hosts
					for (int idx = 0; idx != qryh.rrdata.size(); idx++) {
						if (qryh.rrdata.get(idx).rrtype == Resolver.QTYPE_MX) {
							if (qryh.rrdata.get(idx).domnam.equals(pktrr.domnam)) {
								mxnum = idx;
								break;
							}
						}
					}
				}

				// If in MX follow-on mode - this A RR resolves the MX RR at position 'mxnum' on the 'rrdata' list
				// We only take the first AA RR we see, so check if we've already replaced the original MX RR with an A one.
				// This will become a long-lived cache RR, so ok to do these allocations.
				if (mxnum != -1 && qryh.rrdata.get(mxnum).rrtype != Resolver.QTYPE_A) {
					rr = ipcacheAdd(pktrr);
					ResourceData mxrr = qryh.rrdata.set(mxnum, rr);
					rrstore.store(mxrr);
					qryh.mxcount--;
				}
			}
			break;

		case Resolver.QTYPE_MX:
			ResourceData iprr = ipcacheGet(pktrr.domnam);
			int pref = pktrr.pref;
			int pos = 0; // will insert new RR at head of list, if we don't find any lower preferences
			if (iprr != null) {
				if (pktrr.rrtype == Resolver.QTYPE_NEGATIVE) break;  // pseudo-RR which tells us this hostname is known not to exist - discard this MX host
				rr = iprr;
			} else {
				// allocate temp MX RR on rrdata list, until we can obtain the A RR corresponding to its relay-hostname
				rr = rrstore.extract().set(pktrr, true);
				qryh.mxcount++;
			}
			for (int idx = qryh.rrdata.size() - 1; idx != -1; idx--) {
				if (qryh.rrdata.get(idx).pref <= pref) {
					// insert new RR after this node
					pos = idx + 1;
					break;
				}
			}
			qryh.rrdata.add(pos, rr);
			break;

		case Resolver.QTYPE_PTR:
			// presume that this was also the query type - like QTYPE_A, we only take the first answer we see
			if (qryh.rrdata.size() == 0) {
				rr = new ResourceData(pktrr, true);
				qryh.rrdata.add(rr);
			}
			break;

		default:  // ignore
			break;
		}
	}

	private void clearPendingQuery(QueryHandle qryh)
	{
		for (int idx = 0; idx != qryh.retrycnt; idx++) {
			// clear any previously unanswered requests associated with this query as well
			pendingreqs.remove(qryh.prevqid[idx]);
		}
		pendingreqs.remove(qryh.qid);
		qryh.retrycnt = 0;
	}

	com.grey.base.utils.ByteChars buildReverseDomain(int ipaddr)
	{
		sbtmp.setLength(0);
		int shift = 0;

		for (int idx = 0; idx != com.grey.base.utils.IP.IPADDR_OCTETS; idx++) {
			int bval = (ipaddr >> shift) & 0xFF;
			shift += 8;
			sbtmp.append(bval).append('.');
		}
		sbtmp.append("in-addr.arpa");

		com.grey.base.utils.ByteChars nambuf = bcstore.extract();
		nambuf = nambuf.set(sbtmp);
		return nambuf;
	}

	public String dumpCache(CharSequence msg)
	{
		String pthnam = dsptch.nafcfg.path_var+"/DNSdump-"+dsptch.name+".txt";
		int rrcnt = cache_a.size() + cache_ptr.size();
		java.io.OutputStream fout;

		try {
			pthnam = new java.io.File(pthnam).getCanonicalPath();
			fout = new java.io.FileOutputStream(pthnam, true);
		} catch (Exception ex) {
			dsptch.logger.log(LEVEL.ERR, ex, false, "DNS-Resolver failed to create dumpfile="+pthnam);
			return null;
		}
		java.io.PrintWriter ostrm = new java.io.PrintWriter(fout);

		try {
			ostrm.println("===========================================================");
			if (msg != null) ostrm.println(msg);
			ostrm.println("Time is "+new java.util.Date(dsptch.systime()));
			ostrm.println("A = "+cache_a.size()+"; MX = "+cache_mx.size()+"; PTR = "+cache_ptr.size());

			if (cache_a.size() != 0) ostrm.println();
			java.util.Iterator<com.grey.base.utils.ByteChars> itip = cache_a.keySet().iterator();
			while (itip.hasNext()) {
				com.grey.base.utils.ByteChars k = itip.next();
				ostrm.println("A: "+k+" - "+cache_a.get(k));
			}

			if (cache_mx.size() != 0) ostrm.println();
			java.util.Iterator<com.grey.base.utils.ByteChars> itmx = cache_mx.keySet().iterator();
			while (itmx.hasNext()) {
				com.grey.base.utils.ByteChars k = itmx.next();
				java.util.ArrayList<ResourceData> mxips = cache_mx.get(k);
				int cnt = mxips.size();
				rrcnt += cnt;
				ostrm.println("MX: "+k+" - RRs="+cnt);
				for (int idx = 0; idx != cnt; idx++) {
					ostrm.println("\tRR #"+idx+": "+mxips.get(idx));
				}
			}

			if (cache_ptr.size() != 0) ostrm.println();
			com.grey.base.utils.IteratorInt iter = cache_ptr.keysIterator();
			while (iter.hasNext()) {
				int key = iter.next();
				sbtmp.setLength(0);
				sbtmp.append("PTR: ");
				com.grey.base.utils.IP.displayDottedIP(key, sbtmp);
				sbtmp.append(" - ");
				sbtmp.append(cache_ptr.get(key));
				ostrm.println(sbtmp);
			}
			if (rrcnt != 0) ostrm.println(com.grey.base.config.SysProps.EOL+"Total RRs = "+rrcnt);
			dsptch.logger.trace("DNS Resolver dumped cache: A="+cache_a.size()+"; MX="+cache_mx.size()+"; PTR="+cache_ptr.size()+" (total RRs="+rrcnt+")"
					+" - "+pthnam);
		} catch (Throwable ex) {
			dsptch.logger.log(LEVEL.ERR, ex, true, "DNS-Resolver failed to dump cache to "+pthnam);
			pthnam = null;
		}

		try {
			ostrm.close();
		} catch (Exception ex) {
			dsptch.logger.log(LEVEL.ERR, ex, false, "DNS-Resolver failed to close dumpfile="+pthnam);
			pthnam = null;
		}
		return pthnam;
	}

	// remove expired entries from our DNS cache
	public void pruneCache()
	{
		long time1 = System.currentTimeMillis();
		int rrcnt = cache_a.size() + cache_ptr.size();
		int rrdelcnt = 0;

		// delete all cache_a entries with an expired TTL
		int oldsize = cache_a.size();
		java.util.Iterator<com.grey.base.utils.ByteChars> itip = cache_a.keySet().iterator();
		while (itip.hasNext()) {
			com.grey.base.utils.ByteChars k = itip.next();
			if (cache_a.get(k).ttl < dsptch.systime()) itip.remove();
		}
		int dels = oldsize - cache_a.size();
		rrdelcnt += dels;
		if (dels != 0) dsptch.logger.trace("DNS Resolver: Pruned stale IP cache entries: " + dels + "/" + oldsize);

		int excess = cache_a.size() - cache_hiwater;
		if (excess > 0) {
			// delete random entries to bring us down to the stable high-water ceiling
			itip = cache_a.keySet().iterator();
			for (int idx = 0; idx != excess; idx++) {
				itip.next(); itip.remove();
			}
			rrdelcnt += excess;
			dsptch.logger.trace("DNS Resolver: Pruned IP cache back to hiwater="+cache_a.size()+" - excess="+excess);
		}

		// prune MX cache in same way
		oldsize = cache_mx.size();
		java.util.Iterator<com.grey.base.utils.ByteChars> itmx = cache_mx.keySet().iterator();
		while (itmx.hasNext()) {
			// rrdata list should never be empty, but verify anyway
			com.grey.base.utils.ByteChars k = itmx.next();
			java.util.ArrayList<ResourceData> mxips = cache_mx.get(k);
			rrcnt += mxips.size();
			if (mxips.size() == 0 || mxips.get(0).ttl < dsptch.systime()) {rrdelcnt += mxips.size(); itmx.remove();}
		}
		dels = oldsize - cache_mx.size();
		if (dels != 0) dsptch.logger.trace("DNS Resolver: Pruned stale MX cache entries: " + dels + "/" + oldsize);

		if ((excess = cache_mx.size() - cache_hiwater_mx) > 0) {
			itmx = cache_mx.keySet().iterator();
			for (int idx = 0; idx != excess; idx++) {
				com.grey.base.utils.ByteChars k = itmx.next();
				rrdelcnt += cache_mx.get(k).size();
				itmx.remove();
			}
			dsptch.logger.trace("DNS Resolver: Pruned MX cache back to hiwater="+cache_mx.size()+" - excess="+excess);
		}

		// prune PTR cache in same way
		oldsize = cache_ptr.size();
		java.util.Iterator<ResourceData> iter = cache_ptr.valuesIterator();
		while (iter.hasNext()) {
			ResourceData rr = iter.next();
			if (rr.ttl < dsptch.systime()) iter.remove();
		}
		dels = oldsize - cache_ptr.size();
		rrdelcnt += dels;
		if (dels != 0) dsptch.logger.trace("DNS Resolver: Pruned stale PTR cache entries: " + dels + "/" + oldsize);

		if ((excess = cache_ptr.size() - cache_hiwater) > 0) {
			com.grey.base.utils.IteratorInt iter2 = cache_ptr.keysIterator();
			for (int idx = 0; idx != excess; idx++) {
				iter2.next();
				iter2.remove();
			}
			rrdelcnt += excess;
			dsptch.logger.trace("DNS Resolver: Pruned PTR cache back to hiwater="+cache_ptr.size()+" - excess="+excess);
		}
		long time2 = System.currentTimeMillis();
		dsptch.logger.trace("DNS Resolver: Swept cache in time="+(time2-time1)+", removed RRs="+rrdelcnt+"/"+rrcnt
				+" - A="+cache_a.size()+", MX="+cache_mx.size()+", PTR="+cache_ptr.size());
	}

	private ServerHandle nextServer()
	{
		ServerHandle next = dnsservers[next_dnssrv++];
		if (next_dnssrv == dnsservers.length) next_dnssrv = 0;
		return next;
	}

	// NB: This method could also use sun.net.dns.ResolverConfiguration.open().nameservers(), but that's even less portable
	private String getSystemDnsServers() throws javax.naming.NamingException
	{
		java.util.Hashtable<String, String> envinput = new java.util.Hashtable<String, String>();
		envinput.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
		javax.naming.directory.DirContext cntxt =  new javax.naming.directory.InitialDirContext(envinput);
		java.util.Hashtable<?, ?> envfinal = cntxt.getEnvironment(); //NB: Does not return same object we passed into InitialDirContext()
		Object providers = envfinal.get("java.naming.provider.url");
		dsptch.logger.info("DNS-Resolver: Default DNS servers ["+providers+"]");
		if (providers == null) return null;
		String[] serverspecs = String.class.cast(providers).split(" ");
		String pfx = "dns://";
		
		for (int idx = 0; idx != serverspecs.length; idx++) {
			int pos = serverspecs[idx].indexOf(pfx);
			if (pos != -1) serverspecs[idx] = serverspecs[idx].substring(pos + pfx.length());
		}
		String servers = null;

		for (int idx = 0; idx != serverspecs.length; idx++) {
			if (serverspecs[idx] == null || serverspecs[idx].trim().length() == 0) continue;
			if (servers == null) {
				servers = serverspecs[idx];
			} else {
				servers = servers + " | " + serverspecs[idx];
			}
		}
		return servers;
	}
}
