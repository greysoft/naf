/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

/** Null logger which discards all the messages passed to it.
 */
public class SinkLogger
	extends Logger
{
	public SinkLogger(String logname)
	{
		this(new Parameters(), logname);
	}

	public SinkLogger(Parameters params, String logname)
	{
		super(adjust(params), logname, false);
	}


	@Override
	public void log(LEVEL lvl, CharSequence msg)
	{
		return;
	}

	// remove settings that make no sense for this logger
	private static Parameters adjust(Parameters params)
	{
		params.pthnam = null;
		params.strm = null;
		params.bufsiz = 0;
		params.flush_interval = 0;
		return params;
	}
}
