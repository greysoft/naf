/*
 * Copyright 2015-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

import com.grey.base.utils.ByteArrayRef;
import com.grey.naf.dns.PacketDNS;

class TransportTCP
	extends com.grey.naf.reactor.CM_Server
{
	public static final class ServerFactory
		implements com.grey.naf.reactor.ConcurrentListener.ServerFactory
	{
		private final com.grey.naf.reactor.CM_Listener lstnr;
		private final com.grey.naf.BufferSpec bufspec;

		@Override
		public TransportTCP factory_create() {return new TransportTCP(lstnr, bufspec);}
		@Override
		public Class<TransportTCP> getServerClass() {return TransportTCP.class;}
		@Override
		public void shutdown() {}

		public ServerFactory(com.grey.naf.reactor.CM_Listener l, com.grey.base.config.XmlConfig cfg) {
			lstnr = l;
			//we only receive queries, so TCP BufferSpec receive-size need not be expanded
			bufspec = new com.grey.naf.BufferSpec(ServerDNS.PKTSIZ_UDP, ServerDNS.PKTSIZ_TCP, ServerDNS.DIRECTNIOBUFS);
		}
	}
	

	private boolean have_rsplen;

	TransportTCP(com.grey.naf.reactor.CM_Listener lstnr, com.grey.naf.BufferSpec bufspec) {
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