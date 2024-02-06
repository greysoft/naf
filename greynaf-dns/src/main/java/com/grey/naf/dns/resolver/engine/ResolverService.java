/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.engine;

import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;
import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteChars;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.grey.base.collections.HashedMap;
import com.grey.base.collections.HashedMapIntKey;
import com.grey.base.collections.HashedSet;
import com.grey.base.collections.IteratorInt;
import com.grey.base.collections.ObjectPool;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.TimerNAF;
import com.grey.logging.Logger.LEVEL;

/**
 * This class is the entry point to the resolver engine
 */
public class ResolverService
	implements TimerNAF.Handler
{
	private static final String LOGLBL = "DNS-Resolver: ";

	final com.grey.logging.Logger logger;
	private final Dispatcher dsptch;
	private final ResolverConfig config;
	private final CacheManager cachemgr;
	private final CommsManager xmtmgr;
	private final java.io.File fh_dump;
	private final java.util.Random rndgen = new java.util.Random(System.nanoTime());

	// short-lived caches tracking currently ongoing requests
	private final HashedMap<ByteChars, QueryHandle> pendingdoms_a = new HashedMap<>();
	private final HashedMapIntKey<QueryHandle> pendingdoms_ptr = new HashedMapIntKey<>();
	private final HashedMap<ByteChars, QueryHandle> pendingdoms_ns = new HashedMap<>();
	private final HashedMap<ByteChars, QueryHandle> pendingdoms_mx = new HashedMap<>();
	private final HashedMap<ByteChars, QueryHandle> pendingdoms_soa = new HashedMap<>();
	private final HashedMap<ByteChars, QueryHandle> pendingdoms_srv = new HashedMap<>();
	private final HashedMap<ByteChars, QueryHandle> pendingdoms_txt = new HashedMap<>();
	private final HashedMap<ByteChars, QueryHandle> pendingdoms_aaaa = new HashedMap<>();

	//activereqs tracks all requests, while pendingreqs tracks UDP ones only and maps them to their QID
	private final HashedSet<QueryHandle> activereqs = new HashedSet<>();
	private final HashedMapIntKey<QueryHandle> pendingreqs = new HashedMapIntKey<>();

	// protects against QID wrap-around - only applies to UDP
	private final HashedSet<QueryHandle> wrapblocked = new HashedSet<>();
	private TimerNAF tmr_wrapblocked;

	private int next_qryid = rndgen.nextInt();

	//stats
	private final int[] stats_reqcnt = new int[256]; //number of user requests by qtype (we don't use qtype>255)
	private final int[] stats_cachemiss = new int[256]; //cache misses by qtype
	private int stats_ureqs;
	private int stats_umiss;
	int stats_trunc; //truncated UDP responses necessitating a TCP follow-up
	int stats_udpxmt; //UDP packets sent
	int stats_udprcv; //UDP packets received
	int stats_tcpconns; //TCP connections attempted
	int stats_tcpfail; //failed TCP connections (both connection refused and disconnect during session)
	int stats_tmt; //number of individual query timeouts (as opposed to a final result of Timeout)
	int caller_errors;

	// We pre-allocate spare instances of these objects, for efficiency
	private final ObjectPool<QueryHandle> qrystore;
	private final ObjectPool<QueryHandle.WrapperRR> rrwstore;
	private final ObjectPool<ByteChars> bcstore;

	// We can't use dnsAnswer indiscriminately, as it would be corruped by callback chains between nested QueryHandles, so
	// it is purely for the use of top-level callers to whom we have to synchronously return an Answer block. Internal
	// DNS-Resolver code must allocate temp intances from anstore;
	private final ObjectPool<ResolverAnswer> anstore;
	private final ResolverAnswer dnsAnswer = new ResolverAnswer();

	// these are just temporary work areas, pre-allocated for efficiency
	private final ByteChars tmplightbc = new ByteChars(-1); //lightweight object without own storage
	private final StringBuilder sbtmp = new StringBuilder();
	private final PacketDNS pkt_tmp;

	public Dispatcher getDispatcher() {return dsptch;}
	ResolverConfig getConfig() {return config;}
	CacheManager getCacheManager() {return cachemgr;}
	CommsManager getCommsManager() {return xmtmgr;}
	StringBuilder reusableStringBuilder() {return sbtmp;}
	boolean isActive(QueryHandle qh) {return activereqs.contains(qh);}
	QueryHandle.WrapperRR allocWrapperRR(ResourceData rr) {return rrwstore.extract().set(rr);}
	void freeWrapperRR(QueryHandle.WrapperRR rrw) {rrwstore.store(rrw.clear());}
	ResolverAnswer allocAnswerBuf() {return anstore.extract().clear();}
	void freeAnswerBuf(ResolverAnswer buf) {anstore.store(buf);}
	ByteChars allocByteChars() {return bcstore.extract().clear();}
	void freeByteChars(ByteChars bc) {bcstore.store(bc);}
	int nextRandomInt(int bound) {return rndgen.nextInt(bound);}

	public ResolverService(Dispatcher dsptch, ResolverConfig config)
		throws java.io.IOException, javax.naming.NamingException
	{
		this.dsptch = dsptch;
		this.config = config;
		logger = dsptch.getLogger();

		long tmt = config.getRetryTimeout() + (config.getRetryStep() / 2);
		for (int idx = 0; idx <= config.getRetryMax(); idx++) {
			tmt += config.getRetryTimeout() + (config.getRetryStep() * idx) + (config.getRetryStep() / 2);
		}
		if (config.getDnsInterceptor() != null) logger.info(LOGLBL+"DNS-Interceptor mode on - "+config.getDnsInterceptor());
		logger.info(LOGLBL+"Recursive="+config.isRecursive()+(config.isRecursive()?"":" - auto="+config.isAutoRoots()+", roots-file="+config.getPathnameRootServers()));
		logger.info(LOGLBL+"UDP-sender-sockets="+config.getSenderSocketsUDP()+"; TCP-only="+config.isAlwaysTCP());
		logger.info(LOGLBL+"cache_nonbailiwick_glue="+config.isCacheAllGlue());
		logger.info(LOGLBL+"retry-timeout="+TimeOps.expandMilliTime(config.getRetryTimeout())
				+"/"+TimeOps.expandMilliTime(config.getRetryStep())
				+"; max-retries="+config.getRetryMax()+" (window="+TimeOps.expandMilliTime(tmt)
				+") - timeout-TCP="+TimeOps.expandMilliTime(config.getRetryTimeoutTCP()));
		logger.trace(LOGLBL+"negative-TTL="+TimeOps.expandMilliTime(config.getNegativeTTL())
				+"; initialMinTTL="+TimeOps.expandMilliTime(config.getInitialMinTTL())
				+"; lookupMinTTL="+TimeOps.expandMilliTime(config.getLookupMinTTL()));
		logger.trace(LOGLBL+"A-cache: lowater="+config.getCacheLoWaterA()+", hiwater="+config.getCacheHiWaterA());
		logger.trace(LOGLBL+"PTR-cache: lowater="+config.getCacheLoWaterPTR()+", hiwater="+config.getCacheHiWaterPTR());
		logger.trace(LOGLBL+"SOA-cache: lowater="+config.getCacheLoWaterSOA()+", hiwater="+config.getCacheHiWaterSOA());
		logger.trace(LOGLBL+"NS-cache: lowater="+config.getCacheLoWaterNS()+", hiwater="+config.getCacheHiWaterNS()+", maxrr="+config.getNsMaxRR());
		logger.trace(LOGLBL+"MX-cache: lowater="+config.getCacheLoWaterMX()+", hiwater="+config.getCacheHiWaterMX()+", maxrr="+config.getMxMaxRR());
		logger.trace(LOGLBL+"Partial cache prune="+config.isPartialPrune()+", dump-on-exit="+config.isDumpOnExit());
		logger.trace(LOGLBL+"directbufs="+ResolverConfig.DIRECTNIOBUFS+"; udpmax="+ResolverConfig.PKTSIZ_UDP+"; tcpmax="+ResolverConfig.PKTSIZ_TCP);

		String[] localNameServers;
		if (config.getLocalNameServers() == null || config.getLocalNameServers().length == 0) {
			List<String> lst = NameServerUtils.getLocalServers(logger);
			if (lst.isEmpty()) lst = Collections.singletonList("127.0.0.1");
			localNameServers = lst.toArray(new String[lst.size()]);
			logger.info(LOGLBL+"Auto-retrieved local name-servers: "+lst);
		} else {
			localNameServers = config.getLocalNameServers();
			logger.info(LOGLBL+"Configured local name-servers: "+Arrays.asList(localNameServers));
		}

		cachemgr = new CacheManager(dsptch, config, localNameServers); // NB: This can reorder the localNameServers array
		xmtmgr = new CommsManager(this, localNameServers);
		new ResolverNAFMAN(this, config);

		pkt_tmp = new PacketDNS(Math.max(ResolverConfig.PKTSIZ_TCP, ResolverConfig.PKTSIZ_UDP), ResolverConfig.DIRECTNIOBUFS, config.getInitialMinTTL(), dsptch);
		fh_dump = new java.io.File(dsptch.getApplicationContext().getConfig().getPathVar()+"/DNSdump-"+dsptch.getName()+".txt");

		bcstore = new ObjectPool<>(() -> new ByteChars());
		anstore = new ObjectPool<>(() -> new ResolverAnswer());
		rrwstore = new ObjectPool<>(() -> new QueryHandle.WrapperRR());
		qrystore = new ObjectPool<>(() -> new QueryHandle(this));
	}

	public void start() throws java.io.IOException
	{
		xmtmgr.start();
	}

	// We abandon all outstanding requests without notifying the callers.
	// It's up to whatever intelligence is stopping us to handle the implications of that, but this is probably being done
	// as part of a Dispatcher shutdown (if not a JVM process termination), so there'll be nobody left to care).
	public void stop()
	{
		logger.info(LOGLBL+"Received shutdown request - active="+activereqs.size()+"/pending="+pendingreqs.size()
				+"/wrapped="+wrapblocked.size());
		if (config.isDumpOnExit()) {
			cachemgr.prune(null);
			logger.info(LOGLBL+"Dumping final cache to "+fh_dump.getAbsolutePath());
			try {
				FileOps.ensureDirExists(fh_dump.getParentFile());
			} catch (Exception ex) {
				logger.warn(LOGLBL+"Failed to ensure existence of dump directory - "+ex);
			}
			dumpState(fh_dump, "Dumping cache on exit");
		}
		xmtmgr.stop();
		if (tmr_wrapblocked != null) tmr_wrapblocked.cancel();
		tmr_wrapblocked = null;

		java.util.ArrayList<QueryHandle> reqs = new java.util.ArrayList<>(activereqs);
		for (int idx = 0; idx != reqs.size(); idx++) {
			int callers = reqs.get(idx).cancelExternalCallers(ResolverAnswer.STATUS.SHUTDOWN);
			if (callers == 0) requestCompleted(reqs.get(idx));
		}

		if (ObjectPool.DEBUG) {
			if (qrystore.getActiveCount() != 0) throw new IllegalStateException("DNS-Resolver has active timers on exit - count="+qrystore.getActiveCount());
			if (anstore.getActiveCount() != 0) throw new IllegalStateException("DNS-Resolver has active FileWrites on exit - count="+anstore.getActiveCount());
			if (rrwstore.getActiveCount() != 0) throw new IllegalStateException("DNS-Resolver has active FileWrites on exit - count="+rrwstore.getActiveCount());
			if (bcstore.getActiveCount() != 0) throw new IllegalStateException("DNS-Resolver has active FileWrites on exit - count="+bcstore.getActiveCount());
		}
	}

	// This is expected to be a relatively rare event, and the number of pending requests is never expected to be very large,
	// so make do with a simple iteration.
	// We only cancel the caller (as in all notifications due to them), not the DNS requests themselves. Since we've already
	// issued those, we might as well cache the results when they arrive.
	public int cancel(ResolverDNS.Client caller)
	{
		int reqs = 0;
		java.util.Iterator<QueryHandle> it = activereqs.recycledIterator();
		while (it.hasNext()) {
			QueryHandle qh = it.next();
			reqs += qh.removeCaller(caller);
		}
		return reqs;
	}

	public ResolverAnswer resolve(byte qtype, ByteChars qname, ResolverDNS.Client caller, Object callerParam, int flags)
	{
		return resolve(qtype, qname, caller, callerParam, flags, 0, dnsAnswer);
	}

	public ResolverAnswer resolve(byte qtype, int qip, ResolverDNS.Client caller, Object callerParam, int flags)
	{
		return resolve(qtype, qip, caller, callerParam, flags, dnsAnswer);
	}

	ResolverAnswer resolve(byte qtype, ByteChars qname, ResolverDNS.Client caller, Object callerParam,
			int flags, int server_ip, ResolverAnswer answerbuf)
	{
		//update stats
		if (caller != null && caller.getClass() != QueryHandle.class) stats_ureqs++;
		stats_reqcnt[qtype]++;
		if (qname.length() == 0) return answerbuf.set(ResolverAnswer.STATUS.BADNAME, qtype, qname);

		// check if answer is cached first
		qname.toLowerCase();
		ResolverAnswer answer = lookupCache(qtype, qname, answerbuf);
		if (answer != null) return answer; //answer was already in cache
		if (caller != null && caller.getClass() != QueryHandle.class) stats_umiss++;
		stats_cachemiss[qtype]++;

		if ((flags & ResolverDNS.FLAG_NOQRY) != 0) {
			// caller doesn't want to go any further if not cached
			return answerbuf.set(ResolverAnswer.STATUS.NODOMAIN, qtype, qname);
		}

		// A DNS request is required to satisfy this call, and the caller's dnsResolved() method will be called back later.
		// If we encounter an immediate error though, we return that to the user now as our final answer.
		// If a DNS request for this domain is already underway, then rather than duplicate that, we simply add this caller
		// to the list of those waiting on the request.
		QueryHandle qryh = null;
		if (server_ip == 0) {
			if (qtype == ResolverDNS.QTYPE_A) {
				qryh = pendingdoms_a.get(qname);
			} else if (qtype == ResolverDNS.QTYPE_NS) {
				qryh = pendingdoms_ns.get(qname);
			} else if (qtype == ResolverDNS.QTYPE_MX) {
				qryh = pendingdoms_mx.get(qname);
			} else if (qtype == ResolverDNS.QTYPE_SOA) {
				qryh = pendingdoms_soa.get(qname);
			} else if (qtype == ResolverDNS.QTYPE_SRV) {
				qryh = pendingdoms_srv.get(qname);
			} else if (qtype == ResolverDNS.QTYPE_TXT) {
				qryh = pendingdoms_txt.get(qname);
			} else if (qtype == ResolverDNS.QTYPE_AAAA) {
				qryh = pendingdoms_aaaa.get(qname);
			} else {
				throw new UnsupportedOperationException("qtype="+qtype+" - "+qname);
			}
		}
		boolean newqry = (qryh == null);

		if (qryh == null) {
			// an associated DNS request is not currently underway, so we need to issue a new one
			qryh = qrystore.extract().init(qtype, qname);
			activereqs.add(qryh);
			answer = issueQuery(qryh, server_ip, answerbuf);
			if (answer != null) {
				requestCompleted(qryh);
				return answer;
			}

			if (qtype == ResolverDNS.QTYPE_NS) {
				pendingdoms_ns.put(qryh.qname, qryh);
			} else if (qtype == ResolverDNS.QTYPE_MX) {
				pendingdoms_mx.put(qryh.qname, qryh);
			} else if (qtype == ResolverDNS.QTYPE_SOA) {
				pendingdoms_soa.put(qryh.qname, qryh);
			} else if (qtype == ResolverDNS.QTYPE_SRV) {
				pendingdoms_srv.put(qryh.qname, qryh);
			} else if (qtype == ResolverDNS.QTYPE_TXT) {
				pendingdoms_txt.put(qryh.qname, qryh);
			} else if (qtype == ResolverDNS.QTYPE_AAAA) {
				pendingdoms_aaaa.put(qryh.qname, qryh);
			} else {
				pendingdoms_a.put(qryh.qname, qryh);
			}
		}

		if (caller != null) {
			//Must check for deadlock even if qryh is newly allocated, as it could have added itself to another request's
			//caller list inside issueQuery().
			//However that can never happen if we specified an explicit server to send the query to.
			if (caller.getClass() == QueryHandle.class && server_ip == 0) {
				QueryHandle qhcaller = (QueryHandle)caller;
				if (qhcaller.isCaller(qryh)) {
					// Whoa! We're about to piggyback on qryh, but it's already piggybacking on our caller.
					// This can happen with NS resolution and leads to infinite recursion during caller notification,
					// so fail this query to break the loop.
					if (logger.isActive(LEVEL.TRC2)) logger.log(LEVEL.TRC2, LOGLBL+"Request="+ResolverDNS.getQTYPE(qtype)+"/"+qname
							+" has deadlock with caller="+ResolverDNS.getQTYPE(qhcaller.qtype)+"/"+qhcaller.qname);
					if (newqry) requestCompleted(qryh);
					return answerbuf.set(ResolverAnswer.STATUS.DEADLOCK, qtype, qname);
				}
			}
			// NB: There must be no steps in the synchronous resolve() call chain which can fail after this, as that
			// means the caller would receive a failure callback as well as the synchronous error return code.
			qryh.addCaller(caller, callerParam);
		}
		// the result is not yet available, so the caller will be notified later via callback.
		return null;
	}

	// Same logic as above, but for an IP-based request.
	// This is currently used for PTR requests only, and they never generate sub-queries or get issued as sub-queries.
	ResolverAnswer resolve(byte qtype, int qip, ResolverDNS.Client caller, Object callerparam, int flags, ResolverAnswer answerbuf)
	{
		if (caller != null && caller.getClass() != QueryHandle.class) stats_ureqs++;
		stats_reqcnt[qtype]++;
		ResolverAnswer answer = lookupCache(qtype, qip, answerbuf);
		if (answer != null) return answer;
		if (caller != null && caller.getClass() != QueryHandle.class) stats_umiss++;
		stats_cachemiss[qtype]++;

		if ((flags & ResolverDNS.FLAG_NOQRY) != 0) {
			return answerbuf.set(ResolverAnswer.STATUS.NODOMAIN, qtype, qip);
		}
		QueryHandle qryh = pendingdoms_ptr.get(qip);

		if (qryh == null) {
			qryh = qrystore.extract().init(qtype, qip);
			activereqs.add(qryh);
			answer = issueQuery(qryh, 0, answerbuf);
			if (answer != null) {
				requestCompleted(qryh);
				return answer;
			}
			pendingdoms_ptr.put(qip, qryh);
		}
		if (caller != null) qryh.addCaller(caller, callerparam);
		return null;
	}

	private ResolverAnswer issueQuery(QueryHandle qryh, int server_ip, ResolverAnswer answerbuf)
	{
		java.net.InetSocketAddress nsaddr = null;
		boolean sticky_ip = (server_ip != 0);

		if (config.isRecursive()) {
			nsaddr = xmtmgr.nextLocalNameServer();
		} else {
			if (server_ip != 0) {
				nsaddr = cachemgr.createServerTSAP(server_ip);
			} else {
				nsaddr = getNameserver(qryh.qtype, qryh.qname);
				if (nsaddr == null) {
					//the nameserver for the target domain is not cached - issue a nested sub-query for it
					ByteChars dom = getParentDomain(qryh.qtype, qryh.qname);
					ResolverAnswer answer = qryh.issueSubQuery(ResolverDNS.QTYPE_NS, dom, 0, answerbuf);
					if (answer == null) return null; //will resume once we know the nameserver
					if (answer.result != ResolverAnswer.STATUS.OK) return answerbuf.set(answer.result, qryh.qtype, qryh.qname);
					//Query completed with success - nameserver will be cached now, so call self recursively
					//I don't think this can actually happen as wasn't cached just above, but handle it.
					dom = getParentDomain(qryh.qtype, qryh.qname); //parse again, as was probably overwritten
					if (!dom.equals(answer.qname)) {
						//the nameserver is actually several levels up, so use explicit IP to avoid infinite loop
						int idx = rndgen.nextInt(answer.size());
						server_ip = answer.getNS(idx).getIP();
					}
					return issueQuery(qryh, server_ip, answerbuf);
				}
				if (nsaddr.getPort() == 0) {
					// the TSAP was negatively cached
					return answerbuf.set(ResolverAnswer.STATUS.NODOMAIN, qryh.qtype, qryh.qname);
				}
			}
		}

		if (!qryh.isTCP()) {
			if (config.isAlwaysTCP()) {
				qryh.qid = 1; //we only issue one query on a TCP connection
				ResolverAnswer.STATUS result = qryh.switchTCP(nsaddr);
				if (result == ResolverAnswer.STATUS.OK) return null;
				return answerbuf.set(result, qryh.qtype, qryh.qname);
			}
			if (qryh.qid == 0) {
				//we use zero to indicate an unallocated QID
				qryh.qid = (next_qryid++) & 0xFFFF;
				if (qryh.qid == 0) qryh.qid = next_qryid++;

				if (pendingreqs.containsKey(qryh.qid)) {
					//we have issued so many requests so quickly that the QID has wrapped around before they're answered
					wrapblocked.add(qryh);
					if (tmr_wrapblocked == null) tmr_wrapblocked = dsptch.setTimer(config.getWrapRetryFreq(), 0, this);
					return null; //will resume later, when QID is no longer contended
				}
				pendingreqs.put(qryh.qid, qryh);
			}
		}
		ResolverAnswer.STATUS result = qryh.issueQuery(nsaddr, sticky_ip, pkt_tmp);
		if (result == ResolverAnswer.STATUS.OK) return null;
		return answerbuf.set(result, qryh.qtype, qryh.qname);
	}

	ResolverAnswer repeatQuery(QueryHandle qryh, int server_ip)
	{
		ResolverAnswer answerbuf = allocAnswerBuf();
		try {
			ResolverAnswer answer;
			if (qryh.qip != 0) {
				answer = lookupCache(qryh.qtype, qryh.qip, answerbuf);
			} else {
				answer = lookupCache(qryh.qtype, qryh.qname, answerbuf);
			}
			if (answer == null) answer = issueQuery(qryh, server_ip, answerbuf);
			if (answer != null) answer = qryh.endRequest(answer);
			return answer;
		} finally {
			freeAnswerBuf(answerbuf);
		}
	}

	void handleResponse(ByteArrayRef rcvdata, QueryHandle qryh, java.net.InetSocketAddress srvaddr)
	{
		boolean validresponse = false;
		boolean mapped_qryh = false;
		boolean is_tcp = (qryh != null);

		PacketDNS pkt = pkt_tmp;
		pkt.resetDecoder(rcvdata.buffer(), rcvdata.offset(), rcvdata.size());
		int off = pkt.decodeHeader();

		if (off != -1 && pkt.isResponse()) {
			if (is_tcp) {
				validresponse = (pkt.hdr_qid == qryh.qid);
				mapped_qryh = true; //even if packet is wrong, we know we have the right qryh object
			} else {
				qryh = pendingreqs.get(pkt.hdr_qid);
				validresponse = (qryh != null);
			}
		}

		if (validresponse) {
			// Even though QID matches a pending query, do belt-and-braces check that it matches the expected question
			// Note that unmatched and duplicate responses both generally amount to the same thing, namely a duplicate
			// response, in reply to timeout retransmissions by us.
			if (pkt.hdr_qcnt != 1) {
				validresponse = false;
			} else {
				off = pkt.parseQuestion(off, pkt.hdr_qcnt, srvaddr, qryh);
				validresponse = (off != -1);
			}
			if (validresponse) {
				mapped_qryh = true; //if this lined up, we've identified the UDP request handle
				validresponse = (pkt.rcode() == 0);
			}
		}

		if (!validresponse) {
			if (logger.isActive(ResolverConfig.DEBUGLVL)) {
				String msg = (mapped_qryh ? "received rejection " : "discarding invalid ");
				logger.log(ResolverConfig.DEBUGLVL, LOGLBL+msg+(is_tcp?"TCP":"UDP")+" response="+pkt.hdr_qid
						+(qryh==null? "" : "/"+ResolverDNS.getQTYPE(qryh.qtype)+"/"+qryh.qname)
						+" from "+srvaddr+" - ans="+pkt.hdr_anscnt+"/"+pkt.hdr_authcnt+"/"+pkt.hdr_infocnt
						+"/auth="+pkt.isAuth()+"/trunc="+pkt.isTruncated()
						+" - size="+rcvdata.size()+", mapped="+mapped_qryh+", rcode="+pkt.rcode());
			}
			if (mapped_qryh) {
				if (pkt.rcode() == PacketDNS.RCODE_NXDOM) {
					qryh.endRequest(ResolverAnswer.STATUS.NODOMAIN);
				} else {
					qryh.endRequest(ResolverAnswer.STATUS.BADRESPONSE);
				}
			}
			return;
		}
		if (qryh.haveResponse()) return;
		qryh.handleResponse(pkt, off, srvaddr, rcvdata.size());
	}

	void requestCompleted(QueryHandle qryh)
	{
		activereqs.remove(qryh);
		if (qryh.qtype == ResolverDNS.QTYPE_PTR) {
			pendingdoms_ptr.remove(qryh.qip);
		} else if (qryh.qtype == ResolverDNS.QTYPE_NS) {
			pendingdoms_ns.remove(qryh.qname);
		} else if (qryh.qtype == ResolverDNS.QTYPE_MX) {
			pendingdoms_mx.remove(qryh.qname);
		} else if (qryh.qtype == ResolverDNS.QTYPE_SOA) {
			pendingdoms_soa.remove(qryh.qname);
		} else if (qryh.qtype == ResolverDNS.QTYPE_SRV) {
			pendingdoms_srv.remove(qryh.qname);
		} else if (qryh.qtype == ResolverDNS.QTYPE_TXT) {
			pendingdoms_txt.remove(qryh.qname);
		} else if (qryh.qtype == ResolverDNS.QTYPE_AAAA) {
			pendingdoms_aaaa.remove(qryh.qname);
		} else {
			pendingdoms_a.remove(qryh.qname);
		}
		if (pendingreqs.get(qryh.qid) == qryh) pendingreqs.remove(qryh.qid);
		if (qryh.getSubQueryCount() != 0) cancel(qryh);
		qrystore.store(qryh.clear());
	}

	private ResolverAnswer lookupCache(byte qtype, ByteChars qname, ResolverAnswer answerbuf)
	{
		java.util.ArrayList<ResourceData> rrlist = null;
		ResourceData rr = null;

		if (qtype == ResolverDNS.QTYPE_NS || qtype == ResolverDNS.QTYPE_MX || qtype == ResolverDNS.QTYPE_SRV) {
			rrlist = cachemgr.lookupList(qtype, qname);
			if (rrlist != null) rr = rrlist.get(0);
		} else {
			rr = cachemgr.lookup(qtype, qname);
		}
		if (rr == null) return null;
		ResolverAnswer.STATUS sts = (rr.isNegative() ? ResolverAnswer.STATUS.NODOMAIN : ResolverAnswer.STATUS.OK);
		if (qname == tmplightbc) qname = new ByteChars(qname, true); //make it permanent
		answerbuf.set(sts, qtype, qname);
		if (sts == ResolverAnswer.STATUS.OK) {
			if (rrlist != null) {
				answerbuf.rrdata.addAll(rrlist);
			} else {
				answerbuf.rrdata.add(rr);
			}
		}
		return answerbuf;
	}

	private ResolverAnswer lookupCache(byte qtype, int qip, ResolverAnswer answerbuf)
	{
		ResourceData rr = cachemgr.lookup(qtype, qip);
		if (rr == null) return null;
		ResolverAnswer.STATUS sts = (rr.isNegative() ? ResolverAnswer.STATUS.NODOMAIN : ResolverAnswer.STATUS.OK);
		answerbuf.set(sts, qtype, qip);
		if (sts == ResolverAnswer.STATUS.OK) answerbuf.rrdata.add(rr);
		return answerbuf;
	}

	// Throwing here results in a hanging request, but since rewinding past the root domain must be a bug, that's
	// probably a better outcome than returning Answer=ERROR/NODOM
	java.net.InetSocketAddress getNameserver(byte qtype, ByteChars qname)
	{
		java.net.InetSocketAddress nsaddr = null;
		ByteChars dom = getParentDomain(qtype, qname);
		if (dom.length() == 0) throw new IllegalStateException("DNS-Resolver: Root servers are missing!!");
		do {
			nsaddr = cachemgr.lookupNameServer(dom);
			if (nsaddr == null || nsaddr.getPort() != 0) break;
			int dotcnt = StringOps.count(dom, ResolverDNS.DOMDLM);
			if (dotcnt == 0) break;
			if (dotcnt == 1) {
				// certain TLDs are guaranteed to only delegate one level down
				if (dom.endsWith(".com") || dom.endsWith(".org") || dom.endsWith(".net")) break;
			}
			dom = getParentDomain((byte)0, dom);
		} while (nsaddr.getPort() == 0);

		if (nsaddr == null) {
			dom = getParentDomain(qtype, qname);
			QueryHandle qhpending = pendingdoms_ns.get(dom);
			if (qhpending != null) {
				int ip = qhpending.getPartialAnswer();
				if (ip != 0) nsaddr = cachemgr.createServerTSAP(ip);
			}
		}
		return nsaddr;
	}

	ByteChars getParentDomain(byte qtype, ByteChars qname)
	{
		if (qtype == ResolverDNS.QTYPE_SOA || qtype == ResolverDNS.QTYPE_MX || qtype == ResolverDNS.QTYPE_TXT) {
			return qname; //target is already the parent domain
		}
		int pos = qname.indexOf((byte)ResolverDNS.DOMDLM);
		if (pos == -1) return NameServerUtils.ROOTDOM_BC;
		tmplightbc.set(qname.buffer(), qname.offset(pos+1), qname.size()-pos-1);
		if (qtype == ResolverDNS.QTYPE_SRV) return getParentDomain((byte)0, tmplightbc); //next level up is service-protocol, not parent domain
		return tmplightbc;
	}

	@Override
	public void timerIndication(TimerNAF t, Dispatcher d)
	{
		tmr_wrapblocked = null;
		int cnt1 = wrapblocked.size();
		QueryHandle[] arr = wrapblocked.toArray(new QueryHandle[wrapblocked.size()]); //in case of concurrent mods

		for (int idx = 0; idx != arr.length; idx++) {
			QueryHandle qryh = arr[idx];
			if (!pendingreqs.containsKey(qryh.qid)) {
				pendingreqs.put(qryh.qid, qryh);
				wrapblocked.remove(qryh);
				qryh.repeatQuery(0);
			}
		}
		LEVEL lvl = LEVEL.TRC;
		if (logger.isActive(lvl)) {
			String txt = LOGLBL+cnt1+" requests blocked by QID-wraparound";
			if (cnt1 != wrapblocked.size()) txt += " - reduced to "+wrapblocked.size();
			logger.log(lvl, txt);
		}
		if (wrapblocked.size() != 0) tmr_wrapblocked = dsptch.setTimer(config.getWrapRetryFreq(), 0, this);
	}

	ByteChars buildArpaDomain(int ip)
	{
		sbtmp.setLength(0);
		IP.displayArpaDomain(ip, sbtmp);
		return allocByteChars().append(sbtmp);
	}

	void dumpState(java.io.File fh, CharSequence msg) {
		String eol = "\n";
		StringBuilder sb = dumpState(eol, null);
		java.io.OutputStream fout = null;
		java.io.PrintWriter ostrm = null;
		try {
			FileOps.ensureDirExists(fh.getParentFile());
			fout = new java.io.FileOutputStream(fh, false);
			ostrm = new java.io.PrintWriter(fout);
			fout = null;
			ostrm.print("===========================================================\n");
			if (msg != null) ostrm.print(msg+eol);
			ostrm.print(sb);
			ostrm.close();
			ostrm = null;
		} catch (Exception ex) {
			logger.log(LEVEL.ERR, ex, false, LOGLBL+"failed to create dumpfile="+fh.getAbsolutePath()+" - strm="+fout);
		} finally {
			try {
				if (ostrm != null) ostrm.close();
				if (fout != null) fout.close();
			} catch (Exception ex) {
				logger.log(LEVEL.ERR, ex, false, LOGLBL+"failed to close dumpfile="+fh.getAbsolutePath());
			}
		}
	}

	StringBuilder dumpState(String eol, StringBuilder sb) {
		int reqcnt = 0;
		int cachemiss = 0;
		for (int idx = 0; idx != stats_reqcnt.length; idx++) {
			reqcnt += stats_reqcnt[idx];
			cachemiss += stats_cachemiss[idx];
		}
		if (sb == null) sb = new StringBuilder();
		sb.append("DNS-Resolver status as of ").append(new java.util.Date(dsptch.getSystemTime()));
		sb.append(eol).append("Requests=").append(reqcnt).append(" (user=").append(stats_ureqs).append("):");
		sb.append(" A=").append(stats_reqcnt[ResolverDNS.QTYPE_A]);
		sb.append(", AAAA=").append(stats_reqcnt[ResolverDNS.QTYPE_AAAA]);
		sb.append(", PTR=").append(stats_reqcnt[ResolverDNS.QTYPE_PTR]);
		sb.append(", SOA=").append(stats_reqcnt[ResolverDNS.QTYPE_SOA]);
		sb.append(", NS=").append(stats_reqcnt[ResolverDNS.QTYPE_NS]);
		sb.append(", MX=").append(stats_reqcnt[ResolverDNS.QTYPE_MX]);
		sb.append(", SRV=").append(stats_reqcnt[ResolverDNS.QTYPE_SRV]);
		sb.append(", TXT=").append(stats_reqcnt[ResolverDNS.QTYPE_TXT]);
		sb.append(eol).append("Cache misses=").append(cachemiss).append(" (user=").append(stats_umiss).append("):");
		sb.append(" A=").append(stats_cachemiss[ResolverDNS.QTYPE_A]);
		sb.append(", AAAA=").append(stats_cachemiss[ResolverDNS.QTYPE_AAAA]);
		sb.append(", PTR=").append(stats_cachemiss[ResolverDNS.QTYPE_PTR]);
		sb.append(", SOA=").append(stats_cachemiss[ResolverDNS.QTYPE_SOA]);
		sb.append(", NS=").append(stats_cachemiss[ResolverDNS.QTYPE_NS]);
		sb.append(", MX=").append(stats_cachemiss[ResolverDNS.QTYPE_MX]);
		sb.append(", SRV=").append(stats_cachemiss[ResolverDNS.QTYPE_SRV]);
		sb.append(", TXT=").append(stats_cachemiss[ResolverDNS.QTYPE_TXT]);
		sb.append(eol).append("UDP send/receive=").append(stats_udpxmt).append('/').append(stats_udprcv);
		sb.append(" - truncated=").append(stats_trunc).append(", timeouts=").append(stats_tmt);
		sb.append(eol).append("TCP connections=").append(stats_tcpconns-stats_tcpfail).append('/').append(stats_tcpconns);
		if (wrapblocked.size() != 0) sb.append(eol).append(wrapblocked.size()).append(" queries blocked by QID wraparound");
		sb.append(eol);
		cachemgr.dump(eol, sb);
		String dlm1 = " - ";
		String dlm2 = ", ";
		sb.append(eol).append("Pending Requests:");
		dumpPending(pendingdoms_a, "A", dlm1, dlm2, eol, sb);
		dumpPending(pendingdoms_aaaa, "AAAA", dlm1, dlm2, eol, sb);

		sb.append(eol).append("PTR=").append(pendingdoms_ptr.size());
		IteratorInt it = pendingdoms_ptr.keysIterator();
		String dlm = dlm1;
		while (it.hasNext()) {
			int ip = it.next();
			sb.append(dlm);
			IP.displayDottedIP(ip, sb);
			dlm = dlm2;
		}
		dumpPending(pendingdoms_soa, "SOA", dlm1, dlm2, eol, sb);
		dumpPending(pendingdoms_ns, "NS", dlm1, dlm2, eol, sb);
		dumpPending(pendingdoms_mx, "MX", dlm1, dlm2, eol, sb);
		dumpPending(pendingdoms_srv, "SRV", dlm1, dlm2, eol, sb);
		dumpPending(pendingdoms_txt, "TXT", dlm1, dlm2, eol, sb);
		return sb;
	}

	private static void dumpPending(HashedMap<ByteChars, QueryHandle> pending, String qtype, String dlm1, String dlm2, String eol, StringBuilder sb) {
		sb.append(eol).append(qtype).append('=').append(pending.size());
		java.util.Iterator<ByteChars> it = pending.keysIterator();
		String dlm = dlm1;
		while (it.hasNext()) {
			ByteChars bc = it.next();
			sb.append(dlm).append(bc);
			dlm = dlm2;
		}
	}
}
