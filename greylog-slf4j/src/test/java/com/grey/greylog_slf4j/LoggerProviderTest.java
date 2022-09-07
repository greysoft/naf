/*
 * Copyright 2018-2022 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.greylog_slf4j;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.Assert;

import org.slf4j.ILoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

public class LoggerProviderTest {
	@Test
	public void testProvider() {
		ServiceLoader<SLF4JServiceProvider> loader = ServiceLoader.load(SLF4JServiceProvider.class);
		AtomicBoolean found = new AtomicBoolean();
		loader.forEach(provider -> {
			ILoggerFactory factory = provider.getLoggerFactory();
			System.out.println("Provider="+provider.getClass().getName()+" with factory="+factory.getClass().getName()+", version="+provider.getRequestedApiVersion());
			if (provider.getClass() == LoggerProvider.class) {
				Assert.assertSame(LoggerFactory.class, factory.getClass());
				found.set(true);
			}
		});
		Assert.assertTrue(found.get());
	}
}