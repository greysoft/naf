/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;

public final class ResolverService
	implements com.grey.naf.nafman.Command.Handler,
		com.grey.naf.reactor.Timer.Handler
{
	//NAFMAN attributes
	private static final String MATTR_QTYP = "qt";
	private static final String MATTR_QVAL = "qv";
	private static final String MATTR_DUMPFILE = "df";
	private static final String MATTR_DUMPHTML = "dh";

	public final com.grey.naf.reactor.Dispatcher dsptch;
	final com.grey.logging.Logger logger;
	final Config config;
	final CacheManager cachemgr;
	final CommsManager xmtmgr;
	final java.util.Random rndgen = new java.util.Random(System.nanoTime());
	private final java.io.File fh_dump;

	// short-lived caches tracking currently ongoing requests
	private final com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_a
								= new com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();
	private final com.grey.base.collections.HashedMapIntKey<QueryHandle> pendingdoms_ptr
								= new com.grey.base.collections.HashedMapIntKey<QueryHandle>();
	private final com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_ns
								= new com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();
	private final com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_mx
								= new com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();
	private final com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_soa
								= new com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();
	private final com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_srv
								= new com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();
	private final com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_txt
								= new com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();
	private final com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pendingdoms_aaaa
								= new com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle>();

	//activereqs tracks all requests, while pendingreqs tracks UDP ones only and maps them to their QID
	private final com.grey.base.collections.HashedSet<QueryHandle> activereqs
								= new com.grey.base.collections.HashedSet<QueryHandle>();
	private final com.grey.base.collections.HashedMapIntKey<QueryHandle> pendingreqs
								= new com.grey.base.collections.HashedMapIntKey<QueryHandle>();

	// protects against QID wrap-around - only applies to UDP
	private final com.grey.base.collections.HashedSet<QueryHandle> wrapblocked
								= new com.grey.base.collections.HashedSet<QueryHandle>();
	private com.grey.naf.reactor.Timer tmr_wrapblocked;

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
	private final com.grey.base.collections.ObjectWell<QueryHandle> qrystore;
	private final com.grey.base.collections.ObjectWell<QueryHandle.WrapperRR> rrwstore;
	private final com.grey.base.collections.ObjectWell<com.grey.base.utils.ByteChars> bcstore;

	// We can't use dnsAnswer indiscriminately, as it would be corruped by callback chains between nested QueryHandles, so
	// it is purely for the use of top-level callers to whom we have to synchronously return an Answer block. Internal
	// DNS-Resolver code must allocate temp intances from anstore;
	final com.grey.base.collections.ObjectWell<Answer> anstore;
	private final Answer dnsAnswer = new Answer();

	// these are just temporary work areas, pre-allocated for efficiency
	private final com.grey.base.utils.ByteChars tmpbc_nafman = new com.grey.base.utils.ByteChars();
	private final com.grey.base.utils.ByteChars tmplightbc = new com.grey.base.utils.ByteChars(-1); //lightweight object without own storage
	final StringBuilder sbtmp = new StringBuilder();
	private final Packet pkt_tmp;

	boolean isActive(QueryHandle qh) {return activereqs.contains(qh);}
	QueryHandle.WrapperRR allocWrapperRR(ResourceData rr) {return rrwstore.extract().set(rr);}
	void freeWrapperRR(QueryHandle.WrapperRR rrw) {rrwstore.store(rrw.clear());}
	com.grey.base.utils.ByteChars allocByteChars() {return bcstore.extract().clear();}
	void freeByteChars(com.grey.base.utils.ByteChars bc) {bcstore.store(bc);}
	@Override
	public CharSequence nafmanHandlerID() {return "Resolver";}
	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex) {}

	public ResolverService(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg)
		throws com.grey.base.ConfigException, java.io.IOException, javax.naming.NamingException
	{
		dsptch = d;
		logger = dsptch.logger;
		config = new Config(cfg, logger);
		cachemgr = new CacheManager(dsptch, config);
		xmtmgr = new CommsManager(this);

		bcstore = new com.grey.base.collections.ObjectWell<com.grey.base.utils.ByteChars>(com.grey.base.utils.ByteChars.class, "DNS_"+dsptch.name);
		anstore = new com.grey.base.collections.ObjectWell<Answer>(Answer.class, "DNS_"+dsptch.name);
		rrwstore = new com.grey.base.collections.ObjectWell<QueryHandle.WrapperRR>(QueryHandle.WrapperRR.class, "DNS_"+dsptch.name);
		QueryHandle.Factory qryfact = new QueryHandle.Factory(this);
		qrystore = new com.grey.base.collections.ObjectWell<QueryHandle>(qryfact, "DNS_"+dsptch.name);
		pkt_tmp = new Packet(Math.max(Config.PKTSIZ_TCP, Config.PKTSIZ_UDP), Config.DIRECTNIOBUFS, config.minttl_initial);

		fh_dump = new java.io.File(dsptch.nafcfg.path_var+"/DNSdump-"+dsptch.name+".txt");
		FileOps.ensureDirExists(fh_dump.getParentFile()); //flush out any permissions issues right away

		com.grey.naf.nafman.Registry reg = com.grey.naf.nafman.Registry.get();
		reg.registerHandler(com.grey.naf.nafman.Registry.CMD_DNSDUMP, 0, this, dsptch);
		reg.registerHandler(com.grey.naf.nafman.Registry.CMD_DNSPRUNE, 0, this, dsptch);
		reg.registerHandler(com.grey.naf.nafman.Registry.CMD_DNSQUERY, 0, this, dsptch);
		if (!config.recursive) reg.registerHandler(com.grey.naf.nafman.Registry.CMD_DNSLOADROOTS, 0, this, dsptch);
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
		logger.info("DNS-Resolver received shutdown request - active="+activereqs.size()+"/pending="+pendingreqs.size()
				+"/wrapped="+wrapblocked.size());
		if (config.dump_on_exit) {
			cachemgr.prune(null);
			logger.info("Dumping final cache to "+fh_dump.getAbsolutePath());
			dumpState(fh_dump, "Dumping cache on exit");
		}
		xmtmgr.stop();
		if (tmr_wrapblocked != null) tmr_wrapblocked.cancel();
		tmr_wrapblocked = null;

		java.util.ArrayList<QueryHandle> reqs = new java.util.ArrayList<QueryHandle>(activereqs);
		for (int idx = 0; idx != reqs.size(); idx++) {
			int callers = reqs.get(idx).cancelExternalCallers(Answer.STATUS.SHUTDOWN);
			if (callers == 0) requestCompleted(reqs.get(idx));
		}
	}

	// This is expected to be a relatively rare event, and the number of pending requests is never expected to be very large,
	// so make do with a simple iteration.
	// We only cancel the caller (as in all notifications due to them), not the DNS requests themselves. Since we've already
	// issued those, we might as well cache the results when they arrive.
	public int cancel(Resolver.Client caller)
	{
		int reqs = 0;
		java.util.Iterator<QueryHandle> it = activereqs.recycledIterator();
		while (it.hasNext()) {
			QueryHandle qh = it.next();
			reqs += qh.removeCaller(caller);
		}
		return reqs;
	}

	public Answer resolve(byte qtype, com.grey.base.utils.ByteChars qname, Resolver.Client caller, Object callerparam, int flags)
	{
		return resolve(qtype, qname, caller, callerparam, flags, 0, dnsAnswer);
	}

	public Answer resolve(byte qtype, int qip, Resolver.Client caller, Object callerparam, int flags)
	{
		return resolve(qtype, qip, caller, callerparam, flags, dnsAnswer);
	}

	Answer resolve(byte qtype, com.grey.base.utils.ByteChars qname, Resolver.Client caller, Object callerparam,
			int flags, int server_ip, Answer answerbuf)
	{
		// check if answer is cached first
		if (caller != null && caller.getClass() != QueryHandle.class) stats_ureqs++;
		stats_reqcnt[qtype]++;
		qname.toLowerCase();
		Answer answer = lookupCache(qtype, qname, answerbuf);
		if (answer != null) return answer; //answer was already in cache
		if (caller != null && caller.getClass() != QueryHandle.class) stats_umiss++;
		stats_cachemiss[qtype]++;

		if ((flags & com.grey.naf.dns.Resolver.FLAG_NOQRY) != 0) {
			// caller doesn't want to go any further if not cached
			return answerbuf.set(Answer.STATUS.NODOMAIN, qtype, qname);
		}

		// A DNS request is required to satisfy this call, and the caller's dnsResolved() method will be called back later.
		// If we encounter an immediate error though, we return that to the user now as our final answer.
		// If a DNS request for this domain is already underway, then rather than duplicate that, we simply add this caller
		// to the list of those waiting on the request.
		QueryHandle qryh = null;
		if (server_ip == 0) {
			if (qtype == Resolver.QTYPE_A) {
				qryh = pendingdoms_a.get(qname);
			} else if (qtype == Resolver.QTYPE_NS) {
				qryh = pendingdoms_ns.get(qname);
			} else if (qtype == Resolver.QTYPE_MX) {
				qryh = pendingdoms_mx.get(qname);
			} else if (qtype == Resolver.QTYPE_SOA) {
				qryh = pendingdoms_soa.get(qname);
			} else if (qtype == Resolver.QTYPE_SRV) {
				qryh = pendingdoms_srv.get(qname);
			} else if (qtype == Resolver.QTYPE_TXT) {
				qryh = pendingdoms_txt.get(qname);
			} else if (qtype == Resolver.QTYPE_AAAA) {
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

			if (qtype == Resolver.QTYPE_NS) {
				pendingdoms_ns.put(qryh.qname, qryh);
			} else if (qtype == Resolver.QTYPE_MX) {
				pendingdoms_mx.put(qryh.qname, qryh);
			} else if (qtype == Resolver.QTYPE_SOA) {
				pendingdoms_soa.put(qryh.qname, qryh);
			} else if (qtype == Resolver.QTYPE_SRV) {
				pendingdoms_srv.put(qryh.qname, qryh);
			} else if (qtype == Resolver.QTYPE_TXT) {
				pendingdoms_txt.put(qryh.qname, qryh);
			} else if (qtype == Resolver.QTYPE_AAAA) {
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
					if (logger.isActive(LEVEL.TRC2)) logger.log(LEVEL.TRC2, "DNS-Resolver: Request="+Resolver.getQTYPE(qtype)+"/"+qname
							+" has deadlock with caller="+Resolver.getQTYPE(qhcaller.qtype)+"/"+qhcaller.qname);
					if (newqry) requestCompleted(qryh);
					return answerbuf.set(Answer.STATUS.DEADLOCK, qtype, qname);
				}
			}
			// NB: There must be no steps in the synchronous resolve() call chain which can fail after this, as that
			// means the caller would receive a failure callback as well as the synchronous error return code.
			qryh.addCaller(caller, callerparam);
		}
		// the result is not yet available, so the caller will be notified later via callback.
		return null;
	}

	// Same logic as above, but for an IP-based request.
	// This is currently used for PTR requests only, and they never generate sub-queries or get issued as sub-queries.
	Answer resolve(byte qtype, int qip, Resolver.Client caller, Object callerparam, int flags, Answer answerbuf)
	{
		if (caller != null && caller.getClass() != QueryHandle.class) stats_ureqs++;
		stats_reqcnt[qtype]++;
		Answer answer = lookupCache(qtype, qip, answerbuf);
		if (answer != null) return answer;
		if (caller != null && caller.getClass() != QueryHandle.class) stats_umiss++;
		stats_cachemiss[qtype]++;

		if ((flags & com.grey.naf.dns.Resolver.FLAG_NOQRY) != 0) {
			return answerbuf.set(Answer.STATUS.NODOMAIN, qtype, qip);
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

	private Answer issueQuery(QueryHandle qryh, int server_ip, Answer answerbuf)
	{
		java.net.InetSocketAddress nsaddr = null;
		boolean sticky_ip = (server_ip != 0);

		if (config.recursive) {
			nsaddr = xmtmgr.nextServer();
		} else {
			if (server_ip != 0) {
				nsaddr = cachemgr.createServerTSAP(server_ip);
			} else {
				nsaddr = getNameserver(qryh.qtype, qryh.qname);
				if (nsaddr == null) {
					//the nameserver for the target domain is not cached - issue a nested sub-query for it
					com.grey.base.utils.ByteChars dom = getParentDomain(qryh.qtype, qryh.qname);
					Answer answer = qryh.issueSubQuery(Resolver.QTYPE_NS, dom, 0, answerbuf);
					if (answer == null) return null; //will resume once we know the nameserver
					if (answer.result != Answer.STATUS.OK) return answerbuf.set(answer.result, qryh.qtype, qryh.qname);
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
					return answerbuf.set(Answer.STATUS.NODOMAIN, qryh.qtype, qryh.qname);
				}
			}
		}

		if (!qryh.isTCP()) {
			if (config.always_tcp) {
				qryh.qid = 1; //we only issue one query on a TCP connection
				Answer.STATUS result = qryh.switchTCP(nsaddr);
				if (result == Answer.STATUS.OK) return null;
				return answerbuf.set(result, qryh.qtype, qryh.qname);
			}
			if (qryh.qid == 0) {
				//we use zero to indicate an unallocated QID
				qryh.qid = (next_qryid++) & 0xFFFF;
				if (qryh.qid == 0) qryh.qid = next_qryid++;

				if (pendingreqs.containsKey(qryh.qid)) {
					//we have issued so many requests so quickly that the QID has wrapped around before they're answered
					wrapblocked.add(qryh);
					if (tmr_wrapblocked == null) tmr_wrapblocked = dsptch.setTimer(config.wrapretryfreq, 0, this);
					return null; //will resume later, when QID is no longer contended
				}
				pendingreqs.put(qryh.qid, qryh);
			}
		}
		Answer.STATUS result = qryh.issueQuery(nsaddr, sticky_ip, pkt_tmp);
		if (result == Answer.STATUS.OK) return null;
		return answerbuf.set(result, qryh.qtype, qryh.qname);
	}

	Answer repeatQuery(QueryHandle qryh, int server_ip)
	{
		Answer answerbuf = anstore.extract();
		try {
			Answer answer;
			if (qryh.qip != 0) {
				answer = lookupCache(qryh.qtype, qryh.qip, answerbuf);
			} else {
				answer = lookupCache(qryh.qtype, qryh.qname, answerbuf);
			}
			if (answer == null) answer = issueQuery(qryh, server_ip, answerbuf);
			if (answer != null) answer = qryh.endRequest(answer);
			return answer;
		} finally {
			anstore.store(answerbuf);
		}
	}

	void handleResponse(com.grey.base.utils.ArrayRef<byte[]> rcvdata, QueryHandle qryh, java.net.InetSocketAddress srvaddr)
	{
		boolean validresponse = false;
		boolean mapped_qryh = false;
		boolean is_tcp = (qryh != null);

		Packet pkt = pkt_tmp;
		pkt.resetDecoder(rcvdata.ar_buf, rcvdata.ar_off, rcvdata.ar_len);
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
			if (logger.isActive(Config.DEBUGLVL)) {
				String msg = (mapped_qryh ? "received rejection " : "discarding invalid ");
				logger.log(Config.DEBUGLVL, "DNS-Resolver "+msg+(is_tcp?"TCP":"UDP")+" response="+pkt.hdr_qid
						+(qryh==null? "" : "/"+Resolver.getQTYPE(qryh.qtype)+"/"+qryh.qname)
						+" from "+srvaddr+" - ans="+pkt.hdr_anscnt+"/"+pkt.hdr_authcnt+"/"+pkt.hdr_infocnt
						+"/auth="+pkt.isAuth()+"/trunc="+pkt.isTruncated()
						+" - size="+rcvdata.ar_len+", mapped="+mapped_qryh+", rcode="+pkt.rcode());
			}
			if (mapped_qryh) {
				if (pkt.rcode() == Packet.RCODE_NXDOM) {
					qryh.endRequest(Answer.STATUS.NODOMAIN);
				} else {
					qryh.endRequest(Answer.STATUS.BADRESPONSE);
				}
			}
			return;
		}
		if (qryh.haveResponse()) return;
		qryh.handleResponse(pkt, off, srvaddr, rcvdata.ar_len);
	}

	void requestCompleted(QueryHandle qryh)
	{
		activereqs.remove(qryh);
		if (qryh.qtype == Resolver.QTYPE_PTR) {
			pendingdoms_ptr.remove(qryh.qip);
		} else if (qryh.qtype == Resolver.QTYPE_NS) {
			pendingdoms_ns.remove(qryh.qname);
		} else if (qryh.qtype == Resolver.QTYPE_MX) {
			pendingdoms_mx.remove(qryh.qname);
		} else if (qryh.qtype == Resolver.QTYPE_SOA) {
			pendingdoms_soa.remove(qryh.qname);
		} else if (qryh.qtype == Resolver.QTYPE_SRV) {
			pendingdoms_srv.remove(qryh.qname);
		} else if (qryh.qtype == Resolver.QTYPE_TXT) {
			pendingdoms_txt.remove(qryh.qname);
		} else if (qryh.qtype == Resolver.QTYPE_AAAA) {
			pendingdoms_aaaa.remove(qryh.qname);
		} else {
			pendingdoms_a.remove(qryh.qname);
		}
		if (pendingreqs.get(qryh.qid) == qryh) pendingreqs.remove(qryh.qid);
		if (qryh.getSubQueryCount() != 0) cancel(qryh);
		qrystore.store(qryh.clear());
	}

	private Answer lookupCache(byte qtype, com.grey.base.utils.ByteChars qname, Answer answerbuf)
	{
		java.util.ArrayList<ResourceData> rrlist = null;
		ResourceData rr = null;

		if (qtype == Resolver.QTYPE_NS || qtype == Resolver.QTYPE_MX || qtype == Resolver.QTYPE_SRV) {
			rrlist = cachemgr.lookupList(qtype, qname);
			if (rrlist != null) rr = rrlist.get(0);
		} else {
			rr = cachemgr.lookup(qtype, qname);
		}
		if (rr == null) return null;
		Answer.STATUS sts = (rr.isNegative() ? Answer.STATUS.NODOMAIN : Answer.STATUS.OK);
		if (qname == tmplightbc) qname = new com.grey.base.utils.ByteChars(qname); //make it permanent
		answerbuf.set(sts, qtype, qname);
		if (sts == Answer.STATUS.OK) {
			if (rrlist != null) {
				for (int idx = 0; idx != rrlist.size(); idx++) {
					answerbuf.rrdata.add(rrlist.get(idx));
				}
			} else {
				answerbuf.rrdata.add(rr);
			}
		}
		return answerbuf;
	}

	private Answer lookupCache(byte qtype, int qip, Answer answerbuf)
	{
		ResourceData rr = cachemgr.lookup(qtype, qip);
		if (rr == null) return null;
		Answer.STATUS sts = (rr.isNegative() ? Answer.STATUS.NODOMAIN : Answer.STATUS.OK);
		answerbuf.set(sts, qtype, qip);
		if (sts == Answer.STATUS.OK) answerbuf.rrdata.add(rr);
		return answerbuf;
	}

	// Throwing here results in a hanging request, but since rewinding past the root domain must be a bug, that's
	// probably a better outcome than returning Answer=ERROR/NODOM
	java.net.InetSocketAddress getNameserver(byte qtype, com.grey.base.utils.ByteChars qname)
	{
		java.net.InetSocketAddress nsaddr = null;
		com.grey.base.utils.ByteChars dom = getParentDomain(qtype, qname);
		if (dom.length() == 0) throw new IllegalStateException("DNS-Resolver: Root servers are missing!!");
		do {
			nsaddr = cachemgr.lookupNameServer(dom);
			if (nsaddr == null || nsaddr.getPort() != 0) break;
			int dotcnt = StringOps.count(dom, Resolver.DOMDLM);
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

	com.grey.base.utils.ByteChars getParentDomain(byte qtype, com.grey.base.utils.ByteChars qname)
	{
		if (qtype == Resolver.QTYPE_SOA || qtype == Resolver.QTYPE_MX || qtype == Resolver.QTYPE_TXT) {
			return qname; //target is already the parent domain
		}
		int pos = qname.indexOf((byte)Resolver.DOMDLM);
		if (pos == -1) return Config.ROOTDOM_BC;
		tmplightbc.pointAt(qname.ar_buf, qname.ar_off+pos+1, qname.ar_len-pos-1);
		if (qtype == Resolver.QTYPE_SRV) return getParentDomain((byte)0, tmplightbc); //next level up is service-protocol, not parent domain
		return tmplightbc;
	}

	@Override
	public void timerIndication(com.grey.naf.reactor.Timer t, com.grey.naf.reactor.Dispatcher d)
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
			String txt = "DNS-Resolver: "+cnt1+" requests blocked by QID-wraparound";
			if (cnt1 != wrapblocked.size()) txt += " - reduced to "+wrapblocked.size();
			logger.log(lvl, txt);
		}
		if (wrapblocked.size() != 0) tmr_wrapblocked = dsptch.setTimer(config.wrapretryfreq, 0, this);
	}

	com.grey.base.utils.ByteChars buildArpaDomain(int ip)
	{
		sbtmp.setLength(0);
		IP.displayArpaDomain(ip, sbtmp);
		return allocByteChars().append(sbtmp);
	}

	@Override
	public CharSequence handleNAFManCommand(com.grey.naf.nafman.Command cmd)
	{
		//use temp StringBuilder, so that we don't hold onto a potentially huge block of memory
		StringBuilder sbrsp = new StringBuilder();

		if (cmd.def.code.equals(com.grey.naf.nafman.Registry.CMD_DNSDUMP)) {
			String dh = cmd.getArg(MATTR_DUMPHTML);
			String df = cmd.getArg(MATTR_DUMPFILE);
			if (!"N".equalsIgnoreCase(dh)) dumpState("<br/>", sbrsp);
			if (!"N".equalsIgnoreCase(df)) {
				dumpState(fh_dump, "Dumping cache on NAFMAN="+cmd.def.code);
				sbrsp.append("<br/><br/>Dumped cache to file "+fh_dump.getAbsolutePath());
			}
		} else if (cmd.def.code.equals(com.grey.naf.nafman.Registry.CMD_DNSPRUNE)) {
			cachemgr.prune(sbrsp);
		} else if (cmd.def.code.equals(com.grey.naf.nafman.Registry.CMD_DNSLOADROOTS)) {
			try {
				if (config.recursive) {
					sbrsp.append("DNS roots are not applicable, as Resolver is in recursive mode");
				} else {
					cachemgr.loadRootServers();
				}
			} catch (Exception ex) {
				sbrsp.append("Failed to reload roots - "+ex);
			}
		} else if (cmd.def.code.equals(com.grey.naf.nafman.Registry.CMD_DNSQUERY)) {
			String pqt = cmd.getArg(MATTR_QTYP);
			String pqv = cmd.getArg(MATTR_QVAL);
			if (pqv == null) return sbrsp.append("INVALID: Missing query attribute=").append(MATTR_QVAL);
			Answer ans = null;
			byte qt = 0;
			if ("A".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_A;
			} else if ("NS".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_NS;
			} else if ("MX".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_MX;
			} else if ("SOA".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_SOA;
			} else if ("SRV".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_SRV;
			} else if ("TXT".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_TXT;
			} else if ("AAAA".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_AAAA;
			} else if ("PTR".equalsIgnoreCase(pqt)) {
				qt = Resolver.QTYPE_PTR;
			} else {
				return sbrsp.append("INVALID: Unsupported query type - ").append(MATTR_QTYP).append('=').append(pqt);
			}
			if (qt == Resolver.QTYPE_PTR) {
				int ip = IP.convertDottedIP(pqv);
				if (!IP.validDottedIP(pqv, ip)) return sbrsp.append("INVALID: Not a valid dotted IP - ").append(pqv);
				ans = resolve(qt, ip, null, null, 0);
			} else {
				ans = resolve(qt, tmpbc_nafman.set(pqv), null, null, 0);
			}
			if (ans == null) {
				sbrsp.append("Answer not in cache - query has been issued");
			} else {
				sbrsp.append("Cached Answer: ");
				ans.toString(sbrsp);
			}
		} else {
			// we've obviously registered for this command, so we must be missing a clause - clearly a bug
			logger.error("DNS-Resolver NAFMAN: Missing case for cmd="+cmd.def.code);
			return null;
		}
		return sbrsp;
	}

	private void dumpState(java.io.File fh, CharSequence msg)
	{
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
			logger.log(LEVEL.ERR, ex, false, "DNS-Resolver failed to create dumpfile="+fh.getAbsolutePath()+" - strm="+fout);
		} finally {
			try {
				if (ostrm != null) ostrm.close();
				if (fout != null) fout.close();
			} catch (Exception ex) {
				logger.log(LEVEL.ERR, ex, false, "DNS-Resolver failed to close dumpfile="+fh.getAbsolutePath());
			}
		}
	}

	private StringBuilder dumpState(String eol, StringBuilder sb)
	{
		int reqcnt = 0;
		int cachemiss = 0;
		for (int idx = 0; idx != stats_reqcnt.length; idx++) {
			reqcnt += stats_reqcnt[idx];
			cachemiss += stats_cachemiss[idx];
		}
		if (sb == null) sb = new StringBuilder();
		sb.append("DNS-Resolver status as of ").append(new java.util.Date(dsptch.getSystemTime()));
		sb.append(eol).append("Requests=").append(reqcnt).append(" (user=").append(stats_ureqs).append("):");
		sb.append(" A=").append(stats_reqcnt[Resolver.QTYPE_A]);
		sb.append(", AAAA=").append(stats_reqcnt[Resolver.QTYPE_AAAA]);
		sb.append(", PTR=").append(stats_reqcnt[Resolver.QTYPE_PTR]);
		sb.append(", SOA=").append(stats_reqcnt[Resolver.QTYPE_SOA]);
		sb.append(", NS=").append(stats_reqcnt[Resolver.QTYPE_NS]);
		sb.append(", MX=").append(stats_reqcnt[Resolver.QTYPE_MX]);
		sb.append(", SRV=").append(stats_reqcnt[Resolver.QTYPE_SRV]);
		sb.append(", TXT=").append(stats_reqcnt[Resolver.QTYPE_TXT]);
		sb.append(eol).append("Cache misses=").append(cachemiss).append(" (user=").append(stats_umiss).append("):");
		sb.append(" A=").append(stats_cachemiss[Resolver.QTYPE_A]);
		sb.append(", AAAA=").append(stats_cachemiss[Resolver.QTYPE_AAAA]);
		sb.append(", PTR=").append(stats_cachemiss[Resolver.QTYPE_PTR]);
		sb.append(", SOA=").append(stats_cachemiss[Resolver.QTYPE_SOA]);
		sb.append(", NS=").append(stats_cachemiss[Resolver.QTYPE_NS]);
		sb.append(", MX=").append(stats_cachemiss[Resolver.QTYPE_MX]);
		sb.append(", SRV=").append(stats_cachemiss[Resolver.QTYPE_SRV]);
		sb.append(", TXT=").append(stats_cachemiss[Resolver.QTYPE_TXT]);
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
		com.grey.base.collections.IteratorInt it = pendingdoms_ptr.keysIterator();
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

	private static void dumpPending(com.grey.base.collections.HashedMap<com.grey.base.utils.ByteChars, QueryHandle> pending,
			String qtype, String dlm1, String dlm2, String eol, StringBuilder sb)
	{
		sb.append(eol).append(qtype).append('=').append(pending.size());
		java.util.Iterator<com.grey.base.utils.ByteChars> it = pending.keysIterator();
		String dlm = dlm1;
		while (it.hasNext()) {
			com.grey.base.utils.ByteChars bc = it.next();
			sb.append(dlm).append(bc);
			dlm = dlm2;
		}
	}
}
