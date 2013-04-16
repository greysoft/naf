/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public class TSAPTest
{
	private static final byte[] iparr = new byte[IP.IPADDR_OCTETS];

	@org.junit.Test
	public void testBuild() throws java.net.UnknownHostException
	{
		String ipdotted = "1.2.3.4";
		int port = 995;
		TSAP tsap = TSAP.build(ipdotted+":"+port, port+99);
		verify(tsap, ipdotted, ipdotted, port, true);

		tsap = TSAP.build(ipdotted, port);
		verify(tsap, ipdotted, ipdotted, port, true);
		tsap = TSAP.build(ipdotted+":", port);
		verify(tsap, ipdotted, ipdotted, port, true);

		tsap = TSAP.build(Integer.toString(port), 0);
		verify(tsap, "localhost", "127.0.0.1", port, true);
		tsap = TSAP.build(":"+Integer.toString(port), 0);
		verify(tsap, "localhost", "127.0.0.1", port, true);

		tsap = TSAP.build(null, port);
		verify(tsap, "localhost", "127.0.0.1", port, true);
		tsap = TSAP.build("localhost", port);
		verify(tsap, "localhost", "127.0.0.1", port, true);
		port = 0;
		tsap = TSAP.build(null, port);
		verify(tsap, "localhost", "127.0.0.1", port, true);

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
	public void testGet() throws java.net.UnknownHostException
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

		tsap2 = TSAP.get(jdkip, port, tsap, false, false);
		org.junit.Assert.assertTrue(tsap2 == tsap);
		org.junit.Assert.assertEquals(0, tsap.dotted_ip.length());
		org.junit.Assert.assertEquals(port, tsap.port);
		org.junit.Assert.assertEquals(IP.net2ip(ipaddr, 0), tsap.ip);
		org.junit.Assert.assertNull(tsap.sockaddr);
	}

	private void verify(TSAP tsap, String host, String ipdotted, int port, boolean hasdotted)
	{
		org.junit.Assert.assertEquals(IP.convertDottedIP(ipdotted), tsap.ip);
		org.junit.Assert.assertEquals(port, tsap.port);
		org.junit.Assert.assertFalse(tsap.sockaddr.isUnresolved());
		org.junit.Assert.assertEquals(tsap.port, tsap.sockaddr.getPort());

		if (!SysProps.get("greynaf.test.skipdns", false)) {
			if (host != null) org.junit.Assert.assertEquals(host, tsap.sockaddr.getHostName());
			org.junit.Assert.assertEquals(ipdotted, tsap.sockaddr.getAddress().getHostAddress());
			if (host != null) org.junit.Assert.assertEquals(host, tsap.sockaddr.getAddress().getHostName());
			org.junit.Assert.assertArrayEquals(IP.ip2net(tsap.ip, iparr, 0), tsap.sockaddr.getAddress().getAddress());
		}

		if (hasdotted) {
			org.junit.Assert.assertEquals(ipdotted, tsap.dotted_ip.toString());
			org.junit.Assert.assertEquals((host==null?ipdotted:host)+":"+port, tsap.toString());
		} else {
			org.junit.Assert.assertEquals(0, tsap.dotted_ip.length());
			org.junit.Assert.assertTrue(tsap.toString().endsWith(ipdotted+":"+port));
		}
	}
}
