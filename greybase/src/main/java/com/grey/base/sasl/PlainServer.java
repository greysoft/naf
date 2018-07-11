/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.ByteOps;

// The SASL Plain mechanism is defined in RFC-4616 (Aug 2006)
public final class PlainServer
	extends SaslServer
{
	private final ByteChars auth_rolename = new ByteChars(-1);
	private final ByteChars auth_passwd = new ByteChars(-1);

	public PlainServer(Authenticator authenticator, boolean base64)
	{
		super(MECH.PLAIN, authenticator, base64);
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
	protected boolean verifyDecodedResponse(ByteArrayRef msg)
	{
		int dlm1 = ByteOps.indexOf(msg.buffer(), msg.offset(), msg.size(), (byte)0);
		int dlm2 = (dlm1 == -1 ? -1 : ByteOps.indexOf(msg.buffer(), dlm1+1, msg.size() - (dlm1 - msg.offset() + 1), (byte)0));
		if (dlm2 == -1) return false;
		auth_rolename.set(msg.buffer(), msg.offset(), dlm1 - msg.offset()); //authzid
		auth_username.populate(msg.buffer(), dlm1 + 1, dlm2 - dlm1 - 1); //authcid
		auth_passwd.set(msg.buffer(), dlm2 + 1, msg.offset() + msg.size() - dlm2 - 1);
		boolean is_valid = authenticator.saslAuthenticate(auth_username, auth_passwd);
		if (is_valid) is_valid = authorise(auth_rolename);
		return is_valid;
	}
}