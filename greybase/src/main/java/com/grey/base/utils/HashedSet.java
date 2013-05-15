/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Zero-garbage, memory-efficient replacement for java.util.HashSet.
 * <br/>
 * This class may be preferable to the standard JDK HashSet for high-frequency caches with short-lived entries, as
 * it is based on the com.grey.base.utils.HashedMap class with all the advantages it advertises in terms of reduced
 * memory churn and garbage generation.
 * In addition, whereas the JDK's HashSet uses a full HashMap to store its keys, thus wasting unused memory to store its
 * unused values, this class uses a special mode of the GreyBase HashedSet class which doesn't allocate any values, and
 * hence uses only half the total memory.
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 */
public final class HashedSet<E>
	implements java.util.Set<E>
{
	private final HashedMap<E, E> map;

	public HashedSet() {this(0);}
	public HashedSet(int initcap) {this(initcap, 0);}
	
	public HashedSet(int initcap, float factor)
	{
		map = new HashedMap<E, E>(initcap, factor, true);
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
		return map.get(obj);
	}

	@Override
	public boolean add(E obj)
	{
		if (contains(obj)) return false;
		map.put(obj, null);
		return true;
	}

	@Override
	public boolean remove(Object obj)
	{
		if (!contains(obj)) return false;
		map.remove(obj);
		return true;
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
		return map.keySet().iterator();
	}

	public java.util.Iterator<E> recycledIterator()
	{
		return map.keysIterator();
	}

	@Override
	public Object[] toArray()
	{
		return map.keySet().toArray();
	}

	@Override
	public <T> T[] toArray(T[] arr)
	{
		return map.keySet().toArray(arr);
	}

	@Override
	public String toString()
	{
		return map.toString();
	}
}