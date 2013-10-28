/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.utils.ByteOps;

// The SASL Plain mechanism is defined in RFC-4616 (Aug 2006)
public final class PlainServer
	extends SaslServer
{
	private final com.grey.base.utils.ByteChars auth_rolename = new com.grey.base.utils.ByteChars();
	private final com.grey.base.utils.ByteChars auth_passwd = new com.grey.base.utils.ByteChars();
	private final boolean role_ok;

	public PlainServer(Authenticator authenticator, boolean base64, boolean role_ok)
	{
		super(MECH.PLAIN, authenticator, base64);
		this.role_ok = role_ok;
	}

	@Override
	public PlainServer init()
	{
		super.init();
		auth_rolename.clear();
		auth_passwd.clear();
		return this;
	}

	@Override
	protected boolean verifyDecodedResponse(com.grey.base.utils.ArrayRef<byte[]> msg)
	{
		int dlm1 = ByteOps.indexOf(msg.ar_buf, msg.ar_off, msg.ar_len, (byte)0);
		int dlm2 = (dlm1 == -1 ? -1 : ByteOps.indexOf(msg.ar_buf, dlm1+1, msg.ar_len - (dlm1 - msg.ar_off + 1), (byte)0));
		if (dlm2 == -1) return false;
		auth_rolename.pointAt(msg.ar_buf, msg.ar_off, dlm1 - msg.ar_off);
		auth_username.pointAt(msg.ar_buf, dlm1 + 1, dlm2 - dlm1 - 1);
		com.grey.base.utils.ByteChars role = (auth_rolename.ar_len == 0 || auth_rolename.equals(auth_username) ? null : auth_rolename);
		boolean is_valid = (role_ok || role == null) && (auth_username.ar_len != 0);

		if (is_valid) {
			auth_passwd.pointAt(msg.ar_buf, dlm2 + 1, msg.ar_off + msg.ar_len - dlm2 - 1);
			com.grey.base.utils.ByteChars actual_passwd = authenticator.saslPassword(role, auth_username);
			is_valid = auth_passwd.equals(actual_passwd);
		}
		return is_valid;
	}
}