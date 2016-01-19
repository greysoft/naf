/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

/**
 * Same idea as HashedMap, but hardcodes key type as primitive Ints, since generics don't support primitives (not without auto-boxing, which
 * generates garbage for the GC).
 * <br>
 * For that reason, we don't implement the java.util.Map interface, as it expects its key-value parameters to be of type Object, and requires
 * iterators that don't make sense in our context. We do match its functionality however, to the maximum extent that is appropriate.
 * <p>
 * See HashedMap.java for additional comments throughout the code, since the classes are so similiar.<br>
 * The main design difference with HashedMap is obviously in getBucket().
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 */
public final class HashedMapIntKey<V>
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 5;  //because key comparisons are so quick, try to save on storage space
	private static final int BUCKETCAP_INCR = 4;  //growth increment of bucket array

	final boolean keyset_only; //if True, we're in Set mode, storing keys only
	private final V dummyvalue;
	private final float loadfactor;

	private int threshold;
	private int hashmask;

	int capacity;
	int[][] keytbl; //first slot in each bucket is the bucket-size, then come the keys
	V[][] valtbl; //the values begin in first slot, so index is offset by 1 from the assoc key
	private int entrycnt;

	// recycled operators
	private KeysIterator<V> keys_iterator;
	private ValuesIterator<V> values_iterator;

	public HashedMapIntKey() {this(0);}
	public HashedMapIntKey(int initcap) {this(initcap, 0);}
	public HashedMapIntKey(int initcap, float factor) {this(initcap, factor, null);}

	public boolean isEmpty() {return (entrycnt == 0);}
	public int size() {return entrycnt;}

	HashedMapIntKey(int initcap, float factor, V dummy)
	{
		if (initcap == 0) initcap = DFLT_CAP;
		if (factor == 0) factor = DFLT_LOADFACTOR;
		loadfactor = factor;
		keyset_only = (dummy != null);
		dummyvalue = dummy;

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
			keytbl[idx][0] = 0;
			if (!keyset_only) java.util.Arrays.fill(valtbl[idx], null);
		}
		entrycnt = 0;
	}

	public boolean containsKey(int key)
	{
		final int bktid = getBucket(key);
		final int[] bucket = keytbl[bktid];
		if (bucket == null) return false;

		for (int idx = bucket[0]; idx != 0; idx--) {
			if (key == bucket[idx]) return true;
		}
		return false;
	}

	// not called in keyset mode
	public V get(int key)
	{
		final int bktid = getBucket(key);
		final int[] bucket = keytbl[bktid];
		if (bucket == null) return null;

		for (int idx = bucket[0]; idx != 0; idx--) {
			if (key == bucket[idx]) return valtbl[bktid][idx-1];
		}
		return null;
	}

	// We always return null if key doesn't exist, but in keyset mode we return dummyvalue to indicate it was found.
	public V put(int key, V value)
	{
		if (entrycnt == threshold) {
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		final int bktid = getBucket(key);
		int[] bucket = keytbl[bktid];
		final int lmt = (bucket == null ? 1 : bucket[0] + 1);
		int slot = 1;

		if (bucket == null) {
			// this bucket doesn't exist yet, so its obviously a new key, and we need to create its bucket
			bucket = growBucket(bktid);
		} else {
			// check if key already exists
			while (slot != lmt) {
				if (key == bucket[slot]) break;
				slot++;
			}

			if (slot == bucket.length) {
				// not only did we not find a match, but the bucket is full so we'll have to grow it before appending this new key to it
				bucket = growBucket(bktid);
			}
		}
		V oldvalue;

		if (slot == lmt) {
			// adding a new key
			bucket[slot] = key;
			bucket[0]++;
			entrycnt++;
			if (keyset_only) return null;
			oldvalue = null;
		} else {
			// updating the value of an existing key
			if (keyset_only) return dummyvalue; //there are no values, caller just wants to know that key existed
			oldvalue = valtbl[bktid][slot-1];
		}
		valtbl[bktid][slot-1] = value;
		return oldvalue;
	}

	// We always return null if key doesn't exist, but in keyset mode we return dummyvalue to indicate it was found.
	public V remove(int key)
	{
		final int bktid = getBucket(key);
		final int[] bucket = keytbl[bktid];
		if (bucket == null) return null;
		final int lmt = bucket[0] + 1;

		for (int idx = 1; idx != lmt; idx++) {
			if (key == bucket[idx]) {
				if (keyset_only) {
					remove(bucket, idx, lmt, null);
					return dummyvalue;
				}
				V[] valbucket = valtbl[bktid];
				V oldval = valbucket[idx-1];
				remove(bucket, idx, lmt, valbucket);
				return oldval;
			}
		}
		return null;
	}

	void remove(int[] keybucket, int slot, int lmt, V[] valbucket)
	{
		if (slot != lmt - 1) {
			// shorten this bucket by swapping final entry into the slot we're now vacating
			keybucket[slot] = keybucket[lmt - 1];
			if (valbucket != null) valbucket[slot-1] = valbucket[lmt - 2];
		}
		if (valbucket != null) valbucket[lmt - 2] = null;
		keybucket[0]--;
		entrycnt--;
	}

	// not called in keyset mode
	public boolean containsValue(Object val)
	{
		if (val == null) {
			for (int idx = keytbl.length - 1; idx != -1; idx--) {
				final int[] bucket = keytbl[idx];
				if (bucket == null) continue;
				for (int idx2 = bucket[0]; idx2 != 0; idx2--) {
					if (valtbl[idx][idx2-1] == null) return true;
				}
			}
		} else {
			for (int idx = keytbl.length - 1; idx != -1; idx--) {
				final int[] bucket = keytbl[idx];
				if (bucket == null) continue;
				for (int idx2 = bucket[0]; idx2 != 0; idx2--) {
					V v = valtbl[idx][idx2-1];
					if (val == v || val.equals(v)) return true;
				}
			}
		}
		return false;
	}

	public int[] getKeys(int[] keys)
	{
		if (keys == null) keys = new int[size()];
		IteratorInt it = keysIterator();
		int idx = 0;
		while (it.hasNext()) {
			keys[idx++] = it.next();
		}
		return keys;
	}

	public java.util.List<V> getValues()
	{
		java.util.List<V> lst = new java.util.ArrayList<V>(size());
		java.util.Iterator<V> it = valuesIterator();
		while (it.hasNext()) {
			V v = it.next();
			if (!lst.contains(v)) lst.add(v);
		}
		return lst;
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

		if (!keyset_only) {
			@SuppressWarnings("unchecked") final V[][] unchecked = (V[][])new Object[capacity][];
			valtbl = unchecked;
		}
		keytbl = new int[capacity][];

		if (oldkeys != null) {
			for (int idx = 0; idx != oldkeys.length; idx++) {
				int[] bucket = oldkeys[idx];
				int lmt = (bucket == null ? 1 : bucket[0] + 1);
				for (int idx2 = 1; idx2 != lmt; idx2++) {
					V v = (keyset_only ? null : oldvals[idx][idx2-1]);
					put(bucket[idx2], v);
				}
			}
		}
	}

	private int[] growBucket(int bktid)
	{
		final int[] oldkeys = keytbl[bktid];
		final V[] oldvals = (keyset_only ? null : valtbl[bktid]);
		int oldcap = (oldkeys == null ? 0 : oldkeys.length);
		int newcap = oldcap + BUCKETCAP_INCR;
		if (oldcap == 0) newcap++; //allocate an extra slot for the counter-slot, when creating new bucket

		if (!keyset_only) {
			@SuppressWarnings("unchecked") final V[] unchecked = (V[])new Object[newcap-1]; //no size slot
			valtbl[bktid] = unchecked;
		}
		keytbl[bktid] = new int[newcap];

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

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName());
		if (keyset_only) sb.append("/Set");
		sb.append('=').append(size()).append(" {");
		String dlm = "";
		for (int idx = 0; idx != keytbl.length; idx++) {
			int[] bucket = keytbl[idx];
			if (bucket == null) continue;
			for (int idx2 = 0; idx2 != bucket[0]; idx2++) {
				sb.append(dlm).append(bucket[idx2+1]);
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
	public IteratorInt keysIterator() {return new KeysIterator<V>(this);}
	public java.util.Iterator<V> valuesIterator() {return new ValuesIterator<V>(this);}

	public IteratorInt recycledKeysIterator()
	{
		if (keys_iterator == null) {
			keys_iterator = new KeysIterator<V>(this);
		} else {
			keys_iterator.reset();
		}
		return keys_iterator;
	}

	public java.util.Iterator<V> recycledValuesIterator()
	{
		if (values_iterator == null) {
			values_iterator = new ValuesIterator<V>(this);
		} else {
			values_iterator.reset();
		}
		return values_iterator;
	}


	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support Collections views and iterators on this map.
	 * ===================================================================================================================
	 */

	final static class KeysIterator<V>
		extends MapIterator<V>
		implements IteratorInt
	{
		KeysIterator(HashedMapIntKey<V> m) {super(m);}
		@Override
		public int next() {setNext(); return map.keytbl[bktid][bktslot];}
	}

	private final static class ValuesIterator<V>
		extends MapIterator<V>
		implements java.util.Iterator<V>
	{
		ValuesIterator(HashedMapIntKey<V> m) {super(m);}
		@Override
		public V next() {setNext(); return map.valtbl[bktid][bktslot-1];}
	}

	private static abstract class MapIterator<V>
	{
		protected final HashedMapIntKey<V> map;
		protected int bktid;
		protected int bktslot;
		private int next_bktid;
		private int next_bktslot;

		MapIterator(HashedMapIntKey<V> m) {map=m; reset();}

		final void reset()
		{
			next_bktid = 0;
			next_bktslot = 0; //so that first increment takes us to first slot (index=1)
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
			int[] bucket = map.keytbl[bktid];
			map.remove(bucket, bktslot, bucket[0]+1, map.keyset_only ? null : map.valtbl[bktid]);
			if (next_bktid == bktid) next_bktslot = bktslot; //remove() shifted final entry into current slot, so stay where we are
			bktid = -1;
		}

		private final void moveNext()
		{
			int[] bucket = map.keytbl[next_bktid];
			if (bucket == null || next_bktslot++ == bucket[0]) {
				while (++next_bktid != map.capacity) {
					bucket = map.keytbl[next_bktid];
					if (bucket != null && bucket[0] != 0) break;
				}
				next_bktslot = 1;
			}
		}
	}
}
