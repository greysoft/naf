/*
 * Copyright 2015-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

import com.grey.base.utils.ByteArrayRef;
import com.grey.naf.BufferGenerator;
import com.grey.naf.dns.resolver.engine.PacketDNS;

class TransportTCP
	extends com.grey.naf.reactor.CM_Server
{
	public static final class ServerFactory
		implements com.grey.naf.reactor.CM_Listener.ServerFactory
	{
		private final com.grey.naf.reactor.CM_Listener lstnr;
		private final com.grey.naf.BufferGenerator bufspec;
		@Override
		public TransportTCP createServer() {return new TransportTCP(lstnr, bufspec);}

		public ServerFactory(com.grey.naf.reactor.CM_Listener l, Object cfg) {
			lstnr = l;
			//we only receive queries, so TCP BufferSpec receive-size need not be expanded
			BufferGenerator.BufferConfig bufcfg = new BufferGenerator.BufferConfig(ServerDNS.PKTSIZ_UDP, true, ServerDNS.DIRECTNIOBUFS, null);
			bufspec = new BufferGenerator(bufcfg);
		}
	}
	

	private boolean have_rsplen;

	TransportTCP(com.grey.naf.reactor.CM_Listener lstnr, com.grey.naf.BufferGenerator bufspec) {
		super(lstnr, bufspec, bufspec);
	}

	@Override
	protected void connected() throws java.io.IOException
	{
		have_rsplen = false;
		getReader().receive(PacketDNS.TCPMSGLENSIZ);
	}

	@Override
	protected void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException
	{
		if (!have_rsplen) {
			have_rsplen = true;
			int len = com.grey.base.utils.ByteOps.decodeInt(rcvdata.buffer(), rcvdata.offset(), 2);
			getReader().receive(len);
			return;
		}
		ServerDNS qryh = (ServerDNS)getListener().getController();
		qryh.queryReceived(rcvdata, getRemoteAddress(), this);
	}

	public void sendResponse(java.nio.ByteBuffer buf) throws java.io.IOException
	{
		try {
			getWriter().transmit(buf);
		} finally {
			disconnect();
		}
	}
}