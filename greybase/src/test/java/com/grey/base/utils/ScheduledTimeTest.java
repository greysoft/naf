/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.util.Calendar;
import com.grey.base.utils.ScheduledTime.FREQ;

/*
 * These tests need the "grey.timezone" system property to be set to "UTC", to work reliably.
 * This can be set in grey.properties or on the JVM command line with the "-D" option.
 * They will often work even if it's not set, but if you happen to run this at a time when
 * the chosen timezone has a daylight shift in between some of the before/after times we're
 * comparing, then they will fail.
 */
public class ScheduledTimeTest
{
	@org.junit.Test
	public void testIntervals()
	{
		if (TimeOps.TZDFLT == null) return; //fails too often if not set
		final long systime = System.currentTimeMillis();

		ScheduledTime sched = new ScheduledTime(FREQ.HOURLY);
		org.junit.Assert.assertEquals(FREQ.HOURLY, sched.frequency());
		org.junit.Assert.assertTrue(FREQ.MINUTE.ordinal() < FREQ.HOURLY.ordinal());
		org.junit.Assert.assertEquals(0, sched.compare(FREQ.HOURLY));
		org.junit.Assert.assertTrue(sched.compare(FREQ.MINUTE) < 0);
		org.junit.Assert.assertTrue(sched.compare(FREQ.DAILY) > 0);
		org.junit.Assert.assertEquals("yyyyMMdd_HH00", sched.getFormat());
		verifyInterval(sched, systime, TimeOps.MSECS_PER_HOUR, Calendar.HOUR_OF_DAY, 23, true);  //hour is 0-23

		sched = new ScheduledTime(FREQ.MINUTE);
		org.junit.Assert.assertEquals("yyyyMMdd_HHmm", sched.getFormat());
		verifyInterval(sched, systime, TimeOps.MSECS_PER_MINUTE, Calendar.MINUTE, 59, true);

		sched = new ScheduledTime(FREQ.DAILY);
		org.junit.Assert.assertEquals("yyyyMMdd", sched.getFormat());
		verifyInterval(sched, systime, TimeOps.MSECS_PER_DAY, Calendar.DAY_OF_MONTH, 0, false); //we'll work out max-day later

		sched = new ScheduledTime(FREQ.MONTHLY);
		org.junit.Assert.assertEquals("yyyyMM", sched.getFormat());
		verifyInterval(sched, systime, 0, Calendar.MONTH, 11, true);  //month is 0-11

		sched = new ScheduledTime(FREQ.YEARLY);
		org.junit.Assert.assertEquals("yyyy", sched.getFormat());
		verifyInterval(sched, systime, 0, Calendar.YEAR, 1000000, true);  //effectively no modulus, year just keeps increasing

		// The Weekly interval is unique in that it's the only one which may or may not span other boundaries (such as year
		// and month) so we can make fewer assumptions.
		sched = new ScheduledTime(FREQ.WEEKLY);
		org.junit.Assert.assertEquals(ScheduledTime.getFormat(FREQ.DAILY), sched.getFormat());
		verifyInterval(sched, systime, 7L * TimeOps.MSECS_PER_DAY, 0, 0, false);
	}

