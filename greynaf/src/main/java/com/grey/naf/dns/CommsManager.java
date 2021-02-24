/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.utils.TSAP;

class CommsManager
	implements com.grey.naf.EntityReaper
{
	private final java.net.InetSocketAddress[] srvaddr;
	private final EndPointUDP[] udplocal;
	private final com.grey.base.collections.ObjectWell<EndPointTCP> tcpstore;
	private int next_server;
	private int next_udplocal;

	public CommsManager(ResolverService rslvr) throws java.io.IOException
	{
		ResolverConfig cfg = rslvr.getConfig();
		//we only transmit queries, so TCP BufferSpec transmit-size need not be expanded to the TCP limit
		com.grey.naf.BufferSpec bufspec_udp = new com.grey.naf.BufferSpec(ResolverConfig.PKTSIZ_UDP, ResolverConfig.PKTSIZ_UDP, ResolverConfig.DIRECTNIOBUFS);
		com.grey.naf.BufferSpec bufspec_tcp = new com.grey.naf.BufferSpec(ResolverConfig.PKTSIZ_TCP, ResolverConfig.PKTSIZ_UDP, ResolverConfig.DIRECTNIOBUFS);

		if (cfg.dns_localservers == null) {
			srvaddr = null;
		} else {
			srvaddr = new java.net.InetSocketAddress[cfg.dns_localservers.length];
			for (int idx = 0; idx != srvaddr.length; idx++) {
				//if the server name include a port spec (ie. host:port) allow that to override dnscfg.dns_port
				TSAP tsap = TSAP.build(cfg.dns_localservers[idx], cfg.dns_port, false);
				srvaddr[idx] = rslvr.getCacheManager().createServerTSAP(tsap.ip, tsap.port);
			}
		}

		if (cfg.udp_sendersockets == 0) {
			udplocal = null;
		} else {
			udplocal = new EndPointUDP[cfg.udp_sendersockets];
			for (int idx = 0; idx != cfg.udp_sendersockets; idx++) {
				udplocal[idx] = new EndPointUDP(rslvr, bufspec_udp, ResolverConfig.UDPSOCKBUFSIZ);
			}
		}
		EndPointTCP.Factory fact = new EndPointTCP.Factory(rslvr.getDispatcher(), bufspec_tcp);
		tcpstore = new com.grey.base.collections.ObjectWell<>(fact, "DNS_"+rslvr.getDispatcher().getName());
	}

	public void start() throws java.io.IOException
	{
		int cnt = (udplocal == null ? 0 : udplocal.length);
		for (int idx = 0; idx != cnt; idx++) {
			udplocal[idx].start();
		}
	}

	public void stop()
	{
		int cnt = (udplocal == null ? 0 : udplocal.length);
		for (int idx = 0; idx != cnt; idx++) {
			udplocal[idx].stop();
		}
	}

	// this is only called in recursive mode, so srvaddr is guaranteed non-null
	public java.net.InetSocketAddress nextServer()
	{
		java.net.InetSocketAddress next = srvaddr[next_server++];
		if (next_server == srvaddr.length) next_server = 0;
		return next;
	}

	public EndPointUDP nextSendPort()
	{
		if (udplocal == null) return null;
		EndPointUDP next = udplocal[next_udplocal++];
		if (next_udplocal == udplocal.length) next_udplocal = 0;
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
