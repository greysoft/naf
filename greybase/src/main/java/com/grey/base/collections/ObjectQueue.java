/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

/**
 * Simple FIFO queue, with the ability to peek at the head.
 * <br>
 * This class is simpler and more efficient than JDK alternatives based on java.util.Queue, because it does much
 * less (no JDK class seems to offer an unadulterated FIFO!) and above all it is backed by a circular array, to minimise
 * shift and copy ops (will only happen when we grow, which should be never, once we reach steady state).
 */
public final class ObjectQueue<T>
{
	private final Circulist<T> lst;

	public int size() {return lst.size();}
	public int capacity() {return lst.capacity();}
	public void clear() {lst.clear();}
	public T[] toArray(T[] arrcopy) {return lst.toArray(arrcopy);}

	public ObjectQueue() {this(64, 64);}

	public ObjectQueue(int initcap, int incr)
	{
		lst = new Circulist<>(initcap, incr);
	}

	// add to end of queue
	public void add(T obj)
	{
		lst.append(obj);
	}

	// remove and return leading entry on queue
	public T remove()
	{
		if (lst.size() == 0) return null;
		return lst.remove(0);
	}

	// return leading entry on queue without removing it
	public T peek()
	{
		if (lst.size() == 0) return null;
		return peek(0);
	}

	//peek(0) is equivant to peek() with no args, except it doesn't verify queue size
	public T peek(int pos)
	{
		return lst.get(pos);
	}

	// extract object from queue, returning True if it was actually found on the queue
	public boolean withdraw(T obj)
	{
		return lst.remove(obj);
	}

	@Override
	public String toString()
	{
		return "ObjectQueue/"+lst;
	}
}
