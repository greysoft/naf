/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
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

	private com.grey.logging.Logger logger;
	private String name;

	protected LoggerAdapter(String name, com.grey.logging.Logger logger)
	{
		this.name = name;
		this.logger = logger;
	}

	//override org.slf4j.helpers.MarkerIgnoringBase method
	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public boolean isTraceEnabled()
	{
		return logger.isActive(LEVEL.TRC2);
	}

	@Override
	public boolean isDebugEnabled()
	{
		return logger.isActive(LEVEL.TRC);
	}

	@Override
	public boolean isInfoEnabled()
	{
		return logger.isActive(LEVEL.INFO);
	}

	@Override
	public boolean isWarnEnabled()
	{
		return logger.isActive(LEVEL.WARN);
	}

	@Override
	public boolean isErrorEnabled()
	{
		return logger.isActive(LEVEL.ERR);
	}

	@Override
	public void trace(String msg)
	{
		logger.log(LEVEL.TRC2, msg);
	}

	@Override
	public void trace(String msg, Throwable ex)
	{
		logger.log(LEVEL.TRC2, ex, dumpStack, msg);
	}

	@Override
	public void trace(String fmt, Object arg)
	{
		formatAndLog(LEVEL.TRC2, fmt, arg, null);
	}

	@Override
	public void trace(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.TRC2, fmt, arg1, arg2);
	}

	@Override
	public void trace(String fmt, Object[] args)
	{
		formatAndLog(LEVEL.TRC2, fmt, args);
	}

	@Override
	public void debug(String msg)
	{
		logger.log(LEVEL.TRC, msg);
	}

	@Override
	public void debug(String msg, Throwable ex)
	{
		logger.log(LEVEL.TRC, ex, dumpStack, msg);
	}

	@Override
	public void debug(String fmt, Object arg)
	{
		formatAndLog(LEVEL.TRC, fmt, arg, null);
	}

	@Override
	public void debug(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.TRC, fmt, arg1, arg2);
	}

	@Override
	public void debug(String fmt, Object[] args)
	{
		formatAndLog(LEVEL.TRC, fmt, args);
	}

	@Override
	public void info(String msg)
	{
		logger.log(LEVEL.INFO, msg);
	}

	@Override
	public void info(String msg, Throwable ex)
	{
		logger.log(LEVEL.INFO, ex, dumpStack, msg);
	}

	@Override
	public void info(String fmt, Object arg)
	{
		formatAndLog(LEVEL.INFO, fmt, arg, null);
	}

	@Override
	public void info(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.INFO, fmt, arg1, arg2);
	}

	@Override
	public void info(String fmt, Object[] args)
	{
		formatAndLog(LEVEL.INFO, fmt, args);
	}

	@Override
	public void warn(String msg)
	{
		logger.log(LEVEL.WARN, msg);
	}

	@Override
	public void warn(String msg, Throwable ex)
	{
		logger.log(LEVEL.WARN, ex, dumpStack, msg);
	}

	@Override
	public void warn(String fmt, Object arg)
	{
		formatAndLog(LEVEL.WARN, fmt, arg, null);
	}

	@Override
	public void warn(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.WARN, fmt, arg1, arg2);
	}

	@Override
	public void warn(String fmt, Object[] args)
	{
		formatAndLog(LEVEL.WARN, fmt, args);
	}

	@Override
	public void error(String msg)
	{
		logger.log(LEVEL.ERR, msg);
	}

	@Override
	public void error(String msg, Throwable ex)
	{
		logger.log(LEVEL.ERR, ex, dumpStack, msg);
	}

	@Override
	public void error(String fmt, Object arg)
	{
		formatAndLog(LEVEL.ERR, fmt, arg, null);
	}

	@Override
	public void error(String fmt, Object arg1, Object arg2)
	{
		formatAndLog(LEVEL.ERR, fmt, arg1, arg2);
	}

	@Override
	public void error(String fmt, Object[] args)
	{
		formatAndLog(LEVEL.ERR, fmt, args);
	}

	private void formatAndLog(LEVEL lvl, String fmt, Object arg1, Object arg2)
	{
		formatAndLog(lvl, fmt, new Object[] {arg1, arg2});
	}

	private void formatAndLog(LEVEL lvl, String fmt, Object[] args)
	{
		if (!logger.isActive(lvl)) return;
		org.slf4j.helpers.FormattingTuple tp = org.slf4j.helpers.MessageFormatter.arrayFormat(fmt, args);
		logger.log(lvl, tp.getThrowable(), dumpStack, tp.getMessage());
	}


	@Override
	public void flush() throws java.io.IOException
	{
		logger.flush();
	}

	@Override
	public void close()
	{
		logger.close();
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName()+":"+logger;
	}
}