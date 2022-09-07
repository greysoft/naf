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
		Logger log = LoggerFactory.getLogger(getClass());
		Assert.assertSame(LoggerAdapter.class, log.getClass());

		Logger log2 = LoggerFactory.getLogger(getClass());
		Assert.assertSame(LoggerAdapter.class, log2.getClass());
		Assert.assertSame(log, log2);

		log2 = LoggerFactory.getLogger("anotherclass");
		Assert.assertSame(LoggerAdapter.class, log2.getClass());
		Assert.assertNotSame(log, log2);

		// the output of these logs just needs to be examined manually
		log.debug("Dummy debug msg"); //shouldn't come out
		log.info("Dummy msg");
		log2.info("Dummy log2 msg");
		log.warn("Dummy msg with param1={}", "val1");
		log.info("Dummy msg with param1={}, param2={}", "val1", "val2");
		log.error("Dummy msg with param1={}, param2={}, param3={}", "val1", "val2", "val3");
		log.error("Dummy error msg with param1={}", "val1", new Exception("Dumy Exception"));
	}
}