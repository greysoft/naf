/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Handy array handle. Allows us to specify all or part of an array buffer without having to pass around the 3 separate
 * parameters that describe it (buffer, offset, length).
 * <br>
 * Note that Java generics don't take primitive types like byte and char, so instances of this class have to be declared
 * with T as byte[], char[], Object[] etc. It does not make sense for T to be anything other than an array.
 */
public class ArrayRef<T>
{
	public interface Allocator<T> {
		T allocate(int capacity);
	}

	private T ar_buf;
	private int ar_off;
	private int ar_len;

	public ArrayRef() {this(0, null);}
	public ArrayRef(T src, int off, int len) {this(src, off, len, null);}
	public ArrayRef(ArrayRef<T> src) {this(src, null);}
	public ArrayRef(ArrayRef<T> src, Allocator<T> allocator) {this(src, 0, getSize(src), allocator);}
	public ArrayRef(ArrayRef<T> src, int off, int len) {this(src, off, len, null);}
	public ArrayRef(ArrayRef<T> src, int off, int len, Allocator<T> allocator) {this(getBuffer(src), getOffset(src, off), len, allocator);}

	public ArrayRef<T> set(T src) {return set(src, 0, totalBufferSize(src));}
	public ArrayRef<T> set(ArrayRef<T> src) {return set(src, 0, getSize(src));}
	public ArrayRef<T> set(ArrayRef<T> src, int off) {return set(src, off, src.size() - off);}
	public ArrayRef<T> set(ArrayRef<T> src, int off, int len) {return set(getBuffer(src), getOffset(src, off), len);}

	public final int size() {return ar_len;}
	public final int offset() {return ar_off;}
	public final T buffer() {return ar_buf;}
	public final void setSize(int len) {ar_len = len;}
	private final void setOffset(int off) {ar_off = off;}
	private final void setBuffer(T buf) {ar_buf = buf;}

	public final int offset(int off) {return offset() + off;}
	public final int limit() {return offset() + size();}
	protected final int capacity() {return totalBufferSize() - offset();}
	protected final int spareCapacity() {return capacity() - size();}

	public final void incrementSize(int len) {setSize(size() + len);}
	private final void incrementOffset(int off) {setOffset(offset() + off);}
	public ArrayRef<T> clear() {setSize(0); return this;}

	public T toArray() {return toArray(false);}
	public T toArray(boolean nocopy) {return toArray(0, size(), nocopy);}
	public T toArray(int off, int len) {return toArray(off, len, false);}

	// subclasses should override the reflective one, for efficiency
	protected int totalBufferSize() {return totalBufferSize(buffer());}
	protected int totalBufferSize(T buf) {return (buf == null ? 0 : java.lang.reflect.Array.getLength(buf));}

	public ArrayRef(T src, int off, int len, Allocator<T> allocator)
	{
		if (allocator == null) {
			ar_buf = src;
			ar_off = off;
		} else {
			if (len != 0) {
				ar_buf = allocator.allocate(len);
				System.arraycopy(src, off, ar_buf, 0, len);
			}
		}
		ar_len = len;
	}

	public ArrayRef(int capacity, Allocator<T> allocator)
	{
		if (capacity > 0) {
			//beware that many older callers use capacity == -1 rather than zero to indicate no-alloc
			if (allocator == null) throw new UnsupportedOperationException("No Allocator for "+this+" - cap="+capacity);
			ar_buf = allocator.allocate(capacity);
		}
	}

	// subclasses should override this for efficiency, to avoid doing reflection
	protected T allocBuffer(int capacity) {
		if (buffer() == null)
			throw new UnsupportedOperationException("No prototype buffer - cannot alloc cap="+capacity+" on "+this);
		return allocBuffer(buffer().getClass(), capacity);
	}

	public ArrayRef<T> set(T buf, int off, int len)
	{
		setBuffer(buf);
		setOffset(off);
		setSize(len);
		return this;
	}

	public void copyIn(T src_buf, int src_off, int off, int len) {
		if (len != 0) System.arraycopy(src_buf, src_off, buffer(), offset(off), len);
	}

