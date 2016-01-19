/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

import java.util.ConcurrentModificationException;

/**
 * This class implements the {@link java.util.Map} interface, and serves as a zero-garbage replacement for the JRE's {@link java.util.HashMap} class.
 * <br>
 * This class may be preferable to the JRE-provided HashMap for high-frequency caches with short-lived entries, as the JRE class not only allocates
 * new memory (in the shape of the Entry class, a linked list of which is rooted in each table slot) when inserting each new key, but also releases
 * that memory when the key is removed. The standard HashMap would therefore generate too much garbage in such a scenario, whereas this class never
 * frees its own memory (though it does of course free its references to deleted key/value pairs) and keeps empty bucket positions available for re-use.
 * <br>
 * This implementation also has a smaller memory footprint than the JRE HashMap - and smaller than HashedMapEntries as well.
 * <p>
 * Beware that this class reuses the returned {@link java.util.Map.Entry} objects when iterating on the entrySet() view.<br>
 * This is perfectly legal, and well-written applications should be aware of this and not hold references to Map.Entry objects
 * beyond each iteration loop, but some applications may nevertheless fall foul.<br>
 * If this does cause a problem for badly implemented applications, then they should use {@link HashedMapEntries} instead of this class,
 * or alternatively, set the JVM system property -Dgrey.hashedmap.noreuse=Y.<br>
 * The latter will however negate the primary advantage of this class, namely its non-generation of any garbage, as that will
 * cause it to allocate a new Map.Entry object on each iteration of an entrySet() collection.<br>
 * FindBugs warns Map implementors not to reuse Map.Entry objects (PZ_DONT_REUSE_ENTRY_OBJECTS_IN_ITERATORS) but concedes it
 * is permitted, and its recommendation is merely good practice to accomodate poorly coded applications. However, this class
 * takes the view that it is not willing to compromise its memory efficiency, and believes it is sufficient to alert its users
 * to possible pitfalls.
 * <p>
 * This class does return independent Map.Entry nodes in the entrySet() view's toArray() methods, as required by the Java Map spec.
 * <p>
 * Like the JRE's {@link java.util.HashMap}, this class implements all optional {@link java.util.Map} operations.<br>
 * Beware that this class is single-threaded and non-reentrant.
 *
 * @see HashedMapEntries
 */
