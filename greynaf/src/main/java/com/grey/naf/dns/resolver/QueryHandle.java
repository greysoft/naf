/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.IP;
import com.grey.naf.reactor.TimerNAF;
import com.grey.naf.reactor.CM_TCP;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.errors.NAFException;
import com.grey.logging.Logger;

class QueryHandle
	implements ResolverDNS.Client, PacketDNS.MessageCallback, TimerNAF.Handler
{
	public static final class WrapperRR
	{
		ResourceData rr;
		boolean qrysent;
		boolean deadlocked;

		WrapperRR set(ResourceData rr) {
			this.rr = rr;
			return this;
		}

		WrapperRR clear() {
			rr = null;
			qrysent = false;
			deadlocked = false;
			return this;
		}

		@Override
		public String toString() {return rr.toString();}
	}

	public static final class Factory
		implements com.grey.base.collections.ObjectWell.ObjectFactory
	{
		private final ResolverService rslvr;

		public Factory(ResolverService r) {
			rslvr = r;
		}

		@Override
		public QueryHandle factory_create() {
			return new QueryHandle(rslvr);
		}
	}

	private static final int F_TCP = 1 << 0; //can't rely on non-null tcpconn, as it gets nulled before handleReponse()
	private static final int F_IPSTICKY = 1 << 1;
	private static final int F_HAVERSP = 1 << 2;
	private static final int F_NOTNXDOM = 1 << 3;
	private static final int F_PARTIALNOTIFIED = 1 << 4;
	private static final int F_AUTHREDIRECT = 1 << 5; //the Auth-Redirect hack is in effect

	private static final int TMR_TIMEOUT = 1;
	private static final int TMR_DEADLOCK = 2;

	private static final ByteChars ARPADOM0 = new ByteChars(IP.displayArpaDomain(0));

	public final ResolverService rslvr;
	private final java.util.ArrayList<WrapperRR> rrdata = new java.util.ArrayList<>(); //answer RRs
	private final java.util.ArrayList<ResolverDNS.Client> callers = new java.util.ArrayList<>();
	private final java.util.ArrayList<Object> callerparams = new java.util.ArrayList<>();

	public int qid; //ID of current request packet, not this request as a whole
	public byte qtype; //type of request
	public ByteChars qname; //overall question for this request
	public int qip; //for PTR queries, this is the raw IP value behind qname (which will be an in-addr.arpa name)

	private EndPointTCP tcpconn;
	private int ip_request;
	private int unresolved_cnt; //number of answer RRs on rrdata list whose IP needs to be resolved
	private int subqry_open; //number of currently outstanding sub-queries
	private byte flags;

	// these fields support the retry functionality
	private TimerNAF tmr;
	private int retrycnt;

	public boolean isTCP() {return isFlagSet(F_TCP);}
	public boolean haveResponse() {return isFlagSet(F_HAVERSP);}
	public int getSubQueryCount() {return subqry_open;}
	private int getResolvedCount() {return rrdata.size() - unresolved_cnt;}

	private boolean isFlagSet(int f) {return ((flags & f) != 0);}
	private final void clearFlag(int f) {flags &= ~f;}
	private void setFlag(int f) {flags |= f;}
	private void setFlag(int f, boolean b) {if(b) setFlag(f); else clearFlag(f);}

	@Override
	public long getSystemTime() {return rslvr.getDispatcher().getSystemTime();}

	QueryHandle(ResolverService r)
	{
		rslvr = r;
	}

	//qname must be permanent storage, as it will be used to populate RRs, act as CacheManager keys and to set Answer.qname
	public QueryHandle init(byte qt, ByteChars qn)
	{
		qname = new ByteChars(qn);
		if (qname.size() != 0 && qname.charAt(qname.size() - 1) == ResolverDNS.DOMDLM) qname.incrementSize(-1);
		qip = 0;
		return init(qt);
	}

	// Unlike the other request types, this incarnation's qname will not be passed to a long-term cache, and does not need
	// to outlive this request, so we can benefit from object conservation and reuse.
	// There's no good reason why the 'ip' arg should be zero, but we must handle it differently if so because clear() uses
	// non-zero ip as the cue to free qname.
	public QueryHandle init(byte qt, int ip)
	{
		qip = ip;
		qname = (qip == 0 ? ARPADOM0 : rslvr.buildArpaDomain(qip));
		return init(qt);
	}

	private QueryHandle init(byte qt)
	{
		qtype = qt;
		qid = 0;
		retrycnt = 0;
		unresolved_cnt = 0;
		subqry_open = 0;
		ip_request = 0;
		flags = 0;
		return this;
	}

	public QueryHandle clear()
	{
		if (tcpconn != null) disconnectTCP();
		stopTimer();

		if (qname != null) {
			if (qip != 0) rslvr.freeByteChars(qname);
			qname = null;
		}
		clearAnswer();
		callers.clear();
		callerparams.clear();
		return this;
	}

	public ResolverAnswer.STATUS issueQuery(java.net.InetSocketAddress srvaddr, boolean sticky_ip, PacketDNS pkt)
	{
		pkt.resetEncoder(isTCP(), false); //we only ever encode 1 question, so avoid compression overhead
		pkt.hdr_qid = qid;
		pkt.hdr_qcnt = 1;
		if (rslvr.getConfig().isRecursive()) pkt.setRecursionDesired();
		int off = pkt.encodeHeader();
		off = pkt.encodeQuestion(off, qtype, qname);
		java.nio.ByteBuffer niobuf = pkt.completeEncoding(off);
		int pktlen = niobuf.limit();
		if (rslvr.logger.isActive(ResolverConfig.DEBUGLVL)) {
			rslvr.logger.log(ResolverConfig.DEBUGLVL, "DNS-Resolver sending "+(isTCP()?"TCP":"UDP")
					+" query="+qid+"/"+ResolverDNS.getQTYPE(qtype)+"/"+qname+" to "+srvaddr
					+" - size="+pktlen);
		}
		ResolverAnswer.STATUS result = send(niobuf, srvaddr);
		if (result == ResolverAnswer.STATUS.OK) {
			ip_request = IP.convertIP(srvaddr.getAddress());
			setFlag(F_IPSTICKY, sticky_ip);
			startTimer(TMR_TIMEOUT);
		}
		return result;
	}

	public ResolverAnswer.STATUS endRequest(ResolverAnswer.STATUS result)
	{
		if (result == ResolverAnswer.STATUS.OK && rrdata.size() == 0 && isFlagSet(F_NOTNXDOM)) result = ResolverAnswer.STATUS.ERROR;
		ResolverAnswer answer = setAnswer(result, null);

		if (result == ResolverAnswer.STATUS.OK || result == ResolverAnswer.STATUS.NODOMAIN) {
			for (int idx = 0; idx != rrdata.size(); idx++) {
				answer.rrdata.add(rrdata.get(idx).rr);
			}
			result = rslvr.getCacheManager().storeResult(answer);
		}
		answer.ip_responder = ip_request;
		notifyCallers(answer); //must be the last thing we do before requestCompleted(), in case of re-entrancy
		rslvr.freeAnswerBuf(answer);
		rslvr.requestCompleted(this);
		return result;
	}

	public ResolverAnswer endRequest(ResolverAnswer answer)
	{
		clearAnswer();
		for (int idx = 0; idx != answer.rrdata.size(); idx++) {
			rrdata.add(rslvr.allocWrapperRR(answer.rrdata.get(idx)));
		}
		answer.result = endRequest(answer.result);
		return answer;
	}

	public ResolverAnswer repeatQuery(int server_ip)
	{
		clearFlag(F_HAVERSP);
		return rslvr.repeatQuery(this, server_ip);
	}

	private ResolverAnswer.STATUS send(java.nio.ByteBuffer niobuf, java.net.InetSocketAddress srvaddr)
	{
		try {
			if (tcpconn != null) {
				tcpconn.send(niobuf);
			} else {
				EndPointUDP sender = rslvr.getCommsManager().nextSendPort();
				sender.send(niobuf, srvaddr);
			}
		} catch (Throwable ex) {
			Logger.LEVEL lvl = (NAFException.isError(ex) ? Logger.LEVEL.ERR : Logger.LEVEL.TRC);
			if (rslvr.logger.isActive(lvl)) {
				rslvr.logger.log(lvl, ex, lvl == Logger.LEVEL.ERR,
						"DNS-Resolver send failed on "+(isTCP()?"TCP":"UDP")+" for "+ResolverDNS.getQTYPE(qtype)+"/"+qname
						+" - server="+(tcpconn!=null?tcpconn.getChannel():srvaddr));
			}
			return ResolverAnswer.STATUS.ERROR;
		}
		return ResolverAnswer.STATUS.OK;
	}

	public ResolverAnswer.STATUS switchTCP(java.net.InetSocketAddress nsaddr)
	{
		setFlag(F_TCP);
		tcpconn = rslvr.getCommsManager().allocateTCP();
		try {
			tcpconn.connect(this, nsaddr); //TCP should time itself out long before retrytmt_tcp expires
		} catch (Throwable ex) {
			Logger.LEVEL lvl = (NAFException.isError(ex) ? Logger.LEVEL.ERR : CM_TCP.LOGLEVEL_CNX);
			if (rslvr.logger.isActive(lvl)) {
				rslvr.logger.log(lvl, ex, lvl==Logger.LEVEL.ERR, "DNS-Resolver connect failed to "+nsaddr+" for "+ResolverDNS.getQTYPE(qtype)+"/"+qname);
			}
			return endRequest(ResolverAnswer.STATUS.TIMEOUT); //treat in same way as unsuccessful connected()
		}
		return ResolverAnswer.STATUS.OK;
	}

	private void disconnectTCP()
	{
		tcpconn.disconnect();
		tcpconn = null;
	}

	// Servers are supposed to hold the connection open to allow for multiple requests, but we'll play it safe and close it
	// after receiving one response, and then open a new one if needed for any follow-on queries.
	// As it happens, it seems TCP responses contain all the info required since they have no size constraints, so it seems
	// follow-on queries are never needed.
	public void handleResponseTCP(ByteArrayRef rcvdata)
	{
		java.net.InetSocketAddress srvaddr = tcpconn.getRemoteAddress();
		disconnectTCP();
		rslvr.handleResponse(rcvdata, this, srvaddr);
	}

	public void handleResponse(PacketDNS pkt, int off, java.net.InetSocketAddress srvaddr, int msglen)
	{
		// our response has been received - if any sub-queries take too long, well they have their own timers
		if (rslvr.logger.isActive(ResolverConfig.DEBUGLVL)) {
			rslvr.logger.log(ResolverConfig.DEBUGLVL, "DNS-Resolver received "+(isTCP()?"TCP":"UDP")+" response="+pkt.hdr_qid+"/"+ResolverDNS.getQTYPE(qtype)+"/"+qname
					+" from "+srvaddr+" - ans="+pkt.hdr_anscnt+"/"+pkt.hdr_authcnt+"/"+pkt.hdr_infocnt
					+"/auth="+pkt.isAuth()+"/trunc="+pkt.isTruncated()
					+" - size="+msglen+", QH-state=0x"+Integer.toHexString(flags)+"/"+subqry_open+"/"+unresolved_cnt+"/"+rrdata.size()+"/"+rrdata);
		}
		setFlag(F_HAVERSP);
		stopTimer();

		if (pkt.isTruncated()) {
			// Response is truncated, so reissue request via TCP.
			// NB: We've already parsed the question, but that's ok because we always expect it to be present, even in a truncated packet.
			if (isTCP()) {
				// already in TCP mode, so truncation shouldn't happen
				endRequest(ResolverAnswer.STATUS.ERROR);
				return;
			}
			rslvr.stats_trunc++;
			ResolverAnswer.STATUS sts = switchTCP(srvaddr);
			if (sts != ResolverAnswer.STATUS.OK) endRequest(sts);
			return;
		}
		boolean authredirect_hack = false; //lets us know if F_AUTHREDIRECT is freshly set or an existing state
		boolean parsed_auth = false;
		boolean parse_auth = (qtype == ResolverDNS.QTYPE_NS && !rslvr.getConfig().isRecursive());

		if (!parse_auth && pkt.hdr_anscnt == 0 && pkt.hdr_authcnt != 0) {
			// We sent our request to what was supposed to be a valid nameserver for this domain, and it's responded
			// with what looks like a referral to an alternative set of nameservers.
			// I'm inclined to regard this as a misconfigured domain and return NODOMAIN (or even BADRESPONSE) but
			// BIND follows the referrals and repeats this query. I've no idea what the basis for this behaviour is
			// in the DNS specs, or if it's just an accepted practice for redirecting queries, but let's do the same.
			if (isFlagSet(F_AUTHREDIRECT)) {
				//The referrals have referred us again. This is definitely bad config, so abort to avoid looping.
				endRequest(ResolverAnswer.STATUS.BADRESPONSE);
				return;
			}
			setFlag(F_AUTHREDIRECT);
			authredirect_hack = true;
			parse_auth = true;
		}
		off = pkt.parseSection(off, qtype, PacketDNS.SECT_ANSWERS, pkt.hdr_anscnt, srvaddr, this);

		if (parse_auth && off != -1) {
			// NS RRs are returned in the Answer section by an authoritive response, else in the Authority section by a
			// referral response - or at least that seems to be the way it goes. We'll just parse both sections, though
			// I don't think they can be in both.
			off = pkt.parseSection(off, qtype, PacketDNS.SECT_AUTH, pkt.hdr_authcnt, srvaddr, this);
			parsed_auth = true;
		}
		if (unresolved_cnt != 0 && pkt.hdr_infocnt != 0 && off != -1) {
			// Additional-Info section potentially contains A RRs for unresolved rrdata elements
			if (!parsed_auth) off = pkt.skipSection(off, PacketDNS.SECT_AUTH, pkt.hdr_authcnt);
			if (off != -1) off = pkt.parseSection(off, qtype, PacketDNS.SECT_INFO, pkt.hdr_infocnt, srvaddr, this);
		}

		if (off == -1) {
			endRequest(ResolverAnswer.STATUS.BADRESPONSE);
			return;
		}

		if (authredirect_hack && rrdata.size() != 0) {
			//Resend request to one of the Auth-NS RRs if any of them have a known IP. Only need to resolve one RR.
			for (int idx = 0; idx != rrdata.size(); idx++) {
				int ip = rrdata.get(idx).rr.getIP();
				if (ip != 0) {
					clearAnswer();
					repeatQuery(ip);
					return;
				}
			}
			// All the NS RRs are unresolved, so discard all but one, which we'll resolve in the usual way.
			// Beware of broken domains redirecting a query to its own target, resulting in a loop.
			while (rrdata.size() > 1) discardUnresolvedRR(0);
			if (qtype == ResolverDNS.QTYPE_A && qname.equals(rrdata.get(0).rr.getName())) {
				endRequest(ResolverAnswer.STATUS.BADRESPONSE); //endless redirection loop
				return;
			}
		}
		evaluateResponse();
	}

	// Note that rsprr represents a temp object that will be reused after this call returns, so we need to take a copy
	// of its fields if we want to keep them longer term.
	// Any resulting cache entries will be long-lived, so it's not wasteful to create new RRs for them in here.
	@Override
	public boolean handleMessageRR(int pktqid, int sectiontype, int rrnum, int rrcnt, ByteChars rrname, ResourceData rsprr, java.net.InetSocketAddress srvaddr)
	{
		Logger.LEVEL trclvl = Logger.LEVEL.TRC3;
		if (rslvr.logger.isActive(trclvl)) {
			rslvr.logger.log(trclvl, "Loading RR="+PacketDNS.getSectionType(sectiontype)+"/"+rrnum+"/"+rrcnt+"="+rrname+" - "+rsprr
					+" - state=0x"+Integer.toHexString(flags)+"/"+subqry_open+"/"+unresolved_cnt+"/"+rrdata.size());
		}
		boolean completed = false; //true means we have all the info we need to answer this request

		switch (rsprr.rrType())
		{
		case ResolverDNS.QTYPE_A: {
			//It may be that the hostname for which we made a type-A query is actually a CNAME alias. We never check if
			//that is the case, but assume that the appropriate type-A RR gets included in the Answer section alongside
			//the CNAME and since we ignore CNAME RRs that's all we'll see here. The cached RR_A record will therefore be
			//named after the actual hostname, while the cache will be keyed on the originally requested CNAME alias.
			if (qtype == ResolverDNS.QTYPE_A && sectiontype == PacketDNS.SECT_ANSWERS) {
				// The answer to an original hostname query (type-A) - take the first A RR we get back as our final answer
				if (rrdata.size() == 0) {
					ResourceData rr = new ResourceData.RR_A((ResourceData.RR_A)rsprr, qname);
					rrdata.add(rslvr.allocWrapperRR(rr));
				}
				return true;
			}

			// Original request must be of type NS/MX/SRV, so this is either the Authority or Additional-Info part of
			// the original response, or the answer part of a follow-on type-A sub-query.
			if (!rslvr.getConfig().isRecursive() && sectiontype == PacketDNS.SECT_INFO) {
				if (rslvr.getConfig().isCacheAllGlue() || rsprr.getName().endsWith(qname)) {
					ResourceData rr = new ResourceData.RR_A((ResourceData.RR_A)rsprr, qname);
					rslvr.getCacheManager().storeHostAddress(rr.getName(), rr);
				}
			}
			// check if it resolves any of our answer RRs
			int pos_resolved = getUnresolvedRR(rsprr.getName());
			if (pos_resolved == -1) break; //discard unexpected response RR
			resolveIP(pos_resolved, rsprr);
			//check if any unresolved RRs remain, or we've reached our quorum
			completed = (unresolved_cnt == 0);
			if (!completed) completed = haveQuorumRRs();
		}
		break;

		case ResolverDNS.QTYPE_NS: {
			//ignore informational NS RRs that weren't the subject of our query
			if (qtype != ResolverDNS.QTYPE_NS && !isFlagSet(F_AUTHREDIRECT)) break;
			ResourceData rr = new ResourceData.RR_NS((ResourceData.RR_NS)rsprr, qname);
			completed = loadResolvableRR(rr, rrdata.size());
		}
		break;

		case ResolverDNS.QTYPE_MX: {
			// presume that this was also the query type and this is the Answers section of the DNS packet
			int pos = 0; //will insert new RR at head of list, if we don't find any lower preferences
			int pref = ((ResourceData.RR_MX)rsprr).pref;
			for (int idx = rrdata.size() - 1; idx != -1; idx--) {
				if (((ResourceData.RR_MX)rrdata.get(idx).rr).pref <= pref) {
					// insert new RR after this node
					pos = idx + 1;
					break;
				}
			}
			ResourceData rr = new ResourceData.RR_MX((ResourceData.RR_MX)rsprr, qname);
			completed = loadResolvableRR(rr, pos);
		}
		break;

		case ResolverDNS.QTYPE_SOA: {
			if (qtype == rsprr.rrType()) {
				// presume that this was also the query type - we only expect one answer, so take the first one we see
				if (rrdata.size() == 0) {
					ResourceData rr = new ResourceData.RR_SOA((ResourceData.RR_SOA)rsprr, qname);
					rrdata.add(rslvr.allocWrapperRR(rr));
				}
				return true;
			}
		}
		break;

		case ResolverDNS.QTYPE_SRV: {
			// presume that this was also the query type
			ResourceData rr = new ResourceData.RR_SRV((ResourceData.RR_SRV)rsprr, qname);
			completed = loadResolvableRR(rr, rrdata.size());
		}
		break;

		case ResolverDNS.QTYPE_TXT: {
			// presume that this was also the query type - we only expect one answer, so take the first one we see
			if (rrdata.size() == 0) {
				ResourceData rr = new ResourceData.RR_TXT((ResourceData.RR_TXT)rsprr, qname);
				rrdata.add(rslvr.allocWrapperRR(rr));
			}
		}
		return true;

		case ResolverDNS.QTYPE_AAAA: {
			// presume that this was also the query type - we only expect one answer, so take the first one we see
			if (rrdata.size() == 0) {
				ResourceData rr = new ResourceData.RR_AAAA((ResourceData.RR_AAAA)rsprr, qname);
				rrdata.add(rslvr.allocWrapperRR(rr));
			}
		}
		return true;

		case ResolverDNS.QTYPE_PTR: {
			// presume that this was also the query type - like QTYPE_A, we only take the first answer we see
			if (rrdata.size() == 0) {
				ResourceData rr = new ResourceData.RR_PTR((ResourceData.RR_PTR)rsprr);
				rr.setIP(qip);//because the RR parsed from the incoming packet still has no IP address
				rrdata.add(rslvr.allocWrapperRR(rr));
			}
		}
		return true;

		default: //ignore
			break;
		}
		return completed;
	}

	private boolean loadResolvableRR(ResourceData rsprr, int pos)
	{
		ByteChars domnam = getResolvableName(rsprr);
		int ip = IP.convertDottedIP(domnam);
		boolean have_ip = IP.validDottedIP(domnam, ip);
		ResourceData rr_a = null;
		if (!have_ip) {
			rr_a = rslvr.getCacheManager().lookup(ResolverDNS.QTYPE_A, domnam);
			if (rr_a != null && rr_a.isNegative()) return false; //discard this RR
			have_ip = (rr_a != null);
		}
		boolean completed = false;
		rrdata.add(pos, rslvr.allocWrapperRR(rsprr));
		if (have_ip) {
			if (rr_a != null) {
				resolveIP(rsprr, rr_a);
			} else {
				rsprr.setIP(ip);
			}
			completed = haveQuorumRRs();
		} else {
			unresolved_cnt++;
		}
		return completed;
	}

	// Whereas handleMessageRR() streams the response RRs into QueryHandle as they're parsed, this method is called after
	// the response packet has been fully parsed, and determines whether the response is now complete or if some
	// type-A sub-queries need to be issued to resolve some of the RRs we received.
	// It is called again every time a sub-query completes.
	//
	// On the use of WrapperRR.qrysent:
	// If a sub-query fails or times out we will get notified via dnsResolved() and we will accordingly discard the
	// unresolved RR for which it was issued.
	// But, if we get a sub-query response with odd or unexpected RRs which don't match any unresolved RRs, then we
	// could keep calling back from dnsResolved() to here and resend the same unresolved RRs in an infinite loop,
	// without unresolved_cnt ever decreasing.
	// Even a scheme where we capped the maximum sub-queries would be prone to sending a few queries too many, or
	// wasting our finite query quota on RRs that have already yielded inconclusive responses.
	// Setting qrysent gets around this, by marking the RRs which have been queried. Since we only call this method
	// once all outstanding sub-queries have completed, any RRs we find in here with qrysent set must be have generated
	// responses that could not be matched back to them, so we can discard them.
	//
	// NB: I'm not sure this method is ever actually invoked via the deadlock timer it sets, as the deadlock always
	// seems to get resolved by a notifyPartialAnswer() call in one of our subqueries.
	// Update: Yes, I have seen this happen (and deadlocked RRs were found in the A cache after the timeout).
	private boolean evaluateResponse(ResolverAnswer answerbuf)
	{
		if (unresolved_cnt == 0) return true;
		int resolved_cnt = getResolvedCount();
		int qname_ip = 0;

		// Issue sub-queries for all the remaining unresolved RRs
		int maxrr = getMaxRRs();
		int cap = (maxrr == 0 ? unresolved_cnt : maxrr - resolved_cnt);
		int send_cnt = Math.min(unresolved_cnt, cap);
		int sent_cnt = 0;
		int circular = 0;

		for (int idx = rrdata.size() - 1; idx != -1; idx--) {
			WrapperRR wrr = rrdata.get(idx);
			ResourceData rr = wrr.rr;
			if (rr.getIP() != 0) continue; //already resolved
			if (wrr.qrysent) {
				//already inconclusively queried
				discardUnresolvedRR(idx);
				continue;
			}
			int server_ip = 0;
			ByteChars domnam = getResolvableName(rr);

			if (!rslvr.getConfig().isRecursive() && qtype == ResolverDNS.QTYPE_NS) {
				if (qname_ip == 0) qname_ip = getPartialAnswer();
				ByteChars dom = rslvr.getParentDomain(ResolverDNS.QTYPE_A, domnam);
				if (qname.equals(dom)) {
					//a sub-query for this would be circular, unless we already know some of our nameservers
					circular++; //never tested if any queries sent, so ok to increment here
					if (qname_ip == 0) continue;
					server_ip = qname_ip;
				} else if (wrr.deadlocked) {
					ResourceData rr_a = rslvr.getCacheManager().lookup(ResolverDNS.QTYPE_A, domnam);
					if (rr_a != null) {
						//the associated type-A RR has been cached due to activity in other requests
						resolveIP(idx, rr_a);
						continue;
					}
					java.net.InetSocketAddress nsaddr = rslvr.getNameserver(ResolverDNS.QTYPE_A, domnam);
					if (nsaddr != null) {
						if (nsaddr.getPort() == 0) {
							discardUnresolvedRR(idx);
							continue;
						}
						server_ip = IP.convertIP(nsaddr.getAddress());
					}
					if (server_ip == 0) continue;
				}
			}
			ResolverAnswer answer = issueSubQuery(ResolverDNS.QTYPE_A, domnam, server_ip, answerbuf);

			if (answer == null) {
				wrr.qrysent = true;
			} else {
				if (answer.result == ResolverAnswer.STATUS.OK) {
					ResourceData rr_a = answer.rrdata.get(0);
					resolveIP(idx, rr_a);
				} else {
					if (answer.result == ResolverAnswer.STATUS.DEADLOCK) {
						wrr.deadlocked = true;
					} else {
						discardUnresolvedRR(idx);
					}
				}
			}
			if (++sent_cnt == send_cnt) break;
		}

		if (subqry_open == 0) {
			//all the subqueries we tried either returned an immediate answer or couldn't even be attempted
			if (sent_cnt != 0) return evaluateResponse(answerbuf); //the former, so do next batch (if any)
			if (circular == unresolved_cnt) { //and we'll never be able to resolve them
				discardUnresolvedRRs();
			} else {
				startTimer(TMR_DEADLOCK);
			}
		}
		return (unresolved_cnt == 0);
	}

	private void evaluateResponse()
	{
		ResolverAnswer buf = rslvr.allocAnswerBuf();
		boolean completed = false;
		try {
			completed = evaluateResponse(buf);
		} finally {
			rslvr.freeAnswerBuf(buf);
		}

		if (completed) {
			endRequest(ResolverAnswer.STATUS.OK);
		} else {
			if (qtype == ResolverDNS.QTYPE_NS && !isFlagSet(F_PARTIALNOTIFIED) && getResolvedCount() != 0) {
				setFlag(F_PARTIALNOTIFIED);
				notifyPartialAnswer();
			}
		}
	}

	public ResolverAnswer issueSubQuery(byte type, ByteChars name, int ip, ResolverAnswer answerbuf)
	{
		if (rslvr.logger.isActive(ResolverConfig.DEBUGLVL)) {
			rslvr.logger.log(ResolverConfig.DEBUGLVL, "DNS-Resolver issuing subquery="+ResolverDNS.getQTYPE(type)+"/"+name
					+" on behalf of query="+qid+"/"+ResolverDNS.getQTYPE(qtype)+"/"+qname
					+" - state=0x"+Integer.toHexString(flags)+"/"+subqry_open+"/"+unresolved_cnt+"/"+rrdata.size());
		}
		ResolverAnswer answer = rslvr.resolve(type, name, this, null, 0, ip, answerbuf);
		if (answer == null) {
			subqry_open++;
		} else {
			if (rslvr.logger.isActive(ResolverConfig.DEBUGLVL)) {
				rslvr.logger.log(ResolverConfig.DEBUGLVL, "DNS-Resolver has sync answer for "+qid+"/"+ResolverDNS.getQTYPE(qtype)+"/"+qname
						+" subquery - "+answer);
			}
		}
		return answer;
	}

	// This is the response to a "stacked", "chained" or "nested" sub-query, as they are variously termed.
	// We must not modify the input Answer block, as it describes the result of our nested query not the result
	// of this query, and the nested query may be notifying several callers in a loop.
	@Override
	public void dnsResolved(Dispatcher d, ResolverAnswer answer, Object callerparam)
	{
		if (rslvr.logger.isActive(ResolverConfig.DEBUGLVL)) {
			rslvr.logger.log(ResolverConfig.DEBUGLVL, "DNS-Resolver has answer for "+ResolverDNS.getQTYPE(qtype)+"/"+qname+" subquery - "
					+answer+" QH-state=0x"+Integer.toHexString(flags)+"/"+subqry_open+"/"+unresolved_cnt+"/"+rrdata.size()+"/"+rrdata);
		}
		subqry_open--;

		if (answer.qtype == ResolverDNS.QTYPE_NS) {
			//this is the response to an NS query we needed before we could proceed
			int server_ip = 0;
			if (answer.result != ResolverAnswer.STATUS.OK) {
				if (answer.result == ResolverAnswer.STATUS.NODOMAIN) {
					//This means no delegation exists at the point in the DNS hierarchy represented by answer.qname,
					//so carry on down the chain using the address of the nameserver which sent us this response, as
					//it is the last known delegation point along the chain.
					server_ip = answer.ip_responder;
				} else {
					//means we can't resolve our own qname, so fail the entire request
					endRequest(answer.result);
					return;
				}
			}
			//We've found the nameservers we wanted, so continue walking their domain. We say "repeat", but
			//this would actually be the first time this query would get transmitted, albeit it's not the
			//first call to ResolverService.issueQuery().
			//Going via repeatQuery() gives us a chance to check the cache first.
			repeatQuery(server_ip);
			return;
		}

		// must be the response to a follow-on type-A query to resolve NS/MX/SRV RRs
		if (answer.result == ResolverAnswer.STATUS.OK) {
			handleMessageRR(qid, PacketDNS.SECT_SUBQUERY, 0, 0, answer.qname, answer.rrdata.get(0), null);

			if (isFlagSet(F_AUTHREDIRECT)) {
				//no, it's one of the responses to subqueries by the Auth-Redirect hack
				int ip = (rrdata.size() == 0 ? 0 : rrdata.get(0).rr.getIP());
				clearAnswer(); //if response failed to resolve our RR, this ensures evaluate() returns NODOM
				if (ip != 0) {
					repeatQuery(ip);
					return;
				}
			}
		} else {
			//we've failed to find one of a number of targets - just discard it and carry on
			int pos = getUnresolvedRR(answer.qname);
			if (pos != -1) discardUnresolvedRR(pos);
			if (answer.result != ResolverAnswer.STATUS.NODOMAIN) setFlag(F_NOTNXDOM);
		}
		if (subqry_open == 0) evaluateResponse();
	}

	// Only relevant to NS queries. Is used to unblock A and NS callers as soon as possible, to prevent deadlock
	// chains building up.
	// Callers are kicked into action in the same way they would be by an NS subquery calling back into dnsResolved()
	// after it completes. The only difference is we pass in a suggested IP here to save some work, but this ought to
	// work even if we passed IP=0 to repeatQuery().
	private void notifyPartialAnswer()
	{
		for (int idx = callers.size() - 1; idx != -1; idx--) {
			if (callers.get(idx) instanceof QueryHandle) {
				QueryHandle qhcaller = (QueryHandle)callers.get(idx);
				if (qhcaller.qtype == ResolverDNS.QTYPE_NS || qhcaller.qtype == ResolverDNS.QTYPE_A) {
					callers.remove(idx);
					callerparams.remove(idx);
					qhcaller.subqry_open--;
					qhcaller.repeatQuery(getPartialAnswer());
				}
			}
		}
	}

	// Only relevant to NS queries. Allows a peek at our partially formed answer, before this query officially completes
	public int getPartialAnswer()
	{
		int cnt = getResolvedCount();
		if (cnt == 0) return 0;
		int selected = rslvr.nextRandomInt(cnt);
		int pos = 0;
		for (int idx = 0; idx != rrdata.size(); idx++) {
			int ip = rrdata.get(idx).rr.getIP();
			if (ip != 0) {
				if (pos++ == selected) return ip;
			}
		}
		throw new IllegalStateException("Query="+ResolverDNS.getQTYPE(qtype)+"/"+qname+" failed to locate partialIP="
				+selected+"/"+cnt+"/"+rrdata.size()+" - "+rrdata);
	}

	private int getUnresolvedRR(ByteChars domnam)
	{
		for (int idx = 0; idx != rrdata.size(); idx++) {
			ResourceData rr = rrdata.get(idx).rr;
			ByteChars rr_domnam = getResolvableName(rr);
			if (rr.getIP() == 0 && rr_domnam.equals(domnam)) return idx;
		}
		return -1;
	}

	private ByteChars getResolvableName(ResourceData rr)
	{
		ByteChars domnam = rr.getName();
		if (rr.rrType() == ResolverDNS.QTYPE_NS) {
			domnam = ((ResourceData.RR_NS)rr).getHostname();
		} else if (rr.rrType() == ResolverDNS.QTYPE_MX) {
			domnam = ((ResourceData.RR_MX)rr).getRelay();
		} else if (rr.rrType() == ResolverDNS.QTYPE_SRV) {
			domnam = ((ResourceData.RR_SRV)rr).getTarget();
		}
		return domnam;
	}

	private void resolveIP(ResourceData rr_unresolved, ResourceData rr_a)
	{
		long ttl_unresolved = rr_unresolved.getExpiry();
		long ttl_a = rr_a.getExpiry();
		long ttl = (rslvr.getConfig().isSetMinTTL() ? Math.min(ttl_unresolved, ttl_a) : Math.max(ttl_unresolved, ttl_a));
		rr_unresolved.setIP(rr_a.getIP());
		rr_unresolved.setExpiry(ttl);
	}

	private void resolveIP(int pos, ResourceData rr_a)
	{
		ResourceData rr_unresolved = rrdata.get(pos).rr;
		resolveIP(rr_unresolved, rr_a);
		unresolved_cnt--;
	}

	private void discardUnresolvedRR(int pos)
	{
		WrapperRR wrr = rrdata.remove(pos);
		rslvr.freeWrapperRR(wrr);
		unresolved_cnt--;
	}

	private void discardUnresolvedRRs()
	{
		for (int idx = rrdata.size() - 1; idx != -1; idx--) {
			if (rrdata.get(idx).rr.getIP() == 0) {
				discardUnresolvedRR(idx);
			}
		}
	}

	private void clearAnswer()
	{
		for (int idx = 0; idx != rrdata.size(); idx++) {
			rslvr.freeWrapperRR(rrdata.get(idx));
		}
		rrdata.clear();
		unresolved_cnt = 0;
	}

	private boolean haveQuorumRRs()
	{
		int quorum = getMaxRRs();
		if (quorum == 0) return false; //no finite quorum, must load and resolve all answer RRs
		if (getResolvedCount() < quorum) return false;
		//we have sufficient resolved RRs - discard all the unresolved ones
		discardUnresolvedRRs();
		return true;
	}

	private void notifyCallers(ResolverAnswer answer)
	{
		while (callers.size() != 0) {
			ResolverDNS.Client caller = callers.remove(0);
			Object param = callerparams.remove(0);
			try {
				caller.dnsResolved(rslvr.getDispatcher(), answer, param);
			} catch (Throwable ex) {
				rslvr.logger.log(Logger.LEVEL.INFO, ex, true, "DNS-Resolver: Client error on Answer - "+answer);
				rslvr.caller_errors++;
			}
			// make sure caller didn't terminate us (eg. by stopping Dispatcher), or any other re-entrancy effects
			if (!rslvr.isActive(this)) return;
		}
	}

	public int cancelExternalCallers(ResolverAnswer.STATUS result)
	{
		ResolverAnswer answer = setAnswer(result, null);
		for (int idx = callers.size() - 1; idx != -1; idx--) {
			if (callers.get(idx) instanceof QueryHandle) continue;
			ResolverDNS.Client caller = callers.remove(idx);
			Object param = callerparams.remove(idx);
			try {
				caller.dnsResolved(rslvr.getDispatcher(), answer, param);
			} catch (Throwable ex) {
				rslvr.logger.log(Logger.LEVEL.INFO, ex, true, "DNS-Resolver: Client-cancel error - "+answer);
				rslvr.caller_errors++;
			}
		}
		rslvr.freeAnswerBuf(answer);
		return callers.size();
	}

	public void addCaller(ResolverDNS.Client cllr, Object callerparam)
	{
		callers.add(cllr);
		callerparams.add(callerparam);
	}

	// Take care to remove multiple occurrences of this caller, but no need to check callers of callers
	public int removeCaller(ResolverDNS.Client caller)
	{
		int reqs = 0;
		int idx;
		while ((idx = callers.indexOf(caller)) != -1) {
			callers.remove(idx);
			callerparams.remove(idx);
			reqs++;
		}
		return reqs;
	}

	// returns true if caller is one of our callers
	public boolean isCaller(ResolverDNS.Client caller)
	{
		for (int idx = 0; idx != callers.size(); idx++) {
			if (callers.get(idx) == caller) return true;
			if (callers.get(idx).getClass() == getClass()) {
				QueryHandle qh2 = (QueryHandle)callers.get(idx);
				if (qh2.isCaller(caller)) return true;
			}
		}
		return false;
	}

	@Override
	public boolean handleMessageQuestion(int pktqid, int qnum, int qcnt, byte qt, byte qclass, ByteChars qn, java.net.InetSocketAddress srvaddr)
	{
		if (qnum != 0) return false; //we only send one question per query, so expect only one echoed back
		if (qclass != PacketDNS.QCLASS_INET || qt != qtype || !qn.equals(qname)) return false;
		if (isTCP()) return true;
		int remote_ip = IP.convertIP(srvaddr.getAddress());
		if (remote_ip != ip_request) {
			StringBuilder sb = rslvr.reusableStringBuilder();
			sb.setLength(0);
			sb.append("DNS-Resolver: discarding response from unexpected server=");
			IP.displayDottedIP(remote_ip, sb).append(" vs ");
			IP.displayDottedIP(ip_request, sb);
			sb.append(" - request=").append(ResolverDNS.getQTYPE(qtype)).append('/').append(qname);
			rslvr.logger.info(sb);
			return false;
		}
		return true;
	}

	private void startTimer(int type)
	{
		ResolverConfig cfg = rslvr.getConfig();
		long tmt;
		if (tcpconn == null) {
			tmt = cfg.getRetryTimeout() + (cfg.getRetryStep() * retrycnt) + rslvr.nextRandomInt((int)cfg.getRetryStep());
		} else {
			tmt = cfg.getRetryTimeoutTCP();
		}
		if (tmr == null) {
			tmr = rslvr.getDispatcher().setTimer(tmt, type, this);
		} else {
			tmr.reset(tmt);
		}
	}

	private void stopTimer()
	{
		if (tmr == null) return;
		tmr.cancel();
		tmr = null;
	}

	@Override
	public void timerIndication(TimerNAF t, Dispatcher d)
	{
		if (rslvr.logger.isActive(ResolverConfig.DEBUGLVL)) {
			rslvr.logger.log(ResolverConfig.DEBUGLVL, "DNS-Resolver timeout="+t.getType()+"/"+(retrycnt+1)+"/"+(rslvr.getConfig().getRetryMax()+1)
					+" on "+ResolverDNS.getQTYPE(qtype)+"/"+qname);
		}
		tmr = null;
		rslvr.stats_tmt++;

		// one timeout is enough to fail a TCP query
		if (isTCP() || (retrycnt == rslvr.getConfig().getRetryMax())) {
			endRequest(ResolverAnswer.STATUS.TIMEOUT);
			return;
		}
		retrycnt++;

		if (t.getType() == TMR_DEADLOCK) {
			evaluateResponse();
		} else if (t.getType() == TMR_TIMEOUT) {
			int server_ip = (isFlagSet(F_IPSTICKY) ? ip_request : 0);
			repeatQuery(server_ip);
		} else {
			throw new IllegalStateException("DNS-Resolver: Missing case for timer-type="+t.getType());
		}
	}

	@Override
	public void eventError(TimerNAF t, Dispatcher d, Throwable ex)
	{
		//already logged by Dispatcher
		endRequest(ResolverAnswer.STATUS.ERROR);
	}

	private ResolverAnswer setAnswer(ResolverAnswer.STATUS result, ResolverAnswer answer)
	{
		if (answer == null) answer = rslvr.allocAnswerBuf();
		if (qip != 0) {
			answer.set(result, qtype, qip);
		} else {
			answer.set(result, qtype, qname);
		}
		return answer;
	}

	private int getMaxRRs()
	{
		if (qtype == ResolverDNS.QTYPE_NS) {
			return rslvr.getConfig().getNsMaxRR();
		} else if (qtype == ResolverDNS.QTYPE_MX) {
			return rslvr.getConfig().getMxMaxRR();
		}
		return 0;
	}
}
