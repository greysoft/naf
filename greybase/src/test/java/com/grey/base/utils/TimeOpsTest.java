/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.util.Calendar;

public class TimeOpsTest
{
	@org.junit.Test
	public void testFormats()
	{
		final java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Europe/London");
		final Calendar dtcal = TimeOps.getCalendar(tz.getID());
		dtcal.setLenient(true);
		dtcal.set(Calendar.MILLISECOND, 1);
		dtcal.set(Calendar.SECOND, 2);
		dtcal.set(Calendar.MINUTE, 3);
		dtcal.set(Calendar.HOUR_OF_DAY, 14);
		dtcal.set(Calendar.DAY_OF_MONTH, 5);
		dtcal.set(Calendar.MONTH, 6);
		dtcal.set(Calendar.YEAR, 2009);
		long systime = dtcal.getTimeInMillis();
		System.out.println("Effective timezone1: "+TimeOps.displayTimezone(dtcal)+" - "+tz.getID());

		StringBuilder sb = TimeOps.makeTimeISO8601(systime, tz.getID(), null, true, true, false);
		org.junit.Assert.assertEquals("20090705T140302", sb.toString());
		sb = TimeOps.makeTimeISO8601(systime, tz.getID(), null, true, true, true);
		org.junit.Assert.assertEquals("20090705T140302+01", sb.toString());
		sb = TimeOps.makeTimeISO8601(systime, tz.getID(), null, true, false, false);
		org.junit.Assert.assertEquals("2009-07-05T14:03:02", sb.toString());
		sb = TimeOps.makeTimeISO8601(systime, tz.getID(), null, false, true, false);
		org.junit.Assert.assertEquals("20090705", sb.toString());
		sb = TimeOps.makeTimeISO8601(systime, tz.getID(), null, false, false, false);
		org.junit.Assert.assertEquals("2009-07-05", sb.toString());

		sb = TimeOps.makeTimeLogger(systime, tz.getID(), null, true, true);
		org.junit.Assert.assertEquals("2009-07-05 14:03:02.001", sb.toString());
		sb = TimeOps.makeTimeLogger(systime, tz.getID(), null, true, false);
		org.junit.Assert.assertEquals("2009-07-05 14:03:02", sb.toString());
		sb = TimeOps.makeTimeLogger(systime, tz.getID(), null, false, true);
		org.junit.Assert.assertEquals("14:03:02.001", sb.toString());
		sb = TimeOps.makeTimeLogger(systime, tz.getID(), null, false, false);
		org.junit.Assert.assertEquals("14:03:02", sb.toString());

		sb = TimeOps.makeTimeRFC822(systime, tz.getID(), null);
		org.junit.Assert.assertEquals("Sun, 05 Jul 2009 14:03:02 +0100", sb.toString());

		long excess = (dtcal.get(Calendar.SECOND)*TimeOps.MSECS_PER_SECOND) + dtcal.get(Calendar.MILLISECOND);
		long systime2 = TimeOps.getSystime(dtcal, dtcal.get(Calendar.YEAR), dtcal.get(Calendar.MONTH), dtcal.get(Calendar.DAY_OF_MONTH),
				dtcal.get(Calendar.HOUR_OF_DAY), dtcal.get(Calendar.MINUTE));
		org.junit.Assert.assertEquals(systime, systime2+excess);

		// non-summertime
		dtcal.set(Calendar.MONTH, 10);
		dtcal.set(Calendar.SECOND, 2);
		systime = dtcal.getTimeInMillis();
		System.out.println("Effective timezone2: "+TimeOps.displayTimezone(dtcal));
		sb = TimeOps.makeTimeISO8601(systime, tz.getID(), new StringBuilder(), true, true, true);
		org.junit.Assert.assertEquals("20091105T140302Z", sb.toString());

		// behind GMT
		java.util.TimeZone tz2 = java.util.TimeZone.getTimeZone("America/Montreal");
		Calendar dtcal2 = TimeOps.getCalendar(tz2.getID());
		dtcal2.setLenient(true);
		dtcal2.set(Calendar.MILLISECOND, 1);
		dtcal2.set(Calendar.SECOND, 2);
		dtcal2.set(Calendar.MINUTE, 3);
		dtcal2.set(Calendar.HOUR_OF_DAY, 14);
		dtcal2.set(Calendar.DAY_OF_MONTH, 5);
		dtcal2.set(Calendar.MONTH, 10);
		dtcal2.set(Calendar.YEAR, 2009);
		systime = dtcal2.getTimeInMillis();
		System.out.println("Effective timezone3: "+TimeOps.displayTimezone(dtcal2)+" - "+tz2.getID());
		sb = TimeOps.makeTimeISO8601(systime, tz2.getID(), new StringBuilder(), true, true, true);
		org.junit.Assert.assertEquals("20091105T140302-05", sb.toString());

		//verify that with-calendar variants match the without-calendar ones
		String t1 = TimeOps.makeTimeISO8601(systime, null, new StringBuilder(), true, true, true).toString();
		String t2 = TimeOps.makeTimeISO8601(systime, new StringBuilder(), true, true, true).toString();
		org.junit.Assert.assertEquals(t1, t2);
		t1 = TimeOps.makeTimeRFC822(systime, null, new StringBuilder()).toString();
		t2 = TimeOps.makeTimeRFC822(systime, new StringBuilder()).toString();
		org.junit.Assert.assertEquals(t1, t2);
		t1 = TimeOps.makeTimeLogger(systime, null, new StringBuilder(), true, true).toString();
		t2 = TimeOps.makeTimeLogger(systime, new StringBuilder(), true, true).toString();
		org.junit.Assert.assertEquals(t1, t2);
		long systime3 = TimeOps.getSystime(TimeOps.getCalendar(null), 2001, 2, 3, 4, 5);
		long systime4 = TimeOps.getSystime(null, 2001, 2, 3, 4, 5);
		org.junit.Assert.assertEquals(systime3, systime4);
	}

