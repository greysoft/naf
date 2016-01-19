/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

public abstract class SaslServer
	extends SaslEntity
{
	public static class Authenticator
	{
		/**
		 * Null passwd arg means we simply want to establish whether this user exists, whereas blank arg means we
		 * are testing if login is possible without a password (ie. if this user has no password defined) and is
		 * therefore evaluated like any other password.
		 */
		public boolean saslAuthenticate(com.grey.base.utils.ByteChars usrnam, com.grey.base.utils.ByteChars passwd) {
			//provide a naive default implementation that depends on saslPasswordLookup()
			if (usrnam == null || usrnam.ar_len == 0) return false;
			com.grey.base.utils.ByteChars actual_passwd = saslPasswordLookup(usrnam);
			if (actual_passwd == null) return false; //user doesn't exist
			if (passwd == null) return true; //just wanted to know if user exists
			return passwd.equals(actual_passwd);
		}
		/**
		 * Returns plaintext password of specified user.
		 * <br>Returns null if user doesn't exist and blank if they exist but have no password.
		 * <br>Throws UnsupportedOperationException if password lookup is not possible.
		 */
		public com.grey.base.utils.ByteChars saslPasswordLookup(com.grey.base.utils.ByteChars usrnam) {
			throw new UnsupportedOperationException("Password lookup is not supported - username="+usrnam);
		}
		/**
		 * Returns true if user=usrnam is permitted to act as user=rolename.
		 * <br>If it returns True, then subsequent calls to SaslServer.getUser() will return rolename.
		 */
		public boolean saslAuthorise(com.grey.base.utils.ByteChars usrnam, com.grey.base.utils.ByteChars rolename) {
			return rolename.equals(usrnam);
		}
	}


	protected final Authenticator authenticator;
	protected final com.grey.base.utils.ByteChars auth_username = new com.grey.base.utils.ByteChars();

	abstract protected boolean verifyDecodedResponse(com.grey.base.utils.ArrayRef<byte[]> rspdata);

	public com.grey.base.utils.ByteChars getUser() {return auth_username;}

	public SaslServer(SaslEntity.MECH id, Authenticator a, boolean base64) {super(id, base64); authenticator = a;}

	// is client required to initiate the authentication process with an initial response?
	public boolean requiresInitialResponse() {return true;}
	// does server issue a challenge?
	public boolean sendsChallenge() {return false;}

	@Override
	public SaslServer init() {super.init(); auth_username.clear(); return this;}

	// default is to send an empty challenge - override to actually include one
	public com.grey.base.utils.ByteChars setChallenge(com.grey.base.utils.ByteChars outbuf) {return outbuf;}

	public final boolean verifyResponse(com.grey.base.utils.ArrayRef<byte[]> msg)
	{
		if (msg.ar_len != 0) {
			// An error decoding the message is more likely to mean that the client has prevented an invalid
			// response rather than a bug in Base64, so just return False for failure, rather than throwing
			// a dramatic exception.
			try {
				msg = decode(msg);
			} catch (Throwable ex) {
				return false;
			}
		}
		return verifyDecodedResponse(msg);
	}

	protected final boolean authorise(com.grey.base.utils.ByteChars rolename)
	{
		if (rolename == null || rolename.ar_len == 0) return true;
		boolean is_valid = authenticator.saslAuthorise(auth_username, rolename);
		if (is_valid) auth_username.set(rolename);
		return is_valid;
	}
}