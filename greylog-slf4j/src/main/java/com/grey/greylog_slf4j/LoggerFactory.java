/*
 * Copyright 2011-2022 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.greylog_slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.grey.base.config.SysProps;
import com.grey.logging.Parameters;
import com.grey.logging.adapters.AdapterSLF4J;

public class LoggerFactory
	implements org.slf4j.ILoggerFactory
{
	static {
		System.setProperty("slf4j.detectLoggerNameMismatch", "true");
		com.grey.base.utils.PkgInfo.announceJAR(LoggerFactory.class, "GreyLog-SLF4J", null);
	}
	private static final boolean NOCFG = SysProps.get("grey.slf4j.nocfg", false);

	private final Map<String,LoggerAdapter> loggers = new ConcurrentHashMap<>();

	@Override
	public org.slf4j.Logger getLogger(String name)
	{
		if (AdapterSLF4J.class.getName().equals(SysProps.get(Parameters.SYSPROP_LOGCLASS))) {
			String msg = "You cannot set "+Parameters.SYSPROP_LOGCLASS+"="+SysProps.get(Parameters.SYSPROP_LOGCLASS)+" when greylog-slf4j is on your classpath"
					+"\n\tThat environment setting directs GreyLog loggers to external facades (such as SLF4J) while greylog-slf4j performs the opposite mapping";
			throw new IllegalStateException(msg);
		}
		LoggerAdapter logger = loggers.computeIfAbsent(name, s -> createLogger(s));
		return logger;
	}

	private static LoggerAdapter createLogger(String name) {
		com.grey.logging.Logger logger;
		try {
			if (NOCFG) {
				logger = com.grey.logging.Factory.getLogger(new Parameters.Builder().build(), name);
			} else {
				logger = com.grey.logging.Factory.getLogger(name);
			}
		} catch (Throwable ex) {
			throw new IllegalStateException("GreyLog-SLF4J factory failed to create logger="+name+" - "+ex);
		}
		return new LoggerAdapter(name, logger);
	}
}
