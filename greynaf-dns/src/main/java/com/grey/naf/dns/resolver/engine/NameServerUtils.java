/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.engine;

import java.util.List;
import java.util.ArrayList;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.FileOps;
import com.grey.base.collections.HashedMap;
import com.grey.base.utils.IP;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.errors.NAFConfigException;

class NameServerUtils
{
	private static final String ROOTDOM = ".";
	public static final ByteChars ROOTDOM_BC = new ByteChars(".");

	private static final String NSMODE_AUTO = "AUTO";
	private static final String NSMODE_MANUAL = "MANUAL";

	private static final String JNDIFACT_DNS = "com.sun.jndi.dns.DnsContextFactory";
	private static final String URL_DNS = "dns://";
	private static final String LOGLBL = "DNS-Resolver: ";

	public static HashedMap<ByteChars, java.util.ArrayList<ResourceData>> loadRootServers(String[] dns_localservers, String pthnam_roots, boolean auto_roots, com.grey.logging.Logger logger)
			throws javax.naming.NamingException, java.io.IOException
	{
		HashedMap<ByteChars, java.util.ArrayList<ResourceData>> roots = new HashedMap<ByteChars, java.util.ArrayList<ResourceData>>();
		boolean want_roots = auto_roots;
		if (auto_roots) {
			for (int idx = 0; idx != dns_localservers.length; idx++) {
				java.util.ArrayList<ResourceData> lst = null;
				try {
					lst = lookupNameServers(ROOTDOM, dns_localservers[idx], logger);
				} catch (Throwable ex) {
					logger.log(com.grey.logging.Logger.LEVEL.WARN, ex, true, "Failed to look up root nameservers with "+dns_localservers[idx]);
				}
				if (lst != null && !lst.isEmpty()) {
					// we have an answer from the current server
					if (idx != 0) {
						//shuffle to front - will make future lookups faster on misconfigured machines
						String good_server = dns_localservers[idx];
						for (int idx2 = idx; idx2 != 0; idx2--) {
							dns_localservers[idx2] = dns_localservers[idx2 - 1];
						}
						dns_localservers[0] = good_server;
					}
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
			throw new NAFConfigException(LOGLBL+"No root servers found - auto="+auto_roots+", file="+pthnam_roots);
		}
		if (want_roots && !roots.containsKey(ROOTDOM_BC)) {
			throw new NAFConfigException(LOGLBL+"No global root servers found - auto="+auto_roots+", file="+pthnam_roots);
		}
		return roots;
	}

	// Get the local DNS servers willing to provide a recursive lookup service for us.
	// NB: This method could also use sun.net.dns.ResolverConfiguration.open().nameservers(), but that's not portable
	public static List<String> getLocalServers(com.grey.logging.Logger logger) throws javax.naming.NamingException
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
		List<String> servers = new ArrayList<>();

		for (int idx = 0; idx != serverspecs.length; idx++) {
			if (serverspecs[idx] == null) continue;
			String server = serverspecs[idx].trim();
			if (!server.isEmpty()) servers.add(server);
		}
		return servers;
	}

	private static java.util.ArrayList<ResourceData> lookupNameServers(String domnam, String dns_server, com.grey.logging.Logger logger)
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

		public RootsParser(String fnam, HashedMap<ByteChars, java.util.ArrayList<ResourceData>> cache, com.grey.logging.Logger l)
		{
			filename = fnam;
			roots = cache;
			logger = l;
		}

		@Override
		public boolean processLine(String line, int lno, int readmode, Object cbdata)
				throws java.net.UnknownHostException, javax.naming.NamingException
		{
			String[] parts = line.split(":");
			if (parts.length < 2 || parts.length > 3) parts = null;
			String domnam = (parts == null ? null : parts[0].trim().replace('\t', ' '));
			String nsnam = (parts == null ? null : parts[1].trim().replace('\t', ' '));
			String nsmode = (parts == null || parts.length < 3 ? NSMODE_AUTO : parts[2].trim().replace('\t', ' ').toUpperCase());
			if (parts == null || domnam.indexOf(' ')!=-1 || nsnam.indexOf(' ')!=-1
					|| (!nsmode.equals(NSMODE_AUTO) && !nsmode.equals(NSMODE_MANUAL))) {
				throw new NAFConfigException(LOGLBL+"Bad syntax at "+filename+" line "+lno
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
		if (!bc.equals(ROOTDOM_BC) && bc.charAt(bc.size()-1) == ResolverDNS.DOMDLM) bc.incrementSize(-1);
		return bc;
	}
}
