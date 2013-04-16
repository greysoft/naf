/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

public interface Defs
{
	// See http://download.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html
	public static final String ALG_CPHR_RSA = "RSA";
	public static final String ALG_CPHR_DES = "DES";
	public static final String ALG_CPHR_3DES = "DESede";
	public static final String ALG_CPHR_AES = "AES";
	public static final String ALG_CPHR_BLOWFISH = "Blowfish";
	public static final String ALG_CPHR_PBEDES = "PBEWithMD5AndDES";
	public static final String ALG_CPHR_PBE3DES = "PBEWithMD5AndTripleDES";
	public static final String ALG_CPHR_PBKD = "PBKDF2WithHmacSHA1";

	public static final String PADTYPE = "/CBC/PKCS5Padding";
	public static final String ALG_CPHR_PBE3DESPAD = ALG_CPHR_PBE3DES + PADTYPE;
	public static final String ALG_CPHR_AESPAD = ALG_CPHR_AES + PADTYPE;

	public static final String ALG_DIGEST_MD5 = "MD5";
	public static final String ALG_DIGEST_SHA1 = "SHA";
	public static final String ALG_DIGEST_SHA256 = "SHA-256"; //part of SHA-2 family
	public static final String ALG_DIGEST_SHA512 = "SHA-512"; //part of SHA-2 family

	public static final String ALG_RNG_SHA1 = "SHA1PRNG";
}
