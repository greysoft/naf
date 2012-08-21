/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

public class Ascii
{
	public enum ARMOURTYPE {HEX, BASE64}

	private static final String EOL = "\r\n";  // Generate Windows/Internet linebreak, as Notepad will not properly copy/paste LineFeed breaks
	private static final String EOL_ALT = "\n";
	private static final String BEGINTEXT = "====== *****"+" Begin Grey Text "+"***** =======";
	private static final String ENDTEXT = "====== *****"+" End Grey Text "+"***** =========";
	private static final String ARMOUR_BEGIN = EOL+BEGINTEXT+EOL;
	private static final String ARMOUR_END =   EOL+ENDTEXT+EOL;
	private static final String ARMOUR_BEGIN_LF = EOL_ALT+BEGINTEXT+EOL_ALT;  // Tolerate Unix-style LineFeed breaks on incoming data
	private static final String ARMOUR_END_LF =   EOL_ALT+ENDTEXT+EOL_ALT;
	private static final char[] ARMOURSPC_BEGIN = (EOL+EOL+ARMOUR_BEGIN).toCharArray();  //add blank lines for easy visibility
	private static final char[] ARMOURSPC_END = (ARMOUR_END+EOL+EOL+EOL).toCharArray();
	private static final int ARMOUR_LINESIZE = 40;

	private static final char[] hexchars = "0123456789ABCDEF".toCharArray();


	public static byte[] decrypt(String wrapdata, java.math.BigInteger kmod, java.math.BigInteger kpub, ARMOURTYPE atype)
			throws java.security.GeneralSecurityException
	{
		java.security.Key pubkey = com.grey.base.crypto.AsyKey.buildPublicKey(kmod, kpub);
		byte[] encdata = com.grey.base.crypto.Ascii.armourUnwrap(wrapdata, atype);
		byte[] plaindata = com.grey.base.crypto.AsyKey.decryptData(pubkey, encdata, 0, encdata.length);
		return plaindata;
	}

	public static char[] armourWrap(byte[] rawdata, int off, int len, ARMOURTYPE atype)
	{
		char[] cdata;

		switch (atype)
		{
		case BASE64:
			cdata = Base64.encode(rawdata, off, len, ARMOUR_LINESIZE, null);
			break;
		case HEX:
			cdata = hexEncode(rawdata, off, len, null);
			break;
		default:
			throw new RuntimeException("Missing case for armourtype="+atype);
		}
		return armourWrap(cdata, 0, cdata.length);
	}

	private static char[] armourWrap(char[] rawdata, int off, int len)
	{
		char[] wrapdata = new char[ARMOURSPC_BEGIN.length+ARMOURSPC_END.length+len];
		int dst_off = 0;
		System.arraycopy(ARMOURSPC_BEGIN, 0, wrapdata, dst_off, ARMOURSPC_BEGIN.length);
		dst_off += ARMOURSPC_BEGIN.length;
		System.arraycopy(rawdata, off, wrapdata, dst_off, len);
		dst_off += len;
		System.arraycopy(ARMOURSPC_END, 0, wrapdata, dst_off, ARMOURSPC_END.length);
		return wrapdata;
	}

	public static byte[] armourUnwrap(char[] wrapdata, int off, int len, ARMOURTYPE atype)
	{
		return armourUnwrap(new String(wrapdata, off, len), atype);
	}

	public static byte[] armourUnwrap(String wrapdata, ARMOURTYPE atype)
	{
		String cstr = removeArmour(wrapdata);
		if (cstr == null) return null;
		char[] cdata = cstr.toCharArray();
		byte[] rawdata;

		switch (atype)
		{
		case BASE64:
			rawdata = Base64.decode(cdata, 0, cdata.length, null);
			break;
		case HEX:
			rawdata = hexDecode(cdata, 0, cdata.length, null);
			break;
		default:
			throw new RuntimeException("Missing case for armourtype="+atype);
		}
		return rawdata;
	}

	private static String removeArmour(String wrapdata)
	{
		if (wrapdata == null) return null;
		String begintxt = ARMOUR_BEGIN;
		int off1 = wrapdata.indexOf(begintxt);
		if (off1 == -1)
		{
			// try LineFeed linebreaks
			begintxt = ARMOUR_BEGIN_LF;
			off1 = wrapdata.indexOf(begintxt);
			if (off1 == -1) return null;
		}
		String endtxt = ARMOUR_END;
		int off2 = wrapdata.lastIndexOf(endtxt);
		if (off2 == -1)
		{
			endtxt = ARMOUR_END_LF;
			off2 = wrapdata.lastIndexOf(endtxt);
			if (off2 == -1) return null;
		}
		String rawdata = wrapdata.substring(off1 + begintxt.length(), off2);
		return rawdata;
	}


	public static char[] hexEncode(byte[] barr)
	{
		return hexEncode(barr, 0, barr.length, null);
	}

	public static byte[] hexDecode(char[] carr)
	{
		return hexDecode(carr, 0, carr.length, null);
	}

	public static char[] hexEncode(byte[] barr, int boff, int blen, char[] carr)
	{
		int clen = blen << 1;  //fast multiply-by-2
		int blmt = boff + blen;
		int coff = 0;
		if (carr == null) carr = new char[clen];

		for (int idx = boff; idx != blmt; idx++)
		{
			carr[coff++] = hexchars[(barr[idx] >> 4) & 0xf];
			carr[coff++] = hexchars[barr[idx] & 0xf];
		}
		return carr;
	}

	public static byte[] hexDecode(char[] carr, int coff, int clen, byte[] barr)
	{
		int blen = clen >> 1;  //fast divide-by-2
		if (barr == null) barr = new byte[blen];

		for (int idx = 0; idx != blen; idx++)
		{
			barr[idx] = (byte)(hexCharValue(carr[coff++]) << 4);
			barr[idx] += hexCharValue(carr[coff++]);
		}
		return barr;
	}

	private static byte hexCharValue(char ch)
	{
		int bval;

		if (ch > '9')
		{
			bval = Character.toUpperCase(ch) - 'A' + 10;
		}
		else
		{
			bval = ch - '0';
		}
		return (byte)bval;
	}
}
