/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

import com.grey.base.config.SysProps;

public class Base64
{
	private static final int DFLT_LINESIZE = SysProps.get("grey.crypto.base64.maxline", 76); //corresponds to the standard MIME limit
	private static final int UNITBYTES = 3;  // number of bytes per encoding unit
	private static final int UNITCHARS = 4;  // number of characters per encoding unit
	private static final char PADCHAR = '=';
	private static final char CRLF1 = '\r';  // use the standard Internet line-break
	private static final char CRLF2 = '\n';
	private static final int DFLT_LINEUNITS = DFLT_LINESIZE / UNITCHARS;
	private static final int MASK6 = 0x3f;   // mask for lower 6 bits of an int
	private static final byte[] EMPTYBYTES = new byte[0];

	private static StringBuilder tmpstrbuf = new StringBuilder(64);
	static {
		for (char ch = 'A'; ch <= 'Z'; ch++) tmpstrbuf.append(ch);
		for (char ch = 'a'; ch <= 'z'; ch++) tmpstrbuf.append(ch);
		for (char ch = '0'; ch <= '9'; ch++) tmpstrbuf.append(ch);
		tmpstrbuf.append("+/");
	}
	private static final char[] alph64 = tmpstrbuf.toString().toCharArray();
	private static final int[] map64 = new int[256];

	static {
		// this leaves map64[PADCHAR] at its initialised value of zero
		for (int idx = 0; idx != alph64.length; idx++) map64[alph64[idx]] = idx;
	}


	public static char[] encode(byte[] rawdata)
	{
		return encode(rawdata, 0, rawdata==null? 0 : rawdata.length, 0, null);
	}

	public static byte[] decode(char[] encdata)
	{
		return decode(encdata, 0, encdata==null? 0 : encdata.length, null);
	}

	// maxline=-1 means no limit on line length (ie. add no line-breaks), while zero means use the standard MIME limit
	public static char[] encode(byte[] rawdata, int rawoff, int rawlen, int maxline, com.grey.base.utils.CharBlock arrh)
	{
		if (rawlen == 0) return null;
		int encoff = 0;
		int line_maxunits = maxline;

		if (line_maxunits == 0)
		{
			maxline = DFLT_LINESIZE;
			line_maxunits = DFLT_LINEUNITS;
		}
		else if (line_maxunits != -1)
		{
			line_maxunits = maxline / UNITCHARS;
			maxline = line_maxunits * UNITCHARS;  // round line-size down to a valid multiple of units, in case it wasn't
		}
		int paddinglen = rawlen % UNITBYTES;
		if (paddinglen != 0) paddinglen = UNITBYTES - paddinglen;
		int enclen = ((rawlen + paddinglen) / UNITBYTES) * UNITCHARS;
		if (maxline != -1) enclen += (((enclen - 1) / maxline) * 2);  //add space for line-breaks (x2 because it consists of 2 chars - CRLF)
		char[] encdata;

		if (arrh != null)
		{
			arrh.ensureCapacity(arrh.size() + enclen);
			encdata = arrh.buffer();
			encoff = arrh.limit();
			arrh.incrementSize(enclen);
		}
		else
		{
			encdata = new char[enclen];
		}
		int line_units = 0;  // number of encoding units processed so far on current line
		int rawlimit = rawoff + rawlen;
		int ridx = rawoff;
		int eidx = encoff;

		while (ridx != rawlimit)
		{
			int b1 = rawdata[ridx++] & 0xff;  //1st byte of current encoding unit is known to exist, but next 2 might be virtual pad bytes
			int b2 = (ridx == rawlimit ? 0 : rawdata[ridx++] & 0xff);
			int b3 = (ridx == rawlimit ? 0 : rawdata[ridx++] & 0xff);
			int unitval = (b1 << 16) | (b2 << 8) | b3;
			encdata[eidx++] = alph64[(unitval >> 18) & MASK6];
			encdata[eidx++] = alph64[(unitval >> 12) & MASK6];
			encdata[eidx++] = alph64[(unitval >> 6) & MASK6];
			encdata[eidx++] = alph64[unitval & MASK6];

			if (++line_units == line_maxunits && ridx != rawlimit)
			{
				encdata[eidx++] = CRLF1;
				encdata[eidx++] = CRLF2;
				line_units = 0;
			}
		}

		if (paddinglen != 0)
		{
			// number of padding chars is equal to number of padding bytes
			int lmt = encoff + enclen;
			for (int idx = lmt - paddinglen; idx != lmt; idx++)
			{
				encdata[idx] = PADCHAR;
			}
		}
		return encdata;
	}

