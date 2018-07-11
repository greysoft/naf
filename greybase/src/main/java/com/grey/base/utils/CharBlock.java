/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class CharBlock
	extends ArrayRef<char[]>
	implements CharSequence
{
	private static final char[] EMPTYBUF = new char[0];
	private static final Allocator<char[]> ALLOCATOR = (n) -> (n == 0 ? EMPTYBUF : new char[n]);

	public CharBlock() {this(16);}
	public CharBlock(int cap) {super(cap, ALLOCATOR);}
	public CharBlock(char[] src)  {this(src, false);}
	public CharBlock(char[] src, boolean copy) {this(src, 0, src == null ? 0 : src.length, copy);}
	public CharBlock(char[] src, int off, int len) {this(src, off, len, false);}
	public CharBlock(CharBlock src) {this(src, false);}
	public CharBlock(CharBlock src, boolean copy) {this(src, 0, getSize(src), copy);}
	public CharBlock(CharSequence src) {this(src, 0, src == null ? 0 : src.length());}

	@Override
	public int length() {return size();}
	@Override
	public char charAt(int idx) {return buffer()[offset(idx)];}
	@Override
	public CharBlock clear() {return (CharBlock)super.clear();}

	@Override
	protected int totalBufferSize(char[] buf) {return (buf == null ? 0 : buf.length);}
	@Override
	protected char[] allocBuffer(int cap) {return ALLOCATOR.allocate(cap);}

	public CharBlock(CharSequence src, int off, int len)
	{
		this(len);
		for (int idx = 0; idx != len; idx++) {
			buffer()[offset(idx)] = src.charAt(off + idx);
		}
		setSize(len);
	}

	public CharBlock(CharBlock src, int off, int len, boolean copy) {
		this(getBuffer(src), getOffset(src, off), len, copy);
	}

	public CharBlock(char[] src, int off, int len, boolean copy) {
		super(src, off, len, copy ? ALLOCATOR : null);
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

	@Override	
	public String toString()
	{
		return new String(buffer(), offset(), size());
	}
}
