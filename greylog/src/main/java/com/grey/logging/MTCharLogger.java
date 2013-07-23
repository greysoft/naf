/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

/** MT-safe wrapper around its non-MT parent.
 */
public class MTCharLogger
	extends CharLogger
{
	protected MTCharLogger(Parameters params, String logname) throws java.io.IOException
	{
		super(params, logname, true);
	}

	@Override
	synchronized public void flush() throws java.io.IOException
	{
		super.flush();
	}

	@Override
	public void log(LEVEL lvl, CharSequence msg)
	{
		if (!isActive(lvl)) return;
		synchronized (this) {super.log(lvl, msg);}
	}
}
