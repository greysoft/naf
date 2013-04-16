/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class ScheduledTime
{
	public enum FREQ {NEVER, MINUTE, HOURLY, DAILY, MONTHLY, YEARLY, WEEKLY}

	public static final String TOKEN_DT = "%DT%";
	public static final String TOKEN_YEAR = "%Y%";
	public static final String TOKEN_MONTH = "%M%";
	public static final String TOKEN_MDAY = "%D%";
	public static final String TOKEN_HOUR = "%h%";
	public static final String TOKEN_MIN = "%m%";
	public static final String TOKEN_SEC = "%s%";

	private final FREQ schedfreq;
	private final java.util.Calendar dtcal;  // this does not hold required global state (ie. across calls) - merely pre-allocated for efficiency
	private final java.text.SimpleDateFormat dtformatter;  // likewise, pre-allocated merely for efficiency
	private long schedtime;  // the scheduled time, expressed in system milliseconds time

	public long get() {return schedtime;}
	public FREQ frequency() {return schedfreq;}
	public int compare(FREQ freq) {return (freq.ordinal() - schedfreq.ordinal());}
	public String getFormat() {return getFormat(schedfreq);}
	public java.util.TimeZone timezone() {return dtcal.getTimeZone();}

	public ScheduledTime(FREQ frq)
	{
		this(frq, null, null);
	}

	public ScheduledTime(FREQ frq, java.util.Calendar cal, java.text.SimpleDateFormat fmt)
	{
		schedfreq = frq;
		dtcal = (cal == null ? TimeOps.getCalendar(null) : cal);
		dtformatter = (fmt == null ? new java.text.SimpleDateFormat() : fmt);
		dtformatter.setCalendar(dtcal);
	}

	public long set(long systime)
	{
		if (systime == 0) systime = System.currentTimeMillis();
		schedtime = getNext(systime);
		return schedtime;
	}

	private long getNext(long systime)
	{
		dtcal.setTimeInMillis(systime);
		return getNextTime(schedfreq, dtcal);
	}

	public String embedTimestamp(long systime, String template)
	{
		dtcal.setTimeInMillis(systime);
		return embedTimestamp(schedfreq, dtcal, template, dtformatter);
	}

	public static String embedTimestamp(FREQ freq, java.util.Calendar dtcal, String template,
			java.text.SimpleDateFormat dtformatter)
	{
		if (dtcal == null) {
			dtcal = TimeOps.getCalendar(null);
			dtcal.setTimeInMillis(System.currentTimeMillis());
		}
		String dtfmt = "yyyyMMdd_HHmmss";
		long systime = dtcal.getTimeInMillis();
		long savetime = 0;

		if (freq != null && freq != FREQ.NEVER) {
			savetime = dtcal.getTimeInMillis();
			systime = getBaseTime(freq, dtcal);
			dtfmt = getFormat(freq);
		}

		if (template.indexOf(TOKEN_DT) != -1) {
			if (dtformatter == null) {
				dtformatter = new java.text.SimpleDateFormat(dtfmt);
			} else {
				dtformatter.applyPattern(dtfmt);
			}
			dtformatter.setCalendar(dtcal);
			java.util.Date dt = new java.util.Date(systime);
			template = template.replace(TOKEN_DT, dtformatter.format(dt));
		}
		template = template.replace(TOKEN_YEAR, Integer.toString(dtcal.get(java.util.Calendar.YEAR)));  //always 4 digits
		StringBuilder sb = new StringBuilder();
		StringOps.zeroPad(sb, dtcal.get(java.util.Calendar.MONTH) + 1, 2);
		template = template.replace(TOKEN_MONTH, sb);
		sb.setLength(0);
		StringOps.zeroPad(sb, dtcal.get(java.util.Calendar.DAY_OF_MONTH), 2);
		template = template.replace(TOKEN_MDAY, sb);
		sb.setLength(0);
		StringOps.zeroPad(sb, dtcal.get(java.util.Calendar.HOUR_OF_DAY), 2);
		template = template.replace(TOKEN_HOUR, sb);
		sb.setLength(0);
		StringOps.zeroPad(sb, dtcal.get(java.util.Calendar.MINUTE), 2);
		template = template.replace(TOKEN_MIN, sb);
		sb.setLength(0);
		StringOps.zeroPad(sb, dtcal.get(java.util.Calendar.SECOND), 2);
		template = template.replace(TOKEN_SEC, sb);

		if (savetime != 0) dtcal.setTimeInMillis(savetime);
		return template;
	}

	// Returns the start of the current interval, based on the given frequency
	// dtcal is expected to have been set to the reference time (typically now) beforehand, and will have been updated to the base
	// time on return.
	@SuppressWarnings("static-access")
	public static long getBaseTime(FREQ freq, java.util.Calendar dtcal)
	{
		dtcal.setLenient(true);
		dtcal.set(dtcal.MILLISECOND, 0);
		dtcal.set(dtcal.SECOND, 0);

		switch (freq)
		{
		case YEARLY:
			getBaseTime(FREQ.MONTHLY, dtcal);
			dtcal.set(dtcal.MONTH, 0);
			break;
		case MONTHLY:
			getBaseTime(FREQ.DAILY, dtcal);
			dtcal.set(dtcal.DAY_OF_MONTH, 1);
			break;
		case WEEKLY:
			int diff = dtcal.get(dtcal.DAY_OF_WEEK) - TimeOps.WDAY1;
			if (diff < 0) diff += 7;
			dtcal.set(dtcal.DAY_OF_MONTH, dtcal.get(dtcal.DAY_OF_MONTH) - diff);
			getBaseTime(FREQ.DAILY, dtcal);
			break;
		case DAILY:
			getBaseTime(FREQ.HOURLY, dtcal);
			dtcal.set(dtcal.HOUR_OF_DAY, 0);
			break;
		case HOURLY:
			dtcal.set(dtcal.MINUTE, 0);
			break;
		default:
			// already handled by universal settings above
			break;
		}
		return dtcal.getTimeInMillis();
	}

	public static long getBaseTime(FREQ freq, long systime)
	{
		java.util.Calendar dtcal = TimeOps.getCalendar(systime, null);
		return getBaseTime(freq, dtcal);
	}

	// Returns the start of the next interval, based on the given frequency
	@SuppressWarnings("static-access")
	public static long getNextTime(FREQ freq, java.util.Calendar dtcal)
	{
		long basetime = getBaseTime(freq, dtcal);
		dtcal.setTimeInMillis(basetime);

		switch (freq)
		{
		case YEARLY:
			dtcal.set(dtcal.YEAR, dtcal.get(dtcal.YEAR) + 1);
			break;
		case MONTHLY:
			dtcal.set(dtcal.MONTH, dtcal.get(dtcal.MONTH) + 1);
			break;
		case WEEKLY:
			dtcal.set(dtcal.DAY_OF_MONTH, dtcal.get(dtcal.DAY_OF_MONTH) + 7);
			break;
		case DAILY:
			dtcal.set(dtcal.DAY_OF_MONTH, dtcal.get(dtcal.DAY_OF_MONTH) + 1);
			break;
		case HOURLY:
			dtcal.set(dtcal.HOUR_OF_DAY, dtcal.get(dtcal.HOUR_OF_DAY) + 1);
			break;
		case MINUTE:
			dtcal.set(dtcal.MINUTE, dtcal.get(dtcal.MINUTE) + 1);
			break;
		default:
			throw new IllegalArgumentException("INTERNAL ERROR - Unrecognised FREQ="+freq);  //switch statement must be out of sync with current enum def
		}
		return dtcal.getTimeInMillis();
	}

	public static long getNextTime(FREQ freq, long systime)
	{
		java.util.Calendar dtcal = TimeOps.getCalendar(systime, null);
		return getNextTime(freq, dtcal);
	}

	// returns an appropriate SimpleDateFormat pattern for our frequency
	public static String getFormat(FREQ freq)
	{
		String dtfmt = null;
		switch (freq)
		{
		case YEARLY:
			dtfmt = "yyyy";
			break;
		case MONTHLY:
			dtfmt = "yyyyMM";
			break;
		case WEEKLY:
		case DAILY:
			dtfmt = "yyyyMMdd";
			break;
		case HOURLY:
			dtfmt = "yyyyMMdd_HH00";
			break;
		case MINUTE:
			dtfmt = "yyyyMMdd_HHmm";
			break;
		default:
			throw new IllegalArgumentException("INTERNAL ERROR - Unrecognised FREQ=" + freq);  // missing case label
		}
		return dtfmt;
	}
}
