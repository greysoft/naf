/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Zero-garbage replacement for java.util.HashMap.
 * <br/>
 * This class may be preferable to the standard JDK HashMap for high-frequency caches with short-lived entries, as the JDK class not only allocates
 * new memory (in the shape of the Entry class, a linked list of which is rooted in each table slot) when inserting each new key, but also releases
 * that memory when the key is removed. The standard HashMap would therefore generate too much garbage in such a scenario, whereas this class never
 * frees memory.
 * <br/>
 * Furthermore, this class offers a recycled Iterator object for each of the Collections views, if you know that you will only be using one iterator
 * at a time (HashMap always allocates a new Iterator object).
 * <p>
 * On the downside, the standard HashMap appears to be 50% faster at locating the first key in each bucket. This is probably because its first
 * bucket entry resides in the main hashtable array, and I expect its advantage to be reversed under heavier loads, as it has to traverse Next
 * pointers to traverse a bucket, whereas our buckets are arrays.
 * <br/>
 * Obtaining the Map.Entry set view is also potentially slower in this class than in the JDK HashMap, as we have to reconstruct an array of
 * Map.Entry entries whenever the underlying hashmap key-set has changed since the last set view was constructed. Also, because the set view
 * is artificially constructed, Map.Entry.getValue() requires a Map lookup, rather than being available in-situ in the Entry node.
 * <p>
 * Like the JDK HashMap, this class implements all optional Map operations.
 */
