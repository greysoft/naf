/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver;

import java.time.Duration;
import java.util.Arrays;
import java.net.UnknownHostException;

import com.grey.base.config.SysProps;
import com.grey.base.utils.TSAP;
import com.grey.base.utils.TimeOps;
import com.grey.base.config.XmlConfig;
import com.grey.naf.dns.resolver.distributed.DistributedResolver;
import com.grey.naf.dns.resolver.embedded.EmbeddedResolver;
import com.grey.naf.dns.resolver.engine.PacketDNS;
import com.grey.naf.errors.NAFConfigException;

public class ResolverConfig
{
	// UDP max: add a small bit extra to allow for sloppy encoding by remote host (NB: no reason to suspect that)
	public static final int PKTSIZ_UDP = SysProps.get("greynaf.dns.maxudp", PacketDNS.UDPMAXMSG + 64);
	// TCP max: allow for larger TCP messages (but we only really expect a fraction larger, not 4-fold)
	public static final int PKTSIZ_TCP = SysProps.get("greynaf.dns.maxtcp", PacketDNS.UDPMAXMSG * 4);
	// Linux limit is 128K, while Windows seems to accept just about anything
	public static final int UDPSOCKBUFSIZ = SysProps.get("greynaf.dns.sockbuf", PacketDNS.UDPMAXMSG * 128);
	public static final boolean DIRECTNIOBUFS = com.grey.naf.BufferGenerator.directniobufs;
	private static final int SVCPORT_DNS = SysProps.get("greynaf.dns.altport", PacketDNS.INETPORT);

	public static final com.grey.logging.Logger.LEVEL DEBUGLVL = com.grey.logging.Logger.LEVEL.TRC2;

	private final boolean recursive; //true means we issue recursive queries to localNameServers
	private final String[] localNameServers; //local name servers to which we can issue queries - pipe-separated, each part is host[:port]
	private final boolean autoRoots; //automatically discover the root servers (via localNameServers) - not relevant if in non-recursive mode
	private final String pathnameRootServers; //file containing the root servers - contents are appended even if autoRoots true - not relevant if in non-recursive mode

	private final boolean alwaysTCP; //start off in TCP mode to begin with, rather than falling back from UDP for large responses as usual
	private final int udpSenderSockets; //no. UDP sockets on which to distribute our outgoing DNS queries
	private final int dnsPort; //an alternative DNS port to send queries to - unlikely to ever make sense when talking to public nameservers

	//overrides any nameserver we would otherwise send a request to - mainly useful for troubleshooting
	private final java.net.InetSocketAddress dnsInterceptor;

	private final int retryMax; //max UDP retries - 0 means try once, with no retries
	private final long retryTimeout; //timeout on DNS/UDP requests
	private final long retryTimeoutTCP; //UDP/TCP - make it long enough that we don't preempt server's idle-connection close
	private final long retryStep; //number of milliseconds to increment DNS timeout by on each retry
	private final long wrapRetryFreq; //interval for retrying queries blocked by QID wraparound

	private final boolean cacheAllGlue;
	private final long negativeTTL; //how long to cache DNS no-domain answers (negative TTL)
	private final long initialMinTTL;
	private final long lookupMinTTL;
	private final boolean setMinTTL;

	private final int cacheLoWaterA;
	private final int cacheHiWaterA;
	private final int cacheLoWaterPTR;
	private final int cacheHiWaterPTR;
	private final int cacheLoWaterSOA;
	private final int cacheHiWaterSOA;
	private final int cacheLoWaterNS;
	private final int cacheHiWaterNS;
	private final int cacheLoWaterMX;
	private final int cacheHiWaterMX;
	private final boolean partialPrune;
	private final boolean dumpOnExit;

	private final int nsMaxRR; //the max no. of answer RRs to return for an MX query - zero means return all
	private final int mxMaxRR; //the max no. of answer RRs to return for an NS query - zero means return all

	// The above fields are all for the resolver engine config, but these 2 are for its ResolverDNS API
	private final boolean distributed;
	private final String distributedMaster;

