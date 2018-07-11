/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import com.grey.base.utils.ByteArrayRef;

/*
 * Obviously you wouldn't use NIO to build the fastest possible single-socket echo-server,
 * but as per the rationale for NIO, this illustrates how to integrate a UDP socket into a single
 * Dispatcher thread handling potentially thousands of connections.
 */
public class ServerUDP
	extends com.grey.naf.reactor.CM_UDP
{
	private final App app;
	private final java.nio.channels.DatagramChannel udpchan;
	private java.nio.ByteBuffer niobuf;

	public ServerUDP(App app, com.grey.naf.reactor.Dispatcher d, com.grey.base.utils.TSAP tsap, com.grey.naf.BufferSpec bufspec,
			int sockbufsiz) throws java.io.IOException
	{
		super(d, bufspec);
		this.app = app;

		udpchan = java.nio.channels.DatagramChannel.open();
		java.net.DatagramSocket sock = udpchan.socket();
		sock.setReceiveBufferSize(sockbufsiz);
		sock.setSendBufferSize(sockbufsiz);
		sock.bind(tsap.sockaddr);

		registerConnectionlessChannel(udpchan, true);
	}
	
	public void start() throws java.io.IOException {
		getReader().receive();
	}

	@Override
	public void ioReceived(ByteArrayRef data, java.net.InetSocketAddress remaddr) throws java.io.IOException
	{
		niobuf = com.grey.base.utils.NIOBuffers.encode(data.buffer(), data.offset(), data.size(), niobuf, app.sbufspec.directbufs);
		int nbytes = udpchan.send(niobuf, remaddr);
		if (nbytes != data.size()) {
			getLogger().error("Server send failed - nbytes="+nbytes+"/"+data.size());
		}
	}
}
