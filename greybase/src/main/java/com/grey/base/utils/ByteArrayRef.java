/*
 * Copyright 2018-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class ByteArrayRef extends ArrayRef<byte[]>
{
	private static final Allocator<byte[]> ALLOCATOR = (n) -> (n == 0 ? ByteOps.EMPTYBUF : new byte[n]);

	public ByteArrayRef() {this(0);}
	public ByteArrayRef(int cap) {super(cap, ALLOCATOR);}
	public ByteArrayRef(byte[] src) {this(src, false);}
	public ByteArrayRef(byte[] src, boolean copy) {this(src, 0, arraySize(src), copy);}
	public ByteArrayRef(byte[] src, int off, int len) {this(src, off, len, false);}
	public ByteArrayRef(ByteArrayRef src) {this(src, false);}
	public ByteArrayRef(ByteArrayRef src, boolean copy) {this(src, 0, getSize(src), copy);}

	public final int byteAt(int idx) {return buffer()[offset(idx)] & 0xff;} //return Int to handle sign-extension
	public final void setByte(int idx, int val) {buffer()[offset(idx)] = (byte)val;}
	public boolean equalsBytes(byte[] arr) {return equalsBytes(arr, 0, totalBufferSize(arr));}

	public int count(int val) {return count(0, val);}
	public int count(int off, int val) {return ByteOps.count(val, buffer(), offset(off), size()-off);}
	public int count(byte[] seq) {return count(0, seq, 0, totalBufferSize(seq));}
	public int count(int off, byte[] seq, int soff, int slen) {return ByteOps.count(buffer(), offset(off), size()-off, seq, soff, slen);}

	@Override
	protected int totalBufferSize(byte[] buf) {return arraySize(buf);}
	@Override
	protected byte[] allocBuffer(int cap) {return ALLOCATOR.allocate(cap);}

	public ByteArrayRef(ByteArrayRef src, int off, int len, boolean copy) {
		this(getBuffer(src), getOffset(src, off), len, copy);
	}

	public ByteArrayRef(byte[] src, int off, int len, boolean copy) {
		super(src, off, len, copy ? ALLOCATOR : null);
	}

	// Shift-Add-XOR hash - see http://eternallyconfuzzled.com/tuts/algorithms/jsw_tut_hashing.aspx
	@Override
	public int hashCode()
	{
		int hash = 0;
		int off = offset();
		final byte[] buf = buffer();
		final int lmt = off + size();

		while (off != lmt) {
			hash ^= ((hash << 5) + (hash >>> 2) + buf[off++]);
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == this) return true;
		if (!canEqual(obj)) return false;
		ByteArrayRef ref2 = (ByteArrayRef)obj;
		if (!ref2.canEqual(this) || ref2.size() != size()) return false;

		// This shaves 40% off the time to count up to arr.len and loop on bc2.arr.buf[bc2.arr.off + idx] != arr.buf[arr.off + idx]
		int off = offset();
		int off2 = ref2.offset();
		final byte[] buf = buffer();
		final byte[] buf2 = ref2.buffer();
		final int lmt = off + size();

		while (off != lmt) {
			if (buf2[off2++] != buf[off++]) return false;
		}
		return true;
	}

	@Override
	public boolean canEqual(Object obj)
	{
		return (obj instanceof ByteArrayRef);
	}

	public boolean equalsBytes(byte[] barr, int boff, int blen)
	{
		if (blen != size()) return false;
		final byte[] buf = buffer();
		int off = offset();
		final int lmt = off + size();

		while (off != lmt) {
			if (barr[boff++] != buf[off++]) return false;
		}
		return true;
	}

	protected static int arraySize(byte[] buf) {
		return (buf == null ? 0 : buf.length);
	}

	// This is predicated on the byte array representing 8-bit chars, which is the case for most text-based network protocols
	public CharSequence toString(StringBuilder sb, int off, int len) {
		if (off + len > size())
			throw new ArrayIndexOutOfBoundsException(off+"/"+len+" in "+this);
		if (sb == null)
			sb = new StringBuilder(size());
		off += offset();
		int lmt = off + len;
		for (int idx = off; idx != lmt; idx++) {
			sb.append((char)buffer()[idx]);
		}
		return sb;
	}

	public CharSequence toString(StringBuilder sb) {
		return toString(sb, 0, size());
	}
}