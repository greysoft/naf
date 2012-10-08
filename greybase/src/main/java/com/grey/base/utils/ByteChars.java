/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * This class is a byte array handle which lets us handle the byte array as if it were composed of single-byte chars (effectively
 * 8-bit text) - and conversely also lets us treat 8-bit text as a byte array.&nbsp;All <em>without</em> doing any charset conversions.
 * <br/>
 * That assumption holds true for a wide range of Internet protocols (HTTP headers, FTP commands, SMTP, etc), so this class lets
 * us operate on the raw byte-stream protocol data as if it was a string, without having to copy or transform it.
 */
public final class ByteChars
	extends ArrayRef<byte[]>
	implements CharSequence, Comparable<ByteChars>
{
	private static final int INCR = 64;

	@Override
	public int length() {return ar_len;}
	@Override
	public char charAt(int idx) {return (char)byteAt(idx);}

	public int byteAt(int idx) {return ar_buf[ar_off + idx] & 0xff;} //return Int to handle sign-extension
	public void setByte(int idx, byte val) {ar_buf[ar_off + idx] = val;}
	public char[] toCharArray() {return toCharArray(0, ar_len, null);}
	public byte[] toByteArray() {return toByteArray(0, ar_len);}

	public ByteChars clear() {ar_len = 0; return this;}
	public ByteChars copy(ByteChars dst) {return copy(0, ar_len, dst);}
	public ByteChars set(CharSequence str) {return set(str, 0, str == null ? 0 : str.length());}
	public ByteChars set(CharSequence str, int off, int len) {return clear().append(str, off, len);}
	public ByteChars set(byte[] barr) {return set(barr, 0, barr == null ? 0 : barr.length);}
	public ByteChars set(byte[] barr, int off, int len) {return clear().append(barr, off, len);}
	public ByteChars append(byte[] barr) {return append(barr, 0, barr == null ? 0 : barr.length);}
	public ByteChars pointAt(ByteChars src, int off) {return pointAt(src, off, src.ar_len - off);}
	public ByteChars pointAt(ByteChars src) {return pointAt(src, 0, src.ar_len);}
	public ByteChars pointAt(ByteChars src, int off, int len) {return pointAt(src.ar_buf, src.ar_off + off, len);}
	public int indexOf(CharSequence cs) {return indexOf(0, cs, 0, cs.length());}
	public int indexOf(int bcoff, CharSequence cs) {return indexOf(bcoff, cs, 0, cs.length());}
	public int indexOf(byte val) {return indexOf(0, val);}
	public int lastIndexOf(byte val) {return lastIndexOf(ar_len - 1, val);}
	public long parseDecimal() {return parseDecimal(0, ar_len);}
	public long parseDecimal(int off, int len) {return parseNumber(off, len, 10);}
	public long parseHexadecimal() {return parseHexadecimal(0, ar_len);}
	public long parseHexadecimal(int off, int len) {return parseNumber(off, len, 16);}

	public ByteChars() {this(INCR);}
	public ByteChars(int cap) {super(byte.class, cap);}
	public ByteChars(byte[] src) {this(src, false);}
	public ByteChars(byte[] src, boolean copy) {this(src, 0, src.length, copy);}
	public ByteChars(byte[] src, int off, int len, boolean copy) {super(src, off, len, copy);}
	public ByteChars(ByteChars src) {this(src, false);}
	public ByteChars(ByteChars src, boolean copy) {this(src, 0, src.ar_len, copy);}
	public ByteChars(ByteChars src, int off, int len, boolean copy) {super(src, off, len, copy);}
	public ByteChars(CharSequence src) {this(src, 0, src==null?0:src.length());}

	public ByteChars(CharSequence src, int off, int len)
	{
		this(len);
		append(src, off, len);
	}

	public ByteChars pointAt(byte[] buf, int off, int len)
	{
		ar_buf = buf;
		ar_off = off;
		ar_len = len;
		return this;
	}

	public ByteChars copy(int off, int len, ByteChars dst)
	{
		if (dst == null) return new ByteChars(this, off, len, true);
		dst.ensureCapacity(len);
		System.arraycopy(ar_buf, ar_off + off, dst.ar_buf, dst.ar_off, len);
		dst.ar_len = len;
		return dst;
	}

	public ByteChars append(byte[] barr, int boff, int blen)
	{
		if (blen == 0) return this;
		if (ar_len + blen > ar_buf.length - ar_off) grow(ar_len + blen);
		System.arraycopy(barr, boff, ar_buf, ar_off + ar_len, blen);
		ar_len += blen;
		return this;
	}

	public ByteChars append(byte bval)
	{
		int slot = ar_off + ar_len;
		if (slot == ar_buf.length)  grow(ar_len + 1);
		ar_buf[slot] = bval;
		ar_len++;
		return this;
	}

	public ByteChars append(long numval, StringBuilder strbuf)
	{
		strbuf.setLength(0);
		strbuf.append(numval);
		return append(strbuf);
	}

	public ByteChars append(CharSequence str)
	{
		if (str == null) return this;
		return append(str, 0, str.length());
	}

	public ByteChars append(CharSequence cs, int csoff, int cslen)
	{
		if (cslen == 0) return this;
		if (cs instanceof ByteChars)
		{
			ByteChars bc = (ByteChars)cs;
			return append(bc.ar_buf, bc.ar_off + csoff, cslen);
		}
		int bcoff = ar_len;
		if (bcoff + cslen > ar_buf.length - ar_off) grow(bcoff + cslen);
		ar_len = bcoff + cslen;  // don't advance ar_len until after grow()
		byte[] buf = ar_buf;
		bcoff += ar_off;

		if (cs instanceof String)
		{
			// tried the deprecated str.getBytes() - simply doesn't work, even for simple ASCII text
			String str = (String)cs;
			for (int idx = 0; idx != cslen; idx++)
			{
				buf[bcoff++] = (byte)str.charAt(csoff++);
			}
		}
		else if (cs instanceof StringBuilder)
		{
			StringBuilder str = (StringBuilder)cs;
			for (int idx = 0; idx != cslen; idx++)
			{
				buf[bcoff++] = (byte)str.charAt(csoff++);
			}
		}
		else
		{
			for (int idx = 0; idx != cslen; idx++)
			{
				buf[bcoff++] = (byte)cs.charAt(csoff++);
			}	
		}
		return this;
	}

	private void grow(int mincap)
	{
		byte[] newbuf = new byte[mincap + INCR];
		System.arraycopy(ar_buf, ar_off, newbuf, 0, ar_len);
		ar_buf = newbuf;
		ar_off = 0;
	}

	public boolean ensureCapacity(int cap)
	{
		if (ar_buf.length - ar_off >= cap) return false;
		ar_buf = new byte[cap];
		ar_off = 0;
		return true;
	}

	public int indexOf(int off, byte val)
	{
		int limit = ar_off + ar_len;
		off = ar_off + off - 1;
		byte[] buf = ar_buf;

		while (++off != limit)
		{
			if (buf[off] == val) return off - ar_off;
		}
		return -1;
	}

	public int lastIndexOf(int off, byte val)
	{
		off += ar_off + 1;
		int limit = ar_off - 1;
		byte[] buf = ar_buf;

		while (--off != limit)
		{
			if (buf[off] == val) return off - ar_off;
		}
		return -1;
	}

	public int indexOf(int bcoff, CharSequence cs, int csoff, int cslen)
	{
		byte[] buf = ar_buf;
		int bc0 = ar_off;
		int bclen = ar_len;
		int cslimit = csoff + cslen;
		byte char1 = (byte)cs.charAt(csoff);

		while (bcoff != bclen)
		{
			bcoff = indexOf(bcoff, char1);
			if (bcoff == -1 || bcoff > bclen - cslen) break;
			int bcoff_phys = bc0 + bcoff + 1;
			int idx = csoff + 1;

			while (idx != cslimit)
			{
				if (buf[bcoff_phys++] != cs.charAt(idx)) break;
				idx++;
			}
			if (idx == cslimit) return bcoff;
			bcoff++;
		}
		return -1;
	}

	private long parseNumber(int off, int len, int radix)
	{
		long numval = 0;
		long sign = 1;
		int base = ar_off + off;  // this is aligned to detect leading minus sign - will subtract 1 if not found
		byte[] buf = ar_buf;
		long power = 1;
		long digit;

		if (buf[base] == '-' && radix == 10)
		{
			// only really makes sense for decimal
			len--;
			sign = -1;
		}
		else
		{
			base--;
		}

		for (int idx = base + len; idx != base; idx--)
		{
			if ((digit = Character.digit(buf[idx], radix)) == -1)
			{
				throw new NumberFormatException((char)buf[idx]+"@"+idx+" in "+off+"+"+len+" - "+subSequence(off, off+len));
			}
			numval += (digit * power);
			power *= radix;
		}
		return numval * sign;
	}

	/**
	 * Breaks up the current buffer into a sequence of terms, and returns the specified term.
	 * The dlm parameter breaks up the current buffer into a sequence of terms, where the first term has id=0, the second id=1, etc
	 * @param dlm Delimits the terms, where the first term has id=0, the second id=1, etc
	 * @param off Start processing buffer at this offset - positions 0 to off-1 are ignored.
	 * @param target Specifies which term to return, where the first term is 0, the second  is 1, etc
	 * @param to_end If True, the rest of this buffer from the specified term onwards is returned, unbroken by 'dlm'
	 * @param ptr If null, this method allocates a new lightweight ByteChars to use as the return value, but if an existing ByteChars
	 * object is passed in here, it is used to point at the returned term, without having to allocate a new Object.
	 * @return Another ByteChars ref pointing at the specified term
	 */
	public ByteChars extractTerm(byte dlm, int off, int target, boolean to_end, ByteChars ptr)
	{
		for (int loop = 0; loop != target; loop++)
		{
			if ((off = indexOf(off, dlm)) == -1) return null;
			off++;  // advance past delimiter
		}
		int limit = (to_end ? -1 : indexOf(off, dlm));

		if (limit == -1)
		{
			// trailing blank term might as well not exist, eg. the strings "@" and "abc@" both contain one term (terminated by dlm=@)
			if ((limit = length()) == off) return null;
		}
		if (ptr == null) ptr = new ByteChars(-1);  // lightweight ByteChars object, without own storage
		ptr.pointAt(this, off, limit - off);
		return ptr;
	}

	// Shift-Add-XOR hash - see http://eternallyconfuzzled.com/tuts/algorithms/jsw_tut_hashing.aspx
	@Override
	public int hashCode()
	{
		int hash = 0;
		int off = ar_off;
		final byte[] buf = ar_buf;
		final int lmt = off + ar_len;

		while (off != lmt) {
			hash ^= ((hash << 5) + (hash >>> 2) + buf[off++]);
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == this) return true;
		if (!(obj instanceof ByteChars)) return false;
		ByteChars bc2 = (ByteChars)obj;
		if (bc2.ar_len != ar_len) return false;

		// This shaves 40% off the time to count up to arr.len and loop on bc2.arr.buf[bc2.arr.off + idx] != arr.buf[arr.off + idx]
		int off = ar_off;
		int off2 = bc2.ar_off;
		final byte[] buf = ar_buf;
		final byte[] buf2 = bc2.ar_buf;
		final int lmt = off + ar_len;

		while (off != lmt) {
			if (buf2[off2++] != buf[off++]) return false;
		}
		return true;
	}

	@Override
	public int compareTo(ByteChars bc2)
	{
		if (bc2 == this) return 0;
		int off = ar_off;
		int off2 = bc2.ar_off;
		final byte[] buf = ar_buf;
		final byte[] buf2 = bc2.ar_buf;
		final int lmt = off + Math.min(ar_len, bc2.ar_len);

		while (off != lmt) {
			byte b1 = buf[off++];
			byte b2 = buf2[off2++];
			if (b1 != b2) return b1 - b2;
		}
		return ar_len - bc2.ar_len;
	}

	@Override
	public String toString()
	{
		return toString(null);
	}

	public String toString(StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder();
		sb.append(this);
		return sb.toString();
	}

	@Override
	public CharSequence subSequence(int start, int end)
	{
		return new ByteChars(this, start, end - start, true);
	}

	public char[] toCharArray(int off, int len, char[] chbuf)
	{
		if (chbuf == null || chbuf.length < len) chbuf = new char[len];
		off += ar_off;
		final int lmt = off + len;
		final byte[] buf = ar_buf;
		int offch = 0;

		for (int idx = off; idx != lmt; idx++)
		{
			chbuf[offch++] = (char)buf[idx];
		}
		return chbuf;
	}

	// Don't bother providing an output byte[] arg, because ByteChars already offers full access to its own backing byte[] array,
	// and this method just supports cleanly generating an independent copy of the exact size requested.
	public byte[] toByteArray(int off, int len)
	{
		byte[] newbuf = new byte[len];
		System.arraycopy(ar_buf, ar_off + off, newbuf, 0, len);
		return newbuf;
	}

	public static ByteChars convertCharSequence(CharSequence src, ByteChars buf)
	{
		if (src == null || src instanceof ByteChars) return (ByteChars)src;
		if (buf == null) buf = new ByteChars(src.length());
		return buf.set(src);
	}

	public static java.util.ArrayList<ByteChars> parseTerms(byte[] barr, int off, int len, byte dlm,
			java.util.ArrayList<ByteChars> terms, ObjectWell<ByteChars> store)
	{
		if (terms == null) terms = new java.util.ArrayList<ByteChars>();
		final int lmt = off + len;
		int arg0 = off;
		for (int idx = off; idx <= lmt; idx++)
		{
				if (idx == lmt || barr[idx] == dlm) {
					int termlen = idx - arg0;
					if (termlen != 0) {
						ByteChars term = (store == null ? new ByteChars(termlen) : store.extract());
						term.set(barr, arg0, termlen);
						terms.add(term);
					}
					arg0 = idx + 1;
				}
		}
		return terms;
	}

    public static java.util.ArrayList<ByteChars> parseTerms(CharSequence str, int off, int len, byte dlm,
			java.util.ArrayList<ByteChars> terms, ObjectWell<ByteChars> store, ByteChars bctmp)
    {
    	if (bctmp == null) bctmp = new ByteChars(len);
    	bctmp.set(str, off, len);
    	return ByteChars.parseTerms(bctmp.ar_buf, bctmp.ar_off, bctmp.ar_len, dlm, terms, store);
    }
}
