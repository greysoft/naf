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
 */
public final class HashedMapIntInt
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 5;  // because key comparisons are so quick, try to save on storage space
	private static final int BUCKETCAP_INIT = 5;  // initial bucket size
	private static final int BUCKETCAP_INCR = 5;  // number of entries to increment a bucket by, when growing it
	private static final byte BUCKETCAP_MAX = 127;  //physical byte-value limitation - should never actually reach this size

	private final boolean keyset_only; //if True, we're in Set mode, storing keys only
	private final float loadfactor;
	private int capacity;
	private int threshold;
	private int hashmask;

	private int[][] keytbl;
	private int[][] valtbl;
	private byte[] bucketsizes;
	private int entrycnt;

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
		java.util.Arrays.fill(bucketsizes, (byte)0);
		entrycnt = 0;
	}

	public boolean containsKey(int key)
	{
		int idx = getBucket(key);

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
			if (key == keytbl[idx][idx2]) return true;
		}
		return false;
	}

	// This is never called in keyset_only mode
	public int get(int key)
	{
		int idx = getBucket(key);

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
			if (key == keytbl[idx][idx2]) return valtbl[idx][idx2];
		}
		return 0;
	}

	public int put(int key, int value)
	{
		if (entrycnt == threshold) {
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		int idx = getBucket(key);
		int idx2 = 0;

		if (keytbl[idx] == null) {
			growBucket(idx);
		} else {
			while (idx2 != bucketsizes[idx]) {
				if (key == keytbl[idx][idx2]) break;
				idx2++;
			}
			
			if (idx2 == keytbl[idx].length) {
				if (idx2 == BUCKETCAP_MAX) {
					capacity <<= 1;
					allocateBuckets();
					return put(key, value);
				}
				growBucket(idx);
			}
		}
		int oldvalue = 0;

		if (idx2 == bucketsizes[idx]) {
			entrycnt++;
			bucketsizes[idx]++;
		} else {
			// This is never called in keyset_only mode - caller checks for existence first and does not make duplicate put() calls
			oldvalue = valtbl[idx][idx2];
		}
		keytbl[idx][idx2] = key;
		if (!keyset_only) valtbl[idx][idx2] = value;
		return oldvalue;
	}

	public int remove(int key)
	{
		int idx = getBucket(key);
		int bktsiz = bucketsizes[idx];

		for (int idx2 = 0; idx2 != bktsiz; idx2++) {
			if (key == keytbl[idx][idx2]) {
				int oldvalue = (keyset_only ? 0 : valtbl[idx][idx2]);

				if (idx2 != bktsiz - 1) {
					keytbl[idx][idx2] = keytbl[idx][bktsiz - 1];
					if (!keyset_only) valtbl[idx][idx2] = valtbl[idx][bktsiz - 1];
				}
				entrycnt--;
				bucketsizes[idx]--;
				return oldvalue;
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

	public int[] toArrayKeys(int[] arr)
	{
		if (arr == null || arr.length < entrycnt) arr = new int[entrycnt];
		IteratorInt it = keysIterator();
		int idx = 0;
		while (it.hasNext()) {
			arr[idx++] = it.next();
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
		IteratorInt it = keysIterator();
		while (it.hasNext()) {
			int key = it.next();
			sb.append(dlm).append(key);
			if (!keyset_only) sb.append('=').append(get(key));
			dlm = ", ";
		}
		sb.append("}");
		return sb.toString();
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

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

		int[][] oldkeys = keytbl;
		int[][] oldvals = valtbl;
		byte[] oldsizes = bucketsizes;
		keytbl = new int[capacity][];
		if (!keyset_only) valtbl = new int[capacity][];
		bucketsizes = new byte[capacity];

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

	private void growBucket(int idx)
	{
		int[] oldkeys = keytbl[idx];
		int[] oldvals = (keyset_only ? null : valtbl[idx]);
		int newsiz = (keytbl[idx] == null ? BUCKETCAP_INIT : keytbl[idx].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;

		keytbl[idx] = new int[newsiz];
		if (!keyset_only) valtbl[idx] = new int[newsiz];

		if (oldkeys != null) {
			System.arraycopy(oldkeys, 0, keytbl[idx], 0, oldkeys.length);
			if (!keyset_only) System.arraycopy(oldvals, 0, valtbl[idx], 0, oldvals.length);
		}
	}

	private int getBucket(int key)
	{
		return HashedMapIntKey.intHash(key) & hashmask;
	}

	public byte[] getBucketStats(boolean printstats, int mincolls)
	{
		return HashedMap.getBucketStats(size(), bucketsizes, printstats, mincolls);  // NB: bucketsizes.length is equal to this.capacity
	}


	/*
	 * These are not standard Map methods (let alone required), but they provide reusable Iterator objects for those callers who
	 * wish to make use of them.
	 */
	public IteratorInt keysIterator() {return new KeysIterator();}
	public IteratorInt valuesIterator() {return new ValuesIterator();}


	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support Collections views and iterators on this map.
	 * ===================================================================================================================
	 */
	public final class KeysIterator
		extends Iterator
	{
		@Override
		public int next() {moveNext(); return keytbl[bktid][bktslot];}
	}

	public final class ValuesIterator
		extends Iterator
	{
		@Override
		public int next() {moveNext(); return valtbl[bktid][bktslot];}
	}

	public abstract class Iterator
		implements IteratorInt
	{
		protected int bktid;  	// current index within bucket array
		protected int bktslot;	// current index within current bucket
		private int next_bktid;
		private int next_bktslot;

		Iterator() {reset();}
		final void reset() {bktid = -1; findNext();}

		@Override
		public final boolean hasNext()
		{
			return (next_bktid < capacity);
		}

		@Override
		public final void remove()
		{
			HashedMapIntInt.this.remove(keytbl[bktid][bktslot]);
			if (next_bktid == bktid) next_bktslot = bktslot;  // the remove() will have shifted final entry into current slot, so stay where we are
			bktslot = -1; // invalidate current position
		}

		final void moveNext()
		{
			if (!hasNext()) throw new java.util.NoSuchElementException();
			bktid = next_bktid;
			bktslot = next_bktslot;
			findNext();
		}

		private final void findNext()
		{
			if (bktid == -1) {
				// at start of iteration
				next_bktid = 0;
				next_bktslot = 0;
			} else {
				next_bktslot++;	
			}

			while (next_bktslot >= bucketsizes[next_bktid]) {
				if (++next_bktid == capacity) break;
				next_bktslot = 0;
			}
		}
	}
}
