/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver;

import java.util.function.Supplier;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.IP;
import com.grey.naf.dns.resolver.distributed.DistributedResolver;
import com.grey.naf.dns.resolver.embedded.EmbeddedResolver;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.DispatcherRunnable;

/**
 * This class represents a DNS-Resolver API for NAF applications, ie. applications running in the context of a Dispatcher.
 * External clients should use DNSClient as their resolver API.
 */
public abstract class ResolverDNS implements DispatcherRunnable
{
	// This interface should be implemented by all users of the DNS-Resolver API and allows them to receive responses
	public interface Client
	{
		public void dnsResolved(Dispatcher dsptch, ResolverAnswer answer, Object callerparam);
	}

	public static final int FLAG_NOQRY = 1 << 0; //give up if answer not already in cache
	public static final int FLAG_SYNTAXONLY = 1 << 1; //don't do any lookup or query at all
	public static final int FLAG_MUSTHAVEDOTS = 1 << 2;
	public static final int FLAG_NODOTTEDIP = 1 << 3;

	public static final int MAXDOMAIN = 255;
	public static final int MAXNAMELABEL = 63;
	public static final char DOMDLM = '.';

	public static final byte QTYPE_A = 1;
	public static final byte QTYPE_NS = 2;
	public static final byte QTYPE_CNAME = 5;
	public static final byte QTYPE_SOA = 6;
	public static final byte QTYPE_PTR = 12;
	public static final byte QTYPE_MX = 15;
	public static final byte QTYPE_TXT = 16;
	public static final byte QTYPE_RP = 17;
	public static final byte QTYPE_AAAA = 28;
	public static final byte QTYPE_LOC = 29;
	public static final byte QTYPE_SRV = 33;
	public static final byte QTYPE_CERT = 37;
	public static final byte QTYPE_DNAME = 39;
	public static final byte QTYPE_EDNSOPT = 41;
	public static final byte QTYPE_SPF = 99;
	public static final byte QTYPE_IXFR = (byte)251;
	public static final byte QTYPE_AXFR = (byte)252;
	public static final byte QTYPE_ALL = (byte)255;

	private static final String[] qtype_txt = DynLoader.generateSymbolNames(ResolverDNS.class, "QTYPE_", 255);
	public static String getQTYPE(int qtype) {return qtype_txt[qtype & 0xff];}

	abstract public Dispatcher getMasterDispatcher();
	abstract public int cancel(Client caller) throws java.io.IOException;
	abstract protected ResolverAnswer resolve(byte qtype, ByteChars qname, Client caller,
			Object cbdata, int flags) throws java.io.IOException;
	abstract protected ResolverAnswer resolve(byte qtype, int qip, Client caller, Object cbdata, int flags) throws java.io.IOException;

	private final Dispatcher dsptch;
	@Override
	public String getName() {return "DNS-Resolver";}
	@Override
	public Dispatcher getDispatcher() {return dsptch;}

	// these are just temporary work areas, pre-allocated for efficiency
	private final ResolverAnswer dnsAnswer = new ResolverAnswer();
	private final ResolverAnswer answerA = new ResolverAnswer();
	private final ResolverAnswer answerLocalIP = new ResolverAnswer();

	public static ResolverDNS create(Dispatcher d, ResolverConfig config) {
		Supplier<ResolverDNS> func = () -> {
			ResolverDNS r = null;
			try {
				r = (config.isDistributed() ? new DistributedResolver(d, config) : new EmbeddedResolver(d, config));
				d.loadRunnable(r);
			} catch (Exception ex) {
				throw new NAFConfigException("Failed to start DNS-Resolver="+r+" for Dispatcher="+d.getName()+" with distributed="+config.isDistributed(), ex);
			}
			return r;
		};
		return d.getNamedItem(ResolverDNS.class.getName(), func);
	}

	protected ResolverDNS(Dispatcher d)
	{
		dsptch = d;

		answerA.set(ResolverAnswer.STATUS.OK, QTYPE_A, null);
		answerA.rrdata.add(new ResourceData.RR_A(new ByteChars(), 0, Long.MAX_VALUE));

		answerLocalIP.set(ResolverAnswer.STATUS.OK, QTYPE_PTR, IP.IP_LOCALHOST);
		answerLocalIP.rrdata.add(new ResourceData.RR_PTR(new ByteChars("localhost"), IP.IP_LOCALHOST, Long.MAX_VALUE));
	}