	public ResolverConfig(Builder bldr) throws UnknownHostException {
		recursive = bldr.recursive;
		localNameServers = bldr.localNameServers;
		alwaysTCP = bldr.alwaysTCP;
		udpSenderSockets = (alwaysTCP ? 0 : bldr.udpSenderSockets);
		dnsPort = bldr.dnsPort;
		retryMax = bldr.retryMax;
		retryTimeout = bldr.retryTimeout;
		retryTimeoutTCP = bldr.retryTimeoutTCP;
		retryStep = bldr.retryStep;
		wrapRetryFreq = bldr.wrapRetryFreq;
		cacheAllGlue = bldr.cacheAllGlue;
		setMinTTL = bldr.setMinTTL;
		cacheLoWaterA = bldr.cacheLoWaterA;
		cacheHiWaterA = bldr.cacheHiWaterA;
		cacheLoWaterPTR = bldr.cacheLoWaterPTR;
		cacheHiWaterPTR = bldr.cacheHiWaterPTR;
		cacheLoWaterSOA = bldr.cacheLoWaterSOA;
		cacheHiWaterSOA = bldr.cacheHiWaterSOA;
		cacheLoWaterNS = bldr.cacheLoWaterNS;
		cacheHiWaterNS = bldr.cacheHiWaterNS;
		cacheLoWaterMX = bldr.cacheLoWaterMX;
		cacheHiWaterMX = bldr.cacheHiWaterMX;
		partialPrune = bldr.partialPrune;
		dumpOnExit = bldr.dumpOnExit;
		nsMaxRR = bldr.nsMaxRR;
		mxMaxRR = bldr.mxMaxRR;
		distributed = bldr.distributed;
		distributedMaster = bldr.distributedMaster;

		if (recursive) {
			pathnameRootServers = null;
			autoRoots = false;
		} else {
			pathnameRootServers = bldr.pathnameRootServers;
			autoRoots = bldr.autoRoots;
		}

		if (bldr.dnsInterceptor == null) {
			dnsInterceptor = null;
		} else {
			TSAP tsap = TSAP.build(bldr.dnsInterceptor, dnsPort, false);
			dnsInterceptor = tsap.sockaddr;
		}

		// RRs that are so short-lived as to disappear even as we construct an Answer would cause problems
		if (bldr.initialMinTTL < TimeOps.MSECS_PER_MINUTE) bldr.initialMinTTL = TimeOps.MSECS_PER_MINUTE;
		if (bldr.negativeTTL < bldr.initialMinTTL) bldr.negativeTTL = bldr.initialMinTTL;
		initialMinTTL = bldr.initialMinTTL;
		lookupMinTTL = bldr.lookupMinTTL;
		negativeTTL = bldr.negativeTTL;
	}

	public boolean isRecursive() {
		return recursive;
	}

	public String[] getLocalNameServers() {
		return localNameServers;
	}

	public boolean isAutoRoots() {
		return autoRoots;
	}

	public String getPathnameRootServers() {
		return pathnameRootServers;
	}

	public boolean isAlwaysTCP() {
		return alwaysTCP;
	}

	public int getSenderSocketsUDP() {
		return udpSenderSockets;
	}

	public java.net.InetSocketAddress getDnsInterceptor() {
		return dnsInterceptor;
	}

	public int getDnsPort() {
		return dnsPort;
	}

	public boolean isCacheAllGlue() {
		return cacheAllGlue;
	}

	public long getNegativeTTL() {
		return negativeTTL;
	}

	public long getInitialMinTTL() {
		return initialMinTTL;
	}

	public long getLookupMinTTL() {
		return lookupMinTTL;
	}

	public boolean isSetMinTTL() {
		return setMinTTL;
	}

	public int getRetryMax() {
		return retryMax;
	}

	public long getRetryTimeout() {
		return retryTimeout;
	}

	public long getRetryTimeoutTCP() {
		return retryTimeoutTCP;
	}

	public long getRetryStep() {
		return retryStep;
	}

	public long getWrapRetryFreq() {
		return wrapRetryFreq;
	}

	public int getCacheLoWaterA() {
		return cacheLoWaterA;
	}

	public int getCacheHiWaterA() {
		return cacheHiWaterA;
	}

	public int getCacheLoWaterPTR() {
		return cacheLoWaterPTR;
	}

	public int getCacheHiWaterPTR() {
		return cacheHiWaterPTR;
	}

	public int getCacheLoWaterSOA() {
		return cacheLoWaterSOA;
	}

	public int getCacheHiWaterSOA() {
		return cacheHiWaterSOA;
	}

	public int getCacheLoWaterNS() {
		return cacheLoWaterNS;
	}

	public int getCacheHiWaterNS() {
		return cacheHiWaterNS;
	}

	public int getCacheLoWaterMX() {
		return cacheLoWaterMX;
	}

	public int getCacheHiWaterMX() {
		return cacheHiWaterMX;
	}

	public boolean isPartialPrune() {
		return partialPrune;
	}

	public boolean isDumpOnExit() {
		return dumpOnExit;
	}

	public int getNsMaxRR() {
		return nsMaxRR;
	}

	public int getMxMaxRR() {
		return mxMaxRR;
	}

	public boolean isDistributed() {
		return distributed;
	}

	public String getDistributedMaster() {
		return distributedMaster;
	}


