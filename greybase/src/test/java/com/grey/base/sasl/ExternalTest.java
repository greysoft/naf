/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.crypto.SSLCertificate;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;

public class ExternalTest
{
	private final SaslAuthenticator saslauth = new SaslAuthenticator();

	@org.junit.Test
	public void test() throws java.io.IOException, java.security.cert.CertificateException
	{
		runtests(false);
		runtests(true);
	}

	private void runtests(boolean base64) throws java.io.IOException, java.security.cert.CertificateException
	{
		saslauth.known_user = true;
		ExternalClient client = new ExternalClient(base64);
		ExternalServer server = new ExternalServer(saslauth, base64);
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
		org.junit.Assert.assertTrue(verify(client, server, cert, SaslAuthenticator.ROLE_GOOD));
		org.junit.Assert.assertFalse(verify(client, server, cert, "badrolename"));
		saslauth.known_user = false;
		org.junit.Assert.assertFalse(verify(client, server, cert, null));
		org.junit.Assert.assertFalse(verify(client, server, cert, ""));
		org.junit.Assert.assertFalse(verify(client, server, cert, cn));
		org.junit.Assert.assertFalse(verify(client, server, cert, "badrolename"));
	}

	private boolean verify(ExternalClient client, ExternalServer server, java.security.cert.X509Certificate cert, CharSequence rolename)
	{
		boolean status1 = verify(client, server, cert, rolename, null);
		boolean status2 = verify(client, server, cert, rolename, new ByteChars("Prefix "));
		org.junit.Assert.assertTrue(status1 == status2);
		return status1;
	}

	private boolean verify(ExternalClient client, ExternalServer server, java.security.cert.X509Certificate cert, CharSequence rolename, ByteChars rspbuf)
	{
		server.init(cert);
		int pfxlen = (rspbuf == null ? 0 : rspbuf.size());
		ByteChars rsp = client.setResponse(rolename, rspbuf);
		if (rspbuf != null) org.junit.Assert.assertTrue(rsp == rspbuf);
		rsp.advance(pfxlen);
		boolean ok = server.verifyResponse(rsp);
		if (ok) {
			if (rolename != null && rolename.length() != 0) {
				org.junit.Assert.assertEquals(new ByteChars(rolename), server.getUser());
			} else {
				String cn = com.grey.base.crypto.SSLCertificate.getCN(cert);
				org.junit.Assert.assertEquals(new ByteChars(cn), server.getUser());
			}
		}
		return ok;
	}


	private static final class SaslAuthenticator
		extends SaslServer.Authenticator
	{
		static final ByteChars ROLE_GOOD = new ByteChars("goodrolename");
		boolean known_user;
		SaslAuthenticator() {} //defined purely to avoid synthetic accessor

		//implement this rather than saslAuthenticate() so as to exercise the default version of the latter
		@Override
		public ByteChars saslPasswordLookup(ByteChars usrnam) {
			return (known_user ? new ByteChars("anyoldpassword") : null);
		}
		@Override
		public boolean saslAuthorise(ByteChars usrnam, ByteChars rolename) {
			if (ROLE_GOOD.equals(rolename)) return true;
			return super.saslAuthorise(usrnam, rolename);
		}
	}
}
