/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

/**
 * Same idea as HashedMap, but hardcodes both the key and value types as primitive Ints, since generics don't support
 * primitives (not without auto-boxing, which generates garbage for the GC).
 * <br>
 * For that reason, we don't implement the java.util.Map interface, as it expects its parameters to be of type Object, and requires
 * iterators that don't make sense in this context. We do implement its functionality however, to the maximum extent that is appropriate.
 * <p>
 * See HashedMap.java for additional comments throughout the code, since the classes are so similiar.<br>
 * See HashedMapIntKey.java for the prime example of mapping HashedMap to primitive types.
 * <p> 
 * The missing java.util.Map methods are: putAll(), keySet(), entrySet()<br>
 * The equivalent functionality is provided by: iteratorInit(), iteratorHasNext(), iteratorNextEntry(), iteratorNextKey()<br>
 * <p>
 * Beware that this class is single-threaded and non-reentrant.
 * Also note that this class's iterators are not fail-fast, unlike HashedMap.
 */
public final class HashedMapIntInt
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 5;  //because key comparisons are so quick, try to save on storage space
	private static final int BUCKETCAP_INCR = 8;  //growth increment of bucket array
	private static final int KVSPAN = 2; //adjacent K-V pair take up 2 slots within a bucket
	private static final int NOKEY = -1; //a key-index within a bucket that means it has no keys (is empty)
	private static final int FIRSTKEY = NOKEY + KVSPAN; //index of first key slot within a bucket

	private final float loadfactor;

	private int threshold;
	private int hashmask;
	private int entrycnt; //total population

	int capacity;
	int[][] buckets; //each bucket contains key/value in alternating slots, slot-0 is index of final key

	// recycled operators
	private KeysIterator keys_iterator;
	private ValuesIterator values_iterator;

	public HashedMapIntInt() {this(0);}
	public HashedMapIntInt(int initcap) {this(initcap, 0);}

	public boolean isEmpty() {return (entrycnt == 0);}
	public int size() {return entrycnt;}

	public HashedMapIntInt(int initcap, float factor)
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
		for (int idx = 0; idx != buckets.length; idx++) {
			if (buckets[idx] != null) buckets[idx][0] = NOKEY;
		}
		entrycnt = 0;
	}

	public boolean containsKey(int key)
	{
		final int bktid = getBucket(key);
		final int[] bucket = buckets[bktid];
		if (bucket == null) return false;

		for (int idx = bucket[0]; idx != NOKEY; idx -= KVSPAN) {
			if (key == bucket[idx]) return true;
		}
		return false;
	}

	public boolean containsValue(int val)
	{
		for (int idx = buckets.length - 1; idx != -1; idx--) {
			final int[] bucket = buckets[idx];
			if (bucket == null) continue;
			for (int idx2 = bucket[0] + 1; idx2 != 0; idx2 -= KVSPAN) {
				if (val == bucket[idx2]) return true;
			}
		}
		return false;
	}

	public int get(int key)
	{
		final int bktid = getBucket(key);
		final int[] bucket = buckets[bktid];
		if (bucket == null) return 0;

		for (int idx = bucket[0]; idx != NOKEY; idx -= KVSPAN) {
			if (key == bucket[idx]) return bucket[idx+1];
		}
		return 0;
	}

	public int put(int key, int value)
	{
		if (entrycnt == threshold) {
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		final int bktid = getBucket(key);
		int[] bucket = buckets[bktid];
		final int lmt = (bucket == null ? FIRSTKEY : bucket[0] + KVSPAN);
		int slot = FIRSTKEY;

		if (bucket == null) {
			// this bucket doesn't exist yet, so its obviously a new key, and we need to create its bucket
			bucket = growBucket(bktid);
		} else {
			// check if key already exists
			while (slot != lmt) {
				if (key == bucket[slot]) break;
				slot += KVSPAN;
			}
			
			if (slot == bucket.length) {
				// not only did we not find a match, but the bucket is full so we'll have to grow it before appending this new key to it
				bucket = growBucket(bktid);
			}
		}
		int oldvalue;

		if (slot == lmt) {
			// adding a new key
			bucket[slot] = key;
			bucket[0] = slot;
			entrycnt++;
			oldvalue = 0;
		} else {
			// updating the value of an existing key
			oldvalue = bucket[slot+1];
		}
		bucket[slot+1] = value;
		return oldvalue;
	}

	public int remove(int key)
	{
		final int bktid = getBucket(key);
		final int[] bucket = buckets[bktid];
		if (bucket == null) return 0;
		final int finalkeyslot = bucket[0];

		for (int idx = finalkeyslot; idx != NOKEY; idx -= KVSPAN) {
			if (key == bucket[idx]) {
				int oldval = bucket[idx+1];
				remove(bucket, idx, finalkeyslot);
				return oldval;
			}
		}
		return 0;
	}

	void remove(int[] bucket, int keyslot, int finalkeyslot)
	{
		if (keyslot != finalkeyslot) {
			bucket[keyslot] = bucket[finalkeyslot];
			bucket[keyslot+1] = bucket[finalkeyslot+1];
		}
		bucket[0] -= KVSPAN;
		entrycnt--;
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

		final int[][] oldbuckets = buckets;
		buckets = new int[capacity][];

		if (oldbuckets != null) {
			for (int idx = 0; idx != oldbuckets.length; idx++) {
				int[] bucket = oldbuckets[idx];
				int lastkey = (bucket == null ? NOKEY : bucket[0]);
				for (int idx2 = lastkey; idx2 != NOKEY; idx2 -= KVSPAN) {
					put(bucket[idx2], bucket[idx2+1]);
				}
			}
		}
	}

	private int[] growBucket(int bktid)
	{
		int[] oldbucket = buckets[bktid];
		int oldsiz = (oldbucket == null ? 0 : oldbucket.length);
		int newsiz = oldsiz + BUCKETCAP_INCR;
		if (oldsiz == 0) newsiz++; //allocate an extra slot for the counter-slot, when creating new bucket

		int[] newbucket = new int[newsiz];
		if (oldbucket == null) {
			newbucket[0] = NOKEY;
		} else {
			System.arraycopy(oldbucket, 0, newbucket, 0, oldbucket.length);
		}

		buckets[bktid] = newbucket;
		return newbucket;
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

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName()).append('=').append(size()).append(" {");
		String dlm = "";
		for (int idx = 0; idx != buckets.length; idx++) {
			int[] bucket = buckets[idx];
			int lastkey = (bucket == null ? NOKEY : bucket[0]);
			for (int idx2 = lastkey; idx2 != NOKEY; idx2 -= KVSPAN) {
				sb.append(dlm).append(bucket[idx2]).append('=').append(bucket[idx2+1]);
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
	public IteratorInt keysIterator() {return new KeysIterator(this);}
	public IteratorInt valuesIterator() {return new ValuesIterator(this);}

	public IteratorInt recycledKeysIterator()
	{
		if (keys_iterator == null) {
			keys_iterator = new KeysIterator(this);
		} else {
			keys_iterator.reset();
		}
		return keys_iterator;
	}

	public IteratorInt recycledValuesIterator()
	{
		if (values_iterator == null) {
			values_iterator = new ValuesIterator(this);
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
	private static final class KeysIterator
		extends MapIterator
	{
		KeysIterator(HashedMapIntInt m) {super(m);}
		@Override
		public int getCurrentElement(int id, int slot) {return  map.buckets[id][slot];}
	}

	private static final class ValuesIterator
		extends MapIterator
	{
		ValuesIterator(HashedMapIntInt m) {super(m);}
		@Override
		public int getCurrentElement(int id, int slot) {return map.buckets[id][slot+1];}
	}

	private static abstract class MapIterator
		implements IteratorInt
	{
		protected final HashedMapIntInt map;
		private int bktid;
		private int bktslot;
		private int next_bktid;
		private int next_bktslot;

		protected abstract int getCurrentElement(int bkt_id, int bkt_slot);

		MapIterator(HashedMapIntInt m) {map=m; reset();}

		final void reset()
		{
			next_bktid = -1;
			bktid = -1;
			goNextBucket();
		}

		@Override
		public final boolean hasNext()
		{
			return (next_bktid != map.capacity);
		}

		@Override
		public final int next()
		{
			if (!hasNext()) throw new java.util.NoSuchElementException();
			bktid = next_bktid;
			bktslot = next_bktslot;
			goNextSlot();
			return getCurrentElement(bktid, bktslot);
		}

		@Override
		public final void remove()
		{
			if (bktid == -1) throw new IllegalStateException();
			int[] bucket = map.buckets[bktid];
			map.remove(bucket, bktslot, bucket[0]);
			if (next_bktid == bktid) next_bktslot = bktslot; //remove() shifted final entry into current slot, so stay where we are
			bktid = -1;
		}

		private final void goNextSlot()
		{
			if (next_bktslot == map.buckets[next_bktid][0]) {
				goNextBucket();
			} else {
				next_bktslot += KVSPAN;
			}
		}

		private void goNextBucket()
		{
			while (++next_bktid != map.capacity) {
				int[] bucket = map.buckets[next_bktid];
				if (bucket != null && bucket[0] != NOKEY) {
					//found non-empty bucket
					next_bktslot = FIRSTKEY;
					return;
				}
			}
		}
	}
}
