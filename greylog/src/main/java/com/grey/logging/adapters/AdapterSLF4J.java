/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging.adapters;

import com.grey.base.utils.ScheduledTime;

public class AdapterSLF4J
	extends com.grey.logging.Logger
{
	private final org.slf4j.Logger extlog;  //the external logger we're bridging to

	protected AdapterSLF4J(com.grey.logging.Parameters params, String logname)
	{
		super(adjust(params), logname, false);
		extlog = org.slf4j.LoggerFactory.getLogger(logname);
	}

	@Override
	public void log(com.grey.logging.Logger.LEVEL lvl, CharSequence logmsg)
	{
		if (!isActive(lvl)) return;
		String msg = logmsg.toString();

		switch (lvl)
		{
			case ERR:
				extlog.error(msg);
				break;
			case WARN:
				extlog.warn(msg);
				break;
			case INFO:
				extlog.info(msg);
				break;
			case TRC:
				extlog.debug(msg);
				break;
			default:
				extlog.trace(msg);
				break;
		}
	}

	// remove settings that make no sense for this logger
	private static com.grey.logging.Parameters adjust(com.grey.logging.Parameters params)
	{
		params.mode = com.grey.logging.Parameters.MODE_AUDIT;
		params.pthnam = null;
		params.strm = null;
		params.rotfreq = ScheduledTime.FREQ.NEVER;
		params.maxsize = 0;
		params.bufsiz = 0;
		params.flush_interval = 0;
		return params;
	}
}