/*
 * Copyright 2011-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.greylog_slf4j;

import com.grey.logging.Logger.LEVEL;

//NB: This class is of type org.slf4j.Logger, as its MarkerIgnoringBase superclass implements the interface
public class LoggerAdapter
	extends org.slf4j.helpers.MarkerIgnoringBase
	implements java.io.Closeable, java.io.Flushable
{
	private static final long serialVersionUID = 1L;
	private static final boolean dumpStack = com.grey.base.config.SysProps.get("grey.logger.slf4j.dumpstack", true);

	private final com.grey.logging.Logger defaultLogger;
	private final String lname;

	public com.grey.logging.Logger getDelegate() {return defaultLogger;}

	protected LoggerAdapter(String lname, com.grey.logging.Logger logger)
	{
		if (logger == null) throw new IllegalArgumentException(getClass().getName()+" has null delegate");
		this.lname = lname;
		this.defaultLogger = logger;
	}

	//override org.slf4j.helpers.MarkerIgnoringBase method
	@Override
	public String getName()
	{
		return lname;
	}

	@Override
	public boolean isTraceEnabled()
	{
		return isActive(LEVEL.TRC2);
	}

	@Override
	public boolean isDebugEnabled()
	{
		return isActive(LEVEL.TRC);
	}

	@Override
	public boolean isInfoEnabled()
	{
		return isActive(LEVEL.INFO);
	}

	@Override
	public boolean isWarnEnabled()
	{
		return isActive(LEVEL.WARN);
	}

	@Override
	public boolean isErrorEnabled()
	{
		return isActive(LEVEL.ERR);
	}

	@Override
	public void trace(String msg)
	{
		trace(msg, (Throwable)null);
	}

	@Override
	public void trace(String msg, Throwable ex)
	{
		log(LEVEL.TRC2, msg, ex);
	}

	@Override
	public void trace(String fmt, Object arg)
	{
		trace(fmt, arg, null);
	}

	@Override
	public void trace(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.TRC2, fmt, arg1, arg2);
	}

	@Override
	public void trace(String fmt, Object... args)
	{
		formatAndLog(LEVEL.TRC2, fmt, args);
	}

	@Override
	public void debug(String msg)
	{
		debug(msg, (Throwable)null);
	}

	@Override
	public void debug(String msg, Throwable ex)
	{
		log(LEVEL.TRC, msg, ex);
	}

	@Override
	public void debug(String fmt, Object arg)
	{
		debug(fmt, arg, null);
	}

	@Override
	public void debug(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.TRC, fmt, arg1, arg2);
	}

	@Override
	public void debug(String fmt, Object... args)
	{
		formatAndLog(LEVEL.TRC, fmt, args);
	}

	@Override
	public void info(String msg)
	{
		info(msg, (Throwable)null);
	}

	@Override
	public void info(String msg, Throwable ex)
	{
		log(LEVEL.INFO, msg, ex);
	}

	@Override
	public void info(String fmt, Object arg)
	{
		info(fmt, arg, null);
	}

	@Override
	public void info(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.INFO, fmt, arg1, arg2);
	}

	@Override
	public void info(String fmt, Object... args)
	{
		formatAndLog(LEVEL.INFO, fmt, args);
	}

	@Override
	public void warn(String msg)
	{
		warn(msg, (Throwable)null);
	}

	@Override
	public void warn(String msg, Throwable ex)
	{
		log(LEVEL.WARN, msg, ex);
	}

	@Override
	public void warn(String fmt, Object arg)
	{
		warn(fmt, arg, null);
	}

	@Override
	public void warn(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.WARN, fmt, arg1, arg2);
	}

	@Override
	public void warn(String fmt, Object... args)
	{
		formatAndLog(LEVEL.WARN, fmt, args);
	}

	@Override
	public void error(String msg)
	{
		error(msg, (Throwable)null);
	}

	@Override
	public void error(String msg, Throwable ex)
	{
		log(LEVEL.ERR, msg, ex);
	}

	@Override
	public void error(String fmt, Object arg)
	{
		error(fmt, arg, null);
	}

	@Override
	public void error(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.ERR, fmt, arg1, arg2);
	}

	@Override
	public void error(String fmt, Object... args)
	{
		formatAndLog(LEVEL.ERR, fmt, args);
	}

	private void formatAndLog(LEVEL lvl, String fmt, Object arg1, Object arg2)
	{
		if (!isActive(lvl)) return;
		formatAndLog(lvl, fmt, new Object[] {arg1, arg2});
	}

	private void formatAndLog(LEVEL lvl, String fmt, Object[] args)
	{
		if (!isActive(lvl)) return;
		org.slf4j.helpers.FormattingTuple tp = org.slf4j.helpers.MessageFormatter.arrayFormat(fmt, args);
		log(lvl, tp.getMessage(), tp.getThrowable());
	}

	private void log(LEVEL lvl, String msg, Throwable ex)
	{
		getLogger().log(lvl, ex, dumpStack, "SLF4J-"+getName()+" "+msg);
	}


	@Override
	public void flush() throws java.io.IOException
	{
		defaultLogger.flush();
	}

	@Override
	public void close()
	{
		defaultLogger.close();
	}

	// The basis for doing this, is that the current Thread logger probably represents the Dispatcher context we're running in.
	// And at startup time, it should map to the NAF boot logger.
	private com.grey.logging.Logger getLogger() {
		com.grey.logging.Logger logger = com.grey.logging.Logger.getThreadLogger();
		if (logger == null) logger = defaultLogger;
		return logger;
	}

	private boolean isActive(LEVEL lvl) {
		return  getLogger().isActive(lvl);
	}

	@Override
	public String toString()
	{
		return super.toString()+" with delegate="+defaultLogger.getClass().getName()+"/"+defaultLogger+" - current="+getLogger();
	}
}