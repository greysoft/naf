/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
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

	public static boolean sameSeq(CharSequence str1, int off1, int len1, CharSequence str2, int off2, int len2)
	{
		if (len1 != len2) return false;
		int lmt = off1 + len1;

		while (off1 != lmt) {
			if (str1.charAt(off1++) != str2.charAt(off2++)) return false;
		}
		return true;
	}

	public static boolean sameSeq(CharSequence str1, CharSequence str2)
	{
		if (str1 == null || str2 == null) return  (str1 == null && str2 == null);
		return sameSeq(str1, 0, str1.length(), str2, 0, str2.length());
	}

	public static boolean sameSeq(CharSequence str1, int off1, int len1, CharSequence str2)
	{
		if (str2 == null) return (len1 == 0);
		return sameSeq(str1, off1, len1, str2, 0, str2.length());
	}

	public static boolean sameSeqNoCase(CharSequence str1, int off1, int len1, CharSequence str2, int off2, int len2)
	{
		if (len1 != len2) return false;
		int lmt = off1 + len1;

		while (off1 != lmt) {
			if (Character.toUpperCase(str1.charAt(off1++)) != Character.toUpperCase(str2.charAt(off2++))) return false;
		}
		return true;
	}

	public static boolean sameSeqNoCase(CharSequence str1, CharSequence str2)
	{
		if (str1 == null || str2 == null) return  (str1 == null && str2 == null);
		return sameSeqNoCase(str1, 0, str1.length(), str2, 0, str2.length());
	}

	public static boolean sameSeqNoCase(CharSequence str1, int off1, int len1, CharSequence str2)
	{
		if (str2 == null) return (len1 == 0);
		return sameSeqNoCase(str1, off1, len1, str2, 0, str2.length());
	}

	public static int indexOfNoCase(CharSequence str, int off, int len, CharSequence target)
	{
		int tlen = target.length();
		if (tlen > len) return -1;
		int lmt = off + len;
		int maxpos = lmt - tlen;
		int toff = 0;
		char tval = Character.toLowerCase(target.charAt(toff));

		for (int idx = off; idx != lmt; idx++) {
			if (Character.toLowerCase(str.charAt(idx)) == tval) {
				if (++toff == tlen) return idx - tlen + 1;
				tval = Character.toLowerCase(target.charAt(toff));
			} else {
				if (off == maxpos) return -1;
				if (toff != 0) {
					toff = 0;
					tval = Character.toLowerCase(target.charAt(toff));
				}
			}
		}
		return -1;
	}

	public static int indexOfNoCase(CharSequence str, CharSequence target)
	{
		return indexOfNoCase(str, 0, str.length(), target);
	}

	public static int indexOf(CharSequence str, int off, int len, char ch)
	{
		int lmt = off + len;
		for (int idx = off; idx != lmt; idx++) {
			if (str.charAt(idx) == ch) return idx;
		}
		return -1;
	}

	public static int indexOf(CharSequence str, char ch)
	{
		return indexOf(str, 0, str.length(), ch);
	}

	public static int indexOf(CharSequence str, int off, char ch)
	{
		return indexOf(str, off, str.length() - off, ch);
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

	public static int occurrences(String main, String target)
	{
		int cnt = 0;
		int idx = 0;
		while ((idx = main.indexOf(target, idx)) != -1) {
			cnt++;
			idx += target.length();
		}
		return cnt;
	}

	public static int occurrences(CharSequence cs, int off, int len, char target)
	{
		int cnt = 0;
		int lmt = off + len;
		for (int idx = off; idx != lmt; idx++) {
			if (cs.charAt(idx) == target) cnt++;
		}
		return cnt;
	}

	public static long parseNumber(CharSequence cs, int off, int len, int radix)
	{
		long numval = 0;
		long power = 1;
		for (int idx = off+len-1; idx >= off; idx--) {
			long digit = Character.digit(cs.charAt(idx), radix);
			if (digit == -1) {
				throw new NumberFormatException(cs.charAt(idx)+"@"+idx+" in "+off+":"+len+" - "+cs.subSequence(off, off+len));
			}
			numval += (digit * power);
			power *= radix;
		}
		return numval;
	}

	public static long parseNumber(CharSequence cs, int radix)
	{
		return parseNumber(cs, 0, cs.length(), radix);
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
		for (int idx = 0; idx != cnt; idx++) {
			if ((pos = str.indexOf(dlm, pos)) == -1) return str;
			pos++;
		}
		if (pos == 0 || pos == 1) return "";
		return str.substring(0, pos - 1);
	}

	public static String stripLeadingParts(String str, String dlm, int cnt)
	{
		int pos = 0;
		for (int idx = 0; idx != cnt; idx++) {
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
		for (int idx = 0; idx != cnt; idx++) {
			if ((pos = str.lastIndexOf(dlm, --pos)) == -1) return str;
		}
		if (pos == startpos || pos == startpos - 1) return "";
		return str.substring(pos + 1);
	}

	public static String stripTrailingParts(String str, String dlm, int cnt)
	{
		int startpos = str.length();
		int pos = startpos;
		for (int idx = 0; idx != cnt; idx++) {
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

	public static void erase(String str)
	{
		char[] value = (char[])DynLoader.getField(str, "value");
		Integer offset = (Integer)DynLoader.getField(str, "offset");
		int lmt = offset.intValue() + str.length();
		for (int idx = offset; idx != lmt; idx++) {
			value[idx] = '?';
		}
	}
}
