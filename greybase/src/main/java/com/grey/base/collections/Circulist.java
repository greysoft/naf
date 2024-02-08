/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

import java.util.Arrays;

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
	private final int growthIncrement;

	private Object[] buffer;
	private int count;
	private int head = 0;
	private int tail = -1;

	public int size() {return count;}
	public int capacity() {return buffer.length;}

	public Circulist() {this(64, 64);}

	public Circulist(int initcap, int incr)
	{
		growthIncrement = incr;
		grow(initcap);
	}

	public void clear()
	{
		head = 0;
		tail = -1;
		count = 0;
		Arrays.fill(buffer, null);  // release the vacated slots' object references, to let GC do its work
	}

	// As with all methods, the caller supplies an index relative to Head, so pos=0 means the first element, wherever it is
	public T get(int pos)
	{
		int physpos = physicalIndex(pos);
		@SuppressWarnings("unchecked") T val = (T)buffer[physpos];
		return val;
	}
	
	public T set(int pos, T newval)
	{
		int physpos = physicalIndex(pos);
		@SuppressWarnings("unchecked") T oldval = (T)buffer[physpos];
		buffer[physpos] = newval;
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
		if (count == buffer.length) {
			// time to grow the array
			if (growthIncrement == 0) throw new UnsupportedOperationException("At capacity="+buffer.length+" with no growth allowed");
			grow(growthIncrement);
		}

		if (pos == count) {
			// append at tail - checking for this first means we take care of incrementing tail on first element (when it might be -1)
			if (++tail == buffer.length) tail = 0;
			buffer[tail] = obj;
		} else if (pos == 0) {
			// prepend - the Head pointer simply retreats, to avoid any copying or shifting
			if (head-- == 0) head = buffer.length - 1;
			buffer[head] = obj;
		} else {
			// yes, if we grew the array above, we will do yet another copy here, but Grows are presumed infrequent
			int physpos = physicalIndex(pos);
			if (head == 0 || physpos < head) {
				// shift tail segment up - physpos<head means list has already wrapped, and either way, above grow() means tail has room to advance
				tail++;
				System.arraycopy(buffer, physpos, buffer, physpos + 1, tail - physpos);
			} else {
				// list is a single segment, offset from start of array - shift down the leading elements to make room for the new one
				System.arraycopy(buffer, head, buffer, head - 1, physpos - head);
				head--;
				physpos--;
			}
			buffer[physpos] = obj;
		}
		count++;
	}

	public T remove(int pos)
	{
		int physpos = physicalIndex(pos);
		@SuppressWarnings("unchecked") T obj = (T)buffer[physpos];

		// we can optimise a bit when removing head or tail - the head advances and the tail retreats
		if (pos == 0) { //means physpos==head
			buffer[head] = null;  //release the vacated slot's object reference
			if (++head == buffer.length) head = 0;
		} else if (physpos == tail) {
			buffer[tail] = null;  //release the vacated slot's object reference
			if (tail-- == 0) tail = buffer.length - 1;
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
				System.arraycopy(buffer, physpos + 1, buffer, physpos, tail - physpos);
				buffer[tail--] = null;  // release the vacated tail slot's object reference
			} else {
				// the list is not wrapped, and we go for our preferred option of shifting up (see above comment)
				System.arraycopy(buffer, head, buffer, head + 1, physpos - head);
				buffer[head++] = null;  // release the vacated head slot's object reference
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

	/*
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
			if (limit > buffer.length) limit = buffer.length;

			for (int idx = off; idx != limit; idx++) {
				if (buffer[idx] == obj) return idx - head;
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
			if (buffer[idx] == obj) return idx + (buffer.length - head);
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
			throw new IllegalArgumentException("Circulist index="+logicalIndex+"/"+count+" - Head="+head+"/Tail="+tail+"/Cap="+buffer.length);
		}
		int physpos = head + logicalIndex;
		int wrap = physpos - buffer.length;
		if (wrap >= 0) physpos = wrap;
		return physpos;
	}

	public T[] toArray(T[] arrcopy)
	{
		if (count == 0)
			return arrcopy;
		if (arrcopy.length < count) {
			@SuppressWarnings("unchecked") T[] arr = (T[])Arrays.copyOf(buffer, count, arrcopy.getClass());
			if (head == 0)
				return arr;
			arrcopy = arr;
		}

		if (tail < head) {
			// head and tail have wrapped, and we know head segment extends all way to end of buffer
			int hcount = buffer.length - head;
			System.arraycopy(buffer, head, arrcopy, 0, hcount);
			System.arraycopy(buffer, 0, arrcopy, hcount, tail + 1);
		} else {
			System.arraycopy(buffer, head, arrcopy, 0, count);
		}
		return arrcopy;
	}

	private int grow(int delta)
	{
		int oldcap = (buffer == null ? 0 : buffer.length);
		int newcap = oldcap + delta;
		T[] arrnew = alloc(newcap);
		buffer = toArray(arrnew);
		head = 0;
		tail = count - 1;
		return delta;
	}

	private T[] alloc(int cap)
	{
		@SuppressWarnings("unchecked") T[] arr = (T[])new Object[cap];
		return arr;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Circulist#").append(System.identityHashCode(this)).append('=').append(count).append("/cap=").append(buffer.length)
			.append("/head=").append(head).append("/tail=").append(tail);
		String dlm = " [";
		for (int idx = 0; idx != count; idx++) {
			int phys = (head+idx)%buffer.length;
			sb.append(dlm).append(phys).append('#').append(buffer[phys]);
			dlm = "; ";
		}
		sb.append(']');
		return sb.toString();
	}
}
