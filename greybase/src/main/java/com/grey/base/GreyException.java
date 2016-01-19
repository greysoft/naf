/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base;

public class GreyException extends Exception
{
	private static final long serialVersionUID = 1L;
	
	public static String summary(Throwable ex)
	{
		return summary(ex, 1, false);
	}
	
	public static String summary(Throwable ex, boolean withstack)
	{
		return summary(ex, 1, withstack);
	}
	
	private static String summary(Throwable ex, int level, boolean withstack)
	{
		StringBuilder strbuf = new StringBuilder(128);

		if (withstack)
		{
			// efficiency goes out the window here, as this is taken to be a relatively rare and serious event
			java.io.StringWriter sw = new java.io.StringWriter();
			java.io.PrintWriter pw = new java.io.PrintWriter(sw, false);
	        ex.printStackTrace(pw);
	        pw.close();
	        strbuf.append(sw);
		}
		else
		{
			String msg = ex.getMessage();
			strbuf.append("Exception-").append(level).append('=').append(ex.getClass().getName());
			if (msg != null) strbuf.append(" - ").append(msg);

			// printStackTrace() automatically follows exception chains, but we need to do it manually here
			ex = ex.getCause();
			if (ex != null) strbuf.append(com.grey.base.config.SysProps.EOL).append("\tCaused by: ").append(summary(ex, level+1, withstack));	
		}
		return strbuf.toString().trim();
	}

	public GreyException() {super();}
	public GreyException(String msg) {super(msg);}
	public GreyException(Throwable ex, String msg) {super(msg, ex);}
}