	@org.junit.Test
	public void testParse()
	{
		org.junit.Assert.assertEquals(0, TimeOps.parseMilliTime(""));
		verifyParse("0ms", 0);
		verifyParse("12ms", 12);
		org.junit.Assert.assertEquals(TimeOps.parseMilliTime("12"), TimeOps.parseMilliTime("12ms"));
		verifyParse("2d", TimeOps.MSECS_PER_DAY * 2L);
		verifyParse("2h", TimeOps.MSECS_PER_HOUR * 2L);
		verifyParse("2m", TimeOps.MSECS_PER_MINUTE * 2L);
		org.junit.Assert.assertEquals(TimeOps.MSECS_PER_SECOND * 121L, TimeOps.parseMilliTime("121s"));
		long msecs = (TimeOps.MSECS_PER_DAY * 2L) + (TimeOps.MSECS_PER_HOUR * 3L) + (TimeOps.MSECS_PER_MINUTE * 10L)
				+ (TimeOps.MSECS_PER_SECOND * 59) + 12L;
		verifyParse("2d3h10m59s12ms", msecs);
		org.junit.Assert.assertEquals(TimeOps.parseMilliTime("2d3h10m59s12"), TimeOps.parseMilliTime("2d3h10m59s12ms"));
		org.junit.Assert.assertEquals(23L, TimeOps.parseMilliTime("dhm23"));
		org.junit.Assert.assertEquals(1001L, TimeOps.parseMilliTime("dhs1001"));
		try {
			msecs = TimeOps.parseMilliTime("1x");
			org.junit.Assert.fail("Failed to detect bad time units - "+msecs);
		} catch (NumberFormatException ex) {}
		try {
			msecs = TimeOps.parseMilliTime("1h2d");
			org.junit.Assert.fail("Failed to detect out-of-sequence time units - "+msecs);
		} catch (NumberFormatException ex) {}
		try {
			msecs = TimeOps.parseMilliTime("1h2h");
			org.junit.Assert.fail("Failed to detect duplicate time units - "+msecs);
		} catch (NumberFormatException ex) {}
		try {
			msecs = TimeOps.parseMilliTime("9ms8");
			org.junit.Assert.fail("Failed to detect duplicate implicit ms - "+msecs);
		} catch (NumberFormatException ex) {}
	}

