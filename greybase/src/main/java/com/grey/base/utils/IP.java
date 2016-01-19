/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public final class IP
{
	// This class specifies an IP subnet - or an aggregated CIDR-style supernet or route
	public static final class Subnet
	{
		public final int ip;  //the network portion of the IP - host part is zeroed out
		public final int netprefix;
		public final int netmask;

		public Subnet(int ip, int pfx) {netprefix=pfx; netmask=prefixToMask(pfx); this.ip=ip&netmask;}
		public boolean isMember(int ip2) {return ((ip2 & netmask) == ip);}
		@Override
		public String toString() {return IP.displayDottedIP(ip).toString()+"/"+netprefix;}
	}
	public static final int IPADDR_OCTETS = 4;
	public static final int IPV6ADDR_OCTETS = 16;

	private static final boolean IPV6_MAP0 = SysProps.get("grey.ipv6.zero", true);

	// You can construct a Regex compiler from the public def at the end, but beware it's not MT-safe
	private static final String RGXPATT_DOT = "\\.";
	private static final String RGXPATT_IPBYTE = "([01]?\\d\\d?|2[0-4]\\d|25[0-5])";  // enforces legal range of 0-255
	public static final String RGXPATT_DOTTEDIP = 
		"^" + RGXPATT_IPBYTE + RGXPATT_DOT + RGXPATT_IPBYTE + RGXPATT_DOT + RGXPATT_IPBYTE + RGXPATT_DOT + RGXPATT_IPBYTE + "$";

	public static final int FLAG_IFUP = 1 << 0;
	public static final int FLAG_IFREAL = 1 << 1;
	public static final int FLAG_IFIP4 = 1 << 2;
	public static final int FLAG_IFIP6 = 1 << 3;

	private static final int IP_INVALID = 0;  // not strictly invalid - special return value from convertDottedIP()
	public static final int IP_LOCALHOST = convertDottedIP("127.0.0.1");

	public static int prefixToMask(int prefix) {return (prefix == 0 ? 0 : ~((1 << (32 - prefix)) - 1));}
	
	public static StringBuilder displayDottedIP(int ip) {return displayDottedIP(ip, null);}
	public static StringBuilder displayDottedIP(byte[] netaddr, int off)  {return displayDottedIP(netaddr, off, null);}
	public static StringBuilder displayIP6(byte[] netaddr, int off) {return displayIP6(netaddr, off, null);}
	public static byte[] ip2net(int ip) {return ip2net(ip, null, 0);}
	public static java.net.InetAddress convertIP(int ip) {return convertIP(ip, null);}
	public static StringBuilder displayArpaDomain(int ip) {return displayArpaDomain(ip, null);}

	// We will rarely come across subnets larger than a /24, meaning in practice this should terminate within 8 loops.
	public static int maskToPrefix(int mask)
	{
		if (mask == 0) return 0;
		int pfx = 32;
		while ((mask & 1) == 0) {
			mask >>>= 1;
			pfx--;
		}
		return pfx;
	}

	public static int prefixToNetSize(int prefix)
	{
		switch (prefix)
		{
		case 0:
			// the correct answer is 0x100000000 which can't be expressed as an Int, but this is a nonsense case anyway
			return -1;
		case 1:
			return 0x80000000;
		case 32:
			return 0;
		default:
			return (int)Math.pow(2, 32 - prefix);
		}
	}

	// Param is tested to see if it's a valid dotted IP string, and we return the binary IP address value if so
	// We return IP_INVALID if address is invalid, so beware of confusing that with 0.0.0.0, which is NOT invalid.
	// The isValidDottedIP() procedure is provided to clear up any ambiguity.
	// If there are fewer than 4 decimal parts, we treat the missing least significant bytes as zero.
	public static int convertDottedIP(CharSequence ipdotted)
	{
		int dottedlen = ipdotted.length();
		int ipaddr = 0;
		int shift = 24;
		int off = 0;
		int ipbytes  = 0;

		for (int idx = 0; idx <= dottedlen; idx++)
		{
			if (idx == dottedlen || ipdotted.charAt(idx) == '.')
			{
				if (idx == off) return IP_INVALID;  //consecutive dots (or a trailing one)
				if (ipbytes++ == IPADDR_OCTETS) return IP_INVALID;
				int byteval;
				try {
					byteval = (int)IntValue.parseDecimal(ipdotted, off, idx - off);
				} catch (NumberFormatException ex) {
					return IP_INVALID;
				}
				if (byteval > 255) return IP_INVALID;
				ipaddr += (byteval << shift);
				shift -= 8;
				off = idx + 1;
			}
		}
		return ipaddr;
	}

	// Because convertDottedIP() returns a 32-bit binary address, it's not possible to distinguish its error returns from
	// some genuine addresses. Therefore, if you pass in the result obtained from a call to convertDottedIP() along with
	// the string that was passed to it, this function indicates whether that result was valid.
	public static boolean validDottedIP(CharSequence ipdotted, int ipaddr)
	{
		if (ipaddr != IP_INVALID) return true;
		return (ipdotted.equals("0.0.0.0"));
	}

	public static StringBuilder displayDottedIP(int ipaddr, StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder(15);
		char dlm = 0;
		int shift = 24;

		for (int idx = 0; idx != IPADDR_OCTETS; idx++) {
			int bval = (ipaddr >> shift) & 0xFF;
			shift -= 8;
			if (dlm != 0) sb.append(dlm);
			sb.append(bval);
			dlm = '.';
		}
		return sb;
	}

	public static StringBuilder displayDottedIP(byte[] netaddr, int off, StringBuilder sb)
	{
		int ipaddr = ByteOps.decodeInt(netaddr, off, IPADDR_OCTETS);
		return displayDottedIP(ipaddr, sb);
	}

	public static int net2ip(byte[] netaddr, int off)
	{
		return ByteOps.decodeInt(netaddr, off, IPADDR_OCTETS);
	}

	public static byte[] ip2net(int ip, byte[] netaddr, int off)
	{
		if (netaddr == null) {
			netaddr = new byte[IPADDR_OCTETS];
			off = 0;
		}
		ByteOps.encodeInt(ip, netaddr, off, IPADDR_OCTETS);
		return netaddr;
	}

	public static int convertIP(java.net.InetAddress jdkip)
	{
		if (jdkip instanceof java.net.Inet6Address) {
			java.net.Inet6Address addr = (java.net.Inet6Address)jdkip;
			if (addr.isLoopbackAddress()) return IP_LOCALHOST;
			if (addr.isIPv4CompatibleAddress()) {
				byte[] ipbytes = jdkip.getAddress();
				return net2ip(ipbytes, ipbytes.length - IPADDR_OCTETS); //take final 4 bytes
			}
			if (IPV6_MAP0) return 0;
			throw new UnsupportedOperationException("Cannot convert IPv6 to scalar - "+jdkip);
		}
		//assume Inet4Address, with usual 4-byte address buffer
		byte[] ipbytes = jdkip.getAddress();
		return net2ip(ipbytes, 0);
	}

	public static java.net.InetAddress convertIP(int ip, byte[] workbuf)
	{
		workbuf = ip2net(ip, workbuf, 0);
		try {
			return java.net.InetAddress.getByAddress(workbuf);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Converting IP="+displayDottedIP(ip)+" in bufsiz="+workbuf.length);
		}
	}

	public static StringBuilder displayArpaDomain(int ip, StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder(128);
		int shift = 0;

		for (int idx = 0; idx != IP.IPADDR_OCTETS; idx++) {
			int bval = (ip >> shift) & 0xFF;
			shift += 8;
			sb.append(bval).append('.');
		}
		return sb.append("in-addr.arpa");
	}

	public static StringBuilder displayIP6(byte[] netaddr, int off, StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder(128);
		final int grpsiz = 2;
		final int lmt = off + IPV6ADDR_OCTETS;
		int zerocnt = 0;
		for (int off_grp = off; off_grp != lmt; off_grp += grpsiz) {
			int grpval = com.grey.base.utils.ByteOps.decodeInt(netaddr, off_grp, grpsiz);
			if (grpval == 0) {
				zerocnt++;
				continue;
			}
			if (off_grp != off) sb.append(':');
			if (zerocnt == 1) {
				sb.append("0:");
			} else if (zerocnt != 0) {
				sb.append(':');
			}
			zerocnt = 0;
			IntValue.encodeHex(grpval, false, sb);
		}
		return sb;
	}

	// Note that the 'ipnet' parameter is actually a network IP (even if it is specified as a symbolic hostname
	// within that subnet for simplicity), therefore the absence of a CIDR prefix means it is all network, with
	// no host part. Hence netprefix defaults to 32, rather than 0 as you might intuitively expect if we
	// were thinking of the IP as representing a host.
	public static Subnet parseSubnet(String ipnet) throws java.net.UnknownHostException
	{
		int prefix = 32;
		int pos = ipnet.indexOf("/");

		if (pos != -1) {
			prefix = (int)IntValue.parseDecimal(ipnet, pos+1, ipnet.length() - pos - 1);
			ipnet = ipnet.substring(0, pos);
		}
		int ip = parseIP(ipnet);
		return new Subnet(ip, prefix);
	}

	public static int parseIP(String host) throws java.net.UnknownHostException
	{
		int ip = convertDottedIP(host);
		boolean is_dotted = validDottedIP(host, ip);
		if (is_dotted) return ip;
		java.net.InetAddress jdkip = getHostByName(host);
		return convertIP(jdkip);
	}

	public static int countLocalIPs(int flags) throws java.net.SocketException
	{
		int[] ips = getLocalIPs(flags);
		return (ips == null ? 0 : ips.length);
	}

	public static int[] getLocalIPs(int flags) throws java.net.SocketException
	{
		java.util.List<Integer> iplist = new java.util.ArrayList<Integer>();
		java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
		while (ifaces != null && ifaces.hasMoreElements()) {
			getLocalIPs(ifaces.nextElement(), iplist, flags);
		}
		if (iplist.size() == 0) return null;

		int[] arr = new int[iplist.size()];
		for (int idx = 0; idx != arr.length; idx++) {
			arr[idx] = iplist.get(idx).intValue();
		}
		return arr;
	}

	private static void getLocalIPs(java.net.NetworkInterface nif, java.util.List<Integer> iplist, int flags) throws java.net.SocketException
	{
		if ((flags & FLAG_IFREAL) != 0
				&& (nif.isVirtual() || nif.isLoopback() || nif.isPointToPoint())) return;
		if ((flags & FLAG_IFUP) != 0 && !nif.isUp()) return;

		java.util.Enumeration<java.net.InetAddress> ips = nif.getInetAddresses();
		while (ips != null && ips.hasMoreElements()) {
			java.net.InetAddress ip = ips.nextElement();
			if ((flags & FLAG_IFREAL) != 0 && (ip.isLoopbackAddress() || ip.isMulticastAddress())) continue;
			if ((flags & FLAG_IFIP4) != 0 && !ip.getClass().equals(java.net.Inet4Address.class)) continue;
			if ((flags & FLAG_IFIP6) != 0 && !ip.getClass().equals(java.net.Inet6Address.class)) continue;
			iplist.add(Integer.valueOf(net2ip(ip.getAddress(), 0)));
		}

		java.util.Enumeration<java.net.NetworkInterface> ifaces = nif.getSubInterfaces();
		while (ifaces != null && ifaces.hasMoreElements()) {
			getLocalIPs(ifaces.nextElement(), iplist, flags);
		}
	}

	public static String[] getLocalMACs(int flags) throws java.net.SocketException
	{
		java.util.List<String> maclist = new java.util.ArrayList<String>();
		java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
		while (ifaces != null && ifaces.hasMoreElements()) {
			getLocalMACs(ifaces.nextElement(), maclist, flags);
		}
		if (maclist.size() == 0) return null;
		return maclist.toArray(new String[maclist.size()]);
	}

	private static void getLocalMACs(java.net.NetworkInterface nif, java.util.List<String> maclist,
			int flags) throws java.net.SocketException
	{
		if ((flags & FLAG_IFREAL) != 0
				&& (nif.isVirtual() || nif.isLoopback() || nif.isPointToPoint())) return;
		if ((flags & FLAG_IFUP) != 0 && !nif.isUp()) return;

		if ((flags & FLAG_IFIP4) != 0 || (flags & FLAG_IFIP6) != 0) {
			java.util.Enumeration<java.net.InetAddress> ips = nif.getInetAddresses();
			boolean hasValidIPs = false;
			while (!hasValidIPs && ips != null && ips.hasMoreElements()) {
				java.net.InetAddress ip = ips.nextElement();
				if ((flags & FLAG_IFREAL) != 0 && (ip.isLoopbackAddress() || ip.isMulticastAddress())) continue;
				if ((flags & FLAG_IFIP4) != 0 && !ip.getClass().equals(java.net.Inet4Address.class)) continue;
				if ((flags & FLAG_IFIP6) != 0 && !ip.getClass().equals(java.net.Inet6Address.class)) continue;
				hasValidIPs = true;
			}
			if (!hasValidIPs) return;
		}
		byte[] mac = nif.getHardwareAddress();
		if (mac == null || mac.length == 0) return;
		maclist.add(new String(com.grey.base.crypto.Ascii.hexEncode(mac)));

		java.util.Enumeration<java.net.NetworkInterface> ifaces = nif.getSubInterfaces();
		while (ifaces != null && ifaces.hasMoreElements()) {
			getLocalMACs(ifaces.nextElement(), maclist, flags);
		}
	}

	// This method uses the blocking OS-level DNS resolver.
	// The JDK method here throws an Exception which names the unresolvable hostname, which is important for logging purposes.
	//
	// NB: When constructing an InetSocketAddress object, we should always use this method to obtain an InetAddress object first, and then call
	// the InetSocketAddress(InetAddress,String) variant of the SocketAddress constructor, as the variant that takes a hostname string doesn't
	// actually resolve it there and then, and it will not get resolved until the socket is used in a Bind or Connect call - at which time it
	// throws an exception that doesn't even tell us which hostname it failed on.
	public static java.net.InetAddress getHostByName(String hostname) throws java.net.UnknownHostException 
	{
		// NB: handles dotted IPs ok, without doing DNS lookup
		if ("localhost".equalsIgnoreCase(hostname)) hostname = "127.0.0.1";
		return java.net.InetAddress.getByName(hostname);
	}
}