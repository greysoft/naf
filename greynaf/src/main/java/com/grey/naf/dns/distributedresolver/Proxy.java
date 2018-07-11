/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

import com.grey.base.config.XmlConfig;
import com.grey.base.utils.ByteChars;
import com.grey.naf.dns.ResolverAnswer;
import com.grey.naf.dns.ResolverDNS;
import com.grey.naf.dns.ResolverService;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;
import com.grey.logging.Logger.LEVEL;

// This class acts as a proxy for ResolverService
class Proxy
	implements ResolverDNS.Client, Producer.Consumer<Request>
{
	private static final String LOGLABEL = "DNS-Distributed-Resolver";
	private static final String APPCONTEXTNAME = Proxy.class.getName();

	private final String master;
	private ResolverService rslvr;
	private Producer<Request> prod;
	private XmlConfig cfg;

	public Dispatcher getMaster() {return (rslvr == null ? null : rslvr.getDispatcher());}

	// Our users are long-lived objects who locate us in their constructor, so no need to be
	// super-optimal about avoiding synchronisation here.
	public static Proxy get(Dispatcher dsptch, XmlConfig cfg) throws java.io.IOException, javax.naming.NamingException
	{
		Proxy p = dsptch.getApplicationContext().getNamedItem(APPCONTEXTNAME, (c) -> new Proxy(dsptch, cfg));

		if (p.rslvr == null) {
			// we haven't yet latched our master thread
			if (p.master == null || p.master.equals(dsptch.name)) {
				// we've found it now - we will execute within the context of this dispatcher
				p.setDispatcher(dsptch);
			}
		}
		dsptch.getLogger().info(LOGLABEL+": Dispatcher="+dsptch.name+" set as "+(p.rslvr!=null?"master":"secondary")+" client");
		return p;
	}

	private Proxy(Dispatcher d, XmlConfig cfg)
	{
		this.cfg = cfg;
		master = cfg.getValue("@master", false, null);
		d.getLogger().info(LOGLABEL+": Proxy with master="+master+" created by Dispatcher="+d.name);
	}

	private void setDispatcher(Dispatcher d) throws java.io.IOException, javax.naming.NamingException
	{
		rslvr = new ResolverService(d, cfg);
		prod = new Producer<>(Request.class, d, this);
		cfg = null;
	}

	protected void clientStarted(Client clnt) throws java.io.IOException
	{
		if (rslvr == null) throw new IllegalStateException(LOGLABEL+": Client="+clnt.getDispatcher().name+" started before setting Master");
		if (clnt.getDispatcher() == rslvr.getDispatcher()) rslvr.start(); //this is the Master, so start Resolver within its thread
	}

	protected void clientStopped(Client clnt)
	{
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			// The master Dispatcher is shutting down.
			// Shut down the backing ResolverService in the Master thread, and enable a new master to be latched.
			rslvr.stop();
			clnt.getDispatcher().getApplicationContext().deregisterNamedItem(APPCONTEXTNAME);
		}
	}

	protected ResolverAnswer resolve(Client clnt, byte qtype, ByteChars qname, ResolverDNS.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			return rslvr.resolve(qtype, qname, caller, cbdata, flags);
		}
		Request req = clnt.allocateRequestBlock();
		req.setQuery(caller, qtype, qname, 0, cbdata, flags);
		prod.produce(req);
		return null;
	}

	protected ResolverAnswer resolve(Client clnt, byte qtype, int qip, ResolverDNS.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			return rslvr.resolve(qtype, qip, caller, cbdata, flags);
		}
		Request req = clnt.allocateRequestBlock();
		req.setQuery(caller, qtype, null, qip, cbdata, flags);
		prod.produce(req);
		return null;
	}

	public int cancel(Client clnt, ResolverDNS.Client caller)
	{
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			return rslvr.cancel(caller);
		}
		return -1; //can't pass this onto Resolver thread, as it thinks this object was the caller
	}

	@Override
	public void producerIndication(Producer<Request> p) throws java.io.IOException
	{
		Request req;
		while ((req = prod.consume()) != null) {
			issueRequest(req);
		}
	}

	@Override
	public void dnsResolved(Dispatcher d, ResolverAnswer answer, Object cbdata)
	{
		try {
			requestResolved((Request)cbdata, answer);
		} catch (Exception ex) {
			d.getLogger().log(LEVEL.ERR, ex, true, LOGLABEL+": Failed on answer="+answer);
		}
	}

	private void issueRequest(Request req) throws java.io.IOException
	{
		ResolverAnswer answer;
		if (req.answer.qtype == ResolverDNS.QTYPE_PTR) {
			answer = rslvr.resolve(req.answer.qtype, req.answer.qip, this, req, req.flags);
		} else {
			answer = rslvr.resolve(req.answer.qtype, req.answer.qname, this, req, req.flags);
		}
		if (answer != null) requestResolved(req, answer);
	}

	private void requestResolved(Request req, ResolverAnswer answer) throws java.io.IOException
	{
		req.setResponse(answer);
		req.client.produce(req);
	}
}