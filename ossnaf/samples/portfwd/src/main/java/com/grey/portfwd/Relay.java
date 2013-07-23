/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

public class Relay
{
	public final ClientSession client;
	public final ServerSession server;
	private final com.grey.portfwd.balance.Balancer loadbalancer;
	private final Task task;

	private long start_time;

	public Relay(ClientSession c, com.grey.portfwd.balance.Balancer b, com.grey.naf.BufferSpec bufspec)
	{
		task = Task.class.cast(c.lstnr.controller);
		loadbalancer = b;
		client = c;
		server = new ServerSession(c.dsptch, this, bufspec);
	}

	public void clientConnected() throws com.grey.base.FaultException, java.io.IOException
	{
		start_time = client.dsptch.systime();
		com.grey.base.utils.TSAP remote_addr = loadbalancer.selectService(client);
		try {
			server.connect(remote_addr);
		} catch (Exception ex) {
			client.dsptch.logger.trace("Failed to connect to "+remote_addr.sockaddr+" - "+com.grey.base.GreyException.summary(ex));
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

	public void serverConnected()  throws com.grey.base.FaultException, java.io.IOException
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
		java.net.Socket csock = java.nio.channels.SocketChannel.class.cast(client.iochan).socket();
		com.grey.base.utils.TSAP.get(csock, false, ctsap, false);
		sb.append(ctsap.sockaddr).append(" => ").append(server.getServerAddress()).append(" since ");
		com.grey.base.utils.TimeOps.makeTimeISO8601(start_time, sb, true, false, true);
		if (!server.isConnected()) sb.append(" ... Connecting");
	}
}
