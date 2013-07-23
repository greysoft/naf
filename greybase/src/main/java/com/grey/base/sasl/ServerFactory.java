package com.grey.base.sasl;

public class ServerFactory
	implements com.grey.base.utils.ObjectWell.ObjectFactory
{
	private final SaslEntity.MECH mech;
	private final SaslServer.Authenticator auth;
	private final boolean base64;
	private final boolean role_ok;

	public ServerFactory(SaslEntity.MECH mech, SaslServer.Authenticator auth, boolean base64, boolean role_ok) {
		this.mech = mech;
		this.auth = auth;
		this.base64 = base64;
		this.role_ok = role_ok;
	}

	public ServerFactory(SaslEntity.MECH mech, SaslServer.Authenticator auth, boolean base64) {
		this(mech, auth, base64, false);
	}

	@Override
	public SaslServer factory_create() {
		try {
			switch (mech)
			{
			case PLAIN:
				return new PlainServer(auth, base64, role_ok);
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