/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

// This represents the set of echo-clients running on a particular server
public class ClientGroup
{
	private final App app;
	public final com.grey.naf.reactor.Dispatcher dsptch;
	public final com.grey.base.utils.TSAP tsap;
	public final byte[] msgbuf;
	public final int msgcnt;
	public final boolean verify;

	public final java.util.ArrayList<Long> durations = new java.util.ArrayList<Long>();  //session times, in nano-seconds
	public int failcnt;
	private int clientcnt;

	public ClientGroup(App app, com.grey.naf.reactor.Dispatcher d, boolean udpmode, com.grey.base.utils.TSAP remote_addr,
			int size, com.grey.naf.BufferSpec bufspec, byte[] mbuf, int mcnt, int sockbufsiz, boolean verify)
			throws com.grey.base.FaultException, java.io.IOException
	{
		this.app = app;
		dsptch = d;
		tsap = remote_addr;
		msgbuf = mbuf;
		msgcnt = mcnt;
		this.verify = verify;

		for (int idx = 0; idx != size; idx++) {
			clientcnt++;
			if (udpmode) {
				new ClientUDP(clientcnt, this, bufspec, sockbufsiz);
			} else {
				new ClientTCP(clientcnt, this, bufspec);
			}
		}
	}

	public void terminated(Client c, boolean success, long duration) throws java.io.IOException
	{
		if (!success) {
			failcnt++;
		} else {
			durations.add(duration);
		}
		clientcnt--;
		if (clientcnt == 0) app.terminated(this);
	}
}
