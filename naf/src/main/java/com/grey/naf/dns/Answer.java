/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

public final class Answer
{
	public enum STATUS {OK, NODOMAIN, BADNAME, TIMEOUT, ERROR, INPROGRESS}

	// Query info
	public byte qtype;
	public com.grey.base.utils.ByteChars qname;
	public int qip;

	// Query result
	// rrdata is guaranteed non-empty if result=OK (unless FLAG_NOQRY or FLAG_SYNTAXONLY was set)
	public STATUS result;
	public final java.util.ArrayList<ResourceData> rrdata = new java.util.ArrayList<ResourceData>();

	// set result for domain-based query - caller still has to set rrdata afterwards
	public Answer set(STATUS result, byte qtype, com.grey.base.utils.ByteChars qname)
	{
		this.qname = qname;
		qip = 0;
		return reset(result, qtype);
	}

	// set result for IP-based query - caller still has to set rrdata afterwards
	public Answer set(STATUS result, byte qtype, int qip)
	{
		this.qip = qip;
		qname = null;
		return reset(result, qtype);
	}

	public Answer set(Answer answer1)
	{
		if (answer1.qname == null) {
			set(answer1.result, answer1.qtype, answer1.qip);
		} else {
			set(answer1.result, answer1.qtype, answer1.qname);
		}
		rrdata.addAll(answer1.rrdata);
		return this;
	}

	public Answer initQuery(byte qtype, com.grey.base.utils.ByteChars qname, int qip)
	{
		if (qname == null) return set(STATUS.INPROGRESS, qtype, qip);
		return set(STATUS.INPROGRESS, qtype, qname);
	}

	private Answer reset(STATUS result, byte qtype)
	{
		this.result = result;
		this.qtype = qtype;
		rrdata.clear();
		return this;
	}

	@Override
	public String toString()
	{
		return toString(new StringBuilder(512)).toString();
	}

	public StringBuilder toString(StringBuilder sb)
	{
		sb.append("Result=").append(result).append("/QTYPE=").append(qtype & 0xff).append('/');
		if (qname == null) {
			sb.append("IP=");
			com.grey.base.utils.IP.displayDottedIP(qip, sb);
		} else {
			sb.append(qname);
		}
		if (result == STATUS.OK) sb.append("/RRs=").append(rrdata.size());
		if (rrdata.size() != 0) {
			for (int idx = 0; idx != rrdata.size(); idx++) {
				sb.append(' ');
				rrdata.get(idx).toString(sb);
			}
		}
		return sb;
	}
}
