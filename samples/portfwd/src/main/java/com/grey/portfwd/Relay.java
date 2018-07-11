/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.logging.Logger;
import com.grey.naf.errors.NAFException;

public class Relay
{
	public final ClientSession client;
	public final ServerSession server;
	private final com.grey.portfwd.balance.Balancer loadbalancer;
	private final Task task;

	private long start_time;

	public Relay(ClientSession c, com.grey.portfwd.balance.Balancer b, com.grey.naf.BufferSpec bufspec)
	{
		task = Task.class.cast(c.getListener().getController());
		loadbalancer = b;
		client = c;
		server = new ServerSession(c.getDispatcher(), this, bufspec);
	}

	public void clientConnected()
	{
		start_time = client.getSystemTime();
		com.grey.base.utils.TSAP remote_addr = loadbalancer.selectService(client);
		try {
			server.connect(remote_addr);
		} catch (Exception ex) {
			if (NAFException.isError(ex)) {
				client.getLogger().log(Logger.LEVEL.WARN, ex, true, "Failed to connect to "+remote_addr.sockaddr);
			} else {
				client.getLogger().trace("Failed to connect to "+remote_addr.sockaddr+" - "+ex);
			}
			serverDisconnected();
			return;
		}
		task.connectionStarted(this);
	}

	public void clientDisconnected()
	{
		server.disconnect();
		connectionEnded();
	}

	public void serverConnected()  throws java.io.IOException
	{
		client.initiateIO();
	}

	public void serverDisconnected()
	{
		connectionEnded();
		client.endConnection(); //this object is inactive after this call returns
	}

	private void connectionEnded()
	{
		task.connectionEnded(this);
	}

	public void dumpState(StringBuilder sb)
	{
		com.grey.base.utils.TSAP ctsap = new com.grey.base.utils.TSAP();
		java.net.Socket csock = java.nio.channels.SocketChannel.class.cast(client.getChannel()).socket();
		com.grey.base.utils.TSAP.get(csock, false, ctsap, false);
		sb.append(ctsap.sockaddr).append(" => ").append(server.getServerAddress()).append(" since ");
		com.grey.base.utils.TimeOps.makeTimeISO8601(start_time, sb, true, false, true);
		if (!server.isConnected()) sb.append(" ... Connecting");
	}
}
