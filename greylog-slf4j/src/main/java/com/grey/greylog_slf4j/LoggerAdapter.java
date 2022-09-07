/*
 * Copyright 2011-2022 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.greylog_slf4j;

import com.grey.logging.Logger.LEVEL;

//NB: This class is of type org.slf4j.Logger, as its MarkerIgnoringBase superclass implements the interface
public class LoggerAdapter
	extends org.slf4j.helpers.LegacyAbstractLogger
	implements java.io.Closeable, java.io.Flushable
{
	private static final long serialVersionUID = 1L;
	private static final boolean dumpStack = com.grey.base.config.SysProps.get("grey.logger.slf4j.dumpstack", true);

	private final com.grey.logging.Logger defaultLogger;

	public com.grey.logging.Logger getDelegate() {return defaultLogger;}

	protected LoggerAdapter(String lname, com.grey.logging.Logger logger) {
		if (logger == null) throw new IllegalArgumentException(getClass().getName()+" has null delegate");
		this.name = lname;
		this.defaultLogger = logger;
	}

	@Override
	protected String getFullyQualifiedCallerName() {return null;}

	@Override
	public void flush() throws java.io.IOException {
		defaultLogger.flush();
	}

	@Override
	public void close() {
		defaultLogger.close();
	}

	@Override
	public boolean isTraceEnabled() {
		return isActive(LEVEL.TRC2);
	}

	@Override
	public boolean isDebugEnabled() {
		return isActive(LEVEL.TRC);
	}

	@Override
	public boolean isInfoEnabled() {
		return isActive(LEVEL.INFO);
	}

	@Override
	public boolean isWarnEnabled() {
		return isActive(LEVEL.WARN);
	}

	@Override
	public boolean isErrorEnabled() {
		return isActive(LEVEL.ERR);
	}

	private boolean isActive(LEVEL lvl) {
		return  getLogger().isActive(lvl);
	}

	@Override
	protected void handleNormalizedLoggingCall(org.slf4j.event.Level slf4jLevel, org.slf4j.Marker marker, String fmt, Object[] args, Throwable ex) {
		LEVEL lvl = mapSlf4jLogLevel(slf4jLevel);
		if (!isActive(lvl)) return;
		org.slf4j.helpers.FormattingTuple tp = org.slf4j.helpers.MessageFormatter.arrayFormat(fmt, args);
		getLogger().log(lvl, ex, dumpStack, "SLF4J-"+getName()+" "+tp.getMessage());
	}

	// The basis for doing this, is that the current Thread logger probably represents the Dispatcher context we're running in.
	// And at startup time, it should map to the NAF boot logger.
	private com.grey.logging.Logger getLogger() {
		com.grey.logging.Logger logger = com.grey.logging.Logger.getThreadLogger();
		if (logger == null) logger = defaultLogger;
		return logger;
	}

	private static LEVEL mapSlf4jLogLevel(org.slf4j.event.Level lvl) {
		switch (lvl) {
		case ERROR:
			return LEVEL.ERR;
		case WARN:
			return LEVEL.WARN;
		case INFO:
			return LEVEL.INFO;
		case DEBUG:
			return LEVEL.TRC;
		case TRACE:
			return LEVEL.TRC2;
		default:
			return LEVEL.ERR;
		}
	}

	@Override
	public String toString() {
		return super.toString()+" with delegate="+defaultLogger.getClass().getName()+"/"+defaultLogger+" - current="+getLogger();
	}
}