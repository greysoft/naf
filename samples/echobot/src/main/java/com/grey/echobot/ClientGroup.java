/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import org.slf4j.LoggerFactory;

// This represents the set of echo-clients running on each Dispatcher, all pointing at the same server.
public class ClientGroup
{
	private static final org.slf4j.Logger Logger = LoggerFactory.getLogger(ClientGroup.class);

	private final App app;
	public final com.grey.naf.reactor.Dispatcher dsptch;
	public final com.grey.base.utils.TSAP tsap;
	public final int msgcnt;
	public final int echosize;
	public final boolean verify;

	// These need not be synchronised, since they are set before Dispatcher thread starts, and then read back by App class
	// after joining the terminated Dispatcher thread, which is a synchronising event.
	public final java.util.ArrayList<Long> durations = new java.util.ArrayList<Long>();  //session times, in nano-seconds
	public final java.util.ArrayList<Long> latencies = new java.util.ArrayList<Long>();  //echo times, in nano-seconds
	public int failcnt;
	private int clientcnt;

	public ClientGroup(App app, com.grey.naf.reactor.Dispatcher d, boolean udpmode, com.grey.base.utils.TSAP remote_addr,
			int size, com.grey.naf.BufferSpec bufspec, byte[] msgbuf, int mcnt, int sockbufsiz, boolean verify)
			throws java.io.IOException
	{
		Logger.info("Creating client-group with mode="+(udpmode?"UDP":"TCP")+" and size="+size);
		this.app = app;
		dsptch = d;
		tsap = remote_addr;
		msgcnt = mcnt;
		echosize = msgbuf.length;
		this.verify = verify;

		for (int idx = 0; idx != size; idx++) {
			clientcnt++;
			if (udpmode) {
				ClientUDP c = new ClientUDP("EchoBot-Client-UDP-"+(idx+1), clientcnt, this, bufspec, msgbuf, sockbufsiz);
				dsptch.loadRunnable(c);
			} else {
				ClientTCP c = new ClientTCP("EchoBot-Client-TCP-"+(idx+1), clientcnt, this, bufspec, msgbuf);
				dsptch.loadRunnable(c);
			}
		}
	}

	public void terminated(boolean success, long duration)
	{
		Logger.info("Client-group terminated with success="+success+", clients="+clientcnt);
		if (!success) {
			failcnt++;
		} else {
			durations.add(duration);
		}
		clientcnt--;
		if (clientcnt == 0) app.terminated(this);
	}
}
