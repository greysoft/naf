/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class StringOps
{
	private static String dfltchset = com.grey.base.config.SysProps.get("grey.text.charset", "UTF-8");
	static {
		if (dfltchset.equalsIgnoreCase("dflt")) dfltchset = java.nio.charset.Charset.defaultCharset().name();
	}
	public static final String DFLT_CHARSET = dfltchset;
	protected static final char DFLT_QUOTE = '"';

	public static String stripQuotes(String str) {return stripQuotes(str, DFLT_QUOTE);}

	public static boolean stringAsBool(String strval)
	{
		if (strval == null) return false;
		return (strval.equalsIgnoreCase("YES") || strval.equalsIgnoreCase("Y")
				|| strval.equalsIgnoreCase("TRUE") || strval.equalsIgnoreCase("T")
				|| strval.equalsIgnoreCase("ON")
				|| strval.equals("1"));
	}

	public static String boolAsString(boolean bval)
	{
		return (bval ? "Y" : "N");
	}

	public static String convert(byte[] buf, String charset) throws java.io.UnsupportedEncodingException
	{
		if (buf == null) return null;
		return new String(buf, charset == null ? DFLT_CHARSET : charset);
	}

	// NB: This doesn't work on all CharSequence classes, only on this whose equals() method genuinely compares their contents.
	// Thus it wouldn't work on StringBuilder, because it falls back on the root Object.equals() method, ie. test for identity
	public static boolean sameSeq(CharSequence str1, CharSequence str2)
	{
		if (str1 == null || str2 == null) return  (str1 == null && str2 == null);
		int len = str1.length();
		if (str2.length() != len) return false;
		
		for (int idx = 0; idx != len; idx++)
		{
			if (str1.charAt(idx) != str2.charAt(idx)) return false;
		}
		return true;
	}

	// zero-pad numbers without generating any memory garbage
	public static StringBuilder zeroPad(StringBuilder sbuf, int numval, int size)
	{
		int pad = size - digits(numval);
		for (int loop = 0; loop < pad; loop++) sbuf.append('0');
		sbuf.append(numval);
		return sbuf;
	}

	// How many digits are required to represent a decimal number?
	// JDK's stringSizeOfInt() is the same performance-wise, as it loops through a pre-built table
	// of number sizes. However, this layout makes it easier to spot typos in the number of zeroes.
	public static int digits(int numval)
	{
		if (numval < 10) return 1;
		if (numval < 100) return 2;
		if (numval < 1000) return 3;
		if (numval < 10000) return 4;
		if (numval < 100000) return 5;
		if (numval < 1000000) return 6;
		if (numval < 10000000) return 7;
		if (numval < 100000000) return 8;
		if (numval < 1000000000) return 9;
		return 10;
	}

	public static int occurrences(String main, String patt)
	{
		int cnt = 0;
		int idx = 0;
		while ((idx = main.indexOf(patt, idx)) != -1)
		{
			cnt++;
			idx += patt.length();
		}
		return cnt;
	}

	public static String leadingChars(String str, int cnt)
	{
		if (str == null || cnt == 0 || cnt >= str.length()) return str;
		return str.substring(0, cnt);
	}

	public static String trailingChars(String str, int cnt)
	{
		if (str == null || cnt == 0) return str;
		int len = str.length();
		if (cnt >= len) return str;
		return str.substring(len - cnt, len);
	}

	public static String keepLeadingParts(String str, String dlm, int cnt)
	{
		int pos = 0;
		for (int idx = 0; idx != cnt; idx++)
		{
			if ((pos = str.indexOf(dlm, pos)) == -1) return str;
			pos++;
		}
		if (pos == 0 || pos == 1) return "";
		return str.substring(0, pos - 1);
	}

	public static String stripLeadingParts(String str, String dlm, int cnt)
	{
		int pos = 0;
		for (int idx = 0; idx != cnt; idx++)
		{
			if ((pos = str.indexOf(dlm, pos)) == -1) return "";
			pos++;
		}
		if (pos == 0) return str;
		if (pos == str.length()) return "";
		return str.substring(pos);
	}

	public static String keepTrailingParts(String str, String dlm, int cnt)
	{
		int startpos = str.length();
		int pos = startpos;
		for (int idx = 0; idx != cnt; idx++)
		{
			if ((pos = str.lastIndexOf(dlm, --pos)) == -1) return str;
		}
		if (pos == startpos || pos == startpos - 1) return "";
		return str.substring(pos + 1);
	}

	public static String stripTrailingParts(String str, String dlm, int cnt)
	{
		int startpos = str.length();
		int pos = startpos;
		for (int idx = 0; idx != cnt; idx++)
		{
			if ((pos = str.lastIndexOf(dlm, --pos)) == -1) return "";
		}
		if (pos == startpos) return str;
		if (pos == 0) return "";
		return str.substring(0, pos);
	}

	public static String fill(char ch, int size)
	{
		char[] arr = new char[size];
		java.util.Arrays.fill(arr, ch);
		return new String(arr);
	}

	public static String stripQuotes(String str, char quote)
	{
		if (str == null || str.length() < 2 || str.charAt(0) != quote || str.charAt(str.length()-1) != quote) return str;
		return str.substring(1, str.length() - 1);
	}

	public static String flatten(String str, int maxlen)
	{
		if (str == null) return "NULL";
		str = str.replaceAll("\n", " ").replaceAll("\r", " ").replaceAll("\t", " ");
		while (str.indexOf("  ") != -1) str = str.replaceAll("  ", " ");
		return leadingChars(str, maxlen);
	}
}
