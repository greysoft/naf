/*
 * Copyright 2011-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.ByteOps;
import com.grey.base.utils.TimeOps;
import com.grey.base.utils.ScheduledTime;

public class Parameters
{
	public static final String SYSPROP_LOGCLASS = "grey.logger.class";
	public static final String SYSPROP_LOGSDIR = "grey.logger.dir";
	public static final String SYSPROP_LOGFILE = "grey.logger.file";
	public static final String SYSPROP_LOGLEVEL = "grey.logger.level";
	public static final String SYSPROP_FLUSHINTERVAL = "grey.logger.flushinterval";
	public static final String SYSPROP_BUFSIZ = "grey.logger.bufsiz";
	public static final String SYSPROP_ROTFREQ = "grey.logger.rotfreq";
	public static final String SYSPROP_SHOWTID = "grey.logger.tid";
	public static final String SYSPROP_SHOWTHRDNAME = "grey.logger.threadname";
	public static final String SYSPROP_SHOWDELTA = "grey.logger.delta";
	public static final String SYSPROP_FORCE_STDOUT = "grey.logger.stdout";

	public static final String TOKEN_LOGSDIR = "%DIRLOG%";
	public static final String TOKEN_TID = "%TID%";

	public static final String MODE_AUDIT = "AUDIT";

	public static final Class<?> DFLTCLASS = MTLatinLogger.class;  //safe option for naive/unaware apps
	public static final java.io.OutputStream DFLT_STRM = System.out;
	public static final String PTHNAM_STDOUT = "%stdout%";
	public static final String PTHNAM_STDERR = "%stderr%";

	public String logclass = DFLTCLASS.getName();
	public java.io.OutputStream strm = DFLT_STRM;
	public Logger.LEVEL loglevel = Logger.LEVEL.INFO;
	public ScheduledTime.FREQ rotfreq = ScheduledTime.FREQ.NEVER;
	public int bufsiz = 8 * 1024;  // 8K
	public long flush_interval = 0;
	public String pthnam = null;	// pathname template for logfile
	public int maxsize;
	public boolean withTID = true;
	public boolean tidPlusName = false;
	public boolean withDelta = false;
	public String mode;

	public Parameters()
	{
		pthnam = SysProps.get(SYSPROP_LOGFILE, pthnam);
		logclass = SysProps.get(SYSPROP_LOGCLASS, logclass);
		loglevel = Logger.LEVEL.valueOf(SysProps.get(SYSPROP_LOGLEVEL, loglevel.name()).toUpperCase());
		withTID = SysProps.get(SYSPROP_SHOWTID, withTID);
		tidPlusName = SysProps.get(SYSPROP_SHOWTHRDNAME, tidPlusName);
		withDelta = SysProps.get(SYSPROP_SHOWDELTA, withDelta);
		flush_interval = SysProps.getTime(SYSPROP_FLUSHINTERVAL, flush_interval);
		bufsiz = SysProps.get(SYSPROP_BUFSIZ, bufsiz);

		String str = SysProps.get(SYSPROP_ROTFREQ, rotfreq.name());
		rotfreq = ScheduledTime.FREQ.valueOf(str.toUpperCase());
	}

	public Parameters(XmlConfig cfg) throws com.grey.base.ConfigException
	{
		this();
		if (cfg == null) return;
		pthnam = cfg.getValue(".", false, null);
		logclass = cfg.getValue("@class", false, logclass);
		loglevel = Logger.LEVEL.valueOf(cfg.getValue("@level", false, loglevel.name()).toUpperCase());
		withTID = cfg.getBool("@tid", withTID);
		tidPlusName = cfg.getBool("@tname", tidPlusName);
		withDelta = cfg.getBool("@delta", withDelta);
		flush_interval = cfg.getTime("@flush", flush_interval);
		bufsiz = (int)cfg.getSize("@buffer", bufsiz);
		maxsize = (int)cfg.getSize("@maxfile", maxsize);
		mode = cfg.getValue("@mode", false, mode);

		String str = cfg.getValue("@rot", false, rotfreq.name());
		rotfreq = ScheduledTime.FREQ.valueOf(str.toUpperCase());
	}

	public Parameters(Logger.LEVEL max, String path)
	{
		this();
		if (max != null) loglevel = max;
		if (path != null) pthnam = path;
	}

	public Parameters(Logger.LEVEL max, java.io.OutputStream s)
	{
		this();
		if (max != null) loglevel = max;
		if (s != null) strm = s;
	}

	// NB: We deliberately don't take a static final reading of SYSPROP_LOGSDIR, in order to allow callers
	// to set it even if this logging framework was initialised before them.
	public void reconcile()
	{
		if (SysProps.get(SYSPROP_FORCE_STDOUT, false)) {
			strm = System.out;
			pthnam = null;
		}
		if (rotfreq == null) rotfreq = ScheduledTime.FREQ.NEVER;

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
			rotfreq = ScheduledTime.FREQ.NEVER;
			maxsize = 0;
		} else {
			if (rotfreq == ScheduledTime.FREQ.NEVER && maxsize == 0) {
				pthnam = ScheduledTime.embedTimestamp(null, null, pthnam, null);
			} else {
				// rotation of some form is in effect - size limit takes precedence
				if (maxsize != 0) rotfreq = ScheduledTime.FREQ.NEVER;
			}
			String tokenval = SysProps.get(SYSPROP_LOGSDIR);
			if (tokenval != null) pthnam = pthnam.replace(TOKEN_LOGSDIR, tokenval);
			pthnam = pthnam.replace(SysProps.DIRTOKEN_TMP, SysProps.TMPDIR);
			pthnam = pthnam.replaceAll(TOKEN_TID, String.valueOf(Thread.currentThread().getId()));
			try {
				pthnam = new java.io.File(pthnam).getCanonicalPath();
			} catch (Exception ex) {
				throw new RuntimeException("Failed to determine logger path - "+com.grey.base.GreyException.summary(ex), ex);
			}
		}
		if (bufsiz == 0) flush_interval = 0;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(80);
		sb.append("Dest=");
		if (pthnam != null) {
			sb.append(pthnam);
		} else if (strm == System.out) {
			sb.append("stdout");
		} else if (strm == System.err) {
			sb.append("stderr");
		} else if (strm == null) {
			sb.append("SINK");
		} else {
			sb.append(strm);
		}
		sb.append(" Type=").append(logclass).append('/').append(loglevel);

		if (rotfreq != ScheduledTime.FREQ.NEVER || maxsize != 0) {
			sb.append(" Rot=");
			if (maxsize == 0) {
				sb.append(rotfreq);
			} else {
				ByteOps.expandByteSize(maxsize, sb, false);
			}
		}
		if (bufsiz != 0) {
			sb.append(" Buffer=");
			ByteOps.expandByteSize(bufsiz, sb, false);
			sb.append('/');
			TimeOps.expandMilliTime(flush_interval, sb, false);
		}
		return sb.toString();
	}
}
