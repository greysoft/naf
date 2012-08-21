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
 */
public final class HashedMapIntValue<K>
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 0.8f;
	private static final int BUCKETCAP_INIT = 5;  // initial bucket size
	private static final int BUCKETCAP_INCR = 5;  // number of entries to increment a bucket by, when growing it
	private static final byte BUCKETCAP_MAX = 127;  //physical byte-value limitation - should never actually reach this size
	@SuppressWarnings("unchecked")
	private final K NULLKEY = (K)new Object();

	private final float loadfactor;
	private int capacity;
	private int threshold;
	private int hashmask;

	private K[][] keytbl;
	private int[][] valtbl;
	private byte[] bucketsizes;
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
		for (int idx = keytbl.length - 1; idx != -1; idx--)
		{
			if (keytbl[idx] == null) continue;
			java.util.Arrays.fill(keytbl[idx], null);
		}
		java.util.Arrays.fill(bucketsizes, (byte)0);
		entrycnt = 0;
	}

	public boolean containsKey(Object key)
	{
		if (key == null) key = NULLKEY;
		int idx = getBucket(key);

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++)
		{
			// identity test has strong possibility of avoiding the cost of equals() call
			if (key == keytbl[idx][idx2] || key.equals(keytbl[idx][idx2])) return true;
		}
		return false;
	}

	public int get(Object key)
	{
		if (key == null) key = NULLKEY;
		int idx = getBucket(key);

		// considered looping backwards from bucketsizes[idx]-1, but almost twice as slow!
		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++)
		{
			if (key == keytbl[idx][idx2] || key.equals(keytbl[idx][idx2])) return valtbl[idx][idx2];
		}
		return 0;
	}

	public int put(K key, int value)
	{
		if (entrycnt == threshold)
		{
			// It may turn out that we're replacing an existing value rather than adding a new mapping, but even that means we're infinitesmally
			// close to exceeding the threshold, so grow the hash table now anyway.
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		if (key == null) key = NULLKEY;
		int idx = getBucket(key);
		int idx2 = 0;

		if (keytbl[idx] == null)
		{
			growBucket(idx);
		}
		else
		{
			while (idx2 != bucketsizes[idx])
			{
				if (key == keytbl[idx][idx2] || key.equals(keytbl[idx][idx2])) break;
				idx2++;
			}
			
			if (idx2 == keytbl[idx].length)
			{
				if (idx2 == BUCKETCAP_MAX)
				{
					capacity <<= 1;
					allocateBuckets();
					return put(key, value);
				}
				growBucket(idx);
			}
		}
		int oldvalue = 0;

		if (idx2 == bucketsizes[idx])
		{
			entrycnt++;
			bucketsizes[idx]++;
		}
		else
		{
			oldvalue = valtbl[idx][idx2];
		}
		keytbl[idx][idx2] = key;
		valtbl[idx][idx2] = value;
		return oldvalue;
	}

	public int remove(Object key)
	{
		if (key == null) key = NULLKEY;
		int idx = getBucket(key);
		int bktsiz = bucketsizes[idx];

		for (int idx2 = 0; idx2 != bktsiz; idx2++)
		{
			if (key == keytbl[idx][idx2] || key.equals(keytbl[idx][idx2]))
			{
				int oldvalue = valtbl[idx][idx2];

				if (idx2 != bktsiz - 1)
				{
					keytbl[idx][idx2] = keytbl[idx][bktsiz - 1];
					valtbl[idx][idx2] = valtbl[idx][bktsiz - 1];
				}
				keytbl[idx][bktsiz - 1] = null;
				bucketsizes[idx]--;
				entrycnt--;
				return oldvalue;
			}
		}
		return 0;
	}

	public boolean containsValue(int val)
	{
		for (int idx = keytbl.length - 1; idx != -1; idx--)
		{
			for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--)
			{
				if (val == valtbl[idx][idx2]) return true;
			}
		}
		return false;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName()).append('=').append(size()).append(" {");
		String dlm = "";
		java.util.Iterator<K> it = keysIterator();
		while (it.hasNext()) {
			K key = it.next();
			sb.append(dlm).append(key).append('=').append(get(key));
			dlm = ", ";
		}
		sb.append("}");
		return sb.toString();
	}
	
	public int trimToSize()
	{
		int newcap = 1;
		while (((int)(newcap * loadfactor)) <= entrycnt) newcap <<= 1;
	
		if (newcap != capacity)
		{
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

		K[][] oldkeys = keytbl;
		int[][] oldvals = valtbl;
		byte[] oldsizes = bucketsizes;
		bucketsizes = new byte[capacity];
		valtbl = new int[capacity][];
		@SuppressWarnings("unchecked")
		K[][] uncheckedbuf = (K[][])new Object[capacity][];
		keytbl = uncheckedbuf;

		if (oldkeys != null)
		{
			for (int idx = 0; idx != oldkeys.length; idx++)
			{
				for (int idx2 = 0; idx2 != oldsizes[idx]; idx2++)
				{
					put(oldkeys[idx][idx2], oldvals[idx][idx2]);
				}
			}
		}
	}

	private void growBucket(int idx)
	{
		K[] oldkeys = keytbl[idx];
		int[] oldvals = valtbl[idx];
		int newsiz = (keytbl[idx] == null ? BUCKETCAP_INIT : keytbl[idx].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;

		valtbl[idx] = new int[newsiz];
		@SuppressWarnings("unchecked")
		K[] uncheckedbuf = (K[])new Object[newsiz];
		keytbl[idx] = uncheckedbuf;

		if (oldkeys != null)
		{
			System.arraycopy(oldkeys, 0, keytbl[idx], 0, oldkeys.length);
			System.arraycopy(oldvals, 0, valtbl[idx], 0, oldvals.length);
		}
	}

	private int getBucket(Object key)
	{
		return key.hashCode() & hashmask;
	}

	private K mapNullKey(K key)
	{
		if (key == NULLKEY) return null;
		return key;
	}

	public byte[] getBucketStats(boolean printstats, int mincolls)
	{
		return HashedMap.getBucketStats(size(), bucketsizes, printstats, mincolls);  // NB: bucketsizes.length is equal to this.capacity
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

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support the required Collections views of this map.
	 * ===================================================================================================================
	 */

	private final class KeysIterator
		extends CollectionIterator
		implements java.util.Iterator<K>
	{
		@Override
		public K next() {moveNext(); return mapNullKey(keytbl[bktid][bktslot]);}
	}

	private final class ValuesIterator
		extends CollectionIterator
		implements IteratorInt
	{
		@Override
		public int next() {moveNext(); return valtbl[bktid][bktslot];}
	}


	private class CollectionIterator
	{
		int bktid = -1;  	// current index within bucket array
		int bktslot;	// current index within current bucket
		private int next_bktid;
		private int next_bktslot;

		public CollectionIterator() {reset();}

		final void reset()
		{
			bktid = -1;
			findNext();
		}

		public final boolean hasNext()
		{
			return (next_bktid < capacity);
		}

		public final void remove()
		{
			HashedMapIntValue.this.remove(keytbl[bktid][bktslot]);
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
			if (bktid == -1)
			{
				// at start of iteration
				next_bktid = 0;
				next_bktslot = 0;
			}
			else
			{
				next_bktslot++;	
			}

			while (next_bktslot >= bucketsizes[next_bktid])
			{
				if (++next_bktid == capacity) break;
				next_bktslot = 0;
			}
		}
	}
}
