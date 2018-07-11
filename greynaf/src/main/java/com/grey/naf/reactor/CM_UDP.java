/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.IP;

public abstract class CM_UDP extends ChannelMonitor
{
	private final IOExecReaderUDP udpreader;

	protected abstract void ioReceived(ByteArrayRef rcvdata, java.net.InetSocketAddress remaddr) throws java.io.IOException;

	public int getLocalPort() {return getDatagramChannel().socket().getLocalPort();}
	public java.net.InetAddress getLocalIP() {return getDatagramChannel().socket().getLocalAddress();}
	protected IOExecReaderUDP getReader() {return udpreader;}

	public CM_UDP(Dispatcher d, com.grey.naf.BufferSpec bufspec)
	{
		super(d);
		udpreader = (bufspec == null ? null : new com.grey.naf.reactor.IOExecReaderUDP(bufspec));
	}

	protected void registerConnectionlessChannel(java.nio.channels.DatagramChannel chan, boolean takeOwnership)
		throws java.io.IOException
	{
		registerChannel(chan, takeOwnership);
		if (udpreader != null) udpreader.initChannel(this);
	}

	@Override
	boolean shutdownChannel(boolean linger)
	{
		if (udpreader != null) udpreader.clearChannel();
		return true;
	}
	
	@Override
	void ioIndication(int readyOps) throws java.io.IOException
	{
		if ((readyOps & java.nio.channels.SelectionKey.OP_READ) != 0) {
			if (udpreader != null) {
				udpreader.handleIO();
			}
		}
	}

	@Override
	StringBuilder dumpChannelState(StringBuilder sb, String dlm)
	{
		if (sb == null) sb = new StringBuilder();
		if (getChannel() == null) {
			sb.append(" CM_UDP closed");
			return sb;
		}
		java.net.DatagramSocket sock = getDatagramChannel().socket();
		sb.append(dlm).append("Reader=");
		if (udpreader == null) {
			sb.append("none");
		} else {
			udpreader.dumpState(sb, dlm);
		}
		sb.append("<br/>Endpoint: ").append(getChannel().getClass().getName()).append('/');
		IP.displayDottedIP(IP.convertIP(getLocalIP()), sb);
		sb.append(':').append(String.valueOf(getLocalPort()));
		if (sock.isConnected()) {
			sb.append("=>");
			IP.displayDottedIP(IP.convertIP(sock.getInetAddress()), sb);
			sb.append(':').append(String.valueOf(sock.getPort()));
		}
		return sb;
	}
}