/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

/**
 * This logger faithfully writes out the log message as is, preserving the character-set encoding.
 * <br>
 * As such, it is a universal logger, but this may come at a potential performance cost if we know there are no characters
 * larger than 8 bits (see LatinLogger).
 */
public class CharLogger
	extends Logger
{
	private static final String eolstr = com.grey.base.config.SysProps.EOL;

	private final StringBuilder logmsg_buf = new StringBuilder();
	private char[] logmsg_chars = new char[1024];  // should be large enough for most log messages
	private java.io.BufferedWriter logstrm;

	protected CharLogger(Parameters params, String logname)
	{
		this(params, logname, false);
	}

	protected CharLogger(Parameters params, String logname, boolean is_mt)
	{
		super(params, logname, is_mt);
	}

	@Override
	protected void openStream(java.io.OutputStream strm)
	{
		logstrm = new java.io.BufferedWriter(new java.io.OutputStreamWriter(strm), bufsiz);
	}

	@Override
	protected void openStream(String pthnam) throws java.io.IOException
	{
		java.io.FileOutputStream fstrm = new java.io.FileOutputStream(pthnam, true);
		logstrm = new java.io.BufferedWriter(new java.io.OutputStreamWriter(fstrm), bufsiz);
	}

	@Override
	protected void closeStream(boolean is_owner) throws java.io.IOException
	{
		if (logstrm != null)
		{
			java.io.BufferedWriter strm = logstrm;
			logstrm = null;
			if (isOwner()) strm.close();
		}
	}

	@Override
	public void flush() throws java.io.IOException
	{
		if (logstrm != null) logstrm.flush();	
	}

	@Override
	public void log(LEVEL lvl, CharSequence msg)
	{
		if (!isActive(lvl)) return;

		try {
			setLogEntry(lvl, logmsg_buf);
			logmsg_buf.append(msg).append(eolstr);

			int mlen = logmsg_buf.length();
			if (mlen > logmsg_chars.length - 100) logmsg_chars = new char[mlen + 100];  // guaranteed comfortable excess
			logmsg_buf.getChars(0, mlen, logmsg_chars, 0);

			logstrm.write(logmsg_chars, 0, logmsg_buf.length());
		} catch (Throwable ex) {
	        System.out.println(new java.util.Date(System.currentTimeMillis())+" FATAL ERROR: Failed to write CharLogger - "
	        		+com.grey.base.ExceptionUtils.summary(ex, true));
			System.exit(1);
		}
	}
}