	// We expect the encoded data to be a legal Base64 sequence.
	// That means illegal chars - the only non-alphabetic chars that may exist in 'encdata' are linebreaks and padding chars.
	// It also means that 'enclen' must be a legal size, ie. an integer multiple of UNITCHARS.
	// The exception to the strict syntax requirement, is that we do allow leading and trailing whitespace, which we strip,
	// and we also allow linebreaks to consist of Unix-style solitary LineFeeds, as well as CRLF.
	public static byte[] decode(char[] encdata, int encoff, int enclen, com.grey.base.utils.ByteChars arrh)
	{
		int enclimit = encoff + enclen;
		while (encoff != enclimit && encdata[encoff] <= 32) encoff++;  //strip leading whitespace
		while (encoff != enclimit && encdata[enclimit - 1] <= 32) enclimit--;  //strip trailing whitespace
		enclen = enclimit - encoff;
		if (enclen == 0) return (encdata == null ? null : EMPTYBYTES);
		int rawoff = 0;
		int datalimit = enclimit;
		int paddinglen = 0;
		int eolchars = 0;
		int whtspc = 0;

		// count pad chars
		while (encdata[datalimit - 1] == PADCHAR)
		{
			paddinglen++;
			datalimit--;
			if (datalimit == encoff) return EMPTYBYTES;
		}

		// count the number of line breaks
		for (int idx = encoff; idx != datalimit; idx++)
		{
			if (encdata[idx] == CRLF1 || encdata[idx] == CRLF2) {
				eolchars++;
			} else if (encdata[idx] == ' ' || encdata[idx] == '\t') {
				//assume this is white space at start or end of line - not really allowed but be tolerant
				whtspc++;
			}
		}

		// now we can work out the size of the final decoded byte buffer
		int rawlen = (((enclen - eolchars - whtspc) / UNITCHARS) * UNITBYTES) - paddinglen;
		byte[] rawdata;

		if (arrh != null)
		{
			arrh.ensureCapacity(arrh.size() + rawlen);
			rawdata = arrh.buffer();
			rawoff = arrh.limit();
			arrh.incrementSize(rawlen);
		}
		else
		{
			rawdata = new byte[rawlen];
		}
		int rawlimit = rawoff + rawlen;
		int ridx = rawoff;
		int eidx = encoff;

		while (eidx != enclimit)
		{
			if (encdata[eidx] == CRLF1)
			{
				eidx += 2;
				continue;
			}
			if (encdata[eidx] == CRLF2 || encdata[eidx] == ' ' || encdata[eidx] == '\t')
			{
				eidx++;
				continue;
			}
			// no need to test for PADCHAR as a special case, as it's map64 slot already returns zero
			int c1 = map64[encdata[eidx++]];
			int c2 = map64[encdata[eidx++]];
			int c3 = map64[encdata[eidx++]];
			int c4 = map64[encdata[eidx++]];
			int unitval = (c1 << 18) | (c2 << 12) | (c3 << 6) | c4;

			// there can be at most 2 pad chars, so we'll definitely output at least one byte, but must test for next two
			rawdata[ridx++] = (byte)(unitval >> 16);
			if (ridx != rawlimit)
			{
				rawdata[ridx++] = (byte)(unitval >> 8);
				if (ridx != rawlimit) rawdata[ridx++] = (byte)(unitval);
			}
		}
		return rawdata;
	}


