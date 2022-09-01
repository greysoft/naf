/*
 * Copyright 2012-2022 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor.config;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.NAFConfig;
import com.grey.naf.errors.NAFConfigException;

public class SSLConfig
{
	/* Requesting a renegotiation fails if that party's certificate is self-signed or weak. That can be overridden
	 * with this JDK system property setting:
	 *     sun.security.ssl.allowUnsafeRenegotiation=true
	 * However handshake renegotiation will still fail if the initiator doesn't even have a certificate.
	 * If you don't want to use that property, nor go through the NAF config and set all session/handshake timeouts
	 * to zero to disable them, this system property will disable them all for you.
	 */
	private static final boolean NO_RENEGOTIATION = SysProps.get("greynaf.ssl.norenegotiation", false);
	/*
	 * Clients are unable to force a re-handshake unless they have their own cert. Since most clients won't have a cert
	 * and the default session/handshake timeout values (see below) may cause them to initiate the occasional re-handshake
	 * (which would fail) we simply prevent clients from doing so unless this system property is specified.
	 */
	private static final boolean CLIENT_RESHAKE = SysProps.get("greynaf.ssl.clientreshake", false);

	public static final String KSTYPE_JKS = "JKS";
	public static final String KSTYPE_PKCS12 = "PKCS12";

	private final String protocol;
	private final boolean isClient;
	private final int clientAuth; //0=No, 1=Will-Accept, 2=Need
	private final String localCertAlias; //keystore alias
	private final String peerCertName; //this is the cert's subject hostname
	private final String trustFormat;
	private final java.net.URL trustPath;
	private final String storeFormat;
	private final java.net.URL storePath;
	private final int sessionCacheSize;
	private final long sessionTimeout; //maximum SSL session lifetime, before forcibly invalidated - zero means never
	private final long shakeFreq; //maximum time between partial handshakes - zero means never
	public long shakeTimeout; //timeout on initial handshake - zero means none
	private final boolean latent; //true means this is not initially an SSL connection - SSL may be activated later
	private final boolean mdty;
	private final javax.net.ssl.SSLContext ctx;

	public SSLConfig(Builder bldr)
	{
		if (NO_RENEGOTIATION) {
			bldr.sessionTimeout = 0;
			bldr.shakeFreq = 0;
		}
		protocol = bldr.protocol;
		isClient = bldr.isClient;
		localCertAlias = bldr.localCertAlias;
		peerCertName = bldr.peerCertName;
		trustFormat = bldr.trustFormat;
		trustPath = bldr.trustPath;
		storeFormat = bldr.storeFormat;
		storePath = bldr.storePath;
		sessionCacheSize = bldr.sessionCacheSize;
		shakeTimeout = bldr.shakeTimeout;
		latent = bldr.latent;
		mdty = bldr.mdty;
		long sesstmt = (bldr.sessionTimeout != 0 && bldr.sessionTimeout < 1000 ? 1000 : bldr.sessionTimeout); //preserve finite value

		if (isClient) {
			// Even a partial re-handshake fails in client mode with this error:
			//      javax.net.ssl.SSLProtocolException - handshake alert: no_negotiation
			if (!CLIENT_RESHAKE) sesstmt = 0;
			if (bldr.clientAuth != 0) bldr.clientAuth = 0; //irrelevant for clients - adjust to only legal value
		} else {
			if (bldr.clientAuth < -1 || bldr.clientAuth > 2) {
				throw new NAFConfigException("Illegal client-auth="+bldr.clientAuth);
			}
			if (bldr.clientAuth == 0 && peerCertName != null) bldr.clientAuth = 1; //allow client certs to be accepted
		}
		clientAuth = bldr.clientAuth;
		sessionTimeout = sesstmt;

		if (sessionTimeout <= bldr.shakeFreq) {
			shakeFreq = 0; //session expiry will force a full handshake anyway
		} else {
			shakeFreq = bldr.shakeFreq;
		}

		try {
			ctx = initContext(protocol, isClient, sessionCacheSize, localCertAlias, trustFormat, trustPath, storeFormat, storePath,
								bldr.trustPasswd, bldr.storePasswd, bldr.certPasswd);
		} catch (NAFConfigException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new NAFConfigException("Failed to create SSL context", ex);
		}
	}

	public String getProtocol() {
		return protocol;
	}

	public boolean isClient() {
		return isClient;
	}

	public int getClientAuth() {
		return clientAuth;
	}

	public String getLocalCertAlias() {
		return localCertAlias;
	}

	public String getPeerCertName() {
		return peerCertName;
	}

	public String getTrustFormat() {
		return trustFormat;
	}

	public java.net.URL getTrustPath() {
		return trustPath;
	}

	public String getStoreFormat() {
		return storeFormat;
	}

	public java.net.URL getStorePath() {
		return storePath;
	}

	public int getSessionCacheSize() {
		return sessionCacheSize;
	}

	public long getSessionTimeout() {
		return sessionTimeout;
	}

	public long getShakeFreq() {
		return shakeFreq;
	}

	public long getShakeTimeout() {
		return shakeTimeout;
	}

	public boolean isLatent() {
		return latent;
	}

	public boolean isMandatory() {
		return mdty;
	}

	public javax.net.ssl.SSLContext getContext() {
		return ctx;
	}

	@Override
	public String toString()
	{
		String txt = super.toString()+": Context="+getContext().getProtocol()+"/"+getContext().getProvider().getClass().getName()
				+"; server="+!isClient+(isClient()?"":"/client-auth="+getClientAuth())
				+"; latent="+isLatent()+(isLatent() ? "/mandatory="+isMandatory() : "")
				+"; session-cache="+TimeOps.expandMilliTime(getSessionTimeout())+"/"+getSessionCacheSize()+"; shake="+TimeOps.expandMilliTime(getShakeFreq())
				+"; timeout="+TimeOps.expandMilliTime(getShakeTimeout());
		if (getLocalCertAlias() != null) {
			txt += "\n\tlocal-cert="+getLocalCertAlias()+"; format="+getStoreFormat()+" - "+getStorePath();
		}
		if (getPeerCertName() != null || getTrustPath() != null) {
			txt += "\n\tpeer-cert="+getPeerCertName();
			if (getTrustPath() != null) txt += "; format="+getTrustFormat()+" - "+getTrustPath();
		}
		return txt;
	}

	private static javax.net.ssl.SSLContext initContext(String protocol, boolean isClient, int sessionCacheSize, String localCertAlias,
			String trustFormat, java.net.URL trustPath,
			String storeFormat, java.net.URL storePath,
			char[] trustPasswd, char[] storePasswd, char[] certPasswd) throws java.io.IOException, java.security.GeneralSecurityException {
		javax.net.ssl.SSLContext ctx;
		if (certPasswd == null) certPasswd = storePasswd;

		if (protocol == null) {
			ctx = javax.net.ssl.SSLContext.getDefault();
		} else {
			ctx = javax.net.ssl.SSLContext.getInstance(protocol);
			javax.net.ssl.KeyManagerFactory kmf = null;
			javax.net.ssl.TrustManagerFactory tmf = null;
			if (localCertAlias != null) {
				// we will supply a certificate, so construct a dedicated key manager for it
				if (storePath == null) throw new NAFConfigException("SSLConfig: Must supply private-key store for SSL servers");
				java.security.KeyStore ksmaster = loadStore(storePath, storeFormat, storePasswd);
				java.security.Key localkey = ksmaster.getKey(localCertAlias, certPasswd);
				java.security.cert.Certificate[] localcert = ksmaster.getCertificateChain(localCertAlias);

				java.security.KeyStore ks = java.security.KeyStore.getInstance(KSTYPE_JKS);
				ks.load(null, null);
				ks.setKeyEntry(localCertAlias, localkey, certPasswd, localcert);
				kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(ks, certPasswd);
			}

			if (trustPath != null) {
				// we have been given an explicit set of certificates to trust
				java.security.KeyStore ts = loadStore(trustPath, trustFormat, trustPasswd);
				tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(ts);
			}
			ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);
			javax.net.ssl.SSLSessionContext sessctx = (isClient ? ctx.getClientSessionContext() : ctx.getServerSessionContext());
			if (sessctx != null) {
				// This seems to have no effect on SSLEngine, probably only meant for blocking SSL sockets
				// Set the session cache size anyway just in case, but timeouts will be explicitly enforced by SSLConnection
				sessctx.setSessionCacheSize(sessionCacheSize);
			}
		}
		return ctx;
	}

	private static java.security.KeyStore loadStore(java.net.URL pthnam, String type, char[] passwd)
			throws java.io.IOException, java.security.GeneralSecurityException
	{
		java.security.KeyStore store = java.security.KeyStore.getInstance(type);
		java.io.InputStream fin = pthnam.openStream();
		try {
			store.load(fin, passwd);
		} finally {
			fin.close();
		}
		return store;
	}


	public static class Builder
	{
		private String protocol = "TLSv1.2";
		private boolean isClient;
		private int clientAuth = 1; //accept client certs, if supplied
		private String localCertAlias;
		private String peerCertName;
		private String trustFormat = SysProps.get("javax.net.ssl.trustStoreType", java.security.KeyStore.getDefaultType());
		private java.net.URL trustPath = makeURL(SysProps.get("javax.net.ssl.trustStore"));
		private String storeFormat = SysProps.get("javax.net.ssl.keyStoreType", java.security.KeyStore.getDefaultType());
		private java.net.URL storePath = makeURL(SysProps.get("javax.net.ssl.keyStore"));
		private int sessionCacheSize = SysProps.get("javax.net.ssl.sessionCacheSize", 0);
		private long sessionTimeout = TimeOps.parseMilliTime("24h"); //this is the JDK default
		private long shakeFreq = TimeOps.parseMilliTime("1h");
		private long shakeTimeout = TimeOps.parseMilliTime("2m");
		private boolean latent;
		private boolean mdty; //qualifies 'latent' by specifying whether it's mandatory to switch to SSL mode
		private char[] trustPasswd = makeChars(SysProps.get("javax.net.ssl.trustStorePassword"));
		private char[] storePasswd = makeChars(SysProps.get("javax.net.ssl.keyStorePassword"));
		private char[] certPasswd;

		public Builder withXmlConfig(XmlConfig cfg, NAFConfig nafcfg) throws java.io.IOException {
			protocol = cfg.getValue("@proto", false, protocol);
			clientAuth = cfg.getInt("@clientauth", false, clientAuth);
			localCertAlias = cfg.getValue("@cert", false, localCertAlias);
			peerCertName = cfg.getValue("@peercert", false, peerCertName);
			trustFormat = cfg.getValue("@tstype", false, trustFormat);
			trustPasswd = cfg.getPassword("@tspass", trustPasswd);
			storeFormat = cfg.getValue("@kstype", false, storeFormat);
			storePasswd = cfg.getPassword("@kspass", storePasswd);
			certPasswd = cfg.getPassword("@certpass", certPasswd);
			sessionCacheSize = cfg.getInt("@cache", false, sessionCacheSize);
			sessionTimeout = cfg.getTime("@expiry", sessionTimeout);
			shakeFreq = cfg.getTime("@shake", shakeFreq);
			shakeTimeout = cfg.getTime("@timeout", shakeTimeout);
			latent = cfg.getBool("@latent", latent);
			mdty = (latent ? cfg.getBool("@mandatory", mdty) : false);
			trustPath = nafcfg.getURL(cfg, "@tspath", null, false, trustPath == null ? null : trustPath.toString(), null);
			storePath = nafcfg.getURL(cfg, "@kspath", null, false, storePath == null ? null : storePath.toString(), null);
			return this;
		}

		public Builder withProtocol(String v) {
			protocol = v;
			return this;
		}

		public Builder withIsClient(boolean v) {
			isClient = v;
			return this;
		}

		public Builder withClientAuth(int v) {
			clientAuth = v;
			return this;
		}

		public Builder withLocalCertAlias(String v) {
			localCertAlias = v;
			return this;
		}

		public Builder withPeerCertName(String v) {
			int pos = (v == null ? -1 : v.indexOf(':'));
			if (pos != -1) v = v.substring(0, pos);
			peerCertName = v;
			return this;
		}

		public Builder withTrustFormat(String v) {
			trustFormat = v;
			return this;
		}

		public Builder withTrustPath(java.net.URL v) {
			trustPath = v;
			return this;
		}

		public Builder withStoreFormat(String v) {
			storeFormat = v;
			return this;
		}

		public Builder withStorePath(java.net.URL v) {
			storePath = v;
			return this;
		}

		public Builder withSessionCacheSize(int v) {
			sessionCacheSize = v;
			return this;
		}

		public Builder withSessionTimeout(long v) {
			sessionTimeout = v;
			return this;
		}

		public Builder withShakeFreq(long v) {
			shakeFreq = v;
			return this;
		}

		public Builder withShakeTimeout(long v) {
			shakeTimeout = v;
			return this;
		}

		public Builder withLatent(boolean v) {
			latent = v;
			return this;
		}

		public Builder withMandatory(boolean v) {
			mdty = v;
			return this;
		}

		public Builder withTrustPasswd(char[] v) {
			trustPasswd = v;
			return this;
		}

		public Builder withStorePasswd(char[] v) {
			storePasswd = v;
			return this;
		}

		public Builder withCertPasswd(char[] v) {
			certPasswd = v;
			return this;
		}

		public SSLConfig build() {
			return new SSLConfig(this);
		}

		private static java.net.URL makeURL(String pthnam) {
			if (pthnam == null) return null;
			try {
				java.net.URL url = FileOps.makeURL(pthnam);
				if (url == null) url = new java.io.File(pthnam).toURI().toURL(); //must be a straight pathname, so convert to URL syntax
				return url;
			} catch (Exception ex) {
				throw new NAFConfigException("Failed to make URL from "+pthnam, ex);
			}
		}

		private static char[] makeChars(String str) {
			if (str == null || str.length() == 0) return null;
			return str.toCharArray();
		}
	}
}
