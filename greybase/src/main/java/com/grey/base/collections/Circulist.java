/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

/**
 * This class implements an array-backed circular list.
 * <br>
 * The head and tail chase each other up and down the buffer, and will wrap around as required. When/if they wrap around we will have two
 * non-contiguous segments: a head segment that runs to the end of the buffer, and a tail segment that starts at position 0.
 * A wrapped list can be identified by the fact that the tail index will be less than the head. Indeed, if we find ourselves operating on
 * any elements whose index is less than the head or greater than the tail, then we're dealing with a wrapped list.
 */
public final class Circulist<T>
{
	private final Class<T> clss;
	private final int increment;

	private T[] arr;
	private int count;
	private int head = 0;
	private int tail = -1;

	public int size() {return count;}
	public int capacity() {return arr.length;}

	public Circulist(Class<?> clss) {this(clss, 64, 64);}

	public Circulist(Class<?> clss, int initcap, int incr)
	{
		@SuppressWarnings("unchecked")
		Class<T> unchecked_clss = (Class<T>)clss;
		this.clss = unchecked_clss;  //minimised scope of Suppress annotation
		increment = incr;
		grow(initcap);
	}

	public void clear()
	{
		head = 0;
		tail = -1;
		count = 0;
		java.util.Arrays.fill(arr, null);  // release the vacated slots' object references, to let GC do its work
	}

	// As with all methods, the caller supplies an index relative to Head, so pos=0 means the first element, wherever it is
	public T get(int pos)
	{
		return arr[physicalIndex(pos)];
	}
	
	public T set(int pos, T newval)
	{
		int physpos = physicalIndex(pos);
		T oldval = arr[physpos];
		arr[physpos] = newval;
		return oldval;
	}

	public void append(T obj)
	{
		insert(count, obj);
	}

	public void prepend(T obj)
	{
		insert(0, obj);
	}

	public void insert(int pos, T obj)
	{
		if (count == arr.length) {
			// time to grow the array
			if (increment == 0) throw new UnsupportedOperationException(getClass().getName()+"/"+clss.getName()+" has max capacity="+arr.length);
			grow(increment);
		}

		if (pos == count) {
			// append at tail - checking for this first means we take care of incrementing tail on first element (when it might be -1)
			if (++tail == arr.length) tail = 0;
			arr[tail] = obj;
		} else if (pos == 0) {
			// prepend - the Head pointer simply retreats, to avoid any copying or shifting
			if (head-- == 0) head = arr.length - 1;
			arr[head] = obj;
		} else {
			// yes, if we grew the array above, we will do yet another copy here, but Grows are presumed infrequent
			int physpos = physicalIndex(pos);
			if (head == 0 || physpos < head) {
				// shift tail segment up - physpos<head means list has already wrapped, and either way, above grow() means tail has room to advance
				tail++;
				System.arraycopy(arr, physpos, arr, physpos + 1, tail - physpos);
			} else {
				// list is a single segment, offset from start of array - shift down the leading elements to make room for the new one
				System.arraycopy(arr, head, arr, head - 1, physpos - head);
				head--;
				physpos--;
			}
			arr[physpos] = obj;
		}
		count++;
	}

	public T remove(int pos)
	{
		int physpos = physicalIndex(pos);
		T obj = arr[physpos];

		// we can optimise a bit when removing head or tail - the head advances and the tail retreats
		if (pos == 0) { //means physpos==head
			arr[head] = null;  //release the vacated slot's object reference
			if (++head == arr.length) head = 0;
		} else if (physpos == tail) {
			arr[tail] = null;  //release the vacated slot's object reference
			if (tail-- == 0) tail = arr.length - 1;
		} else {
			if (head == 0 || physpos < head) {
				// Need to do a shift to fill the deleted element's slot.
				// If head points to distinct upper segment, then this operation only applies to the tail segment (in lower part of buffer),
				// so have to shift trailing elements down to fill deleted slot.
				// If list isn't wrapped, then we have the option of shifting up unless tail is already at upper limit, or shifting down
				// unless head is already at lower limit (would still be possible to move head or tail even if at their limits, but they'd have
				// to wrap around, which makes for slower and vastly more complex code).
				// Our bias is to shift up if possible, but the most efficient limits test is head==0 which (if satisfied) tells us there's
				// definitely room to shift down. It doesn't necessarily preclude shifting up, but it's less efficient to test for the tail
				// limit (tail==arr.length-1) which would preclude that.
				System.arraycopy(arr, physpos + 1, arr, physpos, tail - physpos);
				arr[tail--] = null;  // release the vacated tail slot's object reference
			} else {
				// the list is not wrapped, and we go for our preferred option of shifting up (see above comment)
				System.arraycopy(arr, head, arr, head + 1, physpos - head);
				arr[head++] = null;  // release the vacated head slot's object reference
			}
		}
		count--;
		return obj;
	}

