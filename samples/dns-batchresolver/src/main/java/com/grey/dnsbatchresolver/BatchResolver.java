/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.dnsbatchresolver;

import java.io.IOException;

import com.grey.base.utils.ByteChars;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.engine.ResolverAnswer;
import com.grey.naf.reactor.Dispatcher;
import com.grey.logging.Logger.LEVEL;

/*
 * Setup:
 * If in recursive mode, this application places a very heavy load on the local DNS servers, possibly breaching
 * various configured limits in addition to the general increase in workload.
 * If any of the servers are BIND, then these 'options' settings will increase various limits to half a million:
 * 		recursive-clients	500000
 * 		tcp-clients			500000
 * Also run named with the -S option, to increase max sockets.
 * These settings would leave a public BIND server vulnerable to Denial-of-Service tests, but then again, this test harness
 * virtually is a DoS!
 * In tandem with the -S option, you need to also increase file-descriptors limit on the Unix process as high as possible.
 * 		ulimit -n MAX sets hard and soft limits
 * In the Windows Registry, beware of TcpNumConnections, but it's default value is set to max.
 */
public class BatchResolver
	implements ResolverDNS.Client
{
	public interface Harness {
		void resolverCompleted();
		void resolverReady();
		String getNextResolverQuery() throws IOException;
	}

	private static final String LOGLBL = "DNS-BatchResolver: ";

	private final BatchConfig config;
	private final ResolverDNS resolver;
	private final Harness harness;
	private final java.io.PrintStream ostrm;
	private final com.grey.logging.Logger logger;
	private final int[] stats = new int[ResolverAnswer.STATUS.values().length];

	private boolean inputEOF;
	private int reqcnt;
	private int cache_hits;
	private int pendingcnt;
	private boolean paused;

	// temp work areas, preallocated for efficiency
	private final ByteChars domnam = new ByteChars(ResolverDNS.MAXDOMAIN);
	private final ByteChars tmplightbc = new ByteChars(-1);  //lightweight object without own storage
	private final StringBuilder tmpsb = new StringBuilder();

	public BatchResolver(Dispatcher dsptch,
			Harness harness,
			BatchConfig config,
			java.io.PrintStream ostrm,
			com.grey.logging.Logger logger) {
		this.config = config;
		this.logger = logger;
		this.harness = harness;
		this.ostrm = ostrm;

		// Create the DNS resolver, retrieving the existing one if any have already been defined on this Dispatcher
		resolver = ResolverDNS.create(dsptch, config.getResolverConfig());
	}

	public void shutdown() {
		logger.log(LEVEL.INFO, LOGLBL+"Shutdown with reqcnt="+reqcnt+", pending="+pendingcnt+", EOF="+inputEOF);
		if (pendingcnt != 0) {
			try {
				resolver.cancel(this);
			} catch (Exception ex) {
				logger.log(LEVEL.ERR, ex, true, LOGLBL+"Failed to cancel DNS ops");
			}
		}
	}

	@Override
	public void dnsResolved(Dispatcher d, ResolverAnswer answer, Object cbdata) {
		if (logger.isActive(LEVEL.TRC2)) {
			logger.log(LEVEL.TRC2, LOGLBL+"Name="+cbdata+" has Answer="+answer
					+" - reqcnt="+reqcnt+", pending="+pendingcnt+", EOF="+inputEOF);
		}
		printAnswer(answer, (String)cbdata);
		pendingcnt--;

		if (paused) {
			if (pendingcnt <= config.getMaxPendingLoWater()) {
				paused = false;
				harness.resolverReady();
			}
		} else {
			if (pendingcnt == 0 && inputEOF) harness.resolverCompleted();
		}
	}

	// processes a batch of requests, and returns True to indicate next batch should be scheduled
	public boolean issueRequests() throws java.io.IOException {
		int maxpending = config.getMaxPending();
		byte lookup_type = config.getLookupType();

		if (maxpending != 0 && pendingcnt >= maxpending) {
			logger.log(LEVEL.TRC, LOGLBL+"Suspending due to pending="+pendingcnt+" - reqcnt="+reqcnt);
			paused = true;
			return false;
		}
		int batchlimit = config.getBatchSize();
		if (maxpending != 0 && pendingcnt + batchlimit > maxpending) batchlimit = maxpending - pendingcnt;

		if (logger.isActive(LEVEL.TRC)) {
			logger.log(LEVEL.TRC, LOGLBL+"Starting batchsize="+batchlimit+" with reqcnt="+reqcnt+", pending="+pendingcnt);
		}
		int linecnt = 0;
		ResolverAnswer answer;
		String query;

		while ((query = harness.getNextResolverQuery()) != null) {
			linecnt++;
			reqcnt++;
			query = query.trim();
			if (logger.isActive(LEVEL.TRC2)) logger.log(LEVEL.TRC2, LOGLBL+"Reading name="+reqcnt+"/"+linecnt+" ["+query+"]");
			domnam.populate(query.trim().toLowerCase());

			if (lookup_type == ResolverDNS.QTYPE_A) {
				answer = resolver.resolveHostname(domnam, this, query, 0);
			} else if (lookup_type == ResolverDNS.QTYPE_AAAA) {
				answer = resolver.resolveAAAA(domnam, this, query, 0);
			} else if (lookup_type == ResolverDNS.QTYPE_PTR) {
				int ip = com.grey.base.utils.IP.convertDottedIP(query);
				answer = resolver.resolveIP(ip, this, query, 0);
			} else if (lookup_type == ResolverDNS.QTYPE_NS) {
				answer = resolver.resolveNameServer(domnam, this, query, 0);
			} else if (lookup_type == ResolverDNS.QTYPE_SOA) {
				answer = resolver.resolveSOA(domnam, this, query, 0);
			} else if (lookup_type == ResolverDNS.QTYPE_MX) {
				int pos = domnam.lastIndexOf((byte)com.grey.base.utils.EmailAddress.DLM_DOM);
				pos = (pos == -1 ? 0 : pos+1);
				tmplightbc.set(domnam, pos);
				answer = resolver.resolveMailDomain(tmplightbc, this, query, 0);
			} else if (lookup_type == ResolverDNS.QTYPE_SRV) {
				answer = resolver.resolveSRV(domnam, this, query, 0);
			} else if (lookup_type == ResolverDNS.QTYPE_TXT) {
				answer = resolver.resolveTXT(domnam, this, query, 0);
			} else {
				throw new Error(LOGLBL+"Missing case for lookup-type="+lookup_type);
			}

			if (answer == null) {
				pendingcnt++;
			} else {
				cache_hits++;
				printAnswer(answer, query);
			}
			if (reqcnt == config.getMaxrequests() || linecnt == batchlimit) break;
		}

		if (logger.isActive(LEVEL.TRC)) {
			logger.log(LEVEL.TRC, LOGLBL+"Completed batchsize="+linecnt+" with reqcnt="+reqcnt+", pending="+pendingcnt);
		}

		if (query == null || reqcnt == config.getMaxrequests()) {
			logger.log(LEVEL.TRC, LOGLBL+"Completed input - EOF="+(query==null));
			inputEOF = true;
			if (pendingcnt == 0) harness.resolverCompleted();
			return false;
		}
		return true;
	}

	private void printAnswer(ResolverAnswer answer, String origname) {
		tmpsb.setLength(0);
		tmpsb.append("Resolved ").append(origname).append(" to ");
		if (config.isFullAnswer()) {
			answer.toString(tmpsb);
		} else {
			tmpsb.append(answer.result);
		}
		stats[answer.result.ordinal()]++;
		ostrm.println(tmpsb);
	}

	public void printFinalSummary() {
		if (pendingcnt != 0 || !inputEOF) {
			ostrm.println("Queries not completed - EOF="+inputEOF+", Pending="+pendingcnt);
		}
		ostrm.println("\nTotal Requests = "+reqcnt+":");
		for (int idx = 0; idx < stats.length; idx++) {
			ostrm.println("- "+ResolverAnswer.STATUS.values()[idx]+"="+stats[idx]);
		}
		ostrm.println("Cache hits = "+(cache_hits-stats[ResolverAnswer.STATUS.BADNAME.ordinal()]));
	}
}