	public static class Builder {
		private boolean recursive = true;
		private String[] localNameServers;
		private boolean autoRoots = true;
		private String pathnameRootServers;
		private boolean alwaysTCP;
		private int udpSenderSockets = 2;
		private String dnsInterceptor;
		private int dnsPort = SVCPORT_DNS;
		private int retryMax = 3;
		private long retryTimeout = Duration.ofSeconds(10).toMillis();
		private long retryTimeoutTCP = Duration.ofSeconds(60).toMillis();
		private long retryStep = Duration.ofSeconds(3).toMillis();
		private long wrapRetryFreq = Duration.ofSeconds(1).toMillis();
		private boolean cacheAllGlue = true;
		private long negativeTTL = Duration.ofHours(1).toMillis();
		private long initialMinTTL = Duration.ofMinutes(5).toMillis();
		private long lookupMinTTL = Duration.ofMinutes(1).toMillis();
		private boolean setMinTTL;
		private int cacheLoWaterA;
		private int cacheHiWaterA;
		private int cacheLoWaterPTR;
		private int cacheHiWaterPTR;
		private int cacheLoWaterSOA;
		private int cacheHiWaterSOA;
		private int cacheLoWaterNS;
		private int cacheHiWaterNS;
		private int cacheLoWaterMX;
		private int cacheHiWaterMX;
		private boolean partialPrune;
		private boolean dumpOnExit;
		private int nsMaxRR;
		private int mxMaxRR;
		private boolean distributed = true;
		private String distributedMaster;

		public Builder withXmlConfig(XmlConfig cfg) {
			String srvlist = (localNameServers == null ? null : String.join("|", Arrays.asList(localNameServers)));
			recursive = cfg.getBool("@recursive", recursive);
			localNameServers = cfg.getTuple("localservers", "|", false, srvlist);
			autoRoots = cfg.getBool("rootservers/@auto", autoRoots);
			pathnameRootServers = cfg.getValue("rootservers", false, pathnameRootServers);
			alwaysTCP = cfg.getBool("@alwaystcp", alwaysTCP);
			udpSenderSockets = cfg.getInt("udpsockets", true, udpSenderSockets);
			dnsInterceptor = cfg.getValue("interceptor/@host", false, dnsInterceptor);
			dnsPort = cfg.getInt("interceptor/@port", true, dnsPort);
			cacheAllGlue = cfg.getBool("@nonbailiwick_glue", cacheAllGlue);
			negativeTTL = cfg.getTime("@negativeTTL", negativeTTL);
			initialMinTTL = cfg.getTime("@initialMinTTL", initialMinTTL);
			lookupMinTTL = cfg.getTime("@lookupMinTTL", lookupMinTTL);
			setMinTTL = cfg.getBool("@setMinTTL", setMinTTL);
			retryMax = cfg.getInt("retry/@max", false, retryMax);
			retryTimeout = cfg.getTime("retry/@timeout", retryTimeout);
			retryTimeoutTCP = cfg.getTime("retry/@timeout_tcp", retryTimeoutTCP);
			retryStep = cfg.getTime("retry/@backoff", retryStep);
			wrapRetryFreq = cfg.getTime("@wrapretry", wrapRetryFreq);
			cacheHiWaterA = cfg.getInt("cache_a/@hiwater", false, cacheHiWaterA);
			cacheLoWaterA = getLowater(cfg, "cache_a/@lowater", cacheHiWaterA);
			cacheHiWaterPTR = cfg.getInt("cache_ptr/@hiwater", false, cacheHiWaterPTR);
			cacheLoWaterPTR = getLowater(cfg, "cache_ptr/@lowater", cacheHiWaterPTR);
			cacheHiWaterSOA = cfg.getInt("cache_soa/@hiwater", false, cacheHiWaterSOA);
			cacheLoWaterSOA = getLowater(cfg, "cache_soa/@lowater", cacheHiWaterSOA);
			cacheHiWaterNS = cfg.getInt("cache_ns/@hiwater", false, cacheHiWaterNS);
			cacheLoWaterNS = getLowater(cfg, "cache_ns/@lowater", cacheHiWaterNS);
			cacheHiWaterMX = cfg.getInt("cache_mx/@hiwater", false, cacheHiWaterMX);
			cacheLoWaterMX = getLowater(cfg, "cache_mx/@lowater", cacheHiWaterMX);
			partialPrune = cfg.getBool("@partialprune", partialPrune);
			dumpOnExit = cfg.getBool("@exitdump", dumpOnExit);
			nsMaxRR = cfg.getInt("cache_ns/@maxrr", false, nsMaxRR);
			mxMaxRR = cfg.getInt("cache_mx/@maxrr", false, mxMaxRR);

			String resolverClass = cfg.getValue("@class", false, null);
			if (DistributedResolver.class.getName().equals(resolverClass)) {
				distributed = true;
			} else if (EmbeddedResolver.class.getName().equals(resolverClass)) {
				distributed = false;
			} else if (resolverClass != null) {
				throw new NAFConfigException("Unrecognised ResolverDNS class="+resolverClass);
			}
			distributedMaster = cfg.getValue("@master", false, distributedMaster);
			return this;
		}