	public void copyIn(T src_buf, int src_off, int len) {
		copyIn(src_buf, src_off, 0, len);
	}

	public void copyOut(int off, T dst_buf, int dst_off, int len) {
		if (len != 0) System.arraycopy(buffer(), offset(off), dst_buf, dst_off, len);
	}

	public void copyOut(T dst_buf, int dst_off) {
		copyOut(0, dst_buf, dst_off, size());
	}

	public static <T> void copy(ArrayRef<T> src, int src_off, ArrayRef<T> dst, int dst_off, int len) {
		dst.copyIn(getBuffer(src), getOffset(src, src_off), dst_off, len);
	}

	public ArrayRef<T> ensureCapacity(int capacity) {
		if (capacity() >= capacity) return this;
		T newbuf = allocBuffer(capacity);
		copyOut(newbuf, 0);
		set(newbuf, 0, size());
		return this;
	}

	public ArrayRef<T> ensureSpareCapacity(int spareCapacity) {
		ensureCapacity(size() + spareCapacity);
		return this;
	}

	// The nocopy arg avoids a copy if possible, by returning the underlying buffer.
	// This will never return null.
	public T toArray(int off, int len, boolean nocopy) {
		if (nocopy && off == 0 && offset() == 0 && len == totalBufferSize()) {
			return (buffer() == null ? allocBuffer(0) : buffer()); //len must be zero if buffer is null
		}
		T buf = allocBuffer(len);
		copyOut(off, buf, 0, len);
		return buf;
	}

	/**
	 * Note that since this class is basically a glorified pointer, the Equals method merely checks that they both point to
	 * the same memory location.
	 * <br>
	 * The fact that two ArrayRef objects point at different buffers does not mean that the pointed-to contents are unequal
	 * in terms of their own array-class semantics, but they are unequal from the point of view of this class, so if you are
	 * interested in that sort of equality, this method is not relevant.
	 * <br>
	 * Subclasses of ArrayRef must override this method, if they are in a position to know what their array type is and expect
	 * equals() to take the buffer contents into account.
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (obj == this) return true;
		if (!canEqual(obj)) return false;
		ArrayRef<?> ref2 = (ArrayRef<?>)obj;
		return (ref2.buffer() == buffer() && ref2.offset() == offset() && ref2.size() == size() && ref2.canEqual(this));
	}

	// This hashcode is not unique (nor do hashcodes have to be), but it is distinct enough to be a viable hash key.
	@Override
	public int hashCode()
	{
		if (buffer() == null) return 0;
		return buffer().hashCode() + offset() + size();
	}

	/*
	 * Must be overridden by any subclass which is not willing to consider instances of this base class as equal
	 */
	public boolean canEqual(Object obj)
	{
		return (obj instanceof ArrayRef<?>);
	}

	@Override
	public String toString()
	{
		return getClass().getName()+"/"+System.identityHashCode(this)+" buf="+buffer()
				+" off="+offset()+"/len="+size()+"/cap="+capacity();
	}

	// NB: These methods don't null the vacated array elements.
	// First, we don't even know if the element type is Object or primitive, and secondly this ArrayRef may simply be a pointer into a
	// buffer owned by something else.
	// It is therefore always the responsibility of the caller to nullify any elements that are no longer needed.   
	public void advance(int incr)
	{
		incrementOffset(incr);
		incrementSize(-incr);
	}

	@SuppressWarnings("unchecked")
	private static <T> T allocBuffer(Class<?> bufferClass, int capacity) {
		return (T)java.lang.reflect.Array.newInstance(bufferClass.getComponentType(), capacity);
	}

	protected static <T> T getBuffer(ArrayRef<T> ref) {
		return (ref == null ? null : ref.buffer());
	}

	protected static <T> int getSize(ArrayRef<T> ref) {
		return (ref == null ? 0 : ref.size());
	}

	protected static <T> int getOffset(ArrayRef<T> ref) {
		return (ref == null ? 0 : ref.offset());
	}

	protected static <T> int getOffset(ArrayRef<T> ref, int off) {
		return (ref == null ? 0 : ref.offset(off));
	}
}
