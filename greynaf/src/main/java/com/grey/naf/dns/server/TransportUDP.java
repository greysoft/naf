/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

import com.grey.base.utils.ByteArrayRef;

class TransportUDP
	extends com.grey.naf.reactor.CM_UDP
{
	private final java.nio.channels.DatagramChannel udpchan;
	private final ServerDNS qryh;

	public TransportUDP(com.grey.naf.reactor.Dispatcher d, ServerDNS qh, java.net.InetAddress iface, int port)
		throws java.io.IOException
	{
		super(d, new com.grey.naf.BufferSpec(ServerDNS.PKTSIZ_UDP, ServerDNS.PKTSIZ_UDP, ServerDNS.DIRECTNIOBUFS, null));
		java.net.InetSocketAddress addr = new java.net.InetSocketAddress(iface, port);
		int sockbufsiz = ServerDNS.UDPSOCKBUFSIZ;
		qryh = qh;
		udpchan = java.nio.channels.DatagramChannel.open();
		udpchan.socket().setReceiveBufferSize(sockbufsiz);
		udpchan.socket().setSendBufferSize(sockbufsiz);
		udpchan.socket().bind(addr);
		registerConnectionlessChannel(udpchan, true);
		getLogger().info("DNS-Server bound to local UDP port="+udpchan.socket().getLocalSocketAddress()+(port==0?"/dynamic":"")
				+" - sockbuf="+com.grey.base.utils.ByteOps.expandByteSize(sockbufsiz, null, false));
	}

	public void start() throws java.io.IOException
	{
		getReader().receive();
	}

	public boolean stop() {
		return disconnect();
	}

	@Override
	protected void ioReceived(ByteArrayRef rcvdata, java.net.InetSocketAddress remote_addr) throws java.io.IOException
	{
		qryh.queryReceived(rcvdata, remote_addr, null);
	}

	public void sendResponse(java.nio.ByteBuffer buf, java.net.InetSocketAddress remote_addr) throws java.io.IOException
	{
		int len = buf.remaining();
		int nbytes = udpchan.send(buf, remote_addr);
		if (nbytes != len) throw new java.io.IOException("Datagram write="+nbytes+"/"+len);
	}
}
