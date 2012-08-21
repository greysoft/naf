/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

/**
 * This class supports generic secret-key encryption, as well as PBE (Password-Based Encryption) specifically.
 * <br/>
 * Note 1: IVs allow you to reuse the same key for multiple messages. Salts prevent dictionary attacks on the password that derived the key
 * <br/>
 * Note 2: In the JCE (Java Cryptography Extension), Generators are used to generate brand new objects (eg. keys), whereas Factories are used to
 * convert data from one existing object type to another (eg. keys to key specs or vice versa).
 */
public class SymKey
{
	private static final String DFLT_ALGNAME = Defs.ALG_CPHR_AES;
	private static final int DFLT_KEYSIZE = 128;  //need the unlimited-jurisdiction JCE download for key sizes greater than 128 bits

	// Cannot just mix and match these settings - the ciphers are sensitive to the nature of the key, and even the salt length, etc
	// TripleDES did not work for me. Not sure if that's due to possible lack of the unlimited-jurisdiction JARs or not
	private static final String PBE_CIPHER = Defs.ALG_CPHR_PBEDES;
	private static final String PBE_KEYFACT = PBE_CIPHER;
	private static final int PBE_KEYSIZE = DFLT_KEYSIZE;
	private static final int PBE_SALTLEN = 8;
	private static final int PBE_ITERATIONS = 1024;
	private static final String PBE_CHARSET = com.grey.base.utils.StringOps.DFLT_CHARSET;

	public static java.security.Key generateKey(String algname, int keysize) throws java.security.NoSuchAlgorithmException
	{
		if (algname == null) algname = DFLT_ALGNAME;
		if (keysize == 0) keysize = DFLT_KEYSIZE;  // NB: 256 didn't work for AES or Blowfish - not sure whether I had unlimited-JCE installed
		javax.crypto.KeyGenerator kgen = javax.crypto.KeyGenerator.getInstance(algname);
		kgen.init(keysize);
		return kgen.generateKey();
	}

	// The 'enc' parameter to this method corresponds to the byte[] array returned by of java.security.Key.getEncoded() and hence this method
	// allows us to reconstruct a key that was originally created by generateKey(). However, 'enc' could also be random bytes to generate a new
	// key with.
	// I initially wanted to create a javax.crypto.SecretKeyFactory, and pass the SecretKeySpec to its generateSecret() method so that I could
	// return a Key object that way, but the standard JDK (even with the unlimited-jurisdiction JCE download?) does not provide secret-key
	// factories for AES and Blowfish.
	// It turns out that conversion from spec to key is superfluous anyway, as SecretKeySpec is a concrete class which all the the Key, KeySpec
	// and SecretKey interfaces, so we still get to return the desired type.
	public static java.security.Key buildKey(String algname, byte[] enc, int off, int len)
	{
		if (algname == null) algname = DFLT_ALGNAME;
		return new javax.crypto.spec.SecretKeySpec(enc, off, len, algname);
	}

	public static byte[] encrypt(java.security.Key key, java.security.AlgorithmParameters algp, byte[] plaintxt, int off, int len)
			throws java.security.GeneralSecurityException
	{
		return crypt(javax.crypto.Cipher.ENCRYPT_MODE, key, algp, plaintxt, off, len);
	}

	public static byte[] decrypt(java.security.Key key, java.security.AlgorithmParameters algp, byte[] ciphertxt, int off, int len)
			throws java.security.GeneralSecurityException
	{
		return crypt(javax.crypto.Cipher.DECRYPT_MODE, key, algp, ciphertxt, off, len);
	}

	private static byte[] crypt(int opmode, java.security.Key key, java.security.AlgorithmParameters algp,
			byte[] txt, int off, int len) throws java.security.GeneralSecurityException
	{
		javax.crypto.Cipher cphr = javax.crypto.Cipher.getInstance(key.getAlgorithm());
		cphr.init(opmode, key, algp);
		return cphr.doFinal(txt, off, len);
	}


