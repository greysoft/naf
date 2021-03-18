/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.distributed;

import java.util.function.Supplier;

import com.grey.base.utils.ByteChars;
import com.grey.naf.dns.resolver.ResolverAnswer;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.ResolverService;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;
import com.grey.logging.Logger.LEVEL;

/**
 * This class mediates access to the resolver engine, on behalf of the DistributedReslver interface.
 * There is only a single instance of this class per ApplicationContextNAF, and it creates the resolver engine,
 * providing direct access for callers in the same Dispatcher thread, and indirect thread-safe access for callers
 * in other Dispatchers, via the message-passing Producer API.
 */
class Proxy
	implements ResolverDNS.Client, Producer.Consumer<Request>
{
	private static final String LOGLABEL = "DNS-Distributed-Resolver";
	private static final String APPCONTEXTNAME = Proxy.class.getName();

	private final ResolverService rslvr;
	private final Producer<Request> distributedReceiver; //receives requests from non-master Dispatchers, consumer side runs in master Dispatcher

	public Dispatcher getMasterDispatcher() {return rslvr.getDispatcher();}

	// The getNamedItem() call in here is synchronised and so will synchronise our callers.
	// Our users are long-lived objects who locate us in their constructor, so no need to be super-optimal about avoiding synchronisation here.
	public static Supplier<Proxy> get(Dispatcher dsptch, ResolverConfig cfg, String master)
	{
		dsptch.getLogger().info(LOGLABEL+": Acquiring Proxy in Dispatcher="+dsptch.getName()+" for master="+master);
		Proxy proxy = null;
		if (master == null || master.equals(dsptch.getName())) {
			Supplier<Proxy> func = () -> {
				try {
					return new Proxy(dsptch, cfg);
				} catch (Exception ex) {
					throw new NAFConfigException("Failed to create Proxy DNS resolver", ex);
				}
			};
			proxy = dsptch.getApplicationContext().getNamedItem(APPCONTEXTNAME, func);
		} else {
			proxy = dsptch.getApplicationContext().getNamedItem(APPCONTEXTNAME, null);
		}
		dsptch.getLogger().info(LOGLABEL+": Dispatcher="+dsptch.getName()+" has latched Proxy="+(proxy==null?null:proxy.getMasterDispatcher().getName()));
		Proxy p = proxy;
		return () -> p;
	}

	private Proxy(Dispatcher dsptch, ResolverConfig cfg) throws java.io.IOException, javax.naming.NamingException
	{
		dsptch.getLogger().info(LOGLABEL+": Master Dispatcher="+dsptch.getName()+" is creating Proxy");
		rslvr = new ResolverService(dsptch, cfg);
		distributedReceiver = new Producer<>("DNS-distrib-proxyreq", Request.class, dsptch, this);
	}

	protected void clientStarted(DistributedResolver clnt) throws java.io.IOException
	{
		clnt.getDispatcher().getLogger().info(LOGLABEL+": Client="+clnt+" has started - master="+rslvr.getDispatcher().getName());
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			// this is the Master, so start Resolver within its thread
			rslvr.start();
			distributedReceiver.startDispatcherRunnable();
		}
	}

	protected void clientStopped(DistributedResolver clnt)
	{
		clnt.getDispatcher().getLogger().info(LOGLABEL+": Client="+clnt+" has stopped - master="+rslvr.getDispatcher().getName());
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			// The master Dispatcher is shutting down
			distributedReceiver.stopDispatcherRunnable();
			rslvr.stop();
			clnt.getDispatcher().getApplicationContext().removeNamedItem(APPCONTEXTNAME);
		}
	}

	protected ResolverAnswer resolve(DistributedResolver clnt, byte qtype, ByteChars qname, ResolverDNS.Client caller,
			Object cbdata, int flags, Supplier<Request> reqSupplier) throws java.io.IOException
	{
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			return rslvr.resolve(qtype, qname, caller, cbdata, flags);
		}
		Request req = reqSupplier.get();
		req.setQuery(caller, qtype, qname, 0, cbdata, flags);
		distributedReceiver.produce(req);
		return null;
	}

	protected ResolverAnswer resolve(DistributedResolver clnt, byte qtype, int qip, ResolverDNS.Client caller,
			Object cbdata, int flags, Supplier<Request> reqSupplier) throws java.io.IOException
	{
		if (clnt.getDispatcher() == rslvr.getDispatcher()) {
			return rslvr.resolve(qtype, qip, caller, cbdata, flags);
		}
		Request req = reqSupplier.get();
		req.setQuery(caller, qtype, null, qip, cbdata, flags);
		distributedReceiver.produce(req);
		return null;
	}

	public int cancel(DistributedResolver clnt, ResolverDNS.Client caller)
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
		while ((req = distributedReceiver.consume()) != null) {
			issueRequest(req);
		}
	}

	@Override
	public void dnsResolved(Dispatcher d, ResolverAnswer answer, Object cbdata)
	{
		Request req = (Request)cbdata;
		try {
			requestResolved(req, answer);
		} catch (Exception ex) {
			d.getLogger().log(LEVEL.ERR, ex, true, LOGLABEL+": Failed on answer="+answer+" for client-dispatcher="+req.issuer.getDispatcher().getName());
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
		req.issuer.issueResponse(req);
	}
}