/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public class Flusher
	implements TimerNAF.Handler
{
	private final java.util.ArrayList<java.io.Flushable> flushables = new java.util.ArrayList<java.io.Flushable>();
	private final Dispatcher dsptch;
	private final long interval; //needn't be aligned with com.grey.logging.Parameters.flush_interval
	private TimerNAF tmr;

	Flusher(Dispatcher d, long interval)
	{
		dsptch = d;
		this.interval = interval;
	}

	void shutdown()
	{
		if (tmr != null) {
			tmr.cancel();
			tmr = null;
		}
		flushAll();
	}

	public boolean register(Object flushable)
	{
		if (!(flushable instanceof java.io.Flushable)) return false;
		flushables.add(java.io.Flushable.class.cast(flushable));
		setTimer();
		return true;
	}

	public boolean unregister(Object flushable)
	{
		if (!flushables.remove(flushable)) return false;
		try {
			java.io.Flushable.class.cast(flushable).flush();
		} catch (Exception ex) {
			dsptch.getLogger().error("Failed to flush "+flushable+" on unregister - "+com.grey.base.ExceptionUtils.summary(ex));
		}
		return true;
	}

	// we only have one timer type, so we assume the 't' param corresponds to our 'tmr' field
	@Override
	public void timerIndication(TimerNAF t, Dispatcher d)
	{
		tmr = null;
		flushAll();
		if (flushables.size() != 0) setTimer();
	}

	public void flushAll()
	{
		java.util.ArrayList<java.io.Flushable> bad = null; //will rarely be called upon, so not wasteful to allocate temp list

		for (int idx = 0; idx != flushables.size(); idx++) {
			java.io.Flushable flushable = flushables.get(idx);
			try {
				flushable.flush();
			} catch (Exception ex) {
				dsptch.getLogger().error("Failed to flush "+flushable+" - "+com.grey.base.ExceptionUtils.summary(ex));
				if (bad == null) bad = new java.util.ArrayList<java.io.Flushable>();
				bad.add(flushable);
			}
		}

		if (bad != null) {
			for (int idx = 0; idx != bad.size(); idx++) {
				flushables.remove(bad.get(idx));
			}
		}
	}

	private void setTimer()
	{
		if (tmr != null || interval == 0) return;
		tmr = dsptch.setTimer(interval, 1, this);
	}

	//error already logged by Dispatcher so nothing more to do
	@Override
	public void eventError(TimerNAF t, Dispatcher d, Throwable ex) {}
}
