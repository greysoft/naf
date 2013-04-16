/*
 * Copyright 2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class DuplexPipe
{
	public static class Factory
	{
		private final java.net.ServerSocket srvsock;

		public Factory() throws java.io.IOException
		{
	        byte[] ipbytes = IP.ip2net(IP.IP_LOCALHOST, null, 0);
	        java.net.InetAddress ipaddr = java.net.InetAddress.getByAddress(ipbytes);
	        java.nio.channels.ServerSocketChannel srvchan = java.nio.channels.ServerSocketChannel.open();
	        srvsock = srvchan.socket();
	        srvsock.bind(new java.net.InetSocketAddress(ipaddr, 0));
		}

		public DuplexPipe create() throws java.io.IOException
		{
			return new DuplexPipe(srvsock);
		}

		public void shutdown()
		{
			try {
				srvsock.close();
			} catch (Exception ex) {
				throw new RuntimeException("Shutdown failed on DuplexPipe Factory - "+ex, ex);
			}
		}
	}

	// Convenience utility.
	// If you are going to create several pipes, it is more efficient to use the factory.
	public static DuplexPipe create() throws java.io.IOException
	{
		Factory f = new Factory();
		DuplexPipe p = f.create();
		f.shutdown();
		return p;
	}

	public final java.net.Socket ep1;
	public final java.net.Socket ep2;

	private DuplexPipe(java.net.ServerSocket srvsock) throws java.io.IOException
	{
        ep1 = new java.net.Socket(srvsock.getInetAddress(), srvsock.getLocalPort());
        ep2 = srvsock.accept();
	}

	public void close() throws java.io.IOException
	{
		ep1.close();
		ep2.close();
	}
}