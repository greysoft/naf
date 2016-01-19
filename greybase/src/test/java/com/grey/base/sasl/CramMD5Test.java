/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;

/* See Also:
	docs.oracle.com/javase/6/docs/technotes/guides/security/sasl/sasl-refguide.html
	stackoverflow.com/questions/2077768/how-to-use-the-java-sasl-api-and-cram-md5
 */
public class CramMD5Test
{
	private final SaslAuthenticator saslauth = new SaslAuthenticator();

	@org.junit.Test
	public void testBase64() throws java.security.NoSuchAlgorithmException
	{
		CramMD5Client client = new CramMD5Client(true);
		CramMD5Server server = new CramMD5Server(saslauth, true);
		org.junit.Assert.assertFalse(server.requiresInitialResponse());
		org.junit.Assert.assertTrue(server.sendsChallenge());

		// issue tests based on the RFC-2195 examples
		String usrnam = "tim";
		ByteChars secret = new ByteChars("tanstaaftanstaaf");
		ByteChars nonce = new ByteChars("<1896.697170952@postoffice.reston.mci.net>");
		String expected_challenge = "PDE4OTYuNjk3MTcwOTUyQHBvc3RvZmZpY2UucmVzdG9uLm1jaS5uZXQ+";
		String expected_response = "dGltIGI5MTNhNjAyYzdlZGE3YTQ5NWI0ZTZlNzMzNGQzODkw";
		runtestGood(client, server, usrnam, secret, nonce, expected_challenge, expected_response);
		runtestGood(client, server, usrnam, secret, nonce, expected_challenge, expected_response); //make sure repeat call works

		// RFC-2195 tests have succeeded - repeat with different challenges
		nonce = new ByteChars("<time@localhost>"); //shorter challenge string
		runtestGood(client, server, usrnam, secret, nonce, null, null);
		nonce = new ByteChars("<longer_initial_challenge@localhost>"); //longest challenge
		runtestGood(client, server, usrnam, secret, nonce, null, null);

		// simulate client specifying a bad user or password
		usrnam = "baduser";
		runtestBad(client, server, usrnam, secret, nonce, null, null);
		usrnam = "badpass";
		runtestBad(client, server, usrnam, secret, nonce, null, null);
	}

	@org.junit.Test
	public void testNon64() throws java.security.NoSuchAlgorithmException
	{
		CramMD5Client client = new CramMD5Client(false);
		CramMD5Server server = new CramMD5Server(saslauth, false);
		org.junit.Assert.assertFalse(server.requiresInitialResponse());
		org.junit.Assert.assertTrue(server.sendsChallenge());
		String usrnam = "myname1";
		ByteChars secret = new ByteChars("mypass1");
		ByteChars nonce = new ByteChars("<initial_challenge@localhost>");
		runtestGood(client, server, usrnam, secret, nonce, null, null);
		runtestGood(client, server, usrnam, secret, nonce, null, null); //make sure repeat call works

		// repeat with different params
		usrnam = "anothername2";
		secret = new ByteChars("pass2");
		nonce = new ByteChars("<time@localhost>"); //shorter challenge string
		runtestGood(client, server, usrnam, secret, nonce, null, null);

		// and once more
		usrnam = "anothername3";
		secret = new ByteChars("mypassword3");
		nonce = new ByteChars("<longer_initial_challenge@localhost>"); //longest challenge
		runtestGood(client, server, usrnam, secret, nonce, null, null);

		// simulate client specifying a bad user or password
		usrnam = "baduser";
		runtestBad(client, server, usrnam, secret, nonce, null, null);
		usrnam = "badpass";
		runtestBad(client, server, usrnam, secret, nonce, null, null);
	}

