/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * Handy array handle.&nbsp;Allows us to specify all or part of an array buffer without having to pass
 * around 3 separate parameters (buffer, offset, length).
 * <br/>
 * Note that Java generics don't take primitive types like byte and char, so instances of this class have to be declared
 * with T as byte[], char[], Object[] etc.
 */
public class ArrayRef<T>
{
	public T ar_buf;
	public int ar_off;
	public int ar_len;

	public final int size() {return ar_len;}
	public final int capacity() {return (ar_buf == null ? 0 : java.lang.reflect.Array.getLength(ar_buf) - ar_off);}

	public ArrayRef(T src) {this(src, false);}
	public ArrayRef(T src, boolean copy) {this(src, 0, java.lang.reflect.Array.getLength(src), copy);}
	public ArrayRef(ArrayRef<T> src) {this(src, false);}
	public ArrayRef(ArrayRef<T> src, boolean copy) {this(src, 0, src.ar_len, copy);}
	public ArrayRef(ArrayRef<T> src, int src0, int len, boolean copy) {this(src.ar_buf, src.ar_off + src0, len, copy);}

	public ArrayRef(T src, int src0, int len, boolean copy)
	{
		if (copy)
		{
			ar_off = 0;
			ar_len = len;

			if (ar_len == 0)
			{
				ar_buf = null;
				return;
			}
			@SuppressWarnings("unchecked")
			T uncheckedbuf = (T)java.lang.reflect.Array.newInstance(src.getClass().getComponentType(), ar_len);
			ar_buf = uncheckedbuf;  //minimised scope of Suppress annotation
			System.arraycopy(src, src0, ar_buf, ar_off, ar_len);
		}
		else
		{
			ar_buf = src;
			ar_off = src0;
			ar_len = len;
		}
	}

	/**
	 * Allocate the buffer, but leave it marked as empty (len=0).
	 * <br/>
	 * If size is -1 we don't even allocate the buffer, and this is equivalent to the basic ArrayRef() constructor
	 */
	public ArrayRef(Class<?> clss, int cap)
	{
		if (cap != -1) {
			@SuppressWarnings("unchecked")
			T uncheckedbuf = (T)java.lang.reflect.Array.newInstance(clss, cap);
			ar_buf = uncheckedbuf;  //minimised scope of Suppress annotation
		}
	}

	/**
	 * Note that since this class is basically a glorified pointer, the Equals method merely checks that they both point to the same memory location.
	 * <br/>
	 * The fact that two ArrayRef objects point at different buffers does not mean that the pointed-to contents are unequal in terms of their own
	 * object-class semantics, but they are unequal from the point of view of this class, so if you are interested in that sort of equality, then
	 * you need to perform additional checks when this returns False (or just call something else or even override it).<br/>
	 * Subclasses of ArrayRef must override this method, if they are in a position to know what their array type is.
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (obj == this) return true;
		if (!(obj instanceof ArrayRef<?>)) return false;
		ArrayRef<?> ah2 = (ArrayRef<?>)obj;
		return (ah2.ar_buf == ar_buf && ah2.ar_off == ar_off && ah2.ar_len == ar_len);
	}

	// This hashcode is not unique (nor do hashcodes have to be), but it is distinct enough to be a viable hash key.
	@Override
	public int hashCode()
	{
		if (ar_buf == null) return 0;
		return ar_buf.hashCode() + ar_off + ar_len;
	}

	// NB: These methods don't null the vacated array elements.
	// First, we don't even know if the element type is Object or primitive, and secondly this ArrayRef may simply be a pointer into a
	// buffer owned by something else.
	// It is therefore always the responsibility of the caller to nullify any elements that are no longer needed.	
	public final void advance(int incr)
	{
		ar_off += incr;
		ar_len -= incr;
	}

	public final void truncateBy(int incr)
	{
		ar_len -= incr;
	}

	public final void truncateTo(int len)
	{
		ar_len = len;
	}
}