public final class HashedMap<K,V>
	implements java.util.Map<K,V>
{
	static final boolean MAPENTRY_NOREUSE = com.grey.base.config.SysProps.get("grey.hashedmap.noreuse", false);

	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 0.8f;
	private static final int BUCKETCAP_INCR = 8; //growth increment of bucket array
	private static final int KVSPAN = 2; //adjacent K-V pair take up 2 slots

	private final float loadfactor;

	private int threshold;
	private int hashmask;
	private int entrycnt; //total population

	int capacity; //this is just the number of buckets, which is much less than real capacity as each one is an array
	Object[][] buckets; //each bucket contains key/value in alternating slots
	int[] bucketsizes; //total occupied size of each bucket, counting both the key and the value
	int modcnt;

	//these classes are stateless, so allocate just once, on first usage
	private KeysCollection<K,V> keysview;
	private ValuesCollection<K,V> valuesview;
	private EntriesCollection<K,V> entriesview;

	// recycled iterators
	private KeysIterator<K,V> keys_iterator;
	private ValuesIterator<K,V> values_iterator;
	private EntriesIterator<K,V> entries_iterator;

	public HashedMap() {this(0);}
	public HashedMap(int initcap) {this(initcap, 0);}

	@Override
	public boolean isEmpty() {return (entrycnt == 0);}
	@Override
	public int size() {return entrycnt;}

	public HashedMap(int initcap, float factor)
	{
		if (initcap == 0) initcap = DFLT_CAP;
		if (factor == 0) factor = DFLT_LOADFACTOR;
		loadfactor = factor;

		// find min power-of-2 size that holds initcap entries
		capacity = 1;
		while (capacity < initcap) capacity <<= 1;

		allocateBuckets();
	}

	// Note that zeroing a bucketsizes slot is enough to invalidate all the keys in that bucket, but we need to explicitly
	// nullify the Objects refs for the sake of the GC.
	@Override
	public void clear()
	{
		for (int idx = buckets.length - 1; idx != -1; idx--) {
			if (buckets[idx] != null) java.util.Arrays.fill(buckets[idx], null);
		}
		java.util.Arrays.fill(bucketsizes, 0);
		entrycnt = 0;
		modcnt++;
	}

	@Override
	public boolean containsKey(Object key)
	{
		if (key == null) {
			// Null key hashes to zero
			final Object[] bucket = buckets[0];
			int bktsiz = bucketsizes[0];
			for (int idx = 0; idx != bktsiz; idx += KVSPAN) {
				if (bucket[idx] == null) return true;
			}
		} else {
			final int bktid = getBucket(key);
			final Object[] bucket = buckets[bktid];
			int bktsiz = bucketsizes[bktid];
			for (int idx = 0; idx != bktsiz; idx += KVSPAN) {
				Object k = bucket[idx];
				if (key == k || key.equals(k)) return true;
			}
		}
		return false;
	}

	// keep this aligned with containsKey() logic
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key)
	{
		if (key == null) {
			// Null key hashes to zero
			final Object[] bucket = buckets[0];
			int bktsiz = bucketsizes[0];
			for (int idx = 0; idx != bktsiz; idx += KVSPAN) {
				if (bucket[idx] == null) return (V)bucket[idx+1];
			}
		} else {
			final int bktid = getBucket(key);
			final Object[] bucket = buckets[bktid];
			int bktsiz = bucketsizes[bktid];
			for (int idx = 0; idx != bktsiz; idx += KVSPAN) {
				Object k = bucket[idx];
				if (key == k || key.equals(k)) return (V)bucket[idx+1];
			}
		}
		return null;
	}

	@Override
	public V put(K key, V value)
	{
		if (entrycnt == threshold) {
			// It may turn out that we're replacing an existing value rather than adding a new mapping, but even that means we're infinitesmally
			// close to exceeding the threshold, so grow the hash table now anyway.
			capacity <<= 1; // double the capacity
			allocateBuckets();
		}
		final int bktid = (key == null ? 0 : getBucket(key));
		final int bktsiz = bucketsizes[bktid];
		Object[] bucket = buckets[bktid];
		int slot = 0;

		if (bucket == null) {
			// this bucket doesn't exist yet, so its obviously a new key, and we need to create its bucket
			bucket = growBucket(bktid);
		} else {
			// check if key already exists
			if (key == null) {
				while (slot != bktsiz) {
					if (bucket[slot] == null) break; //found the key
					slot += KVSPAN;
				}
			} else {
				while (slot != bktsiz) {
					Object k = bucket[slot];
					if (key == k || key.equals(k)) break; //found the key
					slot += KVSPAN;
				}
			}

			if (slot == bucket.length) {
				// not only did we not find a match, but the bucket is full so we'll have to grow it before appending this new key to it
				bucket = growBucket(bktid);
			}
		}
		Object oldvalue;

		if (slot == bktsiz) {
			// adding a new key
			bucket[slot] = key;
			bucketsizes[bktid] += KVSPAN;
			entrycnt++;
			modcnt++;
			oldvalue = null;
		} else {
			// updating the value of an existing key
			oldvalue = bucket[slot+1];
		}
		bucket[slot+1] = value;

		@SuppressWarnings("unchecked") V typedval = (V)oldvalue;
		return typedval;
	}

	@Override
	public V remove(Object key)
	{
		final int bktid = (key == null ? 0 : getBucket(key));
		final Object[] bucket = buckets[bktid];
		final int bktsiz = bucketsizes[bktid];
		int slot = -1;

		if (key == null) {
			for (int idx = 0; idx != bktsiz; idx += KVSPAN) {
				if (bucket[idx] == null) {
					slot = idx;
					break;
				}
			}
		} else {
			for (int idx = 0; idx != bktsiz; idx += KVSPAN) {
				Object k = bucket[idx];
				if (key == k || key.equals(k)) {
					slot = idx;
					break;
				}
			}
		}
		if (slot == -1) return null;

		@SuppressWarnings("unchecked") V oldval = (V)bucket[slot+1];
		remove(bucket, bktid, bktsiz, slot);
		return oldval;
	}

	void remove(Object[] bucket, int bktid, int bktsiz, int slot)
	{
		int finalslot = bktsiz - KVSPAN;
		if (finalslot != slot) {
			// shorten this bucket by swapping final K-V pair into the deleted slots we're now vacating
			System.arraycopy(bucket, finalslot, bucket, slot, KVSPAN);
		}
		bucket[finalslot] = null;
		bucket[++finalslot] = null;
		bucketsizes[bktid] -= KVSPAN;
		entrycnt--;
		modcnt++;
	}

	@Override
	public boolean containsValue(Object val)
	{
		if (val == null) {
			for (int idx = buckets.length - 1; idx != -1; idx--) {
				final Object[] bucket = buckets[idx];
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2 -= KVSPAN) {
					if (bucket[idx2] == null) return true;
				}
			}
		} else {
			for (int idx = buckets.length - 1; idx != -1; idx--) {
				final Object[] bucket = buckets[idx];
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2 -= KVSPAN) {
					Object v = bucket[idx2];
					if (val == v || val.equals(v)) return true;
				}
			}
		}
		return false;
	}

	@Override
	public void putAll(java.util.Map<? extends K, ? extends V> srcmap)
	{
		java.util.Set<? extends java.util.Map.Entry<? extends K, ? extends V>> srcitems = srcmap.entrySet();
		java.util.Iterator<? extends java.util.Map.Entry<? extends K, ? extends V>> itsrc = srcitems.iterator();

		while (itsrc.hasNext()) {
			java.util.Map.Entry<? extends K, ? extends V> entry = itsrc.next();
			put(entry.getKey(), entry.getValue());
		}
	}

	// The object's hash function maps the object to an arbitrary 32-bit number, which is hopefully reasonably distinct.
	// This method in turn converts that into an index into our hash table, which means the return value is considerably
	// less than 32 bits.
	private int getBucket(Object key)
	{
		return objectHash(key) & hashmask;
	}

	// We're going to assume that the object's hash function is uniformly distributed over all bits (True for String and ByteChars).
	// Note that the JDK HashMap class applies an extra hash to key.hashCode() to improve poor hashes (the JDK Integer class being
	// an obvious example) but we don't bother. Could wrap return value in HashedMapIntKey.intHash() if we cared.
	static int objectHash(Object key)
	{
		return key.hashCode();
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

		final Object[][] oldbuckets = buckets;
		final int[] oldsizes = bucketsizes;
		buckets = new Object[capacity][];
		bucketsizes = new int[capacity];

		if (oldbuckets != null) {
			for (int idx = 0; idx != oldbuckets.length; idx++) {
				Object[] oldbucket = oldbuckets[idx];
				int oldsiz = oldsizes[idx];
				for (int idx2 = 0; idx2 != oldsiz; idx2 += KVSPAN) {
					@SuppressWarnings("unchecked") K k = (K)oldbucket[idx2];
					@SuppressWarnings("unchecked") V v = (V)oldbucket[idx2+1];
					put(k, v);
				}
			}
		}
	}

	private Object[] growBucket(int bktid)
	{
		Object[] oldbucket = buckets[bktid];
		int oldsiz = (oldbucket == null ? 0 : oldbucket.length);
		int newsiz = oldsiz + BUCKETCAP_INCR;

		Object[] newbucket = new Object[newsiz];
		if (oldbucket != null) System.arraycopy(oldbucket, 0, newbucket, 0, oldbucket.length);

		buckets[bktid] = newbucket;
		return newbucket;
	}

	public int trimToSize()
	{
		int newcap = 1;
		while (((int)(newcap * loadfactor)) <= entrycnt) newcap <<= 1;
		if (newcap == capacity) return capacity;
		capacity = newcap;
		allocateBuckets();
		modcnt++;
		return capacity;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName()).append('=').append(size()).append(" {");
		String dlm = "";
		for (int idx = 0; idx != buckets.length; idx++) {
			Object[] bucket = buckets[idx];
			int bktsiz = bucketsizes[idx];
			for (int idx2 = 0; idx2 != bktsiz; idx2 += KVSPAN) {
				sb.append(dlm).append(bucket[idx2]).append('=').append(bucket[idx2+1]);
				dlm = ", ";
			}
		}
		sb.append("}");
		return sb.toString();
	}

	/*
	 * These required Map methods provide Collections views of this object.
	 */
	@Override
	public java.util.Set<K> keySet()
	{
		if (keysview == null) keysview = new KeysCollection<K,V>(this);
		return keysview;
	}

	@Override
	public java.util.Collection<V> values()
	{
		if (valuesview == null) valuesview = new ValuesCollection<K,V>(this);
		return valuesview;
	}

	@Override
	public java.util.Set<java.util.Map.Entry<K,V>> entrySet()
	{
		if (entriesview == null) entriesview = new EntriesCollection<K,V>(this);
		return entriesview;
	}

	/**
	 * This returns a recycled keys iterator.<br>
	 * Whereas keySet().iterator() creates a new Iterator object, this method returns a single instance of that iterator
	 * type, that is associated with this Map object.<br>
	 * This is not a standard Map method, and is obviously unsuitable for multi-threaded use.
	 * @return The reusable keys iterator. It is created on the first call to this method.
	 */
	public java.util.Iterator<K> keysIterator()
	{
		if (keys_iterator == null) {
			keys_iterator = new KeysIterator<K,V>(this);
		} else {
			keys_iterator.reset();
		}
		return keys_iterator;
	}

	/**
	 * This returns a recycled values iterator.<br>
	 * Whereas values().iterator() creates a new Iterator object, this method returns a single instance of that iterator
	 * type, that is associated with this Map object.<br>
	 * This is not a standard Map method, and is obviously unsuitable for multi-threaded use.
	 * @return The reusable values iterator. It is created on the first call to this method.
	 */
	public java.util.Iterator<V> valuesIterator()
	{
		if (values_iterator == null) {
			values_iterator = new ValuesIterator<K,V>(this);
		} else {
			values_iterator.reset();
		}
		return values_iterator;
	}

	/**
	 * This returns a recycled Map.Entry iterator.<br>
	 * Whereas entrySet().iterator() creates a new Iterator object, this method returns a single instance of that iterator
	 * type, that is associated with this Map object.<br>
	 * This is not a standard Map method, and is obviously unsuitable for multi-threaded use.
	 * @return The reusable entries iterator. It is created on the first call to this method.
	 */
	public java.util.Iterator<java.util.Map.Entry<K, V>> entriesIterator()
	{
		if (entries_iterator == null) {
			entries_iterator = new EntriesIterator<K,V>(this);
		} else {
			entries_iterator.reset();
		}
		return entries_iterator;
	}

	// provide access to private members for the inner classes
	@SuppressWarnings("unchecked")
	K getMapKey(int id, int slot) {return (K)buckets[id][slot];}
	@SuppressWarnings("unchecked")
	V getMapValue(int id, int slot) {return (V)buckets[id][slot+1];}

	static boolean compareObjects(Object o1, Object o2) {
		if (o1 == null) return (o2 == null);
		return (o1 == o2 || o1.equals(o2));
	}

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support the required Collections views of this map.
	 * ===================================================================================================================
	 */

	private static final class KeysCollection<K, V>
		extends java.util.AbstractSet<K>
	{
		private final HashedMap<K, V> map;
		KeysCollection(HashedMap<K, V> m) {map = m;}
		@Override
		public int size() {return map.size();}
		@Override
		public java.util.Iterator<K> iterator() {return new KeysIterator<K,V>(map);}
		/*
		 * Override methods which AbstractSet implements naively (it uses lowest-common-denominator Collections view)
		 */
		@Override
		public boolean contains(Object obj) {return map.containsKey(obj);}
		@Override
		public boolean remove(Object obj) {if (!map.containsKey(obj)) return false; map.remove(obj); return true;}
		@Override
		public void clear() {map.clear();}
	}


	private static final class ValuesCollection<K, V>
		extends java.util.AbstractCollection<V>
	{
		private final HashedMap<K, V> map;
		ValuesCollection(HashedMap<K, V> m) {map = m;}
		@Override
		public int size() {return map.size();}
		@Override
		public java.util.Iterator<V> iterator() {return new ValuesIterator<K,V>(map);}
		/*
		 * Override methods which AbstractCollection implements naively (it uses lowest-common-denominator Collections view)
		 * We can do a fraction better on contains(), since even though containsValue() does a linear scan too, at least it avoids
		 * creating an iterator.
		 * The base class implements remove() by iterating until first match, so let it.
		 */
		@Override
		public boolean contains(Object obj) {return map.containsValue(obj);}
		@Override
		public void clear() {map.clear();}
	}


	private static final class EntriesCollection<K, V>
		extends java.util.AbstractSet<java.util.Map.Entry<K,V>>
	{
		private final HashedMap<K, V> map;
		EntriesCollection(HashedMap<K, V> m) {map = m;}
		@Override
		public int size() {return map.size();}
		@Override
		public java.util.Iterator<java.util.Map.Entry<K, V>> iterator() {return new EntriesIterator<K,V>(map);}
		/*
		 * Once again, override methods which AbstractSet implements naively.
		 */
		@Override
		public boolean contains(Object obj) {
			if (!(obj instanceof java.util.Map.Entry)) return false;
			java.util.Map.Entry<?,?> entry2 = (java.util.Map.Entry<?,?>)obj;
			Object k2 = entry2.getKey();
			V collval = map.get(k2);
			if (collval == null) {
				return (entry2.getValue() == null && map.containsKey(k2));
			}
			Object v2 = entry2.getValue();
			return (collval == v2) || collval.equals(v2);
		}

		@Override
		public boolean remove(Object obj) {
			if (!contains(obj)) return false;
			java.util.Map.Entry<?,?> ent = (java.util.Map.Entry<?,?>)obj;
			map.remove(ent.getKey());
			return true;
		}

		@Override
		public Object[] toArray() {
			return toArray(new Object[size()]);
		}

		@Override
		public <T> T[] toArray(T[] arr) {
			if (arr.length < size()) {
				@SuppressWarnings("unchecked")
				T[] unchecked = (T[])java.lang.reflect.Array.newInstance(arr.getClass().getComponentType(), size());
				arr = unchecked;
			}
			java.util.Iterator<java.util.Map.Entry<K, V>> it = iterator();
			int idx = 0;
			while (it.hasNext()) {
				java.util.Map.Entry<K, V> src = it.next();
				java.util.Map.Entry<K, V> dst = new HashedMapEntries.LinkedMapEntry<K,V>(map, src.getKey());
				@SuppressWarnings("unchecked") T elem = (T)dst;
				arr[idx++] = elem;
			}
			if (arr.length > idx) arr[idx] = null;
			return arr;
		}

		@Override
		public void clear() {map.clear();}
	}


	private static final class KeysIterator<K, V>
		extends MapIterator<K, K, V>
	{
		KeysIterator(HashedMap<K, V> m) {super(m);}
		@Override
		public K getCurrentElement(int id, int slot) {return map.getMapKey(id, slot);}
	}

	private static final class ValuesIterator<K, V>
		extends MapIterator<V, K, V>
	{
		ValuesIterator(HashedMap<K, V> m) {super(m);}
		@Override
		public V getCurrentElement(int id, int slot) {return map.getMapValue(id, slot);}
	}

	// This class masquerades as the Map.Entry object of the current element in the iteration, so the
	// virtual Map.Entry object it returns will appear to get reused (ie. will mutate) as the iteration
	// proceeds. This is perfectly legal, and is discussed in the class-header comments above.
	private static final class EntriesIterator<K, V>
		extends MapIterator<java.util.Map.Entry<K, V>, K, V>
		implements java.util.Map.Entry<K, V>
	{
		EntriesIterator(HashedMap<K, V> m) {super(m);}
		K key;

		@Override
		public java.util.Map.Entry<K, V> getCurrentElement(int id, int slot) {
			key = map.getMapKey(id, slot);
			if (MAPENTRY_NOREUSE) return new HashedMapEntries.LinkedMapEntry<>(map, key);
			return this;
		}

		@Override
		public K getKey() {return key;}
		@Override
		public V getValue() {return map.get(key);}

		// Set new value in backing map, if this key is still a member
		@Override
		public V setValue(V newval) {
			V oldval = getValue();
			if (oldval != null || map.containsKey(key)) map.put(key, newval); //is still a member of the map
			return oldval;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || !(obj instanceof java.util.Map.Entry)) return false;
			java.util.Map.Entry<?,?> ment = (java.util.Map.Entry<?,?>)obj;
			return (compareObjects(key, ment.getKey()) && compareObjects(getValue(), ment.getValue()));
		}

		@Override
		public int hashCode() {return (key == null ? 0 : key.hashCode());}
		@Override
		public String toString() {return getClass().getName()+"/"+System.identityHashCode(this)+"/"+key+"="+getValue();}
	}


	private static abstract class MapIterator<T, K, V>
		implements java.util.Iterator<T>
	{
		protected final HashedMap<K, V> map;
		private int bktid;
		private int bktslot;
		private int next_bktid;
		private int next_bktslot;
		protected int expmodcnt;

		protected abstract T getCurrentElement(int bkt_id, int bkt_slot);

		MapIterator(HashedMap<K, V> m) {map=m; reset();}

		final void reset()
		{
			next_bktid = 0;
			next_bktslot = -KVSPAN; //so that first increment takes us to first slot (index=0)
			bktid = -1;
			expmodcnt = map.modcnt;
			moveNext();
		}

		@Override
		public final boolean hasNext()
		{
			return (next_bktid != map.capacity);
		}

		@Override
		public final T next()
		{
			if (map.modcnt != expmodcnt) throw new ConcurrentModificationException("Next on "+getClass().getName());
			if (!hasNext()) throw new java.util.NoSuchElementException();
			bktid = next_bktid;
			bktslot = next_bktslot;
			moveNext();
			return getCurrentElement(bktid, bktslot);
		}

		@Override
		public final void remove()
		{
			if (bktid == -1) throw new IllegalStateException();
			if (map.modcnt != expmodcnt) throw new ConcurrentModificationException("Remove on "+getClass().getName());
			map.remove(map.buckets[bktid], bktid, map.bucketsizes[bktid], bktslot);
			if (next_bktid == bktid) next_bktslot = bktslot; //remove() shifted final entry into current slot, so stay where we are
			bktid = -1;
			expmodcnt++;
		}

		private final void moveNext()
		{
			next_bktslot += KVSPAN;
			if (next_bktslot == map.bucketsizes[next_bktid]) {
				while (++next_bktid != map.capacity) {
					if (map.bucketsizes[next_bktid] != 0) break;
				}
				next_bktslot = 0;
			}
		}
	}
}
