/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.grey.base.utils.ByteArrayRef;
import com.grey.naf.BufferGenerator;
import com.grey.naf.reactor.Dispatcher;

class TransportUDP
	extends com.grey.naf.reactor.CM_UDP
{
	private final ServerDNS qryh;

	@Override
	public String getName() {return "DNS-Server-UDP";}

	public TransportUDP(Dispatcher d, ServerDNS qh, InetAddress iface, int port) throws java.io.IOException {
		super(d, new InetSocketAddress(iface, port), new BufferGenerator(ServerDNS.PKTSIZ_UDP, ServerDNS.PKTSIZ_UDP, ServerDNS.DIRECTNIOBUFS, null), ServerDNS.UDPSOCKBUFSIZ);
		qryh = qh;
	}

	@Override
	protected void ioReceived(ByteArrayRef rcvdata, InetSocketAddress remaddr) throws java.io.IOException {
		qryh.queryReceived(rcvdata, remaddr, null);
	}

	public void sendResponse(java.nio.ByteBuffer buf, InetSocketAddress remaddr) throws java.io.IOException {
		transmit(buf, remaddr);
	}
}