	public final ResolverAnswer resolveHostname(ByteChars hostname, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		boolean allowDottedIP = ((flags & FLAG_NODOTTEDIP) == 0);
		boolean have_ip = false;
		int ipaddr = 0;

		if (StringOps.sameSeqNoCase("localhost", hostname)
				|| StringOps.sameSeqNoCase("IPv6:::1", hostname) //Thunderbird uses this as HELO name - thanks Mozilla!
				|| StringOps.sameSeqNoCase("::1", hostname)) {
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
			answerA.rrdata.get(0).setIP(ipaddr);
			answerA.rrdata.get(0).setName(answerA.qname);
			return answerA;
		}
		ResolverAnswer answer = verifyQuery(QTYPE_A, hostname, flags);
		if (answer != null) return answer;
		return resolveDomain(QTYPE_A, hostname, caller, cbdata, flags, false);
	}

	public final ResolverAnswer resolveIP(int ipaddr, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		if (ipaddr == 0 || ipaddr == -1) return dnsAnswer.set(ResolverAnswer.STATUS.BADNAME, QTYPE_PTR, ipaddr);
		if (ipaddr == IP.IP_LOCALHOST) return answerLocalIP;
		return resolve(QTYPE_PTR, ipaddr, caller, cbdata, flags);
	}

	public final ResolverAnswer resolveMailDomain(ByteChars maildom, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		return resolveDomain(QTYPE_MX, maildom, caller, cbdata, flags, true);
	}

	public final ResolverAnswer resolveNameServer(ByteChars domnam, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		return resolveDomain(QTYPE_NS, domnam, caller, cbdata, flags, true);
	}

	public final ResolverAnswer resolveSOA(ByteChars domnam, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		return resolveDomain(QTYPE_SOA, domnam, caller, cbdata, flags, true);
	}

	public final ResolverAnswer resolveSRV(ByteChars domnam, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		return resolveDomain(QTYPE_SRV, domnam, caller, cbdata, flags, true);
	}

	public final ResolverAnswer resolveTXT(ByteChars domnam, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		return resolveDomain(QTYPE_TXT, domnam, caller, cbdata, flags, true);
	}

	public final ResolverAnswer resolveAAAA(ByteChars domnam, Client caller, Object cbdata, int flags)
			throws java.io.IOException
	{
		return resolveDomain(QTYPE_AAAA, domnam, caller, cbdata, flags, true);
	}

	private ResolverAnswer resolveDomain(byte qtype, ByteChars qname, Client caller, Object cbdata, int flags, boolean verify)
			throws java.io.IOException
	{
		if (verify) {
			ResolverAnswer answer = verifyQuery(qtype, qname, flags);
			if (answer != null) return answer;
		}
		if ((flags & FLAG_SYNTAXONLY) != 0) return dnsAnswer.set(ResolverAnswer.STATUS.OK, qtype, qname);
		return resolve(qtype, qname, caller, cbdata, flags);
	}

	// does some checks common to all query types
	private ResolverAnswer verifyQuery(byte qtype, ByteChars qname, int flags)
	{
		if (qname == null || qname.size() == 0 || qname.size() > MAXDOMAIN) {
			return dnsAnswer.set(ResolverAnswer.STATUS.BADNAME, qtype, qname);
		}
		byte[] buf = qname.buffer();
		int off = qname.offset();
		int limit = off + qname.size();
		int dotcnt = 0;

		// don't allow leading (or consecutive - see below) dots - not possible to encode empty labels in DNS packets
		if (buf[off] == DOMDLM) return dnsAnswer.set(ResolverAnswer.STATUS.BADNAME, qtype, qname);

		while (off != limit) {
			if (buf[off] == DOMDLM) {
				if (off + 1 != limit && buf[off + 1] == DOMDLM) {
					return dnsAnswer.set(ResolverAnswer.STATUS.BADNAME, qtype, qname);
				}
				dotcnt++;
			} else {
				// underscores are not RFC-legal (see RFC-1035 section 3.5), but safest to tolerate them
				if (buf[off] != '-' && buf[off] != '_' && !Character.isLetterOrDigit(buf[off])) {
					return dnsAnswer.set(ResolverAnswer.STATUS.BADNAME, qtype, qname);
				}
			}
			off++;
		}

		if ((dotcnt == 0) && ((flags & FLAG_MUSTHAVEDOTS) != 0)) {
			return dnsAnswer.set(ResolverAnswer.STATUS.BADNAME, qtype, qname);
		}
		if (qtype == QTYPE_SRV && dotcnt < 2) {
			// format should be _service._proto.domain_name
			return dnsAnswer.set(ResolverAnswer.STATUS.BADNAME, qtype, qname);
		}
		return null;
	}
}