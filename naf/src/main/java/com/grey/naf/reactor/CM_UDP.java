/*
 * Copyright 2014-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.IP;

public abstract class CM_UDP extends ChannelMonitor
{
	protected final IOExecReaderUDP udpreader;

	protected abstract void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata, java.net.InetSocketAddress remaddr)
			throws com.grey.base.FaultException, java.io.IOException;

	public final int getLocalPort() {return ((java.nio.channels.DatagramChannel)iochan).socket().getLocalPort();}
	public final java.net.InetAddress getLocalIP() {return ((java.nio.channels.DatagramChannel)iochan).socket().getLocalAddress();}

	public CM_UDP(Dispatcher d, com.grey.naf.BufferSpec bufspec)
	{
		super(d);
		udpreader = (bufspec == null ? null : new com.grey.naf.reactor.IOExecReaderUDP(bufspec));
	}

	protected final void registerConnectionlessChannel(java.nio.channels.DatagramChannel chan, boolean takeOwnership)
		throws java.io.IOException
	{
		registerChannel(chan, takeOwnership);
		if (udpreader != null) udpreader.initChannel(this);
	}

	@Override
	final boolean shutdownChannel(boolean linger)
	{
		if (udpreader != null) udpreader.clearChannel();
		return true;
	}
	
	@Override
	void ioIndication(int readyOps) throws com.grey.base.FaultException, java.io.IOException
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
		if (iochan == null) {
			sb.append(" CM_UDP closed");
			return sb;
		}
		java.net.DatagramSocket sock = ((java.nio.channels.DatagramChannel)iochan).socket();
		sb.append(dlm).append("Reader=");
		if (udpreader == null) {
			sb.append("none");
		} else {
			udpreader.dumpState(sb, dlm);
		}
		sb.append("<br/>Endpoint: ").append(iochan.getClass().getName()).append('/');
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