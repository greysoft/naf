/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.utils.StringOps;
import com.grey.base.utils.ByteChars;
import com.grey.base.collections.ObjectWell;

public class PlainTest
{
	private final SaslAuthenticator saslauth = new SaslAuthenticator();
	private CharSequence req_role;
	private CharSequence req_user;
	private CharSequence req_passwd;

	@org.junit.Test
	public void testPlain()
	{
		runtests(false, true);
		runtests(false, false);
		runtests(true, true);
		runtests(true, false);
	}

	@org.junit.Test
	public void testBadResponse()
	{
		PlainServer server = new PlainServer(saslauth, false);
		org.junit.Assert.assertTrue(server.requiresInitialResponse());
		org.junit.Assert.assertFalse(server.sendsChallenge());
		req_user = SaslAuthenticator.USRNAM_GOOD;
		req_passwd = SaslAuthenticator.PASSWD_GOOD;
		req_role = "";
		//only 1 delimiter
		ByteChars rsp = new ByteChars(req_role).append((byte)0).append(req_user);
		org.junit.Assert.assertFalse(server.init().verifyResponse(rsp));
		// no delimiters
		rsp = new ByteChars(req_user);
		org.junit.Assert.assertFalse(server.init().verifyResponse(rsp));
		// just delimiters
		rsp.clear().append((byte)0).append((byte)0);
		org.junit.Assert.assertFalse(server.init().verifyResponse(rsp));
	}

	private void runtests(boolean base64, boolean role_ok)
	{
		ServerFactory fact = new ServerFactory(SaslEntity.MECH.PLAIN, saslauth, base64);
		ObjectWell<PlainServer> servers = new ObjectWell<PlainServer>(null, fact, "utest_SaslPlain", 0, 0, 1);
		PlainServer server = servers.extract().init();
		PlainClient client = new PlainClient(base64);
		req_user = SaslAuthenticator.USRNAM_GOOD;
		req_passwd = SaslAuthenticator.PASSWD_GOOD;
		req_role = (role_ok ? SaslAuthenticator.ROLE_GOOD : "role_bad");
		org.junit.Assert.assertTrue(role_ok == verify(client, server, null));
		org.junit.Assert.assertTrue(role_ok == verify(client, server, new ByteChars("Prefix")));
		req_role = null;
		org.junit.Assert.assertTrue(verify(client, server, null));
		org.junit.Assert.assertTrue(verify(client, server, new ByteChars("Prefix")));
		req_role = "";
		org.junit.Assert.assertTrue(verify(client, server, null));
		org.junit.Assert.assertTrue(verify(client, server, new ByteChars("Prefix")));
		req_role = req_user;
		org.junit.Assert.assertTrue(verify(client, server, null));
		org.junit.Assert.assertTrue(verify(client, server, new ByteChars("Prefix")));

		req_role = null;
		req_passwd = "badpass";
		org.junit.Assert.assertFalse(verify(client, server, null));
		org.junit.Assert.assertFalse(verify(client, server, new ByteChars("Prefix")));
		req_passwd = null;
		org.junit.Assert.assertFalse(verify(client, server, null));
		org.junit.Assert.assertFalse(verify(client, server, new ByteChars("Prefix")));
		req_passwd = "";
		org.junit.Assert.assertFalse(verify(client, server, null));
		org.junit.Assert.assertFalse(verify(client, server, new ByteChars("Prefix")));
		req_passwd = "badpass";
		req_user = "nosuchuser";
		org.junit.Assert.assertFalse(verify(client, server, null));
		org.junit.Assert.assertFalse(verify(client, server, new ByteChars("Prefix")));
		req_user = null;
		org.junit.Assert.assertFalse(verify(client, server, null));
		org.junit.Assert.assertFalse(verify(client, server, new ByteChars("Prefix")));
		req_user = "";
		org.junit.Assert.assertFalse(verify(client, server, null));
		org.junit.Assert.assertFalse(verify(client, server, new ByteChars("Prefix")));
		servers.store(server);
	}

	private boolean verify(PlainClient client, SaslServer server, ByteChars rspbuf)
	{
		client.init();
		int pfxlen = (rspbuf == null ? 0 : rspbuf.ar_len);
		ByteChars rsp = client.setResponse(req_role, req_user, req_passwd, rspbuf);
		if (rspbuf != null) org.junit.Assert.assertTrue(rsp == rspbuf);
		rsp.advance(pfxlen);
		boolean ok = server.init().verifyResponse(rsp);
		if (ok) {
			if (req_role != null && req_role.length() != 0) {
				org.junit.Assert.assertEquals(new ByteChars(req_role), server.getUser());
			} else {
				org.junit.Assert.assertEquals(new ByteChars(req_user), server.getUser());
			}
		}
		return ok;
	}


	private static final class SaslAuthenticator
		extends SaslServer.Authenticator
	{
		static final CharSequence USRNAM_GOOD = "userid1";
		static final ByteChars PASSWD_GOOD = new ByteChars("password1");
		static final ByteChars ROLE_GOOD = new ByteChars("goodrolename");
		SaslAuthenticator() {} //defined purely to avoid synthetic accessor

		//implement this rather than saslAuthenticate() so as to exercise the default version of the latter
		@Override
		public ByteChars saslPasswordLookup(ByteChars usrnam) {
			if (StringOps.sameSeq(usrnam, USRNAM_GOOD)) return PASSWD_GOOD;
			return null;
		}
		@Override
		public boolean saslAuthorise(ByteChars usrnam, ByteChars rolename) {
			if (ROLE_GOOD.equals(rolename)) return true;
			return super.saslAuthorise(usrnam, rolename);
		}
	}
}
