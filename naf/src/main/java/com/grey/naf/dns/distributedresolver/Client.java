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
	private final com.grey.base.utils.ObjectWell<Request> reqpool;

	public Client(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		super(dsptch, cfg);
		proxy = Proxy.get(dsptch, cfg);
		prod = new com.grey.naf.reactor.Producer<Request>(Request.class, dsptch, this);

		RequestFactory factory = new RequestFactory(prod);
		reqpool = new com.grey.base.utils.ObjectWell<Request>(Request.class, factory, 0, 0, 1);
	}

	@Override
	public void start() throws java.io.IOException
	{
		if (proxy.dsptch == dsptch) proxy.rslvr.start();
	}

	@Override
	public boolean stop()
	{
		if (proxy.dsptch == dsptch) return proxy.rslvr.stop();
		return true;
	}


	@Override
	protected com.grey.naf.dns.Answer resolve(byte qtype, com.grey.base.utils.ByteChars qname, com.grey.naf.dns.Resolver.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		if (proxy.dsptch == dsptch) {
			return proxy.rslvr.resolve(qtype, qname, caller, cbdata,flags);
		}
		return proxyRequest(caller, qtype, qname, 0, cbdata, flags);
	}

	@Override
	protected com.grey.naf.dns.Answer resolve(byte qtype, int qip, com.grey.naf.dns.Resolver.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		if (proxy.dsptch == dsptch) {
			return proxy.rslvr.resolve(qtype, qip, caller, cbdata,flags);
		}
		return proxyRequest(caller, qtype, null, qip, cbdata, flags);
	}

	@Override
	public int cancel(com.grey.naf.dns.Resolver.Client caller) throws java.io.IOException
	{
		if (proxy.dsptch == dsptch) {
			return proxy.rslvr.cancel(caller);
		}
		proxy.prod.produce(caller, dsptch);
		return -1;  //we don't know how many requests got cancelled
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

	private com.grey.naf.dns.Answer proxyRequest(com.grey.naf.dns.Resolver.Client caller, byte qtype,
			com.grey.base.utils.ByteChars qname, int qip,
			Object cbdata, int flags) throws java.io.IOException
	{
		Request req = reqpool.extract();
		if (qname == null) {
			req.set(caller, qtype, null, qip, cbdata, flags);
		} else {
			req.set(caller, qtype, qname, 0, cbdata, flags);
		}
		proxy.prod.produce(req, dsptch);
		return null;
	}
}
