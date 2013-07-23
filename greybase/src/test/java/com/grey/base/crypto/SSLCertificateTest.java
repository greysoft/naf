/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

import com.grey.base.utils.DynLoader;

public class SSLCertificateTest
{
	@org.junit.Test
	public void load() throws java.io.IOException, java.security.cert.CertificateException, java.net.URISyntaxException
	{
		java.net.URL url = DynLoader.getResource("cert-nosans-dolphin.der", getClass());
		java.io.File fh = new java.io.File(url.toURI());
		java.security.cert.X509Certificate cert = SSLCertificate.loadX509(null, fh);
		org.junit.Assert.assertFalse(SSLCertificate.matchSubjectHost(cert, "badhostname"));
		org.junit.Assert.assertTrue(SSLCertificate.matchSubjectHost(cert, "dolphin-w.grey"));

		url = DynLoader.getResource("cert-sans-gmail.pem", getClass());
		cert = SSLCertificate.loadX509(null, url);
		org.junit.Assert.assertFalse(SSLCertificate.matchSubjectHost(cert, "badhostname"));
		org.junit.Assert.assertTrue(SSLCertificate.matchSubjectHost(cert, "pop.gmail.com"));
	}
}