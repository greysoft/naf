/*
 * Copyright 2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

// We don't bother implementing ioDisconnected() as there's nothing sensible we could do with it
// and the fact all our sends will noisily fail is enough.
final class EndPointUDP
	extends com.grey.naf.reactor.CM_UDP
{
	private final java.nio.channels.DatagramChannel udpchan;
	private final ResolverService rslvr;

	public EndPointUDP(ResolverService r, com.grey.naf.BufferSpec bufspec, int sockbufsiz)
		throws java.io.IOException
	{
		super(r.dsptch, bufspec);
		rslvr = r;
		udpchan = java.nio.channels.DatagramChannel.open();
		udpchan.socket().setReceiveBufferSize(sockbufsiz);
		udpchan.socket().setSendBufferSize(sockbufsiz);
		udpchan.socket().bind(null);
		registerConnectionlessChannel(udpchan, true);
		dsptch.logger.info("DNS-Resolver bound to local UDP port="+udpchan.socket().getLocalPort()
				+" - sockbuf="+com.grey.base.utils.ByteOps.expandByteSize(sockbufsiz, null, false));
	}

	public void start() throws java.io.IOException
	{
		udpreader.receive();
	}

	public void stop()
	{
		disconnect();
	}

	@Override
	protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata, java.net.InetSocketAddress remaddr)
	{
		rslvr.stats_udprcv++;
		rslvr.handleResponse(rcvdata, null, remaddr);
	}

	public void send(java.nio.ByteBuffer buf, java.net.InetSocketAddress remaddr) throws java.io.IOException
	{
		int len = buf.remaining();
		int nbytes = udpchan.send(buf, remaddr);
		if (nbytes != len) {
			throw new java.io.IOException("Datagram write="+nbytes+"/"+len);
		}
		rslvr.stats_udpxmt++;
	}
}
