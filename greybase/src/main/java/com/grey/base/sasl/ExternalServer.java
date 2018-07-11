/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteChars;

// The SASL External mechanism is defined in RFC-4422 Appendix A.
// See RFC-4959 for some more examples based on the email protocols.
public final class ExternalServer
	extends SaslServer
{
	private final ByteChars auth_rolename = new ByteChars(-1);
	private java.security.cert.X509Certificate clientcert;

	public ExternalServer(Authenticator authenticator, boolean base64)
	{
		super(MECH.EXTERNAL, authenticator, base64);
	}

	public ExternalServer init(java.security.cert.Certificate cert)
	{
		init();
		auth_rolename.clear();
		clientcert = (java.security.cert.X509Certificate)cert;
		return this;
	}

	@Override
	protected boolean verifyDecodedResponse(ByteArrayRef msg)
	{
		String cn = (clientcert == null ? null : com.grey.base.crypto.SSLCertificate.getCN(clientcert));
		if (cn == null) return false;
		auth_username.populate(cn);
		auth_rolename.set(msg); //authzid
		boolean is_valid = authenticator.saslAuthenticate(auth_username, null);
		if (is_valid) is_valid = authorise(auth_rolename);
		return is_valid;
	}
}