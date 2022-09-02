/*
 * Copyright 2018-2022 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.greylog_slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Test;
import org.junit.Assert;

public class LoggerFactoryTest {
	@Test
	public void testBasic() {
		Logger log = LoggerFactory.getLogger(LoggerFactoryTest.class);
		Assert.assertNotNull(log);
		Assert.assertTrue("log="+log.getClass().getName()+"/"+log, log instanceof LoggerAdapter);
		Logger log2 = LoggerFactory.getLogger(LoggerFactoryTest.class);
		verifySame(log, log2, true);
		verifySame(((LoggerAdapter)log).getDelegate(), ((LoggerAdapter)log2).getDelegate(), true);

		log2 = LoggerFactory.getLogger("anotherclass");
		verifySame(log, log2, false); //sameness of delegates depends on grey.logger.sinkstdio system property

		// the output of these logs just needs to be examined manually
		log.info("Dummy msg");
		log.info("Dummy msg with param1={} and param2={}", "val1", "val2");
		log.error("Dummy error msg with param1={} and param2={}", "val1", "val2", new Exception("Dumy Exception"));
		log2.info("Dummy log2 msg");
	}

	private static void verifySame(Object o1, Object o2, boolean same) {
		String desc = objectSynopsis(o1)+" vs "+objectSynopsis(o2);
		if (same) {
			Assert.assertSame(desc, o1, o2);
		} else {
			Assert.assertNotSame(desc, o1, o2);
		}
	}

	private static String objectSynopsis(Object o) {
		if (o == null) return null;
		return o.getClass().getName()+"/"+System.identityHashCode(o);
	}
}