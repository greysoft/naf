/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

/**
 * This logger optimises away some relatively expensive character handling by assuming the log messages are all 8-bit charsets,
 * and can be mapped to 1-to-1 to a byte stream.
 * <br>
 * This assumption allows it to map the characters to bytes on a 1-to-1 basis, and write out a byte stream via the more efficient
 * JDK OutputStream classes (that is, more efficient than the char-oriented java.io.Writer classes).
 * <p>
 * Technically this class is therefore not limited to messages in the Latin alphabets since Arabic, Cyrillic and even Thai have
 * 8-bit charsets defined in the ISO-8859 family, but the more exotic character sets are more likely to be represented with Unicode,
 * so 8-bit characters do tend to mean we're dealing with plain ASCII or the Latin members of the ISO-8859 family.
 */
public class LatinLogger
	extends Logger
{
	private static final byte[] eolbytes = com.grey.base.config.SysProps.EOL.getBytes();

	//these two preallocated purely for efficiency - their state does not persist across calls
	private final com.grey.base.utils.ByteChars logmsg_buf = new com.grey.base.utils.ByteChars();
	private final StringBuilder tmpstrbuf = new StringBuilder();

	private java.io.OutputStream logstrm;

	protected LatinLogger(Parameters params, String logname)
	{
		this(params, logname, false);
	}

	protected LatinLogger(Parameters params, String logname, boolean is_mt)
	{
		super(params, logname, is_mt);
	}

	@Override
	protected void openStream(java.io.OutputStream strm)
	{	
		logstrm = strm;
	}

	@Override
	protected void openStream(String pthnam) throws java.io.IOException
	{
		java.io.FileOutputStream fstrm = new java.io.FileOutputStream(pthnam, true);
		logstrm = new java.io.BufferedOutputStream(fstrm, bufsiz);
	}

	@Override
	protected void closeStream(boolean is_owner) throws java.io.IOException
	{
		if (logstrm != null)
		{
			java.io.OutputStream strm = logstrm;
			logstrm = null;
			if (isOwner) strm.close();
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
			setLogEntry(lvl, tmpstrbuf);
			logmsg_buf.set(tmpstrbuf).append(msg).append(eolbytes, 0, eolbytes.length);
			logstrm.write(logmsg_buf.ar_buf, logmsg_buf.ar_off, logmsg_buf.ar_len);
		} catch (Throwable ex) {
	        System.out.println(new java.util.Date(System.currentTimeMillis())+" FATAL ERROR: Failed to write LatinLogger - "
	        		+com.grey.base.GreyException.summary(ex, true));
			System.exit(1);
		}
	}
}
