/*
 * Copyright 2013 Grey Software (Yusef Badri) - All rights reserved
 */
package com.grey.base.collections;

// A fast and simple class for handling lists of Integer primitives.
// Note that ar_off is always zero for this class, so no need to add it
public final class NumberList
	extends com.grey.base.utils.ArrayRef<int[]>
{
	private final int increment;

	public void clear() {ar_len = 0;}
	public int get(int idx) {return ar_buf[idx];}

	public NumberList() {this(16);}
	public NumberList(int incr) {super(int.class, incr); increment=incr;}

	public void append(int v) {
		if (ar_len == ar_buf.length) {
			int[] newbuf = new int[ar_buf.length+increment];
			System.arraycopy(ar_buf, ar_off, newbuf, 0, ar_len);
			ar_buf = newbuf;
			ar_off = 0;
		}
		ar_buf[ar_len++] = v;
	}

	public void append(com.grey.base.utils.ArrayRef<int[]> lst) {
		int lmt = lst.ar_off + lst.ar_len;
		for (int idx = lst.ar_off; idx != lmt; idx++) {
			append(lst.ar_buf[idx]);
		}
	}

	public void sort() {
		java.util.Arrays.sort(ar_buf, 0, ar_len);
	}
}
