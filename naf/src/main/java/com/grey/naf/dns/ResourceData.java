/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

public final class ResourceData
{
	// For QTYPE=A, domnam is the resource record's header name, whereas for MX, CNAME, PTR and NS, it's the name in the RDATA section
	// Because so many 10s of 1000s of instances of this class may potentially exist, we've tried to optimise its storage requirements by pruning
	// the Type and Class back to their minimum practical bit size. Even though they are technically 16-bit quantities, there are no defined values
	// greater than a byte, so we can get away with this (but beware of sign extension).
	public byte rrtype; //Resolver.QTYPE_x values
	public byte rrclass; //Packet.QCLASS_x values
	public com.grey.base.utils.ByteChars domnam;
	public int ipaddr;
	public short pref;

	// Careful with this one!  In the DNS protocol's RRs, this is a duration (in seconds), but in this class
	// it represents the absolute system time at which the RR expires.
	public long ttl;

	public ResourceData() {}

	public ResourceData(byte type, com.grey.base.utils.ByteChars name, int ip, long ttlsecs)
	{
		rrtype = type;
		domnam = name;
		ipaddr = ip;
		ttl = ttlsecs;
		rrclass = Packet.QCLASS_INET;
		pref = 0;
	}

	public ResourceData(ResourceData src, boolean allocName)
	{
		set(src, allocName);
	}

	public ResourceData(Packet pkt, int off, byte type, byte clss, long ttlsecs, int rrlen, com.grey.base.utils.ByteChars rrname)
	{
		set(pkt, off, type, clss, ttlsecs, rrlen, rrname);
	}

	public ResourceData set(ResourceData src, boolean allocName)
	{
		rrtype = src.rrtype;
		rrclass = src.rrclass;
		ipaddr = src.ipaddr;
		ttl = src.ttl;
		pref = src.pref;

		if (allocName) {
			if (domnam == null) {
				domnam = new com.grey.base.utils.ByteChars(src.domnam, true);
			} else {
				// this only allocates new ByteChars if existing buffer too small - else we reuse current 'domnam' object
				domnam = src.domnam.copy(domnam);
			}
		} else {
			if (domnam != null) domnam.ar_len = 0;
		}
		return this;
	}

	// constructs an RDATA record from the raw packet RDATA buffer and some other Resource Record parameters supplied by caller
	public ResourceData set(Packet pkt, int off, byte type, byte clss, long ttlsecs, int rrlen, com.grey.base.utils.ByteChars rrname)
	{
		rrtype = type;
		rrclass = clss;
		ttl = ttlsecs;
		domnam = rrname;
		ipaddr = 0;
		pref = 0;

		switch (rrtype)
		{
		case Resolver.QTYPE_A:
			ipaddr = pkt.decodeIP(off);
			break;

		case Resolver.QTYPE_MX:
			pref = (short)pkt.decodeInt16(off);
			off += 2;
			if (rrname != null) parseName(pkt, off, rrname);  // overwrite supplied buffer (which domnam points at)
			break;

		case Resolver.QTYPE_CNAME:
		case Resolver.QTYPE_NS:
		case Resolver.QTYPE_PTR:
			if (rrname != null) parseName(pkt, off, rrname);  // overwrite supplied buffer (which domnam points at)
			break;

		default:
			// ignore unrecognised types
			break;
		}
		return this;
	}
	
	private int parseName(Packet pkt, int off, com.grey.base.utils.ByteChars namebuf)
	{
		namebuf.ar_off = 0;
		namebuf.ar_len = 0;
		off = pkt.decodeName(off, namebuf);
		return off;
	}

	@Override
	public String toString()
	{
		return toString(new StringBuilder(128)).toString();
	}

	public StringBuilder toString(StringBuilder sb)
	{
		if (rrtype == Resolver.QTYPE_NEGATIVE) {
			sb.append("NEGATIVE");
		} else {
			sb.append("RR=").append(rrtype & 0xff).append(':').append(rrclass & 0xff);
			if (domnam != null) sb.append('/').append(domnam);
			if (ipaddr != 0) {
				sb.append("/IP=");
				com.grey.base.utils.IP.displayDottedIP(ipaddr, sb);	
			}
		}
		sb.append("/TTL=");
		com.grey.base.utils.TimeOps.expandMilliTime(ttl - System.currentTimeMillis(), sb, false);
		if (pref != 0) sb.append("/Pref="+pref);
		return sb;
	}
}