	@org.junit.Test
	public void testBadResponse() throws java.security.NoSuchAlgorithmException
	{
		CramMD5Server server = new CramMD5Server(saslauth, false);
		ByteChars username = new ByteChars("");
		StringBuilder sbtmp = new StringBuilder();

		//blank response
		server.init("utest1", 1, sbtmp).setChallenge(null);
		ByteChars rsp = new ByteChars();
		org.junit.Assert.assertFalse(server.verifyResponse(rsp));

		//just a name
		username = new ByteChars("x");
		server.init(null, 0, null).setChallenge(null);
		rsp.clear().append(username);
		org.junit.Assert.assertFalse(server.verifyResponse(rsp));

		//just a space
		username = new ByteChars("");
		server.init().setChallenge(null);
		rsp.clear().append(" ");
		org.junit.Assert.assertFalse(server.verifyResponse(rsp));

		//name and space but no digest
		username = new ByteChars("name");
		server.init().setChallenge(null);
		rsp.clear().append(username).append(" ");
		org.junit.Assert.assertFalse(server.verifyResponse(rsp));

		//bad digest
		saslauth.username_good = username;
		server.init().setChallenge(null);
		rsp.clear().append(username).append(" xyz");
		org.junit.Assert.assertFalse(server.verifyResponse(rsp));
	}

	private void runtestGood(CramMD5Client client, CramMD5Server server, CharSequence usrnam, ByteChars secret, ByteChars nonce,
			String expected_challenge, String expected_response)
	{
		boolean valid = runtest(client, server, usrnam, secret, nonce, expected_challenge, expected_response);
		org.junit.Assert.assertTrue(valid);
		org.junit.Assert.assertEquals(new ByteChars(usrnam), server.getUser());
	}

	private void runtestBad(CramMD5Client client, CramMD5Server server, CharSequence usrnam, ByteChars secret, ByteChars nonce,
			String expected_challenge, String expected_response)
	{
		boolean valid = runtest(client, server, usrnam, secret, nonce, expected_challenge, expected_response);
		org.junit.Assert.assertFalse(valid);
	}

	private boolean runtest(CramMD5Client client, CramMD5Server server, CharSequence usrnam, ByteChars secret, ByteChars nonce,
			String expected_challenge, String expected_response)
	{
		client.init();
		server.init();
		if (nonce != null) {
			ByteChars srvnonce = (ByteChars)DynLoader.getField(server, "srvnonce");
			srvnonce.set(nonce);
		}
		saslauth.username_good = new ByteChars(usrnam);
		if (usrnam.toString().equals("baduser")) {
			saslauth.passwd = new ByteChars("wrongpassword");
		} else if (usrnam.toString().equals("badpass")) {
			saslauth.passwd = null;
		} else {
			saslauth.passwd = new ByteChars(secret);
		}
		ByteChars sbuf = new ByteChars();
		ByteChars msg = server.setChallenge(sbuf);
		org.junit.Assert.assertTrue(msg == sbuf);
		if (expected_challenge != null) org.junit.Assert.assertEquals(expected_challenge, msg.toString());
		ByteChars cbuf = (usrnam.equals("tim") ? new ByteChars() : null);
		msg = client.setResponse(usrnam, secret, msg, cbuf);
		if (cbuf != null) org.junit.Assert.assertTrue(msg == cbuf);
		if (msg.ar_off == 0) {
			// shuffle buffer up to test offset handling
			byte[] buf = new byte[msg.ar_len + 15];
			System.arraycopy(msg.ar_buf, msg.ar_off, buf, 10, msg.ar_len);
			msg.pointAt(buf, 10, msg.ar_len);
		}
		if (expected_response != null) org.junit.Assert.assertEquals(expected_response, msg.toString());
		return server.verifyResponse(msg);
	}


	private static final class SaslAuthenticator
		extends SaslServer.Authenticator
	{
		ByteChars username_good;
		ByteChars passwd;
		SaslAuthenticator() {} //defined purely to avoid synthetic accessor
		@Override
		public ByteChars saslPasswordLookup(ByteChars usrnam) {
			if (!username_good.equals(usrnam)) return null;
			return passwd;
		}
	}
}