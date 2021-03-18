/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.distributed;

import java.util.function.Supplier;

import com.grey.base.utils.ByteChars;
import com.grey.base.collections.HashedSet;
import com.grey.base.collections.ObjectPool;
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
	private final Producer<Request> proxyReceiver; //receives responses from Proxy, is null if we're the master Dispatcher
	private final HashedSet<ResolverDNS.Client> cancelledCallers;
	private final ObjectPool<Request> reqPool;
	private final ProxyReference proxyRef;

	private Proxy getProxy() {return proxyRef.get();}

	/**
	 * Applications should call ResolverDNS.create() rather than this, as that sets it up for execution within a Dispatcher
	 */
	public DistributedResolver(Dispatcher dsptch, ResolverConfig config) throws java.io.IOException {
		super(dsptch);
		Supplier<Proxy> proxySupplier = Proxy.get(dsptch, config, config.getDistributedMaster());
		proxyRef = new ProxyReference(proxySupplier);

		if (getMasterDispatcher() != dsptch) {
			// the resolver engine is not running in this thread, so we will access it via the Producer's message-passing API
			cancelledCallers = new HashedSet<>();
			proxyReceiver = new Producer<>("DNS-distrib-proxyrsp", Request.class, dsptch, this);
			Supplier<Request> reqFactory = () -> new Request(this);
			reqPool = new ObjectPool<>(reqFactory);
		} else {
			// the resolver engine is running in this thread, so we will invoke it directly
			proxyReceiver = null;
			reqPool = null;
			cancelledCallers = new HashedSet<>();
		}
	}

	@Override
	public Dispatcher getMasterDispatcher() {
		Proxy proxy = getProxy();
		return (proxy == null ? null : proxy.getMasterDispatcher());
	}

	// This is called in the Dispatcher thread, and we will be running within that thread from now on
	@Override
	public void startDispatcherRunnable() throws java.io.IOException {
		Proxy proxy = getProxy();
		if (proxy == null) throw new NAFConfigException("Client="+this+" starting before Proxy exists");
		proxy.clientStarted(this);
		if (proxyReceiver != null) proxyReceiver.startDispatcherRunnable();
	}

	@Override
	public boolean stopDispatcherRunnable() {
		Proxy proxy = getProxy();
		if (proxy != null) proxy.clientStopped(this);
		if (proxyReceiver != null) proxyReceiver.stopDispatcherRunnable();
		return true;
	}

	@Override
	protected ResolverAnswer resolve(byte qtype, ByteChars qname, ResolverDNS.Client caller, Object cbdata, int flags) throws java.io.IOException {
		return getProxy().resolve(this, qtype, qname, caller, cbdata, flags, () -> allocateRequestBlock());
	}

	@Override
	protected ResolverAnswer resolve(byte qtype, int qip, ResolverDNS.Client caller, Object cbdata, int flags) throws java.io.IOException {
		return getProxy().resolve(this, qtype, qip, caller, cbdata, flags, () -> allocateRequestBlock());
	}

	// NB: This behaves differently for callers outside the Resolver thread. Cancel normally cancels
	// all requests made so far, but in their case it cancels all future requests as well.
	// That may not be such a big deal as it sounds, as the cancel() method is quite a blunt instrument
	// which is really only intended to be used during shutdown (to prevent callbacks into a destroyed object),
	// in which case there would be no future calls.
	@Override
	public int cancel(ResolverDNS.Client caller) throws java.io.IOException {
		int cnt = getProxy().cancel(this, caller);
		if (cnt == -1) cancelledCallers.add(caller);
		return cnt;
	}

	@Override
	public void producerIndication(Producer<Request> p) throws java.io.IOException {
		Request req;
		while ((req = proxyReceiver.consume()) != null) {
			if (req.caller != null && !cancelledCallers.contains(req.caller)) {
				req.caller.dnsResolved(getDispatcher(), req.answer, req.cbdata);
			}
			reqPool.store(req);
		}
	}

	public void issueResponse(Request req) throws java.io.IOException {
		proxyReceiver.produce(req);
	}

	private Request allocateRequestBlock() {
		return reqPool.extract();
	}

	@Override
	public String toString() {
		return super.toString()+"/Dispatcher="+getDispatcher().getName()+"/Master="+(getMasterDispatcher()==getDispatcher());
	}


	private static class ProxyReference
	{
		private final Supplier<Proxy> proxySupplier;
		private Proxy proxy;

		public ProxyReference(Supplier<Proxy> proxySupplier) {
			this.proxySupplier = proxySupplier;
		}

		public Proxy get() {
			if (proxy == null) proxy = proxySupplier.get();
			return proxy;
		}
	}
}