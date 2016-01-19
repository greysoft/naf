/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.batchresolver;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.config.SysProps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.dns.Answer;
import com.grey.naf.dns.Resolver;

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
public final class BatchResolver
	extends com.grey.naf.Naflet
	implements com.grey.naf.dns.Resolver.Client, com.grey.naf.reactor.Timer.Handler
{
	private static final long DELAY_TERM = SysProps.getTime("greynaf.batchdns.delayterm", "5s");
	private static final String LOGLBL = "DNS-BatchResolver: ";
	private static final int TMRTYPE_BATCH = 1;
	private static final int TMRTYPE_TERM = 2;

	private final byte lookup_type;
	private final int batchsize;
	private final int maxpending;
	private final int maxpending_lowater;
	private final int maxrequests;
	private final long delay_batch;
	private final long delay_start;
	private final boolean full_answer;
	private final java.io.BufferedReader istrm;
	private final java.io.PrintStream ostrm;
	private final int[] stats = new int[Answer.STATUS.values().length];
	private final com.grey.logging.Logger logger;

	private com.grey.naf.reactor.Timer tmr_batch;
	private boolean inputEOF;
	private int reqcnt;
	private int cache_hits;
	private int pendingcnt;
	private boolean paused;
	private long systime_init;
	private long systime_term;
	private long systime_delays; //cumulative sleeps

	// temp work areas, preallocated for efficiency
	private final com.grey.base.utils.ByteChars domnam = new com.grey.base.utils.ByteChars(Resolver.MAXDOMAIN);
	private final com.grey.base.utils.ByteChars tmplightbc = new com.grey.base.utils.ByteChars(-1);  //lightweight object without own storage
	private final StringBuilder tmpsb = new StringBuilder();

	public BatchResolver(String name, com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException, java.io.IOException
	{
		super(name, dsptch, cfg);
		java.io.InputStream fin = System.in;
		java.io.OutputStream fout = System.out;
		logger = dsptch.logger;
		com.grey.base.config.XmlConfig taskcfg = taskConfig();

		String mode = taskcfg.getValue("dnstype", false, "A").toUpperCase();
		batchsize = taskcfg.getInt("batchsize", false, 10);
		maxrequests = taskcfg.getInt("maxrequests", false, 0);
		maxpending = taskcfg.getInt("maxpending", false, 0);
		maxpending_lowater = taskcfg.getInt("maxpending_lowater", false, Math.max(maxpending/2, maxpending-20));
		delay_batch = taskcfg.getTime("delay_batch", 0);
		delay_start = taskcfg.getTime("delay_start", 0);
		full_answer = taskcfg.getBool("fullanswer", true);
		String filename_in = taskcfg.getValue("infile", false, null);
		String filename_out = taskcfg.getValue("outfile", false, null);

		if (mode.equals("A")) {
			lookup_type = Resolver.QTYPE_A;
		} else if (mode.equals("AAAA")) {
			lookup_type = Resolver.QTYPE_AAAA;
		} else if (mode.equals("PTR")) {
			lookup_type = Resolver.QTYPE_PTR;
		} else if (mode.equals("NS")) {
			lookup_type = Resolver.QTYPE_NS;
		} else if (mode.equals("SOA")) {
			lookup_type = Resolver.QTYPE_SOA;
		} else if (mode.equals("MX")) {
			lookup_type = Resolver.QTYPE_MX;
		} else if (mode.equals("SRV")) {
			lookup_type = Resolver.QTYPE_SRV;
		} else if (mode.equals("TXT")) {
			lookup_type = Resolver.QTYPE_TXT;
		} else {
			throw new com.grey.base.ConfigException(LOGLBL+"Invalid lookup-type="+mode);
		}
		if (dsptch.dnsresolv == null) throw new com.grey.base.ConfigException(LOGLBL+"Dispatcher="+dsptch.name+" does not have DNS-Resolver enabled");
		if (maxpending != 0 && maxpending_lowater >= maxpending)  throw new com.grey.base.ConfigException(LOGLBL+"maxpending_lowater cannot exceed max - "+maxpending_lowater+" vs "+maxpending);
		
		if (filename_in != null && !filename_in.equals("-")) fin = new java.io.FileInputStream(filename_in);
		if (filename_out != null && !filename_out.equals("-")) fout = new java.io.FileOutputStream(filename_out);
		java.io.BufferedOutputStream bstrm = new java.io.BufferedOutputStream(fout, 64 * 1024);
		istrm = new java.io.BufferedReader(new java.io.InputStreamReader(fin), 8 * 1024);
		ostrm = new java.io.PrintStream(bstrm, false);

		String msg = LOGLBL+"Mode="+mode+", batchsize="+batchsize+", batchdelay="+delay_batch
				+", maxpending="+maxpending+"/"+maxpending_lowater
				+"\n\tinfile="+filename_in+", outfile="+filename_out
				+"\n\tStart time: "+new java.util.Date(dsptch.getSystemTime())+" - "+dsptch.getSystemTime();
        ostrm.println(msg);
		logger.info(msg);
	}

	@Override
	protected void startNaflet()
	{
		systime_init = dsptch.getSystemTime();
		systime_delays = delay_start;
		tmr_batch = dsptch.setTimer(delay_start, TMRTYPE_BATCH, this);
	}

	@Override
	protected boolean stopNaflet()
	{
		try {
			istrm.close();
		} catch (Exception ex) {
			logger.info(LOGLBL+"Failed to close input stream - "+com.grey.base.GreyException.summary(ex));
		}
		if (pendingcnt != 0) {
			try {
				dsptch.dnsresolv.cancel(this);
			} catch (Exception ex) {
				logger.log(LEVEL.ERR, ex, true, LOGLBL+"Failed to cancel DNS ops");
			}
		}
		if (tmr_batch != null) tmr_batch.cancel();
		tmr_batch = null;
		terminate(true);
		return true;
	}

	@Override
	public void timerIndication(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d) throws java.io.IOException
	{
		if (tmr.type == TMRTYPE_TERM) {
			terminated();
		} else {
			tmr_batch = null;
			issueRequests();	
		}
	}

	@Override
	public void dnsResolved(com.grey.naf.reactor.Dispatcher d, Answer answer, Object cbdata)
	{
		if (logger.isActive(LEVEL.TRC2)) {
			logger.log(LEVEL.TRC2, LOGLBL+"Name="+cbdata+" has Answer="+answer
					+" - reqcnt="+reqcnt+", pending="+pendingcnt+", EOF="+inputEOF);
		}
		printAnswer(answer, (String)cbdata);
		pendingcnt--;

		if (paused) {
			if (pendingcnt <= maxpending_lowater) {
				paused = false;
				tmr_batch = dsptch.setTimer(0, TMRTYPE_BATCH, this);
			}
		} else {
			if (pendingcnt == 0 && inputEOF) terminate(false);
		}
	}

	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex)
	{
		logger.error(LOGLBL+"NAF failure - "+ex);
		if (stopNaflet()) nafletStopped();
	}

	private void issueRequests() throws java.io.IOException
	{
		if (maxpending != 0 && pendingcnt >= maxpending) {
			logger.log(LEVEL.TRC, LOGLBL+"Suspending due to pending="+pendingcnt+" - reqcnt="+reqcnt);
			paused = true;
			return;
		}
		int batchlimit = batchsize;
		if (maxpending != 0 && pendingcnt + batchlimit > maxpending) batchlimit = maxpending - pendingcnt;

		if (logger.isActive(LEVEL.TRC)) {
			logger.log(LEVEL.TRC, LOGLBL+"Starting batchsize="+batchlimit+" with reqcnt="+reqcnt+", pending="+pendingcnt);
		}
		int linecnt = 0;
		Answer answer;
		String inline;

		while ((inline = istrm.readLine()) != null) {
			linecnt++;
			reqcnt++;
			if (logger.isActive(LEVEL.TRC2)) logger.log(LEVEL.TRC2, LOGLBL+"Reading name="+reqcnt+"/"+linecnt+" ["+inline+"]");
			domnam.set(inline.trim().toLowerCase());

			if (lookup_type == Resolver.QTYPE_A) {
				answer = dsptch.dnsresolv.resolveHostname(domnam, this, inline, 0);
			} else if (lookup_type == Resolver.QTYPE_AAAA) {
				answer = dsptch.dnsresolv.resolveAAAA(domnam, this, inline, 0);
			} else if (lookup_type == Resolver.QTYPE_PTR) {
				int ip = com.grey.base.utils.IP.convertDottedIP(inline);
				answer = dsptch.dnsresolv.resolveIP(ip, this, inline, 0);
			} else if (lookup_type == Resolver.QTYPE_NS) {
				answer = dsptch.dnsresolv.resolveNameServer(domnam, this, inline, 0);
			} else if (lookup_type == Resolver.QTYPE_SOA) {
				answer = dsptch.dnsresolv.resolveSOA(domnam, this, inline, 0);
			} else if (lookup_type == Resolver.QTYPE_MX) {
				int pos = domnam.lastIndexOf((byte)com.grey.base.utils.EmailAddress.DLM_DOM);
				pos = (pos == -1 ? 0 : pos+1);
				tmplightbc.pointAt(domnam, pos);
				answer = dsptch.dnsresolv.resolveMailDomain(tmplightbc, this, inline, 0);
			} else if (lookup_type == Resolver.QTYPE_SRV) {
				answer = dsptch.dnsresolv.resolveSRV(domnam, this, inline, 0);
			} else if (lookup_type == Resolver.QTYPE_TXT) {
				answer = dsptch.dnsresolv.resolveTXT(domnam, this, inline, 0);
			} else {
				throw new RuntimeException(LOGLBL+"Missing case for lookup-type="+lookup_type);
			}

			if (answer == null) {
				pendingcnt++;
			} else {
				cache_hits++;
				printAnswer(answer, inline);
			}
			if (reqcnt == maxrequests || linecnt == batchlimit) break;
		}

		if (logger.isActive(LEVEL.TRC)) {
			logger.log(LEVEL.TRC, LOGLBL+"Completed batchsize="+linecnt+" with reqcnt="+reqcnt+", pending="+pendingcnt);
		}

		if (inline == null || reqcnt == maxrequests) {
			logger.log(LEVEL.TRC, LOGLBL+"Completed input - EOF="+(inline==null));
			inputEOF = true;
			try {
				istrm.close();
			} catch (Exception ex) {
				logger.info(LOGLBL+"Failed to close input stream - "+com.grey.base.GreyException.summary(ex));
			}
			if (pendingcnt == 0) terminate(false);
		} else {
			systime_delays += delay_batch;
			tmr_batch = dsptch.setTimer(delay_batch, TMRTYPE_BATCH, this);
		}
	}

	private void printAnswer(Answer answer, String origname)
	{
		tmpsb.setLength(0);
		tmpsb.append("Resolved ").append(origname).append(" to ");
		if (full_answer) {
			answer.toString(tmpsb);
		} else {
			tmpsb.append(answer.result);
		}
		ostrm.println(tmpsb);
		stats[answer.result.ordinal()]++;
	}

	private void terminate(boolean immediate)
	{
		logger.log(LEVEL.INFO, LOGLBL+"Terminating - reqcnt="+reqcnt+", pending="+pendingcnt+", EOF="+inputEOF);
		systime_term = dsptch.getSystemTime();
		if (immediate) {
			terminated();
		} else {
			dsptch.setTimer(DELAY_TERM, TMRTYPE_TERM, this);
		}
	}

	private void terminated()
	{
		String txt_elapsed = " (elapsed="+TimeOps.expandMilliTime(systime_term - systime_init - systime_delays)+")";
        ostrm.println();
        if (pendingcnt == 0 && inputEOF) {
            ostrm.println("Running time: "+TimeOps.expandMilliTime(systime_term - systime_init)+txt_elapsed);
        } else {
        	ostrm.println("Not completed - EOF="+inputEOF+", pending="+pendingcnt+txt_elapsed);
        }
        ostrm.println("Total Requests = "+reqcnt+":");
        for (int idx = 0; idx < stats.length; idx++) {
            ostrm.println("- "+Answer.STATUS.values()[idx]+"="+stats[idx]);
        }
        ostrm.println("Cache hits = "+(cache_hits-stats[Answer.STATUS.BADNAME.ordinal()]));
		ostrm.close();
		logger.log(LEVEL.INFO, LOGLBL+"Terminated");
		nafletStopped();
	}
}