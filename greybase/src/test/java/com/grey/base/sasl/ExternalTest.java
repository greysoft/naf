/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.crypto.SSLCertificate;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;

public class ExternalTest
	implements SaslServer.Authenticator
{
	private boolean known_user;

	@org.junit.Test
	public void test() throws java.io.IOException, java.security.cert.CertificateException
	{
		runtests(false);
		runtests(true);
	}

	private void runtests(boolean base64) throws java.io.IOException, java.security.cert.CertificateException
	{
		known_user = true;
		ExternalClient client = new ExternalClient(base64);
		ExternalServer server = new ExternalServer(this, base64);
		org.junit.Assert.assertTrue(server.requiresInitialResponse());
		org.junit.Assert.assertFalse(server.sendsChallenge());
		org.junit.Assert.assertFalse(verify(client, server, null, null));
		org.junit.Assert.assertFalse(verify(client, server, null, "any_old_name"));

		java.net.URL url = DynLoader.getResource("com/grey/base/crypto/cert-nosans-dolphin.der", null);
		java.security.cert.X509Certificate cert = SSLCertificate.loadX509(null, url);
		String cn = com.grey.base.crypto.SSLCertificate.getCN(cert);
		org.junit.Assert.assertTrue(verify(client, server, cert, null));
		org.junit.Assert.assertTrue(verify(client, server, cert, ""));
		org.junit.Assert.assertTrue(verify(client, server, cert, cn));
		org.junit.Assert.assertFalse(verify(client, server, cert, cn+"x"));
		known_user = false;
		org.junit.Assert.assertFalse(verify(client, server, cert, null));
		org.junit.Assert.assertFalse(verify(client, server, cert, ""));
		org.junit.Assert.assertFalse(verify(client, server, cert, cn));
		org.junit.Assert.assertFalse(verify(client, server, cert, cn+"x"));
	}

	private boolean verify(ExternalClient client, ExternalServer server, java.security.cert.Certificate cert, String reqname)
	{
		boolean status1 = verify(client, server, cert, reqname, null);
		boolean status2 = verify(client, server, cert, reqname, new ByteChars("Prefix "));
		org.junit.Assert.assertTrue(status1 == status2);
		return status1;
	}

	private boolean verify(ExternalClient client, ExternalServer server, java.security.cert.Certificate cert, String reqname, ByteChars rspbuf)
	{
		server.init(cert);
		int pfxlen = (rspbuf == null ? 0 : rspbuf.ar_len);
		ByteChars rsp = client.setResponse(reqname, rspbuf);
		if (rspbuf != null) org.junit.Assert.assertTrue(rsp == rspbuf);
		rsp.advance(pfxlen);
		return server.verifyResponse(rsp);
	}

	@Override
	public ByteChars saslPassword(ByteChars role, ByteChars user)
	{
		boolean is_valid = (role == null);
		if (is_valid) is_valid = known_user;
		return is_valid ? new ByteChars("any_old_password") : null;
	}
}