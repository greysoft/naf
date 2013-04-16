/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

public final class ExternalClient
	extends SaslClient
{
	public ExternalClient(boolean base64)
	{
		super(MECH.EXTERNAL, base64);
	}

	public com.grey.base.utils.ByteChars setResponse(CharSequence username, com.grey.base.utils.ByteChars outbuf)
	{
		if (outbuf == null) outbuf = new com.grey.base.utils.ByteChars();
		if (username != null && username.length() != 0) {
			int len = outbuf.ar_len;
			outbuf.append(username);
			outbuf = encode(outbuf, len);
		}
		return outbuf;
	}
}