/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

public class HMACTest
{
	private static final java.util.Random randgen = new java.util.Random(0);

	@org.junit.Test
	public void testRFC2195() throws java.security.NoSuchAlgorithmException, java.security.InvalidKeyException
	{
		// these are the inputs and output illustrated in RFC-2195
		byte[] key = "tanstaaftanstaaf".getBytes(); //shared secret
		byte[] data = "<1896.697170952@postoffice.reston.mci.net>".getBytes();  //challenge
		String algname = "MD5";
		String hexhash = "b913a602c7eda7a495b4e6e7334d3890"; //hex encoding of resulting HMAC-MD5 hash

		HMAC.KeyMaterial km = new HMAC.KeyMaterial(algname, null);
		int offset = 10;
		byte[] keybuf = new byte[key.length + (offset * 2)];
		java.util.Arrays.fill(keybuf, (byte)125);
		System.arraycopy(key, 0, keybuf, offset, key.length);
		km.reset(keybuf, offset, key.length);
		byte[] hash = HMAC.encode(km, data);
		org.junit.Assert.assertEquals(hexhash, hexEncode(hash));
		//make sure consecutive calls work
		hash = HMAC.encode(km, data);
		org.junit.Assert.assertEquals(hexhash, hexEncode(hash));
		//test the convenience method
		hash = HMAC.encode(algname, key, data);
		org.junit.Assert.assertEquals(hexhash, hexEncode(hash));

		// Sanity-check our usage of the JDK API, before we use it elsewhere.
		// API usage: http://docs.oracle.com/javase/6/docs/technotes/guides/security/crypto/CryptoSpec.html#HmacEx
		// Algorithm names: http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html
		// Note that the algorithm doesn't seem to matter when creating the key here.
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacMD5");
		javax.crypto.spec.SecretKeySpec kspec = new javax.crypto.spec.SecretKeySpec(key, mac.getAlgorithm());
        mac.init(kspec);
        hash = mac.doFinal(data);
		org.junit.Assert.assertEquals(hexhash, hexEncode(hash));
		// verify consecutive calls
        hash = mac.doFinal(data);
		org.junit.Assert.assertEquals(hexhash, hexEncode(hash));
	}

	@org.junit.Test
	public void testLargeKey()
			throws java.security.NoSuchAlgorithmException, java.security.InvalidKeyException
	{
		byte[] key = new byte[2057];  //anything >64 is "large"
		randgen.nextBytes(key);
		byte[] data = new byte[27001];
		randgen.nextBytes(data);
		verifyHash(key, data);
	}

	@org.junit.Test
	public void testSmallKey()
			throws java.security.NoSuchAlgorithmException, java.security.InvalidKeyException
	{
		byte[] key = new byte[10];  //anything shorter than digest length will do
		randgen.nextBytes(key);
		byte[] data = new byte[27001];
		randgen.nextBytes(data);
		verifyHash(key, data);
	}

	private static void verifyHash(byte[] key, byte[] data)
			throws java.security.NoSuchAlgorithmException, java.security.InvalidKeyException
	{
		for (String alg : new String[]{"MD5", "1", "256", "384", "512"}) {
			String algname = alg;
			String macname = "Hmac"+alg;
			if (!alg.equals("MD5")) {
				algname = "SHA-"+alg;
				macname = "HmacSHA"+alg;
			}
	        javax.crypto.Mac mac = javax.crypto.Mac.getInstance(macname);
			javax.crypto.spec.SecretKeySpec kspec = new javax.crypto.spec.SecretKeySpec(key, mac.getAlgorithm());
	        mac.init(kspec);
	        byte[] jdkhash = mac.doFinal(data);
	        //verify our HMAC code gives same answer
			HMAC.KeyMaterial km = new HMAC.KeyMaterial(algname, key);
			byte[] hash = HMAC.encode(km, data);
			org.junit.Assert.assertTrue(algname, java.util.Arrays.equals(hash, jdkhash));
			// test repeat call
			km.reset(key);
			byte[] hash2 = HMAC.encode(km, data);
			org.junit.Assert.assertTrue(algname+"-repeat", java.util.Arrays.equals(hash, hash2));
		}
	}

	private static String hexEncode(byte[] buf)
	{
		return new String(Ascii.hexEncode(buf));
	}
}