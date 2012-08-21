/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Implements a canonical set of ByteChars values, akin to the String intern pool.
 */
public final class CanonByteChars
{
	private final HashedMap<ByteChars,ByteChars> canonset = new HashedMap<ByteChars,ByteChars>(0, 10f); //start small (might not be needed)
	private final ObjectWell<ByteChars> bufpool = new ObjectWell<ByteChars>(ByteChars.class);

	public int size() {return canonset.size();}

	public boolean isCanon(ByteChars bc)
	{
		ByteChars canonval = canonset.get(bc);
		return (bc == canonval);
	}

	public ByteChars intern(ByteChars bc)
	{
		ByteChars canonval = canonset.get(bc);
		if (canonval != null) return canonval;
		canonval = bufpool.extract();
		canonval.set(bc);
		canonset.put(canonval, canonval);
		return canonval;
	}

	public void clear()
	{
		java.util.Iterator<ByteChars> it = canonset.keysIterator();

		while (it.hasNext()) {
			ByteChars bc = it.next();
			bufpool.store(bc);
		}
		canonset.clear();
	}
}
