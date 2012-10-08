/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.logging.Logger.LEVEL;

/*
 * NB: We never expect TCP channels to block, since we only write out one puny little DNS request packet per connection. So no IOExecWriter.
 * We also prefer to do raw I/O handling for our reads, so no IOExecReader either.
 * Since we don't make use of those 2 helper classes, we have no need to implement ChannelMonitor.ioDisconnected() either (or the more
 * obvious methods called by those 2 classes).
 */
public final class QueryHandle
	extends com.grey.naf.reactor.ChannelMonitor
{
	public static final class Factory
		implements com.grey.base.utils.ObjectWell.ObjectFactory
	{
		private final com.grey.naf.reactor.Dispatcher dsptch;

		public Factory(com.grey.naf.reactor.Dispatcher d) {
			dsptch = d;
		}

		@Override
		public QueryHandle factory_create() {
			return new QueryHandle(dsptch);
		}
	}

	public ResolverService rslvr;
	public int qid;  //ID of current request packet, not this query as a whole
	public byte qtype;	//type of query
	public com.grey.base.utils.ByteChars qname;  //overall question for this query
	public int qip;  //for PTR queries, this is the raw IP value behind qname (which will be an in-addr.arpa name)
	public com.grey.base.utils.ByteChars pktqname;  //question for current request packet
	public byte pktqtype;	//type of pktqname
	public com.grey.naf.reactor.Timer tmr;
	public int retrycnt;
	public int prevqid[];  //QIDs of timed-out requests for current query

	private ServerHandle dnsserver;  //stick to one server for all this query's requests, as first one probably populates all/most of the RRs we need
	private final java.util.ArrayList<Resolver.Client> callers = new java.util.ArrayList<Resolver.Client>();
	private final java.util.ArrayList<Object> callerparams = new java.util.ArrayList<Object>();
	private Packet tcpdnspkt;

	// The result RRs
	// For MX queries, these are the IP addresses (wrapped in A RRs) corresponding to the MX relays
	public final java.util.ArrayList<ResourceData> rrdata = new java.util.ArrayList<ResourceData>(1);

	// these fields only apply to MX queries
	public int mxcount;  //number of MX RRs remaining in rrdata list
	public int mxquery;  //points at MX entry in rrdata that is subject of current follow-on query (-1 means we're not in a follow-on query)
	public boolean fallback_mx_a;  //have fallen back to an A query

	public boolean isTCP() {return (tcpdnspkt != null);};
	public Packet getPacket(Packet udp) {return (isTCP() ? tcpdnspkt : udp);}


	public QueryHandle(com.grey.naf.reactor.Dispatcher d)
	{
		super(d);
	}

	// convenience method which can be called with same syntax as a constructor
	public QueryHandle init(com.grey.naf.reactor.Dispatcher d, ResolverService resolver,
			Resolver.Client cllr, Object callerparam, ServerHandle srv, byte qt, com.grey.base.utils.ByteChars qn)
	{
		clear();
		rslvr = resolver;
		dnsserver = srv;
		qtype = qt;
		if (cllr != null) addCaller(cllr, callerparam);

		// We generate new ByteChars instance on every reset() rather than reusing prev object, because this domain name will end up being
		// stored on a long-lived Resolver cache, so we'd have to create a new copy at that stage anyway.
		if (qn != null) qname = new com.grey.base.utils.ByteChars(qn, true);
		return this;
	}

	// Unlike the other query types, this incarnation's qname will not be passed to a long-term cache, and does not need to live beyond this
	// query, so we can benefit from object conservation and reuse
	public QueryHandle init(com.grey.naf.reactor.Dispatcher d, ResolverService resolver,
			Resolver.Client cllr, Object callerparam, ServerHandle srv, byte qt, int ip)
	{
		init(d, resolver, cllr, callerparam, srv, qt, null);
		qip = ip;
		qname = resolver.buildReverseDomain(ip);
		return this;
	}

	public QueryHandle clear()
	{
		disconnect();
		stopTimer();
		rrdata.clear();
		callers.clear();
		callerparams.clear();
		if (tcpdnspkt != null) rslvr.pktstore.store(tcpdnspkt);
		if (qip != 0 && qname != null) rslvr.bcstore.store(qname);
		tcpdnspkt = null;
		pktqname = null;
		pktqtype = 0;
		qname = null;
		qip = 0;
		qid = 0;
		retrycnt = 0;
		mxcount = 0;
		mxquery = -1;
		fallback_mx_a = false;
		return this;
	}

	public void startTimer(com.grey.naf.reactor.Timer.Handler handler, long tmt)
	{
		if (tmr == null) {
			tmr = dsptch.setTimer(tmt, 0, handler);
			tmr.attachment = this;
		} else {
			tmr.reset(tmt);
		}
	}
	
	public void stopTimer()
	{
		if (tmr == null) return;
		tmr.cancel();
		tmr = null;
	}
	
	public int send(Packet pkt)
	{
		return pkt.send(dnsserver, (java.nio.channels.WritableByteChannel)iochan);
	}

	public void connect(long tmt) throws com.grey.base.FaultException, java.io.IOException
	{
		tcpdnspkt = rslvr.pktstore.extract();
		connect(dnsserver.address());
	}

	@Override
	protected void connected(boolean success, Throwable ex) throws java.io.IOException
	{
		if (!success) {
			if (dsptch.logger.isActive(LEVEL.TRC)) {
				dsptch.logger.log(LEVEL.TRC, ex, false, "Resolver: TCP connect failed - "+dnsserver.address()+"/TCP="+isTCP());
			}
			rslvr.endQuery(this, Answer.STATUS.ERROR);
			return;
		}
		// the TCP connection is now established, so resend the previous UDP query (with new ID) over it
		rslvr.issueRequest(this, qtype, qname);

		// now wait for response
		tcpdnspkt.reset();
		enableRead();
	}

	// Servers are supposed to hold the connection open to allow for multiple requests, but we'll play it safe and close it
	// after receiving one response, and open a new one if needed for any follow-on requests.
	// If this throws, the Dispatcher will call our eventIndication() method, so we will get to the final conclusion either
	// way, with or without the required answers.
	// We know that the readyOps argument must indicate a Read (that's all we registered for), so don't bother checking it.
	@Override
	protected void ioIndication(int readyOps) throws java.io.IOException
	{
		int nbytes = 0;

		// AXFR queries always result in multiple response messages, but be prepared for that to happen on any TCP connection.
		while ((nbytes = tcpdnspkt.receive(false, dnsserver, (java.nio.channels.ReadableByteChannel)iochan)) != 0) {
			if (nbytes == -1) {
				// remote server has closed connection, so we've received all the data (if any) for this query
				rslvr.endQuery(this, Answer.STATUS.OK);
				return;
			}
			rslvr.processResponse(this);
			if (!isConnected()) return;  // this query has completed

			// read in next message if already available
			tcpdnspkt.reset();
		}

		// wait for next message
		tmr.reset();
	}

	// Our TCP connection has been aborted by the Dispatcher after an error somewhere, so notify the resolver
	// We know that 'cm' arg must be tcpmon, so don't bother checking that.
	@Override
	public void eventError(com.grey.naf.reactor.ChannelMonitor cm, Throwable ex)
	{
		disconnect();
		rslvr.endQuery(this, Answer.STATUS.ERROR);
	}

	public void notifyCallers(Answer answer)
	{
		for (int idx = 0; idx != callers.size(); idx++) {
			try {
				callers.get(idx).dnsResolved(dsptch, answer, callerparams.get(idx));
			} catch (Throwable ex) {
				dsptch.logger.log(LEVEL.INFO, ex, true, "Resolver: Client error - " + answer);
			}
		}		
	}

	public void addCaller(Resolver.Client cllr, Object callerparam)
	{
		callers.add(cllr);
		callerparams.add(callerparam);
	}

	// take care to remove multiple occurrences of this caller
	public int removeCaller(Resolver.Client caller)
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
}
