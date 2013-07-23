/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

/**
 * This class implements an efficient object container, in which anonymous objects can be stored and later retrieved.
 * <br/>
 * Anonymous in the sense that this class is indifferent to the objects and callers can't examine the container's contents
 * or refer to specific objects within it.<br/>
 * All you can do is retrieve random objects and then return them to the container, which is literally a pile of objects.
 * As such, this class is well suited to being used as a cache of pre-allocated objects.
 */
public class ObjectPile<T>
{
	public static final String SYSPROP_UNIQUE = "grey.objectpile.unique";
	private static final boolean enforceUnique = SysProps.get(SYSPROP_UNIQUE, false);

	private final java.util.ArrayList<T> cache = new java.util.ArrayList<T>(64);

	public int size() {return cache.size();}

	/*
	 * The obj param cannot be null, and it is also expected not to already be on the pile.
	 * The latter is not enforced unless the grey.objectpile.unique System property is set
	 * to True (set at JVM startup).
	 * Uniqueness is here defined in terms of identity, not equality.
	 */
	public void store(T obj)
	{
		if (enforceUnique) {
			for (int idx = 0; idx != cache.size(); idx++) {
				if (obj == cache.get(idx)) {
					throw new IllegalArgumentException("ObjectPile: Duplicates not allowed - "+obj);
				}
			}
		}
		if (obj == null) throw new NullPointerException("ObjectPile: Nulls not allowed");
		cache.add(obj);
	}

	public void bulkStore(T[] arr, int off, int len)
	{
		int lmt = off + len;
		for (int idx = off; idx != lmt; idx++) {
			store(arr[idx]);
		}
	}

	public void bulkStore(java.util.Collection<T> coll)
	{
		java.util.Iterator<T> it = coll.iterator();
		while (it.hasNext()) {
			store(it.next());
		}
	}

	// Returns Null if the pile is empty
	public T extract()
	{
		if (cache.size() == 0) return null;
		return cache.remove(cache.size() - 1);
	}
	
	public void clear()
	{
		cache.clear();
	}
}
