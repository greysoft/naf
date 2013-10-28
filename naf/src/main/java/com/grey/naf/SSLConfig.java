/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;

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

	public static class Def
	{
		public boolean isClient;
		public int clientAuth = 1; //accept client certs, if supplied
		public String localCertAlias;
		public String peerCertName;
		public String trustFormat = SysProps.get("javax.net.ssl.trustStoreType", java.security.KeyStore.getDefaultType());
		public java.net.URL trustPath = makeURL(SysProps.get("javax.net.ssl.trustStore"));
		public char[] trustPasswd = makeChars(SysProps.get("javax.net.ssl.trustStorePassword"));
		public String storeFormat = SysProps.get("javax.net.ssl.keyStoreType", java.security.KeyStore.getDefaultType());
		public java.net.URL storePath = makeURL(SysProps.get("javax.net.ssl.keyStore"));
		public char[] storePasswd = makeChars(SysProps.get("javax.net.ssl.keyStorePassword"));
		public char[] certPasswd;
		public String protocol = "TLSv1";
		public int sessionCache = SysProps.get("javax.net.ssl.sessionCacheSize", 0);
		public long sessionTimeout = TimeOps.parseMilliTime("24h"); //this is the JDK default
		public long shakeFreq = TimeOps.parseMilliTime("1h");
		public long shakeTimeout = TimeOps.parseMilliTime("2m");
		public boolean latent;
		public boolean mdty;  //qualifies 'latent' by specifying whether it's mandatory to switch to SSL mode
	}

	public final javax.net.ssl.SSLContext ctx;
	public final boolean isClient;
	public final int clientAuth; //0=No, 1=Will-Accept, 2=Need
	public final String localCertAlias; //keystore alias
	public final String peerCertName;   //this is the cert's subject hostname
	public final String trustFormat;
	public final java.net.URL trustPath;
	public final String storeFormat;
	public final java.net.URL storePath;
	public final String protocol;
	public final int sessionCache;
	public final long sessionTimeout;  //maximum SSL session lifetime, before forcibly invalidated - zero means never
	public final long shakeFreq; //maximum time between partial handshakes - zero means never
	public long shakeTimeout; //timeout on initial handshake - zero means none
	public final boolean latent; //true means this is not initially an SSL connection - SSL may be activated later
	public final boolean mdty;

	public static SSLConfig create(XmlConfig cfg, com.grey.naf.Config nafcfg, String peername, boolean client)
			throws com.grey.base.ConfigException, java.io.IOException, java.security.GeneralSecurityException
	{
		return create(cfg, nafcfg, null, peername, client);
	}

	public static SSLConfig create(XmlConfig cfg, com.grey.naf.Config nafcfg, Def def, String peername, boolean client)
			throws com.grey.base.ConfigException, java.io.IOException, java.security.GeneralSecurityException
	{
		if (cfg == null || !cfg.exists()) return null;
		def = convert(cfg, nafcfg, def, peername, client);
		SSLConfig sslcfg = new SSLConfig(def);
		if (def.trustPasswd != null) java.util.Arrays.fill(def.trustPasswd, (char)0);
		if (def.storePasswd != null) java.util.Arrays.fill(def.storePasswd, (char)0);
		if (def.certPasswd != null) java.util.Arrays.fill(def.certPasswd, (char)0);
		return sslcfg;
	}

	public SSLConfig(Def def) throws com.grey.base.ConfigException, java.io.IOException, java.security.GeneralSecurityException
	{
		if (NO_RENEGOTIATION) {
			def.sessionTimeout = 0;
			def.shakeFreq = 0;
		}
		isClient = def.isClient;
		localCertAlias = def.localCertAlias;
		peerCertName = def.peerCertName;
		trustFormat = def.trustFormat;
		trustPath = def.trustPath;
		storeFormat = def.storeFormat;
		storePath = def.storePath;
		protocol = def.protocol;
		sessionCache = def.sessionCache;
		shakeTimeout = def.shakeTimeout;
		latent = def.latent;
		mdty = def.mdty;
		long sesstmt = (def.sessionTimeout != 0 && def.sessionTimeout < 1000 ? 1000 : def.sessionTimeout); //preserve finite value

		if (isClient) {
			// Even a partial re-handshake fails in client mode with this error:
			//      javax.net.ssl.SSLProtocolException - handshake alert:  no_negotiation
			if (!CLIENT_RESHAKE) sesstmt = 0;
			if (def.clientAuth != 0) def.clientAuth = 0; //irrelevant for clients - adjust to only legal value
		} else {
			if (def.clientAuth < -1 || def.clientAuth > 2) {
				throw new com.grey.base.ConfigException("Illegal client-auth="+def.clientAuth);
			}
			if (def.clientAuth == 0 && peerCertName != null) def.clientAuth = 1; //allow client certs to be accepted
		}
		clientAuth = def.clientAuth;
		sessionTimeout = sesstmt;

		if (sessionTimeout <= def.shakeFreq) {
			shakeFreq = 0;  //session expiry will force a full handshake anyway
		} else {
			shakeFreq = def.shakeFreq;
		}
		char[] certPasswd = (def.certPasswd == null ? def.storePasswd : def.certPasswd);

		if (protocol == null) {
			ctx = javax.net.ssl.SSLContext.getDefault();
		} else {
			ctx = javax.net.ssl.SSLContext.getInstance(protocol);
			javax.net.ssl.KeyManagerFactory kmf = null;
			javax.net.ssl.TrustManagerFactory tmf = null;
			if (localCertAlias != null) {
				// we will supply a certificate, so construct a dedicated key manager for it
				if (storePath == null) throw new com.grey.base.ConfigException("SSLConfig: Must supply private-key store for SSL servers");
				java.security.KeyStore ksmaster = loadStore(storePath, storeFormat, def.storePasswd);
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
				java.security.KeyStore ts = loadStore(trustPath, trustFormat, def.trustPasswd);
				tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(ts);
			}
			ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);
			javax.net.ssl.SSLSessionContext sessctx = isClient ? ctx.getClientSessionContext() : ctx.getServerSessionContext();
			if (sessctx != null) {
				// This seems to have no effect on SSLEngine, probably only meant for blocking SSL sockets
				// Set the session cache size anyway just in case, but timeouts will be explicitly enforced by SSLConnection
				sessctx.setSessionCacheSize(sessionCache);
			}
		}
	}

	private static Def convert(XmlConfig cfg, com.grey.naf.Config nafcfg, Def def, String peername, boolean client)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		if (def == null) def = new Def();
		int pos = peername == null ? -1 : peername.indexOf(':');
		if (pos != -1) peername = peername.substring(0, pos);
		def.isClient = client;
		def.localCertAlias = cfg.getValue("@cert", false, null);
		def.peerCertName = cfg.getValue("@peercert", false, peername);
		def.clientAuth = cfg.getInt("@clientauth", false, def.clientAuth);
		def.trustFormat = cfg.getValue("@tstype", false, def.trustFormat);
		def.trustPasswd = cfg.getPassword("@tspass", def.trustPasswd);
		def.storeFormat = cfg.getValue("@kstype", false, def.storeFormat);
		def.storePasswd = cfg.getPassword("@kspass", def.storePasswd);
		def.certPasswd = cfg.getPassword("@certpass", def.certPasswd);
		def.protocol = cfg.getValue("@proto", false, def.protocol);
		def.sessionCache = cfg.getInt("@cache", false, def.sessionCache);
		def.sessionTimeout = cfg.getTime("@expiry", def.sessionTimeout);
		def.shakeFreq = cfg.getTime("@shake", def.shakeFreq);
		def.shakeTimeout = cfg.getTime("@timeout", def.shakeTimeout);
		def.latent = cfg.getBool("@latent", def.latent);
		def.mdty = (def.latent ? cfg.getBool("@mandatory", def.mdty) : true);

		if (nafcfg == null) {
			def.trustPath = makeURL(cfg.getValue("@tspath", false, def.trustPath == null ? null : def.trustPath.toString()));
			def.storePath = makeURL(cfg.getValue("@kspath", false, def.storePath == null ? null : def.storePath.toString()));
		} else {
			def.trustPath = nafcfg.getURL(cfg, "@tspath", null, false, def.trustPath == null ? null : def.trustPath.toString(), null);
			def.storePath = nafcfg.getURL(cfg, "@kspath", null, false, def.storePath == null ? null : def.storePath.toString(), null);
		}
		return def;
	}

	public void declare(String pfx, com.grey.logging.Logger logger)
	{
		String txt = "";
		if (localCertAlias != null) {
			txt += "\n\tlocal-cert="+localCertAlias+"; format="+storeFormat+" - "+storePath;
		}
		if (peerCertName != null || trustPath != null) {
			txt += "\n\tpeer-cert="+peerCertName;
			if (trustPath != null) txt += "; format="+trustFormat+" - "+trustPath;
		}
		logger.info((pfx==null?"":pfx)+"SSL="+ctx.getProtocol()+"/"+ctx.getProvider().getClass().getName()
				+"; server="+!isClient+(isClient?"":"/client-auth="+clientAuth)
				+"; latent="+latent+(latent ? "/mandatory="+mdty : "")
				+"; session-cache="+TimeOps.expandMilliTime(sessionTimeout)+"/"+sessionCache+"; shake="+TimeOps.expandMilliTime(shakeFreq)
				+"; timeout="+TimeOps.expandMilliTime(shakeTimeout)
				+txt);
	}

	private static java.security.KeyStore loadStore(java.net.URL pthnam, String type, char[] passwd)
			throws java.io.IOException, java.security.KeyStoreException, java.security.cert.CertificateException, java.security.NoSuchAlgorithmException
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

	private static java.net.URL makeURL(String pthnam) {
		if (pthnam == null) return null;
		try {
			java.net.URL url = FileOps.makeURL(pthnam);
			if (url != null) return url;
			//must be a straight pathname, so convert to URL syntax
			return new java.io.File(pthnam).toURI().toURL();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to make URL from "+pthnam, ex);
		}
	}

	private static char[] makeChars(String str) {
		if (str == null) return null;
		return str.toCharArray();
	}
}