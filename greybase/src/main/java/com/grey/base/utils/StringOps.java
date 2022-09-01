/*
 * Copyright 2010-2022 Yusef Badri - All rights reserved.
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

	public static int count(CharSequence container, char target) {return count(container, 0, container == null ? 0 : container.length(), target);}
	public static int indexOf(CharSequence container, char target) {return indexOf(container, 0, container == null ? 0 : container.length(), target);}
	public static int indexOf(CharSequence container, int off, char target) {return indexOf(container, off, (container==null?0:container.length()) - off, target);}
	public static int indexOf(CharSequence container, int coff, int clen, char target) {return indexOf(false, container, coff, clen, target);}
	public static int indexOf(CharSequence container, CharSequence target) {return indexOf(container, 0, container == null ? 0 : container.length(), target);}
	public static int indexOf(CharSequence container, int coff, int clen, CharSequence target) {return indexOf(false, container, coff, clen, target);}
	public static int indexOfNoCase(CharSequence container, char target) {return indexOfNoCase(container, 0, container == null ? 0 : container.length(), target);}
	public static int indexOfNoCase(CharSequence container, int off, char target) {return indexOfNoCase(container, off, (container==null?0:container.length()) - off, target);}
	public static int indexOfNoCase(CharSequence container, int coff, int clen, char target) {return indexOf(true, container, coff, clen, target);}
	public static int indexOfNoCase(CharSequence container, CharSequence target) {return indexOfNoCase(container, 0, container == null ? 0 : container.length(), target);}
	public static int indexOfNoCase(CharSequence container, int coff, int clen, CharSequence target) {return indexOf(true, container, coff, clen, target);}
	public static boolean sameSeq(CharSequence str1, CharSequence str2) {return sameSeq(str1, 0, str1 == null ? 0 : str1.length(), str2, 0, str2 == null ? 0 : str2.length());}
	public static boolean sameSeq(CharSequence str1, int off1, int len1, CharSequence str2) {return sameSeq(str1, off1, len1, str2, 0, str2 == null ? 0 : str2.length());}
	public static boolean sameSeq(CharSequence str1, int off1, int len1, CharSequence str2, int off2, int len2) {return sameSeq(false, str1, off1, len1, str2, off2, len2);}
	public static boolean sameSeqNoCase(CharSequence str1, CharSequence str2) {return sameSeqNoCase(str1, 0, str1 == null ? 0 : str1.length(), str2, 0, str2 == null ? 0 : str2.length());}
	public static boolean sameSeqNoCase(CharSequence str1, int off1, int len1, CharSequence str2) {return sameSeqNoCase(str1, off1, len1, str2, 0, str2 == null ? 0 : str2.length());}
	public static boolean sameSeqNoCase(CharSequence str1, int off1, int len1, CharSequence str2, int off2, int len2) {return sameSeq(true, str1, off1, len1, str2, off2, len2);}
	public static String stripQuotes(String str) {return stripQuotes(str, DFLT_QUOTE);}
	public static long parseNumber(CharSequence cs, int radix) {return parseNumber(cs, 0, (cs == null ? 0 : cs.length()), radix);}

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

	private static boolean sameSeq(boolean nocase, CharSequence str1, int off1, int len1, CharSequence str2, int off2, int len2)
	{
		if (len1 != len2) return false;
		int lmt = off1 + len1;
		while (off1 != lmt) {
			char ch1 = str1.charAt(off1++);
			char ch2 = str2.charAt(off2++);
			if (nocase) {
				ch1 = Character.toUpperCase(ch1);
				ch2 = Character.toUpperCase(ch2);
			}
			if (ch1 != ch2) return false;
		}
		return true;
	}

	public static int count(String container, String target)
	{
		if (target == null || target.length() == 0) return 0;
		if (container == null) return 0;
		int cnt = 0;
		int idx = 0;
		while ((idx = container.indexOf(target, idx)) != -1) {
			cnt++;
			idx += target.length();
		}
		return cnt;
	}

	public static int count(CharSequence container, int coff, int clen, char target)
	{
		int cnt = 0;
		int clmt = coff + clen;
		for (int idx = coff; idx != clmt; idx++) {
			if (container.charAt(idx) == target) cnt++;
		}
		return cnt;
	}

	private static int indexOf(boolean nocase, CharSequence container, int coff, int clen, char target)
	{
		if (nocase) target = Character.toLowerCase(target);
		int clmt = coff + clen;
		for (int idx = coff; idx != clmt; idx++) {
			char cval = container.charAt(idx);
			if (nocase) cval = Character.toLowerCase(cval);
			if (cval == target) return idx;
		}
		return -1;
	}

	private static int indexOf(boolean nocase, CharSequence container, int coff, int clen, CharSequence target)
	{
		int tlen = (target == null ? 0 : target.length());
		if (tlen == 0) return -1;
		int clmt = coff + clen;
		int cmaxpos = clmt - tlen;
		int toff = 0;
		char tval = 0;

		for (int idx = coff; idx != clmt; idx++) {
			if (tval == 0) {
				tval = target.charAt(toff);
				if (nocase) tval = Character.toLowerCase(tval);
			}
			char cval = container.charAt(idx);
			if (nocase) cval = Character.toLowerCase(cval);
			if (cval == tval) {
				if (++toff == tlen) return idx - tlen + 1;
				tval = 0;
			} else {
				if (idx == cmaxpos) return -1;
				if (toff != 0) {
					toff = 0;
					tval = 0;
				}
			}
		}
		return -1;
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

	public static long parseNumber(CharSequence cs, int off, int len, int radix)
	{
		if (len == 0) return 0;
		long numval = 0;
		long sign = 1;
		long power = 1;

		char ch0 = cs.charAt(off);
		if (ch0 == '-' || ch0 == '+') {
			if (ch0 == '-') sign = -1;
			off++;
			len--;
		}
		int lmt = off - 1;

		for (int idx = off+len-1; idx != lmt; idx--) {
			long digit = Character.digit(cs.charAt(idx), radix);
			if (digit == -1) {
				throw new NumberFormatException(cs.charAt(idx)+"@"+idx+" in "+off+":"+len+" - "+cs.subSequence(off, off+len));
			}
			numval += (digit * power);
			power *= radix;
		}
		return numval * sign;
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

	// This is merely a best effort
	public static boolean erase(String str)
	{
		Object raw_offset = DynLoader.getField(str, "offset"); //existed before Java 11
		int offset = (raw_offset == null ? 0 : (int)raw_offset);
		int lmt = offset + str.length();
		Object raw = DynLoader.getField(str, "value");
		if (raw instanceof byte[]) {
			byte[] value = (byte[])raw;
			for (int idx = offset; idx != lmt; idx++) {
				value[idx] = 0;
			}
		} else if (raw instanceof char[]) {
			//before Java 11
			char[] value = (char[])raw;
			for (int idx = offset; idx != lmt; idx++) {
				value[idx] = 0;
			}
		} else {
			//this method is incompatible with this version of Java
			return false;
		}
		return true;
	}

	public static String convert(byte[] buf, String charset) throws java.io.UnsupportedEncodingException
	{
		if (buf == null) return null;
		return new String(buf, charset == null ? DFLT_CHARSET : charset);
	}
}
