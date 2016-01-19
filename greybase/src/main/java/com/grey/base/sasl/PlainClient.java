/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

public final class PlainClient
	extends SaslClient
{
	private static final byte NUL = (byte)0;

	public PlainClient(boolean base64)
	{
		super(MECH.PLAIN, base64);
	}

	public com.grey.base.utils.ByteChars setResponse(CharSequence role, CharSequence user, CharSequence passwd, com.grey.base.utils.ByteChars outbuf)
	{
		if (outbuf == null) outbuf = new com.grey.base.utils.ByteChars();
		int len = outbuf.ar_len;
		if (role != null) outbuf.append(role);
		outbuf.append(NUL).append(user).append(NUL).append(passwd);
		return encode(outbuf, len);
	}
}