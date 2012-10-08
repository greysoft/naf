/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

public class Client
	extends com.grey.naf.dns.Resolver
	implements com.grey.naf.reactor.Producer.Consumer
{
	public static final class RequestFactory
		implements com.grey.base.utils.ObjectWell.ObjectFactory
	{
		private final com.grey.naf.reactor.Producer<Request> prod;

		public RequestFactory(com.grey.naf.reactor.Producer<Request> prod) {
			this.prod = prod;
		}

		@Override
		public Request factory_create() {
			return new Request(prod);
		}
	}

	protected final com.grey.naf.reactor.Producer<Request> prod;

	private final Proxy proxy;
	final com.grey.base.utils.ObjectWell<Request> reqpool;

	public Client(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		super(dsptch, cfg);
		proxy = Proxy.get(dsptch, cfg);
		prod = new com.grey.naf.reactor.Producer<Request>(Request.class, dsptch, this);

		RequestFactory factory = new RequestFactory(prod);
		reqpool = new com.grey.base.utils.ObjectWell<Request>(Request.class, factory, "DNS_DistribReqs_"+dsptch.name, 0, 0, 1);
	}

	@Override
	public void start() throws java.io.IOException
	{
		proxy.clientStarted(this);
	}

	@Override
	public void stop()
	{
		proxy.clientStopped(this);
	}

	@Override
	protected com.grey.naf.dns.Answer resolve(byte qtype, com.grey.base.utils.ByteChars qname, com.grey.naf.dns.Resolver.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		return proxy.resolve(this, qtype, qname, caller, cbdata, flags);
	}

	@Override
	protected com.grey.naf.dns.Answer resolve(byte qtype, int qip, com.grey.naf.dns.Resolver.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		return proxy.resolve(this, qtype, qip, caller, cbdata, flags);
	}

	@Override
	public int cancel(com.grey.naf.dns.Resolver.Client caller) throws java.io.IOException
	{
		return proxy.cancel(this, caller);
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<?> p) throws java.io.IOException
	{
		Request req;
		while ((req = prod.consume()) != null) {
			if (req.caller != null) req.caller.dnsResolved(dsptch, req.answer, req.cbdata);
			reqpool.store(req);
		}
	}
}