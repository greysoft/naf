/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.util.ConcurrentModificationException;

/**
 * Zero-garbage replacement for java.util.HashMap.
 * <br/>
 * This class may be preferable to the standard JDK HashMap for high-frequency caches with short-lived entries, as the JDK class not only allocates
 * new memory (in the shape of the Entry class, a linked list of which is rooted in each table slot) when inserting each new key, but also releases
 * that memory when the key is removed. The standard HashMap would therefore generate too much garbage in such a scenario, whereas this class never
 * frees memory and keeps empty slots available for re-use.
 * <br/>
 * However, the JDK HashMap class offers a more efficient Map.Entry view, as that is the native type of its underlying storage. This class allocates
 * Map.Entry objects on demand as you iterate through the entrySet() view, so if you expect to set up this map once and then iterate over its entries
 * view many times, then the JDK class would be a better option.
 * <br/>
 * Like the JDK HashMap, this class implements all optional Map operations.
 * Beware that this class is single-threaded and non-reentrant.
 */
public final class HashedMap<K,V>
	implements java.util.Map<K,V>
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 0.8f;
	private static final int BUCKETCAP_INIT = 5;  //initial bucket size
	private static final int BUCKETCAP_INCR = 5;  //number of entries to increment a bucket by, when growing it
	private static final int BUCKETCAP_MAX = Short.MAX_VALUE;  //should never actually reach this size

	private final boolean keyset_only; //if True, we're in Set mode, storing keys only
	private final float loadfactor;
	private int capacity;  // this is just the number of buckets, which is much less than total capacity as a chain hangs off each one
	private int threshold;
	private int hashmask;

	private K[][] keytbl;
	private V[][] valtbl;
	private short[] bucketsizes;
	private int entrycnt;
	private int modcnt;

	private KeysCollection keysview;
	private ValuesCollection valuesview;

	// recycled iterators
	private KeysIterator keys_iterator;
	private ValuesIterator values_iterator;

	public HashedMap() {this(0);}
	public HashedMap(int initcap) {this(initcap, 0);}
	public HashedMap(int initcap, float factor) {this(initcap, factor, false);}

	@Override
	public boolean isEmpty() {return (entrycnt == 0);}
	@Override
	public int size() {return entrycnt;}

	protected int bucketCount() {return capacity;}  // useful for test harness

	protected HashedMap(int initcap, float factor, boolean set_only)
	{
		if (initcap == 0) initcap = DFLT_CAP;
		if (factor == 0) factor = DFLT_LOADFACTOR;
		loadfactor = factor;
		keyset_only = set_only;

		// find min power-of-2 size that holds initcap entries
		capacity = 1;
		while (capacity < initcap) capacity <<= 1;

		allocateBuckets();
	}

	// Note that nulling the key slot is enough to invalidate the corresponding value slot, and zeroing the bucketsizes slot is enough to
	// invalidate the key slot, but we need to explicitly nullify the Objects refs for the sake of the GC.
	@Override
	public void clear()
	{
		for (int idx = keytbl.length - 1; idx != -1; idx--) {
			if (keytbl[idx] != null) {
				java.util.Arrays.fill(keytbl[idx], null);
				if (!keyset_only) java.util.Arrays.fill(valtbl[idx], null);
			}
		}
		java.util.Arrays.fill(bucketsizes, (short)0);
		entrycnt = 0;
		modcnt++;
	}

	@Override
	public boolean containsKey(Object key)
	{
		if (key == null) {
			// Null key hashes to zero
			final K[] bucket = keytbl[0];
			for (int idx2 = 0; idx2 != bucketsizes[0]; idx2++) {
				if (bucket[idx2] == null) return true;
			}
		} else {
			final int idx = getBucket(key);
			final K[] bucket = keytbl[idx];
			for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
				if (key == bucket[idx2] || key.equals(bucket[idx2])) return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key)
	{
		if (key == null) {
			final K[] bucket = keytbl[0];
			for (int idx2 = 0; idx2 != bucketsizes[0]; idx2++) {
				if (bucket[idx2] == null) return (keyset_only ? (V)keytbl[0][idx2] : valtbl[0][idx2]);
			}
		} else {
			final int idx = getBucket(key);
			final K[] bucket = keytbl[idx];
			for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
				if (key == bucket[idx2] || key.equals(bucket[idx2])) return (keyset_only ? (V)keytbl[idx][idx2] : valtbl[idx][idx2]);
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
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		final int idx = (key == null ? 0 : getBucket(key));
		final int bktsiz = bucketsizes[idx];
		K[] bucket = keytbl[idx];
		int idx2 = 0;

		if (bucket == null) {
			// this bucket doesn't exist yet, so its obviously a new key, and we need to create its bucket
			bucket = growBucket(idx);
		} else {
			// check if key already exists
			if (keyset_only) {
				 //caller should already have verified if key exists, and must not make duplicate put() calls
				idx2 = bktsiz;
			} else {
				if (key == null) {
					while (idx2 != bktsiz) {
						if (bucket[idx2] == null) break;
						idx2++;
					}
				} else {
					while (idx2 != bktsiz) {
						if (key.equals(bucket[idx2])) break;
						idx2++;
					}
				}
			}

			if (idx2 == bucket.length) {
				// not only did we not find a match, but the bucket is full so we'll have to grow it before appending this new key to it
				if (idx2 == BUCKETCAP_MAX) {
					// Bucket can't grow any more. This is an utterly ridiculous loading which can't happen in practice, unless user selected
					// a ridiculous load factor (or our hash function is broken), but we have to handle it as a theoretical possibility anyway.
					// Just grow the whole hash table, in the hope that will reduce this key's bucket. It might not, if we have a hashing problem,
					// in which case we have a non-recoverable situation, so let that manifest itself by growing until we run out of memory.
					capacity <<= 1;
					allocateBuckets();
					return put(key, value);
				}
				bucket = growBucket(idx);
			}
		}
		V oldvalue = null;
		modcnt++;

		if (idx2 == bktsiz) {
			// adding a new key
			bucket[idx2] = key;
			entrycnt++;
			bucketsizes[idx]++;
			if (keyset_only) return null;
		} else {
			// Updating the value of an existing key.
			// As explained above, we do not enter this code path in set-only mode.
			oldvalue = valtbl[idx][idx2];
		}
		valtbl[idx][idx2] = value;
		return oldvalue;
	}

	@Override
	public V remove(Object key)
	{
		final int idx = (key == null ? 0 : getBucket(key));
		final K[] bucket = keytbl[idx];
		final int bktsiz = bucketsizes[idx];
		int slot = -1;

		if (key == null) {
			for (int idx2 = 0; idx2 != bktsiz; idx2++) {
				if (bucket[idx2] == null) {
					slot = idx2;
					break;
				}
			}
		} else {
			for (int idx2 = 0; idx2 != bktsiz; idx2++) {
				if (key.equals(bucket[idx2])) {
					slot = idx2;
					break;
				}
			}
		}
		if (slot == -1) return null;
		V oldval = null;

		// Shorten this bucket by swapping final entry into the slot we're now vacating
		if (!keyset_only) {
			oldval = valtbl[idx][slot];
			if (slot != bktsiz - 1) valtbl[idx][slot] = valtbl[idx][bktsiz - 1];
			valtbl[idx][bktsiz - 1] = null;
		}
		if (slot != bktsiz - 1) bucket[slot] = bucket[bktsiz - 1];
		bucket[bktsiz - 1] = null;
		bucketsizes[idx]--;
		entrycnt--;
		modcnt++;
		return oldval;
	}

	// This is never called in keyset_only mode
	@Override
	public boolean containsValue(Object val)
	{
		if (val == null) {
			for (int idx = valtbl.length - 1; idx != -1; idx--) {
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
					if (valtbl[idx][idx2] == null) return true;
				}
			}
		} else {
			for (int idx = valtbl.length - 1; idx != -1; idx--) {
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
					if (val == valtbl[idx][idx2] || val.equals(valtbl[idx][idx2])) return true;
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
	// This method in turn converts that into an index into our hash table, which means the return value is considerably less than 32 bits.
	// We're going to assume that the object's hash function is uniformly distributed over all bits (True for String and ByteChars), so just
	// chop off the excess upper bits.
	private int getBucket(Object key)
	{
		return key.hashCode() & hashmask;
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

        final K[][] oldkeys = keytbl;
        final V[][] oldvals = valtbl;
		final short[] oldsizes = bucketsizes;
		bucketsizes = new short[capacity];

		@SuppressWarnings("unchecked")
        K[][] uncheck_k = (K[][])new Object[capacity][];
        keytbl = uncheck_k;

        if (!keyset_only) {
            @SuppressWarnings("unchecked")
            V[][] uncheck_v = (V[][])new Object[capacity][];
            valtbl = uncheck_v;
        }

		if (oldkeys != null) {
			for (int idx = 0; idx != oldkeys.length; idx++) {
				for (int idx2 = 0; idx2 != oldsizes[idx]; idx2++) {
					put(oldkeys[idx][idx2], keyset_only ? null : oldvals[idx][idx2]);
				}
			}
		}
	}

	private K[] growBucket(int bktid)
	{
		final K[] oldkeys = keytbl[bktid];
		final V[] oldvals = (keyset_only ? null : valtbl[bktid]);
		int newsiz = (keytbl[bktid] == null ? BUCKETCAP_INIT : keytbl[bktid].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;  //should never really happen

        if (!keyset_only) {
            @SuppressWarnings("unchecked")
            V[] uncheck_v = (V[])new Object[newsiz];
            valtbl[bktid] = uncheck_v;
        }
        @SuppressWarnings("unchecked")
        K[] uncheck_k = (K[])new Object[newsiz];
        keytbl[bktid] = uncheck_k;

        if (oldkeys != null) {
            System.arraycopy(oldkeys, 0, keytbl[bktid], 0, oldkeys.length);
            if (!keyset_only) System.arraycopy(oldvals, 0, valtbl[bktid], 0, oldvals.length);
        }
		return keytbl[bktid];
	}

	public int trimToSize()
	{
		int newcap = 1;
		while (((int)(newcap * loadfactor)) <= entrycnt) newcap <<= 1;
	
		if (newcap != capacity) {
			capacity = newcap;
			allocateBuckets();
		}
		modcnt++;
		return capacity;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName());
		if (keyset_only) sb.append("/Set");
		sb.append('=').append(size()).append(" {");
		String dlm = "";
		for (int idx = 0; idx != keytbl.length; idx++) {
			for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
				sb.append(dlm).append(keytbl[idx][idx2]);
				if (!keyset_only) sb.append('=').append(valtbl[idx][idx2]);
				dlm = ", ";
			}
		}
		sb.append("}");
		return sb.toString();
	}

	public String getStats()
	{
		return getStats(size(), bucketsizes);
	}

	static String getStats(int entrycnt, short[] bucketsizes)
	{
		StringBuilder sb = new StringBuilder(256);
		sb.append("Size=").append(entrycnt).append(", Capacity=").append(bucketsizes.length);
		sb.append("\nBucket Loading Freq: ");
		HashedMapIntInt freq = new HashedMapIntInt();

		for (int idx = 0; idx != bucketsizes.length; idx++) {
			freq.put(bucketsizes[idx], freq.get(bucketsizes[idx])+1);
		}
		int[] afreq = freq.toArrayKeys(null);
		java.util.Arrays.sort(afreq);
		String dlm = "";

		for (int idx = afreq.length - 1; idx != -1; idx--) {
			sb.append(dlm).append(afreq[idx]).append('x').append(freq.get(afreq[idx]));
			dlm = ", ";
		}
		return sb.toString();
	}


	/*
	 * These required Map methods provide Collections views of this object.
	 */
	@Override
	public java.util.Set<K> keySet()
	{
		if (keysview == null) keysview = new KeysCollection();
		return keysview;
	}

	@Override
	public java.util.Collection<V> values()
	{
		if (valuesview == null) valuesview = new ValuesCollection();
		return valuesview;
	}

	@Override
	public java.util.Set<java.util.Map.Entry<K,V>> entrySet()
	{
		return new EntriesCollection();
	}

	// These are not standard Map methods (let alone required), but they provide reusable Iterator objects for those callers
	// who wish to make use of them.
	public java.util.Iterator<K> keysIterator()
	{
		if (keys_iterator == null) {
			keys_iterator = new KeysIterator();
		} else {
			keys_iterator.reset();
		}
		return keys_iterator;
	}

	public java.util.Iterator<V> valuesIterator()
	{
		if (values_iterator == null) {
			values_iterator = new ValuesIterator();
		} else {
			values_iterator.reset();
		}
		return values_iterator;
	}

	// provide access to private members for the inner classes
	K getMapKey(int id, int slot) {return keytbl[id][slot];}
	V getMapValue(int id, int slot) {return valtbl[id][slot];}
	short getBucketSize(int id) {return bucketsizes[id];}
	int modCount() {return modcnt;}

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support the required Collections views of this map.
	 * ===================================================================================================================
	 */

	private final class KeysCollection
		extends java.util.AbstractSet<K>
	{
		@Override
		public int size() {return HashedMap.this.size();}
		@Override
		public java.util.Iterator<K> iterator() {return new KeysIterator();}
		/*
		 * Override methods which AbstractSet implements naively (it uses lowest-common-denominator Collections view)
		 */
		@Override
		public boolean contains(Object obj) {return HashedMap.this.containsKey(obj);}
		@Override
		public boolean remove(Object obj) {if (!HashedMap.this.containsKey(obj)) return false; HashedMap.this.remove(obj); return true;}
		@Override
		public void clear() {HashedMap.this.clear();}
	}


	private final class ValuesCollection
		extends java.util.AbstractCollection<V>
	{
		@Override
		public int size() {return HashedMap.this.size();}
		@Override
		public java.util.Iterator<V> iterator() {return new ValuesIterator();}
		/*
		 * Override methods which AbstractCollection implements naively (it uses lowest-common-denominator Collections view)
		 * We can do a fraction better on contains(), since even though containsValue() does a linear scan too, at least it avoids
		 * creating an iterator.
		 * The base class implements remove() by iterating until first match, so let it.
		 */
		@Override
		public boolean contains(Object obj) {return HashedMap.this.containsValue(obj);}
		@Override
		public void clear() {HashedMap.this.clear();}
	}


	private final class EntriesCollection
		extends java.util.AbstractSet<java.util.Map.Entry<K,V>>
	{
		@Override
		public int size() {return HashedMap.this.size();}
		@Override
		public java.util.Iterator<java.util.Map.Entry<K, V>> iterator() {return new EntriesIterator();}
		/*
		 * Once again, override methods which AbstractSet implements naively.
		 */
		@Override
		public boolean contains(Object obj)
		{
			if (obj == null || obj.getClass() != MapEntry.class) return false;
			@SuppressWarnings("unchecked")
			MapEntry entry = (MapEntry)obj;
			return HashedMap.this.containsKey(entry.key);
		}

		@Override
		public boolean remove(Object obj)
		{
			if (!contains(obj)) return false;
			@SuppressWarnings("unchecked")
			MapEntry entry = (MapEntry)obj;
			HashedMap.this.remove(entry.key);
			return true;
		}

		@Override
		public void clear() {HashedMap.this.clear();}
	}

	private final class MapEntry
		implements java.util.Map.Entry<K,V>
	{
		private final EntriesIterator iter;
		final K key;
		private V value;

		private MapEntry(EntriesIterator i, K k) {iter=i; key=k; value=getValue();}

		@Override
		public K getKey() {return key;}

		// Update value from backing map, if this key is still a member
		@Override
		public V getValue() {
			if (HashedMap.this.containsKey(key)) value = HashedMap.this.get(key);
			return value;
		}

		// Flush new value to backing map, if this key is still a member
		@Override
		public V setValue(V newval) {
			V oldval = getValue();
			value = newval;
			iter.update(key, newval);
			return oldval;
		}

		@Override
		public String toString() {return key+"="+getValue();}
	}


	private final class KeysIterator
		extends MapIterator<K>
	{
		@Override
		public K getCurrentValue(int id, int slot) {return HashedMap.this.getMapKey(id, slot);}
	}

	private final class ValuesIterator
		extends MapIterator<V>
	{
		@Override
		public V getCurrentValue(int id, int slot) {return HashedMap.this.getMapValue(id, slot);}
	}

	private final class EntriesIterator
		extends MapIterator<java.util.Map.Entry<K, V>>
	{
		@Override
		public java.util.Map.Entry<K, V> getCurrentValue(int id, int slot) {return new MapEntry(this, HashedMap.this.getMapKey(id, slot));}

		protected void update(K key, V val) {
			if (!HashedMap.this.containsKey(key)) return;
			HashedMap.this.put(key, val);
			expmodcnt++;
		}
	}


	private abstract class MapIterator<T>
		implements java.util.Iterator<T>
	{
		private int bktid;
		private int bktslot;
		private int next_bktid;
		private int next_bktslot;
		protected int expmodcnt;
		protected abstract T getCurrentValue(int bktid, int bktslot);

		MapIterator() {reset();}

		final void reset()
		{
			next_bktid = 0;
			next_bktslot = -1;
			bktid = -1;
			expmodcnt = HashedMap.this.modCount();
			moveNext();
		}

		@Override
		public final boolean hasNext()
		{
			return (next_bktid != bucketCount());
		}

		@Override
		public final T next()
		{
			if (!hasNext()) throw new java.util.NoSuchElementException();
			if (HashedMap.this.modCount() != expmodcnt) throw new ConcurrentModificationException("Next on "+getClass().getName());
			bktid = next_bktid;
			bktslot = next_bktslot;
			moveNext();
			return getCurrentValue(bktid, bktslot);
		}

		@Override
		public final void remove()
		{
			if (bktid == -1) throw new IllegalStateException();
			if (HashedMap.this.modCount() != expmodcnt) throw new ConcurrentModificationException("Remove on "+getClass().getName());
			HashedMap.this.remove(HashedMap.this.getMapKey(bktid, bktslot));
			if (next_bktid == bktid) next_bktslot = bktslot; //remove() shifted final entry into current slot, so stay where we are
			bktid = -1;
			expmodcnt++;
		}

		private final void moveNext()
		{
			if (++next_bktslot == getBucketSize(next_bktid)) {
				while (++next_bktid != bucketCount()) {
					if (getBucketSize(next_bktid) != 0) break;
				}
				next_bktslot = 0;
			}
		}
	}
}