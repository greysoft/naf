/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver;

import com.grey.base.utils.ByteArrayRef;

// We don't bother implementing ioDisconnected() as there's nothing sensible we could do with it
// and the fact all our sends will noisily fail is enough.
class EndPointUDP
	extends com.grey.naf.reactor.CM_UDP
{
	private final String name;
	private final ResolverService rslvr;

	@Override
	public String getName() {return name;}

	public EndPointUDP(String name, ResolverService r, com.grey.naf.BufferSpec bufspec, int sockbufsiz) throws java.io.IOException {
		super(r.getDispatcher(), null, bufspec, sockbufsiz);
		this.name = name;
		rslvr = r;
	}

	@Override
	protected void ioReceived(ByteArrayRef rcvdata, java.net.InetSocketAddress remaddr) {
		rslvr.stats_udprcv++;
		rslvr.handleResponse(rcvdata, null, remaddr);
	}

	public void send(java.nio.ByteBuffer buf, java.net.InetSocketAddress remaddr) throws java.io.IOException {
		rslvr.stats_udpxmt++;
		transmit(buf, remaddr);
	}
}
