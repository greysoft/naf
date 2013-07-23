/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

public abstract class SaslServer
	extends SaslEntity
{
	public interface Authenticator
	{
		public com.grey.base.utils.ByteChars saslPassword(com.grey.base.utils.ByteChars role, com.grey.base.utils.ByteChars user);
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
	public SaslEntity init() {super.init(); auth_username.clear(); return this;}

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
}