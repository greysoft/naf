/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class CanonByteCharsTest
{
	@org.junit.Test
	public void testSuite()
	{
		CanonByteChars canon = new CanonByteChars();
		org.junit.Assert.assertEquals(0, canon.size());
		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());

		ByteChars bc_in = new ByteChars("Item1");
		ByteChars bc_out = canon.intern(bc_in);
		org.junit.Assert.assertEquals(1, canon.size());
		org.junit.Assert.assertFalse(bc_out == bc_in);
		org.junit.Assert.assertFalse(canon.isCanon(bc_in));
		org.junit.Assert.assertTrue(canon.isCanon(bc_out));
		ByteChars bc1 = bc_out;
		bc_out = canon.intern(bc1);
		org.junit.Assert.assertTrue(bc_out == bc1);
		org.junit.Assert.assertTrue(canon.isCanon(bc_out));

		bc_in = new ByteChars("Item2");
		bc_out = canon.intern(bc_in);
		org.junit.Assert.assertEquals(2, canon.size());
		org.junit.Assert.assertFalse(bc_out == bc_in);
		org.junit.Assert.assertFalse(canon.isCanon(bc_in));
		org.junit.Assert.assertTrue(canon.isCanon(bc_out));
		ByteChars bc2 = bc_out;
		org.junit.Assert.assertFalse(bc1 == bc2);
		bc_out = canon.intern(bc2);
		org.junit.Assert.assertTrue(bc_out == bc2);
		org.junit.Assert.assertTrue(canon.isCanon(bc_out));

		bc_in = new ByteChars("Item1");
		bc_out = canon.intern(bc_in);
		org.junit.Assert.assertEquals(2, canon.size());
		org.junit.Assert.assertFalse(bc_out == bc_in);
		org.junit.Assert.assertTrue(bc_out == bc1);
		org.junit.Assert.assertFalse(canon.isCanon(bc_in));
		org.junit.Assert.assertTrue(canon.isCanon(bc_out));

		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());
		org.junit.Assert.assertFalse(canon.isCanon(bc1));
		org.junit.Assert.assertFalse(canon.isCanon(bc2));
		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());
	}
}