		public Builder withRecursive(boolean v) {
			recursive = v;
			return this;
		}

		public Builder withLocalNameServers(String[] v) {
			localNameServers = v;
			return this;
		}

		public Builder withAutoRoots(boolean v) {
			autoRoots = v;
			return this;
		}

		public Builder withPathnameRootServers(String v) {
			pathnameRootServers = v;
			return this;
		}

		public Builder withAlwaysTCP(boolean v) {
			alwaysTCP = v;
			return this;
		}

		public Builder withUdpSenderSockets(int v) {
			udpSenderSockets = v;
			return this;
		}

		public Builder withDnsInterceptor(String v) {
			dnsInterceptor = v;
			return this;
		}

		public Builder withDnsPort(int v) {
			dnsPort = v;
			return this;
		}

		public Builder withRetryMax(int v) {
			retryMax = v;
			return this;
		}

		public Builder withRetryTimeout(long v) {
			retryTimeout = v;
			return this;
		}

		public Builder withRetryTimeoutTCP(long v) {
			retryTimeoutTCP = v;
			return this;
		}

		public Builder withRetryStep(long v) {
			retryStep = v;
			return this;
		}

		public Builder withWrapRetryFreq(long v) {
			wrapRetryFreq = v;
			return this;
		}

		public Builder withCacheAllGlue(boolean v) {
			cacheAllGlue = v;
			return this;
		}

		public Builder withNegativeTTL(long v) {
			negativeTTL = v;
			return this;
		}

		public Builder withInitialMinTTL(long v) {
			initialMinTTL = v;
			return this;
		}

		public Builder withLookupMinTTL(long v) {
			lookupMinTTL = v;
			return this;
		}

		public Builder withSetMinTTL(boolean v) {
			setMinTTL = v;
			return this;
		}

		public Builder withCacheLoWaterA(int v) {
			cacheLoWaterA = v;
			return this;
		}

		public Builder withCacheHiWaterA(int v) {
			cacheHiWaterA = v;
			return this;
		}

		public Builder withCacheLoWaterPTR(int v) {
			cacheLoWaterPTR = v;
			return this;
		}

		public Builder withCacheHiWaterPTR(int v) {
			cacheHiWaterPTR = v;
			return this;
		}

		public Builder withCacheLoWaterSOA(int v) {
			cacheLoWaterSOA = v;
			return this;
		}

		public Builder withCacheHiWaterSOA(int v) {
			cacheHiWaterSOA = v;
			return this;
		}

		public Builder withCacheLoWaterNS(int v) {
			cacheLoWaterNS = v;
			return this;
		}

		public Builder withCacheHiWaterNS(int v) {
			cacheHiWaterNS = v;
			return this;
		}

		public Builder withCacheLoWaterMX(int v) {
			cacheLoWaterMX = v;
			return this;
		}

		public Builder withCacheHiWaterMX(int v) {
			cacheHiWaterMX = v;
			return this;
		}

		public Builder withCacheHiWater(int v) {
			return withCacheHiWaterA(v)
					.withCacheHiWaterMX(v)
					.withCacheHiWaterNS(v)
					.withCacheHiWaterPTR(v)
					.withCacheHiWaterSOA(v);
		}

		public Builder withCacheLoWater(int v) {
			return withCacheLoWaterA(v)
					.withCacheLoWaterMX(v)
					.withCacheLoWaterNS(v)
					.withCacheLoWaterPTR(v)
					.withCacheLoWaterSOA(v);
		}

		public Builder withPartialPrune(boolean v) {
			partialPrune = v;
			return this;
		}

		public Builder withDumpOnExit(boolean v) {
			dumpOnExit = v;
			return this;
		}

		public Builder withNsMaxRR(int v) {
			nsMaxRR = v;
			return this;
		}

		public Builder withMxMaxRR(int v) {
			mxMaxRR = v;
			return this;
		}

		public Builder withDistributed(boolean v) {
			distributed = v;
			return this;
		}

		public Builder withDistributedMaster(String v) {
			distributedMaster = v;
			return this;
		}

		public ResolverConfig build() throws UnknownHostException {
			return new ResolverConfig(this);
		}

		private static int getLowater(XmlConfig cfg, String xpath, int hiwater)
		{
			int lowater = cfg.getInt(xpath, false, hiwater/2);
			if (lowater > hiwater) throw new NAFConfigException("lowater="+lowater+" exceeds hiwater="+hiwater+" - "+xpath);
			return lowater;
		}
	}
}