	/*
	 * PBE methods follow
	 */
	public static byte[] encryptPBE(char[] passwd, byte[] plaintxt, int off, int len)
			throws java.security.GeneralSecurityException, java.io.UnsupportedEncodingException
	{
		byte[] salt = new byte[PBE_SALTLEN];
		java.security.SecureRandom srnd = java.security.SecureRandom.getInstance(Defs.ALG_RNG_SHA1);
		srnd.nextBytes(salt);

		javax.crypto.Cipher cphr = setCipher(javax.crypto.Cipher.ENCRYPT_MODE, passwd, salt);
		byte[] ciphertxt = cphr.doFinal(plaintxt, off, len);

		byte[] enctxt = new byte[salt.length + ciphertxt.length];
		System.arraycopy(salt, 0, enctxt, 0, salt.length);
		System.arraycopy(ciphertxt, 0, enctxt, salt.length, ciphertxt.length);
		return enctxt;
	}

	public static byte[] decryptPBE(char[] passwd, byte[] ciphertxt, int off, int len)
			throws java.security.GeneralSecurityException, java.io.UnsupportedEncodingException
	{
		byte[] salt = new byte[PBE_SALTLEN];
		System.arraycopy(ciphertxt, off, salt, 0, salt.length);

		javax.crypto.Cipher cphr = setCipher(javax.crypto.Cipher.DECRYPT_MODE, passwd, salt);
		return cphr.doFinal(ciphertxt, off + salt.length, len - salt.length);
	}

	// Regardless of what key size we pass into the PBEKeyspec constructor, the generated key size (which is given by key.getEncoded().length)
	// corresponds exactly to the 'passwd' length passed into that constructor.
	// The key has to be one of a select number of sizes (128, 256, etc), so it's not feasible to expect the password to be exactly that size and
	// we therefore have to digest the given password chars to produce enough key material to play with if it's too short.
	//
	// An alternative approach here would be to use the digest byte[] array as direct input to the non-PBE-specific buildKey() method. In that
	// case, we would add the Salt to the password before digesting it, for maximum entropy.
	private static java.security.Key generateKey(char[] passwd, byte[] salt)
			throws java.security.GeneralSecurityException, java.io.UnsupportedEncodingException
	{
		int keybytes = PBE_KEYSIZE / 8;

		if (passwd.length < keybytes)
		{
			// Need to digest the password, but the bloody JCE Digest API only takes String or byte[] arguments, and storing a password as a
			// long-lived immutable String is the last thing we want to do, so we must convert it to byte[] first, and then back to char[] for
			// the Key methods (which dont' take byte[] passwords!)
			java.security.MessageDigest mdg = java.security.MessageDigest.getInstance(Defs.ALG_DIGEST_SHA256);
			byte[] bpass = new String(passwd).getBytes(PBE_CHARSET);
			bpass = mdg.digest(bpass);
			passwd = Ascii.hexEncode(bpass);
		}
		if (passwd.length > keybytes) passwd = java.util.Arrays.copyOf(passwd, keybytes);

		javax.crypto.SecretKeyFactory keyfact = javax.crypto.SecretKeyFactory.getInstance(PBE_KEYFACT);
		javax.crypto.spec.PBEKeySpec keyspec = new javax.crypto.spec.PBEKeySpec(passwd, salt, PBE_ITERATIONS, PBE_KEYSIZE);
		java.security.Key key = keyfact.generateSecret(keyspec);
		keyspec.clearPassword();
		return key;
	}

	private static javax.crypto.Cipher setCipher(int opmode, char[] passwd, byte[] salt)
			throws java.security.GeneralSecurityException, java.io.UnsupportedEncodingException
	{
		java.security.Key key = generateKey(passwd, salt);
		javax.crypto.spec.PBEParameterSpec params = new javax.crypto.spec.PBEParameterSpec(salt, PBE_ITERATIONS);
		javax.crypto.Cipher cphr = javax.crypto.Cipher.getInstance(PBE_CIPHER);
		cphr.init(opmode, key, params);
		return cphr;
	}
}
