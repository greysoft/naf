/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.engine;

import com.grey.base.collections.ObjectPool;
import com.grey.base.utils.TSAP;
import com.grey.naf.dns.resolver.ResolverConfig;

class CommsManager
	implements com.grey.naf.EntityReaper
{
	private final java.net.InetSocketAddress[] localNameServers; //local name servers to which we can issue queries
	private final EndPointUDP[] udpLocal; //the UDP sockets on which we issue our outgoing queries
	private final ObjectPool<EndPointTCP> tcpstore;
	private int nextServer; //allows us to round-robin through localNameServers
	private int nextUDP; //allows us to round-robin through udpLocal

	public CommsManager(ResolverService rslvr) throws java.io.IOException
	{
		ResolverConfig cfg = rslvr.getConfig();
		//we only transmit queries, so TCP BufferSpec transmit-size need not be expanded to the TCP limit
		com.grey.naf.BufferGenerator bufspec_udp = new com.grey.naf.BufferGenerator(ResolverConfig.PKTSIZ_UDP, ResolverConfig.PKTSIZ_UDP, ResolverConfig.DIRECTNIOBUFS, null);
		com.grey.naf.BufferGenerator bufspec_tcp = new com.grey.naf.BufferGenerator(ResolverConfig.PKTSIZ_TCP, ResolverConfig.PKTSIZ_UDP, ResolverConfig.DIRECTNIOBUFS, null);

		if (!cfg.isRecursive()) {
			localNameServers = null;
		} else {
			localNameServers = new java.net.InetSocketAddress[rslvr.getLocalNameServers().length];
			for (int idx = 0; idx != localNameServers.length; idx++) {
				//if the server name include a port spec (ie. host:port) allow that to override the default config DNS port
				TSAP tsap = TSAP.build(rslvr.getLocalNameServers()[idx], cfg.getDnsPort(), false);
				localNameServers[idx] = rslvr.getCacheManager().createServerTSAP(tsap.ip, tsap.port);
			}
		}

		if (cfg.getSenderSocketsUDP() == 0) {
			udpLocal = null;
		} else {
			udpLocal = new EndPointUDP[cfg.getSenderSocketsUDP()];
			for (int idx = 0; idx != cfg.getSenderSocketsUDP(); idx++) {
				udpLocal[idx] = new EndPointUDP("DNS-resolver-udp-"+(idx+1), rslvr, bufspec_udp, ResolverConfig.UDPSOCKBUFSIZ);
			}
		}
		tcpstore = new ObjectPool<>(() -> new EndPointTCP(rslvr.getDispatcher(), bufspec_tcp));
	}

	public void start() throws java.io.IOException
	{
		int cnt = (udpLocal == null ? 0 : udpLocal.length);
		for (int idx = 0; idx != cnt; idx++) {
			udpLocal[idx].startDispatcherRunnable();
		}
	}

	public void stop()
	{
		int cnt = (udpLocal == null ? 0 : udpLocal.length);
		for (int idx = 0; idx != cnt; idx++) {
			udpLocal[idx].stopDispatcherRunnable();
		}
	}

	// this is only called in recursive mode, so localNameServers is guaranteed non-null
	public java.net.InetSocketAddress nextLocalNameServer()
	{
		java.net.InetSocketAddress next = localNameServers[nextServer++];
		if (nextServer == localNameServers.length) nextServer = 0;
		return next;
	}

	public EndPointUDP nextSendPort()
	{
		if (udpLocal == null) return null;
		EndPointUDP next = udpLocal[nextUDP++];
		if (nextUDP == udpLocal.length) nextUDP = 0;
		return next;
	}

	public EndPointTCP allocateTCP() {
		EndPointTCP cm = tcpstore.extract();
		cm.setReaper(this);
		return cm;
	}

	@Override
	public void entityStopped(Object obj) {
		tcpstore.store((EndPointTCP)obj);
	}
}
