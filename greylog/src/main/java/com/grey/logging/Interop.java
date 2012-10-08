/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.logging.Logger.LEVEL;

public class Interop
{
	public static boolean isActive(LEVEL logger, LEVEL msg)
	{
		if (logger == LEVEL.OFF || msg == LEVEL.OFF) return false;
		if (logger == LEVEL.ALL || msg == LEVEL.ALL) return true;  //msg=ALL doesn't really make sense, but pass it
		return (msg.ordinal() <= logger.ordinal());
	}

	public static LEVEL getLevel(java.util.logging.Logger log)
	{
		java.util.logging.Level lvl = getEffectiveLevel(log);
		return mapLevel(lvl);
	}

	public static LEVEL getLevel(java.util.logging.Handler log)
	{
		java.util.logging.Level lvl = log.getLevel();
		return mapLevel(lvl);
	}

	public static LEVEL getLevel(org.slf4j.Logger log)
	{
		if (log.isTraceEnabled()) return LEVEL.ALL;
		if (log.isDebugEnabled()) return LEVEL.TRC;
		if (log.isInfoEnabled()) return LEVEL.INFO;
		if (log.isWarnEnabled()) return LEVEL.WARN;
		if (log.isErrorEnabled()) return LEVEL.ERR;
		return LEVEL.OFF;
	}

	public static LEVEL getLevel(org.apache.commons.logging.Log log)
	{
		if (log.isDebugEnabled()) return LEVEL.ALL;
		if (log.isInfoEnabled()) return LEVEL.INFO;
		if (log.isWarnEnabled()) return LEVEL.WARN;
		if (log.isErrorEnabled() || log.isFatalEnabled()) return LEVEL.ERR;
		return LEVEL.OFF;
	}

	public static LEVEL mapLevel(java.util.logging.Level jul_lvl)
	{
		int lvl = jul_lvl.intValue();
		if (lvl == java.util.logging.Level.OFF.intValue()) return LEVEL.OFF;
		if (lvl == java.util.logging.Level.ALL.intValue()) return LEVEL.ALL;
		if (lvl >= java.util.logging.Level.SEVERE.intValue()) return LEVEL.ERR;
		if (lvl >= java.util.logging.Level.WARNING.intValue()) return LEVEL.WARN;
		if (lvl >= java.util.logging.Level.INFO.intValue()) return LEVEL.INFO;
		if (lvl >= java.util.logging.Level.FINE.intValue()) return LEVEL.TRC;
		if (lvl >= java.util.logging.Level.FINER.intValue()) return LEVEL.TRC2;
		if (lvl >= java.util.logging.Level.FINEST.intValue()) return LEVEL.TRC3;
		return LEVEL.TRC4;
	}

	public static java.util.logging.Level mapLevel(LEVEL lvl)
	{
		switch (lvl)
		{
			case OFF: return java.util.logging.Level.OFF;
			case ALL: return java.util.logging.Level.ALL;
			case ERR: return java.util.logging.Level.SEVERE;
			case WARN: return java.util.logging.Level.WARNING;
			case INFO: return java.util.logging.Level.INFO;
			case TRC: return java.util.logging.Level.FINE;
			case TRC2: return java.util.logging.Level.FINER;
			default: return java.util.logging.Level.FINEST;
		}
	}

	// there will be a Level set somewhere up the Logger hierarchy
	public static java.util.logging.Level getEffectiveLevel(java.util.logging.Logger log)
	{
		java.util.logging.Level lvl = null;
		do {
			if ((lvl = log.getLevel()) != null) break;
			log = log.getParent();
		} while (log != null);
		return lvl;
	}
}