/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import org.slf4j.LoggerFactory;

import com.grey.base.utils.ByteArrayRef;

/*
 * Obviously you wouldn't use NIO to build the fastest possible single-socket echo-server,
 * but as per the rationale for NIO, this illustrates how to integrate a UDP socket into a single
 * Dispatcher thread handling potentially thousands of connections.
 */
public class ServerUDP
	extends com.grey.naf.reactor.CM_UDP
{
	private static final org.slf4j.Logger Logger = LoggerFactory.getLogger(ServerUDP.class);

	private final App app;
	private java.nio.ByteBuffer niobuf;

	@Override
	public String getName() {return "EchoBot-Server-UDP";}

	public ServerUDP(App app, com.grey.naf.reactor.Dispatcher d, com.grey.base.utils.TSAP tsap, com.grey.naf.BufferSpec bufspec, int sockbufsiz)
			throws java.io.IOException {
		super(d, tsap.sockaddr, bufspec, sockbufsiz);
		this.app = app;
		Logger.info("Created UDP server="+this);
	}

	@Override
	public void ioReceived(ByteArrayRef data, java.net.InetSocketAddress remaddr) throws java.io.IOException {
		Logger.info("Received data="+data.size()+" from remote="+remaddr);
		niobuf = com.grey.base.utils.NIOBuffers.encode(data.buffer(), data.offset(), data.size(), niobuf, app.sbufspec.directbufs);
		int nbytes = transmit(niobuf, remaddr);
		if (nbytes != data.size()) {//belt and braces - transmit() has already verified nbytes vs the ByteBuffer encoding of data
			getLogger().error("Server send failed - nbytes="+nbytes+"/"+data.size());
		}
	}
}
