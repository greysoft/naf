/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.greylog_slf4j;

public class LoggerFactory
	implements org.slf4j.ILoggerFactory
{
	static {
		com.grey.base.utils.PkgInfo.announceJAR(LoggerFactory.class, "GreyLog-SLF4J", null);
	}

	@Override
	public org.slf4j.Logger getLogger(String name)
	{
		try {
			com.grey.logging.Logger logger = com.grey.logging.Factory.getLogger(name);
			if (logger == null) {
				System.out.println("* * * GreyLog-SLF4J: Logger="+name+" is not defined");
				return null;
			}
			return new LoggerAdapter(name, logger);
		} catch (Throwable ex) {
			throw new RuntimeException("GreyLog-SLF4J factory failed to create logger="+name+" - "+ex);
		}
	}
}
