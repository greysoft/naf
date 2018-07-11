/*
 * Copyright 2011-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.greylog_slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.grey.base.config.SysProps;

public class LoggerFactory
	implements org.slf4j.ILoggerFactory
{
	static {
		com.grey.base.utils.PkgInfo.announceJAR(LoggerFactory.class, "GreyLog-SLF4J", null);
	}
	private static final boolean NOCFG = SysProps.get("grey.slf4j.nocfg", false);

	private final Map<String,LoggerAdapter> loggers = new ConcurrentHashMap<>();

	@Override
	public org.slf4j.Logger getLogger(String name)
	{
		LoggerAdapter logger = loggers.get(name);
		if (logger == null) {
			logger = createLogger(name);
			LoggerAdapter logger2 = loggers.putIfAbsent(name, logger);
			if (logger2 != null) logger = logger2;
		}
		return logger;
	}

	private static LoggerAdapter createLogger(String name) {
		com.grey.logging.Logger logger;
		try {
			if (NOCFG) {
				logger = com.grey.logging.Factory.getLogger(new com.grey.logging.Parameters(), name);
			} else {
				logger = com.grey.logging.Factory.getLogger(name);
			}
		} catch (Throwable ex) {
			throw new IllegalStateException("GreyLog-SLF4J factory failed to create logger="+name+" - "+ex);
		}
		return new LoggerAdapter(name, logger);
	}
}
