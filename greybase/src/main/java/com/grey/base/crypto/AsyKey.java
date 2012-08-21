/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

// I have verified that the RSA cipher is symmetric, ie. the public and private keys can both be used to decrypt the output of the other.
// That is a prerequisite for digital signatures anyway, but just making sure ...
public class AsyKey
{
	// by "Data", I mean a payload that's too large for RSA encryption, so need to generate and embed a secret key
	public static byte[] encryptData(java.security.Key key, byte[] plaintxt, int off, int len)
			throws java.security.GeneralSecurityException
	{
		java.security.Key symkey = SymKey.generateKey(Defs.ALG_CPHR_AES, 0);
		byte[] keybytes = symkey.getEncoded();
		byte[] enckey = encrypt(key, keybytes, 0, keybytes.length);
		byte[] ciphertxt = SymKey.encrypt(symkey, null, plaintxt, off, len);

		// now package the encrypted sym key and payload in a buffer, prefixed with 2 bytes specifying the size of the encoded sym key
		off = 2;
		byte[] encdata = new byte[off + enckey.length + ciphertxt.length];
		com.grey.base.utils.ByteOps.encodeInt(enckey.length, encdata, 0, off);
		System.arraycopy(enckey, 0, encdata, off, enckey.length);
		off += enckey.length;
		System.arraycopy(ciphertxt, 0, encdata, off, ciphertxt.length);
		return encdata;
	}
	
	public static byte[] decryptData(java.security.Key key, byte[] encdata, int off, int len)
			throws java.security.GeneralSecurityException
	{
		int doff = 2;
		int keysize = com.grey.base.utils.ByteOps.decodeInt(encdata, 0, doff);
		byte[] keybytes = decrypt(key, encdata, doff, keysize);
		java.security.Key symkey = SymKey.buildKey(Defs.ALG_CPHR_AES, keybytes, 0, keybytes.length);
		doff += keysize;
		return SymKey.decrypt(symkey, null, encdata, doff, encdata.length - doff);
	}

	public static java.math.BigInteger[] generateKeyPair()
			throws java.security.GeneralSecurityException
	{
		// generate the keys
		java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance(Defs.ALG_CPHR_RSA);
		kpg.initialize(2048);
		java.security.KeyPair kp = kpg.genKeyPair();  // this is the slow step

		// extract the key info
		java.security.KeyFactory fact = java.security.KeyFactory.getInstance(Defs.ALG_CPHR_RSA);
		java.security.Key pubkey = kp.getPublic();
		java.security.Key prvkey = kp.getPrivate();
		java.security.spec.RSAPublicKeySpec pubspec = fact.getKeySpec(pubkey, java.security.spec.RSAPublicKeySpec.class);
		java.security.spec.RSAPrivateKeySpec prvspec = fact.getKeySpec(prvkey, java.security.spec.RSAPrivateKeySpec.class);

		// return sufficient key info to rebuild both keys
		java.math.BigInteger[] keyparts = new java.math.BigInteger[3];
		keyparts[0] = pubspec.getModulus();
		keyparts[1] = pubspec.getPublicExponent();
		keyparts[2] = prvspec.getPrivateExponent();
		return keyparts;
	}
	
	public static java.security.Key buildPublicKey(java.math.BigInteger kmod, java.math.BigInteger kexp)
			throws java.security.GeneralSecurityException
	{
		java.security.KeyFactory fact = java.security.KeyFactory.getInstance(Defs.ALG_CPHR_RSA);
		java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(kmod, kexp);
		return fact.generatePublic(spec);
	}
	
	public static java.security.Key buildPrivateKey(java.math.BigInteger kmod, java.math.BigInteger kexp)
			throws java.security.GeneralSecurityException
	{
		java.security.KeyFactory fact = java.security.KeyFactory.getInstance(Defs.ALG_CPHR_RSA);
		java.security.spec.RSAPrivateKeySpec spec = new java.security.spec.RSAPrivateKeySpec(kmod, kexp);
		return fact.generatePrivate(spec);
	}
	
	public static byte[] encrypt(java.security.Key key, byte[] plaintxt, int off, int len)
			throws java.security.GeneralSecurityException
	{
		return crypt(javax.crypto.Cipher.ENCRYPT_MODE, key, plaintxt, off, len);
	}
	
	public static byte[] decrypt(java.security.Key key, byte[] ciphertxt, int off, int len)
			throws java.security.GeneralSecurityException
	{
		return crypt(javax.crypto.Cipher.DECRYPT_MODE, key, ciphertxt, off, len);
	}

	private static byte[] crypt(int opmode, java.security.Key key, byte[] txt, int off, int len)
			throws java.security.GeneralSecurityException
	{
		javax.crypto.Cipher cphr = javax.crypto.Cipher.getInstance(Defs.ALG_CPHR_RSA);
		cphr.init(opmode, key);
		return cphr.doFinal(txt, off, len);
	}
}
