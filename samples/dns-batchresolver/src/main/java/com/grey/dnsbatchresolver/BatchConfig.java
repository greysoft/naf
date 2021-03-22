/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.dnsbatchresolver;

import java.net.UnknownHostException;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.naf.NAFConfig;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.errors.NAFConfigException;

public class BatchConfig {
	public static final long DELAY_TERM = SysProps.getTime("greynaf.batchdns.delayterm", "2s");

	private final byte lookupType;
	private final String lookupTypeText;
	private final int batchSize;
	private final int maxPending;
	private final int maxPendingLoWater;
	private final int maxrequests;
	private final long delayBatch;
	private final long delayStart;
	private final boolean fullAnswer;
	private final String filenameIn;
	private final String filenameOut;
	private final ResolverConfig resolverConfig;

	public BatchConfig(XmlConfig taskcfg, NAFConfig nafcfg) throws UnknownHostException {
		lookupTypeText = taskcfg.getValue("dnstype", false, "A").toUpperCase();
		batchSize = taskcfg.getInt("batchsize", false, 10);
		maxrequests = taskcfg.getInt("maxrequests", false, 0);
		maxPending = taskcfg.getInt("maxpending", false, 0);
		maxPendingLoWater = taskcfg.getInt("maxpending_lowater", false, Math.max(maxPending/2, maxPending-20));
		delayBatch = taskcfg.getTime("delay_batch", 0);
		delayStart = taskcfg.getTime("delay_start", 0);
		fullAnswer = taskcfg.getBool("fullanswer", true);
		filenameIn = nullifyHyphen(taskcfg.getValue("infile", false, null));
		filenameOut = nullifyHyphen(taskcfg.getValue("outfile", false, null));

		if (lookupTypeText.equals("A")) {
			lookupType = ResolverDNS.QTYPE_A;
		} else if (lookupTypeText.equals("AAAA")) {
			lookupType = ResolverDNS.QTYPE_AAAA;
		} else if (lookupTypeText.equals("PTR")) {
			lookupType = ResolverDNS.QTYPE_PTR;
		} else if (lookupTypeText.equals("NS")) {
			lookupType = ResolverDNS.QTYPE_NS;
		} else if (lookupTypeText.equals("SOA")) {
			lookupType = ResolverDNS.QTYPE_SOA;
		} else if (lookupTypeText.equals("MX")) {
			lookupType = ResolverDNS.QTYPE_MX;
		} else if (lookupTypeText.equals("SRV")) {
			lookupType = ResolverDNS.QTYPE_SRV;
		} else if (lookupTypeText.equals("TXT")) {
			lookupType = ResolverDNS.QTYPE_TXT;
		} else {
			throw new NAFConfigException("Invalid lookup-type="+lookupTypeText);
		}
		if (maxPending != 0 && maxPendingLoWater >= maxPending)  throw new NAFConfigException("maxpending_lowater cannot exceed max - "+maxPendingLoWater+" vs "+maxPending);

		resolverConfig = new ResolverConfig.Builder()
				.withXmlConfig(nafcfg.getNode("dnsresolver"))
				.build();
	}

	public byte getLookupType() {
		return lookupType;
	}

	public String getLookupTypeText() {
		return lookupTypeText;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public int getMaxPending() {
		return maxPending;
	}

	public int getMaxPendingLoWater() {
		return maxPendingLoWater;
	}

	public int getMaxrequests() {
		return maxrequests;
	}

	public long getDelayBatch() {
		return delayBatch;
	}

	public long getDelayStart() {
		return delayStart;
	}

	public boolean isFullAnswer() {
		return fullAnswer;
	}

	public String getFilenameIn() {
		return filenameIn;
	}

	public String getFilenameOut() {
		return filenameOut;
	}

	public ResolverConfig getResolverConfig() {
		return resolverConfig;
	}

	private static String nullifyHyphen(String s) {
		if (s != null && s.equals("-")) s = null;
		return s;
	}
}