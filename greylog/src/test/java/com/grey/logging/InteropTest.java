/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.logging.Logger.LEVEL;

public class InteropTest
{
	@org.junit.Test
	public void testActive()
	{
		LEVEL logger = LEVEL.OFF;
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.ERR));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.TRC5));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.OFF));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.ALL));

		logger = LEVEL.ALL;
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.OFF));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ALL));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ERR));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.TRC5));

		logger = LEVEL.TRC5;  //same effect as ALL, but "ALL" is easy to understand in config files
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ERR));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.TRC5));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.OFF));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ALL));

		logger = LEVEL.ERR;
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ERR));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.WARN));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.TRC5));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.OFF));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ALL));

		logger = LEVEL.INFO;
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.INFO));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.WARN));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ERR));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.TRC));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.TRC5));
		org.junit.Assert.assertFalse(Interop.isActive(logger, LEVEL.OFF));
		org.junit.Assert.assertTrue(Interop.isActive(logger, LEVEL.ALL));
	}

	@org.junit.Test
	public void testLevels_Grey2JUL()
	{
		LEVEL[] levels = LEVEL.values();
		for (int idx = 0; idx != levels.length; idx++)
		{
			LEVEL lvl = levels[idx];
			java.util.logging.Level jul_lvl;
			switch (lvl)
			{
				case OFF: jul_lvl = java.util.logging.Level.OFF; break;
				case ALL: jul_lvl = java.util.logging.Level.ALL; break;
				case ERR: jul_lvl = java.util.logging.Level.SEVERE; break;
				case WARN: jul_lvl = java.util.logging.Level.WARNING; break;
				case INFO: jul_lvl = java.util.logging.Level.INFO; break;
				case TRC: jul_lvl = java.util.logging.Level.FINE; break;
				case TRC2: jul_lvl = java.util.logging.Level.FINER; break;
				default: jul_lvl = java.util.logging.Level.FINEST; break;
			}
			org.junit.Assert.assertEquals(jul_lvl, Interop.mapLevel(lvl));
			if (lvl.ordinal() > LEVEL.TRC3.ordinal()) lvl = LEVEL.TRC3;
			org.junit.Assert.assertEquals(lvl, Interop.mapLevel(Interop.mapLevel(lvl)));
		}
	}

	@org.junit.Test
	public void testJUL()
	{
		java.util.logging.Logger log = java.util.logging.Logger.getLogger(getClass().getName());
		java.util.logging.Level jul_lvl = Interop.getEffectiveLevel(log);
		java.util.logging.Logger effectiveLog = log;
		java.util.logging.Handler[] handlers;

		do {
			handlers = effectiveLog.getHandlers();
			if (handlers != null && handlers.length != 0) break;
			effectiveLog = effectiveLog.getParent();
		} while (effectiveLog != null);

		LEVEL lvl = Interop.mapLevel(jul_lvl);
		org.junit.Assert.assertEquals(lvl, Interop.getLevel(log));
		org.junit.Assert.assertEquals(lvl, Interop.getLevel(handlers[0]));
	}
}