	/*
	 * These 2 methods are exact copies of the decode() and encode() look-alikes above, but they allows us generate and deal with
	 * Base64-encoded data as a stream of bytes if need be, as well as a stream of chars (since Base64-encoded data consists of
	 * single-byte chars anyway).
	 * 
	 * NEVER EDIT THESE METHOD DIRECTLY:
	 * To modify encodeBytes(), always work on the encode(byte[], int, int, int, CharBlock) version above, and then copy and rename it,
	 * after which we change the return type to byte[], the arrh parameter's type to ByteChars, and then the type of then encdata local
	 * variable from char[] to byte[]. You also need to cast the encdata[eidx] assignments in the main loop to (byte), but the compiler
	 * or IDE will warn you about that anyway.
	 * To modify decodeBytes(), always work on the decode(char[], int, int, ByteChars) version above, and then copy and rename it,
	 * and and simply change the type of the encdata parameter to byte[].
	 */
	public static byte[] encodeBytes(byte[] rawdata, int rawoff, int rawlen, int maxline, com.grey.base.utils.ByteChars arrh)
	{
		if (rawlen == 0) return null;
		int encoff = 0;
		int line_maxunits = maxline;

		if (line_maxunits == 0)
		{
			maxline = DFLT_LINESIZE;
			line_maxunits = DFLT_LINEUNITS;
		}
		else if (line_maxunits != -1)
		{
			line_maxunits = maxline / UNITCHARS;
			maxline = line_maxunits * UNITCHARS;  // round line-size down to a valid multiple of units, in case it wasn't
		}
		int paddinglen = rawlen % UNITBYTES;
		if (paddinglen != 0) paddinglen = UNITBYTES - paddinglen;
		int enclen = ((rawlen + paddinglen) / UNITBYTES) * UNITCHARS;
		if (maxline != -1) enclen += (((enclen - 1) / maxline) * 2);  //add space for line-breaks (x2 because it consists of 2 chars - CRLF)
		byte[] encdata;

		if (arrh != null)
		{
			arrh.ensureCapacity(arrh.size() + enclen);
			encdata = arrh.buffer();
			encoff = arrh.limit();
			arrh.incrementSize(enclen);
		}
		else
		{
			encdata = new byte[enclen];
		}
		int line_units = 0;  // number of encoding units processed so far on current line
		int rawlimit = rawoff + rawlen;
		int ridx = rawoff;
		int eidx = encoff;

		while (ridx != rawlimit)
		{
			int b1 = rawdata[ridx++] & 0xff;  //1st byte of current encoding unit is known to exist, but next 2 might be virtual pad bytes
			int b2 = (ridx == rawlimit ? 0 : rawdata[ridx++] & 0xff);
			int b3 = (ridx == rawlimit ? 0 : rawdata[ridx++] & 0xff);
			int unitval = (b1 << 16) | (b2 << 8) | b3;
			encdata[eidx++] = (byte)alph64[(unitval >> 18) & MASK6];
			encdata[eidx++] = (byte)alph64[(unitval >> 12) & MASK6];
			encdata[eidx++] = (byte)alph64[(unitval >> 6) & MASK6];
			encdata[eidx++] = (byte)alph64[unitval & MASK6];

			if (++line_units == line_maxunits && ridx != rawlimit)
			{
				encdata[eidx++] = CRLF1;
				encdata[eidx++] = CRLF2;
				line_units = 0;
			}
		}

		if (paddinglen != 0)
		{
			// number of padding chars is equal to number of padding bytes
			int lmt = encoff + enclen;
			for (int idx = lmt - paddinglen; idx != lmt; idx++)
			{
				encdata[idx] = PADCHAR;
			}
		}
		return encdata;
	}
	public static byte[] decodeBytes(byte[] encdata, int encoff, int enclen, com.grey.base.utils.ByteChars arrh)
	{
		if (enclen == 0) return null;
		int enclimit = encoff + enclen;
		int rawoff = 0;
		int datalimit = enclimit;
		int paddinglen = 0;
		int linecnt = 0;

		// count pad chars - there's no legal encoded sequence that consists entirely of padding, so no need to test for loop termination
		while (encdata[datalimit - 1] == PADCHAR)
		{
			paddinglen++;
			datalimit--;
		}

		// count the number of line breaks
		for (int idx = encoff; idx != datalimit; idx++)
		{
			if (encdata[idx] == CRLF1)
			{
				linecnt++;
				idx++;  //skip past the following CRLF2 as well
			}
		}
		// now we can work out the size of the final decoded byte buffer
		int rawlen = (((enclen - (linecnt * 2)) / UNITCHARS) * UNITBYTES) - paddinglen;
		byte[] rawdata;

		if (arrh != null)
		{
			arrh.ensureCapacity(arrh.size() + rawlen);
			rawdata = arrh.buffer();
			rawoff = arrh.limit();
			arrh.incrementSize(rawlen);
		}
		else
		{
			rawdata = new byte[rawlen];
		}
		int rawlimit = rawoff + rawlen;
		int ridx = rawoff;
		int eidx = encoff;

		while (eidx != enclimit)
		{
			if (encdata[eidx] == CRLF1)
			{
				eidx += 2;
				continue;
			}
			// no need to test for PADCHAR as a special case, as it's map64 slot already returns zero
			int c1 = map64[encdata[eidx++]];
			int c2 = map64[encdata[eidx++]];
			int c3 = map64[encdata[eidx++]];
			int c4 = map64[encdata[eidx++]];
			int unitval = (c1 << 18) | (c2 << 12) | (c3 << 6) | c4;

			// there can be at most 2 pad chars, so we'll definitely output at least one byte, but must test for next two
			rawdata[ridx++] = (byte)(unitval >> 16);
			if (ridx != rawlimit)
			{
				rawdata[ridx++] = (byte)(unitval >> 8);
				if (ridx != rawlimit) rawdata[ridx++] = (byte)(unitval);
			}
		}
		return rawdata;
	}
}
