/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

public class SSLCertificate
{
	public static final String X509EXT_OID_SAN = "2.5.29.17";
	public static final int X509EXT_SAN_RFC822NAME = 1;
	public static final int X509EXT_SAN_DNSNAME = 2;
	public static final int X509EXT_SAN_DTORYNAME = 4;
	public static final int X509EXT_SAN_IPADDR = 7;

	private static final String FACTORY_X509 = "X.509";

	public static java.security.cert.Certificate load(java.security.cert.CertificateFactory fact, java.io.InputStream fin)
			throws java.io.IOException, java.security.cert.CertificateException
	{
		if (fact == null) {
			fact = java.security.cert.CertificateFactory.getInstance(FACTORY_X509);
		}
		return fact.generateCertificate(fin);
	}

	public static java.security.cert.X509Certificate loadX509(java.security.cert.CertificateFactory fact, java.io.File fh)
			throws java.io.IOException, java.security.cert.CertificateException
	{
		java.io.FileInputStream fin = new java.io.FileInputStream(fh);
		try {
			return java.security.cert.X509Certificate.class.cast(load(fact, fin));
		} finally {
			fin.close();
		}
	}

	public static java.security.cert.X509Certificate loadX509(java.security.cert.CertificateFactory fact, java.net.URL url)
			throws java.io.IOException, java.security.cert.CertificateException
	{
		java.io.InputStream fin = url.openStream();
		try {
			return java.security.cert.X509Certificate.class.cast(load(fact, fin));
		} finally {
			fin.close();
		}
	}

	public static boolean matchSubjectHost(java.security.cert.X509Certificate cert, String hostname)
			throws java.security.cert.CertificateParsingException
	{
		// If DNS name is specified in SANs, that takes priority over the DN's Common Name (RFC-6125)
		java.util.Collection<java.util.List<?>> altnames = cert.getSubjectAlternativeNames();
		if (altnames != null) {
			java.util.Iterator<java.util.List<?>> it = altnames.iterator();
			while (it.hasNext()) {
				java.util.List<?> lst = it.next();
				int nametype = Integer.class.cast(lst.get(0)).intValue();
				if (nametype == X509EXT_SAN_DNSNAME) {
					String nameval = String.class.cast(lst.get(1));
					if (hostname.equalsIgnoreCase(nameval)) return true;
				}
			}
		}

		// Fall back to the traditional Common Name.
		String cn = getCN(cert);
		return hostname.equalsIgnoreCase(cn);
	}

	public static String getCN(java.security.cert.X509Certificate cert)
	{
		// In terms of breaking out the components of the DN, the only API-based method seems to be constructing
		// javax.naming.ldap.LdapName(peer.getName() and stepping through the components returned by ldapname.getRdns())
		// to get at the CN, but it seems more efficient to make one substring() call.
		java.security.Principal subject = cert.getSubjectDN();
		String dn = subject.getName();
		String cnpatt = "CN=";
		int pos = dn.indexOf(cnpatt);
		if (pos == -1) return null;
		int pos2 = dn.indexOf(',', pos);
		if (pos2 == -1) pos2 = dn.length();
		String cn = dn.substring(pos+cnpatt.length(), pos2);
		return cn;
	}
}