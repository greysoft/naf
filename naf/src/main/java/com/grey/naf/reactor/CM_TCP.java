/*
 * Copyright 2014-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public abstract class CM_TCP extends CM_Stream
{
	public final int getLocalPort() {return ((java.nio.channels.SocketChannel)iochan).socket().getLocalPort();}
	public final int getRemotePort() {return ((java.nio.channels.SocketChannel)iochan).socket().getPort();}
	public final java.net.InetAddress getLocalIP() {return ((java.nio.channels.SocketChannel)iochan).socket().getLocalAddress();}
	public final java.net.InetAddress getRemoteIP() {return ((java.nio.channels.SocketChannel)iochan).socket().getInetAddress();}
	public final java.net.InetSocketAddress getLocalTSAP() {return (java.net.InetSocketAddress)((java.nio.channels.SocketChannel)iochan).socket().getLocalSocketAddress();}
	public final java.net.InetSocketAddress getRemoteTSAP() {return (java.net.InetSocketAddress)((java.nio.channels.SocketChannel)iochan).socket().getRemoteSocketAddress();}

	public CM_TCP(Dispatcher d, com.grey.naf.BufferSpec rspec, com.grey.naf.BufferSpec wspec) {
		super(d, rspec, wspec);
	}
}