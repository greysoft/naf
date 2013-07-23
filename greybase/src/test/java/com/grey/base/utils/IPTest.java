/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.net.SocketException;
import java.net.UnknownHostException;

import com.grey.base.utils.IP.Subnet;

public class IPTest
{
	// Hand-calculated netmasks, indexed by prefix, which we use for our tests
	private static int[] verified_masks = new int[]{0x00000000,
		0x80000000, 0xC0000000, 0xE0000000, 0xF0000000,
		0xF8000000, 0xFC000000, 0xFE000000, 0xFF000000,
		0xFF800000, 0xFFC00000, 0xFFE00000, 0xFFF00000,
		0xFFF80000, 0xFFFC0000, 0xFFFE0000, 0xFFFF0000,
		0xFFFF8000, 0xFFFFC000, 0xFFFFE000, 0xFFFFF000,
		0xFFFFF800, 0xFFFFFC00, 0xFFFFFE00, 0xFFFFFF00,
		0xFFFFFF80, 0xFFFFFFC0, 0xFFFFFFE0, 0xFFFFFFF0,
		0xFFFFFFF8, 0xFFFFFFFC, 0xFFFFFFFE, 0xFFFFFFFF};

	@org.junit.Test
	public void testMaskAndPrefix()
	{
		org.junit.Assert.assertEquals(33, verified_masks.length);  // double-check our handiwork above
		for (int pfx = 0; pfx != verified_masks.length; pfx++)
		{
			int mask = IP.prefixToMask(pfx);
			org.junit.Assert.assertEquals(verified_masks[pfx], mask);
			org.junit.Assert.assertEquals(pfx, IP.maskToPrefix(mask));
		}
		org.junit.Assert.assertEquals(0, IP.prefixToNetSize(32));
		org.junit.Assert.assertEquals(2, IP.prefixToNetSize(31));
		org.junit.Assert.assertEquals(4, IP.prefixToNetSize(30));
		org.junit.Assert.assertEquals(8, IP.prefixToNetSize(29));
		org.junit.Assert.assertEquals(16, IP.prefixToNetSize(28));
		org.junit.Assert.assertEquals(256, IP.prefixToNetSize(24));
		org.junit.Assert.assertEquals(256*256, IP.prefixToNetSize(16));
		org.junit.Assert.assertEquals(256*256*256, IP.prefixToNetSize(8));
		org.junit.Assert.assertEquals(256*256*256*16, IP.prefixToNetSize(4));
		org.junit.Assert.assertEquals(256*256*256*32, IP.prefixToNetSize(3));
		org.junit.Assert.assertEquals(256*256*256*64, IP.prefixToNetSize(2));
		org.junit.Assert.assertEquals(256*256*256*128, IP.prefixToNetSize(1));
		org.junit.Assert.assertEquals(-1, IP.prefixToNetSize(0));
	}

	@org.junit.Test
	public void testDottedIP()
	{
		verifyDottedIP("0.0.0.0", 0);
		verifyDottedIP("255.255.255.255", ~0);
		verifyDottedIP("127.0.0.1", (127 << 24) + 1);
		verifyDottedIP("192.168.0.102", (192 << 24) + (168 << 16) + 102);

		verifyBadIP("192.168.2d.102");
		verifyBadIP("192");
		verifyBadIP("192.168.0");
		verifyBadIP("192.168.0.102.0");
		verifyBadIP("192.168.0.102.A");
		verifyBadIP("192.168.256.102");
		verifyBadIP("256.168.0.102");
		verifyBadIP("192.168.0.260");
		verifyBadIP("192.168.0");
		verifyBadIP("192.168.0.");
		verifyBadIP("192.168.0.102.1");
		verifyBadIP("192.168.0.102.");
		verifyBadIP("192.168..0");
	}

	@org.junit.Test
	public void testSubnets() throws UnknownHostException
	{
		String ipstr = "1.2.3.4";
		Subnet net = IP.parseSubnet(ipstr);
		org.junit.Assert.assertEquals(32, net.netprefix);
		org.junit.Assert.assertEquals(0xffffffff, net.netmask);
		org.junit.Assert.assertEquals(0x01020304, net.ip);
		// this should be the same, ie. the default is 32
		ipstr = "1.2.3.4/32";
		Subnet net2 = IP.parseSubnet(ipstr);
		org.junit.Assert.assertEquals(net.ip, net2.ip);
		ipstr = "1.2.3.4/0";
		net = IP.parseSubnet(ipstr);
		org.junit.Assert.assertEquals(0, net.netprefix);
		org.junit.Assert.assertEquals(0, net.netmask);
		org.junit.Assert.assertEquals(0, net.ip);
		ipstr = "1.2.3.4/8";
		net = IP.parseSubnet(ipstr);
		org.junit.Assert.assertEquals(8, net.netprefix);
		org.junit.Assert.assertEquals(0xff000000, net.netmask);
		org.junit.Assert.assertEquals(0x01000000, net.ip);
		ipstr = "1.2.3.4/24";
		net = IP.parseSubnet(ipstr);
		org.junit.Assert.assertEquals(24, net.netprefix);
		org.junit.Assert.assertEquals(0xffffff00, net.netmask);
		org.junit.Assert.assertEquals(0x01020300, net.ip);

		org.junit.Assert.assertTrue(net.isMember(0x01020304));
		org.junit.Assert.assertTrue(net.isMember(0x01020300));
		org.junit.Assert.assertTrue(net.isMember(0x01020301));
		org.junit.Assert.assertFalse(net.isMember(0x01020200));
		org.junit.Assert.assertFalse(net.isMember(0x01020000));
	}

	@org.junit.Test
	public void testInterfaces() throws SocketException
	{
		int badflags = IP.FLAG_IFIP4 | IP.FLAG_IFIP6;   //an interface can't be both IPv4 and IPv6, so should guarantee no matches
		org.junit.Assert.assertEquals(0, IP.countLocalIPs(badflags));
		int[] ips = IP.getLocalIPs(badflags);
		org.junit.Assert.assertNull(ips);
		String[] macs = IP.getLocalMACs(badflags);
		org.junit.Assert.assertNull(macs);

		// we must have at least one IP interface, surely
		int cnt = IP.countLocalIPs(0);
		if (cnt == 0) {
			System.out.println("Warning: No local IPs found - reduces test coverage");
		} else {
			ips = IP.getLocalIPs(0);
			org.junit.Assert.assertEquals(cnt, ips.length);
			macs = IP.getLocalMACs(0);
			org.junit.Assert.assertNotNull(macs);
		}
	}

	private static void verifyDottedIP(String dotted, int binval_exp)
	{
		int binval = IP.convertDottedIP(dotted);
		org.junit.Assert.assertTrue(IP.validDottedIP(dotted, binval));
		org.junit.Assert.assertEquals(binval_exp, binval);
		byte[] ipbytes = IP.ip2net(binval, null, 0);
		CharSequence dotted2 = IP.displayDottedIP(ipbytes, 0, null);
		org.junit.Assert.assertTrue(dotted.equals(dotted2.toString()));
		binval = IP.net2ip(ipbytes, 0);
		org.junit.Assert.assertEquals(binval_exp, binval);
	}

	private static void verifyBadIP(String dotted)
	{
		int binval = IP.convertDottedIP(dotted);
		org.junit.Assert.assertFalse(IP.validDottedIP(dotted,binval));
	}
}
