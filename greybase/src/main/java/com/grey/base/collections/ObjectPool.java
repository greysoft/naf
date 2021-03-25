/*
 * Copyright 2018-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

import com.grey.base.config.SysProps;

/**
 * A more modern and less cumbersome replacement for ObjectWell.
 */
public final class ObjectPool<T>
{
	public static final boolean DEBUG = SysProps.get("grey.objectpool.debug", false);

	private final Supplier<T> factory;
	private final List<T> cache = new ArrayList<>();
	private final int maxItems;  //zero means no limit
	private final int increment; //number of new objects to create when pool is empty

	private int active; //the number of items that are currently allocated

	public int getActiveCount() {return active;}

	public ObjectPool(Supplier<T> factory, int initial, int max, int incr) {
		if (initial < 0) throw new IllegalArgumentException("Initial ObjectPool cannot be negative");
		if (max < 0) throw new IllegalArgumentException("Max ObjectPool cannot be negative");
		if (incr < 1) throw new IllegalArgumentException("ObjectPool increment must be a positive integer");
		this.factory = factory;
		maxItems = max;
		increment = incr;
		allocate(initial);
	}

	public ObjectPool(Supplier<T> factory) {
		this(factory, 0, 0, 1);
	}

	/**
	 * Obtain an item from the pool
	 * @return An instance of the the objects stored on this pool.
	 */
	public T extract() {
		if (cache.isEmpty()) {
			int newTotal = active + increment;
			if (maxItems != 0 && newTotal > maxItems) newTotal = maxItems;
			int delta = newTotal - active;
			if (delta <= 0) throw new IllegalStateException("Object-Pool-"+factory+" cannot allocate any more objects - extant="+active+" vs max="+maxItems);
			allocate(delta);
		}
		active++;
		return pop();
	}

	/**
	 * Return an item to the pool, that was previously obtained with extract()
	 * @param obj The object to restore to the pool.
	 */
	public void store(T obj) {
		cache.add(obj);
		active--;
	}

	public void prune(int maxSpares) {
		while (cache.size() > maxSpares) {
			pop();
		}
	}

	private void allocate(int qty) {
		for (int loop = 0; loop != qty; loop++) {
			T item = factory.get();
			cache.add(item);
		}
	}

	private T pop() {
		return cache.remove(cache.size()-1);
	}
}