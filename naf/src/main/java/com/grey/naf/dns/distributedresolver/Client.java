/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

public final class Client
	extends com.grey.naf.dns.Resolver
	implements com.grey.naf.reactor.Producer.Consumer<Request>
{
	public static final class RequestFactory
		implements com.grey.base.collections.ObjectWell.ObjectFactory
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

	final com.grey.naf.reactor.Producer<Request> prod; //package-private
	private final com.grey.base.collections.HashedSet<com.grey.naf.dns.Resolver.Client> cancelled_callers;

	private final Proxy proxy;
	private final com.grey.base.collections.ObjectWell<Request> reqpool;

	public Client(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException, javax.naming.NamingException
	{
		super(dsptch, cfg);
		cancelled_callers = new com.grey.base.collections.HashedSet<com.grey.naf.dns.Resolver.Client>();
		proxy = Proxy.get(dsptch, cfg);
		prod = new com.grey.naf.reactor.Producer<Request>(Request.class, dsptch, this);

		RequestFactory factory = new RequestFactory(prod);
		reqpool = new com.grey.base.collections.ObjectWell<Request>(Request.class, factory, "DNS_Client_"+dsptch.name, 0, 0, 1);
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

	// NB: This behaves differently for callers outside the Resolver thread. Cancel normally cancels
	// all requests made so far, but in their case it cancels all future requests as well.
	// That may not be such a big deal as it sounds, as the cancel() method is quite a blunt instrument
	// which is really only intended to used during shutdown (to prevent callbacks into a destroyed object),
	// in which case there would be no future calls.
	@Override
	public int cancel(com.grey.naf.dns.Resolver.Client caller) throws java.io.IOException
	{
		int cnt = proxy.cancel(this, caller);
		if (cnt == -1) cancelled_callers.add(caller);
		return cnt;
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<Request> p) throws java.io.IOException
	{
		Request req;
		while ((req = prod.consume()) != null) {
			if (req.caller != null && !cancelled_callers.contains(req.caller)) {
				req.caller.dnsResolved(dsptch, req.answer, req.cbdata);
			}
			synchronized (reqpool) {
				reqpool.store(req);
			}
		}
	}

	Request allocateRequestBlock()
	{
		synchronized (reqpool) {
			return reqpool.extract();
		}
	}

	@Override
	public String toString()
	{
		return getClass().getName()+"/Master="+(proxy.getMaster()==dsptch);
	}
}