/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.ObjectWell;
import com.grey.base.utils.StringOps;

public class PlainTest
	implements SaslServer.Authenticator
{
	private CharSequence req_role;
	private CharSequence req_user;
	private CharSequence req_passwd;
	private CharSequence actual_passwd;

	@org.junit.Test
	public void testPlain()
	{
		runtests(false, true);
		runtests(false, false);
		runtests(true, true);
		runtests(true, false);
	}

	@org.junit.Test
	public void testBadResponse() throws java.security.NoSuchAlgorithmException
	{
		PlainServer server = new PlainServer(this, false, false);
		org.junit.Assert.assertTrue(server.requiresInitialResponse());
		org.junit.Assert.assertFalse(server.sendsChallenge());
		req_role = "";
		req_user = "userid1";
		req_passwd = "password";
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
		ServerFactory fact = (role_ok ? new ServerFactory(SaslEntity.MECH.PLAIN, this, base64, role_ok) : new ServerFactory(SaslEntity.MECH.PLAIN, this, base64));
		ObjectWell<PlainServer> servers = new ObjectWell<PlainServer>(null, fact, "utest_SaslPlain", 0, 0, 1);
		PlainServer server = servers.extract().init();
		PlainClient client = new PlainClient(base64);
		req_role = "role1";
		req_user = "userid1";
		req_passwd = "password";
		org.junit.Assert.assertEquals(role_ok, verify(client, server, null));
		org.junit.Assert.assertEquals(role_ok, verify(client, server, new ByteChars("Prefix")));
		req_role = null;
		org.junit.Assert.assertTrue(verify(client, server, null));
		org.junit.Assert.assertTrue(verify(client, server, new ByteChars("Prefix")));
		req_role = req_user;
		org.junit.Assert.assertTrue(verify(client, server, null));
		org.junit.Assert.assertTrue(verify(client, server, new ByteChars("Prefix")));

		actual_passwd = "passwd2";
		org.junit.Assert.assertFalse(verify(client, server, null));
		actual_passwd = "passwd2";
		org.junit.Assert.assertFalse(verify(client, server, new ByteChars("Prefix")));
		servers.store(server);
	}

	private boolean verify(PlainClient client, SaslServer server, ByteChars rspbuf)
	{
		client.init();
		server.init();
		int pfxlen = (rspbuf == null ? 0 : rspbuf.ar_len);
		int off1 = (rspbuf == null ? 0 : rspbuf.ar_off);
		ByteChars rsp = client.setResponse(req_role, req_user, req_passwd, rspbuf);
		if (rspbuf != null) org.junit.Assert.assertTrue(rsp == rspbuf);
		rsp.advance(pfxlen);
		if (actual_passwd == null) actual_passwd = req_passwd;
		boolean valid = server.verifyResponse(rsp);
		actual_passwd = null;
		if (rspbuf != null) {
			rspbuf.ar_off = off1;
			rspbuf.ar_len = pfxlen;
		}
		return valid;
	}

	@Override
	public ByteChars saslPassword(ByteChars role, ByteChars user)
	{
		boolean is_valid = true;
		if (req_role == null || req_role.equals(req_user)) {
			is_valid = (role == null);
		} else {
			is_valid = StringOps.sameSeq(role, req_role);
		}
		if (is_valid) is_valid = StringOps.sameSeq(user, req_user);
		return is_valid ? new ByteChars(actual_passwd) : null;
	}
}