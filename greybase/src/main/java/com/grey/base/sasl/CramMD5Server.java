/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.crypto.Ascii;
import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.ByteOps;

// The SASL CRAM-MD5 mechanism is defined in RFC-2195 (Sep 1997)
public final class CramMD5Server
	extends SaslServer
{
	private final SecureHash hashfunc = new SecureHash();
	private final ByteChars srvnonce = new ByteChars();

	@Override
	public boolean requiresInitialResponse() {return false;}
	@Override
	public boolean sendsChallenge() {return true;}

	public CramMD5Server(Authenticator authenticator, boolean base64) throws java.security.NoSuchAlgorithmException
	{
		super(MECH.CRAM_MD5, authenticator, base64);
	}

	// this is the preferred init() method for this class
	public CramMD5Server init(CharSequence tagstr, int tagnum, StringBuilder sb)
	{
		super.init();
		SaslEntity.setNonce(srvnonce.clear(), tagstr, tagnum, sb);
		return this;
	}

	@Override
	public CramMD5Server init()
	{
		return init(null, 0, null);
	}

	@Override
	public com.grey.base.utils.ByteChars setChallenge(ByteChars outbuf)
	{
		if (outbuf == null) outbuf = new ByteChars();
		encode(srvnonce, outbuf);
		return outbuf;
	}

	@Override
	protected boolean verifyDecodedResponse(ByteArrayRef msg)
	{
		int dlm = ByteOps.indexOf(msg.buffer(), msg.offset(), msg.size(), (byte)' ');
		if (dlm == -1) return false;
		auth_username.populate(msg.buffer(), msg.offset(), dlm - msg.offset());
		int digest_len = msg.size() - auth_username.size() - 1;
		ByteChars passwd = (digest_len == 0 ? null : authenticator.saslPasswordLookup(auth_username));
		if (passwd == null) return false;
		return hashfunc.matches(passwd, srvnonce, msg.buffer(), dlm + 1, digest_len);
	}


	static class SecureHash
	{
		private final com.grey.base.crypto.HMAC.KeyMaterial km;
		private char[] hexbuf;
		private int hexlen;

		public SecureHash() throws java.security.NoSuchAlgorithmException {
			km = new com.grey.base.crypto.HMAC.KeyMaterial("MD5", null);
		}

		private void calculate(ByteChars secret, ByteArrayRef data)
		{
			km.reset(secret.buffer(), secret.offset(), secret.size());
			byte[] hash = km.encode(data.buffer(), data.offset(), data.size());
			hexbuf = Ascii.hexEncode(hash, 0, hash.length, hexbuf);
			hexlen = Ascii.hexEncodeLength(hash.length);
		}

		public void append(ByteChars secret, ByteArrayRef data, ByteChars outbuf)
		{
			calculate(secret, data);
			outbuf.append(hexbuf, 0, hexlen);
		}

		public boolean matches(ByteChars secret, ByteArrayRef data, byte[] buf, int off, int len)
		{
			calculate(secret, data);
			if (hexlen != len) return false;
			for (int idx = 0; idx != len; idx++) {
				if (hexbuf[idx] != buf[off++]) return false;
			}
			return true;
		}
	}
}