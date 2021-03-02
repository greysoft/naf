/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.distributed;

import java.util.function.Supplier;

import com.grey.base.utils.ByteChars;
import com.grey.base.collections.HashedSet;
import com.grey.base.collections.ObjectWell;
import com.grey.naf.dns.resolver.ResolverAnswer;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;

/**
 * This class provides a ResolverDNS API to access a resolver engine that is shared by all Dispatcher threads in the same application context.
 * One of the Dispatchers will create the resolver engine and invoke it directly, while the others will access it in a thread-safe manner.
 * In all cases, the Proxy class mediates this class's access to the resolver engine.
 */
public class DistributedResolver
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

	private final Producer<Request> prod; //receives responses from Proxy if we're not the master Dispatcher
	private final HashedSet<ResolverDNS.Client> cancelled_callers;
	private final ObjectWell<Request> reqpool;
	private final Supplier<Proxy> proxySupplier;
	private Proxy proxy;

	public DistributedResolver(Dispatcher dsptch, ResolverConfig config, String master) throws java.io.IOException
	{
		super(dsptch);
		proxySupplier = Proxy.get(dsptch, config, master);
		proxy = proxySupplier.get();

		if (proxy == null || proxy.getMaster() != dsptch) {
			// the resolver engine is not running in this thread, so we will access it via the Producer's message-passing API
			cancelled_callers = new HashedSet<>();
			prod = new Producer<>(Request.class, dsptch, this);
			RequestFactory factory = new RequestFactory(prod);
			reqpool = new ObjectWell<>(Request.class, factory, "DNS_Client_"+dsptch.getName(), 0, 0, 1);
			prod.start();
		} else {
			// the resolver engine is running in this thread, so we will invoke it directly
			prod = null;
			reqpool = null;
			cancelled_callers = new HashedSet<>();
		}
	}

	// This is called in the Dispatcher thread, and we will be running within that thread from now on
	@Override
	public void start() throws java.io.IOException
	{
		if (proxy == null) proxy = proxySupplier.get();
		if (proxy == null) throw new NAFConfigException("Client="+this+" starting before Proxy exists");
		proxy.clientStarted(this);
	}

	@Override
	public void stop()
	{
		if (proxy == null) proxy = proxySupplier.get(); //in case we never called start()
		if (proxy != null) proxy.clientStopped(this);
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
	// which is really only intended to be used during shutdown (to prevent callbacks into a destroyed object),
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
		return getClass().getName()+"/Dispatcher="+getDispatcher().getName()+"/Master="+(proxy==null ? false : proxy.getMaster()==getDispatcher());
	}
}