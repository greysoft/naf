/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

/**
 * Same idea as HashedSet, except with integer keys.
 * More to the point, the JDK collections don't support primitives anyway (not without auto-boxing,
 * which generates garbage for the GC).
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 */
public final class HashedSetInt
{
	private static final Object DUMMYVALUE = new Object();

	private final HashedMapIntKey<Object> map;
	private HashedMapIntKey.KeysIterator<?> recycled_iterator;

	public HashedSetInt() {this(0);}
	public HashedSetInt(int initcap) {this(initcap, 0);}

	public int size() {return map.size();}
	public boolean isEmpty() {return map.isEmpty();}
	public boolean contains(int key) {return map.containsKey(key);}
	public IteratorInt iterator() {return map.keysIterator();}
	public int[] toArray() {return toArray(null);}
	@Override
	public String toString() {return map.toString();}

	public HashedSetInt(int initcap, float factor)
	{
		map = new HashedMapIntKey<Object>(initcap, factor, DUMMYVALUE);
	}

	public void clear()
	{
		map.clear();
	}

	// Same semantics as java.util.Set.add() - returns true if key didn't already exist
	public boolean add(int key)
	{
		return (map.put(key, DUMMYVALUE) == null);
	}

	// Same semantics as java.util.Set.remove() - returns true if key did exist
	public boolean remove(int key)
	{
		return (map.remove(key) == DUMMYVALUE);
	}

	public int[] toArray(int[] arr)
	{
		int siz = map.size();
		if (arr == null || arr.length < siz) arr = new int[siz];
		IteratorInt it = map.keysIterator();
		int idx = 0;
		while (it.hasNext()) {
			arr[idx++] = it.next();
		}
		return arr;
	}

	public IteratorInt recycledIterator()
	{
		if (recycled_iterator == null) {
			recycled_iterator = (HashedMapIntKey.KeysIterator<?>)iterator();
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
