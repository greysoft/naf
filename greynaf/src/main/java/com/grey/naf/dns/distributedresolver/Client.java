/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

import com.grey.base.config.XmlConfig;
import com.grey.base.utils.ByteChars;
import com.grey.base.collections.HashedSet;
import com.grey.base.collections.ObjectWell;
import com.grey.naf.dns.ResolverAnswer;
import com.grey.naf.dns.ResolverDNS;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;

public class Client
	extends ResolverDNS
	implements Producer.Consumer<Request>
{
	public static final class RequestFactory
		implements ObjectWell.ObjectFactory
	{
		private final Producer<Request> prod;

		public RequestFactory(Producer<Request> prod) {
			this.prod = prod;
		}

		@Override
		public Request factory_create() {
			return new Request(prod);
		}
	}

	private final Producer<Request> prod;
	private final HashedSet<ResolverDNS.Client> cancelled_callers;

	private final Proxy proxy;
	private final ObjectWell<Request> reqpool;

	public Client(Dispatcher dsptch, XmlConfig cfg) throws java.io.IOException, javax.naming.NamingException
	{
		super(dsptch, cfg);
		cancelled_callers = new HashedSet<>();
		proxy = Proxy.get(dsptch, cfg);
		prod = new Producer<>(Request.class, dsptch, this);
		prod.start();

		RequestFactory factory = new RequestFactory(prod);
		reqpool = new ObjectWell<>(Request.class, factory, "DNS_Client_"+dsptch.getName(), 0, 0, 1);
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
	protected ResolverAnswer resolve(byte qtype, ByteChars qname, ResolverDNS.Client caller,
			Object cbdata, int flags) throws java.io.IOException
	{
		return proxy.resolve(this, qtype, qname, caller, cbdata, flags);
	}

	@Override
	protected ResolverAnswer resolve(byte qtype, int qip, ResolverDNS.Client caller,
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
	public int cancel(ResolverDNS.Client caller) throws java.io.IOException
	{
		int cnt = proxy.cancel(this, caller);
		if (cnt == -1) cancelled_callers.add(caller);
		return cnt;
	}

	@Override
	public void producerIndication(Producer<Request> p) throws java.io.IOException
	{
		Request req;
		while ((req = prod.consume()) != null) {
			if (req.caller != null && !cancelled_callers.contains(req.caller)) {
				req.caller.dnsResolved(getDispatcher(), req.answer, req.cbdata);
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
		return getClass().getName()+"/Master="+(proxy.getMaster()==getDispatcher());
	}
}