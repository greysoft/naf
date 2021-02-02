/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.config;

public class EnvExpression {
	public static String eval(String expr) {
		if (expr == null || expr.isEmpty()) return expr;
		StringBuilder sb = new StringBuilder();
		int pos = 0;
		while (pos != expr.length()) {
			int pos2 = expr.indexOf("${", pos);
			if (pos2 == -1) pos2 = expr.length();
			String s;
			if (pos2 == pos) {
				pos2 = expr.indexOf('}', pos);
				if (pos2 == -1) throw new IllegalArgumentException("Unclosed ${ in env expression - "+expr);
				String envTerm = expr.substring(pos+2, pos2);
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

	private static String resolveEnvTerm(String key) {
		if (key.equals("$")) return "${";
		String dflt = "";
		int pos = key.indexOf(':');
		if (pos != -1) {
			dflt = key.substring(pos+1);
			key = key.substring(0, pos);
		}
		String val = SysProps.get(key);
		if (val == null) val = dflt;
		return val;
	}
}
