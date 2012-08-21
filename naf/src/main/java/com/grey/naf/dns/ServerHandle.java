/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.config.SysProps;

public final class ServerHandle
	extends com.grey.naf.reactor.ChannelMonitor
{
	// Linux limit is 128K, while Windows seems to accept just about anything
	private static final int DFLT_SOCKBUF = SysProps.get("greynaf.dns.sockbuf", Packet.UDPMAXMSG * 250);

	private final ResolverService rslvr;
	private final java.nio.channels.DatagramChannel udpchan;
	private final com.grey.base.utils.TSAP srvtsap;
	private final boolean tcp_only;

	public java.net.InetSocketAddress address() {return srvtsap.sockaddr;}

	protected ServerHandle(String srvname, int srvport, ResolverService resolver, com.grey.naf.reactor.Dispatcher d,
			boolean tcp, com.grey.naf.BufferSpec bufspec, com.grey.base.config.XmlConfig cfg)
				throws com.grey.base.ConfigException, java.io.IOException
	{
		super(d);
		rslvr = resolver;
		tcp_only = tcp;
		srvtsap = com.grey.base.utils.TSAP.build(srvname, srvport); //if in TCP-only mode, this is all we want

		if (!tcp_only) {
			int sockbufsiz = cfg.getInt("udpsock/@sockbuf", false, DFLT_SOCKBUF);
			udpchan = java.nio.channels.DatagramChannel.open();
			udpchan.socket().setReceiveBufferSize(sockbufsiz);
			udpchan.socket().setSendBufferSize(sockbufsiz);
			udpchan.socket().bind(null);
			initChannel(udpchan, true, false);
			if (udpchan.socket().getReceiveBufferSize() != sockbufsiz || udpchan.socket().getSendBufferSize() != sockbufsiz) {
				throw new com.grey.base.ConfigException("Failed to set sockbuf-size - "+sockbufsiz+" vs actual="
						+udpchan.socket().getReceiveBufferSize()+"/"+udpchan.socket().getSendBufferSize());
			}
			dsptch.logger.info("DNS Resolver on UDP port="+udpchan.socket().getLocalPort()+" associated with server at "+srvtsap.sockaddr
					+" - sockbuf="+com.grey.base.utils.ByteOps.expandByteSize(sockbufsiz, null, false));
		} else {
			udpchan = null;
		}
	}

	protected void start() throws java.io.IOException
	{
		if (tcp_only) return;
		enableRead();
	}

	protected boolean stop()
	{
		disconnect();
		srvtsap.clear();
		return true;
	}

	@Override
	protected void ioIndication(int ops) throws java.io.IOException
	{
		rslvr.udpResponseReceived(this);
	}

	protected void receive(java.nio.ByteBuffer buf) throws java.io.IOException
	{
		udpchan.receive(buf);
	}

	protected void send(java.nio.ByteBuffer buf, int len) throws java.io.IOException
	{
		int nbytes = udpchan.send(buf, srvtsap.sockaddr);
		if (nbytes != len) {
			throw new java.io.IOException("Datagram write="+nbytes+"/"+len);
		}
	}
}
