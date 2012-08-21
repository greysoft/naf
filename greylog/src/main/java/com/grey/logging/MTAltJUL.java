/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import java.util.logging.Level;

public class MTAltJUL
	extends AltJUL
{
	private volatile int minlvl;

	public MTAltJUL(String pthnam) throws java.io.FileNotFoundException {super(pthnam);}
	public MTAltJUL(java.io.PrintStream strm) {super(strm);}

	@Override
    public boolean isLoggable(Level lvl)
	{
		return lvl.intValue() >= minlvl;
	}

	@Override
	public void setLevel(Level newlvl)
	{
		minlvl = newlvl.intValue();
		super.setLevel(newlvl);
	}

	@Override
	public void log(java.util.logging.LogRecord rec)
	{
		if (rec.getLevel().intValue() < minlvl) return;
		synchronized (this) {super.log(rec);}
	}

	@Override
	public void log(Level lvl, CharSequence msg)
	{
		if (lvl.intValue() < minlvl) return;
		synchronized (this) {super.log(lvl, msg);}
	}

	@Override
	public void log(Level lvl, CharSequence msg, Throwable ex, boolean withstack)
	{
		if (lvl.intValue() < minlvl) return;
		synchronized (this) {super.log(lvl, msg, ex, withstack);}
	}
}
