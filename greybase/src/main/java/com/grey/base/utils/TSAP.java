/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Transport Service Access Point (ie.&nbsp; a TCP or UDP address)
 */
public final class TSAP
{
	public int ip;
	public int port;
	public java.net.InetSocketAddress sockaddr;
	public StringBuilder dotted_ip;

	private String host;  //records the host as originally supplied, if any
	private final byte[] ipbytes = new byte[IP.IPADDR_OCTETS];

	public static TSAP build(String hostport, int port) throws java.net.UnknownHostException {return build(hostport, port, true);}

	// hostport is of the form "hostname:portnumber", where one (but not both) of those components is optional (unless hostport null).
	// The port paramter is a default, which is overridden by any port component embedded in hostport.
	// port==0 means the hostname part is optional and defaults to localhost. Else the portnumber part of hostpart is the optional bit.
	// Null hostport is interpreted as "localhost" regardless of port parameter.
	public static TSAP build(String hostport, int port, boolean with_dotted) throws java.net.UnknownHostException
	{
		String host = null;
		int pos = (hostport == null ? -1 : hostport.indexOf(":"));
		if (pos == -1) {
			if (port == 0) {
				if (hostport != null) port = (int)IntValue.parseDecimal(hostport, 0, hostport.length());
			} else {
				host = hostport;
			}
		} else {
			if (pos != 0) host = hostport.substring(0, pos);
			if (pos != hostport.length() - 1) port = (int)IntValue.parseDecimal(hostport, pos+1, hostport.length()-pos-1);
		}
		if (host == null) host = "localhost";
		TSAP tsap = new TSAP();
		tsap.host = host;
		tsap.port = port;
		java.net.InetAddress jdkip = IP.getHostByName(host);
		tsap.sockaddr =  new java.net.InetSocketAddress(jdkip, port);
		setIP(tsap, jdkip, with_dotted);
		return tsap;
	}

	public static TSAP get(java.net.InetAddress jdkip, int port, TSAP tsap, boolean with_dotted, boolean with_sockaddr)
	{
		if (tsap == null) {
			tsap = new TSAP();
		} else {
			tsap.clear();
		}
		tsap.sockaddr = (with_sockaddr ? new java.net.InetSocketAddress(jdkip, port) : null);
		tsap.port = port;
		setIP(tsap, jdkip, with_dotted);
		return tsap;
	}

	public static TSAP get(java.net.Socket sock, boolean local, TSAP tsap, boolean with_dotted)
	{
		java.net.InetSocketAddress sockaddr = (java.net.InetSocketAddress)(local ? sock.getLocalSocketAddress() : sock.getRemoteSocketAddress());
		if (sockaddr == null) return null;

		if (tsap == null) {
			tsap = new TSAP();
		} else {
			tsap.clear();
		}
		tsap.sockaddr = sockaddr;
		tsap.port = sockaddr.getPort();
		setIP(tsap, sockaddr.getAddress(), with_dotted);
		return tsap;
	}

	private static void setIP(TSAP tsap, java.net.InetAddress jdkip, boolean with_dotted)
	{
		byte[] ipbytes = jdkip.getAddress();
		tsap.ip = IP.net2ip(ipbytes, 0);
		if (with_dotted) tsap.setDotted();
	}

	// Unlike build(), this method is expected to do everything in-situ, with no new memory being allocated - apart from the sockaddr object,
	// which the JDK forces us to create anew.
	// I have confirmed that these JDK calls don't trigger any DNS lookups.
	public void set(int ipnum, int portnum, boolean with_dotted) throws java.net.UnknownHostException 
	{
		clear();
		ip = ipnum;
		port = portnum;
		IP.ip2net(ip, ipbytes, 0);
		java.net.InetAddress jdkip = java.net.InetAddress.getByAddress(ipbytes);
		sockaddr = new java.net.InetSocketAddress(jdkip, port);
		if (with_dotted) setDotted();
	}

	public void ensureDotted()
	{
		if (dotted_ip == null || dotted_ip.length() == 0) setDotted();
	}

	private void setDotted()
	{
		if (dotted_ip == null) dotted_ip = new StringBuilder();
		IP.displayDottedIP(ip, dotted_ip);
	}

	public void clear()
	{
		sockaddr = null;
		host = null;
		if (dotted_ip != null) dotted_ip.setLength(0);
	}

	@Override
	public String toString()
	{
		CharSequence hostpart = (host == null ? (dotted_ip==null?"":dotted_ip) : host);
		if (hostpart == null || hostpart.length() == 0) hostpart = sockaddr.getAddress().toString();
		return hostpart + ":" + port;
	}
}
