/*
 * Copyright 2014-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;

import com.grey.base.config.SysProps;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.FileOps;
import com.grey.base.collections.HashedMap;
import com.grey.base.utils.IP;
import com.grey.base.utils.TimeOps;

final class Config
{
	private static final String ROOTDOM = ".";
	public static final ByteChars ROOTDOM_BC = new ByteChars(".");
	private static final String NSMODE_AUTO = "AUTO";
	private static final String NSMODE_MANUAL = "MANUAL";

	// UDP max: add a small bit extra to allow for sloppy encoding by remote host (NB: no reason to suspect that)
	public static final int PKTSIZ_UDP = SysProps.get("greynaf.dns.maxudp", Packet.UDPMAXMSG + 64);
	// TCP max: allow for larger TCP messages (but we only really expect a fraction larger, not 4-fold)
	public static final int PKTSIZ_TCP = SysProps.get("greynaf.dns.maxtcp", Packet.UDPMAXMSG * 4);
	// Linux limit is 128K, while Windows seems to accept just about anything
	public static final int UDPSOCKBUFSIZ = SysProps.get("greynaf.dns.sockbuf", Packet.UDPMAXMSG * 128);
	public static final boolean DIRECTNIOBUFS = com.grey.naf.BufferSpec.directniobufs;
	private static final int ALTPORT_DNS = SysProps.get("greynaf.dns.altport", Packet.INETPORT);

	private static final String JNDIFACT_DNS = "com.sun.jndi.dns.DnsContextFactory";
	private static final String URL_DNS = "dns://";
	private static final String LOGLBL = "DNS-Resolver: ";
	public static final com.grey.logging.Logger.LEVEL DEBUGLVL = com.grey.logging.Logger.LEVEL.TRC2;

	public final boolean recursive; //true means we can issue recursive queries to dns_localservers
	public final boolean always_tcp;
	public final boolean partial_prune;
	public final boolean cache_all_glue;
	public final long negttl; //how long to cache DNS no-domain answers (negative TTL)
	public final long minttl_initial;
	public final long minttl_lookup;
	public final boolean setminttl;
	public final boolean validate_srvip;
	public final boolean dump_on_exit;
	public final int retrymax; //max UDP retries - 0 means try once, with no retries
	public final long retrytmt; //timeout on DNS/UDP requests
	public final long retrytmt_tcp; //UDP/TCP - make it long enough that we don't preempt server's idle-connection close
	public final long retrystep; //number of milliseconds to increment DNS timeout by on each retry
	public final long wrapretryfreq; //interval for retrying queries blocked by QID wraparound
	public final int cache_lowater_a;
	public final int cache_hiwater_a;
	public final int cache_lowater_ptr;
	public final int cache_hiwater_ptr;
	public final int cache_lowater_soa;
	public final int cache_hiwater_soa;
	public final int cache_lowater_ns;
	public final int cache_hiwater_ns;
	public final int cache_lowater_mx;
	public final int cache_hiwater_mx;
	public final int ns_maxrr;
	public final int mx_maxrr;
	public final String[] dns_localservers; //our own recursive DNS servers
	public final int udp_sendersockets;
	public final int dns_port;
	public final java.net.InetAddress dns_interceptor;

	private final String pthnam_roots;
	private final boolean auto_roots;

	public Config(com.grey.base.config.XmlConfig cfg, com.grey.logging.Logger logger)
		throws com.grey.base.ConfigException, javax.naming.NamingException, java.net.UnknownHostException
	{
		String srvnames_txt = "n/a";
		recursive = cfg.getBool("@recursive", true);
		always_tcp = cfg.getBool("@alwaystcp", false);
		validate_srvip = cfg.getBool("@validate_response_ip", false);
		cache_all_glue = cfg.getBool("@nonbailiwick_glue", true);
		long _negttl = cfg.getTime("@negttl", "1h");
		long _minttl_initial = cfg.getTime("@minttl_initial", "5m");
		long _minttl_lookup = cfg.getTime("@minttl_lookup", "1m");
		setminttl = cfg.getBool("@setminttl", false);
		partial_prune = cfg.getBool("@partialprune", false);
		dump_on_exit = cfg.getBool("@exitdump", false);
		wrapretryfreq = cfg.getTime("@wrapretry", "1s");
		retrymax = cfg.getInt("retry/@max", false, 3);
		retrytmt = cfg.getTime("retry/@timeout", "10s");
		retrytmt_tcp = cfg.getTime("retry/@timeout_tcp", "60s");
		retrystep = cfg.getTime("retry/@backoff", "3s");
		cache_hiwater_a = cfg.getInt("cache_a/@hiwater", false, 0);
		cache_lowater_a = getLowater(cfg, "cache_a/@lowater", cache_hiwater_a);
		cache_hiwater_ptr = cfg.getInt("cache_ptr/@hiwater", false, 0);
		cache_lowater_ptr = getLowater(cfg, "cache_ptr/@lowater", cache_hiwater_ptr);
		cache_hiwater_soa = cfg.getInt("cache_soa/@hiwater", false, 0);
		cache_lowater_soa = getLowater(cfg, "cache_soa/@lowater", cache_hiwater_soa);
		cache_hiwater_ns = cfg.getInt("cache_ns/@hiwater", false, 0);
		cache_lowater_ns = getLowater(cfg, "cache_ns/@lowater", cache_hiwater_ns);
		ns_maxrr = cfg.getInt("cache_ns/@maxrr", false, 0);
		cache_hiwater_mx = cfg.getInt("cache_mx/@hiwater", false, 0);
		cache_lowater_mx = getLowater(cfg, "cache_mx/@lowater", cache_hiwater_mx);
		mx_maxrr = cfg.getInt("cache_mx/@maxrr", false, 0);
		pthnam_roots = (recursive ? null : cfg.getValue("rootservers", false, null));
		auto_roots = (recursive ? false : cfg.getBool("rootservers/@auto", true));
		udp_sendersockets = (always_tcp ? 0 : cfg.getInt("udpsockets", true, 2));
		dns_port = cfg.getInt("interceptor/@port", true, ALTPORT_DNS);
		String host = cfg.getValue("interceptor/@host", false, null);
		dns_interceptor = (host == null ? null : IP.getHostByName(host));

		// RRs that are so short-lived as to disappear even as we construct an Answer would cause problems
		if (_minttl_initial < TimeOps.MSECS_PER_MINUTE) _minttl_initial = TimeOps.MSECS_PER_MINUTE;
		if (_negttl < _minttl_initial) _negttl = _minttl_initial;
		minttl_initial = _minttl_initial;
		minttl_lookup = _minttl_lookup;
		negttl = _negttl;

		String srvnames_sys = getLocalServers(logger);
		if (srvnames_sys == null) srvnames_sys = "127.0.0.1";
		String[] srvnames_cfg = cfg.getTuple("localservers", "|", false, srvnames_sys);

		if (srvnames_cfg == null) {
			dns_localservers = null;
		} else {
			java.util.ArrayList<String> lst = new java.util.ArrayList<String>();

			srvnames_txt = "";
			String dlm = "";
			for (int idx = 0; idx != srvnames_cfg.length; idx++) {
				String srvname = srvnames_cfg[idx].trim();
				if (srvname.length() == 0) continue;
				lst.add(srvname);
				srvnames_txt = srvnames_txt + dlm + srvname;
				dlm = " | ";
			}
			srvnames_txt = lst.size()+" ["+srvnames_txt+"]";
			dns_localservers = (lst.size() == 0 ? null : lst.toArray(new String[lst.size()]));
		}

		if (recursive) {
			if (dns_localservers == null) throw new com.grey.base.ConfigException(LOGLBL+"Recursive mode requires recursive DNS servers to be available");
		} else {
			if (auto_roots) {
				if (dns_localservers == null) throw new com.grey.base.ConfigException(LOGLBL+"Auto-rootservers requires recursive DNS servers to be available");
			} else {
				if (pthnam_roots == null) throw new com.grey.base.ConfigException(LOGLBL+"Must specify roots file, if auto-bootstrap is disabled");
			}
		}

		long tmt = retrytmt + (retrystep / 2);
		for (int idx = 0; idx <= retrymax; idx++) {
			tmt += retrytmt + (retrystep * idx) + (retrystep / 2);
		}
		logger.info(LOGLBL+"Configured DNS servers: "+srvnames_txt);
		logger.info(LOGLBL+"UDP-sender-sockets="+udp_sendersockets+"; TCP-only="+always_tcp);
		if (dns_interceptor != null || dns_port != Packet.INETPORT) {
			logger.info(LOGLBL+"DNS-Interceptor mode on: port="+dns_port+", host="+dns_interceptor);
		}
		logger.info(LOGLBL+"Recursive="+recursive+(recursive?"":" - auto="+auto_roots+", roots-file="+pthnam_roots));
		logger.info(LOGLBL+"cache_nonbailiwick_glue="+cache_all_glue
				+"; validate_server_ip="+validate_srvip);
		logger.info(LOGLBL+"retry-timeout="+TimeOps.expandMilliTime(retrytmt)
				+"/"+TimeOps.expandMilliTime(retrystep)
				+"; max-retries="+retrymax+" (window="+TimeOps.expandMilliTime(tmt)
				+") - timeout-TCP="+TimeOps.expandMilliTime(retrytmt_tcp));
		logger.trace(LOGLBL+"negative-TTL="+TimeOps.expandMilliTime(negttl)
				+"; minttl_initial="+TimeOps.expandMilliTime(minttl_initial)
				+"; minttl_lookup="+TimeOps.expandMilliTime(minttl_lookup));
		logger.trace(LOGLBL+"A-cache: lowater="+cache_lowater_a+", hiwater="+cache_hiwater_a);
		logger.trace(LOGLBL+"PTR-cache: lowater="+cache_lowater_ptr+", hiwater="+cache_hiwater_ptr);
		logger.trace(LOGLBL+"SOA-cache: lowater="+cache_lowater_soa+", hiwater="+cache_hiwater_soa);
		logger.trace(LOGLBL+"NS-cache: lowater="+cache_lowater_ns+", hiwater="+cache_hiwater_ns+", maxrr="+ns_maxrr);
		logger.trace(LOGLBL+"MX-cache: lowater="+cache_lowater_mx+", hiwater="+cache_hiwater_mx+", maxrr="+mx_maxrr);
		logger.trace(LOGLBL+"Partial cache prune="+partial_prune+", dump-on-exit="+dump_on_exit);
		logger.trace(LOGLBL+"directbufs="+DIRECTNIOBUFS+"; udpmax="+PKTSIZ_UDP+"; tcpmax="+PKTSIZ_TCP);
	}

	private int getLowater(com.grey.base.config.XmlConfig cfg, String xpath, int hiwater)
			throws com.grey.base.ConfigException
	{
		int lowater = cfg.getInt(xpath, false, hiwater/2);
		if (lowater > hiwater) throw new com.grey.base.ConfigException("lowater="+lowater+" exceeds hiwater="+hiwater+" - "+xpath);
		return lowater;
	}

	public HashedMap<ByteChars, java.util.ArrayList<ResourceData>> loadRootServers(com.grey.logging.Logger logger)
			throws com.grey.base.ConfigException, javax.naming.NamingException, java.io.IOException
	{
		HashedMap<ByteChars, java.util.ArrayList<ResourceData>> roots = new HashedMap<ByteChars, java.util.ArrayList<ResourceData>>();
		boolean want_roots = auto_roots;
		if (auto_roots) {
			for (int idx = 0; idx != dns_localservers.length; idx++) {
				java.util.ArrayList<ResourceData> lst = lookupNameServers(ROOTDOM, dns_localservers[0], logger);
				if (lst.size() != 0) {
					roots.put(ROOTDOM_BC, lst);
					break;
				}
			}
		}
		if (pthnam_roots != null) {
			java.io.File fh = new java.io.File(pthnam_roots);
			RootsParser parser = new RootsParser(pthnam_roots, roots, logger);
			FileOps.readTextLines(fh, parser, 4096, "#", 0, null);
			if (!want_roots) want_roots = parser.want_roots;
		}
		if (roots.isEmpty()) {
			throw new com.grey.base.ConfigException(LOGLBL+"No root servers found - auto="+auto_roots+", file="+pthnam_roots);
		}
		if (want_roots && !roots.containsKey(ROOTDOM_BC)) {
			throw new com.grey.base.ConfigException(LOGLBL+"No global root servers found - auto="+auto_roots+", file="+pthnam_roots);
		}
		return roots;
	}

	// Get the local DNS servers willing provide a recursive lookup service for us.
	// NB: This method could also use sun.net.dns.ResolverConfiguration.open().nameservers(), but that's not portable
	private static String getLocalServers(com.grey.logging.Logger logger) throws javax.naming.NamingException
	{
		javax.naming.directory.DirContext ctx = getDnsContext(null);
		java.util.Hashtable<?, ?> env = ctx.getEnvironment(); //NB: Does not return same object we passed into constructor
		ctx.close();
		Object providers = env.get(javax.naming.Context.PROVIDER_URL);
		logger.info(LOGLBL+"Default DNS servers ["+providers+"]");
		if (providers == null) return null;
		String[] serverspecs = ((String)providers).split(" ");

		for (int idx = 0; idx != serverspecs.length; idx++) {
			int pos = serverspecs[idx].indexOf(URL_DNS);
			if (pos != -1) serverspecs[idx] = serverspecs[idx].substring(pos + URL_DNS.length());
		}
		String servers = null;

		for (int idx = 0; idx != serverspecs.length; idx++) {
			if (serverspecs[idx] == null || serverspecs[idx].trim().length() == 0) continue;
			if (servers == null) {
				servers = serverspecs[idx];
			} else {
				servers = servers+" | "+serverspecs[idx];
			}
		}
		return servers;
	}

	static java.util.ArrayList<ResourceData> lookupNameServers(String domnam, String dns_server, com.grey.logging.Logger logger)
			throws javax.naming.NamingException
	{
		ByteChars domnam_bc = createRRName(domnam);
		java.util.ArrayList<ResourceData> lst = new java.util.ArrayList<ResourceData>();
		javax.naming.directory.DirContext ctx = getDnsContext(dns_server);
		javax.naming.directory.Attributes domservers = ctx.getAttributes(domnam, new String[] {"NS"});
		Attribute servers_ns = domservers.get("NS"); //NS is the only attribute we asked for, so this shortcuts getAll()
		NamingEnumeration<?> en1 = servers_ns.getAll();
		while (en1.hasMore()) {
			// Each NS attribute is just a hostname. In turn, the getAttributes() on that hostname would usually
			// return other types of attributes (AAAA, MX, etc) if we didn't specify only A.
			String hostname = (String)en1.next();
			// Could just call java.net.InetAddress.getByName() on this, but let's stick with JNDI
			javax.naming.directory.Attributes domserver = ctx.getAttributes(hostname, new String[] {"A"});
			Attribute domserver_a = domserver.get("A");
			// The A attributes are the host's IP addresses (in fact there seems to be only one of them), with no
			// other RR attributes such as the TTL available.
			// The failure of an A query on an individual name-servers indicates some sort of delegation/glue by
			// its domain admins, but be prepared to handle it.
			if (domserver_a == null) {
				logger.trace(LOGLBL+"DNS-server="+dns_server+" failed to resolve nameserver="+hostname+" for domain="+domnam);
				continue;
			}
			ByteChars hostname_bc = createRRName(hostname);
			String ipstr = (String)domserver_a.get(0);
			int ip = IP.convertDottedIP(ipstr);
			ResourceData rr = new ResourceData.RR_NS(domnam_bc, hostname_bc, ip, Long.MAX_VALUE);
			lst.add(rr);
		}
		ctx.close();
		logger.info(LOGLBL+"Retrieved nameservers="+lst.size()+" for domain="+domnam+" from server="+dns_server);
		return lst;
	}

	private static javax.naming.directory.DirContext getDnsContext(String srvname) throws javax.naming.NamingException
	{
		java.util.Hashtable<String, String> env = new java.util.Hashtable<String, String>();
		if (srvname != null) env.put(javax.naming.Context.PROVIDER_URL, URL_DNS+srvname);
		env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, JNDIFACT_DNS);
		return new javax.naming.directory.InitialDirContext(env);
	}


	private static final class RootsParser implements FileOps.LineReader
	{
		public final HashedMap<ByteChars, java.util.ArrayList<ResourceData>> roots;
		public boolean want_roots;
		private final com.grey.logging.Logger logger;
		private final String filename;

		public RootsParser(String fnam, HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache,
				com.grey.logging.Logger l)
		{
			filename = fnam;
			roots = cache;
			logger = l;
		}

		@Override
		public boolean processLine(String line, int lno, int readmode, Object cbdata)
				throws com.grey.base.ConfigException, java.net.UnknownHostException, javax.naming.NamingException
		{
			String[] parts = line.split(":");
			if (parts.length < 2 || parts.length > 3) parts = null;
			String domnam = (parts == null ? null : parts[0].trim().replace('\t', ' '));
			String nsnam = (parts == null ? null : parts[1].trim().replace('\t', ' '));
			String nsmode = (parts == null || parts.length < 3 ? NSMODE_AUTO : parts[2].trim().replace('\t', ' ').toUpperCase());
			if (parts == null || domnam.indexOf(' ')!=-1 || nsnam.indexOf(' ')!=-1
					|| (!nsmode.equals(NSMODE_AUTO) && !nsmode.equals(NSMODE_MANUAL))) {
				throw new com.grey.base.ConfigException(LOGLBL+"Bad syntax at "+filename+" line "+lno
						+"\n\tFormat should be domain name, followed by colon and it's name server,"
						+" followed by optional "+NSMODE_AUTO+"/"+NSMODE_MANUAL+" field"
						+"\n\t"+line);
			}
			ByteChars domnam_bc = createRRName(domnam);
			ByteChars nsnam_bc = createRRName(nsnam);
			nsnam = nsnam_bc.toString(); //need to strip trailing dots for IP.getHostByName()

			if (nsmode.equals(NSMODE_AUTO)) {
				if (domnam.equals(ROOTDOM)) want_roots = true;
				if (!roots.containsKey(domnam_bc)) {
					try {
						java.util.ArrayList<ResourceData> lst = lookupNameServers(domnam, nsnam, logger);
						if (lst.size() != 0) roots.put(domnam_bc, lst);
					} catch (Exception ex) {
						// not fatal yet, because this hints file probably specifies several servers
						logger.warn(LOGLBL+"Domain="+domnam+" lookup failed on server="+nsnam+" - "+ex);
					}
				}
			} else {
				java.net.InetAddress addr = IP.getHostByName(nsnam);
				ResourceData rr = new ResourceData.RR_NS(domnam_bc, nsnam_bc, IP.convertIP(addr), Long.MAX_VALUE);
				java.util.ArrayList<ResourceData> lst = roots.get(domnam_bc);
				if (lst == null) {
					lst = new java.util.ArrayList<ResourceData>();
					roots.put(domnam_bc, lst);
				}
				lst.add(rr);
				logger.info(LOGLBL+"Manually added nameserver="+nsnam+" for domain="+domnam);
			}
			return false;
		}
	}

	// convert to lower case, to be consistent with the way Packet decodes received names into the cache RRs
	static ByteChars createRRName(String name) {
		ByteChars bc = new ByteChars(name.toLowerCase());
		if (!bc.equals(ROOTDOM_BC) && bc.charAt(bc.ar_len-1) == Resolver.DOMDLM) bc.ar_len--;
		return bc;
	}
}
