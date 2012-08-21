/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class IP
{
	// This class specifies an IP subnet - or an aggregated CIDR-style supernet or route
	public static final class Subnet
	{
		public final int ip;  //the network portion of the IP - host part is zeroed out
		public final int netprefix;
		public final int netmask;

		public Subnet(int ip, int pfx, int mask) {this.ip=ip; netprefix=pfx; netmask=mask;}
		public boolean isMember(int ip2) {return ((ip2 & netmask) == ip);}
	}
	public static final int IPADDR_OCTETS = 4;

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

	// Param is tested to see if it's a valid dotted IP string, and we return the binary IP address value if so
	// We return IP_INVALID if address is invalid, so beware of confusing that with 0.0.0.0, which is NOT invalid. The isValidDottedIP() procedure
	// is provided to clear up any ambiguity.
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
		if (ipbytes != IPADDR_OCTETS) return IP_INVALID;
		return ipaddr;
	}

	// Because convertDottedIP() returns a 32-bit binary address, it's not possible to distinguish its error returns from some genuine addresses.
	// If you pass in the result obtained from a previous call to convertDottedIP() and the string that was passed to it, this function returns
	// True to verify that the result was valid.
	public static boolean validDottedIP(CharSequence ipdotted, int ipaddr)
	{
		if (ipaddr != IP_INVALID) return true;
		return (ipdotted.equals("0.0.0.0"));
	}

	public static StringBuilder displayDottedIP(int ipaddr, StringBuilder strbuf)
	{
		if (strbuf == null) strbuf = new StringBuilder(15);
		char dlm = 0;
		int shift = 24;

		for (int idx = 0; idx != IPADDR_OCTETS; idx++) {
			int bval = (ipaddr >> shift) & 0xFF;
			shift -= 8;
			if (dlm != 0) strbuf.append(dlm);
			strbuf.append(bval);
			dlm = '.';
		}
		return strbuf;
	}

	public static CharSequence displayDottedIP(byte[] netaddr, int off, StringBuilder strbuf)
	{
		int ipaddr = ByteOps.decodeInt(netaddr, off, IPADDR_OCTETS);
		return displayDottedIP(ipaddr, strbuf);
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

	// Note that the 'ipnet' parameter is actually a network IP (even if it is specified as a symbolic hostname
	// within that subnet for simplicity), therefore the absence of a CIDR prefix means it is all network, with
	// with no host part. Hence netprefix defaults to 32, rather than 0 as you might intuitively expect if we
	// were thinking of the IP as representing a host.
	public static Subnet parseSubnet(String ipnet) throws java.net.UnknownHostException
	{
		int prefix = 32;
		int pos = ipnet.indexOf("/");

		if (pos != -1) {
			prefix = (int)IntValue.parseDecimal(ipnet, pos+1, ipnet.length() - pos - 1);
			ipnet = ipnet.substring(0, pos);
		}
		int mask = prefixToMask(prefix);
		int ip = parseIP(ipnet) & mask;
		return new Subnet(ip, prefix, mask);
	}

	public static int parseIP(String host) throws java.net.UnknownHostException
	{
		java.net.InetAddress jdkip = getHostByName(host);
		byte[] ipbytes = jdkip.getAddress();
		return net2ip(ipbytes, 0);
	}

	public static int countLocalIPs(int flags) throws java.net.SocketException
	{
		return enumerateLocalIPs(flags, null);
	}

	public static int[] getLocalIPs(int flags) throws java.net.SocketException
	{
		java.util.List<Integer> iplist = new java.util.ArrayList<Integer>();
		if (enumerateLocalIPs(flags, iplist) == 0) return null;
		int[] arr = new int[iplist.size()];

		for (int idx = 0; idx != arr.length; idx++) {
			arr[idx] = iplist.get(idx).intValue();
		}
		return arr;
	}

	private static int enumerateLocalIPs(int flags, java.util.List<Integer> iplist) throws java.net.SocketException
	{
		int cnt = 0;
		java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();

		while (ifaces != null && ifaces.hasMoreElements()) {
			java.net.NetworkInterface nif = ifaces.nextElement();
			if ((flags & FLAG_IFUP) != 0 && !nif.isUp()) continue;
			if ((flags & FLAG_IFREAL) != 0 && nif.isVirtual()) continue;
			java.util.Enumeration<java.net.InetAddress> ips = nif.getInetAddresses();

			while (ips != null && ips.hasMoreElements()) {
				java.net.InetAddress ip = ips.nextElement();
				if ((flags & FLAG_IFREAL) != 0 && (ip.isLoopbackAddress() || ip.isMulticastAddress())) continue;
				if ((flags & FLAG_IFIP4) != 0 && !ip.getClass().equals(java.net.Inet4Address.class)) continue;
				if ((flags & FLAG_IFIP6) != 0 && !ip.getClass().equals(java.net.Inet6Address.class)) continue;
				if (iplist != null) iplist.add(Integer.valueOf(net2ip(ip.getAddress(), 0)));
				cnt++;
			}
		}
		return cnt;
	}

	public static String[] getLocalMACs(int flags) throws java.net.SocketException
	{
		java.util.List<String> maclist = new java.util.ArrayList<String>();
		java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();

		while (ifaces != null && ifaces.hasMoreElements())
		{
			java.net.NetworkInterface nif = ifaces.nextElement();
			byte[] mac = nif.getHardwareAddress();
			if (mac == null || mac.length == 0) continue;
			if ((flags & FLAG_IFUP) != 0 && !nif.isUp()) continue;
			if ((flags & FLAG_IFREAL) != 0 && nif.isVirtual()) continue;

			// include this interface if it has at least one valid IP
			boolean hasvalidIPs = false;
			java.util.Enumeration<java.net.InetAddress> ips = nif.getInetAddresses();
			while (!hasvalidIPs && ips != null && ips.hasMoreElements())
			{
				java.net.InetAddress ip = ips.nextElement();
				if ((flags & FLAG_IFREAL) != 0 && (ip.isLoopbackAddress() || ip.isMulticastAddress())) continue;
				if ((flags & FLAG_IFIP4) != 0 && !ip.getClass().equals(java.net.Inet4Address.class)) continue;
				if ((flags & FLAG_IFIP6) != 0 && !ip.getClass().equals(java.net.Inet6Address.class)) continue;
				hasvalidIPs = true;
			}
			if (hasvalidIPs) maclist.add(new String(com.grey.base.crypto.Ascii.hexEncode(mac)));
		}
		String[] arr = (maclist.size() == 0 ? null : new String[maclist.size()]);

		for (int idx = 0; idx != maclist.size(); idx++)
		{
			arr[idx] = maclist.get(idx);
		}
		return arr;
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
		return java.net.InetAddress.getByName(hostname);
	}
}
