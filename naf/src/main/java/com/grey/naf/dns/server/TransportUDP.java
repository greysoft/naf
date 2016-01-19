/*
 * Copyright 2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

final class TransportUDP
	extends com.grey.naf.reactor.CM_UDP
{
	private final java.nio.channels.DatagramChannel udpchan;
	private final Server qryh;

	public TransportUDP(com.grey.naf.reactor.Dispatcher d, Server qh, java.net.InetAddress iface, int port)
		throws java.io.IOException
	{
		super(d, new com.grey.naf.BufferSpec(Server.PKTSIZ_UDP, Server.PKTSIZ_UDP, Server.DIRECTNIOBUFS));
		java.net.InetSocketAddress addr = new java.net.InetSocketAddress(iface, port);
		int sockbufsiz = Server.UDPSOCKBUFSIZ;
		qryh = qh;
		udpchan = java.nio.channels.DatagramChannel.open();
		udpchan.socket().setReceiveBufferSize(sockbufsiz);
		udpchan.socket().setSendBufferSize(sockbufsiz);
		udpchan.socket().bind(addr);
		registerConnectionlessChannel(udpchan, true);
		dsptch.logger.info("DNS-Server bound to local UDP port="+udpchan.socket().getLocalSocketAddress()
				+" - sockbuf="+com.grey.base.utils.ByteOps.expandByteSize(sockbufsiz, null, false));
	}

	public void start() throws java.io.IOException
	{
		udpreader.receive();
	}

	public boolean stop() {
		return disconnect();
	}

	@Override
	protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata, java.net.InetSocketAddress remote_addr)
			throws java.io.IOException
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