	@org.junit.Test
	public void testTimestamp()
	{
		String template = "PartA_"+ScheduledTime.TOKEN_YEAR+"_PartB_"+ScheduledTime.TOKEN_MONTH+"_PartC_"+ScheduledTime.TOKEN_MDAY
				+"_PartD_"+ScheduledTime.TOKEN_HOUR+"_PartE_"+ScheduledTime.TOKEN_MIN+"_PartF_"+ScheduledTime.TOKEN_SEC
				+"_PartG_"+ScheduledTime.TOKEN_DT+"_PartZ.log";
		int sec = 59;
		int msec = 68;
		int min = 3;
		int hour = 4;
		Calendar dtcal = TimeOps.getCalendar(null);
		dtcal.setLenient(true);
		dtcal.set(Calendar.MILLISECOND, msec);
		dtcal.set(Calendar.SECOND, sec);
		dtcal.set(Calendar.MINUTE, min);
		dtcal.set(Calendar.HOUR_OF_DAY, hour);
		dtcal.set(Calendar.DAY_OF_MONTH, 5);
		dtcal.set(Calendar.MONTH, 6);
		dtcal.set(Calendar.YEAR, 2009);
		long systime = dtcal.getTimeInMillis();

		String template1 = template;
		String template2 = template;
		ScheduledTime sched = new ScheduledTime(FREQ.MINUTE);
		String str = sched.embedTimestamp(systime, template1);
		String str2 = sched.embedTimestamp(systime, template2);
		org.junit.Assert.assertEquals("PartA_2009_PartB_07_PartC_05_PartD_04_PartE_03_PartF_00_PartG_20090705_0403_PartZ.log", str);
		org.junit.Assert.assertEquals(str2, str);
		str = ScheduledTime.embedTimestamp(sched.frequency(), dtcal, template1, null);
		org.junit.Assert.assertEquals(str2, str);
		org.junit.Assert.assertEquals(msec, dtcal.get(java.util.Calendar.MILLISECOND));
		org.junit.Assert.assertEquals(sec, dtcal.get(java.util.Calendar.SECOND));

		sched = new ScheduledTime(FREQ.HOURLY);
		str = sched.embedTimestamp(systime, template1);
		org.junit.Assert.assertEquals("PartA_2009_PartB_07_PartC_05_PartD_04_PartE_00_PartF_00_PartG_20090705_0400_PartZ.log", str);
		org.junit.Assert.assertEquals(msec, dtcal.get(java.util.Calendar.MILLISECOND));
		org.junit.Assert.assertEquals(sec, dtcal.get(java.util.Calendar.SECOND));
		org.junit.Assert.assertEquals(min, dtcal.get(java.util.Calendar.MINUTE));
		org.junit.Assert.assertEquals(hour, dtcal.get(java.util.Calendar.HOUR));

		sched = new ScheduledTime(FREQ.DAILY);
		str = sched.embedTimestamp(systime, template1);
		org.junit.Assert.assertEquals("PartA_2009_PartB_07_PartC_05_PartD_00_PartE_00_PartF_00_PartG_20090705_PartZ.log", str);
		org.junit.Assert.assertEquals(msec, dtcal.get(java.util.Calendar.MILLISECOND));
		org.junit.Assert.assertEquals(sec, dtcal.get(java.util.Calendar.SECOND));
		org.junit.Assert.assertEquals(min, dtcal.get(java.util.Calendar.MINUTE));
		org.junit.Assert.assertEquals(hour, dtcal.get(java.util.Calendar.HOUR));

		sched = new ScheduledTime(FREQ.NEVER);
		str = sched.embedTimestamp(systime, template1);
		org.junit.Assert.assertEquals("PartA_2009_PartB_07_PartC_05_PartD_04_PartE_03_PartF_59_PartG_20090705_040359_PartZ.log", str);
		sched = new ScheduledTime(null);
		str2 = sched.embedTimestamp(systime, template1);
		org.junit.Assert.assertEquals(str, str2);
		str2 = ScheduledTime.embedTimestamp(null, dtcal, template1, null);
		org.junit.Assert.assertEquals(str, str2);
		//verify duplicate embedding has no effect
		str2 = ScheduledTime.embedTimestamp(null, dtcal, str, null);
		org.junit.Assert.assertEquals(str, str2);
		org.junit.Assert.assertEquals(msec, dtcal.get(java.util.Calendar.MILLISECOND));
		org.junit.Assert.assertEquals(sec, dtcal.get(java.util.Calendar.SECOND));
		org.junit.Assert.assertEquals(min, dtcal.get(java.util.Calendar.MINUTE));
		org.junit.Assert.assertEquals(hour, dtcal.get(java.util.Calendar.HOUR));

		// verify a string without any tokens is left unchanged
		template = "blah";
		str = ScheduledTime.embedTimestamp(null, dtcal, template, null);
		org.junit.Assert.assertEquals(template, str);
		str = ScheduledTime.embedTimestamp(null, null, template, null);
		org.junit.Assert.assertEquals(template, str);
		org.junit.Assert.assertEquals(msec, dtcal.get(java.util.Calendar.MILLISECOND));
		org.junit.Assert.assertEquals(sec, dtcal.get(java.util.Calendar.SECOND));
		org.junit.Assert.assertEquals(min, dtcal.get(java.util.Calendar.MINUTE));
		org.junit.Assert.assertEquals(hour, dtcal.get(java.util.Calendar.HOUR));
	}

