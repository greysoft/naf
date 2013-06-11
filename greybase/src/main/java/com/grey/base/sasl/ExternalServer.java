/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

// The SASL Plain mechanism is defined in RFC-4422 Appendix A.
// See RFC-4959 for some more examples based on the email protocols.
public final class ExternalServer
	extends SaslServer
{
	private java.security.cert.X509Certificate clientcert;

	public ExternalServer(Authenticator authenticator, boolean base64)
	{
		super(MECH.EXTERNAL, authenticator, base64);
	}

	public ExternalServer init(java.security.cert.Certificate cert)
	{
		init();
		clientcert = (java.security.cert.X509Certificate)cert;
		return this;
	}

	@Override
	protected boolean verifyDecodedResponse(com.grey.base.utils.ArrayRef<byte[]> msg)
	{
		String cn = (clientcert == null ? null : com.grey.base.crypto.SSLCertificate.getCN(clientcert));
		if (cn == null) return false;
		auth_username.set(cn);
		if (msg.ar_len != 0) {
			if (!auth_username.equalsBytes(msg.ar_buf, msg.ar_off, msg.ar_len)) return false;
		}
		com.grey.base.utils.ByteChars passwd = authenticator.saslPassword(null, auth_username);
		return (passwd != null);  //just need to establish that user is known
	}
}