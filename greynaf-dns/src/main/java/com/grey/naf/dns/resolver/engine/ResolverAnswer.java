/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.engine;

import com.grey.naf.dns.resolver.ResolverDNS;

public class ResolverAnswer
{
	public enum STATUS {OK, NODOMAIN, BADNAME, BADRESPONSE, ERROR, TIMEOUT, DEADLOCK, SHUTDOWN}

	// Query info
	public byte qtype;
	public com.grey.base.utils.ByteChars qname;
	public int qip;

	// Query result
	// rrdata is guaranteed non-empty if result=OK (unless FLAG_NOQRY or FLAG_SYNTAXONLY was set)
	public STATUS result;
	public final java.util.ArrayList<ResourceData> rrdata = new java.util.ArrayList<ResourceData>();
	int ip_responder;

	public int size() {return rrdata.size();}
	public ResourceData get(int index) {return rrdata.get(index);} //generic getter
	public ResourceData.RR_A getA() {return (ResourceData.RR_A)rrdata.get(0);}
	public ResourceData.RR_PTR getPTR() {return (ResourceData.RR_PTR)rrdata.get(0);}
	public ResourceData.RR_NS getNS(int index) {return (ResourceData.RR_NS)rrdata.get(index);}
	public ResourceData.RR_SOA getSOA() {return (ResourceData.RR_SOA)rrdata.get(0);}
	public ResourceData.RR_MX getMX(int index) {return (ResourceData.RR_MX)rrdata.get(index);}
	public ResourceData.RR_TXT getTXT() {return (ResourceData.RR_TXT)rrdata.get(0);}
	public ResourceData.RR_SRV getSRV(int index) {return (ResourceData.RR_SRV)rrdata.get(index);}
	public ResourceData.RR_AAAA getAAAA() {return (ResourceData.RR_AAAA)rrdata.get(0);}

	// set result for non-IP query - caller still has to set rrdata afterwards
	public ResolverAnswer set(STATUS res, byte qt, com.grey.base.utils.ByteChars qn)
	{
		qname = qn;
		qip = 0;
		return reset(res, qt);
	}

	// set result for IP-based query - caller still has to set rrdata afterwards
	public ResolverAnswer set(STATUS res, byte qt, int ip)
	{
		qip = ip;
		qname = null;
		return reset(res, qt);
	}

	public ResolverAnswer set(ResolverAnswer ans2)
	{
		if (ans2.qname == null) {
			set(ans2.result, ans2.qtype, ans2.qip);
		} else {
			set(ans2.result, ans2.qtype, ans2.qname);
		}
		for (int idx = 0; idx != ans2.rrdata.size(); idx++) {
			rrdata.add(ans2.rrdata.get(idx));
		}
		return this;
	}

	private ResolverAnswer reset(STATUS res, byte qt)
	{
		result = res;
		qtype = qt;
		rrdata.clear();
		return this;
	}
	
	public ResolverAnswer clear()
	{
		qtype = 0;
		qip = 0;
		qname = null;
		result = null;
		ip_responder = 0;
		rrdata.clear();
		return this;
	}

	@Override
	public String toString()
	{
		return toString(new StringBuilder(200)).toString();
	}

	public StringBuilder toString(StringBuilder sb)
	{
		sb.append("Result=").append(result).append("/Type=").append(ResolverDNS.getQTYPE(qtype)).append('/');
		if (qname == null) {
			sb.append("IP=");
			com.grey.base.utils.IP.displayDottedIP(qip, sb);
		} else {
			sb.append(qname);
		}
		if (result == STATUS.OK) sb.append("/RRs=").append(rrdata.size());
		String dlm = " {";
		for (int idx = 0; idx != rrdata.size(); idx++) {
			sb.append(dlm);
			rrdata.get(idx).toString(sb);
			dlm = "; ";
		}
		if (rrdata.size() != 0) sb.append('}');
		return sb;
	}

	public int getRCODE() {
		switch (result) {
		case OK:
			return PacketDNS.RCODE_OK;
		case NODOMAIN:
			return PacketDNS.RCODE_NXDOM;
		case BADNAME:
			return PacketDNS.RCODE_BADFMT;
		case SHUTDOWN:
			return PacketDNS.RCODE_REJ;
		default:
			break;
		}
		return PacketDNS.RCODE_SRVFAIL;
	}
}