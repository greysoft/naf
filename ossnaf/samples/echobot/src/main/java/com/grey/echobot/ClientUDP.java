/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

public class ClientUDP
	extends Client
{
	private final java.nio.channels.DatagramChannel udpchan;
	private final java.nio.ByteBuffer niobuf;

	public ClientUDP(int id, ClientGroup g, com.grey.naf.BufferSpec bufspec, int sockbufsiz)
			throws com.grey.base.FaultException, java.io.IOException
	{
		super(id, g);
		niobuf = com.grey.base.utils.NIOBuffers.encode(grp.msgbuf, 0, grp.msgbuf.length, null, bufspec.directbufs);

		udpchan = java.nio.channels.DatagramChannel.open();
		udpchan.socket().setReceiveBufferSize(sockbufsiz);
		udpchan.socket().setSendBufferSize(sockbufsiz);
		udpchan.socket().bind(null);

		// We're not interested in the sender address of incoming messages, so we don't need to specify udp=true in
		// IOExecReader, but it's worth doing so anyway because the code path for UDP sockets is more streamlined.
		chanreader = new com.grey.naf.reactor.IOExecReader(bufspec, true);
		initChannel(udpchan, true, false);
		chanreader.receive(0, true);

		// kick off the network I/O
		time_start = System.nanoTime();
		send();
	}

	@Override
	protected void transmit() throws java.io.IOException
	{
		niobuf.position(0);
		niobuf.limit(grp.msgbuf.length);
		int nbytes = udpchan.send(niobuf, grp.tsap.sockaddr);
		if (nbytes != grp.msgbuf.length) {
			dsptch.logger.error(logpfx+" Send failed - nbytes="+nbytes+"/"+grp.msgbuf.length);
			completed(false);
		}
	}

	@Override
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data, java.net.SocketAddress remaddr)
			throws com.grey.base.FaultException, java.io.IOException
	{
		ioReceived(data);
	}
}
