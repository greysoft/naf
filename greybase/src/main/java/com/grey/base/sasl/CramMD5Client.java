/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

public final class CramMD5Client
	extends SaslClient
{
	private final CramMD5Server.SecureHash hashfunc = new CramMD5Server.SecureHash();

	public CramMD5Client(boolean base64) throws java.security.NoSuchAlgorithmException
	{
		super(MECH.CRAM_MD5, base64);
	}

	public com.grey.base.utils.ByteChars setResponse(CharSequence username, com.grey.base.utils.ByteChars secret,
			com.grey.base.utils.ArrayRef<byte[]> challenge, com.grey.base.utils.ByteChars outbuf)
	{
		if (outbuf == null) outbuf = new com.grey.base.utils.ByteChars();
		int len = outbuf.ar_len;
		outbuf.append(username).append(' ');
		challenge = decode(challenge);
		hashfunc.append(secret, challenge, outbuf);
		return encode(outbuf, len);
	}
}