	private void verifyInterval(ScheduledTime sched, long systime, long interval, int dtfld, int dtmax, boolean fromzero)
	{
		Calendar dtcal_now = TimeOps.getCalendar(systime, null);
		Calendar dtcal_base = TimeOps.getCalendar(systime, null);
		long basetime = ScheduledTime.getBaseTime(sched.frequency(), dtcal_base);
		long next1 = sched.set(systime);
		long next2 = sched.set(basetime);

		org.junit.Assert.assertEquals(0, dtcal_base.get(Calendar.MILLISECOND));
		org.junit.Assert.assertEquals(0, dtcal_base.get(Calendar.SECOND));
		if (sched.compare(FREQ.MINUTE) >= 0) {
			// this was a sub-minute adjustment, so the minute remains unchanged in the base-time
			org.junit.Assert.assertEquals(dtcal_now.get(Calendar.MINUTE), dtcal_base.get(Calendar.MINUTE));
		} else {
			org.junit.Assert.assertEquals(0, dtcal_base.get(Calendar.MINUTE));
		}
		if (sched.compare(FREQ.HOURLY) >= 0) {
			org.junit.Assert.assertEquals(dtcal_now.get(Calendar.HOUR_OF_DAY), dtcal_base.get(Calendar.HOUR_OF_DAY));
		} else {
			org.junit.Assert.assertEquals(0, dtcal_base.get(Calendar.HOUR_OF_DAY));
		}
		if (sched.compare(FREQ.DAILY) >= 0) {
			org.junit.Assert.assertEquals(dtcal_now.get(Calendar.DAY_OF_MONTH), dtcal_base.get(Calendar.DAY_OF_MONTH));
		} else if (sched.frequency() != FREQ.WEEKLY) {
			org.junit.Assert.assertEquals(1, dtcal_base.get(Calendar.DAY_OF_MONTH));
		}
		if (sched.compare(FREQ.MONTHLY) >= 0) {
			org.junit.Assert.assertEquals(dtcal_now.get(Calendar.MONTH), dtcal_base.get(Calendar.MONTH));
		} else if (sched.frequency() != FREQ.WEEKLY) {
			org.junit.Assert.assertEquals(0, dtcal_base.get(Calendar.MONTH));
		}
		if (sched.frequency() != FREQ.WEEKLY) {
			org.junit.Assert.assertEquals(dtcal_now.get(Calendar.YEAR), dtcal_base.get(Calendar.YEAR));
		}

		if (interval != 0) {
			org.junit.Assert.assertTrue(systime - basetime < interval);
			org.junit.Assert.assertEquals(basetime + interval, next1);
			org.junit.Assert.assertEquals(interval, sched.get() - basetime);
		} else {
			if (sched.frequency() == FREQ.MONTHLY) {
				org.junit.Assert.assertTrue(systime - basetime < (dtcal_now.get(Calendar.DAY_OF_MONTH) * TimeOps.MSECS_PER_DAY));
				org.junit.Assert.assertTrue(next1 - basetime >= 28L * TimeOps.MSECS_PER_DAY); //28-1 to allow for timezone diffs
				org.junit.Assert.assertTrue(next1 - basetime <= 31L * TimeOps.MSECS_PER_DAY); //31+1 to allow for timezone diffs
			} else if (sched.frequency() == FREQ.YEARLY) {
				org.junit.Assert.assertTrue(systime - basetime < (dtcal_now.get(Calendar.DAY_OF_YEAR) * TimeOps.MSECS_PER_DAY));
				org.junit.Assert.assertTrue(next1 - basetime >= 365L * TimeOps.MSECS_PER_DAY);
				org.junit.Assert.assertTrue(next1 - basetime <= 366L * TimeOps.MSECS_PER_DAY);
			}
		}
		org.junit.Assert.assertTrue(systime - basetime >= 0);
		// even if set() time is bang on the interval, the next interval should be returned
		org.junit.Assert.assertEquals(next1, next2);

		Calendar dtcal = TimeOps.getCalendar(next1,null);
		if (dtfld != 0) {
			if (dtfld == Calendar.DAY_OF_MONTH) {
				switch (dtcal_base.get(Calendar.MONTH)) {
				case 1: //Feb
					dtmax = (dtcal_base.get(Calendar.YEAR) % 4 == 0 ? 29 : 28);
					break;
				case 3: case 5: case 8: case 10:  //Sep, Apr, Jun, Nov
					dtmax = 30;
					break;
				default:
					dtmax = 31;
				}
			}
			int nextval = (dtcal_base.get(dtfld)+1) % (dtmax+1);
			if (nextval == 0 && !fromzero) nextval++;
			org.junit.Assert.assertEquals(dtcal.get(dtfld), nextval);
		}

		// exercise the non-Calendar variants of these 2 methods
		org.junit.Assert.assertEquals(basetime, ScheduledTime.getBaseTime(sched.frequency(), systime));
		org.junit.Assert.assertEquals(next1, ScheduledTime.getNextTime(sched.frequency(), basetime));
	}
}