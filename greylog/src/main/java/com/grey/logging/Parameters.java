/*
 * Copyright 2011-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import java.lang.management.ManagementFactory;
import java.time.Clock;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.ByteOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.ScheduledTime;

public class Parameters
{
	public static final String SYSPROP_LOGCLASS = "grey.logger.class";
	public static final String SYSPROP_LOGLEVEL = "grey.logger.level";
	public static final String SYSPROP_LOGSDIR = "grey.logger.dir";
	public static final String SYSPROP_LOGFILE = "grey.logger.file";
	public static final String SYSPROP_FORCE_STDOUT = "grey.logger.stdout";
	public static final String SYSPROP_ROTFREQ = "grey.logger.rotfreq";
	public static final String SYSPROP_MAXSIZ = "grey.logger.maxsiz";
	public static final String SYSPROP_BUFSIZ = "grey.logger.bufsiz";
	public static final String SYSPROP_FLUSHINTERVAL = "grey.logger.flushinterval";
	public static final String SYSPROP_SHOWPID = "grey.logger.pid";
	public static final String SYSPROP_SHOWTID = "grey.logger.tid";
	public static final String SYSPROP_SHOWTHRDNAME = "grey.logger.threadname";
	public static final String SYSPROP_SHOWDELTA = "grey.logger.delta";

	public static final int CURRENT_PID = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);

	public static final String TOKEN_LOGSDIR = "%DIRLOG%";
	public static final String TOKEN_TID = "%TID%";
	public static final String TOKEN_PID = "%PID%";

	private static final Class<?> DFLTCLASS = MTLatinLogger.class;  //safe option for naive/unaware apps
	private static final java.io.OutputStream DFLT_STRM = System.out;
	private static final String PTHNAM_STDOUT = "%stdout%";
	private static final String PTHNAM_STDERR = "%stderr%";

	private final String logClass;
	private final Logger.LEVEL logLevel;
	private final String pthnam;	// pathname template for logfile
	private final java.io.OutputStream strm;
	private final ScheduledTime.FREQ rotfreq;
	private final int maxSize;
	private final int bufSize;
	private final long flushInterval;
	private final Clock clock;
	private final boolean withPID;
	private final boolean withTID;
	private final boolean withThreadName;
	private final boolean withDelta;
	private final boolean quietMode;

	private Parameters(Builder bldr) {
		logClass = bldr.logClass;
		logLevel = bldr.logLevel;
		pthnam = bldr.pthnam;
		strm = bldr.strm;
		rotfreq = bldr.rotFreq;
		maxSize = bldr.maxSize;
		bufSize = bldr.bufSize;
		flushInterval = bldr.flushInterval;
		clock = bldr.clock;
		withPID = bldr.withPID;
		withTID = bldr.withTID;
		withThreadName = bldr.withThreadName;
		withDelta = bldr.withDelta;
		quietMode = bldr.quietMode;

	}

	public Parameters(XmlConfig cfg) {
		this(Builder.fromConfig(cfg));
	}

	public String getLogClass() {
		return logClass;
	}

	public Logger.LEVEL getLogLevel() {
		return logLevel;
	}

	public String getPathname() {
		return pthnam;
	}

	public java.io.OutputStream getStream() {
		return strm;
	}

	public ScheduledTime.FREQ getRotFreq() {
		return rotfreq;
	}

	public int getMaxSize() {
		return maxSize;
	}

	public int getBufSize() {
		return bufSize;
	}

	public long getFlushInterval() {
		return flushInterval;
	}

	public Clock getClock() {
		return clock;
	}

	public boolean withPID() {
		return withPID;
	}

	public boolean withTID() {
		return withTID;
	}

	public boolean withThreadName() {
		return withThreadName;
	}

	public boolean withDelta() {
		return withDelta;
	}

	public boolean isQuietMode() {
		return quietMode;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(super.toString());
		sb.append(getClass().getName());
		sb.append("[Level=").append(logLevel);
		sb.append(", Dest=");
		if ( getPathname() != null) {
			sb.append( getPathname());
		} else if (getStream() == System.out) {
			sb.append("stdout");
		} else if (getStream() == System.err) {
			sb.append("stderr");
		} else if (getStream() == null) {
			sb.append("SINK");
		} else {
			sb.append(getStream());
		}
		sb.append(" Type=").append(getLogClass()).append('/').append(getLogLevel());

		if (getRotFreq() != ScheduledTime.FREQ.NEVER || getMaxSize() != 0) {
			sb.append(" Rot=");
			if (getMaxSize() == 0) {
				sb.append(getRotFreq());
			} else {
				ByteOps.expandByteSize(getMaxSize(), sb, false);
			}
		}
		if (getBufSize() != 0) {
			sb.append(" Buffer=");
			ByteOps.expandByteSize(getBufSize(), sb, false);
			sb.append('/');
			TimeOps.expandMilliTime(getFlushInterval(), sb, false);
		}
		sb.append("]");
		return sb.toString();
	}


	public static class Builder {
		private String logClass = SysProps.get(SYSPROP_LOGCLASS, DFLTCLASS.getName());
		private Logger.LEVEL logLevel = Logger.LEVEL.valueOf(SysProps.get(SYSPROP_LOGLEVEL, Logger.LEVEL.INFO.name()).toUpperCase());
		private String pthnam = SysProps.get(SYSPROP_LOGFILE);
		private java.io.OutputStream strm = DFLT_STRM;
		private Clock clock = Clock.systemUTC();
		private ScheduledTime.FREQ rotFreq = ScheduledTime.FREQ.valueOf(SysProps.get(SYSPROP_ROTFREQ, ScheduledTime.FREQ.NEVER.name()).toUpperCase());
		private int maxSize = SysProps.get(SYSPROP_MAXSIZ, 0);
		private int bufSize = SysProps.get(SYSPROP_BUFSIZ, 8 * 1024);
		private long flushInterval = SysProps.get(SYSPROP_FLUSHINTERVAL, 1_000);
		private boolean withPID = SysProps.get(SYSPROP_SHOWPID, false);
		private boolean withTID = SysProps.get(SYSPROP_SHOWTID, true);
		private boolean withThreadName = SysProps.get(SYSPROP_SHOWTHRDNAME, false);
		private boolean withDelta = SysProps.get(SYSPROP_SHOWDELTA, false);
		public boolean quietMode;

		public Builder() {}

		public Builder(Parameters params) {
			logClass = params.getLogClass();
			logLevel = params.getLogLevel();
			pthnam = params.getPathname();
			strm = params.getStream();
			clock = params.clock;
			rotFreq = params.getRotFreq();
			maxSize = params.getMaxSize();
			bufSize = params.getBufSize();
			flushInterval = params.getFlushInterval();
			withPID = params.withPID();
			withTID = params.withTID();
			withThreadName = params.withThreadName();
			withDelta = params.withDelta();
			quietMode = params.isQuietMode();
		}

		public Builder withLogClass(String v) {
			logClass = v;
			return this;
		}

		public Builder withLogClass(Class<?> v) {
			return withLogClass(v.getName());
		}

		public Builder withLogLevel(Logger.LEVEL v) {
			logLevel = v;
			return this;
		}

		public Builder withPathname(String v) {
			pthnam = v;
			return this;
		}

		public Builder withStream(java.io.OutputStream v) {
			strm = v;
			return this;
		}

		public Builder withClock(Clock v) {
			clock = v;
			return this;
		}

		public Builder withRotFreq(ScheduledTime.FREQ v) {
			rotFreq = v;
			return this;
		}

		public Builder withMaxSize(int v) {
			maxSize = v;
			return this;
		}

		public Builder withBufferSize(int v) {
			bufSize = v;
			return this;
		}

		public Builder withFlushInterval(long v) {
			flushInterval = v;
			return this;
		}

		public Builder withPID(boolean v) {
			withPID = v;
			return this;
		}

		public Builder withTID(boolean v) {
			withTID = v;
			return this;
		}

		public Builder withThreadName(boolean v) {
			withThreadName = v;
			return this;
		}

		public Builder withDelta(boolean v) {
			withDelta = v;
			return this;
		}

		public Builder withQuietMode(boolean v) {
			quietMode = v;
			return this;
		}

		private Builder reconcile()
		{
			if (SysProps.get(SYSPROP_FORCE_STDOUT, false)) {
				strm = System.out;
				pthnam = null;
			}

			if (pthnam != null) {
				if (pthnam.equalsIgnoreCase(PTHNAM_STDOUT)) {
					strm = System.out;
				} else if (pthnam.equalsIgnoreCase(PTHNAM_STDERR)) {
					strm = System.err;
				} else {
					// it's an actual pathname, which overrides the strm field
					strm = null;
				}
				if (strm != null) pthnam = null;
			}

			if (pthnam == null) {
				rotFreq = ScheduledTime.FREQ.NEVER;
				maxSize = 0;
			} else {
				if (rotFreq == null) rotFreq = ScheduledTime.FREQ.NEVER;
				if (rotFreq == ScheduledTime.FREQ.NEVER && maxSize == 0) {
					pthnam = ScheduledTime.embedTimestamp(null, null, pthnam, null);
				} else {
					// rotation of some form is in effect - size limit takes precedence
					if (maxSize != 0) rotFreq = ScheduledTime.FREQ.NEVER;
				}
				String tokenval = SysProps.get(SYSPROP_LOGSDIR, "./");
				pthnam = pthnam.replace(TOKEN_LOGSDIR, tokenval);
				pthnam = pthnam.replace(SysProps.DIRTOKEN_TMP, SysProps.TMPDIR);
				pthnam = pthnam.replaceAll(TOKEN_TID, String.valueOf(Thread.currentThread().getId()));
				pthnam = pthnam.replaceAll(TOKEN_PID, String.valueOf(CURRENT_PID));
				try {
					pthnam = new java.io.File(pthnam).getCanonicalPath();
				} catch (Exception ex) {
					throw new IllegalArgumentException("Failed to canonise config="+pthnam, ex);
				}
			}
			if (bufSize == 0) flushInterval = 0;
			return this;
		}

		public Parameters build() {
			reconcile();
			return new Parameters(this);
		}

		private static Builder fromConfig(XmlConfig cfg) {
			Builder bldr = new Builder().reconcile();
			if (cfg == null || cfg == XmlConfig.BLANKCFG || cfg == XmlConfig.NULLCFG) {
				return bldr;
			}
			bldr.logClass = cfg.getValue("@class", false, bldr.logClass);
			bldr.logLevel = Logger.LEVEL.valueOf(cfg.getValue("@level", false, bldr.logLevel.name()).toUpperCase());
			bldr.pthnam = cfg.getValue(".", false, bldr.pthnam);
			bldr.rotFreq = ScheduledTime.FREQ.valueOf(cfg.getValue("@rot", false, bldr.rotFreq.name()).toUpperCase());
			bldr.maxSize = (int)cfg.getSize("@maxfile", bldr.maxSize);
			bldr.bufSize = (int)cfg.getSize("@buffer", bldr.bufSize);
			bldr.flushInterval = cfg.getTime("@flush", bldr.flushInterval);
			bldr.withPID = cfg.getBool("@pid", bldr.withPID);
			bldr.withTID = cfg.getBool("@tid", bldr.withTID);
			bldr.withThreadName = cfg.getBool("@tname", bldr.withThreadName);
			bldr.withDelta = cfg.getBool("@delta", bldr.withDelta);
			return bldr.reconcile();
		}
	}
}
