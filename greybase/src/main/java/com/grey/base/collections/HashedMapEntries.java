/*
 * Copyright 2014-2024 Yusef Badri - All rights reserved.
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
 *
 * <p>
 * In general, the {@link HashedMap} class should be preferred to this one, as it has a smaller memory footprint, less indirection
 * and better locality of reference.<br>
 * However that class reuses objects when iterating on the entrySet() view and while that is perfectly valid, this class provides
 * a safe option for badly written applications which can't handle that.
 *
 * <p>
 * Like the JRE's {@link java.util.HashMap}, this class implements all optional {@link java.util.Map} operations.<br>
 * Beware that this class is single-threaded and non-reentrant.
 *
 * <p>
 * Discussion on reuse of Map.Entry nodes:<br>
 * You should note that the Map.Entry objects returned by this class's iterator are no longer valid once their key has been deleted,
 * as they will then be nulled and recycled for use by other keys.
 * <br>
 * This is is not in breach of the required Map interface, but it is at variance with the behaviour of some other Maps such as the JDK's
 * java.util.HashMap (whose returned Map.Entry objects are dedicated to their key in perpetuity).
 * <br>
 * {@link java.util.Map.Entry} has this to say:<br>
 * (i) "Map.Entry objects are valid only for the duration of the iteration"
 * (ii) "the behavior of a map entry is undefined if the backing map has been modified after the entry was returned by the iterator,
 * except through the setValue operation on the map entry"
 * <br>
 * FindBugs takes both sides, with PZ_DONT_REUSE_ENTRY_OBJECTS_IN_ITERATORS warning Map implementors not to reuse Map.Entry objects
 * and DMI_ENTRY_SETS_MAY_REUSE_ENTRY_OBJECTS warning Map users to beware that Map.Entry objects might mutate unexpectedly.
 * <br>
 * The HashedMapEntries class is therefore in compliance. It reuses Map.Entry objects only if their key has been deleted, and not
 * under any other circumstances.
 * It also returns long-lived (ie. will not be recycled) Map.Entry nodes in the entrySet() view's toArray() methods, as required by
 * the Java Map spec.
 *
 * @see HashedMap
 */
