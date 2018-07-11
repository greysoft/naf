/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.utils.IP;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.ByteChars;

abstract public class ResourceData
{
	private final ByteChars rrname; //NB: for PTR, this is the RDATA value - saves space as ipaddr can be used to construct the RR name
	private byte rrclass;
	private int ipaddr; //this is officially only part of the A and PTR RRs, but we use it to cache IPs in many others

	// Careful with this one! In the DNS protocol's RRs, this is a duration (in seconds), but in this class
	// it represents the absolute system time (in milliseconds) at which the RR expires.
	private long ttl;

	public abstract int rrType(); //Resolver.QTYPE_x values
	public final int rrClass() {return rrclass;} //Packet.QCLASS_x values

	public final int getIP() {return ipaddr;}
	final void setIP(int ip) {ipaddr = ip;}

	//Obviously getName() returns an object which could be modified, but callers must use setName()
	public final ByteChars getName() {return rrname;}
	final ByteChars setName(ByteChars src) {return rrname.populate(src.buffer(), src.offset(), src.size());}

	public final long getExpiry() {return ttl;}
	public final int getTTL(long systime) {return (int)((ttl - systime) / TimeOps.MSECS_PER_SECOND);}
	final void setExpiry(long expiry) {ttl = expiry;}
	final void setExpiry(int rawttl, long systime) {ttl = (rawttl * 1000L) + systime;}

	final boolean isNegative() {return (rrname == null);}
	final boolean isExpired(long cutoff) {return (ttl < cutoff);}

	ResourceData(ByteChars name, int ip, long expiry_time)
	{
		rrclass = PacketDNS.QCLASS_INET;
		rrname = name;
		ttl = expiry_time;
		ipaddr = ip;
	}

	ResourceData(ResourceData src, ByteChars qname)
	{
		if (src == null) {
			// This is only for callers who wish to create a reusable RR object, eg. Packet
			rrclass = PacketDNS.QCLASS_INET;
			rrname = new ByteChars();
		} else {
			if (qname != null && qname.equals(src.rrname)) {
				//qname is perm storage and often matches the RR name, so no neeed to allocate our own
				rrname = qname;
			} else {
				rrname = new ByteChars(src.rrname, true);
			}
			rrclass = src.rrclass;
			ipaddr = src.ipaddr;
			ttl = src.ttl;
		}
	}

	void reset(byte clss)
	{
		rrclass = clss;
		ttl = 0;
		ipaddr = 0;
		rrname.clear();
	}

	@Override
	final public String toString()
	{
		return toString(null).toString();
	}

	public StringBuilder toString(StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder(128);
		sb.append("RR=").append(ResolverDNS.getQTYPE(rrType()));
		sb.append(':').append(rrclass & 0xff);
		if (isNegative()) sb.append("/NEGATIVE");
		if (rrname != null) sb.append('/').append(rrname);
		if (ipaddr != 0) {
			sb.append("/IP=");
			IP.displayDottedIP(ipaddr, sb);
		}
		sb.append("/Expiry=");
		if (ttl == Long.MAX_VALUE) {
			sb.append("Never");
		} else {
			TimeOps.expandMilliTime(ttl - System.currentTimeMillis(), sb, false);
		}
		return sb;
	}

	static ResourceData createNegativeRR(byte rrtype, long expiry) {
		switch (rrtype) {
		case ResolverDNS.QTYPE_A:
			return new RR_A(null, 0, expiry);
		case ResolverDNS.QTYPE_PTR:
			return new RR_PTR(null, 0, expiry);
		case ResolverDNS.QTYPE_NS:
			return new RR_NS(null, null, expiry);
		case ResolverDNS.QTYPE_MX:
			return new RR_MX(null, null, 0, expiry);
		case ResolverDNS.QTYPE_SOA:
			return new RR_SOA(null, expiry, null, null, 0, 0, 0, 0, 0);
		case ResolverDNS.QTYPE_SRV:
			return new RR_SRV(null, expiry, null, 0, 0, 0);
		case ResolverDNS.QTYPE_TXT:
			return new RR_TXT(null, expiry, null);
		case ResolverDNS.QTYPE_AAAA:
			return new RR_AAAA(null, null, expiry);
		case ResolverDNS.QTYPE_CNAME:
			return new RR_CNAME(null, null, expiry);
		default:
			break;
		}
		throw new IllegalArgumentException("Unsupported RR type="+rrtype);
	}


