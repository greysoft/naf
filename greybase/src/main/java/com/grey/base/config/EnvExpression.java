/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.config;

public class EnvExpression {
	private static final char OPEN_CHAR = '{';
	private static final char CLOSE_CHAR = '}';
	private static final String OPEN_TERM = "$"+String.valueOf(OPEN_CHAR);
	
	public static String eval(String expr) {
		if (expr == null || expr.isEmpty()) return expr;
		StringBuilder sb = new StringBuilder();
		int pos = 0;
		while (pos != expr.length()) {
			int pos2 = expr.indexOf(OPEN_TERM, pos);
			if (pos2 == -1) pos2 = expr.length();
			String s;
			if (pos2 == pos) {
				pos += OPEN_TERM.length();
				pos2 = findClose(expr, pos);
				if (pos2 == -1) throw new IllegalArgumentException("Unclosed "+OPEN_TERM+" in env expression - "+expr);
				String envTerm = expr.substring(pos, pos2);
				s = resolveEnvTerm(envTerm);
				pos2++;
			} else {
				s = expr.substring(pos, pos2);
			}
			sb.append(s);
			pos = pos2;
		}
		String val = sb.toString();
		return (val.isEmpty() ? null : val);
	}

	private static String resolveEnvTerm(String term) {
		if (term.equals("$")) return OPEN_TERM;
		String dflt = "";
		int pos = term.indexOf(':');
		String key = term;
		if (pos != -1) {
			key = term.substring(0, pos);
			dflt = term.substring(pos+1);
			dflt = eval(dflt);
		}
		String val = SysProps.get(key);
		if (val == null) val = dflt;
		return val;
	}

	private static int findClose(String expr, int pos) {
		int openCount = 1;
		while (pos != expr.length()) {
			if (expr.charAt(pos) == CLOSE_CHAR) {
				if (--openCount == 0) {
					return pos;
				}
			} else if (expr.charAt(pos) == OPEN_CHAR) {
				openCount++;
			}
			pos++;
		}
		return -1;
	}
}
