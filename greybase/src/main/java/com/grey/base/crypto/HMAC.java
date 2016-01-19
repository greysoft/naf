/*
 * Copyright 2012-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.crypto;

/*
 * This class implements Keyed-Hashing for Message Authentication - see RFC-2104 (Feb 1997)
 */
public final class HMAC
{
	// This class contains pre-computed and pre-allocated objects tailored to a given key.
	// If you're going to repeatedly apply the same key to various data, it is more efficient
	// to do so with the aid of this class.
	// Even if you are applying different keys, you can reuse this class by calling its reset()
	// method, so it is recommended to always use this class when calculating a HMAC hash.
	public static final class KeyMaterial
	{
		private final java.security.MessageDigest hashfunc;
		private final int hashblocksize; //correct for MD5 to SHA-384
		private final byte[] ipadxor; //XOR of inner pad with key
		private final byte[] opadxor; //XOR of outer pad with key
		private byte[] workbuf = new byte[128];//will grow as needed

		public KeyMaterial(String hashname, byte[] key) throws java.security.NoSuchAlgorithmException
		{
			this(java.security.MessageDigest.getInstance(hashname), key);
		}

		// The supplied Digest instance is not expected to be used outside this class from now on.
		// If it is, then it must be reset() before calling HMAC.encode()
		public KeyMaterial(java.security.MessageDigest hf, byte[] key)
		{
			String algname = hf.getAlgorithm();
			if (algname.equals("SHA-384") || algname.equals("SHA-512")) {
				hashblocksize = 128; // see RFCs 4231, 4634, 4868
			} else {
				hashblocksize = 64; //correct for MD5 to SHA-256
			}
			hashfunc = hf;
			ipadxor = new byte[hashblocksize];
			opadxor = new byte[hashblocksize];
			hashfunc.reset();
			if (key != null) reset(key);
		}

		public KeyMaterial reset(byte[] key)
		{
			return reset(key, 0, key.length);
		}

		public KeyMaterial reset(byte[] key, int keyoff, int keylen)
		{
			// minimum key size is recommended to exceed hashfunc.getDigestLength(), but don't interfere
			if (keylen > hashblocksize) {
				hashfunc.update(key, keyoff, keylen);
				key = hashfunc.digest();
				keyoff = 0;
				keylen = key.length;
			}
			System.arraycopy(key, keyoff, ipadxor, 0, keylen);
			java.util.Arrays.fill(ipadxor, keylen, ipadxor.length, (byte)0);
			System.arraycopy(ipadxor, 0, opadxor, 0, ipadxor.length);

			for (int idx = 0; idx != hashblocksize; idx++) {
				ipadxor[idx] ^= 0x36;
				opadxor[idx] ^= 0x5C;
			}
			return this;
		}

		public byte[] encode(byte[] data)
		{
			return encode(data, 0, data.length);
		}

		public byte[] encode(byte[] data, int off, int len)
		{
			byte[] hash = hashWithPad(ipadxor, data, off, len);
			hash = hashWithPad(opadxor, hash, 0, hash.length);
			return hash;
		}

		private byte[] hashWithPad(byte[] pad, byte[] data, int data_off, int data_len)
		{
			int len = pad.length + data_len;
			if (workbuf.length < len) workbuf = new byte[len];
			System.arraycopy(pad, 0, workbuf, 0, pad.length);
			System.arraycopy(data, data_off, workbuf, pad.length, data_len);
			hashfunc.update(workbuf, 0, len);
			return hashfunc.digest();
		}
	}


	// Convenience method. Unless this is a one-off call, the canonical HMAC.encode(byte[], KeyMaterial)
	// method (with calls to KeyMaterial.reset() whenever the key changes) is more efficient, as it
	// reuses the KeyMaterial working buffers.
	public static byte[] encode(String hashname, byte[] key, byte[] data) throws java.security.NoSuchAlgorithmException
	{
		java.security.MessageDigest hashfunc = java.security.MessageDigest.getInstance(hashname);
		return encode(hashfunc, key, data);
	}

	// Convenience method - same performance caveat as the above.
	public static byte[] encode(java.security.MessageDigest hashfunc, byte[] key, byte[] data)
	{
		KeyMaterial km = new KeyMaterial(hashfunc, key);
		return km.encode(data);
	}
}