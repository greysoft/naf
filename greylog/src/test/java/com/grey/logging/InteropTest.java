/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.logging.Interop.LEVEL;

public class InteropTest
{
	private static class MapJUL
	{
		public LEVEL lvl;
		public java.util.logging.Level jul_lvl;
		public MapJUL(java.util.logging.Level p1, LEVEL p2) {jul_lvl = p1; lvl = p2;}
	}

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
	public void testLevels_JUL2Grey()
	{
		MapJUL[] map = new MapJUL[]{new MapJUL(java.util.logging.Level.ALL, LEVEL.ALL),
				new MapJUL(java.util.logging.Level.OFF, LEVEL.OFF),
				new MapJUL(new Interop.LevelJUL("HIGHEST", java.util.logging.Level.OFF.intValue()-1), LEVEL.ERR),
				new MapJUL(java.util.logging.Level.SEVERE, LEVEL.ERR),
				new MapJUL(new Interop.LevelJUL("SEVERE-", java.util.logging.Level.SEVERE.intValue()-1), LEVEL.WARN),
				new MapJUL(java.util.logging.Level.WARNING, LEVEL.WARN),
				new MapJUL(new Interop.LevelJUL("WARNING-", java.util.logging.Level.WARNING.intValue()-1), LEVEL.INFO),
				new MapJUL(java.util.logging.Level.INFO, LEVEL.INFO),
				new MapJUL(new Interop.LevelJUL("INFO-", java.util.logging.Level.INFO.intValue()-1), LEVEL.TRC),
				new MapJUL(java.util.logging.Level.CONFIG, LEVEL.TRC),
				new MapJUL(new Interop.LevelJUL("CONFIG-", java.util.logging.Level.CONFIG.intValue()-1), LEVEL.TRC),
				new MapJUL(java.util.logging.Level.FINE, LEVEL.TRC),
				new MapJUL(new Interop.LevelJUL("FINE-", java.util.logging.Level.FINE.intValue()-1), LEVEL.TRC2),
				new MapJUL(java.util.logging.Level.FINER, LEVEL.TRC2),
				new MapJUL(new Interop.LevelJUL("FINER-", java.util.logging.Level.FINER.intValue()-1), LEVEL.TRC3),
				new MapJUL(java.util.logging.Level.FINEST, LEVEL.TRC3),
				new MapJUL(new Interop.LevelJUL("FINEST-", java.util.logging.Level.FINEST.intValue()-1), LEVEL.TRC4),
				new MapJUL(new Interop.LevelJUL("LOWEST", java.util.logging.Level.ALL.intValue()+1), LEVEL.TRC5),
				new MapJUL(java.util.logging.Level.ALL, LEVEL.ALL)};
		for (int idx = 0; idx != map.length; idx++)
		{
			org.junit.Assert.assertTrue(map[idx].jul_lvl.toString(), Interop.mapLevel(map[idx].jul_lvl) == map[idx].lvl);
		}
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
				case TRC3: jul_lvl = java.util.logging.Level.FINEST; break;
				case TRC4: jul_lvl = Interop.JULTRC4; break;
				case TRC5: jul_lvl = Interop.JULTRC5; break;
				default: throw new RuntimeException("Missing test case for GreyLog Level="+lvl);
			}
			org.junit.Assert.assertTrue(lvl.toString(), Interop.mapLevel(lvl) == jul_lvl);
			org.junit.Assert.assertTrue(Interop.mapLevel(Interop.mapLevel(lvl)) == lvl);
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

	@org.junit.Test
	public void testSLF4J()
	{
		// we don't know what implementation might happen to be bound, or what its logging level will be, but just make sure it doesn't throw
		org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
		LEVEL lvl = Interop.getLevel(logger);
		System.out.println(getClass().getName()+" obtained SLF4J Logger = "+logger+" with level="+lvl);
	}

	@org.junit.Test
	public void testJCL()
	{
		// we don't know what implementation might happen to be bound, or what its logging level will be, but just make sure it doesn't throw
		org.apache.commons.logging.Log logger = org.apache.commons.logging.LogFactory.getLog(getClass());
		LEVEL lvl = Interop.getLevel(logger);
		System.out.println(getClass().getName()+" obtained JCL Logger = "+logger+" with level="+lvl);
	}

	@org.junit.Test
	public void testMiscellaneous()
	{
		try {
			String obj = "hello";
			Interop.getLevel(obj);
			org.junit.Assert.fail("getLevel(Object) failed to reject Object="+obj.getClass().getName());
		} catch (UnsupportedOperationException ex) {
			//ok, expected
		}
	}
}