	public static class RR_A extends ResourceData
	{
		@Override
		public int rrType() {return ResolverDNS.QTYPE_A;}

		public RR_A(ByteChars hostname, int ip, long expiry) {super(hostname, ip, expiry);}
		RR_A(RR_A src, ByteChars qn) {super(src, qn);}
	}


	public static class RR_NS extends ResourceData
	{
		final ByteChars hostname;

		public ByteChars getHostname() {return hostname;}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_NS;}
		@Override
		public void reset(byte clss) {super.reset(clss); if (hostname!=null) hostname.clear();}

		public RR_NS(ByteChars domain, ByteChars server, long expiry) {
			this(domain, server, 0, expiry);
		}

		RR_NS(ByteChars domain, ByteChars server, int ip, long expiry) {
			super(domain, ip, expiry);
			hostname = server;
		}

		RR_NS(RR_NS src, ByteChars qn) {
			super(src, qn);
			hostname = (src == null ? new ByteChars() : new ByteChars(src.hostname, true));
		}

		@Override
		public StringBuilder toString(StringBuilder sb) {
			sb = super.toString(sb);
			if (hostname != null) sb.append(" - Server=").append(hostname);
			return sb;
		}
	}


	public static final class RR_SOA extends ResourceData
	{
		final ByteChars mname;
		final ByteChars rname;
		int serial;
		int refresh;
		int retry;
		int expire;
		int minttl;

		public int getSerial() {return serial;}
		public int getRefresh() {return refresh;}
		public int getRetry() {return retry;}
		public int getZoneExpiry() {return expire;}
		public int getMinimumTTL() {return minttl;}
		public ByteChars getMNAME() {return mname;}
		public ByteChars getRNAME() {return rname;}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_SOA;}
		@Override
		public void reset(byte clss) {super.reset(clss); if (mname!=null) mname.clear(); if (rname!=null) rname.clear();}

		public RR_SOA(ByteChars n, long expiry, ByteChars mn, ByteChars rn, int s, int rf, int rt, int e, int mttl) {
			super(n, 0, expiry);
			mname = mn;
			rname = rn;
			serial = s;
			refresh = rf;
			retry = rt;
			expire = e;
			minttl = mttl;
		}

		RR_SOA(RR_SOA src, ByteChars qn) {
			super(src, qn);
			mname = (src == null ? new ByteChars() : new ByteChars(src.mname, true));
			rname = (src == null ? new ByteChars() : new ByteChars(src.rname, true));
			if (src != null) {
				serial = src.serial;
				refresh = src.refresh;
				retry = src.retry;
				expire = src.expire;
				minttl = src.minttl;
			}
		}

		@Override
		public StringBuilder toString(StringBuilder sb) {
			sb = super.toString(sb);
			sb.append(" - MNAME=").append(mname);
			sb.append("/RNAME=").append(rname);
			sb.append("/Serial=").append(serial);
			sb.append("/Refresh=").append(refresh);
			sb.append("/Retry=").append(retry);
			sb.append("/Expire=").append(expire);
			sb.append("/MinTTL=").append(minttl);
			return sb;
		}
	}


	public static final class RR_MX extends ResourceData
	{
		final ByteChars relay;
		int pref;

		public ByteChars getRelay() {return relay;}
		public int getPreference() {return pref;}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_MX;}
		@Override
		public void reset(byte clss) {super.reset(clss); if (relay!=null) relay.clear();}

		public RR_MX(ByteChars n, ByteChars r, int p, long expiry) {
			super(n, 0, expiry);
			relay = r;
			pref = p;
		}

		RR_MX(RR_MX src, ByteChars qn) {
			super(src, qn);
			relay = (src == null ? new ByteChars() : new ByteChars(src.relay, true));
			if (src != null) pref = src.pref;
		}

		@Override
		public StringBuilder toString(StringBuilder sb) {
			sb = super.toString(sb);
			sb.append(" - Relay=").append(relay).append("/Pref=").append(pref);
			return sb;
		}
	}


	// See RFC-2782 for RDATA format
	public static final class RR_SRV extends ResourceData
	{
		final ByteChars target;
		int priority;
		int weight;
		int port;

		public int getPriority() {return priority;}
		public int getWeight() {return weight;}
		public int getPort() {return port;}
		public ByteChars getTarget() {return target;}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_SRV;}
		@Override
		public void reset(byte clss) {super.reset(clss); if (target!=null) target.clear();}

		public RR_SRV(ByteChars n, long expiry, ByteChars t, int pri, int w, int p) {
			super(n, 0, expiry);
			target = t;
			priority = pri;
			weight = w;
			port = p;
		}

		RR_SRV(RR_SRV src, ByteChars qn) {
			super(src, qn);
			target = (src == null ? new ByteChars() : new ByteChars(src.target, true));
			if (src != null) {
				priority = src.priority;
				weight = src.weight;
				port = src.port;
			}
		}

		@Override
		public StringBuilder toString(StringBuilder sb) {
			sb = super.toString(sb);
			sb.append(" - Target=").append(target);
			sb.append("/Port=").append(port);
			sb.append("/Priority=").append(priority);
			sb.append("/Weight=").append(weight);
			return sb;
		}

		public String getService() {
			ByteChars name = getName();
			int pos = name.indexOf('.');
			if (pos == -1) return null;
			return name.toString(1, pos-1); //leading underscore is not regarded as part of the Service ID
		}

		public String getProtocol() {
			ByteChars name = getName();
			int pos1 = name.indexOf('.');
			int pos2 = (pos1 == -1 ? -1 : name.indexOf(pos1+1, '.'));
			if (pos2 == -1) return null;
			return name.toString(pos1 + 2, pos2 - pos1 - 2); //leading underscore is not regarded as part of protocol name
		}

		public String getDomain() {
			ByteChars name = getName();
			int pos = name.indexOf('.');
			if (pos != -1) pos = name.indexOf(pos+1, '.');
			if (pos == -1) return null;
			return name.toString(pos + 1, name.size() - pos - 1);
		}
	}


	// See RFC-3596 for RDATA format
	public static final class RR_AAAA extends ResourceData
	{
		private final byte[] ipv6;
		public byte[] getIP6() {return ipv6;}
		void setIP6(byte[] addr, int off) {System.arraycopy(addr, off, ipv6, 0, ipv6.length);}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_AAAA;}

		public RR_AAAA(ByteChars n, byte[] addr, long expiry) {
			super(n, 0, expiry);
			ipv6 = addr;
		}

		RR_AAAA(RR_AAAA src, ByteChars qn) {
			super(src, qn);
			if (src == null) {
				ipv6 = new byte[IP.IPV6ADDR_OCTETS];
			} else {
				ipv6 = java.util.Arrays.copyOf(src.ipv6, src.ipv6.length);
			}
		}

		@Override
		public StringBuilder toString(StringBuilder sb) {
			sb = super.toString(sb);
			if (ipv6 != null) {
				sb.append(" - IPv6=");
				sb = IP.displayIP6(ipv6, 0, sb);
			}
			return sb;
		}
	}


	public static final class RR_TXT extends ResourceData
	{
		private final java.util.ArrayList<String> data = new java.util.ArrayList<String>();

		public int count() {return data.size();}
		public String getData(int idx) {return data.get(idx);}
		void addData(String s) {data.add(s);}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_TXT;}
		@Override
		public void reset(byte clss) {super.reset(clss); data.clear();}

		public RR_TXT(ByteChars n, long expiry, java.util.Collection<String> l) {
			super(n, 0, expiry);
			if (l != null) data.addAll(l);
		}

		RR_TXT(RR_TXT src, ByteChars qn) {
			super(src, qn);
			if (src != null) data.addAll(src.data);
		}

		@Override
		public StringBuilder toString(StringBuilder sb) {
			sb = super.toString(sb);
			if (data.size() != 0) sb.append(" - Data=").append(data.size()).append('/').append(data);
			return sb;
		}
	}


	//Subclasses RR_A out of laziness as it shares the same fields
	public static final class RR_PTR extends RR_A {
		public RR_PTR(ByteChars hostname, int ip, long expiry) {super(hostname, ip, expiry);}
		RR_PTR(RR_PTR src) {super(src, null);}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_PTR;}
	}

	//Subclasses RR_NS out of laziness, as this RR also maps an RR name to a hostname value (although it doesn't use IP)
	public static final class RR_CNAME extends RR_NS {
		public RR_CNAME(ByteChars alias, ByteChars hostname, long expiry) {super(alias, hostname, expiry);}
		RR_CNAME(RR_CNAME src, ByteChars qn) {super(src, qn);}
		@Override
		public int rrType() {return ResolverDNS.QTYPE_CNAME;}
	}
}
