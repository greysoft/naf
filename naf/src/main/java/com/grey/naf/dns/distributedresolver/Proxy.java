/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

class Proxy
	implements com.grey.naf.dns.Resolver.Client, com.grey.naf.reactor.Producer.Consumer
{
	private static Proxy instance;

	protected final com.grey.naf.reactor.Dispatcher dsptch;
	protected final com.grey.naf.dns.ResolverService rslvr;
	protected final com.grey.naf.reactor.Producer<Object> prod;

	// Our users are long-lived objects who locate us in their constructor, so no need to be
	// super-optimal about avoiding synchronisation here.
	public static synchronized Proxy get(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		if (instance == null) instance = new Proxy(dsptch, cfg);
		return instance;
	}

	private Proxy(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg)
		throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		dsptch = d;
		rslvr = new com.grey.naf.dns.ResolverService(dsptch, cfg);
		prod = new com.grey.naf.reactor.Producer<Object>(Object.class, dsptch, this);
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<?> p) throws java.io.IOException
	{
		Object event;
		while ((event = prod.consume()) != null) {
			if (event instanceof Request) {
				issueRequest(Request.class.cast(event));
			} else if (event instanceof com.grey.naf.dns.Resolver.Client) {
				rslvr.cancel(com.grey.naf.dns.Resolver.Client.class.cast(event));
			}
		}
	}

	@Override
	public void dnsResolved(com.grey.naf.reactor.Dispatcher d, com.grey.naf.dns.Answer answer, Object cbdata) throws java.io.IOException
	{
		requestResolved(Request.class.cast(cbdata), answer);
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
