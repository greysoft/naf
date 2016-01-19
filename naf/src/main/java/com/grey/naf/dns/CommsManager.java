/*
 * Copyright 2014-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.utils.TSAP;

final class CommsManager
	implements com.grey.naf.EntityReaper
{
	private final java.net.InetSocketAddress[] srvaddr;
	private final EndPointUDP[] udplocal;
	private final com.grey.base.collections.ObjectWell<EndPointTCP> tcpstore;
	private int next_server;
	private int next_udplocal;

	public CommsManager(ResolverService rslvr) throws java.io.IOException
	{
		Config cfg = rslvr.config;
		//we only transmit queries, so TCP BufferSpec transmit-size need not be expanded to the TCP limit
		com.grey.naf.BufferSpec bufspec_udp = new com.grey.naf.BufferSpec(Config.PKTSIZ_UDP, Config.PKTSIZ_UDP, Config.DIRECTNIOBUFS);
		com.grey.naf.BufferSpec bufspec_tcp = new com.grey.naf.BufferSpec(Config.PKTSIZ_TCP, Config.PKTSIZ_UDP, Config.DIRECTNIOBUFS);

		if (cfg.dns_localservers == null) {
			srvaddr = null;
		} else {
			srvaddr = new java.net.InetSocketAddress[cfg.dns_localservers.length];
			for (int idx = 0; idx != srvaddr.length; idx++) {
				//if the server name include a port spec (ie. host:port) allow that to override dnscfg.dns_port
				TSAP tsap = TSAP.build(cfg.dns_localservers[idx], cfg.dns_port, false);
				srvaddr[idx] = rslvr.cachemgr.createServerTSAP(tsap.ip, tsap.port);
			}
		}

		if (cfg.udp_sendersockets == 0) {
			udplocal = null;
		} else {
			udplocal = new EndPointUDP[cfg.udp_sendersockets];
			for (int idx = 0; idx != cfg.udp_sendersockets; idx++) {
				udplocal[idx] = new EndPointUDP(rslvr, bufspec_udp, Config.UDPSOCKBUFSIZ);
			}
		}
		EndPointTCP.Factory fact = new EndPointTCP.Factory(rslvr.dsptch, bufspec_tcp);
		tcpstore = new com.grey.base.collections.ObjectWell<EndPointTCP>(fact, "DNS_"+rslvr.dsptch.name);
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
