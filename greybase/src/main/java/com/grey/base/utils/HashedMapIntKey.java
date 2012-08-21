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
	private static final float DFLT_LOADFACTOR = 5;  // because key comparisons are so quick, try to save on storage space
	private static final int BUCKETCAP_INIT = 5;  // initial bucket size
	private static final int BUCKETCAP_INCR = 5;  // number of entries to increment a bucket by, when growing it
	private static final byte BUCKETCAP_MAX = 127;  //physical byte-value limitation - should never actually reach this size

	private final float loadfactor;
	private int capacity;
	private int threshold;
	private int hashmask;

	private int[][] keytbl;
	private V[][] valtbl;
	private byte[] bucketsizes;
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
		for (int idx = keytbl.length - 1; idx != -1; idx--)
		{
			if (keytbl[idx] == null) continue;
			java.util.Arrays.fill(valtbl[idx], null);
		}
		java.util.Arrays.fill(bucketsizes, (byte)0);
		entrycnt = 0;
	}

	public boolean containsKey(int key)
	{
		int idx = getBucket(key);

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++)
		{
			if (key == keytbl[idx][idx2]) return true;
		}
		return false;
	}

	public V get(int key)
	{
		int idx = getBucket(key);

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++)
		{
			if (key == keytbl[idx][idx2]) return valtbl[idx][idx2];
		}
		return null;
	}

	public V put(int key, V value)
	{
		if (entrycnt == threshold)
		{
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		int idx = getBucket(key);
		int[] keyslot = keytbl[idx];
		int bktsiz = bucketsizes[idx];
		int idx2 = 0;

		if (keyslot == null)
		{
			growBucket(idx);
			keyslot = keytbl[idx];
		}
		else
		{
			while (idx2 != bktsiz)
			{
				if (key == keyslot[idx2]) break;
				idx2++;
			}

			if (idx2 == keyslot.length)
			{
				if (idx2 == BUCKETCAP_MAX)
				{
					capacity <<= 1;
					allocateBuckets();
					return put(key, value);
				}
				growBucket(idx);
				keyslot = keytbl[idx];
			}
		}
		V oldvalue = null;

		if (idx2 == bktsiz)
		{
			entrycnt++;
			bucketsizes[idx]++;
		}
		else
		{
			oldvalue = valtbl[idx][idx2];
		}
		keyslot[idx2] = key;
		valtbl[idx][idx2] = value;
		return oldvalue;
	}

	public V remove(int key)
	{
		int idx = getBucket(key);
		int bktsiz = bucketsizes[idx];

		for (int idx2 = 0; idx2 != bktsiz; idx2++)
		{
			if (key == keytbl[idx][idx2])
			{
				V oldvalue = valtbl[idx][idx2];

				if (idx2 != bktsiz - 1)
				{
					keytbl[idx][idx2] = keytbl[idx][bktsiz - 1];
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
		if (val == null)
		{
			for (int idx = keytbl.length - 1; idx != -1; idx--)
			{
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--)
				{
					if (valtbl[idx][idx2] == null) return true;
				}
			}
		}
		else
		{
			for (int idx = keytbl.length - 1; idx != -1; idx--)
			{
				for (int idx2 = bucketsizes[idx] - 1; idx2 != -1; idx2--)
				{
					// identity test has strong possibility of avoiding the cost of equals() call
					if (val == valtbl[idx][idx2] || val.equals(valtbl[idx][idx2])) return true;
				}
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
		IteratorInt it = keysIterator();
		while (it.hasNext()) {
			int key = it.next();
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

		int[][] oldkeys = keytbl;
		V[][] oldvals = valtbl;
		byte[] oldsizes = bucketsizes;
		bucketsizes = new byte[capacity];
		keytbl = new int[capacity][];
		@SuppressWarnings("unchecked")
		V[][] uncheckedbuf = (V[][])new Object[capacity][];
		valtbl = uncheckedbuf;

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
		int[] oldkeys = keytbl[idx];
		V[] oldvals = valtbl[idx];
		int newsiz = (keytbl[idx] == null ? BUCKETCAP_INIT : keytbl[idx].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;

		keytbl[idx] = new int[newsiz];
		@SuppressWarnings("unchecked")
		V[] uncheckedbuf = (V[])new Object[newsiz];
		valtbl[idx] = uncheckedbuf;

		if (oldkeys != null)
		{
			System.arraycopy(oldkeys, 0, keytbl[idx], 0, oldkeys.length);
			System.arraycopy(oldvals, 0, valtbl[idx], 0, oldvals.length);
		}
	}

	// This method converts the arbitrary 32-bit value into an index into our hash table, which means the return value is considerably less
	// than 32 bits. So we need to mix in information from all 32 original bits, and ensure the result is evenly distributed over the hash
	// table.
	// We achieve this by means of intHash().
	private int getBucket(int key)
	{
		return intHash(key) & hashmask;
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

	public byte[] getBucketStats(boolean printstats, int mincolls)
	{
		return HashedMap.getBucketStats(size(), bucketsizes, printstats, mincolls);  // NB: bucketsizes.length is equal to this.capacity
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

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support Collections views and iterators on this map.
	 * ===================================================================================================================
	 */

	private final class KeysIterator
		extends CollectionIterator
		implements IteratorInt
	{
		@Override
		public int next() {moveNext(); return keytbl[bktid][bktslot];}
	}

	private final class ValuesIterator
		extends CollectionIterator
		implements java.util.Iterator<V>
	{
		@Override
		public V next() {moveNext(); return valtbl[bktid][bktslot];}
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
			HashedMapIntKey.this.remove(keytbl[bktid][bktslot]);
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
