/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package org.slf4j.impl;

public class StaticLoggerBinder
	implements org.slf4j.spi.LoggerFactoryBinder
{
	private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

	private final org.slf4j.ILoggerFactory factory = new com.grey.greylog_slf4j.LoggerFactory();

	public static final StaticLoggerBinder getSingleton()
	{
		return SINGLETON;
	}

	private StaticLoggerBinder() {}

	@Override
	public org.slf4j.ILoggerFactory getLoggerFactory()
	{
		return factory;
	}

	@Override
	public String getLoggerFactoryClassStr()
	{
		return getLoggerFactory().getClass().getName();
	}
}
