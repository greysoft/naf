/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Same idea as HashedSet, except with integer keys.
 * More to the point, the JDK collections don't support primitives anyway (not without auto-boxing,
 * which generates garbage for the GC).
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 */
public final class HashedSetInt
{
	private final HashedMapIntInt map;
	private HashedMapIntInt.KeysIterator recycled_iterator;

	public HashedSetInt() {this(0);}
	public HashedSetInt(int initcap) {this(initcap, 0);}

	public int size() {return map.size();}
	public boolean isEmpty() {return map.isEmpty();}
	public boolean contains(int key) {return map.containsKey(key);}
	public IteratorInt iterator() {return map.keysIterator();}
	public int[] toArray() {return toArray(null);}
	public int[] toArray(int[] arr) {return map.toArrayKeys(arr);}
	@Override
	public String toString() {return map.toString();}

	public HashedSetInt(int initcap, float factor)
	{
		map = new HashedMapIntInt(initcap, factor, true);
	}

	public void clear()
	{
		map.clear();
	}

	public boolean add(int key)
	{
		if (contains(key)) return false;
		map.put(key, 0);
		return true;
	}

	public boolean remove(int key)
	{
		if (!contains(key)) return false;
		map.remove(key);
		return true;
	}

	public IteratorInt recycledIterator()
	{
		if (recycled_iterator == null) {
			recycled_iterator = HashedMapIntInt.KeysIterator.class.cast(iterator());
		} else {
			recycled_iterator.reset();
		}
		return recycled_iterator;
	}

	public int trimToSize()
	{
		return map.trimToSize();
	}
}
