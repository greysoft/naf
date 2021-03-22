/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.base.utils.ScheduledTime;

/**
 * Accumulates log messages as an in-memory string.
 */
public class MemLogger
	extends Logger
{
	private static final String eolstr = com.grey.base.config.SysProps.EOL;
	private final StringBuilder logbuf = new StringBuilder();
	private final StringBuilder msgbuf = new StringBuilder();  //preallocated for efficiency

	public final CharSequence get() {return logbuf;}
	public final int length() {return logbuf.length();}
	public void reset() {logbuf.setLength(0);}

	protected MemLogger(Parameters params, String logname)
	{
		super(adjust(params), logname, false);
	}

	// Doesn't actually close, just discards contents and capacity.
	// Users can continue to call log()
	@Override
	protected void closeStream(boolean is_owner)
	{
		reset();
		logbuf.trimToSize();
	}

	@Override
	public void log(LEVEL lvl, CharSequence msg)
	{
		if (!isActive(lvl)) return;
		try {
			setLogEntry(lvl, msgbuf);
		} catch (Throwable ex) {
			//This can't actually happen in our case, setLogEntry() won't do any I/O
	        System.out.println(new java.util.Date(getClock().millis())+" FATAL ERROR: Failed to write MemLogger - "
	        		+com.grey.base.ExceptionUtils.summary(ex, true));
			System.exit(1);
		}
		logbuf.append(msgbuf).append(msg).append(eolstr);
	}

	// remove settings that make no sense for this logger
	private static Parameters adjust(Parameters params)
	{
		Parameters.Builder bldr = new Parameters.Builder(params);
		return bldr
				.withPathname(null)
				.withStream(null)
				.withRotFreq(ScheduledTime.FREQ.NEVER)
				.withMaxSize(0)
				.withBufferSize(0)
				.withFlushInterval(0)
				.build();
	}
}
