/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.dnsbatchresolver;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.utils.TimeOps;

import java.io.IOException;

import com.grey.base.config.XmlConfig;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.TimerNAF;

public class BatchTask
	extends com.grey.naf.Naflet
	implements TimerNAF.Handler, BatchResolver.Harness
{
	private static final String LOGLBL = "DNS-BatchTask: ";
	private static final int TMRTYPE_BATCH = 1;
	private static final int TMRTYPE_TERM = 2;

	private final BatchConfig config;
	private final BatchResolver resolver;
	private final java.io.BufferedReader istrm;
	private final java.io.PrintStream ostrm;
	private final com.grey.logging.Logger logger;

	private TimerNAF tmr_batch;
	private long systime_init;
	private long systime_term;
	private long systime_delays; //cumulative sleeps

	public BatchTask(String name, Dispatcher dsptch, XmlConfig cfg)throws java.io.IOException {
		super(name, dsptch, cfg);
		config = new BatchConfig(taskConfig(), dsptch.getApplicationContext().getNafConfig());
		logger = dsptch.getLogger();

		java.io.InputStream fin = (config.getFilenameIn() == null ? System.in : new java.io.FileInputStream(config.getFilenameIn()));
		java.io.OutputStream fout = (config.getFilenameOut() == null ? System.out : new java.io.FileOutputStream(config.getFilenameOut()));

		java.io.BufferedOutputStream bstrm = new java.io.BufferedOutputStream(fout, 64 * 1024);
		istrm = new java.io.BufferedReader(new java.io.InputStreamReader(fin), 8 * 1024);
		ostrm = new java.io.PrintStream(bstrm, false);

		// Create the DNS resolver, retrieving the existing one if any have already been defined on this Dispatcher
		resolver = new BatchResolver(dsptch, this, config, ostrm, logger);

		String msg = LOGLBL+"Mode="+config.getLookupTypeText()+", batchsize="+config.getBatchSize()+", batchdelay="+config.getDelayBatch()
			+", maxpending="+config.getMaxPending()+"/"+config.getMaxPendingLoWater()
			+"\n\tinfile="+config.getFilenameIn()+", outfile="+config.getFilenameOut()
			+"\n\tStart time: "+new java.util.Date(dsptch.getSystemTime())+" - "+dsptch.getSystemTime();
		ostrm.println(msg);
		logger.info(msg);
	}

	@Override
	protected void startNaflet() {
		logger.log(LEVEL.INFO, LOGLBL+"Starting");
		systime_init = getDispatcher().getSystemTime();
		scheduleBatch(config.getDelayStart());
	}

	@Override
	protected boolean stopNaflet() {
		logger.log(LEVEL.INFO, LOGLBL+"Received Stop-Naflet");
		ostrm.println("\nResolver terminated after elapsed time="+TimeOps.expandMilliTime(systime_term - systime_init - systime_delays));
		resolver.printFinalSummary();

		resolver.shutdown();
		if (tmr_batch != null) tmr_batch.cancel();
		tmr_batch = null;

		try {
			istrm.close();
		} catch (Exception ex) {
			logger.info(LOGLBL+"Failed to close input stream - "+com.grey.base.ExceptionUtils.summary(ex));
		}
		ostrm.close();
		logger.log(LEVEL.INFO, LOGLBL+"Terminated");
		return true;
	}

	@Override
	public void resolverCompleted() {
		logger.log(LEVEL.INFO, LOGLBL+"Resolver completed");
		// stop the Dispatcher, but not in this call chain to avoid any reentrancy issues
		systime_term = getDispatcher().getSystemTime();
		getDispatcher().setTimer(BatchConfig.DELAY_TERM, TMRTYPE_TERM, this);
	}

	@Override
	public void resolverReady() {
		scheduleBatch(0);
	}

	@Override
	public String getNextResolverQuery() throws IOException {
		return istrm.readLine();
	}

	@Override
	public void timerIndication(TimerNAF tmr, Dispatcher d) throws java.io.IOException {
		if (tmr.getType() == TMRTYPE_TERM) {
			stopDispatcher();
		} else {
			tmr_batch = null;
			if (resolver.issueRequests()) {
				scheduleBatch(config.getDelayBatch());
			}
		}
	}

	@Override
	public void eventError(com.grey.naf.reactor.TimerNAF tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex) {
		logger.log(LEVEL.ERR, ex, true, "Fatal error on timer callback");
		stopDispatcher();
	}

	private void scheduleBatch(long delay) {
		systime_delays += delay;
		tmr_batch = getDispatcher().setTimer(delay, TMRTYPE_BATCH, this);
	}

	private void stopDispatcher() {
		logger.log(LEVEL.INFO, LOGLBL+"Signalling Dispatcher to stop");
		boolean done = getDispatcher().stop();
		logger.log(LEVEL.INFO, LOGLBL+"Issued Dispatcher stop with done="+done);
	}
}