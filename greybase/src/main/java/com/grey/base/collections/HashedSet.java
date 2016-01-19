/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

/**
 * This class implements the {@link java.util.Set} interface, and serves as a zero-garbage memory-efficient replacement
 * for the JRE's {@link java.util.HashSet} class.
 * <br>
 * This class may be preferable to the JRE-provided HashSet for high-frequency caches with short-lived entries, as it is
 * based on the {@link HashedMapIntValue} class with all the advantages that advertises in terms of reduced memory churn
 * and garbage generation.
 * <br>
 * In addition, this class has a much smaller memory footprint. Whereas the JDK's HashSet uses a full HashMap with Entry
 * objects to store its keys, this class uses a special mode of the already much slimmer GreyBase HashedMapIntValue class,
 * which avoid allocating any storage for its unused Values.
 * <p>
 * Like the JRE's {@link java.util.HashSet}, this class implements all optional {@link java.util.Set} operations.<br>
 * Beware that this class is single-threaded and non-reentrant.
 */
public final class HashedSet<E>
	implements java.util.Set<E>
{
	private final HashedMapIntValue<E> map;

	public HashedSet() {this(0);}
	public HashedSet(int initcap) {this(initcap, 0);}

	public HashedSet(int initcap, float factor)
	{
		map = new HashedMapIntValue<E>(initcap, factor, true);
	}

	@Override
	public int size()
	{
		return map.size();
	}

	@Override
	public boolean isEmpty()
	{
		return map.isEmpty();
	}

	@Override
	public boolean contains(Object obj)
	{
		return map.containsKey(obj);
	}

	// This is useful for obtaining a unique interned instance of the given value
	public E get(E obj)
	{
		return map.getKey(obj);
	}

	@Override
	public boolean add(E obj)
	{
		return (map.put(obj, 0) == 0);
	}

	@Override
	public boolean remove(Object obj)
	{
		return (map.remove(obj) == 1);
	}

	@Override
	public void clear()
	{
		map.clear();
	}

	@Override
	public boolean containsAll(java.util.Collection<?> coll)
	{
		java.util.Iterator<?> it = coll.iterator();
		while (it.hasNext()) {
			if (!contains(it.next())) return false;
		}
		return true;
	}

	@Override
	public boolean addAll(java.util.Collection<? extends E> coll)
	{
		boolean modified = false;
		java.util.Iterator<? extends E> it = coll.iterator();
		while (it.hasNext()) {
			if (add(it.next())) modified = true;
		}
		return modified;
	}

	@Override
	public boolean retainAll(java.util.Collection<?> coll)
	{
		boolean modified = false;
		java.util.Iterator<E> it = iterator();
		while (it.hasNext()) {
			if (!coll.contains(it.next())) {
				it.remove();
				modified = true;
			}
		}
		return modified;
	}

	@Override
	public boolean removeAll(java.util.Collection<?> coll)
	{
		boolean modified = false;
		java.util.Iterator<?> it = coll.iterator();
		while (it.hasNext()) {
			if (remove(it.next())) modified = true;
		}
		return modified;
	}

	@Override
	public java.util.Iterator<E> iterator()
	{
		return map.keysIterator();
	}

	public java.util.Iterator<E> recycledIterator()
	{
		return map.recycledKeysIterator();
	}

	@Override
	public Object[] toArray()
	{
		return toArray(new Object[map.size()]);
	}

	@Override
	public <T> T[] toArray(T[] arr)
	{
		if (arr.length < map.size()) {
			@SuppressWarnings("unchecked")
			T[] unchecked = (T[])java.lang.reflect.Array.newInstance(arr.getClass().getComponentType(), map.size());
			arr = unchecked;
		}
		java.util.Iterator<E> it = map.keysIterator();
		int idx = 0;
		while (it.hasNext()) {
			@SuppressWarnings("unchecked") T elem = (T)it.next();
			arr[idx++] = elem;
		}
		if (arr.length > idx) arr[idx] = null;
		return arr;
	}

	@Override
	public String toString()
	{
		return map.toString();
	}

	public int trimToSize()
	{
		return map.trimToSize();
	}
}
