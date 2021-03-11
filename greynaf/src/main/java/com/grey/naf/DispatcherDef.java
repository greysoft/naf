/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import java.time.Clock;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;

public class DispatcherDef
{
	public static final String SYSPROP_LOGNAME = "greynaf.dispatchers.logname";

	private final String name;
	private final String logName;
	private final boolean surviveHandlers;
	private final boolean zeroNafletsOK;
	private final long flushInterval;
	private final boolean hasDNS;
	private final Clock clock;

	private DispatcherDef(Builder bldr) {
		name = bldr.name;
		logName = bldr.logName;
		surviveHandlers = bldr.surviveHandlers;
		zeroNafletsOK = bldr.zeroNafletsOK;
		flushInterval = bldr.flushInterval;
		hasDNS = bldr.hasDNS;
		clock = bldr.clock;
	}

	public String getName() {
		return name;
	}

	public String getLogName() {
		return logName;
	}

	public boolean isSurviveHandlers() {
		return surviveHandlers;
	}

	public boolean isZeroNafletsOK() {
		return zeroNafletsOK;
	}

	public long getFlushInterval() {
		return flushInterval;
	}

	public boolean hasDNS() {
		return hasDNS;
	}

	public Clock getClock() {
		return clock;
	}


	public static class Builder {
		private String name;
		private String logName = SysProps.get(SYSPROP_LOGNAME, name);
		private boolean surviveHandlers = true;
		private boolean zeroNafletsOK = true;
		private long flushInterval;
		private boolean hasDNS;
		private Clock clock = Clock.systemUTC();

		public Builder() {}

		public Builder(DispatcherDef defs) {
			name = defs.name;
			logName = defs.logName;
			surviveHandlers = defs.surviveHandlers;
			zeroNafletsOK = defs.zeroNafletsOK;
			flushInterval = defs.flushInterval;
			hasDNS = defs.hasDNS;
			clock = defs.clock;
		}

		public Builder withXmlConfig(XmlConfig cfg) {
			name = cfg.getValue("@name", true, name);
			logName = cfg.getValue("@logname", true, logName == null ? name : logName);
			surviveHandlers = cfg.getBool("@survive_handlers", surviveHandlers);
			zeroNafletsOK = cfg.getBool("@zero_naflets", zeroNafletsOK);
			flushInterval = cfg.getTime("@flush", flushInterval);
			hasDNS = cfg.getBool("@dns", hasDNS);
			return this;
		}

		public Builder withName(String v) {
			name = v;
			return this;
		}

		public Builder withLogName(String v) {
			logName = v;
			return this;
		}

		public Builder withSurviveHandlers(boolean v) {
			surviveHandlers = v;
			return this;
		}

		public Builder withZeroNafletsOK(boolean v) {
			zeroNafletsOK = v;
			return this;
		}

		public Builder withFlushInterval(long v) {
			flushInterval = v;
			return this;
		}

		public Builder withDNS(boolean v) {
			hasDNS = v;
			return this;
		}

		public Builder withClock(Clock v) {
			clock = v;
			return this;
		}

		public DispatcherDef build() {
			return new DispatcherDef(this);
		}
	}
}
