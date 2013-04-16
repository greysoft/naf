/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

/*
 * Obviously you wouldn't use NIO to build the fastest possible single-socket echo-server,
 * but as per the rationale for NIO, this illustrates how to integrate a UDP socket into a single
 * Dispatcher thread handling potentially thousands of connections.
 */
public class ServerUDP
	extends com.grey.naf.reactor.ChannelMonitor
{
	private final App app;
	private final java.nio.channels.DatagramChannel udpchan;
	private java.nio.ByteBuffer niobuf;

	public ServerUDP(App app, com.grey.naf.reactor.Dispatcher d, com.grey.base.utils.TSAP tsap, com.grey.naf.BufferSpec bufspec,
			int sockbufsiz) throws com.grey.base.FaultException, java.io.IOException
	{
		super(d);
		this.app = app;

		udpchan = java.nio.channels.DatagramChannel.open();
		java.net.DatagramSocket sock = udpchan.socket();
		sock.setReceiveBufferSize(sockbufsiz);
		sock.setSendBufferSize(sockbufsiz);
		sock.bind(tsap.sockaddr);

		chanreader = new com.grey.naf.reactor.IOExecReader(bufspec);
		initChannel(udpchan, true, false);
		chanreader.receive(0, true);
	}

	@Override
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data, java.net.InetSocketAddress remaddr)
			throws com.grey.base.FaultException, java.io.IOException
	{
		niobuf = com.grey.base.utils.NIOBuffers.ensureCapacity(niobuf, data.ar_len, app.sbufspec.directbufs);
		com.grey.base.utils.NIOBuffers.encode(data.ar_buf, data.ar_off, data.ar_len, niobuf, false);
		int nbytes = udpchan.send(niobuf, remaddr);
		if (nbytes != data.ar_len) {
			dsptch.logger.error("Server send failed - nbytes="+nbytes+"/"+data.ar_len);
		}
	}
}
