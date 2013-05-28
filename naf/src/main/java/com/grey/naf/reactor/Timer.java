/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;

public final class Timer
{
	public interface Handler
	{
		public void timerIndication(Timer tmr, Dispatcher d) throws com.grey.base.FaultException, java.io.IOException;
		public void eventError(Timer tmr, Dispatcher d, Throwable ex) throws com.grey.base.FaultException, java.io.IOException;
	}

	// dampens jitter - see reset() and nextExpiry() comments below
	static final long JITTER_INTERVAL = SysProps.getTime("greynaf.timers.jitter", 20L); //deliberately package-private

	public int id;	// unique ID for every timer activation event (within each Dispatcher)
	public int type;  //caller-specific ID to identify the purpose of this timer
	public long interval;	// requested timer interval, in milliseconds
	public long expiry;  // absolute system time of expiry (milliseconds since epoch)
	public Handler handler;
	public Object attachment;

	private Dispatcher dsptch;
	private long activated;  // absolute system time at which this timer was set (milliseconds since epoch)

	public Timer init(Dispatcher d, Handler h, long interval, int type, int id)
	{
		dsptch = d;
		handler = h;
		this.interval = interval;
		this.type = type;
		this.id = id;
		activated = dsptch.systime();
		expiry = activated + interval;
		return this;
	}

	public Timer clear()
	{
		handler = null;
		attachment = null;
		return this;
	}

	public void fire(Dispatcher d) throws com.grey.base.FaultException, java.io.IOException
	{
		handler.timerIndication(this, d);
	}

	public long reset()
	{
		if ((interval > JITTER_INTERVAL) && (dsptch.systime() - activated < JITTER_INTERVAL)) {
			// dampen excessive reset rates without affecting genuinely short intervals (especially zero-second timers!)
			return dsptch.systime() - expiry;
		}
		activated = dsptch.systime();
		return dsptch.resetTimer(this);
	}

	public long reset(long new_interval)
	{
		if (new_interval != interval) {
			interval = new_interval;
			activated = 0;  // force the reset to go through, since we're changing the expiry interval
		}
		return reset();
	}

	public void cancel()
	{
		dsptch.cancelTimer(this);
	}

	public static void sleep(long msecs)
	{
		try {Thread.sleep(msecs);} catch (InterruptedException ex) {} 
	}

	// This calculates the next time at which a recurring timer should go off, based on the current time and the timer interval,
	// but rather than simplistically adding the given interval (which the calling code could easily have done for itself),
	// this method facilitates those users who want the timer to go off at rounded interval times.
	// Eg. if the interval is one hour, then such a timer is expected to go off on the hour, so if we are currently halfway through
	// the hour, then we want it to go off in 30 minutes rather than 60, and only then would we start firing every 60 minutes.
	// The 'systime' parameter is the current time, and if we are calling this method to reset a recurring timer, that typically
	// represents when the timer last went off.
	//
	// Analytically, the trigger time could of course simply be calculated as ((systime % interval) + interval), but due to jitter
	// in system clocks, a timer will often fire milliseconds before it was due, and the above formula would then result in it being
	// rescheduled again before that due time, and again, and again, potentially hundreds or thousands of times within those few
	// milliseconds.
	// This "jitter" is due to the fact the accuracy of our timers is linked to the process scheduling resolution of the OS, which can
	// be as coarse as 10ms, with threads being woken within that interval on either side of the correct time.
	// Therefore this method makes a heuristic adjusment. We assume that if the interval to the next firing time is too short, we
	// must just have fallen victim to the jitter phenomenon described, and we therefore advance to the next interval.
	//
	// JITTER_INTERVAL effectively represents a lower limit on the precision of NAF timers.
	public static long nextExpiry(long interval, long systime)
	{
		long next = systime - (systime % interval) + interval;
		if (next - systime < JITTER_INTERVAL) next += interval;  //suspiciously small delay, advance to next interval
		return next;
	}

	@Override
	public String toString()
	{
		String txt = id+":"+type+"/"+interval;
		if (handler != null) txt += "/"+handler.getClass().getName();
		if (attachment != null) txt += "/"+attachment.getClass().getName();
		return txt;
	}
}
