/*
 * Copyright 2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.util.Collection;

/*
 * This class rounds off the ArrayRef hierarchy, but anybody who wants this functionality would be better off
 * using the JDK's built-in ArrayList.
 */
public class ObjectArrayRef<T> extends ArrayRef<T[]> {

	public ObjectArrayRef(T[] src, Allocator<T[]> allocator) {this(src, 0, src==null?0:src.length, allocator);}
	public ObjectArrayRef(ObjectArrayRef<T> src, Allocator<T[]> allocator) {this(src, 0, getSize(src), allocator);}
	public ObjectArrayRef(ObjectArrayRef<T> src, int off, int len, Allocator<T[]> allocator) {this(getBuffer(src), getOffset(src, off), len, allocator);}
	public ObjectArrayRef(Collection<T> src, Allocator<T[]> allocator) {this(src, 0, src==null?0:src.size(), allocator);}

	@Override
	public int totalBufferSize(T[] buf) {return (buf == null ? 0 : buf.length);}
	@Override
	protected T[] allocBuffer(int cap) {return (allocator == null ? super.allocBuffer(cap) : allocator.allocate(cap));}

	public T getElement(int idx) {return buffer()[offset(idx)];}
	public void setElement(int idx, T val) {buffer()[offset(idx)] = val;}

	private final Allocator<T[]> allocator;

	public ObjectArrayRef(int capacity, Allocator<T[]> allocator) {
		super(capacity, allocator);
		this.allocator = allocator;
	}

	public ObjectArrayRef(T[] src, int off, int len, Allocator<T[]> allocator) {
		super(src, off, len, allocator);
		this.allocator = allocator;
	}

	public ObjectArrayRef(Collection<T> src, int off, int len, Allocator<T[]> allocator) {
		this(len, allocator);
		if (len != 0) {
			final int src_lmt = off + len;
			int src_off = 0;
			int idx = 0;
			for (T elem : src) {
				if (src_off == src_lmt) break;
				if (src_off++ < off) continue;
				setElement(idx++, elem);
			}
			setSize(len);
		}
	}

	// Same equals methods as ByteArrayRef
	@Override
	public int hashCode()
	{
		int hash = 0;
		int off = offset();
		final T[] buf = buffer();
		final int lmt = off + size();

		while (off != lmt) {
			hash ^= ((hash << 5) + (hash >>> 2) + buf[off++].hashCode());
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == this) return true;
		if (!canEqual(obj)) return false;
		@SuppressWarnings("unchecked") ObjectArrayRef<T> ref2 = (ObjectArrayRef<T>)obj;
		if (!ref2.canEqual(this) || ref2.size() != size()) return false;

		int off = offset();
		int off2 = ref2.offset();
		final T[] buf = buffer();
		final T[] buf2 = ref2.buffer();
		final int lmt = off + size();

		while (off != lmt) {
			if (buf2[off2++] != buf[off++]) return false;
		}
		return true;
	}

	@Override
	public boolean canEqual(Object obj)
	{
		return (obj instanceof ObjectArrayRef<?>);
	}
}