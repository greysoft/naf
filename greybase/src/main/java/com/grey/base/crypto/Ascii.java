/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

import com.grey.base.config.SysProps;

public class Ascii
{
	public enum ARMOURTYPE {HEX, BASE64}

	private static final String EOL = "\r\n";  //generate Windows/Internet linebreak, as Notepad will not properly copy/paste LineFeed breaks
	private static final String ARMOUR_BEGIN = SysProps.get("grey.crypto.armour.begin", "====== ***** Begin Grey Text ***** =======");
	private static final String ARMOUR_END = SysProps.get("grey.crypto.armour.end", ARMOUR_BEGIN.replace("Begin", "End"));
	private static final char[] ARMOURSPC_BEGIN = (EOL+EOL+ARMOUR_BEGIN+EOL).toCharArray();  //add blank lines for easy visibility
	private static final char[] ARMOURSPC_END = (EOL+ARMOUR_END+EOL+EOL+EOL).toCharArray();
	private static final int ARMOUR_LINESIZE = SysProps.get("grey.crypto.armour.maxline", 40);

	/* There is no universal standard on what case the 6 hexadecimal letters should be, but lower-case makes
	 * for compatibility with SASL-CRAM-MD5, among others.
	 */
	private static final char[] hexchars = "0123456789abcdef".toCharArray();


	public static byte[] decrypt(String wrapdata, java.math.BigInteger kmod, java.math.BigInteger kpub, ARMOURTYPE atype)
			throws java.security.GeneralSecurityException
	{
		java.security.Key pubkey = com.grey.base.crypto.AsyKey.buildPublicKey(kmod, kpub);
		byte[] encdata = com.grey.base.crypto.Ascii.armourUnwrap(wrapdata, atype);
		if (encdata == null) return null;
		byte[] plaindata = com.grey.base.crypto.AsyKey.decryptData(pubkey, encdata, 0, encdata.length);
		return plaindata;
	}

	public static char[] armourWrap(byte[] rawdata, int off, int len, ARMOURTYPE atype)
	{
		return armourWrap(rawdata, off, len, atype, ARMOUR_LINESIZE);
	}

	public static char[] armourWrap(byte[] rawdata, int off, int len, ARMOURTYPE atype, int maxline)
	{
		char[] cdata;
		switch (atype)
		{
		case BASE64:
			cdata = Base64.encode(rawdata, off, len, maxline, null);
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
		int off1 = findArmour(wrapdata, ARMOUR_BEGIN, 0);
		if (off1 != -1) off1 = wrapdata.indexOf('\n', off1);
		if (off1 == -1) return null;
		off1++; //points at start of next line after ARMOUR_BEGIN
		int off2 = findArmour(wrapdata, ARMOUR_END, off1);
		if (off2 != -1) off2 = wrapdata.lastIndexOf('\n', off2);
		if (off2 == -1) return null;
		if (wrapdata.charAt(--off2) == '\r') off2--; //points at end of line preceding ARMOUR_END
		if (off1 > off2) return ""; //there was nothing between the armour lines
		String rawdata = wrapdata.substring(off1, off2+1);
		return rawdata;
	}

	private static int findArmour(String wrapdata, String armour, int off) {
		int start = wrapdata.indexOf(armour, off);
		if (start == -1) return -1;
		int pos = start - 1;
		while (pos != -1 && wrapdata.charAt(pos) != '\n') {
			if (wrapdata.charAt(pos) != ' ' && wrapdata.charAt(pos) != '\t') { //was a false match
				return findArmour(wrapdata, armour, start + armour.length());
			}
			pos--;
		}
		pos = start + armour.length();
		while (pos != wrapdata.length() && wrapdata.charAt(pos) != '\n') {
			if (wrapdata.charAt(pos) != ' ' && wrapdata.charAt(pos) != '\t' && wrapdata.charAt(pos) != '\r') { //was a false match
				return findArmour(wrapdata, armour, start + armour.length());
			}
			pos++;
		}
		return start;
	}


	public static char[] digest(com.grey.base.utils.ByteChars plain, java.security.MessageDigest proc)
	{
		com.grey.base.utils.ByteChars digest = plain.digest(proc);
		return com.grey.base.crypto.Ascii.hexEncode(digest.ar_buf, digest.ar_off, digest.ar_len, null);
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
		int clen = hexEncodeLength(blen);
		int blmt = boff + blen;
		int coff = 0;
		if (carr == null || carr.length < clen) carr = new char[clen];

		for (int idx = boff; idx != blmt; idx++) {
			carr[coff++] = hexchars[(barr[idx] >> 4) & 0xf];
			carr[coff++] = hexchars[barr[idx] & 0xf];
		}
		return carr;
	}

	public static byte[] hexDecode(char[] carr, int coff, int clen, byte[] barr)
	{
		int whtspc = 0;
		int clmt = coff + clen;
		for (int idx = coff; idx != clmt; idx++)
		{
			if (carr[idx] == '\n' || carr[idx] == '\r' || carr[idx] == ' ' || carr[idx] == '\t') {
				//assume any spaces are at start or end of line
				whtspc++;
			}
		}
		int clen_hex = clen - whtspc;
		int blen = hexDecodeLength(clen_hex);
		if (barr == null || barr.length < blen) barr = new byte[blen];

		for (int idx = 0; idx != blen; idx++) {
			if (carr[coff] == '\n' || carr[coff] == '\r' || carr[coff] == ' ' || carr[coff] == '\t') continue;
			barr[idx] = (byte)(hexCharValue(carr[coff++]) << 4);
			barr[idx] += hexCharValue(carr[coff++]);
		}
		return barr;
	}

	public static int hexEncodeLength(int blen)
	{
		return (blen << 1);  //fast multiply-by-2
	}

	public static int hexDecodeLength(int clen)
	{
		return (clen >> 1);  //fast divide-by-2
	}

	private static byte hexCharValue(char ch)
	{
		int bval;

		if (ch > '9') {
			bval = Character.toUpperCase(ch) - 'A' + 10;
		} else {
			bval = ch - '0';
		}
		return (byte)bval;
	}
}