	// It might be possible to optimise this a bit by doing some multi-element operations, but the code would look horrendous when Head and Tail
	// have wrapped around.
	// It would also require quite a few shifts and fills to handle that scenario, so it may well not be more efficient anyway, in many cases.
	public void removeRange(int pos_from, int pos_to)
	{
		int delcount = pos_to - pos_from + 1;
		for (int loop = 0; loop != delcount; loop++) {
			remove(pos_from);
		}
	}

	// NB: Unlike the JDK ArrayList, we match the 'obj' param using reference equality (==) not the equals() method 
	public boolean remove(T obj)
	{
		int pos = indexOf(obj);
		if (pos == -1) return false;
		remove(pos);
		return true;
	}

	/**
	 * Unlike many of the methods in this class, we allow this to be called on an empty list, so that callers can consume a
	 * list by looping on this call till it returns null.
	 * There is nothing in this class which prohibits null members, but it's up to the caller to beware whether their list may
	 * contain Nulls and hence avoid this method if so.
	 */
	public T remove()
	{
		if (count == 0) return null;
		return remove(0);
	}

	// NB: Unlike the JDK ArrayList, we match the 'obj' param using reference equality (==) not the equals() method 
	public int indexOf(int start, T obj)
	{
		int off = physicalIndex(start);

		if (off >= head) {
			// start position is in the head segment
			int limit = head + count;
			if (limit > arr.length) limit = arr.length;

			for (int idx = off; idx != limit; idx++) {
				if (arr[idx] == obj) return idx - head;
			}
			if (tail > head) {
				// list is not wrapped, so the head segment (which we've now searched) was the the entire list
				return -1;
			}
			// do another scan from start of wrapped tail segment
			off = 0;
		}

		// scan the tail segment
		int limit = tail + 1;
		for (int idx = off; idx != limit; idx++) {
			if (arr[idx] == obj) return idx + (arr.length - head);
		}
		return -1;
	}

	public int indexOf(T obj)
	{
		if (count == 0) return -1;
		return indexOf(0, obj);
	}

	private int physicalIndex(int logicalIndex)
	{
		if (logicalIndex < 0 || logicalIndex >= count) {
			throw new IllegalArgumentException("Circulist index="+logicalIndex+"/"+count+" - Head="+head+"/Tail="+tail+"/Cap="+arr.length);
		}
		int physpos = head + logicalIndex;
		int wrap = physpos - arr.length;
		if (wrap >= 0) physpos = wrap;
		return physpos;
	}
	
	public T[] toArray()
	{
		return toArray(null);
	}

	public T[] toArray(T[] arrcopy)
	{
		if (arrcopy == null) arrcopy = alloc(count);
		if (count == 0) return arrcopy;

		if (tail < head) {
			// head and tail have wrapped, and we know head segment extends all way to end of buffer
			int hcount = arr.length - head;
			System.arraycopy(arr, head, arrcopy, 0, hcount);
			System.arraycopy(arr, 0, arrcopy, hcount, tail + 1);
		} else {
			System.arraycopy(arr, head, arrcopy, 0, count);
		}
		return arrcopy;
	}

	private int grow(int delta)
	{
		int oldcap = (arr == null ? 0 : arr.length);
		int newcap = oldcap + delta;
		T[] arrnew = alloc(newcap);
		arr = toArray(arrnew);
		head = 0;
		tail = count - 1;
		return delta;
	}

	@SuppressWarnings("unchecked")
	private final T[] alloc(int cap)
	{
		return (T[])java.lang.reflect.Array.newInstance(clss, cap);
	}

	@Override
	public String toString()
	{
		String txt = "Circulist#"+System.identityHashCode(this)+"="+count+"/head="+head+"/tail="+tail+"/cap="+arr.length;
		String dlm = " [";
		for (int idx = 0; idx != count; idx++) {
			int phys = (head+idx)%arr.length;
			txt += dlm+phys+"#"+arr[phys];
			dlm = "; ";
		}
		txt += "]";
		return txt;
	}
}
