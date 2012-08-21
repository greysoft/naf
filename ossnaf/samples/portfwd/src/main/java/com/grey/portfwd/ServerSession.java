/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

public class ServerSession
	extends com.grey.naf.reactor.ChannelMonitor
{
	private final Relay relay;
	private com.grey.base.utils.TSAP svcaddr;

	public com.grey.base.utils.TSAP getServerAddress() {return svcaddr;}

	public ServerSession(com.grey.naf.reactor.Dispatcher d, Relay r, com.grey.naf.BufferSpec bufspec)
	{
		super(d);
		relay = r;
		chanreader = new com.grey.naf.reactor.IOExecReader(bufspec);
		chanwriter = new com.grey.naf.reactor.IOExecWriter(bufspec);
	}

	public void connect(com.grey.base.utils.TSAP addr) throws com.grey.base.FaultException, java.io.IOException
	{
		svcaddr = addr;
		connect(svcaddr.sockaddr);
	}

	@Override
	protected void connected(boolean success, Throwable exconn) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!success) {
			dsptch.logger.debug("Failed to connect to "+svcaddr.sockaddr+" - "+com.grey.base.GreyException.summary(exconn));
			ioDisconnected();
			return;
		}
		chanreader.receive(0, true);
		relay.serverConnected();
	}

	@Override
	protected void ioDisconnected()
	{
		disconnect();
		relay.serverDisconnected();
	}

	@Override
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data) throws java.io.IOException
	{
		relay.client.transmit(data);
	}

	public void transmit(com.grey.base.utils.ArrayRef<byte[]> data) throws java.io.IOException
	{
		chanwriter.transmit(data.ar_buf, data.ar_off, data.ar_len);
	}
}
