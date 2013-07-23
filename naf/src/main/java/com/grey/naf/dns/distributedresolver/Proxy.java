/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

final class Proxy
	implements com.grey.naf.dns.Resolver.Client, com.grey.naf.reactor.Producer.Consumer
{
	private static Proxy instance;

	private final String master;
	private com.grey.naf.reactor.Dispatcher dsptch;
	private com.grey.naf.dns.ResolverService rslvr;
	private com.grey.naf.reactor.Producer<Object> prod;
	private com.grey.base.config.XmlConfig cfg;

	public com.grey.naf.reactor.Dispatcher getMaster() {return dsptch;}

	// Our users are long-lived objects who locate us in their constructor, so no need to be
	// super-optimal about avoiding synchronisation here.
	public static synchronized Proxy get(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		if (instance == null) {
			instance = new Proxy(dsptch, cfg);
		}

		if (instance.dsptch == null) {
			// we haven't yet latched our master thread
			if (instance.master == null || instance.master.equals(dsptch.name)) {
				// we've found it now - we will execute within the context of this dispatcher
				instance.setDispatcher(dsptch);
			}
		}
		if (instance.dsptch != dsptch) dsptch.logger.info("Distributed-Resolver: Dispatcher="+dsptch.name+" set as secondary client");
		return instance;
	}

	private Proxy(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg)
		throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		this.cfg = cfg;
		master = cfg.getValue("@master", false, null);
		d.logger.info("Created Distributed-Resolver proxy with master="+master);
	}

	private void setDispatcher(com.grey.naf.reactor.Dispatcher d)
		throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		dsptch = d;
		rslvr = new com.grey.naf.dns.ResolverService(dsptch, cfg);
		prod = new com.grey.naf.reactor.Producer<Object>(Object.class, dsptch, this);
		cfg = null;
		d.logger.info("Distributed-Resolver: Dispatcher="+dsptch.name+" set as primary client");
	}

	protected void clientStarted(Client clnt) throws java.io.IOException
	{
		if (dsptch == null) throw new IllegalStateException("Distributed-Resolver: Client="+clnt.dsptch.name+" started before setting Master");
		if (clnt.dsptch == dsptch) rslvr.start(); //this is the Master, so start Resolver within its thread
	}

	protected void clientStopped(Client clnt)
	{
		if (clnt.dsptch == dsptch) rslvr.stop(); //stop Resolver within Master's thread
	}

	protected com.grey.naf.dns.Answer resolve(Client clnt, byte qtype, com.grey.base.utils.ByteChars qname, com.grey.naf.dns.Resolver.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		if (clnt.dsptch == dsptch) {
			return rslvr.resolve(qtype, qname, caller, cbdata, flags);
		}
		Request req = clnt.reqpool.extract();
		req.set(caller, qtype, qname, 0, cbdata, flags);
		prod.produce(req, dsptch);
		return null;
	}

	protected com.grey.naf.dns.Answer resolve(Client clnt, byte qtype, int qip, com.grey.naf.dns.Resolver.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		if (clnt.dsptch == dsptch) {
			return rslvr.resolve(qtype, qip, caller, cbdata, flags);
		}
		Request req = clnt.reqpool.extract();
		req.set(caller, qtype, null, qip, cbdata, flags);
		prod.produce(req, dsptch);
		return null;
	}

	public int cancel(Client clnt, com.grey.naf.dns.Resolver.Client caller) throws java.io.IOException
	{
		if (clnt.dsptch == dsptch) {
			return rslvr.cancel(caller);
		}
		prod.produce(caller, dsptch);
		return -1;  //we don't know how many requests will get cancelled
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<?> p) throws java.io.IOException
	{
		Object event;
		while ((event = prod.consume()) != null) {
			if (event.getClass() == Request.class) {
				issueRequest((Request)event);
			} else {
				rslvr.cancel((com.grey.naf.dns.Resolver.Client)event);
			}
		}
	}

	@Override
	public void dnsResolved(com.grey.naf.reactor.Dispatcher d, com.grey.naf.dns.Answer answer, Object cbdata) throws java.io.IOException
	{
		requestResolved((Request)cbdata, answer);
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
		req.answer.set(answer);
		req.client.produce(req, dsptch);
	}
}