/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

import com.grey.base.utils.StringOps;
import com.grey.base.utils.ByteChars;

public class CanonByteCharsTest
{
	@org.junit.Test
	public void testBC()
	{
		CanonByteChars canon = new CanonByteChars("utest_bc");
		org.junit.Assert.assertEquals(0, canon.size());
		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());
		ByteChars bc_out = canon.intern(null);
		org.junit.Assert.assertNull(bc_out);
		org.junit.Assert.assertEquals(0, canon.size());

		ByteChars inpval = new ByteChars("Item1");
		bc_out = canon.intern(inpval);
		org.junit.Assert.assertEquals(1, canon.size());
		org.junit.Assert.assertFalse(bc_out == inpval);
		org.junit.Assert.assertTrue(inpval.equals(bc_out));
		ByteChars bc1 = bc_out;
		bc_out = canon.intern(bc1);
		org.junit.Assert.assertTrue(bc_out == bc1);
		org.junit.Assert.assertEquals(1, canon.size());

		inpval = new ByteChars("Item2");
		bc_out = canon.intern(inpval);
		org.junit.Assert.assertEquals(2, canon.size());
		org.junit.Assert.assertFalse(bc_out == inpval);
		org.junit.Assert.assertTrue(inpval.equals(bc_out));
		ByteChars bc2 = bc_out;
		org.junit.Assert.assertFalse(bc1 == bc2);
		bc_out = canon.intern(bc2);
		org.junit.Assert.assertTrue(bc_out == bc2);
		org.junit.Assert.assertEquals(2, canon.size());

		inpval = new ByteChars("Item1");
		bc_out = canon.intern(inpval);
		org.junit.Assert.assertEquals(2, canon.size());
		org.junit.Assert.assertFalse(bc_out == inpval);
		org.junit.Assert.assertTrue(bc_out == bc1);

		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());
		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());
	}

	@org.junit.Test
	public void testCharSeq()
	{
		CanonByteChars canon = new CanonByteChars("utest_charseq");
		ByteChars bc_out = canon.intern(null);
		org.junit.Assert.assertNull(bc_out);
		org.junit.Assert.assertEquals(0, canon.size());

		String inpval = "Item1";
		bc_out = canon.intern(inpval);
		org.junit.Assert.assertEquals(1, canon.size());
		org.junit.Assert.assertTrue(inpval.equals(bc_out.toString()));
		org.junit.Assert.assertTrue(StringOps.sameSeq(inpval, bc_out));
		ByteChars bc1 = bc_out;
		bc_out = canon.intern(bc1);
		org.junit.Assert.assertTrue(bc_out == bc1);
		org.junit.Assert.assertEquals(1, canon.size());

		inpval = "Item2";
		bc_out = canon.intern(inpval);
		org.junit.Assert.assertEquals(2, canon.size());
		org.junit.Assert.assertTrue(inpval.equals(bc_out.toString()));
		org.junit.Assert.assertTrue(StringOps.sameSeq(inpval, bc_out));
		ByteChars bc2 = bc_out;
		org.junit.Assert.assertFalse(bc1 == bc2);
		bc_out = canon.intern(bc2);
		org.junit.Assert.assertTrue(bc_out == bc2);
		org.junit.Assert.assertEquals(2, canon.size());

		inpval = "Item1";
		bc_out = canon.intern(inpval);
		org.junit.Assert.assertEquals(2, canon.size());
		org.junit.Assert.assertTrue(bc_out == bc1);

		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());
		canon.clear();
		org.junit.Assert.assertEquals(0, canon.size());
	}
}
