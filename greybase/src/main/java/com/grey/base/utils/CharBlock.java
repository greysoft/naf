/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class CharBlock
	extends ArrayRef<char[]>
	implements CharSequence
{
	@Override
	public int length() {return ar_len;}
	@Override
	public char charAt(int idx) {return ar_buf[ar_off + idx];}
	public char[] toCharArray() {return toCharArray(0, ar_len);}

	public CharBlock() {this(16);}
	public CharBlock(int cap) {super(char.class, cap);}
	public CharBlock(char[] src) {this(src, false);}
	public CharBlock(char[] src, boolean copy) {this(src, 0, src.length, copy);}
	public CharBlock(char[] src, int off, int len, boolean copy) {super(src, off, len, copy);}
	public CharBlock(CharBlock src) {this(src, false);}
	public CharBlock(CharBlock src, boolean copy) {this(src, 0, src.ar_len, copy);}
	public CharBlock(CharBlock src, int off, int len, boolean copy) {super(src, off, len, copy);}
	public CharBlock(CharSequence src) {this(src, 0, src.length());}
	public CharBlock clear() {ar_len = 0; return this;}

	public CharBlock(CharSequence src, int off, int len)
	{
		this(len);
		ar_len = len;
		
		for (int idx = 0; idx != len; idx++)
		{
			ar_buf[idx] = src.charAt(off + idx);
		}
	}

	@Override
	public CharSequence subSequence(int start, int end)
	{
		return subSequence(start, end, true);
	}

	public CharSequence subSequence(int start, int end, boolean copy)
	{
		return new CharBlock(this, start, end - start, copy);
	}

	public char[] toCharArray(int off, int len)
	{
		char[] newbuf = new char[len];
		System.arraycopy(ar_buf, ar_off + off, newbuf, 0, len);
		return newbuf;
	}

	public boolean ensureCapacity(int cap)
	{
		if (ar_buf.length - ar_off >= cap) return false;
		ar_buf = new char[cap];
		ar_off = 0;
		return true;
	}

	@Override	
	public String toString()
	{
		return new String(ar_buf, ar_off, ar_len);
	}
}
