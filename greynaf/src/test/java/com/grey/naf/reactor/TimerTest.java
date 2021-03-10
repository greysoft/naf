/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.TestUtils;

public class TimerTest
{
	static {
		TestUtils.initPaths(TimerTest.class);
	}

	private static final ApplicationContextNAF appctx = TestUtils.createApplicationContext("TimerTest", true);

	private static class Handler
		implements TimerNAF.Handler
	{
		TimerNAF tmr2;
		TimerNAF tmr3;
		int tmr1_cnt;
		int tmr2_cnt;
		int tmr3_cnt;
		boolean completed;

		Handler() {} //make explicit with non-private access, to eliminate synthetic accessor

		@Override
		public void timerIndication(TimerNAF tmr, Dispatcher dsptch) throws java.io.IOException
		{
			dsptch.getLogger().info("TimerTest: Timer="+tmr.getID()+"/"+tmr.getType()+" - tmr1="+tmr1_cnt);
			switch (tmr.getType()) {
			case 1:
				tmr1_cnt++;
				if (tmr2 != null) tmr2.cancel(); //cancel a pending timer
				if (tmr3 != null) tmr3.cancel(); //cancel a scheduled timer
				tmr2 = null;
				tmr3 = null;
				if (tmr1_cnt == 2) {
					completed = true;
					dsptch.stop();
				} else {
					//set this later than tmr3, to make sure that got cancelled
					dsptch.setTimer(100, 1, this);
				}
				break;
			case 2:
				tmr2_cnt++;
				break;
			case 3:
				tmr3_cnt++;
				break;
			default:
				throw new RuntimeException("Unrecognised timer="+tmr.getType());
			}
		}

		@Override
		public void eventError(TimerNAF tmr, Dispatcher d, Throwable ex) {}
	}

	@org.junit.Test
	public void test() throws java.io.IOException
	{
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef.Builder()
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));
		Handler handler = new Handler();

		handler.tmr2 = dsptch.setTimer(0, 2, handler);
		handler.tmr3 = dsptch.setTimer(50, 3, handler);
		dsptch.setTimer(0, 1, handler);

		dsptch.start();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());

		org.junit.Assert.assertTrue(handler.completed);
		org.junit.Assert.assertEquals(2, handler.tmr1_cnt);
		org.junit.Assert.assertEquals(0, handler.tmr2_cnt);
		org.junit.Assert.assertEquals(0, handler.tmr3_cnt);
	}
}