/*
 * Copyright 2010-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class StringOpsTest
{
	@org.junit.Test
	public void testBool()
	{
		org.junit.Assert.assertTrue(StringOps.boolAsString(true).equalsIgnoreCase("y"));
		org.junit.Assert.assertTrue(StringOps.boolAsString(false).equalsIgnoreCase("n"));

		org.junit.Assert.assertFalse(StringOps.stringAsBool(null));
		org.junit.Assert.assertFalse(StringOps.stringAsBool(""));
		org.junit.Assert.assertFalse(StringOps.stringAsBool("rubbish"));

		org.junit.Assert.assertTrue(StringOps.stringAsBool("y"));
		org.junit.Assert.assertTrue(StringOps.stringAsBool("Y"));
		org.junit.Assert.assertTrue(StringOps.stringAsBool("yEs"));
		org.junit.Assert.assertTrue(StringOps.stringAsBool("true"));
		org.junit.Assert.assertTrue(StringOps.stringAsBool("t"));
		org.junit.Assert.assertTrue(StringOps.stringAsBool("T"));
		org.junit.Assert.assertTrue(StringOps.stringAsBool("oN"));
		org.junit.Assert.assertTrue(StringOps.stringAsBool("1"));
	}

	@org.junit.Test
	public void testSameSeq()
	{
		String str1 = "Value1";
		String str2 = "Value2";
		ByteChars bc1 = new ByteChars("Value1");
		ByteChars bc2 = new ByteChars("Value2a");
		org.junit.Assert.assertTrue("Null vs Null", StringOps.sameSeq(null, null));
		org.junit.Assert.assertTrue(StringOps.sameSeq(null, ""));
		org.junit.Assert.assertTrue(StringOps.sameSeq("", null));
		org.junit.Assert.assertFalse("String vs Null", StringOps.sameSeq(str1, null));
		org.junit.Assert.assertFalse("Null vs ByteChars", StringOps.sameSeq(null, bc1));
		org.junit.Assert.assertTrue("Equal Strings", StringOps.sameSeq(str1, new String(str1)));
		org.junit.Assert.assertTrue("Identical Strings", StringOps.sameSeq(str1, str1));
		org.junit.Assert.assertFalse("Variant Strings", StringOps.sameSeq(str1, str2));
		org.junit.Assert.assertTrue("Equal ByteChars", StringOps.sameSeq(bc1, new ByteChars(bc1)));
		org.junit.Assert.assertTrue("Identical ByteChars", StringOps.sameSeq(bc1, bc1));
		org.junit.Assert.assertFalse("Variant ByteChars", StringOps.sameSeq(bc1, bc2));
		org.junit.Assert.assertTrue("Equal CharSeqs", StringOps.sameSeq(str1, bc1));
		org.junit.Assert.assertFalse("Variant-Length CharSeqs", StringOps.sameSeq(str1, bc2));
		org.junit.Assert.assertFalse("Variant-Length CharSeqs reversed", StringOps.sameSeq(bc2, str1));
		org.junit.Assert.assertFalse("Variant CharSeqs", StringOps.sameSeq(str2, bc1));
		org.junit.Assert.assertFalse("Variant CharSeqs reversed", StringOps.sameSeq(bc1, str2));
		org.junit.Assert.assertFalse(StringOps.sameSeq("AbC", "abc"));
		org.junit.Assert.assertTrue(StringOps.sameSeq("aBc", "aBc"));
		org.junit.Assert.assertTrue(StringOps.sameSeq("AbC", 0, 0, null));
		org.junit.Assert.assertFalse(StringOps.sameSeq("AbC", 0, 1, null));

		StringBuilder sb1 = new StringBuilder("abc");
		StringBuilder sb2 = new StringBuilder(sb1);
		org.junit.Assert.assertTrue("Equal StringBuilders", StringOps.sameSeq(sb1, sb2));
		sb2 = new StringBuilder("xyz");
		org.junit.Assert.assertFalse("Variant StringBuilders", StringOps.sameSeq(sb1, sb2));

		String pfx = "Now I know my ";
		String seq = "A Bee See";
		org.junit.Assert.assertTrue(StringOps.sameSeq(pfx+seq+" so there", pfx.length(), seq.length(), seq));
		String seq2 = String.valueOf(seq.charAt(0)).toLowerCase()+seq.substring(1);
		org.junit.Assert.assertFalse(StringOps.sameSeq(pfx+seq+" so there", pfx.length(), seq.length(), seq2));
		org.junit.Assert.assertTrue(StringOps.sameSeqNoCase(pfx+seq+" so there", pfx.length(), seq.length(), seq2));
		seq2 = String.valueOf(seq.charAt(0)+1)+seq.substring(1);
		org.junit.Assert.assertFalse(StringOps.sameSeqNoCase(pfx+seq+" so there", pfx.length(), seq.length(), seq2));

		org.junit.Assert.assertTrue(StringOps.sameSeqNoCase("AbC", "abc"));
		org.junit.Assert.assertFalse(StringOps.sameSeqNoCase("AbC", "axc"));
		org.junit.Assert.assertTrue(StringOps.sameSeqNoCase(null, null));
		org.junit.Assert.assertTrue(StringOps.sameSeqNoCase(null, ""));
		org.junit.Assert.assertTrue(StringOps.sameSeqNoCase("", null));
		org.junit.Assert.assertFalse(StringOps.sameSeqNoCase(null, "a"));
		org.junit.Assert.assertFalse(StringOps.sameSeqNoCase("a", null));
		org.junit.Assert.assertTrue(StringOps.sameSeqNoCase("AbC", 0, 0, null));
		org.junit.Assert.assertFalse(StringOps.sameSeqNoCase("AbC", 0, 1, null));
	}

	@org.junit.Test
	public void testIndexOf()
	{
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("vvv", "abc"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("vvvv", "abc"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("vv", "abc"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("ab", "abc"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("avvabvvab", "abc"));

		org.junit.Assert.assertEquals(0, StringOps.indexOfNoCase("abc", "A"));
		org.junit.Assert.assertEquals(1, StringOps.indexOfNoCase("abc", "B"));
		org.junit.Assert.assertEquals(2, StringOps.indexOfNoCase("abc", "C"));

		org.junit.Assert.assertEquals(0, StringOps.indexOfNoCase("abc", "abc"));
		org.junit.Assert.assertEquals(0, StringOps.indexOfNoCase("abc", "aBc"));
		org.junit.Assert.assertEquals(0, StringOps.indexOf("abc", "abc"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOf("abc", "aBc"));
		String lead = "avvabvv";
		org.junit.Assert.assertEquals(lead.length(), StringOps.indexOfNoCase(lead+"abc", "abc"));
		org.junit.Assert.assertEquals(lead.length(), StringOps.indexOfNoCase(lead+"abcvv", "abc"));
		org.junit.Assert.assertEquals(2, StringOps.indexOfNoCase("vvabcvv", "abc"));

		org.junit.Assert.assertEquals(-1, StringOps.indexOf(null, null));
		org.junit.Assert.assertEquals(-1, StringOps.indexOf(null, "A"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOf("A", null));
		org.junit.Assert.assertEquals(-1, StringOps.indexOf("", "A"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOf("A", ""));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase(null, null));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase(null, "A"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("A", null));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("", "A"));
		org.junit.Assert.assertEquals(-1, StringOps.indexOfNoCase("A", ""));

		StringBuilder sb = new StringBuilder("z before zz and one at the endz");
		int pos = StringOps.indexOf(sb, 'z');
		org.junit.Assert.assertEquals(0, pos);
		pos = StringOps.indexOf(sb, sb.length()-2, 2, 'z');
		org.junit.Assert.assertEquals(sb.length()-1, pos);
		int pos_b = StringOps.indexOf(sb, 'b');
		org.junit.Assert.assertEquals(2, pos_b);
		pos = StringOps.indexOf(sb, pos_b, 'b');
		org.junit.Assert.assertEquals(pos_b, pos);
		pos = StringOps.indexOf(sb, pos_b+1, 'b');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOf(sb, 'B');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOf(null, 'B');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOf("", 'B');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOf(null, 0, 'B');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOf("", 0, 'B');
		org.junit.Assert.assertEquals(-1, pos);

		pos = StringOps.indexOfNoCase(sb, 'B');
		org.junit.Assert.assertEquals(pos_b, pos);
		pos = StringOps.indexOfNoCase(sb, 1, 'B');
		org.junit.Assert.assertEquals(pos_b, pos);
		pos = StringOps.indexOfNoCase(null, 'B');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOfNoCase("", 'B');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOfNoCase(null, 0, 'B');
		org.junit.Assert.assertEquals(-1, pos);
		pos = StringOps.indexOfNoCase("", 0, 'B');
		org.junit.Assert.assertEquals(-1, pos);
	}

	@org.junit.Test
	public void testCount()
	{
		String main = "XthisXisXsinglecharXpatternX";
		int cnt = StringOps.count(main, "X");
		org.junit.Assert.assertEquals(5, cnt);
		cnt = StringOps.count(main, "nX");
		org.junit.Assert.assertEquals(1, cnt);
		main = main.replace("X", "XX");
		cnt = StringOps.count(main, "XX");
		org.junit.Assert.assertEquals(5, cnt);
		main = "XXthisXXisXXconsecutiveXXsinglecharXXpatternXX";
		cnt = StringOps.count(main, "X");
		org.junit.Assert.assertEquals(12, cnt);
		main = main.replace("XX", "abab");
		cnt = StringOps.count(main, "ab");
		org.junit.Assert.assertEquals(12, cnt);
		cnt = StringOps.count(main, "_");
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("", "_");
		org.junit.Assert.assertEquals(0, cnt);

		main = "z before zz and one at the endz";
		cnt = StringOps.count(main, 'z');
		org.junit.Assert.assertEquals(4, cnt);
		cnt = StringOps.count(main, 1, main.length()-1, 'z');
		org.junit.Assert.assertEquals(3, cnt);
		cnt = StringOps.count(main, 1, main.length()-1, 'z');
		org.junit.Assert.assertEquals(3, cnt);
		cnt = StringOps.count(main, (char)0);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count(null, 'z');
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("", 'z');
		org.junit.Assert.assertEquals(0, cnt);

		cnt = StringOps.count("", "");
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("", "a");
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("a", "");
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count(null, null);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count(null, "a");
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("a", null);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("aaa", 0, 0, 'a');
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("", 0, 0, 'a');
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count(null, 0, 0, 'a');
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("aaa", (char)0);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = StringOps.count("aaa", 'a');
		org.junit.Assert.assertEquals(3, cnt);
	}

	@org.junit.Test
	public void testStripQuotes()
	{
		char[] quotechars = {StringOps.DFLT_QUOTE, 'x'};
		String[] words = {"", "A", "hello"};

		for (int idx = 0; idx != words.length; idx++)
		{
			String word = words[idx];
			for (int idx2 = 0; idx2 != quotechars.length; idx2++)
			{
				char quote = quotechars[idx2];
				String soloquote = new String(new char[]{quote});
				String unquoted = word;
				String quoted = soloquote + word + soloquote;
				String leadquote = soloquote + word;
				String trailquote = word + soloquote;
				String emptyquotes = soloquote + soloquote;

				if (quote == StringOps.DFLT_QUOTE)
				{
					org.junit.Assert.assertTrue(StringOps.stripQuotes(null) == null);
					org.junit.Assert.assertTrue(StringOps.stripQuotes(unquoted) == unquoted);
					org.junit.Assert.assertTrue(StringOps.stripQuotes(leadquote) == leadquote);
					org.junit.Assert.assertTrue(StringOps.stripQuotes(trailquote) == trailquote);
					org.junit.Assert.assertEquals(unquoted, StringOps.stripQuotes(quoted));
					org.junit.Assert.assertEquals(0, StringOps.stripQuotes(emptyquotes).length());
					org.junit.Assert.assertTrue(StringOps.stripQuotes(soloquote) == soloquote);
				}
				else
				{
					org.junit.Assert.assertTrue(StringOps.stripQuotes(null, quote) == null);
					org.junit.Assert.assertTrue(StringOps.stripQuotes(unquoted, quote) == unquoted);
					org.junit.Assert.assertTrue(StringOps.stripQuotes(leadquote, quote) == leadquote);
					org.junit.Assert.assertTrue(StringOps.stripQuotes(trailquote, quote) == trailquote);
					org.junit.Assert.assertEquals(unquoted, StringOps.stripQuotes(quoted, quote));
					org.junit.Assert.assertEquals(0, StringOps.stripQuotes(emptyquotes, quote).length());
					org.junit.Assert.assertTrue(StringOps.stripQuotes(soloquote, quote) == soloquote);
				}
			}
		}
	}

	@org.junit.Test
	public void testLeadingChars()
	{
		String blank = "";
		String unchanged1 = "A";
		String unchanged = "ABC";
		org.junit.Assert.assertTrue(StringOps.leadingChars(null, 0) == null);
		org.junit.Assert.assertTrue(StringOps.leadingChars(null, 1) == null);
		org.junit.Assert.assertTrue(StringOps.leadingChars(blank, 0) == blank);
		org.junit.Assert.assertTrue(StringOps.leadingChars(blank, 1) == blank);
		org.junit.Assert.assertTrue(StringOps.leadingChars(unchanged1, 0) == unchanged1);
		org.junit.Assert.assertTrue(StringOps.leadingChars(unchanged1, 1) == unchanged1);
		org.junit.Assert.assertTrue(StringOps.leadingChars(unchanged1, 2) == unchanged1);
		org.junit.Assert.assertTrue(StringOps.leadingChars(unchanged, 0) == unchanged);
		org.junit.Assert.assertTrue(StringOps.leadingChars(unchanged, 3) == unchanged);
		org.junit.Assert.assertTrue(StringOps.leadingChars(unchanged, 4) == unchanged);
		org.junit.Assert.assertEquals("A", StringOps.leadingChars(unchanged, 1));
		org.junit.Assert.assertEquals("AB", StringOps.leadingChars(unchanged, 2));
	}

	//must exactly mirror the above method
	@org.junit.Test
	public void testTrailingChars()
	{
		String blank = "";
		String unchanged1 = "A";
		String unchanged = "ABC";
		org.junit.Assert.assertTrue(StringOps.trailingChars(null, 0) == null);
		org.junit.Assert.assertTrue(StringOps.trailingChars(null, 1) == null);
		org.junit.Assert.assertTrue(StringOps.trailingChars(blank, 0) == blank);
		org.junit.Assert.assertTrue(StringOps.trailingChars(blank, 1) == blank);

		org.junit.Assert.assertTrue(StringOps.trailingChars(unchanged1, 0) == unchanged1);
		org.junit.Assert.assertTrue(StringOps.trailingChars(unchanged1, 1) == unchanged1);
		org.junit.Assert.assertTrue(StringOps.trailingChars(unchanged1, 2) == unchanged1);

		org.junit.Assert.assertTrue(StringOps.trailingChars(unchanged, 0) == unchanged);
		org.junit.Assert.assertTrue(StringOps.trailingChars(unchanged, 3) == unchanged);
		org.junit.Assert.assertTrue(StringOps.trailingChars(unchanged, 4) == unchanged);
		org.junit.Assert.assertEquals("C", StringOps.trailingChars(unchanged, 1));
		org.junit.Assert.assertEquals("BC", StringOps.trailingChars(unchanged, 2));
	}

	@org.junit.Test
	public void testKeepLeadingParts()
	{
		String dlm = ".";
		String str = "one.two.three";
		String newstr = StringOps.keepLeadingParts(str, dlm, 0);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepLeadingParts(str, dlm, 1);
		org.junit.Assert.assertEquals("one", newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 2);
		org.junit.Assert.assertEquals("one.two", newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 3);
		org.junit.Assert.assertTrue(str == newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 4);
		org.junit.Assert.assertTrue(str == newstr);

		str = ".one...two.";
		newstr = StringOps.keepLeadingParts(str, dlm, 0);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepLeadingParts(str, dlm, 1);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepLeadingParts(str, dlm, 2);
		org.junit.Assert.assertEquals(".one", newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 3);
		org.junit.Assert.assertEquals(".one.", newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 4);
		org.junit.Assert.assertEquals(".one..", newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 5);
		org.junit.Assert.assertEquals(".one...two", newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 6);
		org.junit.Assert.assertTrue(str == newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 7);
		org.junit.Assert.assertTrue(str == newstr);
		newstr = StringOps.keepLeadingParts(str, dlm, 8);
		org.junit.Assert.assertTrue(str == newstr);

		str = ".";
		newstr = StringOps.keepLeadingParts(str, dlm, 0);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepLeadingParts(str, dlm, 1);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepLeadingParts(str, dlm, 2);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.keepLeadingParts(str, dlm, 3);
		org.junit.Assert.assertTrue(newstr == str);
	}

	@org.junit.Test
	public void testStripLeadingParts()
	{
		String dlm = ".";
		String str = "one.two.three";
		String newstr = StringOps.stripLeadingParts(str, dlm, 0);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.stripLeadingParts(str, dlm, 1);
		org.junit.Assert.assertEquals("two.three", newstr);
		newstr = StringOps.stripLeadingParts(str, dlm, 2);
		org.junit.Assert.assertEquals("three", newstr);
		newstr = StringOps.stripLeadingParts(str, dlm, 3);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripLeadingParts(str, dlm, 4);
		org.junit.Assert.assertEquals(0, newstr.length());

		str = ".one...two.";
		newstr = StringOps.stripLeadingParts(str, dlm, 0);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.stripLeadingParts(str, dlm, 1);
		org.junit.Assert.assertEquals("one...two.", newstr);
		newstr = StringOps.stripLeadingParts(str, dlm, 2);
		org.junit.Assert.assertEquals("..two.", newstr);
		newstr = StringOps.stripLeadingParts(str, dlm, 3);
		org.junit.Assert.assertEquals(".two.", newstr);
		newstr = StringOps.stripLeadingParts(str, dlm, 4);
		org.junit.Assert.assertEquals("two.", newstr);
		newstr = StringOps.stripLeadingParts(str, dlm, 5);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripLeadingParts(str, dlm, 6);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripLeadingParts(str, dlm, 7);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripLeadingParts(str, dlm, 8);
		org.junit.Assert.assertEquals(0, newstr.length());

		str = ".";
		newstr = StringOps.stripLeadingParts(str, dlm, 0);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.stripLeadingParts(str, dlm, 1);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripLeadingParts(str, dlm, 2);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripLeadingParts(str, dlm, 3);
		org.junit.Assert.assertEquals(0, newstr.length());
	}

	@org.junit.Test
	public void testKeepTrailingParts()
	{
		String dlm = ".";
		String str = "one.two.three";
		String newstr = StringOps.keepTrailingParts(str, dlm, 0);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepTrailingParts(str, dlm, 1);
		org.junit.Assert.assertEquals("three", newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 2);
		org.junit.Assert.assertEquals("two.three", newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 3);
		org.junit.Assert.assertTrue(str == newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 4);
		org.junit.Assert.assertTrue(str == newstr);

		str = ".one...two.";
		newstr = StringOps.keepTrailingParts(str, dlm, 0);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepTrailingParts(str, dlm, 1);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepTrailingParts(str, dlm, 2);
		org.junit.Assert.assertEquals("two.", newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 3);
		org.junit.Assert.assertEquals(".two.", newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 4);
		org.junit.Assert.assertEquals("..two.", newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 5);
		org.junit.Assert.assertEquals("one...two.", newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 6);
		org.junit.Assert.assertTrue(str == newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 7);
		org.junit.Assert.assertTrue(str == newstr);
		newstr = StringOps.keepTrailingParts(str, dlm, 8);
		org.junit.Assert.assertTrue(str == newstr);

		str = ".";
		newstr = StringOps.keepTrailingParts(str, dlm, 0);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepTrailingParts(str, dlm, 1);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.keepTrailingParts(str, dlm, 2);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.keepTrailingParts(str, dlm, 3);
		org.junit.Assert.assertTrue(newstr == str);
	}

	@org.junit.Test
	public void testStripTrailingParts()
	{
		String dlm = ".";
		String str = "one.two.three";
		String newstr = StringOps.stripTrailingParts(str, dlm, 0);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.stripTrailingParts(str, dlm, 1);
		org.junit.Assert.assertEquals("one.two", newstr);
		newstr = StringOps.stripTrailingParts(str, dlm, 2);
		org.junit.Assert.assertEquals("one", newstr);
		newstr = StringOps.stripTrailingParts(str, dlm, 3);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripTrailingParts(str, dlm, 4);
		org.junit.Assert.assertEquals(0, newstr.length());

		str = "..one...two.";
		newstr = StringOps.stripTrailingParts(str, dlm, 0);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.stripTrailingParts(str, dlm, 1);
		org.junit.Assert.assertEquals("..one...two", newstr);
		newstr = StringOps.stripTrailingParts(str, dlm, 2);
		org.junit.Assert.assertEquals("..one..", newstr);
		newstr = StringOps.stripTrailingParts(str, dlm, 3);
		org.junit.Assert.assertEquals("..one.", newstr);
		newstr = StringOps.stripTrailingParts(str, dlm, 4);
		org.junit.Assert.assertEquals("..one", newstr);
		newstr = StringOps.stripTrailingParts(str, dlm, 5);
		org.junit.Assert.assertEquals(".", newstr);
		newstr = StringOps.stripTrailingParts(str, dlm, 6);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripTrailingParts(str, dlm, 7);
		org.junit.Assert.assertEquals(0, newstr.length());

		str = ".";
		newstr = StringOps.stripTrailingParts(str, dlm, 0);
		org.junit.Assert.assertTrue(newstr == str);
		newstr = StringOps.stripTrailingParts(str, dlm, 1);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripTrailingParts(str, dlm, 2);
		org.junit.Assert.assertEquals(0, newstr.length());
		newstr = StringOps.stripTrailingParts(str, dlm, 3);
		org.junit.Assert.assertEquals(0, newstr.length());
	}

	@org.junit.Test
	public void testFill()
	{
		org.junit.Assert.assertEquals(0, '\0'); //sanity test to make sure I've got the notation for the NUL char right
		org.junit.Assert.assertEquals(0, StringOps.fill('\0', 0).length());
		org.junit.Assert.assertEquals(2, StringOps.fill('x', 2).length());
		org.junit.Assert.assertEquals(0, StringOps.fill('x', 0).length());
		org.junit.Assert.assertEquals("x", StringOps.fill('x', 1));
		org.junit.Assert.assertEquals("xx", StringOps.fill('x', 2));
	}

	@org.junit.Test
	public void testFlatten()
	{
		org.junit.Assert.assertEquals("NULL", StringOps.flatten(null, 0));
		org.junit.Assert.assertEquals("NULL", StringOps.flatten(null, 1));
		org.junit.Assert.assertEquals("NULL", StringOps.flatten(null, "NULL".length()));
		org.junit.Assert.assertEquals("NULL", StringOps.flatten(null, "NULL".length()+1));

		org.junit.Assert.assertEquals("Abcde", StringOps.flatten("Abcde", 0));
		org.junit.Assert.assertEquals("A", StringOps.flatten("Abcde", 1));
		org.junit.Assert.assertEquals("Abcde", StringOps.flatten("Abcde", "Abcde".length()));
		org.junit.Assert.assertEquals("Abcde", StringOps.flatten("Abcde", "Abcde".length()+1));

		String str = "ABC\r\nXYZ\n123\t789";
		org.junit.Assert.assertEquals("ABC XYZ 123 789", StringOps.flatten(str, 0));
		org.junit.Assert.assertEquals("ABC XYZ 123 78", StringOps.flatten(str, str.length()-2));
		str = "ABC"+StringOps.fill(' ', 2)+"XYZ"+StringOps.fill(' ', 3)+"123"+StringOps.fill(' ', 4)+"789";
		org.junit.Assert.assertEquals("ABC XYZ 123 789", StringOps.flatten(str, 0));
		org.junit.Assert.assertEquals("ABC XYZ 123 789", StringOps.flatten(str, str.length()-6));
		org.junit.Assert.assertEquals("ABC XYZ 123 78", StringOps.flatten(str, str.length()-7));
	}

	@org.junit.Test
	public void testConvertBytes() throws java.io.UnsupportedEncodingException
	{
		String txt = "Hello";
		byte[] buf = txt.getBytes(StringOps.DFLT_CHARSET);

		org.junit.Assert.assertNull(StringOps.convert(null, null));
		org.junit.Assert.assertNull(StringOps.convert(null, StringOps.DFLT_CHARSET));
		org.junit.Assert.assertNull(StringOps.convert(null, "ISO-8859-3"));
		org.junit.Assert.assertNull(StringOps.convert(null, "no such charset"));

		org.junit.Assert.assertEquals(txt, StringOps.convert(buf, null));
		org.junit.Assert.assertEquals(txt, StringOps.convert(buf, StringOps.DFLT_CHARSET));
		org.junit.Assert.assertEquals(txt, StringOps.convert(buf, "ISO-8859-3"));
		try {
			org.junit.Assert.assertEquals(txt, StringOps.convert(buf, "no such charset"));
			org.junit.Assert.fail("Write didn't fail as expected on bad charset");
		} catch (java.io.UnsupportedEncodingException ex) {
			//ok - expected
		}
	}

	@org.junit.Test
	public void testZeroPad()
	{
		StringBuilder buf = new StringBuilder();
		StringOps.zeroPad(buf, 1, 2);
		String str = buf.toString();
		org.junit.Assert.assertTrue(str.equals("01"));

		buf.setLength(0);
		StringOps.zeroPad(buf, 1, 1);
		str = buf.toString();
		org.junit.Assert.assertTrue(str.equals("1"));

		buf.setLength(0);
		StringOps.zeroPad(buf, 11, 2);
		str = buf.toString();
		org.junit.Assert.assertTrue(str.equals("11"));

		buf.setLength(0);
		StringOps.zeroPad(buf, 99, 1);
		str = buf.toString();
		org.junit.Assert.assertTrue(str.equals("99"));

		buf.setLength(0);
		StringOps.zeroPad(buf, 99, 4);
		str = buf.toString();
		org.junit.Assert.assertTrue(str.equals("0099"));

		buf.setLength(0);
		StringOps.zeroPad(buf, 1000000000, 11);
		str = buf.toString();
		org.junit.Assert.assertTrue(str.equals("01000000000"));
	}

	@org.junit.Test
	public void testDigits()
	{
		org.junit.Assert.assertEquals(1, StringOps.digits(0));
		org.junit.Assert.assertEquals(1, StringOps.digits(9));
		org.junit.Assert.assertEquals(2, StringOps.digits(10));
		org.junit.Assert.assertEquals(2, StringOps.digits(99));
		org.junit.Assert.assertEquals(3, StringOps.digits(100));
		org.junit.Assert.assertEquals(3, StringOps.digits(999));
		org.junit.Assert.assertEquals(4, StringOps.digits(1000));
		org.junit.Assert.assertEquals(4, StringOps.digits(9999));
		org.junit.Assert.assertEquals(5, StringOps.digits(10000));
		org.junit.Assert.assertEquals(5, StringOps.digits(99999));
		org.junit.Assert.assertEquals(6, StringOps.digits(100000));
		org.junit.Assert.assertEquals(6, StringOps.digits(999999));
		org.junit.Assert.assertEquals(7, StringOps.digits(1000000));
		org.junit.Assert.assertEquals(7, StringOps.digits(9999999));
		org.junit.Assert.assertEquals(8, StringOps.digits(10000000));
		org.junit.Assert.assertEquals(8, StringOps.digits(99999999));
		org.junit.Assert.assertEquals(9, StringOps.digits(100000000));
		org.junit.Assert.assertEquals(9, StringOps.digits(999999999));
		org.junit.Assert.assertEquals(10, StringOps.digits(1000000000));
		org.junit.Assert.assertEquals(10, StringOps.digits(Integer.MAX_VALUE));
	}

	@org.junit.Test
	public void testNumbers()
	{
		// positive decimal
		org.junit.Assert.assertEquals(23, (int)StringOps.parseNumber("23", 10));
		org.junit.Assert.assertEquals(23, (int)StringOps.parseNumber("+23", 10));
		// negative
		org.junit.Assert.assertEquals(-23, (int)StringOps.parseNumber("-23", 10));

		// with surrounding text
		long numval = 37;
		StringBuilder sb = new StringBuilder();
		sb.append("blah");
		int off = sb.length();
		sb.append(numval);
		int off2 = sb.length();
		sb.append("more text");
		int numval2 = (int)StringOps.parseNumber(sb, off, off2 - off, 10);
		org.junit.Assert.assertEquals(numval, numval2);

		// hex number
		sb.setLength(0);
		sb.append(Long.toHexString(numval));
		numval2 = (int)StringOps.parseNumber(sb, 16);
		org.junit.Assert.assertEquals(numval, numval2);
		org.junit.Assert.assertEquals(10, (int)StringOps.parseNumber("A", 16));
		org.junit.Assert.assertEquals(10, (int)StringOps.parseNumber("a", 16));
		org.junit.Assert.assertEquals(10, (int)StringOps.parseNumber("+a", 16));
		org.junit.Assert.assertEquals(-10, (int)StringOps.parseNumber("-a", 16));

		// invalid number
		String str = "9a";
		try {
			numval2 = (int)StringOps.parseNumber(str, 10);
			org.junit.Assert.fail("parseNumber failed to reject invalid number: -"+str+" - returned "+numval2);
		} catch (NumberFormatException ex) {} //expected error - gets thrown for any invalid char

		org.junit.Assert.assertEquals(0, StringOps.parseNumber(null, 10));
		org.junit.Assert.assertEquals(0, StringOps.parseNumber("", 10));
		org.junit.Assert.assertEquals(0, StringOps.parseNumber("-", 10));
		org.junit.Assert.assertEquals(0, StringOps.parseNumber("+", 10));
		org.junit.Assert.assertEquals(0, StringOps.parseNumber(null, 16));
		org.junit.Assert.assertEquals(0, StringOps.parseNumber("", 16));
		org.junit.Assert.assertEquals(0, StringOps.parseNumber("-", 16));
		org.junit.Assert.assertEquals(0, StringOps.parseNumber("+", 16));
	}

	// We always build the test string via StringBuilder rather than assigning to a literal
	// or using the String(literal) constructor, to avoid modifying the literal.
	@org.junit.Test
	public void testErase() throws Exception {
		org.junit.Assume.assumeTrue(StringOps.erase(new StringBuilder("dummy").toString()));
		// test boundary condition of 1-char string
		StringBuilder sb = new StringBuilder("A");
		String str = new String(sb);
		int len = str.length();
		StringOps.erase(str);
		org.junit.Assert.assertFalse(str.equals(new String(sb)));
		org.junit.Assert.assertEquals(len, str.length());
		char mask = str.charAt(0);
		// test boundary condition of blank string
		str = new String(new StringBuilder(""));
		StringOps.erase(str);
		org.junit.Assert.assertEquals(0, str.length());
		// test longer String
		str = new String(new StringBuilder("ABCDE"));
		len = str.length();
		StringOps.erase(str);
		String masked = StringOps.fill(mask, len);
		org.junit.Assert.assertEquals(masked, str);
		// with non-zero offset
		str = new String(new StringBuilder("ABCDE"));
		str = str.substring(2, str.length() - 1);
		len = str.length();
		StringOps.erase(str);
		masked = StringOps.fill(mask, len);
		org.junit.Assert.assertEquals(masked, str);
	}
}
