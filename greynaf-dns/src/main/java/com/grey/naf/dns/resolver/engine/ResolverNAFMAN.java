/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.engine;

import com.grey.base.utils.IP;
import com.grey.base.utils.ByteChars;

import com.grey.naf.nafman.NafManRegistry;
import com.grey.naf.nafman.NafManRegistry.DefCommand;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.nafman.NafManCommand;
import com.grey.naf.reactor.Dispatcher;
import com.grey.logging.Logger;

public class ResolverNAFMAN
	implements NafManCommand.Handler
{
	//NAFMAN commands
	private static final String CMD_DNSDUMP = "DNSDUMP";
	private static final String CMD_DNSPRUNE = "DNSPRUNE";
	private static final String CMD_DNSQUERY = "DNSQUERY";
	private static final String CMD_DNSLOADROOTS = "DNSLOADROOTS";

	//NAFMAN attributes
	private static final String MATTR_QTYP = "qt";
	private static final String MATTR_QVAL = "qv";
	private static final String MATTR_DUMPFILE = "df";
	private static final String MATTR_DUMPHTML = "dh";

	private static final String FAMILY_NAFDNS = "NAF-DNS";

	private static final DefCommand[] cmds = new DefCommand[] {
		new DefCommand(CMD_DNSDUMP, FAMILY_NAFDNS, "Dump DNS-Resolver cache and stats", NafManRegistry.RSRC_CMDSTATUS, true),
		new DefCommand(CMD_DNSPRUNE, FAMILY_NAFDNS, "Prune aged entries from DNS cache", NafManRegistry.RSRC_CMDSTATUS, false),
		new DefCommand(CMD_DNSLOADROOTS, FAMILY_NAFDNS, "Reload DNS roots in DNS-Resolver", NafManRegistry.RSRC_CMDSTATUS, false),
		new DefCommand(CMD_DNSQUERY, FAMILY_NAFDNS, "Do synchronous lookup on DNS cache (testing aid)", null, true)
	};

	private final ResolverService resolver;
	private final boolean isRecursive;
	private final java.io.File fh_dump;
	private final Logger logger;

	// temporary work areas, pre-allocated for efficiency
	private final ByteChars tmpbc_nafman = new ByteChars();

	@Override
	public CharSequence nafmanHandlerID() {return "DNS-Resolver";}

	public ResolverNAFMAN(ResolverService rslvr, ResolverConfig config) {
		Dispatcher dsptch = rslvr.getDispatcher();
		resolver = rslvr;
		isRecursive = config.isRecursive();
		logger = dsptch.getLogger();
		fh_dump = new java.io.File(dsptch.getApplicationContext().getConfig().getPathVar()+"/DNSdump-"+dsptch.getName()+".txt");

		if (dsptch.getNafManAgent() != null) {
			NafManRegistry reg = dsptch.getNafManAgent().getRegistry();
			reg.registerCommandFamily(FAMILY_NAFDNS, cmds, null, null);
			reg.registerHandler(CMD_DNSDUMP, 0, this, dsptch);
			reg.registerHandler(CMD_DNSPRUNE, 0, this, dsptch);
			reg.registerHandler(CMD_DNSQUERY, 0, this, dsptch);
			if (!config.isRecursive()) reg.registerHandler(CMD_DNSLOADROOTS, 0, this, dsptch);
		}
	}

	@Override
	public CharSequence handleNAFManCommand(NafManCommand cmd) {
		//use temp StringBuilder, so that we don't hold onto a potentially huge block of memory
		StringBuilder sbrsp = new StringBuilder();
		NafManRegistry.DefCommand def = cmd.getCommandDef();
		CacheManager cachemgr = resolver.getCacheManager();

		if (def.code.equals(CMD_DNSPRUNE)) {
			cachemgr.prune(sbrsp);
		} else if (def.code.equals(CMD_DNSDUMP)) {
			String dh = cmd.getArg(MATTR_DUMPHTML);
			String df = cmd.getArg(MATTR_DUMPFILE);
			if (!"N".equalsIgnoreCase(dh)) {
				resolver.dumpState("<br/>", sbrsp);
			}
			if (!"N".equalsIgnoreCase(df)) {
				resolver.dumpState(fh_dump, "Dumping cache on NAFMAN="+def.code);
				sbrsp.append("<br/><br/>Dumped cache to file "+fh_dump.getAbsolutePath());
			}
		} else if (def.code.equals(CMD_DNSLOADROOTS)) {
			try {
				if (isRecursive) {
					sbrsp.append("DNS roots are not applicable, as Resolver is in recursive mode");
				} else {
					cachemgr.loadRootServers();
				}
			} catch (Exception ex) {
				sbrsp.append("Failed to reload roots - "+ex);
			}
		} else if (def.code.equals(CMD_DNSQUERY)) {
			String pqt = cmd.getArg(MATTR_QTYP);
			String pqv = cmd.getArg(MATTR_QVAL);
			if (pqv == null) return sbrsp.append("INVALID: Missing query attribute=").append(MATTR_QVAL);
			ResolverAnswer ans = null;
			byte qt = 0;
			if ("A".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_A;
			} else if ("NS".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_NS;
			} else if ("MX".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_MX;
			} else if ("SOA".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_SOA;
			} else if ("SRV".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_SRV;
			} else if ("TXT".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_TXT;
			} else if ("AAAA".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_AAAA;
			} else if ("PTR".equalsIgnoreCase(pqt)) {
				qt = ResolverDNS.QTYPE_PTR;
			} else {
				return sbrsp.append("INVALID: Unsupported query type - ").append(MATTR_QTYP).append('=').append(pqt);
			}
			if (qt == ResolverDNS.QTYPE_PTR) {
				int ip = IP.convertDottedIP(pqv);
				if (!IP.validDottedIP(pqv, ip)) return sbrsp.append("INVALID: Not a valid dotted IP - ").append(pqv);
				ans = resolver.resolve(qt, ip, null, null, 0);
			} else {
				ans = resolver.resolve(qt, tmpbc_nafman.populate(pqv), null, null, 0);
			}
			if (ans == null) {
				sbrsp.append("Answer not in cache - query has been issued");
			} else {
				sbrsp.append("Cached Answer: ");
				ans.toString(sbrsp);
			}
		} else {
			// we've obviously registered for this command, so we must be missing a clause - clearly a bug
			logger.error("DNS-Resolver NAFMAN: Missing case for cmd="+def.code);
			return null;
		}
		return sbrsp;
	}
}
