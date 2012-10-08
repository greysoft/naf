/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.utils.IP;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.StringOps;

public abstract class Resolver
{
	public interface Client
	{
		public void dnsResolved(com.grey.naf.reactor.Dispatcher dsptch, Answer answer, Object callerparam) throws java.io.IOException;
	}

	public static final int FLAG_NOQRY = 1 << 0;  //give up if answer not already in cache
	public static final int FLAG_SYNTAXONLY = 1 << 1; //don't do any lookup or query at all
	public static final int FLAG_ISMAILBOX = 1 << 2;
	public static final int FLAG_MUSTHAVEDOTS = 1 << 3;
	public static final int FLAG_NODOTTEDIP = 1 << 4;

	public static final int MAXDOMAIN = 255;
	public static final int MAXNAMELABEL = 63;

	public static final byte QTYPE_A = 1;
	public static final byte QTYPE_NS = 2;
	public static final byte QTYPE_SOA = 6;
	public static final byte QTYPE_CNAME = 5;
	public static final byte QTYPE_PTR = 12;
	public static final byte QTYPE_MX = 15;
	public static final byte QTYPE_TXT = 16;
	public static final byte QTYPE_RP = 17;
	public static final byte QTYPE_AAAA = 28;
	public static final byte QTYPE_LOC = 29;
	public static final byte QTYPE_SRV = 33;
	public static final byte QTYPE_CERT = 37;
	public static final byte QTYPE_DNAME = 39;
	public static final byte QTYPE_SPF = 99;
	public static final byte QTYPE_IXFR = (byte)251;
	public static final byte QTYPE_AXFR = (byte)252;
	public static final byte QTYPE_ALL = (byte)255;
	public static final byte QTYPE_NEGATIVE = 68;  // pseudo-type invented by this package, to indicate that the expected RR does not exist in the DNS
	
	private static final Class<?> DFLTCLASS = com.grey.naf.dns.embedded.EmbeddedResolver.class;

	abstract public void start() throws java.io.IOException;
	abstract public void stop();
	abstract public int cancel(Client caller) throws java.io.IOException;
	abstract protected Answer resolve(byte qtype, ByteChars qname, Client caller,
			Object cbdata, int flags) throws java.io.IOException;
	abstract protected Answer resolve(byte qtype, int qip, Client caller, Object cbdata, int flags) throws java.io.IOException;

	public final com.grey.naf.reactor.Dispatcher dsptch;

	// these are just temporary work areas, pre-allocated for efficiency
	private final Answer dnsAnswer = new Answer();
	private final Answer answerA = new Answer();
	private final Answer answerLocalIP = new Answer();
	private final ByteChars tmplightname = new ByteChars(-1);  // lightweight object without own storage

	public static Resolver create(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException
	{
		return Resolver.class.cast(dsptch.nafcfg.createEntity(cfg, DFLTCLASS, Resolver.class, false,
				new Class<?>[]{com.grey.naf.reactor.Dispatcher.class, com.grey.base.config.XmlConfig.class},
				new Object[]{dsptch, cfg}));
	}

	public Resolver(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		dsptch = d;

		answerA.set(Answer.STATUS.OK, QTYPE_A, null);
		answerA.rrdata.add(new ResourceData(answerA.qtype, null, 0, Long.MAX_VALUE));

		answerLocalIP.set(Answer.STATUS.OK, QTYPE_PTR, IP.IP_LOCALHOST);
		answerLocalIP.rrdata.add(new ResourceData(answerLocalIP.qtype, new ByteChars("localhost"), 0, Long.MAX_VALUE));
	}

	public final Answer resolveHostname(ByteChars hostname, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		boolean allowDottedIP = ((flags & FLAG_NODOTTEDIP) == 0);
		Answer answer = verifyQuery(QTYPE_A, hostname, flags);
		if (answer != null) return answer;
		boolean have_ip = false;
		int ipaddr = 0;

		if (StringOps.sameSeq("localhost", hostname)) {
			ipaddr = IP.IP_LOCALHOST;
			have_ip = true;
		} else if (allowDottedIP) {
			// Attempt to parse it as a dotted IP, and if unsuccessful we assume it's a hostname.
			// Of course, this might be an invalid dotted IP (eg. 999.999.999.999), so would end up trying to
			// resolve would as a DNS type-A record before returning not-found.
			ipaddr = IP.convertDottedIP(hostname);
			have_ip = IP.validDottedIP(hostname, ipaddr);
		}

		if (have_ip) {
			answerA.qname = hostname;
			answerA.rrdata.get(0).ipaddr = ipaddr;
			return answerA;
		}
		return resolveDomain(QTYPE_A, hostname, caller, cbdata, flags);
	}

	public final Answer resolveIP(int ipaddr, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		if (ipaddr == 0 || ipaddr == 0xffffffff) return dnsAnswer.set(Answer.STATUS.BADNAME, QTYPE_PTR, ipaddr);
		if (ipaddr == IP.IP_LOCALHOST) return answerLocalIP;
		return resolve(QTYPE_PTR, ipaddr, caller, cbdata, flags);
	}

	public final Answer resolveMailDomain(ByteChars maildom, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		if ((flags & FLAG_ISMAILBOX) != 0) maildom = maildom.extractTerm(com.grey.base.utils.EmailAddress.DLM, 0, 1, true, tmplightname);
		Answer answer = verifyQuery(QTYPE_MX, maildom, flags);
		if (answer != null) return answer;
		return resolveDomain(QTYPE_MX, maildom, caller, cbdata, flags);
	}

	private Answer resolveDomain(byte qtype, ByteChars qname, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		if ((flags & com.grey.naf.dns.Resolver.FLAG_SYNTAXONLY) != 0) return dnsAnswer.set(Answer.STATUS.OK, qtype, qname);
		return resolve(qtype, qname, caller, cbdata, flags);
	}

	// does some checks common to all query types
	private Answer verifyQuery(byte qtype, ByteChars qname, int flags)
	{
		if (qname == null || qname.ar_len == 0 || qname.ar_len > MAXDOMAIN) {
			return dnsAnswer.set(Answer.STATUS.BADNAME, qtype, qname);
		}
		byte[] buf = qname.ar_buf;
		int off = qname.ar_off;
		int limit = off + qname.ar_len;
		int dotcnt = 0;

		// don't allow leading or trailing (or consecutive - see below) dots - not possible to encode empty labels in DNS packets
		if (buf[off] == '.' || buf[limit - 1] == '.') return dnsAnswer.set(Answer.STATUS.BADNAME, qtype, qname);

		while (off != limit) {
			if (buf[off] == '.') {
				if (buf[off + 1] == '.') {
					return dnsAnswer.set(Answer.STATUS.BADNAME, qtype, qname);
				}
				dotcnt++;
			} else {
				// underscores are not RFC-legal (see RFC-1035 section 3.5), but safest to tolerate them
				if (buf[off] != '-' && buf[off] != '_' && !Character.isLetterOrDigit(buf[off])) {
					return dnsAnswer.set(Answer.STATUS.BADNAME, qtype, qname);
				}
			}
			off++;
		}

		if ((dotcnt == 0) && ((flags & com.grey.naf.dns.Resolver.FLAG_MUSTHAVEDOTS) != 0)) {
			return dnsAnswer.set(Answer.STATUS.BADNAME, qtype, qname);
		}
		return null;
	}
}
