/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging.adapters;

import com.grey.base.utils.ScheduledTime;
import com.grey.logging.Parameters;

public class AdapterJCL
	extends com.grey.logging.Logger
{
	private final org.apache.commons.logging.Log extlog;  //the external logger we're bridging to

	public AdapterJCL(com.grey.logging.Parameters params, String logname)
	{
		super(adjust(params), logname, false);
		extlog = org.apache.commons.logging.LogFactory.getLog("NAF-log="+logname);
		System.out.println("NAF-log="+logname+" created JCL="+extlog+" in "+new java.io.File(".").getAbsolutePath());
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