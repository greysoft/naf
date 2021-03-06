/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import java.time.Clock;

import com.grey.base.config.SysProps;

public class DispatcherDef
{
	public static final String SYSPROP_LOGNAME = "greynaf.dispatchers.logname";

	private final String name;
	private final String logName;
	private final boolean hasNafman;
	private final boolean hasDNS;
	private final boolean zeroNafletsOK;
	private final boolean surviveHandlers;
	private final long flushInterval;
	private final Clock clock;
	private final com.grey.base.config.XmlConfig[] naflets;

	private DispatcherDef(Builder bldr) {
		name = bldr.name;
		logName = bldr.logName;
		hasNafman = bldr.hasNafman;
		hasDNS = bldr.hasDNS;
		naflets = bldr.naflets;
		zeroNafletsOK = bldr.zeroNafletsOK;
		surviveHandlers = bldr.surviveHandlers;
		flushInterval = bldr.flushInterval;
		clock = bldr.clock;
	}

	public String getName() {
		return name;
	}

	public String getLogName() {
		return logName;
	}

	public boolean hasNafman() {
		return hasNafman;
	}

	public boolean hasDNS() {
		return hasDNS;
	}

	public boolean isZeroNafletsOK() {
		return zeroNafletsOK;
	}

	public boolean isSurviveHandlers() {
		return surviveHandlers;
	}

	public long getFlushInterval() {
		return flushInterval;
	}

	public Clock getClock() {
		return clock;
	}

	public com.grey.base.config.XmlConfig[] getNafletsConfig() {
		return naflets;
	}


	public static class Builder {
		private String name;
		private String logName;
		private boolean hasNafman;
		private boolean hasDNS;
		private boolean zeroNafletsOK = true;
		private boolean surviveHandlers = true;
		private Clock clock = Clock.systemUTC();
		private long flushInterval;
		private com.grey.base.config.XmlConfig[] naflets;

		public Builder() {}

		public Builder(DispatcherDef defs) {
			name = defs.name;
			logName = defs.logName;
			hasNafman = defs.hasNafman;
			hasDNS = defs.hasDNS;
			naflets = defs.naflets;
			zeroNafletsOK = defs.zeroNafletsOK;
			surviveHandlers = defs.surviveHandlers;
			flushInterval = defs.flushInterval;
			clock = defs.clock;
		}

		public Builder(com.grey.base.config.XmlConfig cfg) {
			name = cfg.getValue("@name", true, name);
			logName = cfg.getValue("@logname", true, SysProps.get(SYSPROP_LOGNAME, name));
			hasNafman = cfg.getBool("@nafman", hasNafman);
			hasDNS = cfg.getBool("@dns", hasDNS);
			zeroNafletsOK = cfg.getBool("@zero_naflets", zeroNafletsOK);
			surviveHandlers = cfg.getBool("@survive_handlers", surviveHandlers);
			flushInterval = cfg.getTime("@flush", flushInterval);

			String xpath = "naflets/naflet"+com.grey.base.config.XmlConfig.XPATH_ENABLED;
			naflets = cfg.getSections(xpath);
		}

		public Builder withName(String v) {
			this.name = v;
			return this;
		}

		public Builder withLogName(String v) {
			this.logName = v;
			return this;
		}

		public Builder withNafman(boolean v) {
			this.hasNafman = v;
			return this;
		}

		public Builder withDNS(boolean v) {
			this.hasDNS = v;
			return this;
		}

		public Builder withNafletsConfig(com.grey.base.config.XmlConfig[] v) {
			this.naflets = v;
			return this;
		}

		public Builder withZeroNafletsOK(boolean v) {
			this.zeroNafletsOK = v;
			return this;
		}

		public Builder withSurviveHandlers(boolean v) {
			this.surviveHandlers = v;
			return this;
		}

		public Builder withClock(Clock v) {
			this.clock = v;
			return this;
		}

		public Builder withFlushInterval(long v) {
			this.flushInterval = v;
			return this;
		}

		public DispatcherDef build() {
			return new DispatcherDef(this);
		}
	}
}