	@org.junit.Test
	public void testExpand()
	{
		long msecs = (TimeOps.MSECS_PER_DAY * 2L) + (TimeOps.MSECS_PER_HOUR * 3L) + (TimeOps.MSECS_PER_MINUTE * 10L)
				+ (TimeOps.MSECS_PER_SECOND * 59) + 12L;
		StringBuilder sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("2d3h10m59s12ms", sb.toString());
		msecs = (TimeOps.MSECS_PER_DAY * 2L);
		sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("2d", sb.toString());
		msecs = (TimeOps.MSECS_PER_HOUR * 2L);
		sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("2h", sb.toString());
		msecs = (TimeOps.MSECS_PER_MINUTE * 2L);
		sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("2m", sb.toString());
		msecs = (TimeOps.MSECS_PER_SECOND * 2L);
		sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("2s", sb.toString());
		msecs = (TimeOps.MSECS_PER_DAY * 2L) + (TimeOps.MSECS_PER_SECOND * 59);
		sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("2d59s", sb.toString());
		msecs = TimeOps.MSECS_PER_SECOND - 1L;
		sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("999ms", sb.toString());
		msecs = TimeOps.MSECS_PER_SECOND;
		sb = TimeOps.expandMilliTime(msecs);
		org.junit.Assert.assertEquals("1s", sb.toString());

		msecs = TimeOps.MSECS_PER_SECOND + 1L;
		sb = TimeOps.expandMilliTime(msecs, null, true);
		String exp = "1s1ms";
		org.junit.Assert.assertEquals(exp, sb.toString());
		msecs = TimeOps.MSECS_PER_DAY * 31L;
		StringBuilder sb2 = TimeOps.expandMilliTime(msecs, sb, false);
		org.junit.Assert.assertSame(sb, sb2);
		org.junit.Assert.assertEquals(exp+"31d", sb.toString());
		msecs = TimeOps.MSECS_PER_MINUTE * 59L;
		sb2 = TimeOps.expandMilliTime(msecs, sb, true);
		org.junit.Assert.assertSame(sb, sb2);
		org.junit.Assert.assertEquals("59m", sb.toString());

		sb = TimeOps.expandMilliTime(-1003);
		org.junit.Assert.assertEquals("minus-1s3ms", sb.toString());
	}

	@org.junit.Test
	public void testZeroPad()
	{
		// the min time which requires 13 digits to represent it (13 digits being enough for all Java times since that instant in 2001)
		StringBuilder sb = new StringBuilder();
		for (int loop = 0; loop != 12; loop++) sb.append('9');
		long threshold_systime = Long.valueOf(sb.toString()).longValue() + 1;

		long systime = 0;
		sb.setLength(0);
		TimeOps.zeroPad(systime, sb);
		int len1 = sb.length();
		org.junit.Assert.assertEquals(systime, Long.valueOf(sb.toString()).longValue());

		long[] times = new long[]{1, System.currentTimeMillis(), threshold_systime, threshold_systime-1, threshold_systime+1};
		for (int idx = 0; idx != times.length; idx++) {
			systime = times[idx];
			sb.setLength(0);
			TimeOps.zeroPad(systime, sb);
			org.junit.Assert.assertEquals(systime, Long.valueOf(sb.toString()).longValue());
			org.junit.Assert.assertEquals(len1, sb.length());
		}

		systime = 42342;
		sb.setLength(0);
		sb.append("any text");
		int len = sb.length();
		TimeOps.zeroPad(systime, sb);
		org.junit.Assert.assertEquals(len1+len, sb.length());
		org.junit.Assert.assertEquals(systime, Long.valueOf(sb.subSequence(len, sb.length()).toString()).longValue());
	}

	private static void verifyParse(String str, long exptime)
	{
		org.junit.Assert.assertEquals(exptime, TimeOps.parseMilliTime(str));
		org.junit.Assert.assertEquals(str, TimeOps.expandMilliTime(exptime).toString());
	}
}
