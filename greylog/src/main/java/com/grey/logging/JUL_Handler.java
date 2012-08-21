/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.logging.Interop.LEVEL;

/**
 * This class wraps the Grey Logger in a JUL handler.
 */
public class JUL_Handler
	extends java.util.logging.Handler
	implements java.io.Closeable, java.io.Flushable
{
	private final com.grey.logging.Logger log;
	private final boolean fullinfo;

	// A null-name invocation has the same semantics as the no-name method below
	public static java.util.logging.Logger getLogger(com.grey.logging.Logger grylog, String name, boolean fullinfo)
	{
		return getLogger(grylog, name, name == null, fullinfo);
	}

	// This method creates a dedicated JUL logger for this Grey Logger instance, and installs the Grey Logger as its only handler,
	// with no recourse to parent handlers.
	// If a JUL logger has already been created for this Grey Logger instance, then it is returned, but it is reinitialised.
	public static java.util.logging.Logger getLogger(com.grey.logging.Logger grylog)
	{
		return getLogger(grylog, null, true, false);
	}

	public JUL_Handler(com.grey.logging.Logger log, boolean fullinfo)
	{
		this.log = log;
		this.fullinfo = fullinfo;
		log.setLevel(LEVEL.ALL);  //anything routed to this handler has already been approved, so make sure Grey Logger allows it
	}

	@Override
	public void close()
	{
		log.close();
	}

	@Override
	public void flush()
	{
		try {
			log.flush();
		} catch (Exception ex) {
			log.log(LEVEL.ERR, "Failed to flush JUL handler");
		}
	}

	@Override
	public void publish(java.util.logging.LogRecord rec)
	{
		String msg = rec.getMessage();
		Throwable ex = rec.getThrown();

		if (fullinfo || ex != null)
		{
			StringBuilder buf = new StringBuilder(128);
			buf.append("[T").append(rec.getThreadID()).append("/SEQ=").append(rec.getSequenceNumber());
			buf.append(' ').append(rec.getSourceClassName()).append(':').append(rec.getSourceMethodName()).append("] ");
			buf.append(msg);
			Object[] params = rec.getParameters();

			if (params != null && params.length != 0)
			{
				buf.append(" - PARAMS=").append(params.length).append(':');
				for (int idx = 0; idx != params.length; idx++)
				{
					buf.append(com.grey.base.config.SysProps.EOL).append("- ").append(params[idx]);
				}
			}
			if (ex != null) buf.append(com.grey.base.GreyException.summary(ex, true));
			msg = buf.toString();
		}
		LEVEL lvl = Interop.mapLevel(rec.getLevel());
		log.log(lvl, msg);
	}


	// Note that the blank name retrieves the root logger, which has a single handler by default.
	// The weird GLOBAL_LOGGER_NAME does not actually return the root logger, but rather one of its immediate children, with no handlers.
	// The getAnonymousLogger() method always returns a new logger, with the same characteristics as GLOBAL (ie. child of root, no handlers)
	// A null name is illegal for the JUL API, but we use it to support the semantics of the getLogger(com.grey.base.logging.Logger) factory
	// method.
	private static java.util.logging.Logger getLogger(com.grey.logging.Logger grylog, String name,
			boolean exclusive, boolean fullinfo)
	{
		if (name == null) name = grylog.toString();
		java.util.logging.Logger log = java.util.logging.Logger.getLogger(name);

		if (exclusive)
		{
			java.util.logging.Handler[] handlers = log.getHandlers();
			for (int idx = 0; idx != handlers.length; idx++)
			{
				handlers[idx].flush();
				log.removeHandler(handlers[idx]);
			}
		}
		java.util.logging.Handler handler = new JUL_Handler(grylog, fullinfo);
		log.addHandler(handler);
		if (exclusive) log.setUseParentHandlers(false);
		return log;
	}
}
