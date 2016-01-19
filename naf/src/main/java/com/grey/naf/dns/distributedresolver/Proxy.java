/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

import com.grey.logging.Logger.LEVEL;

// This class acts as a proxy for ResolverService
final class Proxy
	implements com.grey.naf.dns.Resolver.Client, com.grey.naf.reactor.Producer.Consumer<Request>
{
	private static final String LOGLABEL = "DNS-Distributed-Resolver";
	private static Proxy instance; //singleton

	private final String master;
	private com.grey.naf.dns.ResolverService rslvr;
	private com.grey.naf.reactor.Producer<Request> prod;
	private com.grey.base.config.XmlConfig cfg;

	public com.grey.naf.reactor.Dispatcher getMaster() {return (rslvr == null ? null : rslvr.dsptch);}

	// Our users are long-lived objects who locate us in their constructor, so no need to be
	// super-optimal about avoiding synchronisation here.
	public static synchronized Proxy get(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		if (instance == null) {
			instance = new Proxy(dsptch, cfg);
		}
		boolean is_master = false;

		if (instance.rslvr == null) {
			// we haven't yet latched our master thread
			if (instance.master == null || instance.master.equals(dsptch.name)) {
				// we've found it now - we will execute within the context of this dispatcher
				instance.setDispatcher(dsptch);
				is_master = true;
			}
		}
		dsptch.logger.info(LOGLABEL+": Dispatcher="+dsptch.name+" set as "+(is_master?"master":"secondary")+" client");
		return instance;
	}

	private Proxy(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg)
		throws com.grey.base.ConfigException
	{
		this.cfg = cfg;
		master = cfg.getValue("@master", false, null);
		d.logger.info(LOGLABEL+": Proxy with master="+master+" created by Dispatcher="+d.name);
	}

	private void setDispatcher(com.grey.naf.reactor.Dispatcher d)
		throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		rslvr = new com.grey.naf.dns.ResolverService(d, cfg);
		prod = new com.grey.naf.reactor.Producer<Request>(Request.class, d, this);
		cfg = null;
	}

	protected void clientStarted(Client clnt) throws java.io.IOException
	{
		if (rslvr == null) throw new IllegalStateException(LOGLABEL+": Client="+clnt.dsptch.name+" started before setting Master");
		if (clnt.dsptch == rslvr.dsptch) rslvr.start(); //this is the Master, so start Resolver within its thread
	}

	protected void clientStopped(Client clnt)
	{
		if (clnt.dsptch == rslvr.dsptch) {
			// The master Dispatcher is shutting down.
			// Shut down the backing ResolverService in the Master thread, and enable a new master to be latched.
			synchronized (getClass()) {
				rslvr.stop();
				instance = null;
			}
		}
	}

	protected com.grey.naf.dns.Answer resolve(Client clnt, byte qtype, com.grey.base.utils.ByteChars qname,
		com.grey.naf.dns.Resolver.Client caller, Object cbdata, int flags) throws java.io.IOException
	{
		if (clnt.dsptch == rslvr.dsptch) {
			return rslvr.resolve(qtype, qname, caller, cbdata, flags);
		}
		Request req = clnt.allocateRequestBlock();
		req.setQuery(caller, qtype, qname, 0, cbdata, flags);
		prod.produce(req);
		return null;
	}

	protected com.grey.naf.dns.Answer resolve(Client clnt, byte qtype, int qip, com.grey.naf.dns.Resolver.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		if (clnt.dsptch == rslvr.dsptch) {
			return rslvr.resolve(qtype, qip, caller, cbdata, flags);
		}
		Request req = clnt.allocateRequestBlock();
		req.setQuery(caller, qtype, null, qip, cbdata, flags);
		prod.produce(req);
		return null;
	}

	public int cancel(Client clnt, com.grey.naf.dns.Resolver.Client caller)
	{
		if (clnt.dsptch == rslvr.dsptch) {
			return rslvr.cancel(caller);
		}
		return -1; //can't pass this onto Resolver thread, as it thinks this object was the caller
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<Request> p) throws java.io.IOException
	{
		Request req;
		while ((req = prod.consume()) != null) {
			issueRequest(req);
		}
	}

	@Override
	public void dnsResolved(com.grey.naf.reactor.Dispatcher d, com.grey.naf.dns.Answer answer, Object cbdata)
	{
		try {
			requestResolved((Request)cbdata, answer);
		} catch (Exception ex) {
			d.logger.log(LEVEL.ERR, ex, true, LOGLABEL+": Failed on answer="+answer);
		}
	}

	private void issueRequest(Request req) throws java.io.IOException
	{
		com.grey.naf.dns.Answer answer;
		if (req.answer.qtype == com.grey.naf.dns.Resolver.QTYPE_PTR) {
			answer = rslvr.resolve(req.answer.qtype, req.answer.qip, this, req, req.flags);
		} else {
			answer = rslvr.resolve(req.answer.qtype, req.answer.qname, this, req, req.flags);
		}
		if (answer != null) requestResolved(req, answer);
	}

	private void requestResolved(Request req, com.grey.naf.dns.Answer answer) throws java.io.IOException
	{
		req.setResponse(answer);
		req.client.produce(req);
	}
}