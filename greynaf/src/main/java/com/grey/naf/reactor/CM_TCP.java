/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public abstract class CM_TCP extends CM_Stream
{
	public static final LEVEL LOGLEVEL_CNX = LEVEL.TRC3;

	public int getLocalPort() {return getSocketChannel().socket().getLocalPort();}
	public int getRemotePort() {return getSocketChannel().socket().getPort();}
	public java.net.InetAddress getLocalIP() {return getSocketChannel().socket().getLocalAddress();}
	public java.net.InetAddress getRemoteIP() {return getSocketChannel().socket().getInetAddress();}
	public java.net.InetSocketAddress getLocalAddress() {return (java.net.InetSocketAddress)getSocketChannel().socket().getLocalSocketAddress();}
	public java.net.InetSocketAddress getRemoteAddress() {return (java.net.InetSocketAddress)getSocketChannel().socket().getRemoteSocketAddress();}

	public CM_TCP(Dispatcher d, com.grey.naf.BufferGenerator rspec, com.grey.naf.BufferGenerator wspec) {
		super(d, rspec, wspec);
	}
}