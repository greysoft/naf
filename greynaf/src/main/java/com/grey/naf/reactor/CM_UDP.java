/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.net.DatagramSocket;
import java.net.SocketAddress;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.IP;
import com.grey.naf.BufferSpec;

public abstract class CM_UDP extends ChannelMonitor implements DispatcherRunnable
{
	private final IOExecReaderUDP udpreader;

	protected abstract void ioReceived(ByteArrayRef rcvdata, java.net.InetSocketAddress remaddr) throws java.io.IOException;

	public int getLocalPort() {return getDatagramChannel().socket().getLocalPort();}
	public java.net.InetAddress getLocalIP() {return getDatagramChannel().socket().getLocalAddress();}
	protected IOExecReaderUDP getReader() {return udpreader;}

	public CM_UDP(Dispatcher d, SocketAddress addr, BufferSpec bufspec, int sockbufsiz) throws java.io.IOException {
		super(d);
		udpreader = (bufspec == null ? null : new IOExecReaderUDP(bufspec));
		if (udpreader != null) udpreader.initChannel(this);

		java.nio.channels.DatagramChannel udpchan = java.nio.channels.DatagramChannel.open();
		DatagramSocket sock = udpchan.socket();
		if (sockbufsiz != 0) {
			sock.setReceiveBufferSize(sockbufsiz);
			sock.setSendBufferSize(sockbufsiz);
		}
		sock.bind(addr);
		initChannel(udpchan, true);
		getLogger().info(getClass().getName()+" in Dispatcher="+d.getName()+" bound to local UDP socket="+sock.getLocalSocketAddress()
				+" - sockbuf="+com.grey.base.utils.ByteOps.expandByteSize(sockbufsiz, null, false));
	}

	@Override
	public void startDispatcherRunnable() throws java.io.IOException {
		registerChannel();
		getReader().receive();
	}

	@Override
	public boolean stopDispatcherRunnable() {
		disconnect();
		return true;
	}

	@Override
	boolean shutdownChannel(boolean linger) {
		if (udpreader != null) udpreader.clearChannel();
		return true;
	}

	@Override
	void ioIndication(int readyOps) throws java.io.IOException {
		if ((readyOps & java.nio.channels.SelectionKey.OP_READ) != 0) {
			if (udpreader != null) {
				udpreader.handleIO();
			}
		}
	}

	public int transmit(java.nio.ByteBuffer buf, java.net.InetSocketAddress remaddr) throws java.io.IOException {
		int len = buf.remaining();
		int nbytes = getDatagramChannel().send(buf, remaddr);
		if (nbytes != len) {
			throw new java.io.IOException("Dispatcher="+getDispatcher().getName()+" has Datagram write="+nbytes+"/"+len+" on "+getDatagramChannel().socket()+" => "+remaddr);
		}
		return nbytes;
	}

	@Override
	StringBuilder dumpChannelState(StringBuilder sb, String dlm) {
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