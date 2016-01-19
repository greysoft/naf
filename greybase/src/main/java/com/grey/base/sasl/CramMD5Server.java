/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.crypto.Ascii;
import com.grey.base.utils.ByteOps;

// The SASL CRAM-MD5 mechanism is defined in RFC-2195 (Sep 1997)
public final class CramMD5Server
	extends SaslServer
{
	private final SecureHash hashfunc = new SecureHash();
	private final com.grey.base.utils.ByteChars srvnonce = new com.grey.base.utils.ByteChars();

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
	public com.grey.base.utils.ByteChars setChallenge(com.grey.base.utils.ByteChars outbuf)
	{
		if (outbuf == null) outbuf = new com.grey.base.utils.ByteChars();
		encode(srvnonce, outbuf);
		return outbuf;
	}

	@Override
	protected boolean verifyDecodedResponse(com.grey.base.utils.ArrayRef<byte[]> msg)
	{
		int dlm = ByteOps.indexOf(msg.ar_buf, msg.ar_off, msg.ar_len, (byte)' ');
		if (dlm == -1) return false;
		auth_username.set(msg.ar_buf, msg.ar_off, dlm - msg.ar_off);
		int digest_len = msg.ar_len - auth_username.ar_len - 1;
		com.grey.base.utils.ByteChars passwd = (digest_len == 0 ? null : authenticator.saslPasswordLookup(auth_username));
		if (passwd == null) return false;
		return hashfunc.matches(passwd, srvnonce, msg.ar_buf, dlm + 1, digest_len);
	}


	static class SecureHash
	{
		private final com.grey.base.crypto.HMAC.KeyMaterial km;
		private char[] hexbuf;
		private int hexlen;

		public SecureHash() throws java.security.NoSuchAlgorithmException {
			km = new com.grey.base.crypto.HMAC.KeyMaterial("MD5", null);
		}

		private void calculate(com.grey.base.utils.ByteChars secret, com.grey.base.utils.ArrayRef<byte[]> data)
		{
			km.reset(secret.ar_buf, secret.ar_off, secret.ar_len);
			byte[] hash = km.encode(data.ar_buf, data.ar_off, data.ar_len);
			hexbuf = Ascii.hexEncode(hash, 0, hash.length, hexbuf);
			hexlen = Ascii.hexEncodeLength(hash.length);
		}

		public void append(com.grey.base.utils.ByteChars secret, com.grey.base.utils.ArrayRef<byte[]> data, com.grey.base.utils.ByteChars outbuf)
		{
			calculate(secret, data);
			outbuf.append(hexbuf, 0, hexlen);
		}

		public boolean matches(com.grey.base.utils.ByteChars secret, com.grey.base.utils.ArrayRef<byte[]> data,
				byte[] buf, int off, int len)
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