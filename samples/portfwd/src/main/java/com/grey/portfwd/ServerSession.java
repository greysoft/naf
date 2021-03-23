/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.base.utils.ByteArrayRef;
import com.grey.logging.Logger;
import com.grey.naf.errors.NAFException;

public class ServerSession
	extends com.grey.naf.reactor.CM_Client
{
	private final Relay relay;
	private com.grey.base.utils.TSAP svcaddr;

	public com.grey.base.utils.TSAP getServerAddress() {return svcaddr;}

	public ServerSession(com.grey.naf.reactor.Dispatcher d, Relay r, com.grey.naf.BufferGenerator bufspec)
	{
		super(d, bufspec, bufspec);
		relay = r;
	}

	public void connect(com.grey.base.utils.TSAP addr) throws java.io.IOException
	{
		svcaddr = addr;
		initChannelMonitor();
		connect(svcaddr.sockaddr);
	}

	@Override
	protected void connected(boolean success, CharSequence diag, Throwable exconn) throws java.io.IOException
	{
		if (!success) {
			if (NAFException.isError(exconn)) {
				getLogger().log(Logger.LEVEL.WARN, exconn, true, "Connect failed on "+svcaddr.sockaddr);
			} else {
				getLogger().info("Connect failed on "+svcaddr.sockaddr+" - "+exconn);
			}
			ioDisconnected(diag);
			return;
		}
		getLogger().trace("Connected to "+getRemoteIP()+":"+getRemotePort()+" on behalf of "
				+relay.client.getRemoteIP()+":"+relay.client.getRemotePort()+" on service-port="+relay.client.getLocalPort());
		getReader().receive(0);
		relay.serverConnected();
	}

	@Override
	protected void ioDisconnected(CharSequence diag)
	{
		disconnect();
		relay.serverDisconnected();
	}

	@Override
	public void ioReceived(ByteArrayRef data) throws java.io.IOException
	{
		relay.client.transmit(data);
	}

	public void transmit(ByteArrayRef data) throws java.io.IOException
	{
		getWriter().transmit(data);
	}
}
