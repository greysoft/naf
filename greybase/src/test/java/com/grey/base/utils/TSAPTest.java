/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class TSAPTest
{
	private static final byte[] iparr = new byte[IP.IPADDR_OCTETS];
	private static final String LOCALHOST_DOTTED = "127.0.0.1";

	@org.junit.Test
	public void testBuild() throws java.net.UnknownHostException
	{
		String ipdotted = "1.2.3.4";
		int port = 995;
		TSAP tsap = TSAP.build(ipdotted+":"+port, port+99, true);
		verify(tsap, ipdotted, ipdotted, port, true);

		tsap = TSAP.build(ipdotted, port, true);
		verify(tsap, ipdotted, ipdotted, port, true);
		tsap = TSAP.build(ipdotted+":", port, true);
		verify(tsap, ipdotted, ipdotted, port, true);

		tsap = TSAP.build(Integer.toString(port), 0, true);
		verify(tsap, "127.0.0.1", LOCALHOST_DOTTED, port, true);
		tsap = TSAP.build(":"+Integer.toString(port), 0, true);
		verify(tsap, "127.0.0.1", LOCALHOST_DOTTED, port, true);

		tsap = TSAP.build(null, port, true);
		verify(tsap, "127.0.0.1", LOCALHOST_DOTTED, port, true);
		tsap = TSAP.build("localhost", port, true);
		verify(tsap, "localhost", LOCALHOST_DOTTED, port, true);
		port = 0;
		tsap = TSAP.build(null, port, true);
		verify(tsap, "127.0.0.1", LOCALHOST_DOTTED, port, true);

		port = 995;
		tsap = TSAP.build(ipdotted+":"+port, port+99);
		verify(tsap, ipdotted, ipdotted, port, false);
		org.junit.Assert.assertNull(tsap.dotted_ip);
		org.junit.Assert.assertEquals(ipdotted+":"+port, tsap.toString());
		tsap.ensureDotted();
		org.junit.Assert.assertEquals(ipdotted+":"+port, tsap.toString());
		verify(tsap, ipdotted, ipdotted, port, true);
		tsap.ensureDotted();
		verify(tsap, ipdotted, ipdotted, port, true);
		tsap.clear();
		org.junit.Assert.assertEquals("0.0.0.0:0", tsap.toString());
		org.junit.Assert.assertNotNull(tsap.dotted_ip);
		tsap.ensureDotted();
		org.junit.Assert.assertEquals("0.0.0.0", tsap.dotted_ip.toString());
		org.junit.Assert.assertEquals(0, tsap.ip);
		org.junit.Assert.assertEquals(0, tsap.port);

		ipdotted = "192.168.1.213";
		port = 806;
		int ipnum = IP.convertDottedIP(ipdotted);
		tsap.set(ipnum, port, true);
		verify(tsap, null, ipdotted, port, true);

		ipdotted = "193.168.1.214";
		port = 666;
		ipnum = IP.convertDottedIP(ipdotted);
		tsap.set(ipnum, port, false);
		verify(tsap, null, ipdotted, port, false);
	}

	@org.junit.Test
	public void testGetByIP() throws java.net.UnknownHostException
	{
		byte[] ipaddr = new byte[]{1,2,3,4};
		StringBuilder sb = new StringBuilder();
		sb.append((char)('0'+ipaddr[0])).append('.').append((char)('0'+ipaddr[1])).append('.').append((char)('0'+ipaddr[2])).append('.').append((char)('0'+ipaddr[3]));
		String ipdotted = sb.toString();
		int port = 995;
		java.net.InetAddress jdkip = java.net.InetAddress.getByAddress(ipaddr);

		TSAP tsap = TSAP.get(jdkip, port, null, true, true);
		verify(tsap, ipdotted, ipdotted, port, true);

		TSAP tsap2 = TSAP.get(jdkip, port+1, tsap, true, true);
		org.junit.Assert.assertTrue(tsap2 == tsap);
		verify(tsap, ipdotted, ipdotted, port+1, true);

		tsap2 = TSAP.get(jdkip, port, tsap);
		org.junit.Assert.assertTrue(tsap2 == tsap);
		org.junit.Assert.assertEquals(0, tsap.dotted_ip.length());
		org.junit.Assert.assertEquals(port, tsap.port);
		org.junit.Assert.assertEquals(IP.net2ip(ipaddr, 0), tsap.ip);
		org.junit.Assert.assertNull(tsap.sockaddr);
	}

	@org.junit.Test
	public void testGetBySocket() throws java.io.IOException
	{
		java.net.ServerSocket srvsock = new java.net.ServerSocket(0);
		java.net.Socket csock;
		try {
			csock = new java.net.Socket(LOCALHOST_DOTTED, srvsock.getLocalPort());
		} finally {
			srvsock.close();
		}
		try {
			java.net.SocketAddress laddr = csock.getLocalSocketAddress();
			java.net.SocketAddress raddr = csock.getRemoteSocketAddress();

			TSAP tsap = TSAP.get(csock, true, null, false);
			org.junit.Assert.assertEquals(csock.getLocalPort(), tsap.port);
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, tsap.ip);
			org.junit.Assert.assertEquals(laddr, tsap.sockaddr);
			org.junit.Assert.assertNull(tsap.dotted_ip);
			tsap.clear();
			org.junit.Assert.assertNull(tsap.dotted_ip);
			org.junit.Assert.assertNull(tsap.sockaddr);


			tsap = TSAP.get(csock, false, tsap, true);
			org.junit.Assert.assertEquals(csock.getPort(), tsap.port);
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, tsap.ip);
			org.junit.Assert.assertEquals(raddr, tsap.sockaddr);
			org.junit.Assert.assertEquals(LOCALHOST_DOTTED, tsap.dotted_ip.toString());
			tsap.clear();
			org.junit.Assert.assertEquals(0, tsap.dotted_ip.length());
			org.junit.Assert.assertNull(tsap.sockaddr);
		} finally {
			csock.close();
		}
		TSAP tsap = TSAP.get(new java.net.Socket(), true, null, true);
		org.junit.Assert.assertNull(tsap);
		tsap = TSAP.get(new java.net.Socket(), false, null, true);
		org.junit.Assert.assertNull(tsap);
	}

	@org.junit.Test
	public void testMisc() throws java.io.IOException
	{
		int port = 12;
		String ipstr = "192.168.1.2";
		int ip = IP.convertDottedIP(ipstr);
		java.net.InetSocketAddress sockaddr = TSAP.createSocketAddress(ip, port);
		org.junit.Assert.assertEquals(port, sockaddr.getPort());
		org.junit.Assert.assertEquals(ip, IP.convertIP(sockaddr.getAddress()));
		org.junit.Assert.assertEquals(ipstr, sockaddr.getHostString());
	}

	private void verify(TSAP tsap, String host, String ipdotted, int port, boolean hasdotted)
	{
		org.junit.Assert.assertEquals(IP.convertDottedIP(ipdotted), tsap.ip);
		org.junit.Assert.assertEquals(port, tsap.port);
		org.junit.Assert.assertFalse(tsap.sockaddr.isUnresolved());
		org.junit.Assert.assertEquals(tsap.port, tsap.sockaddr.getPort());
		org.junit.Assert.assertEquals(ipdotted, tsap.sockaddr.getAddress().getHostAddress());
		org.junit.Assert.assertArrayEquals(IP.ip2net(tsap.ip, iparr, 0), tsap.sockaddr.getAddress().getAddress());

		if (hasdotted) {
			org.junit.Assert.assertEquals(ipdotted, tsap.dotted_ip.toString());
			org.junit.Assert.assertEquals((host==null?ipdotted:host)+":"+port, tsap.toString());
		} else {
			if (tsap.dotted_ip != null) org.junit.Assert.assertEquals(0, tsap.dotted_ip.length());
			org.junit.Assert.assertTrue(tsap.toString().endsWith(ipdotted+":"+port));
		}
	}
}
