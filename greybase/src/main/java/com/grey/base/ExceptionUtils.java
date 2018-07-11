/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base;

import com.grey.base.config.SysProps;

public class ExceptionUtils
{
	public static String summary(Throwable ex)
	{
		return summary(ex, false);
	}
	
	public static String summary(Throwable ex, boolean withstack)
	{
		StringBuilder strbuf = new StringBuilder(128);

		if (withstack) {
			// efficiency goes out the window here, as this is taken to be a relatively rare and serious event
			java.io.StringWriter sw = new java.io.StringWriter();
			java.io.PrintWriter pw = new java.io.PrintWriter(sw, false);
	        ex.printStackTrace(pw);
	        pw.close();
	        strbuf.append(sw);
		} else {
			int level = 1;
			while (ex != null) {
				if (level != 1) strbuf.append(SysProps.EOL).append("\tCaused by: ");
				strbuf.append("Exception-").append(level++).append('=').append(ex.toString());
				ex = ex.getCause();
			}
		}
		return strbuf.toString();
	}
	
	public static Throwable getCause(Throwable ex, Class<? extends Throwable> clss)
	{
		while (ex != null) {
			if (clss.isAssignableFrom(ex.getClass())) return ex;
			ex = ex.getCause();
		}
		return null;
	}
}
