/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Same idea as HashedMap, but hardcodes Value type as primitive Ints, since generics don't support primitives (not without auto-boxing, which
 * generates garbage for the GC).
 * <br/>
 * For that reason, we don't implement the java.util.Map interface, as it expects its Value parameters to be of type Object, and requires
 * iterators that don't make sense in this context. We do implement its functionality however, to the maximum extent that is appropriate.
 * <p>
 * See HashedMap.java for additional comments throughout the code, since the classes are so similiar.<br/>
 * See HashedMapIntKey.java for the prime example of mapping HashedMap to primitive types.<br/>
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
	private static final int BUCKETCAP_INIT = 5;  //initial bucket size
	private static final int BUCKETCAP_INCR = 5;  //number of entries to increment a bucket by, when growing it
	private static final int BUCKETCAP_MAX = Short.MAX_VALUE;  //should never actually reach this size

	private final float loadfactor;
	private int capacity;
	private int threshold;
	private int hashmask;

	private K[][] keytbl;
	private int[][] valtbl;
	private short[] bucketsizes;
	private int entrycnt;

	// recycled operators
	private KeysIterator keys_iterator;
	private ValuesIterator values_iterator;

	public HashedMapIntValue() {this(0);}
	public HashedMapIntValue(int initcap) {this(initcap, 0);}

	public boolean isEmpty() {return (entrycnt == 0);}
	public int size() {return entrycnt;}
	int bucketCount() {return capacity;}  // useful for test harness

	public HashedMapIntValue(int initcap, float factor)
	{
		if (initcap == 0) initcap = DFLT_CAP;
		if (factor == 0) factor = DFLT_LOADFACTOR;
		loadfactor = factor;

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
		java.util.Arrays.fill(bucketsizes, (short)0);
		entrycnt = 0;
	}

	public boolean containsKey(Object key)
	{
		if (key == null) {
			// Null key hashes to zero
			final K[] bucket = keytbl[0];
			for (int idx = 0; idx != bucketsizes[0]; idx++) {
				if (bucket[idx] == null) return true;
			}
		} else {
			final int bktid = getBucket(key);
			final K[] bucket = keytbl[bktid];
			for (int idx = 0; idx != bucketsizes[bktid]; idx++) {
				if (key == bucket[idx] || key.equals(bucket[idx])) return true;
			}
		}
		return false;
	}

	public int get(Object key)
	{
		if (key == null) {
			final K[] bucket = keytbl[0];
			for (int idx = 0; idx != bucketsizes[0]; idx++) {
				if (bucket[idx] == null) return valtbl[0][idx];
			}
		} else {
			final int bktid = getBucket(key);
			final K[] bucket = keytbl[bktid];
			for (int idx = 0; idx != bucketsizes[bktid]; idx++) {
				if (key == bucket[idx] || key.equals(bucket[idx])) return valtbl[bktid][idx];
			}
		}
		return 0;
	}

	public int put(K key, int value)
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
			bucket = growBucket(idx);
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
			
			if (idx2 == bucket.length) {
				if (idx2 == BUCKETCAP_MAX) {
					capacity <<= 1;
					allocateBuckets();
					return put(key, value);
				}
				bucket = growBucket(idx);
			}
		}
		int oldvalue = 0;

		if (idx2 == bktsiz) {
			bucket[idx2] = key;
			entrycnt++;
			bucketsizes[idx]++;
		} else {
			oldvalue = valtbl[idx][idx2];
		}
		valtbl[idx][idx2] = value;
		return oldvalue;
	}

	public int remove(Object key)
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
		if (slot == -1) return 0;

		// Shorten this bucket by swapping final entry into the slot we're now vacating.
		int oldval = valtbl[idx][slot];
		if (slot != bktsiz - 1) {
			bucket[slot] = bucket[bktsiz - 1];
			valtbl[idx][slot] = valtbl[idx][bktsiz - 1];
		}
		bucket[bktsiz - 1] = null;
		bucketsizes[idx]--;
		entrycnt--;
		return oldval;
	}

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
		return key.hashCode() & hashmask;
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

		final K[][] oldkeys = keytbl;
		final int[][] oldvals = valtbl;
		final short[] oldsizes = bucketsizes;
		@SuppressWarnings("unchecked")
		final K[][] uncheckedbuf = (K[][])new Object[capacity][];
		keytbl = uncheckedbuf;
		valtbl = new int[capacity][];
		bucketsizes = new short[capacity];

		if (oldkeys != null) {
			for (int idx = 0; idx != oldkeys.length; idx++) {
				for (int idx2 = 0; idx2 != oldsizes[idx]; idx2++) {
					put(oldkeys[idx][idx2], oldvals[idx][idx2]);
				}
			}
		}
	}

	private K[] growBucket(int bktid)
	{
		final K[] oldkeys = keytbl[bktid];
		final int[] oldvals = valtbl[bktid];
		int newsiz = (keytbl[bktid] == null ? BUCKETCAP_INIT : keytbl[bktid].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;

		valtbl[bktid] = new int[newsiz];
		@SuppressWarnings("unchecked")
		final K[] uncheckedbuf = (K[])new Object[newsiz];
		keytbl[bktid] = uncheckedbuf;

		if (oldkeys != null) {
			System.arraycopy(oldkeys, 0, keytbl[bktid], 0, oldkeys.length);
			System.arraycopy(oldvals, 0, valtbl[bktid], 0, oldvals.length);
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
		return capacity;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName()).append('=').append(size()).append(" {");
		String dlm = "";
		for (int idx = 0; idx != keytbl.length; idx++) {
			for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
				sb.append(dlm).append(keytbl[idx][idx2]).append('=').append(valtbl[idx][idx2]);
				dlm = ", ";
			}
		}
		sb.append("}");
		return sb.toString();
	}

	public String getBucketStats(boolean printstats, int mincolls)
	{
		return HashedMap.getStats(size(), bucketsizes);
	}


	/*
	 * These are not standard Map methods (let alone required), but they provide reusable Iterator objects for those callers who
	 * wish to make use of them.
	 */
	public java.util.Iterator<K> keysIterator() {return new KeysIterator();}
	public IteratorInt valuesIterator() {return new ValuesIterator();}

	public java.util.Iterator<K> recycledKeysIterator()
	{
		if (keys_iterator == null) {
			keys_iterator = new KeysIterator();
		} else {
			keys_iterator.reset();
		}
		return keys_iterator;
	}

	public IteratorInt recycledValuesIterator()
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
	int getMapValue(int id, int slot) {return valtbl[id][slot];}
	short getBucketSize(int id) {return bucketsizes[id];};

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support the required Collections views of this map.
	 * ===================================================================================================================
	 */

	private final class KeysIterator
		extends MapIterator
		implements java.util.Iterator<K>
	{
		@Override
		public K next() {setNext(); return getMapKey(bktid, bktslot);}
	}

	private final class ValuesIterator
		extends MapIterator
		implements IteratorInt
	{
		@Override
		public int next() {setNext(); return getMapValue(bktid, bktslot);}
	}

	private abstract class MapIterator
	{
		protected int bktid;
		protected int bktslot;
		private int next_bktid;
		private int next_bktslot;

		MapIterator() {reset();}

		final void reset()
		{
			next_bktid = 0;
			next_bktslot = -1;
			bktid = -1;
			moveNext();
		}

		public final boolean hasNext()
		{
			return (next_bktid != bucketCount());
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
			HashedMapIntValue.this.remove(getMapKey(bktid, bktslot));
			if (next_bktid == bktid) next_bktslot = bktslot; //remove() shifted final entry into current slot, so stay where we are
			bktid = -1;
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