public final class HashedMap<K,V>
	implements java.util.Map<K,V>
{
	private static final int DFLT_CAP = 64;
	private static final float DFLT_LOADFACTOR = 0.8f;
	private static final int BUCKETCAP_INIT = 5;  // initial bucket size
	private static final int BUCKETCAP_INCR = 5;  // number of entries to increment a bucket by, when growing it
	private static final byte BUCKETCAP_MAX = 127;  //physical byte-value limitation - should never actually reach this size
	private static final boolean announcestdout = com.grey.base.config.SysProps.get("grey.hash.announce", false);

	private final boolean keyset_only; //if True, we're in Set mode, storing keys only
	private final float loadfactor;
	private int capacity;  // this is just the number of buckets, which is much less than total capacity as a chain hangs off each one
	private int threshold;
	private int hashmask;

	private K[][] keytbl;
	private V[][] valtbl;
	private byte[] bucketsizes;
	private int entrycnt;

	// We could also map null-key args into a special Object instance and then handle it normally from there, but we'd still have to check for null
	// params as we do now, and while that would give us more uniform code, the current exception we make for null keys doesn't clutter our code
	// significantly, and it does allows us to "look up" the null key much faster.
	// Eg.	private static final Object NULLKEY = new Object()
	private V nullkeyValue;
	private boolean nullkeyExists;

	private KeysCollection keysview;
	private ValuesCollection valuesview;
	private EntriesCollection entriesview;
	private boolean entriesview_isvalid;

	// recycled iterators
	private KeysIterator keys_iterator;
	private ValuesIterator values_iterator;

	public HashedMap() {this(0);}
	public HashedMap(int initcap) {this(initcap, 0);}
	public HashedMap(int initcap, float factor)  {this(initcap, factor, false);}

	public boolean isEmpty() {return (entrycnt == 0);}
	public int size() {return entrycnt;}
	protected int bucketCount() {return capacity;}  // useful for test harness

	protected HashedMap(int initcap, float factor, boolean set_only)
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
	@Override
	public void clear()
	{
		for (int idx = keytbl.length - 1; idx != -1; idx--) {
			if (keytbl[idx] == null) continue;
			java.util.Arrays.fill(keytbl[idx], null);
			if (!keyset_only) java.util.Arrays.fill(valtbl[idx], null);
		}
		java.util.Arrays.fill(bucketsizes, (byte)0);
		nullkeyValue = null;
		nullkeyExists = false;
		if (entrycnt != 0) entriesview_isvalid = false;
		entrycnt = 0;
	}

	@Override
	public boolean containsKey(Object key)
	{
		if (key == null) return nullkeyExists;
		int idx = getBucket(key);

		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
			// identity test has strong possibility of avoiding the cost of equals() call
			if (key == keytbl[idx][idx2] || key.equals(keytbl[idx][idx2])) return true;
		}
		return false;
	}

	// This is never called in keyset_only mode
	@Override
	public V get(Object key)
	{
		if (key == null) return nullkeyValue;
		int idx = getBucket(key);

		// considered looping backwards from bucketsizes[idx]-1, but almost twice as slow!
		for (int idx2 = 0; idx2 != bucketsizes[idx]; idx2++) {
			// identity test has strong possibility of avoiding the cost of equals() call
			if (key == keytbl[idx][idx2] || key.equals(keytbl[idx][idx2])) return valtbl[idx][idx2];
		}
		return null;
	}

	@Override
	public V put(K key, V value)
	{
		if (key == null) {
			if (!nullkeyExists) {
				entrycnt++;
				entriesview_isvalid = false;
			}
			V oldvalue = nullkeyValue;
			nullkeyValue = value;
			nullkeyExists = true;
			return oldvalue;
		}

		if (entrycnt == threshold) {
			// It may turn out that we're replacing an existing value rather than adding a new mapping, but even that means we're infinitesmally
			// close to exceeding the threshold, so grow the hash table now anyway.
			capacity <<= 1;  // double the capacity
			allocateBuckets();
		}
		int idx = getBucket(key);
		K[] keyslot = keytbl[idx];
		int bktsiz = bucketsizes[idx];
		int idx2 = 0;

		if (keyslot == null) {
			// this bucket doesn't exist yet, so its obviously a new key, and we need to create its bucket
			growBucket(idx);
			keyslot = keytbl[idx];
		} else {
			// check if key already exists
			if (keyset_only) {
				//caller should already have verified if key exists, and must not make duplicate put() calls
				idx2 = bktsiz;
			} else {
				while (idx2 != bktsiz) {
					if (key == keyslot[idx2] || key.equals(keyslot[idx2])) break;
					idx2++;
				}
			}

			if (idx2 == keyslot.length) {
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
				growBucket(idx);
				keyslot = keytbl[idx];
			}
		}
		V oldvalue = null;

		if (idx2 == bktsiz) {
			// adding a new key
			keyslot[idx2] = key;
			entrycnt++;
			bucketsizes[idx]++;
			entriesview_isvalid = false;
			if (keyset_only) return null;
		} else {
			// Updating the value of an existing key
			// As explained above, we do not enter this code path in set-only mode
			oldvalue = valtbl[idx][idx2];
		}
		valtbl[idx][idx2] = value;
		return oldvalue;
	}

	@Override
	public V remove(Object key)
	{
		if (key == null) {
			if (!nullkeyExists) return null;
			V oldvalue = nullkeyValue;
			nullkeyValue = null;
			nullkeyExists = false;
			entrycnt--;
			entriesview_isvalid = false;
			return oldvalue;
		}
		int idx = getBucket(key);
		int bktsiz = bucketsizes[idx];

		for (int idx2 = 0; idx2 != bktsiz; idx2++) {
			if (key == keytbl[idx][idx2] || key.equals(keytbl[idx][idx2])) {
				V oldvalue = (keyset_only ? null : valtbl[idx][idx2]);

				if (idx2 != bktsiz - 1) {
					// shorten this bucket by swapping final key into the slot we're now vacating
					keytbl[idx][idx2] = keytbl[idx][bktsiz - 1];
					if (!keyset_only) valtbl[idx][idx2] = valtbl[idx][bktsiz - 1];
				}
				keytbl[idx][bktsiz - 1] = null;
				if (!keyset_only) valtbl[idx][bktsiz - 1] = null;
				bucketsizes[idx]--;
				entrycnt--;
				entriesview_isvalid = false;
				return oldvalue;
			}
		}
		return null;
	}

	// This is never called in keyset_only mode
	@Override
	public boolean containsValue(Object val)
	{
		if (val == nullkeyValue && nullkeyExists) return true;

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

	@Override
	public void putAll(java.util.Map<? extends K, ? extends V> srcmap)
	{
		if (!srcmap.isEmpty()) entriesview_isvalid = false;
		java.util.Set<? extends java.util.Map.Entry<? extends K, ? extends V>> srcitems = srcmap.entrySet();
		java.util.Iterator<? extends java.util.Map.Entry<? extends K, ? extends V>> itsrc = srcitems.iterator();
		
		while (itsrc.hasNext()) {
			java.util.Map.Entry<? extends K, ? extends V> entry = itsrc.next();
			put(entry.getKey(), entry.getValue());
		}
	}

	public int trimToSize()
	{
		int newcap = 1;
		int cnt = entrycnt;
		if (nullkeyExists) cnt--;
		while (((int)(newcap * loadfactor)) <= cnt) newcap <<= 1;
	
		if (newcap != capacity) {
			entriesview_isvalid = false;
			capacity = newcap;
			allocateBuckets();
		}
		return capacity;
	}

	private void allocateBuckets()
	{
		threshold = (int)(capacity * loadfactor);
		hashmask = capacity - 1;
		entrycnt = (nullkeyExists ? 1 : 0);

		K[][] oldkeys = keytbl;
		V[][] oldvals = valtbl;
		byte[] oldsizes = bucketsizes;
		bucketsizes = new byte[capacity];

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
					if (keyset_only) {
						put(oldkeys[idx][idx2], null);
					} else {
						put(oldkeys[idx][idx2], oldvals[idx][idx2]);
					}
				}
			}
		}
	}

	private void growBucket(int idx)
	{
		K[] oldkeys = keytbl[idx];
		V[] oldvals = (keyset_only ? null : valtbl[idx]);
		int newsiz = (keytbl[idx] == null ? BUCKETCAP_INIT : keytbl[idx].length + BUCKETCAP_INCR);
		if (newsiz > BUCKETCAP_MAX) newsiz = BUCKETCAP_MAX;

		if (!keyset_only) {
			@SuppressWarnings("unchecked")
			V[] uncheck_v = (V[])new Object[newsiz];
			valtbl[idx] = uncheck_v;
		}
		@SuppressWarnings("unchecked")
		K[] uncheck_k = (K[])new Object[newsiz];
		keytbl[idx] = uncheck_k;

		if (oldkeys != null) {
			System.arraycopy(oldkeys, 0, keytbl[idx], 0, oldkeys.length);
			if (!keyset_only) System.arraycopy(oldvals, 0, valtbl[idx], 0, oldvals.length);
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
	
	// these methods simply map the given Iterator coordinates to a key/value ref without any validation
	private K getKey(int bktid, int bktslot)
	{
		if (bktid == -1) return null;
		return keytbl[bktid][bktslot];
	}

	// This is never called in keyset_only mode
	private V getValue(int bktid, int bktslot)
	{
		if (bktid == -1) return nullkeyValue;
		return valtbl[bktid][bktslot];
	}

	public byte[] getBucketStats(boolean printstats, int mincolls)
	{
		return getBucketStats(size(), bucketsizes, printstats, mincolls);  // NB: bucketsizes.length is equal to this.capacity
	}

	// some instrumentation to provide collision stats
	static byte[] getBucketStats(int entrycnt, byte[] bucketsizes, boolean printstats, int mincolls)
	{
		byte[] asort = new byte[bucketsizes.length];
		System.arraycopy(bucketsizes, 0, asort, 0, bucketsizes.length);
		java.util.Arrays.sort(asort);

		if (printstats) {
			int peakslot = asort.length - 1;
			int peak = asort[peakslot];
			if (announcestdout) System.out.println("Size="+entrycnt+", Cap="+asort.length+" - MaxBucket="+peak);
			while (peak > mincolls && peakslot != 0) {
				while (asort[peakslot] == peak && peakslot != 0) peakslot--;
				peak = asort[peakslot];
				if (announcestdout) System.out.println("Bucket-"+peakslot+" = "+peak);	
			}
		}
		return asort;
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
		if (entriesview == null) entriesview = new EntriesCollection();
		entriesview.refresh();
		return entriesview;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(size() * 5);
		sb.append(getClass().getName());
		if (keyset_only) sb.append("/Set");
		sb.append('=').append(size()).append(" {");
		String dlm = "";
		java.util.Iterator<K> it = keysIterator();
		while (it.hasNext()) {
			K key = it.next();
			sb.append(dlm).append(key);
			if (!keyset_only) sb.append('=').append(get(key));
			dlm = ", ";
		}
		sb.append("}");
		return sb.toString();
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

	/*
	 * ===================================================================================================================
	 * These inner classes all exist purely to support the required Collections views of this map.
	 * ===================================================================================================================
	 */

	// Our own data structures aren't based on this class, but we have to simulate it to support some of the required Collections views.
	// We record the original key rather than its co-ordinates, so that it survives any reorganisation of the Map due to remove ops.
	// We can't cache the value here, as it would be invalidated by updates to the underlying Map (which we can't see).
	private final class MapEntry
		implements java.util.Map.Entry<K,V>
	{
		private K key;
		private int setslot;  // -1 means this Entry has been invalidated (eg. removed from set)

		private MapEntry(int bktid, int bktslot, int sslot)
		{
			setslot = sslot;
			key = HashedMap.this.getKey(bktid, bktslot);
		}

		@Override
		public K getKey() {return key;}
		@Override
		public V getValue() {return (setslot == -1 ? null : HashedMap.this.get(key));}

		@Override
		public V setValue(V newval)
		{
			if (setslot == -1) throw new RuntimeException("Setting Value on invalidated Map.Entry");
			return HashedMap.this.put(key, newval);
		}
	}


	private final class KeysCollection
		extends java.util.AbstractSet<K>
	{
		@Override
		public java.util.Iterator<K> iterator() {return new KeysIterator();}
		@Override
		public int size() {return HashedMap.this.size();}
		/*
		 * Override methods which AbstractSet implements naively (it uses lowest-common-denominator Collections view)
		 */
		@Override
		public boolean contains(Object obj) {return containsKey(obj);}
		@Override
		public boolean remove(Object obj) {boolean sts = contains(obj); HashedMap.this.remove(obj); return sts;}
		@Override
		public void clear() {HashedMap.this.clear();}
	}


	private final class ValuesCollection
		extends java.util.AbstractCollection<V>
	{
		@Override
		public java.util.Iterator<V> iterator() {return new ValuesIterator();}
		@Override
		public int size() {return HashedMap.this.size();}
		/*
		 * Override methods which AbstractCollection implements naively (it uses lowest-common-denominator Collections view)
		 * We can do a fraction better on contains(), since even though ContainsValue() does a linear scan, at least it avoids an iterator.
		 * The base class implements remove() by iterating until first match, so let it.
		 */
		@Override
		public boolean contains(Object obj) {return containsValue(obj);}
		@Override
		public void clear() {HashedMap.this.clear();}
	}


	// The way this is implemented means that for different instances of this set, the Map.Entry objects for a given key will not be equal.
	// That's probably fair enough given that this Map implementation does not contain any underlying Map.Entry nodes, and there's nothing
	// in the Map contract that states how collections-views relate to the backing data store.
	// For multiple iterations over the same instance of this set however, the Map.Entry nodes will be consistent, which does seem like
	// something users are entitled to expect.
	private final class EntriesCollection
		extends java.util.AbstractSet<java.util.Map.Entry<K,V>>
	{
		private MapEntry[] arr;
		private int arrcnt;

		@Override
		public int size() {return arrcnt;}

		@Override
		public java.util.Iterator<java.util.Map.Entry<K, V>> iterator() {return new EntriesIterator(this);}

		private boolean refresh()
		{
			if (entriesview_isvalid) return false;

			if (arr == null || arr.length < HashedMap.this.size()) {
				@SuppressWarnings("unchecked")
				MapEntry[] unchecked = (MapEntry[])java.lang.reflect.Array.newInstance(MapEntry.class, HashedMap.this.size());
				arr = unchecked;
			} else {
				java.util.Arrays.fill(arr, null);
			}
			arrcnt = 0;
			KeysIterator kit = new KeysIterator();

			while (kit.hasNext()) {
				kit.next();
				arr[arrcnt] = new MapEntry(kit.bktid, kit.bktslot, arrcnt);
				arrcnt++;
			}
			entriesview_isvalid = true;
			return true;
		}

		// MapEntry is a private type which could only have been obtained from this set (or a previous incarnation of it) in the first place
		// so just verify that the given entry is consistent with the current set.
		@Override
		public boolean contains(Object obj)
		{
			if (!(obj instanceof HashedMap<?,?>.MapEntry)) return false;
			@SuppressWarnings("unchecked")
			MapEntry entry = (MapEntry)obj;
			if (entry.setslot < 0 || entry.setslot >= arrcnt) return false;
			return entry == arr[entry.setslot];
		}

		@Override
		public boolean remove(Object obj)
		{
			if (!contains(obj)) return false;
			@SuppressWarnings("unchecked")
			MapEntry entry = (MapEntry)obj;
			removeSlot(entry.setslot);
			return true;
		}

		@Override
		public void clear()
		{
			java.util.Arrays.fill(arr, null);
			arrcnt = 0;
			HashedMap.this.clear();
		}

		MapEntry getSlot(int slot)
		{
			return arr[slot];
		}

		void removeSlot(int slot)
		{
			MapEntry entry = arr[slot];
			if (slot != arrcnt - 1) {
				arr[slot] = arr[arrcnt - 1];
				arr[slot].setslot = slot;
			}
			arr[--arrcnt] = null;
			boolean isvalid = entriesview_isvalid;
			HashedMap.this.remove(entry.getKey());
			entry.setslot = -1;
			entriesview_isvalid = isvalid; // hash map's remove() invalidated us, but as we called it, we've maintained our validity status
		}
	}


	private final class KeysIterator
		extends CollectionIterator<K>
	{
		@Override
		public K next() {moveNext(); return getKey(bktid, bktslot);}
	}

	private final class ValuesIterator
		extends CollectionIterator<V>
	{
		@Override
		public V next() {moveNext(); return getValue(bktid, bktslot);}
	}

	private final class EntriesIterator
		implements java.util.Iterator<Entry<K, V>>
	{
		EntriesCollection set;
		int slot = -1;

		EntriesIterator(EntriesCollection set_p) {set = set_p;}
		@Override
		public boolean hasNext() {return (slot < set.size() - 1);}
		@Override
		public java.util.Map.Entry<K,V> next() {if (!hasNext()) throw new java.util.NoSuchElementException(); return set.getSlot(++slot);}
		@Override
		public void remove() {set.removeSlot(slot--);}
	}

	
	private abstract class CollectionIterator<E>
		implements java.util.Iterator<E>
	{
		int bktid;  	// current index within bucket array
		int bktslot;	// current index within current bucket
		private int next_bktid;
		private int next_bktslot;

		CollectionIterator() {reset();}

		final void reset()
		{
			bktid = -2;
			findNext();
		}

		@Override
		public final boolean hasNext()
		{
			return (next_bktid < capacity);
		}

		@Override
		public final void remove()
		{
			HashedMap.this.remove(getKey(bktid, bktslot));
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
			if (bktid < 0) {
				if (bktid == -2) {
					// at start of iteration
					if (nullkeyExists) {
						//stop here on special null-key entry
						next_bktid = -1;
						return;
					}
				}
				// advance to bucket array
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
