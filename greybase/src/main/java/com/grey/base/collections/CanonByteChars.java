/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

import com.grey.base.utils.ByteChars;

/**
 * Implements a canonical set of ByteChars values, akin to the String intern pool.
 */
public final class CanonByteChars
{
	public final String name;
	private final HashedSet<ByteChars> canonset = new HashedSet<ByteChars>(0, 10f); //start small (might not be needed)
	private final ObjectPool<ByteChars> bufpool;

	public int size() {return canonset.size();}

	public CanonByteChars(String name)
	{
		this.name = name;
		bufpool = new ObjectPool<>(() -> new ByteChars());
	}

	public ByteChars intern(ByteChars inpval)
	{
		if (inpval == null) return null;
		ByteChars canonval = canonset.get(inpval);
		if (canonval == null) {
			canonval = bufpool.extract().populate(inpval);
			canonset.add(canonval);
		}
		return canonval;
	}

	public ByteChars intern(CharSequence inpval)
	{
		if (inpval == null) return null;
		ByteChars inpval_bc = bufpool.extract().populate(inpval);
		ByteChars canonval = canonset.get(inpval_bc);
		if (canonval == null) {
			canonval = inpval_bc;
			canonset.add(canonval);
		} else {
			bufpool.store(inpval_bc);
		}
		return canonval;
	}

	public void clear()
	{
		java.util.Iterator<ByteChars> it = canonset.iterator();
		while (it.hasNext()) {
			ByteChars bc = it.next();
			bufpool.store(bc);
		}
		canonset.clear();
	}
}
