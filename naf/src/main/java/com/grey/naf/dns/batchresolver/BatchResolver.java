/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.batchresolver;

import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger.LEVEL;
import com.grey.naf.dns.Answer;
import com.grey.naf.dns.Resolver;

/*
 * Setup:
 * This application places a very heavy load on the local DNS servers, possibly breaching various configured limits
 * in addition to the general increase in workload.
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
	private static final int TMRTYPE_BATCH = 1;
	private static final int TMRTYPE_TERM = 2;

	private final byte resolve_mode;
	private final int resolverflags;
	private final int batchsize;
	private final int maxrequests;
	private final long delay_batch;
	private final long delay_term;
	private final java.io.BufferedReader istrm;
	private final java.io.PrintStream ostrm;
	private final int[] stats = new int[Answer.STATUS.values().length];

	private com.grey.naf.reactor.Timer tmr_batch;
	private boolean inputEOF;
	private int waitcount;
	private int reqcnt;
	private long systime_init;
	private long systime_term;
	private long systime_eof;
	private long systime_paused;

	// temp work areas, preallocated for efficiency
	private final com.grey.base.utils.ByteChars domnam = new com.grey.base.utils.ByteChars(com.grey.naf.dns.Resolver.MAXDOMAIN);
	private final StringBuilder strbuf = new StringBuilder(120);

	public BatchResolver(String name, com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException, java.io.IOException
	{
		super(name, dsptch, cfg);
		java.io.InputStream fin = System.in;
		java.io.OutputStream fout = System.out;
		boolean mailboxes = false;

		String mode = appcfg.getValue("mode", false, "A").toUpperCase();
		batchsize = appcfg.getInt("batchsize", false, 10);
		maxrequests = appcfg.getInt("maxrequests", false, 0);
		delay_batch = appcfg.getTime("delay_batch", 0);
		delay_term = appcfg.getTime("delay_term", "20s");
		String filename_in = appcfg.getValue("infile", false, null);
		String filename_out = appcfg.getValue("outfile", false, null);

		if (filename_in != null && !filename_in.equals("-")) fin = new java.io.FileInputStream(filename_in);
		if (filename_out != null && !filename_out.equals("-")) fout = new java.io.FileOutputStream(filename_out);
		java.io.BufferedOutputStream bstrm = new java.io.BufferedOutputStream(fout, 64 * 1024);
		istrm = new java.io.BufferedReader(new java.io.InputStreamReader(fin), 8 * 1024);
		ostrm = new java.io.PrintStream(bstrm, false);

		if (mode.equals("A")) {
			resolve_mode = Resolver.QTYPE_A;
		} else if (mode.equals("MX")) {
			resolve_mode = Resolver.QTYPE_MX;
			mailboxes = appcfg.getBool("mailbox", mailboxes);
		} else if (mode.equals("PTR")) {
			resolve_mode = Resolver.QTYPE_PTR;
		} else {
			throw new com.grey.base.ConfigException("Invalid resolve-mode="+mode);
		}
		resolverflags = (mailboxes ? com.grey.naf.dns.Resolver.FLAG_ISMAILBOX : 0);

		String msg = "DNS-BatchResolver: Mode="+mode+", batchsize="+batchsize+", delay="+delay_batch;
        ostrm.println(msg);
		log.info(msg);
        msg = "DNS-BatchResolver: infile="+filename_in+", outfile="+filename_out;
        ostrm.println(msg);
		log.info(msg);
        msg = "Start time: "+new java.util.Date(dsptch.systime())+" - "+dsptch.systime();
        ostrm.println(msg);
        ostrm.println();
	}

	@Override
	protected void startNaflet()
	{
		tmr_batch = dsptch.setTimer(0, TMRTYPE_BATCH, this);
	}

	@Override
	public boolean stopNaflet()
	{
		try {
			istrm.close();
		} catch (Exception ex) {
			log.info("Failed to close input stream - "+com.grey.base.GreyException.summary(ex));
		}
		if (waitcount != 0) {
			try {
				dsptch.dnsresolv.cancel(this);
			} catch (Exception ex) {
				log.log(LEVEL.INFO, ex, true, "Failed to cancel DNS ops");
			}
		}
		return true;
	}

	@Override
	public void timerIndication(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d) throws java.io.IOException
	{
		if (tmr.type == TMRTYPE_TERM) {
			terminate();
		} else {
			tmr_batch = null;
			issueRequests();	
		}
	}

	@Override
	public void dnsResolved(com.grey.naf.reactor.Dispatcher d, Answer answer, Object cbdata) throws java.io.IOException
	{
		if (log.isActive(LEVEL.TRC)) {
			log.trace("DNS-BatchResolver: enter dnsResolved() with reqcnt="+reqcnt+", EOF="+inputEOF+", waitcount="+waitcount
					+" - Result="+answer.result+" for "+answer.qname+" - "+cbdata);
		}
		waitcount--;
		printAnswer(answer, false, String.class.cast(cbdata));
		suspend();
	}

	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex)
	{
		log.error("NAF Timer error");
		if (stopNaflet()) this.nafletStopped();
	}

	private void issueRequests() throws java.io.IOException
	{
		if (reqcnt == 0) {
			systime_init = dsptch.systime();
		}
		if (log.isActive(LEVEL.TRC)) {
			log.trace("DNS-BatchResolver: enter issueRequests() with reqcnt="+reqcnt+", EOF="+inputEOF+", waitcount="+waitcount);
		}
		Answer answer;
		int linecount = 0;
		String inline;

		while ((inline = istrm.readLine()) != null) {
			if (log.isActive(LEVEL.TRC2)) log.log(LEVEL.TRC2, "DNS-BatchResolver: read name="+reqcnt+" ["+inline+"]");
			domnam.set(inline.trim().toLowerCase());

			if (resolve_mode == Resolver.QTYPE_A) {
				answer = dsptch.dnsresolv.resolveHostname(domnam, this, inline, resolverflags);
			} else if (resolve_mode == Resolver.QTYPE_MX) {
				answer = dsptch.dnsresolv.resolveMailDomain(domnam, this, inline, resolverflags);
			} else if (resolve_mode == Resolver.QTYPE_PTR) {
				int ip = com.grey.base.utils.IP.convertDottedIP(inline);
				answer = dsptch.dnsresolv.resolveIP(ip, this, inline, resolverflags);
			} else {
				throw new RuntimeException("Missing case for resolve-mode="+resolve_mode);
			}

			if (answer == null) {
				waitcount++;
			} else {
				printAnswer(answer, true, inline);
			}
			if (++reqcnt == maxrequests) break;
			if (++linecount == batchsize) break;
		}

		if (inline == null || reqcnt == maxrequests) {
			systime_eof = dsptch.systime();
			inputEOF = true;
			istrm.close();
		}
		suspend();
	}

	private void suspend()
	{
		if (log.isActive(LEVEL.TRC)) {
			log.trace("DNS-BatchResolver: suspending with EOF="+inputEOF+", waitcount="+waitcount+" - tmr="+tmr_batch);
		}
		if (inputEOF) {
			if (waitcount == 0) {
				systime_term = dsptch.systime();
				if (tmr_batch != null) tmr_batch.cancel();
				tmr_batch = null;
				dsptch.setTimer(delay_term, TMRTYPE_TERM, this);
			}
		} else {
			if (tmr_batch == null) {
				systime_paused += delay_batch;
				tmr_batch = dsptch.setTimer(delay_batch, TMRTYPE_BATCH, this);
			}
		}
	}

	private void printAnswer(Answer answer, boolean cached, String origname) throws java.io.IOException
	{
		strbuf.setLength(0);
		strbuf.append(origname).append(" - ");
		answer.toString(strbuf);
		if (cached) strbuf.append(" (CACHED)");
		ostrm.println(strbuf);
		stats[answer.result.ordinal()]++;
	}
	
	private void terminate() throws java.io.IOException
	{
        ostrm.println();
        ostrm.println("Time to EOF: "+TimeOps.expandMilliTime(systime_eof - systime_init));
        ostrm.println("Time to completion: "+TimeOps.expandMilliTime(systime_term - systime_init)
        		+" - Elapsed="+TimeOps.expandMilliTime(systime_term - systime_init - systime_paused));
        ostrm.println("Total Requests = "+reqcnt+":");
        for (int idx = 0; idx < stats.length; idx++) {
            ostrm.println("- "+Answer.STATUS.values()[idx]+"="+stats[idx]);
        }
		ostrm.close();
		nafletStopped();
	}
}