/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Same idea as HashedMap, but hardcodes both the key and value types as primitive Ints, since generics don't support
 * primitives (not without auto-boxing, which generates garbage for the GC).
 * <br/>
 * For that reason, we don't implement the java.util.Map interface, as it expects its parameters to be of type Object, and requires
 * iterators that don't make sense in this context. We do implement its functionality however, to the maximum extent that is appropriate.
 * <p>
 * See HashedMap.java for additional comments throughout the code, since the classes are so similiar.<br/>
 * See HashedMapIntKey.java for the prime example of mapping HashedMap to primitive types.
 * <p> 
 * The missing java.util.Map methods are: putAll(), keySet(), entrySet()<br/>
 * The equivalent functionality is provided by: iteratorInit(), iteratorHasNext(), iteratorNextEntry(), iteratorNextKey()<br/>
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 * Also note that this class's iterators are not fail-fast, unlike HashedMap.
 */
public final class HashedMapIntInt
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 5;  //because key comparisons are so quick, try to save on storage space
	private static final int BUCKETCAP_INIT = 5;  //initial bucket size
	private static final int BUCKETCAP_INCR = 5;  //number of entries to increment a bucket by, when growing it
	private static final int BUCKETCAP_MAX = Short.MAX_VALUE;  //should never actually reach this size

	private final boolean keyset_only; //if True, we're in Set mode, storing keys only
	private final float loadfactor;
	private int capacity;
	private int threshold;
	private int hashmask;

	private int[][] keytbl;
	private int[][] valtbl;
	private short[] bucketsizes;
	private int entrycnt;

	// recycled operators
	private KeysIterator keys_iterator;
	private ValuesIterator values_iterator;

	public HashedMapIntInt() {this(0);}
	public HashedMapIntInt(int initcap) {this(initcap, 0);}
	public HashedMapIntInt(int initcap, float factor)  {this(initcap, factor, false);}

	public boolean isEmpty() {return (entrycnt == 0);}
	public int size() {return entrycnt;}
	int bucketCount() {return capacity;}  // useful for test harness

	protected HashedMapIntInt(int initcap, float factor, boolean set_only)
	{
		if (initcap == 0) initcap = DFLT_CAP;
		if (factor == 0) factor = DFLT_LOADFACTOR;
		keyset_only = set_only;
		loadfactor = factor;

		// find min power-of-2 size that holds initcap entries
		capacity = 1;
		while (capacity < initcap) capacity <<= 1;

		allocateBuckets();
	}

	// Note that nulling the key slot is enough to invalidate the corresponding value slot, but we explicitly nullify the latter so as to release the
	// value-object references - these objects need to be marked as garbage now, if no other references exist
	public void clear()
	{
		java.util.Arrays.fill(bucketsizes, (short)0);
		entrycnt = 0;
	}

	public boolean containsKey(int key)
	{
		final int idx = getBucket(key);
		final int[] bucket = keytbl[idx];

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
			if (key == bucket[idx2]) return true;
		}
		return false;
	}

	// This is never called in keyset_only mode
	public int get(int key)
	{
		final int idx = getBucket(key);
		final int[] bucket = keytbl[idx];

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
			if (key == bucket[idx2]) return valtbl[idx][idx2];
		}
		return 0;
	}

	public int put(int key, int value)
	{
		if (entrycnt == threshold) {
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		final int idx = getBucket(key);
		final int bktsiz = bucketsizes[idx];
		int[] bucket = keytbl[idx];
		int idx2 = 0;

		if (bucket == null) {
			bucket = growBucket(idx);
		} else {
			while (idx2 != bktsiz) {
				if (key == bucket[idx2]) break;
				idx2++;
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
			// This is never called in keyset_only mode - caller checks for existence first and does not make duplicate put() calls
			oldvalue = valtbl[idx][idx2];
		}
		if (!keyset_only) valtbl[idx][idx2] = value;
		return oldvalue;
	}

	public int remove(int key)
	{
		final int idx = getBucket(key);
		final int[] bucket = keytbl[idx];
		final int bktsiz = bucketsizes[idx];

		for (int idx2 = 0; idx2 != bktsiz; idx2++) {
			if (key == bucket[idx2]) {
				int oldval = (keyset_only ? 0 : valtbl[idx][idx2]);

				if (idx2 != bktsiz - 1) {
					bucket[idx2] = bucket[bktsiz - 1];
					if (!keyset_only) valtbl[idx][idx2] = valtbl[idx][bktsiz - 1];
				}
				entrycnt--;
				bucketsizes[idx]--;
				return oldval;
			}
		}
		return 0;
	}

	// This is never called in keyset_only mode
	public boolean containsValue(int val)
	{
		for (int idx = keytbl.length - 1; idx != -1; idx--) {
			for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
				if (val == valtbl[idx][idx2]) return true;
			}
		}
		return false;
	}

	private int getBucket(int key)
	{
		return HashedMapIntKey.intHash(key) & hashmask;
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

		final int[][] oldkeys = keytbl;
		final int[][] oldvals = valtbl;
		final short[] oldsizes = bucketsizes;
		keytbl = new int[capacity][];
		if (!keyset_only) valtbl = new int[capacity][];
		bucketsizes = new short[capacity];

		if (oldkeys != null) {
			for (int idx = 0; idx != oldkeys.length; idx++) {
				for (int idx2 = 0; idx2 != oldsizes[idx]; idx2++) {
					if (keyset_only) {
						put(oldkeys[idx][idx2], 0);
					} else {
						put(oldkeys[idx][idx2], oldvals[idx][idx2]);
					}
				}
			}
		}
	}

	private int[] growBucket(int bktid)
	{
		final int[] oldkeys = keytbl[bktid];
		final int[] oldvals = (keyset_only ? null : valtbl[bktid]);
		int newsiz = (keytbl[bktid] == null ? BUCKETCAP_INIT : keytbl[bktid].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;

		keytbl[bktid] = new int[newsiz];
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
	
		if (newcap != capacity) {
			capacity = newcap;
			allocateBuckets();
		}
		return capacity;
	}

	public int[] toArrayKeys(int[] arr)
	{
		if (arr == null || arr.length < entrycnt) arr = new int[entrycnt];
		int slot = 0;
		for (int idx = 0; idx != keytbl.length; idx++) {
			for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
				arr[slot++] = keytbl[idx][idx2];
			}
		}
		return arr;
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

	public String getBucketStats()
	{
		return HashedMap.getStats(size(), bucketsizes);
	}


	/*
	 * These are not standard Map methods (let alone required), but they provide reusable Iterator objects for those callers who
	 * wish to make use of them.
	 */
	public IteratorInt keysIterator() {return new KeysIterator();}
	public IteratorInt valuesIterator() {return new ValuesIterator();}

	public IteratorInt recycledKeysIterator()
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
	int getMapKey(int id, int slot) {return keytbl[id][slot];}
	int getMapValue(int id, int slot) {return valtbl[id][slot];}
	short getBucketSize(int id) {return bucketsizes[id];}

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support Collections views and iterators on this map.
	 * ===================================================================================================================
	 */
	public final class KeysIterator
		extends MapIterator
	{
		@Override
		public int getCurrentValue(int id, int slot) {return  getMapKey(id, slot);}
	}

	public final class ValuesIterator
		extends MapIterator
	{
		@Override
		public int getCurrentValue(int id, int slot) {return getMapValue(id, slot);}
	}

	private abstract class MapIterator
		implements IteratorInt
	{
		private int bktid;
		private int bktslot;
		private int next_bktid;
		private int next_bktslot;
		protected abstract int getCurrentValue(int bktid, int bktslot);

		MapIterator() {reset();}

		final void reset()
		{
			next_bktid = 0;
			next_bktslot = -1;
			bktid = -1;
			moveNext();
		}

		@Override
		public final boolean hasNext()
		{
			return (next_bktid != bucketCount());
		}

		@Override
		public final int next()
		{
			if (!hasNext()) throw new java.util.NoSuchElementException();
			bktid = next_bktid;
			bktslot = next_bktslot;
			moveNext();
			return getCurrentValue(bktid, bktslot);
		}

		@Override
		public final void remove()
		{
			if (bktid == -1) throw new IllegalStateException();
			HashedMapIntInt.this.remove(getMapKey(bktid, bktslot));
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