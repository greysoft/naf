/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;

public class TimerNAF
{
	public interface Handler
	{
		public void timerIndication(TimerNAF tmr, Dispatcher d) throws java.io.IOException;
		default void eventError(TimerNAF tmr, Dispatcher d, Throwable ex) throws java.io.IOException {}
	}

	public interface TimeProvider
	{
		public long getSystemTime();
		public long getRealTime();
	}

	// dampens jitter - see reset() and nextExpiry() comments below
	static final long JITTER_THRESHOLD = SysProps.getTime("greynaf.timers.jitter", 10L); //deliberately package-private

	private Dispatcher dsptch;
	private int id;   //unique ID for every timer activation event (within each Dispatcher)
	private int type; //caller-specific ID to identify the purpose of this timer
	private long interval; //requested timer interval, in milliseconds
	private long expiry;  //absolute system time of expiry (milliseconds since epoch)
	private long activated;  // absolute system time at which this timer was set (milliseconds since epoch)
	private Handler handler;
	private Object attachment;

	public int getID() {return id;}
	public int getType() {return type;}
	public long age(TimeProvider tp) {return tp.getSystemTime() - activated;}
	public Object getAttachment() {return attachment;}
	public long getInterval() {return interval;}

	Handler getHandler() {return handler;}
	long getExpiryTime() {return expiry;}
	void resetExpiry() {expiry = dsptch.getSystemTime() + interval;}

	TimerNAF init(Dispatcher d, Handler h, long p_interval, int p_type, int p_id, Object attch)
	{
		dsptch = d;
		handler = h;
		interval = p_interval;
		type = p_type;
		id = p_id;
		activated = dsptch.getSystemTime();
		expiry = activated + interval;
		attachment = attch;
		return this;
	}

	TimerNAF clear()
	{
		dsptch = null;
		handler = null;
		attachment = null;
		return this;
	}

	void fire(Dispatcher d) throws java.io.IOException
	{
		handler.timerIndication(this, d);
	}

	public void reset()
	{
		if ((interval > JITTER_THRESHOLD) && (dsptch.getSystemTime() - activated < JITTER_THRESHOLD)) {
			// dampen excessive reset rates without affecting genuinely short intervals (especially zero-second timers!)
			return;
		}
		activated = dsptch.getSystemTime();
		dsptch.resetTimer(this);
	}

	public void reset(long new_interval)
	{
		if (new_interval != interval) {
			interval = new_interval;
			activated = 0;  // force the reset to go through, since we're changing the expiry interval
		}
		reset();
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
		if (next - systime < JITTER_THRESHOLD) next += interval;  //suspiciously small delay, advance to next interval
		return next;
	}

	@Override
	public String toString()
	{
		String txt = getClass().getName()+"-"+System.identityHashCode(this)+"/"+getID()+":"+getType()+"/"+getInterval();
		if (handler != null) txt += "/handler="+handler.getClass().getName();
		if (attachment != null) txt += "/attach="+attachment.getClass().getName();
		return txt;
	}
}
