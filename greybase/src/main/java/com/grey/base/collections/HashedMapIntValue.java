/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

/**
 * Same idea as {@link HashedMap}, but hardcodes Value type as primitive Ints, since generics don't support primitives (not without auto-boxing, which
 * generates garbage for the GC).
 * <br>
 * For that reason, we don't implement the java.util.Map interface, as it expects its Value parameters to be of type Object, and requires
 * iterators that don't make sense in this context. We do implement its functionality however, to the maximum extent that is appropriate.
 * <p>
 * See HashedMap.java for additional comments throughout the code, since the classes are so similiar.<br>
 * See HashedMapIntKey.java for the prime example of mapping HashedMap to primitive types.<br>
 * One prime difference with HashedMap, is that this class maps Null keys to a special local object and then treats them normally, rather
 * than doing the complicated dance HashedMap does with multiple state variables.
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 * Also note that this class's iterators are not fail-fast, unlike HashedMap.
 */
public final class HashedMapIntValue<K>
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 0.8f;
	private static final int BUCKETCAP_INCR = 4;  //growth increment of bucket array

	final boolean keyset_only; //if True, we're in Set mode, storing keys only
	private final float loadfactor;

	private int threshold;
	private int hashmask;

	int capacity;
	K[][] keytbl;
	int[][] valtbl;
	int[] bucketsizes; //number of entries in each bucket
	private int entrycnt;

	// recycled operators
	private KeysIterator<K> keys_iterator;
	private ValuesIterator<K> values_iterator;

	public HashedMapIntValue() {this(0);}
	public HashedMapIntValue(int initcap) {this(initcap, 0);}
	public HashedMapIntValue(int initcap, float factor) {this(initcap, factor, false);}

	public boolean isEmpty() {return (entrycnt == 0);}
	public int size() {return entrycnt;}

	HashedMapIntValue(int initcap, float factor, boolean set_only)
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

	public void clear()
	{
		for (int idx = keytbl.length - 1; idx != -1; idx--) {
			if (keytbl[idx] != null) java.util.Arrays.fill(keytbl[idx], null);
		}
        java.util.Arrays.fill(bucketsizes, 0);
		entrycnt = 0;
	}

	// Keep aligned with HashedMap.containsKey()
	public boolean containsKey(Object key)
	{
		if (key == null) {
			// Null key hashes to zero
			final K[] bucket = keytbl[0];
            int bktsiz = bucketsizes[0];
			for (int idx = 0; idx != bktsiz; idx++) {
				if (bucket[idx] == null) return true;
			}
		} else {
			final int bktid = getBucket(key);
			final K[] bucket = keytbl[bktid];
            int bktsiz = bucketsizes[bktid];
			for (int idx = 0; idx != bktsiz; idx++) {
				K k = bucket[idx];
				if (key == k || key.equals(k)) return true;
			}
		}
		return false;
	}

	// keep this aligned with containsKey() logic - not called in keyset mode
	public int get(Object key)
	{
		if (key == null) {
			// Null key hashes to zero
			final K[] bucket = keytbl[0];
            int bktsiz = bucketsizes[0];
			for (int idx = 0; idx != bktsiz; idx++) {
				if (bucket[idx] == null) return valtbl[0][idx];
			}
		} else {
			final int bktid = getBucket(key);
			final K[] bucket = keytbl[bktid];
            int bktsiz = bucketsizes[bktid];
			for (int idx = 0; idx != bktsiz; idx++) {
				K k = bucket[idx];
				if (key == k || key.equals(k)) return valtbl[bktid][idx];
			}
		}
		return 0;
	}

	// This performs the same matching logic as the get() above, but returns the stored key.
	// It exists purely to support HashedSet.get() - see there for broader purpose.
	K getKey(K key)
	{
		if (key == null) return null;
		final int bktid = getBucket(key);
		final K[] bucket = keytbl[bktid];
        int bktsiz = bucketsizes[bktid];
		for (int idx = 0; idx != bktsiz; idx++) {
			K k = bucket[idx];
			if (key == k || key.equals(k)) return k;
		}
		return null;
	}

	// we always return zero if key doesn't exist, but in keyset mode we return 1 to indicate it was found
	public int put(K key, int value)
	{
		if (entrycnt == threshold) {
			// It may turn out that we're replacing an existing value rather than adding a new mapping, but even that means we're infinitesmally
			// close to exceeding the threshold, so grow the hash table now anyway.
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		final int bktid = (key == null ? 0 : getBucket(key));
        final int bktsiz = bucketsizes[bktid];
		K[] bucket = keytbl[bktid];
		int slot = 0;

		if (bucket == null) {
			// this bucket doesn't exist yet, so its obviously a new key, and we need to create its bucket
			bucket = growBucket(bktid);
		} else {
			// check if key already exists
			if (key == null) {
				while (slot != bktsiz) {
					if (bucket[slot] == null) break; //found the key
					slot++;
				}
			} else {
				while (slot != bktsiz) {
					Object k = bucket[slot];
					if (key == k || key.equals(k)) break;
					slot++;
				}
			}

			if (slot == bucket.length) {
				// not only did we not find a match, but the bucket is full so we'll have to grow it before appending this new key to it
				bucket = growBucket(bktid);
			}
		}
		int oldvalue;

		if (slot == bktsiz) {
			// adding a new key
			bucket[slot] = key;
			bucketsizes[bktid]++;
			entrycnt++;
			if (keyset_only) return 0;
			oldvalue = 0;
		} else {
			// updating the value of an existing key
			if (keyset_only) return 1; //there are no values, caller just wants to know that key existed
			oldvalue = valtbl[bktid][slot];
		}
		valtbl[bktid][slot] = value;
		return oldvalue;
	}

	// we always return zero if key doesn't exist, but in keyset mode we return 1 to indicate it was found
	public int remove(Object key)
	{
		final int bktid = (key == null ? 0 : getBucket(key));
        final int bktsiz = bucketsizes[bktid];
		final K[] bucket = keytbl[bktid];
		int slot = -1;

		if (key == null) {
			for (int idx = 0; idx != bktsiz; idx++) {
				if (bucket[idx] == null) {
					slot = idx;
					break;
				}
			}
		} else {
			for (int idx = 0; idx != bktsiz; idx++) {
				Object k = bucket[idx];
				if (key == k || key.equals(k)) {
					slot = idx;
					break;
				}
			}
		}
		if (slot == -1) return 0;

		if (keyset_only) {
			remove(bucket, bktid, bktsiz, slot, null);
			return 1;
		}
		int[] valbucket = valtbl[bktid];
		int oldval = valbucket[slot];
		remove(bucket, bktid, bktsiz, slot, valbucket);
		return oldval;
	}

	void remove(K[] keybucket, int bktid, int bktsiz, int slot, int[] valbucket)
	{
		int finalslot = bktsiz - 1;

		if (finalslot != slot) {
			// shorten this bucket by swapping final entry into the slot we're now vacating
			keybucket[slot] = keybucket[finalslot];
			if (valbucket != null) valbucket[slot] = valbucket[finalslot];
		}
		keybucket[finalslot] = null;
		bucketsizes[bktid]--;
		entrycnt--;
	}

	// not called in keyset mode
	public boolean containsValue(int val)
	{
		for (int idx = keytbl.length - 1; idx != -1; idx--) {
			for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
				if (val == valtbl[idx][idx2]) return true;
			}
		}
		return false;
	}

	private int getBucket(Object key)
	{
		return HashedMap.objectHash(key) & hashmask;
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

		final K[][] oldkeys = keytbl;
		final int[][] oldvals = valtbl;
        final int[] oldsizes = bucketsizes;
		@SuppressWarnings("unchecked") final K[][] unchecked = (K[][])new Object[capacity][];
		keytbl = unchecked;
		if (!keyset_only) valtbl = new int[capacity][];
        bucketsizes = new int[capacity];

		if (oldkeys != null) {
			for (int idx = 0; idx != oldkeys.length; idx++) {
				K[] oldkeybucket = oldkeys[idx];
				int oldsiz = oldsizes[idx];
				for (int idx2 = 0; idx2 != oldsiz; idx2++) {
					K k = oldkeybucket[idx2];
					int v = (keyset_only ? 0 : oldvals[idx][idx2]);
					put(k, v);
				}
			}
		}
	}

	private K[] growBucket(int bktid)
	{
		Object[] oldkeys = keytbl[bktid];
		final int[] oldvals = (keyset_only ? null : valtbl[bktid]);
		int oldsiz = (oldkeys == null ? 0 : oldkeys.length);
		int newsiz = oldsiz + BUCKETCAP_INCR;

		@SuppressWarnings("unchecked") final K[] unchecked = (K[])new Object[newsiz];
		keytbl[bktid] = unchecked;
		if (!keyset_only) valtbl[bktid] = new int[newsiz];

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
		if (newcap == capacity) return capacity;
		capacity = newcap;
		allocateBuckets();
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
			K[] bucket = keytbl[idx];
			int bktsiz = bucketsizes[idx];
			for (int idx2 = 0; idx2 != bktsiz; idx2++) {
				sb.append(dlm).append(bucket[idx2]);
				if (!keyset_only) sb.append('=').append(valtbl[idx][idx2]);
				dlm = ", ";
			}
		}
		sb.append("}");
		return sb.toString();
	}


	/*
	 * These are not standard Map methods (let alone required), but they provide reusable Iterator objects for those callers who
	 * wish to make use of them.
	 */
	public java.util.Iterator<K> keysIterator() {return new KeysIterator<K>(this);}
	public IteratorInt valuesIterator() {return new ValuesIterator<K>(this);}

	public java.util.Iterator<K> recycledKeysIterator()
	{
		if (keys_iterator == null) {
			keys_iterator = new KeysIterator<K>(this);
		} else {
			keys_iterator.reset();
		}
		return keys_iterator;
	}

	public IteratorInt recycledValuesIterator()
	{
		if (values_iterator == null) {
			values_iterator = new ValuesIterator<K>(this);
		} else {
			values_iterator.reset();
		}
		return values_iterator;
	}


	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support the required Collections views of this map.
	 * ===================================================================================================================
	 */

	private static final class KeysIterator<K>
		extends MapIterator<K>
		implements java.util.Iterator<K>
	{
		KeysIterator(HashedMapIntValue<K> m) {super(m);}
		@Override
		public K next() {setNext(); return map.keytbl[bktid][bktslot];}
	}

	private static final class ValuesIterator<K>
		extends MapIterator<K>
		implements IteratorInt
	{
		ValuesIterator(HashedMapIntValue<K> m) {super(m);}
		@Override
		public int next() {setNext(); return map.valtbl[bktid][bktslot];}
	}

	private static abstract class MapIterator<K>
	{
		protected final HashedMapIntValue<K> map;
		protected int bktid;
		protected int bktslot;
		private int next_bktid;
		private int next_bktslot;

		MapIterator(HashedMapIntValue<K> m) {map=m; reset();}

		final void reset()
		{
			next_bktid = 0;
			next_bktslot = -1; //so that first increment takes us to first slot (index=0)
			bktid = -1;
			moveNext();
		}

		public final boolean hasNext()
		{
			return (next_bktid != map.capacity);
		}

		public void setNext()
		{
			if (!hasNext()) throw new java.util.NoSuchElementException();
			bktid = next_bktid;
			bktslot = next_bktslot;
			moveNext();
		}

		public final void remove()
		{
			if (bktid == -1) throw new IllegalStateException();
			int[] valtbl = (map.keyset_only ? null : map.valtbl[bktid]);
			map.remove(map.keytbl[bktid], bktid, map.bucketsizes[bktid], bktslot, valtbl);
			if (next_bktid == bktid) next_bktslot = bktslot; //remove() shifted final entry into current slot, so stay where we are
			bktid = -1;
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
