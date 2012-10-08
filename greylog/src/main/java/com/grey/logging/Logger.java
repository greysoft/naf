/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.base.config.SysProps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.ScheduledTime;

/**
 * Base class for a range of loggers offering basic log() interfaces, optimised for zero garbagae generation.
 * <br/>
 * The subclasses are meant to be accessed via this type, and this class provides configurable logfile rotation.
 * <p>
 * This base class is MT-safe with respect to its public methods, and MT-safe concrete classes would need to synchronise access to
 * their log(CharSequence msg) and flush() methods.
 * <br/>
 * This class makes some inexpensive provision for multi-threading, but the overridden log() and flush() methods need to be synchronized
 * to make it fully MT-safe.
 */
abstract public class Logger
	implements java.io.Closeable, java.io.Flushable
{
	public enum LEVEL {OFF, ERR, WARN, INFO, TRC, TRC2, TRC3, TRC4, TRC5, ALL}

	public static final String SYSPROP_DIAG = "grey.logger.diagnostics";
	private static final boolean diagtrace = SysProps.get(SYSPROP_DIAG, false);
	private static final boolean TIDplusName = SysProps.get(Parameters.SYSPROP_TIDPLUSNAME, true);
	private static final java.util.HashSet<Logger> loggers = new java.util.HashSet<Logger>();
	private static Thread shutdown_hook;

	private final boolean isMT;
	private final String name;
	private final String this_string;
	private final String pthnam_tmpl;  	// the logfile pathname template supplied by Logger user - possibly having been normalised by this class
	private final java.io.OutputStream strm_base;
	private final ScheduledTime rotsched;
	private final int maxsize;
	private final boolean withTID;
	private final boolean withDelta;
	private final boolean withMillisecs;
	private final boolean withLevel;
	private final boolean withInitMark;
	private final long flush_interval;  // interval between logfile flushes, in milliseconds
	protected final int bufsiz;
	private final java.util.Calendar dtcal = TimeOps.getCalendar(null); //merely pre-allocated for efficiency

	private java.io.File fh_active;
	private LEVEL maxLevel;
	private long prevtime;
	private boolean isOwner;
	private long last_flushtime;

	// This is to avoid an MT logger having to override and synchronise getLevel(), but any other MT protection
	// has to be provided by an MT logger class
	private volatile LEVEL maxLevel_MT;

	abstract public void log(LEVEL lvl, CharSequence msg);

	// most subclasses would override these
	protected void openStream(java.io.OutputStream strm) throws java.io.IOException {};
	protected void openStream(String pthnam) throws java.io.IOException {};
	protected void closeStream(boolean isOwner) throws java.io.IOException {};
	@Override
	public void flush() throws java.io.IOException {}

	public final boolean isActive(LEVEL lvl) {return  Interop.isActive(getLevel(), lvl);}
	public final String getName() {return name;}
	public final String getPathTemplate() {return pthnam_tmpl;}
	public final synchronized String getActivePath() {return fh_active == null ? null : fh_active.getAbsolutePath();}
	@Override
	public String toString() {return this_string;}

	protected Logger(Parameters params, String logname, boolean is_mt)
	{
		name = logname;
		isMT = is_mt;
		pthnam_tmpl = params.pthnam;
		strm_base = params.strm;
		maxsize = params.maxsize;
		rotsched = (params.rotfreq == ScheduledTime.FREQ.NEVER ? null : new ScheduledTime(params.rotfreq, dtcal, null));

		boolean with_tid = (isMT ? true : params.withTID);
		boolean with_milli = true;
		boolean with_level = true;
		boolean with_initmark = true;

		if (params.mode != null) {
			if (params.mode.equals(Parameters.MODE_AUDIT)) {
				with_tid = false;
				with_milli = false;
				with_level = false;
				with_initmark = false;
			} else {
				System.out.println("GreyLogger: Logger="+name+" ignoring unrecognised Mode="+params.mode);
			}
		}
		withTID = with_tid;
		withMillisecs = with_milli;
		withLevel = with_level;
		withInitMark = with_initmark;
		withDelta = (withMillisecs ? params.withDelta : false);
		bufsiz = params.bufsiz;
		flush_interval = params.flush_interval;

		set_level(params.loglevel);

		String desc = (name == null ? "" : "Name="+name+" ");
		desc += params.toString();
		if (isMT) desc += " MT";
		this_string = desc;
	}

	// This has to be called after the constructor, else this logger won't be fully operartional.
	// We can't call this within the constructor above, as it would calls back up to not-yet-constructed subclasses.
	// This is all handled properly by the factory methods, which is why all the Logger constructors have 'protected'
	// access qualifiers, to prevent direct invocation.
	protected void init() throws java.io.IOException
	{
		open(System.currentTimeMillis(), null);
	}

	public final LEVEL getLevel()
	{
		if (isMT) return maxLevel_MT;
		return maxLevel;
	}

	// even if we're not in MT mode, this is a rarely called method, so we can easily afford the cost of synchronising
	public final LEVEL setLevel(LEVEL newlvl)
	{
		LEVEL oldlvl;
		synchronized (this) {
			oldlvl = getLevel();
			if (newlvl == oldlvl) return oldlvl;
			set_level(newlvl);
		}
		String action = (newlvl.ordinal() < oldlvl.ordinal()) ? "Reduced" : "Increased";
		log(LEVEL.ALL, action+" log level from " + oldlvl.toString() + " to " + newlvl.toString());
		return oldlvl;
	}

	private void set_level(LEVEL newlvl)
	{
		if (isMT) {
			maxLevel_MT = newlvl;
		} else {
			maxLevel = newlvl;
		}
	}

	// even if we're not in MT mode, this is a rarely called method, so we can easily afford the cost of synchronising
	synchronized private void open(long systime, String nextpath) throws java.io.IOException
	{
		close(true);

		if (pthnam_tmpl == null) {
			openStream(strm_base);
		} else {
			String path = pthnam_tmpl;
			if (nextpath != null) {
				path = nextpath;
			} else if (rotsched != null) {
				rotsched.set(systime);
				path = rotsched.embedTimestamp(systime, pthnam_tmpl);
			} else if (maxsize != 0) {
				path = ScheduledTime.embedTimestamp(null, dtcal, pthnam_tmpl, null);
			}
			fh_active = new java.io.File(path);
			java.io.File dirh = fh_active.getParentFile();
			if (dirh != null) FileOps.ensureDirExists(dirh);  //beware this can be null, if filename specified without slashes
			openStream(path);
			isOwner = true;
		}
		prevtime = systime;

		// It turns out that the shutdown hook can be invoked fractionally before all threads have exited (under JUnit anyway),
		// so we no longer close these loggers here as the other threads might still be logging their shutdown.
		// This process is exiting anyway when this is run, so closing the loggers was just an empty courtesy.
		// Instead, we just display then.
		synchronized (loggers) {
			if (shutdown_hook == null) {
				shutdown_hook = new Thread() {
					@Override
					public void run() {
						if (!diagtrace) return;
						Logger[] openlogs = loggers.toArray(new Logger[loggers.size()]);
						System.out.println("GreyLogger: Shutdown Thread=T"+Thread.currentThread().getId()+" - Open loggers="+openlogs.length);
						for (int idx = 0; idx != openlogs.length; idx++) {
							Logger logger = openlogs[idx];
							System.out.println("- GreyLogger: Logger #"+(idx+1)+"/"+openlogs.length+": IsOwner="+logger.isOwner+" - "+logger);
							logger.close();
						}
					}
				};
				Runtime.getRuntime().addShutdownHook(shutdown_hook);
			}
			loggers.add(this);
		}
		if (!withInitMark) return;

		java.lang.management.RuntimeMXBean rt = java.lang.management.ManagementFactory.getRuntimeMXBean();
		log(LEVEL.ALL, "INITMARK:"
				+com.grey.base.config.SysProps.EOL+"\t"
				+this
				+com.grey.base.config.SysProps.EOL+"\t"
				+"Opened "+(fh_active==null ? "stream" : getActivePath())
				+" with level="+getLevel()+" at "+new java.util.Date(systime)
				+com.grey.base.config.SysProps.EOL+"\t"
				+"Thread="+rt.getName()+":"+Thread.currentThread().getName()+":"+Thread.currentThread().getId()
				+", Running since " + new java.util.Date(rt.getStartTime()));
		flush();
	}

	@Override
	public void close()
	{
		close(false);
	}

	// even if we're not in MT mode, this is a rarely called method, so we can easily afford the cost of synchronising
	synchronized private void close(boolean rollover)
	{
		try {
			flush();  //bizzarely, close() doesn't flush in all circumstances
			closeStream(isOwner);
		} catch (Exception ex) {
	        System.out.println(new java.util.Date(System.currentTimeMillis())+" Logger failed to close logfile - "+this_string+" - "
	        		+com.grey.base.GreyException.summary(ex, false));
		}
		isOwner = false;

		if (!rollover) {
			loggers.remove(this);
			Factory.removeMappedLogger(this);
		}
	}

	public final void log(LEVEL lvl, Throwable ex, boolean dumpStack, CharSequence msg)
	{
		if (!isActive(lvl)) return;
		if (ex == null) {log(lvl, msg); return;}
		if (ex instanceof java.lang.NullPointerException) dumpStack = true;
		String exmsg = "EXCEPTION: "+msg+" - "+com.grey.base.GreyException.summary(ex, dumpStack);
		log(lvl, exmsg);
	}

	// Prepare a new logfile entry. This consists of managing logfile rotation and construct the standard prefix portion of the new
	// message.
	// SimpleDateFormat turns out to be a performance pig, time-wise and memory-wise, and String.format() turned out to be even worse.
	// The date-time formatting code below has been shown to consume zero memory (even the String.valueOf(LEVEL) costs nothing).
	// One possible optimisation is to pre-build the portion up to the Minute and just rebuild that once an hour (can record prev hour and compare to
	// dtcal.get(DAY)), but the calls to zeropad() turn out to have no measurable time cost, so they're probably at least as cheap as the arithmetic
	// to compare change-of-hour (which would also have to confirm whether the day has changed as well, to guarantee we don't miss the hour change).
	protected final StringBuilder setLogEntry(LEVEL lvl, StringBuilder pfxbuf) throws java.io.IOException
	{
		long systime = System.currentTimeMillis();
		if (withMillisecs || systime - dtcal.getTimeInMillis() > 500) dtcal.setTimeInMillis(systime);
		boolean withdate = (rotsched == null || rotsched.compare(ScheduledTime.FREQ.DAILY) < 0);

		pfxbuf.setLength(0);
		TimeOps.makeTimeLogger(dtcal, pfxbuf, withdate, withMillisecs);

		if (withDelta) {
			com.grey.base.utils.StringOps.zeroPad(pfxbuf.append('+'), (int)(systime - prevtime), 3);
			prevtime = systime;
		}
		pfxbuf.append(' ');
		char intro = '[';

		if (withLevel && lvl != LEVEL.ALL) {
			pfxbuf.append(intro).append(lvl);
			intro = '-';
		}

		if (withTID) {
			Thread thrd = Thread.currentThread();
			pfxbuf.append(intro).append('T').append(thrd.getId());
			intro = 0;
			if (TIDplusName) {
				String name = thrd.getName();
				if (name != null && name.length() != 0) pfxbuf.append('-').append(name);
			}
		}
		if (intro != '[') pfxbuf.append("] ");

		if (rotsched != null && systime >= rotsched.get()) {
			// this message will be written to a newlu rotated logfile
			open(systime, null);
		} else if (maxsize != 0 && fh_active.length() >= maxsize) {
			// rotate if file exceeds max size and we are on next naming interval
			String nextpath = ScheduledTime.embedTimestamp(null, dtcal, pthnam_tmpl, null);
			if (!nextpath.equals(getActivePath())) open(systime, nextpath);
		} else if (flush_interval != 0) {
			if (systime - last_flushtime >= flush_interval) {
				flush();
				last_flushtime = systime;
			}
		}
		return pfxbuf;
	}

	// Convenenience methods to ease the transition from SLF4J to this logger
	public final void error(CharSequence msg) {log(LEVEL.ERR, msg);}
	public final void warn(CharSequence msg) {log(LEVEL.WARN, msg);}
	public final void info(CharSequence msg) {log(LEVEL.INFO, msg);}
	public final void trace(CharSequence msg) {log(LEVEL.TRC, msg);}
}