/*
 * Copyright 2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

import com.grey.naf.dns.Packet;

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
			bufspec = new com.grey.naf.BufferSpec(Server.PKTSIZ_UDP, Server.PKTSIZ_TCP, Server.DIRECTNIOBUFS);
		}
	}
	

	private boolean have_rsplen;

	TransportTCP(com.grey.naf.reactor.CM_Listener lstnr, com.grey.naf.BufferSpec bufspec) {
		super(lstnr, bufspec, bufspec);
	}

	@Override
	protected void connected() throws com.grey.base.FaultException, java.io.IOException
	{
		have_rsplen = false;
		chanreader.receive(Packet.TCPMSGLENSIZ);
	}

	@Override
	protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!have_rsplen) {
			have_rsplen = true;
			int len = com.grey.base.utils.ByteOps.decodeInt(rcvdata.ar_buf, rcvdata.ar_off, 2);
			chanreader.receive(len);
			return;
		}
		Server qryh = (Server)lstnr.controller;
		qryh.queryReceived(rcvdata, getRemoteAddress(), this);
	}

	public void sendResponse(java.nio.ByteBuffer buf) throws java.io.IOException
	{
		try {
			chanwriter.transmit(buf);
		} finally {
			disconnect();
		}
	}
}