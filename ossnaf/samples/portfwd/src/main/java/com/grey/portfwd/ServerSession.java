/*
 * Copyright 2012-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.logging.Logger;

public class ServerSession
	extends com.grey.naf.reactor.CM_Client
{
	private final Relay relay;
	private com.grey.base.utils.TSAP svcaddr;

	public com.grey.base.utils.TSAP getServerAddress() {return svcaddr;}

	public ServerSession(com.grey.naf.reactor.Dispatcher d, Relay r, com.grey.naf.BufferSpec bufspec)
	{
		super(d, bufspec, bufspec);
		relay = r;
	}

	public void connect(com.grey.base.utils.TSAP addr) throws com.grey.base.FaultException, java.io.IOException
	{
		svcaddr = addr;
		initChannelMonitor();
		connect(svcaddr.sockaddr);
	}

	@Override
	protected void connected(boolean success, CharSequence diag, Throwable exconn) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!success) {
			if (exconn instanceof RuntimeException) {
				dsptch.logger.log(Logger.LEVEL.WARN, exconn, true, "Connect failed on "+svcaddr.sockaddr);
			} else {
				dsptch.logger.info("Connect failed on "+svcaddr.sockaddr+" - "+exconn);
			}
			ioDisconnected(diag);
			return;
		}
		dsptch.logger.trace("Connected to "+getRemoteIP()+":"+getRemotePort()+" on behalf of "
				+relay.client.getRemoteIP()+":"+relay.client.getRemotePort()+" on service-port="+relay.client.getLocalPort());
		chanreader.receive(0);
		relay.serverConnected();
	}

	@Override
	protected void ioDisconnected(CharSequence diag)
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