public final class HashedMapEntries<K,V>
	implements java.util.Map<K,V>
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 0.8f;
	private static final int BUCKETCAP_INCR = 4; //growth increment of bucket array

	private final float loadfactor;
	private int threshold;
	private int hashmask;

	private final java.util.ArrayList<MapEntry<K,V>> freepool = new java.util.ArrayList<MapEntry<K,V>>(); //stack
	private int alloc_count;
	private int entrycnt;

	int capacity; // this is just the number of buckets, which is much less than total capacity as a chain hangs off each one
	MapEntry<K,V>[][] buckets;
	int[] bucketsizes; //number of entries in each bucket
	int modcnt;

	//these classes are stateless, so allocate just once, on first usage
	private EntriesCollection<K,V> entriesview;
	private KeysCollection<K,V> keysview;
	private ValuesCollection<K,V> valuesview;

	// recycled iterators
	private EntriesIterator<K,V> entries_iterator;
	private KeysIterator<K,V> keys_iterator;
	private ValuesIterator<K,V> values_iterator;

	public HashedMapEntries() {this(0);}
	public HashedMapEntries(int initcap) {this(initcap, 0);}

	@Override
	public boolean isEmpty() {return (entrycnt == 0);}
	@Override
	public int size() {return entrycnt;}

	protected HashedMapEntries(int initcap, float factor)
	{
		if (initcap == 0) initcap = DFLT_CAP;
		if (factor == 0) factor = DFLT_LOADFACTOR;
		loadfactor = factor;

		// find min power-of-2 size that holds initcap entries
		capacity = 1;
		while (capacity < initcap) capacity <<= 1;

		allocateBuckets();
	}

	@Override
	public void clear()
	{
		for (int idx = buckets.length - 1; idx != -1; idx--) {
			MapEntry<K,V>[] bucket = buckets[idx];
			int bktsiz = bucketsizes[idx];
			for (int idx2 = 0; idx2 != bktsiz; idx2++) {
				releasePool(bucket[idx2]);
				bucket[idx2] = null;
			}
		}
		java.util.Arrays.fill(bucketsizes, 0);
		entrycnt = 0;
		modcnt++;
	}

	@Override
	public boolean containsKey(Object key)
	{
		return (getEntry(key) != null);
	}

	@Override
	public V get(Object key)
	{
		MapEntry<K,V> ent = getEntry(key);
		return (ent == null ? null : ent.value);
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
		MapEntry<K,V>[] bucket = buckets[bktid];
		int slot = 0;

		if (bucket == null) {
			// this bucket doesn't exist yet, so its obviously a new key, and we need to create its bucket
			bucket = growBucket(bktid);
		} else {
			// check if key already exists
			if (key == null) {
				while (slot != bktsiz) {
					if (bucket[slot].key == null) break;
					slot++;
				}
			} else {
				while (slot != bktsiz) {
					K mapkey = bucket[slot].key;
					if (key == mapkey || key.equals(mapkey)) break;
					slot++;
				}
			}

			if (slot == bucket.length) {
				// not only did we not find a match, but the bucket is full so we'll have to grow it before appending this new key to it
				bucket = growBucket(bktid);
			}
		}
		MapEntry<K,V> ent;
		V oldvalue;

		if (slot == bktsiz) {
			// adding a new key
			ent = allocPool();
			bucket[slot] = ent;
			ent.key = key;
			bucketsizes[bktid]++;
			entrycnt++;
			modcnt++;
			oldvalue = null;
		} else {
			ent = bucket[slot];
			oldvalue = ent.value;
		}
		ent.value = value;
		return oldvalue;
	}

	@Override
	public V remove(Object key)
	{
		final int bktid = (key == null ? 0 : getBucket(key));
		final MapEntry<K,V>[] bucket = buckets[bktid];
		final int bktsiz = bucketsizes[bktid];
		MapEntry<K,V> ent = null;
		int slot = -1;

		if (key == null) {
			for (int idx = 0; idx != bktsiz; idx++) {
				ent = bucket[idx];
				if (ent.key == null) {
					slot = idx;
					break;
				}
			}
		} else {
			for (int idx = 0; idx != bktsiz; idx++) {
				ent = bucket[idx];
				K mapkey = ent.key;
				if (key == mapkey || key.equals(mapkey)) {
					slot = idx;
					break;
				}
			}
		}
		if (slot == -1) return null;

		V oldval = ent.value;
		remove(bucket, bktid, bktsiz, slot, ent);
		return oldval;
	}

	void remove(Object[] bucket, int bktid, int bktsiz, int slot, MapEntry<K,V> ent)
	{
		int finalslot = bktsiz - 1;
		if (finalslot != slot) {
			// shorten this bucket by swapping final entry into the deleted slot we're now vacating
			bucket[slot] = bucket[finalslot];
		}
		bucket[finalslot] = null;
		releasePool(ent);
		bucketsizes[bktid]--;
		entrycnt--;
		modcnt++;
	}

	@Override
	public boolean containsValue(Object val)
	{
		if (val == null) {
			for (int idx = buckets.length - 1; idx != -1; idx--) {
				MapEntry<K,V>[] bucket = buckets[idx];
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
					if (bucket[idx2].value == null) return true;
				}
			}
		} else {
			for (int idx = buckets.length - 1; idx != -1; idx--) {
				MapEntry<K,V>[] bucket = buckets[idx];
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
					V v = bucket[idx2].value;
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

	MapEntry<K,V> getEntry(Object key)
	{
		if (key == null) {
			// Null key hashes to zero
			MapEntry<K,V>[] bucket = buckets[0];
			int bktsiz = bucketsizes[0];
			for (int idx = 0; idx != bktsiz; idx++) {
				if (bucket[idx].key == null) return bucket[idx];
			}
		} else {
			int bktid = getBucket(key);
			MapEntry<K,V>[] bucket = buckets[bktid];
			int bktsiz = bucketsizes[bktid];
			for (int idx = 0; idx != bktsiz; idx++) {
				MapEntry<K,V> ent = bucket[idx];
				K mapkey = ent.key;
				if (key == mapkey || key.equals(mapkey)) return ent;
			}
		}
		return null;
	}

	// The object's hash function maps the object to an arbitrary 32-bit number, which is hopefully reasonably distinct.
	// This method in turn converts that into an index into our hash table, which means the return value is considerably less than 32 bits.
	// We're going to assume that the object's hash function is uniformly distributed over all bits (True for String and ByteChars), so just
	// chop off the excess upper bits.
	private int getBucket(Object key)
	{
		return HashedMap.objectHash(key) & hashmask;
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

		final MapEntry<K,V>[][] oldbuckets = buckets;
		@SuppressWarnings("unchecked") MapEntry<K,V>[][] uncheck_k = new MapEntry[capacity][];
		buckets = uncheck_k;

		final int[] oldsizes = bucketsizes;
		bucketsizes = new int[capacity];

		if (oldbuckets != null) {
			for (int idx = 0; idx != oldbuckets.length; idx++) {
				MapEntry<K,V>[] oldbucket = oldbuckets[idx];
				int oldsiz = oldsizes[idx];
				for (int idx2 = 0; idx2 != oldsiz; idx2++) {
					MapEntry<K,V> ent = oldbucket[idx2];
					K k = ent.key;
					V v = ent.value;
					releasePool(ent);
					put(k, v);
				}
			}
		}
	}

	private MapEntry<K,V>[] growBucket(int bktid)
	{
		final MapEntry<K,V>[] oldbucket = buckets[bktid];
		int oldsiz = (oldbucket == null ? 0 : oldbucket.length);
		int newsiz = oldsiz + BUCKETCAP_INCR;

		@SuppressWarnings("unchecked") MapEntry<K,V>[] newbucket = new MapEntry[newsiz];
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

	private MapEntry<K,V> allocPool()
	{
		if (freepool.size() == 0) {
			alloc_count++;
			return new MapEntry<K,V>();
		}
		return freepool.remove(freepool.size() - 1);
	}

	private void releasePool(MapEntry<K,V> e)
	{
		e.key = null;
		e.value = null;
		freepool.add(e);
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName()).append('=').append(size()).append(" {");
		String dlm = "";
		for (int idx = 0; idx != buckets.length; idx++) {
			MapEntry<K,V>[] bucket = buckets[idx];
			int bktsiz = bucketsizes[idx];
			for (int idx2 = 0; idx2 != bktsiz; idx2++) {
				MapEntry<K,V> ent = bucket[idx2];
				sb.append(dlm).append(ent.key).append('=').append(ent.value);
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


	private static class MapEntry<K,V>
		implements java.util.Map.Entry<K,V>
	{
		K key;
		V value;

		MapEntry() {}
		MapEntry(K k) {key=k;}

		@Override
		public K getKey() {return key;}
		@Override
		public V getValue() {return value;}

		@Override
		public V setValue(V newval) {
			V oldval = value;
			value = newval;
			return oldval;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || !(obj instanceof java.util.Map.Entry)) return false;
			java.util.Map.Entry<?,?> me2 = (java.util.Map.Entry<?,?>)obj;
			return (HashedMap.compareObjects(key, me2.getKey()) && HashedMap.compareObjects(getValue(), me2.getValue()));
		}

		@Override
		public int hashCode() {return (key == null ? 0 : key.hashCode());}
		@Override
		public String toString() {return getClass().getName()+"/"+System.identityHashCode(this)+"/"+key+"="+getValue();}
	}

	// The MapEntry inner class is the building block of this map, but is not suitable for being copied
	// (eg. for the entrySet.toArray() calls) as the copy would have no relation to the underlying map.
	// Hence we need a modified subclass for making copies, that is linked back to the map.
	static final class LinkedMapEntry<K,V>
		extends MapEntry<K,V>
	{
		private final java.util.Map<K,V> map;
		LinkedMapEntry(java.util.Map<K,V> m, K k) {super(k); map=m;}
		@Override
		public V getValue() {return map.get(getKey());}
		@Override
		public V setValue(V v) {
			V oldval = getValue();
			K k = getKey();
			if (oldval != null || map.containsKey(k)) map.put(k, v); //is still a member of the map
			return oldval;
		}
	}


	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support the required Collections views of this map.
	 * ===================================================================================================================
	 */

	private static final class KeysCollection<K, V>
		extends java.util.AbstractSet<K>
	{
		private final HashedMapEntries<K, V> map;
		KeysCollection(HashedMapEntries<K, V> m) {map = m;}
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
		private final HashedMapEntries<K, V> map;
		ValuesCollection(HashedMapEntries<K, V> m) {map = m;}
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
		private final HashedMapEntries<K, V> map;
		EntriesCollection(HashedMapEntries<K, V> m) {map = m;}
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
			java.util.Map.Entry<?,?> ent2 = (java.util.Map.Entry<?,?>)obj;
			MapEntry<K,V> ent = map.getEntry(ent2.getKey());
			if (ent == null) return false;
			return HashedMap.compareObjects(ent.value, ent2.getValue());
		}
		@Override
		public boolean remove(Object obj) {
			if (!contains(obj)) return false;
			java.util.Map.Entry<?,?> ent = (java.util.Map.Entry<?,?>)obj;
			map.remove(ent.getKey());
			return true;
		}
		@Override
		public void clear() {map.clear();}
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
				//need to make independent copy, as our Entry nodes get recycled and may mutate unexpectedly
				java.util.Map.Entry<K, V> src = it.next();
				java.util.Map.Entry<K, V> dst = new LinkedMapEntry<K,V>(map, src.getKey());
				@SuppressWarnings("unchecked") T elem = (T)dst;
				arr[idx++] = elem;
			}
			if (arr.length > idx) arr[idx] = null;
			return arr;
		}
	}


	private static final class KeysIterator<K, V>
		extends MapIterator<K, K, V>
	{
		KeysIterator(HashedMapEntries<K, V> m) {super(m);}
		@Override
		public K getCurrentElement(int id, int slot) {return map.buckets[id][slot].key;}
	}

	private static final class ValuesIterator<K, V>
		extends MapIterator<V, K, V>
	{
		ValuesIterator(HashedMapEntries<K, V> m) {super(m);}
		@Override
		public V getCurrentElement(int id, int slot) {return map.buckets[id][slot].value;}
	}

	private static final class EntriesIterator<K, V>
		extends MapIterator<java.util.Map.Entry<K, V>, K, V>
	{
		EntriesIterator(HashedMapEntries<K, V> m) {super(m);}
		@Override
		public java.util.Map.Entry<K, V> getCurrentElement(int id, int slot) {return map.buckets[id][slot];}
	}


	private static abstract class MapIterator<T, K, V>
		implements java.util.Iterator<T>
	{
		protected final HashedMapEntries<K, V> map;
		private int bktid;
		private int bktslot;
		private int next_bktid;
		private int next_bktslot;
		protected int expmodcnt;

		protected abstract T getCurrentElement(int bkt_id, int bkt_slot);

		MapIterator(HashedMapEntries<K, V> m) {map=m; reset();}

		final void reset()
		{
			next_bktid = 0;
			next_bktslot = -1; //so that first increment takes us to first slot (index=0)
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
			MapEntry<K,V>[] bucket = map.buckets[bktid];
			map.remove(bucket, bktid, map.bucketsizes[bktid], bktslot, bucket[bktslot]);
			if (next_bktid == bktid) next_bktslot = bktslot; //remove() shifted final entry into current slot, so stay where we are
			bktid = -1;
			expmodcnt++;
		}

		private final void moveNext()
		{
			if (++next_bktslot == map.bucketsizes[next_bktid]) {
				while (++next_bktid != map.capacity) {
					if (map.bucketsizes[next_bktid] != 0) break;
				}
				next_bktslot = 0;
			}
		}
	}
}
