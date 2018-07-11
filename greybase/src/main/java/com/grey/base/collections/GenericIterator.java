/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

// Maybe this should be called UniversalIterator!
public final class GenericIterator<T>
{
	private static final int TYPE_SINGLE = 1;
	private static final int TYPE_ARR = 2;
	private static final int TYPE_COLLECTION = 3;

	private int dtype;
	private T single;
	private T[] arr;
	private java.util.Collection<T> stdcoll;
	private java.util.Map<T,?> mapcoll;
	private java.util.Iterator<T> iter_coll;
	private int slot0;
	private int lmt;
	private int curslot;

	public GenericIterator<T> reset(T[] data) {return reset(data, 0, data.length);}

	public GenericIterator<T> reset(T data)
	{
		dtype = TYPE_SINGLE;
		single = data;
		lmt = 1;
		arr = null;
		return reset(0);
	}

	public GenericIterator<T> reset(T[] data, int off, int len)
	{
		dtype = TYPE_ARR;
		arr = data;
		lmt = off + len;
		return reset(off);
	}

	public GenericIterator<T> reset(java.util.Collection<T> data)
	{
		dtype = TYPE_COLLECTION;
		stdcoll = data;
		iter_coll = stdcoll.iterator();
		arr = null;
		return reset(0);
	}

	// we will iterate through the keys, not through the Map.Entry nodes
	public GenericIterator<T> reset(java.util.Map<T,?> data)
	{
		dtype = TYPE_COLLECTION;
		mapcoll = data;
		iter_coll = mapcoll.keySet().iterator();
		arr = null;
		return reset(0);
	}

	private GenericIterator<T> reset(int off)
	{
		if (dtype == TYPE_COLLECTION)
		{
			return this;
		}
		iter_coll = null;
		slot0 = off;
		curslot = slot0;
		return this;
	}

	public boolean hasNext()
	{
		if (iter_coll != null) return iter_coll.hasNext();
		return curslot != lmt;
	}

	public T next()
	{
		if (iter_coll != null) return iter_coll.next();
		if (curslot == lmt) throw new java.util.NoSuchElementException("GenericIterator at limit="+lmt);
		T data = null;

		switch (dtype)
		{
		case TYPE_SINGLE:
			data = single;
			break;
		case TYPE_ARR:
			data = arr[curslot];
			break;
		default:
			throw new Error("Missing case for dtype="+dtype);

		}
		curslot++;
		return data;
	}
}
