/*
 * Copyright 2013-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

// A fast and simple class for handling lists of Integer primitives.
// Note that ar_off is always zero for this class, so no need to add it
public final class NumberList
	extends com.grey.base.utils.ArrayRef<int[]>
{
	private static final Allocator<int[]> ALLOCATOR = (n) -> new int[n];

	private final int increment;

	public NumberList() {this(16);}
	public NumberList(int incr) {super(incr, ALLOCATOR); increment=incr;}

	public int get(int idx) {return buffer()[idx];}

	@Override
	protected int totalBufferSize(int[] buf) {return (buf == null ? 0 : buf.length);}
	@Override
	protected int[] allocBuffer(int cap) {return ALLOCATOR.allocate(cap);}

	public void append(int v) {
		if (spareCapacity() == 0) ensureSpareCapacity(increment);
		buffer()[size()] = v;
		incrementSize(1);
	}

	public void append(com.grey.base.utils.ArrayRef<int[]> lst) {
		int lmt = lst.limit();
		for (int idx = lst.offset(); idx != lmt; idx++) {
			append(lst.buffer()[idx]);
		}
	}

	public void sort() {
		java.util.Arrays.sort(buffer(), 0, size());
	}
}
