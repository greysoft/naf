/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

public class ServerFactory
	implements com.grey.base.collections.ObjectWell.ObjectFactory
{
	private final SaslEntity.MECH mech;
	private final SaslServer.Authenticator auth;
	private final boolean base64;

	public ServerFactory(SaslEntity.MECH mech, SaslServer.Authenticator auth, boolean base64) {
		this.mech = mech;
		this.auth = auth;
		this.base64 = base64;
	}

	@Override
	public SaslServer factory_create() {
		try {
			switch (mech)
			{
			case PLAIN:
				return new PlainServer(auth, base64);
			case CRAM_MD5:
				return new CramMD5Server(auth, base64);
			case EXTERNAL:
				return new ExternalServer(auth, base64);
			default:
				throw new IllegalStateException("SASL ServerFactory: Missing case for mechanism="+mech);
			}
		} catch (Exception ex) {
			throw new RuntimeException("SASL ServerFactory: Failed to create Server="+mech, ex);
		}
	}
}