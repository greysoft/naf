/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.IP;

class EndPointTCP
	extends com.grey.naf.reactor.CM_Client
{
	public static final class Factory
		implements com.grey.base.collections.ObjectWell.ObjectFactory
	{
		private final com.grey.naf.reactor.Dispatcher dsptch;
		private final com.grey.naf.BufferSpec bufspec;

		public Factory(com.grey.naf.reactor.Dispatcher d, com.grey.naf.BufferSpec spec)
		{
			dsptch = d;
			bufspec = spec;
		}

		@Override
		public EndPointTCP factory_create()
		{
			return new EndPointTCP(dsptch, bufspec);
		}
	}

	private QueryHandle qryh;
	private java.net.InetSocketAddress srvaddr;
	private boolean have_rsplen;

	EndPointTCP(com.grey.naf.reactor.Dispatcher d, com.grey.naf.BufferSpec spec) {
		super(d, spec, spec);
	}

	public void connect(QueryHandle qh, java.net.InetSocketAddress addr) throws java.io.IOException
	{
		qryh = qh;
		srvaddr = addr;
		have_rsplen = false;
		qryh.rslvr.stats_tcpconns++;
		initChannelMonitor();
		connect(srvaddr);
	}

	@Override
	protected void connected(boolean success, CharSequence diag, Throwable ex)
			throws java.io.IOException
	{
		if (!success) {
			if (getLogger().isActive(LOGLEVEL_CNX)) {
				getLogger().log(LOGLEVEL_CNX, ex, false, "DNS-Resolver: TCP connect failed on "+srvaddr+(diag==null?"":" - "+diag));
			}
			qryh.rslvr.stats_tcpfail++;
			qryh.endRequest(ResolverAnswer.STATUS.TIMEOUT); //treat in same way as non-responding UDP server
			return;
		}
		int server_ip = IP.convertIP(getRemoteIP());
		if (qryh.repeatQuery(server_ip) != null) return; //the request has completed
		getReader().receive(PacketDNS.TCPMSGLENSIZ); // now wait for response
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
		qryh.handleResponseTCP(rcvdata);
	}

	@Override
	protected void ioDisconnected(CharSequence diagnostic)
	{
		if (getLogger().isActive(LOGLEVEL_CNX)) {
			getLogger().log(LOGLEVEL_CNX, "DNS-Resolver: unexpected TCP disconnection - "+diagnostic+" - "+this);
		}
		qryh.rslvr.stats_tcpfail++;
		qryh.endRequest(ResolverAnswer.STATUS.ERROR);
	}

	@Override
	public void eventError(Throwable ex)
	{
		//already logged by Dispatcher
		qryh.endRequest(ResolverAnswer.STATUS.ERROR);
	}

	public void send(java.nio.ByteBuffer buf) throws java.io.IOException
	{
		getWriter().transmit(buf);
	}
}