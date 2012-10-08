/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Same idea as HashedMap, but hardcodes key type as primitive Ints, since generics don't support primitives (not without auto-boxing, which
 * generates garbage for the GC).
 * <br/>
 * For that reason, we don't implement the java.util.Map interface, as it expects its key-value parameters to be of type Object, and requires
 * iterators that don't make sense in our context. We do match its functionality however, to the maximum extent that is appropriate.
 * <p>
 * See HashedMap.java for additional comments throughout the code, since the classes are so similiar.<br/>
 * The main design difference with HashedMap is obviously in getBucket().
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 */
public final class HashedMapIntKey<V>
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 5;  //because key comparisons are so quick, try to save on storage space
	private static final int BUCKETCAP_INIT = 5;  //initial bucket size
	private static final int BUCKETCAP_INCR = 5;  //number of entries to increment a bucket by, when growing it
	private static final int BUCKETCAP_MAX = Short.MAX_VALUE;  //should never actually reach this size

	private final float loadfactor;
	private int capacity;
	private int threshold;
	private int hashmask;

	private int[][] keytbl;
	private V[][] valtbl;
	private short[] bucketsizes;
	private int entrycnt;

	// recycled operators
	private KeysIterator keys_iterator;
	private ValuesIterator values_iterator;

	public HashedMapIntKey() {this(0);}
	public HashedMapIntKey(int initcap) {this(initcap, 0);}

	public boolean isEmpty() {return (entrycnt == 0);}
	public int size() {return entrycnt;}
	int bucketCount() {return capacity;}  // useful for test harness

	public HashedMapIntKey(int initcap, float factor)
	{
		if (initcap == 0) initcap = DFLT_CAP;
		if (factor == 0) factor = DFLT_LOADFACTOR;
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
		for (int idx = keytbl.length - 1; idx != -1; idx--) {
			if (keytbl[idx] == null) continue;
			java.util.Arrays.fill(valtbl[idx], null);
		}
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

	public V get(int key)
	{
		final int idx = getBucket(key);
		final int[] bucket = keytbl[idx];

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
			if (key == bucket[idx2]) return valtbl[idx][idx2];
		}
		return null;
	}

	public V put(int key, V value)
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
		V oldvalue = null;

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

	public V remove(int key)
	{
		final int idx = getBucket(key);
		final int[] bucket = keytbl[idx];
		final int bktsiz = bucketsizes[idx];

		for (int idx2 = 0; idx2 != bktsiz; idx2++) {
			if (key == bucket[idx2]) {
				V oldvalue = valtbl[idx][idx2];

				if (idx2 != bktsiz - 1) {
					bucket[idx2] = bucket[bktsiz - 1];
					valtbl[idx][idx2] = valtbl[idx][bktsiz - 1];
				}
				valtbl[idx][bktsiz - 1] = null;
				bucketsizes[idx]--;
				entrycnt--;
				return oldvalue;
			}
		}
		return null;
	}

	public boolean containsValue(Object val)
	{
		if (val == null) {
			for (int idx = keytbl.length - 1; idx != -1; idx--) {
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
					if (valtbl[idx][idx2] == null) return true;
				}
			}
		} else {
			for (int idx = keytbl.length - 1; idx != -1; idx--) {
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--) {
					if (val == valtbl[idx][idx2] || val.equals(valtbl[idx][idx2])) return true;
				}
			}
		}
		return false;
	}

	// This is the hash the JDK HashMap class applies to the input hash code. I don't know why, but it performs vastly better than treating
	// a 32-bit Int as a sequence of 4 bytes, and applying the classic Shift-Add-XOR hash to it. In fact, it's virtually perfect, whereas the
	// SAX hash was positively poor.
	// This didn't offer any worthwhile improvement on String.hashCode() or ByteChars.hashCode() though (and the latter has a Shift-Add-XOR hash).
	static int intHash(int key)
	{
        key ^= (key >>> 20) ^ (key >>> 12);
        return key ^ (key >>> 7) ^ (key >>> 4);
	}

	// This method converts the arbitrary 32-bit value into an index into our hash table, which means the return value is considerably less
	// than 32 bits. So we need to mix in information from all 32 original bits, and ensure the result is evenly distributed over the hash
	// table.
	// We achieve this by means of intHash().
	private int getBucket(int key)
	{
		return intHash(key) & hashmask;
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = 0;

		final int[][] oldkeys = keytbl;
		final V[][] oldvals = valtbl;
		final short[] oldsizes = bucketsizes;
		bucketsizes = new short[capacity];
		keytbl = new int[capacity][];
		@SuppressWarnings("unchecked")
		final V[][] uncheckedbuf = (V[][])new Object[capacity][];
		valtbl = uncheckedbuf;

		if (oldkeys != null) {
			for (int idx = 0; idx != oldkeys.length; idx++) {
				for (int idx2 = 0; idx2 != oldsizes[idx]; idx2++) {
					put(oldkeys[idx][idx2], oldvals[idx][idx2]);
				}
			}
		}
	}

	private int[] growBucket(int bktid)
	{
		final int[] oldkeys = keytbl[bktid];
		final V[] oldvals = valtbl[bktid];
		int newsiz = (keytbl[bktid] == null ? BUCKETCAP_INIT : keytbl[bktid].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;

		keytbl[bktid] = new int[newsiz];
		@SuppressWarnings("unchecked")
		final V[] uncheckedbuf = (V[])new Object[newsiz];
		valtbl[bktid] = uncheckedbuf;

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
	public IteratorInt keysIterator() {return new KeysIterator();}
	public java.util.Iterator<V> valuesIterator() {return new ValuesIterator();}

	public IteratorInt recycledKeysIterator()
	{
		if (keys_iterator == null) {
			keys_iterator = new KeysIterator();
		} else {
			keys_iterator.reset();
		}
		return keys_iterator;
	}

	public java.util.Iterator<V> recycledValuesIterator()
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
	V getMapValue(int id, int slot) {return valtbl[id][slot];}
	short getBucketSize(int id) {return bucketsizes[id];}

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support Collections views and iterators on this map.
	 * ===================================================================================================================
	 */

	private final class KeysIterator
		extends MapIterator
		implements IteratorInt
	{
		@Override
		public int next() {setNext(); return getMapKey(bktid, bktslot);}
	}

	private final class ValuesIterator
		extends MapIterator
		implements java.util.Iterator<V>
	{
		@Override
		public V next() {setNext(); return getMapValue(bktid, bktslot);}
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
			HashedMapIntKey.this.remove(getMapKey(bktid, bktslot));
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