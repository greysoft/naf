/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/**
 * This class is a byte array handle which lets us handle the byte array as if it were composed of single-byte chars (effectively
 * 8-bit text) - and conversely also lets us treat 8-bit text as a byte array.&nbsp;All <em>without</em> doing any charset conversions.
 * <br>
 * That assumption holds true for a wide range of Internet protocols (HTTP headers, FTP commands, SMTP, etc), so this class lets
 * us operate on the raw byte-stream protocol data as if it was a string, without having to copy or transform it.
 */
public final class ByteChars
	extends ByteArrayRef
	implements CharSequence, Comparable<ByteChars>
{
	private static final int INCR = 16;

	// Note that constructors based on ByteChars params as deliberately omitted, as the Charsequence
	// constructors handle the copy case, while new ByteChars(0).pointAt() will do for no-copy.
	public ByteChars() {this(INCR);}
	public ByteChars(int cap) {super(cap);}
	public ByteChars(byte[] src) {this(src, false);}
	public ByteChars(byte[] src, boolean copy) {this(src, 0, arraySize(src), copy);}
	public ByteChars(byte[] src, int off, int len, boolean copy) {super(src, off, len, copy);}
	public ByteChars(CharSequence src) {this(src, 0, sequenceLength(src));}
	public ByteChars(ByteArrayRef src, boolean copy) {this(getBuffer(src), getOffset(src), getSize(src), copy);}

	public ByteChars populate(byte[] src) {return populate(src, 0, arraySize(src));}
	public ByteChars populate(byte[] src, int off, int len) {return clear().append(src, off, len);}
	public ByteChars populate(CharSequence src) {return populate(src, 0, sequenceLength(src));}
	public ByteChars populate(CharSequence src, int off, int len) {return clear().append(src, off, len);}
	public ByteChars populateBytes(ByteArrayRef src) {return populate(getBuffer(src), getOffset(src), getSize(src));}
	public ByteChars append(byte[] src) {return append(src, 0, arraySize(src));}
	public ByteChars append(char[] src) {return append(src, 0, arraySize(src));}

	public ByteChars set(ByteArrayRef src) {return set(src, 0, getSize(src));}
	public ByteChars set(ByteArrayRef src, int off) {return set(src, off, getSize(src) - off);}
	public ByteChars set(ByteArrayRef src, int off, int len) {return set(getBuffer(src), getOffset(src, off), len);}

	public int indexOf(CharSequence cs) {return indexOf(0, cs);}
	public int indexOf(int bcoff, CharSequence cs) {return indexOf(bcoff, cs, 0, sequenceLength(cs));}
	public int indexOf(int val) {return indexOf(0, val);}
	public int indexOf(byte[] seq) {return indexOf(0, seq, 0, arraySize(seq));}
	public int lastIndexOf(int val) {return lastIndexOf(size() - 1, val);}
	public boolean startsWith(CharSequence cs) {return startsWith(cs, 0, sequenceLength(cs));}
	public boolean endsWith(CharSequence cs) {return endsWith(cs, 0, sequenceLength(cs));}

	public boolean equalsChars(char[] arr) {return equalsChars(arr, 0, arraySize(arr));}
	public char[] toCharArray() {return toCharArray(0, size(), null);}

	public long parseDecimal() {return parseDecimal(0, size());}
	public long parseDecimal(int off, int len) {return parseNumber(off, len, 10);}
	public long parseHexadecimal() {return parseHexadecimal(0, size());}
	public long parseHexadecimal(int off, int len) {return parseNumber(off, len, 16);}

	@Override
	public int length() {return size();}
	@Override
	public char charAt(int idx) {return (char)byteAt(idx);}
	
	// Overridden merely to facilitate a fluent ByteChars API.
	// The set() methods also originated as ByteChars-only methods that were called pointAt().
	@Override
	public ByteChars clear() {return (ByteChars)super.clear();}
	@Override
	public ByteChars set(byte[] src, int off, int len) {return (ByteChars)super.set(src, off, len);}
	@Override
	public ByteChars set(byte[] src) {return set(src, 0, arraySize(src));}

	public ByteChars(CharSequence src, int off, int len)
	{
		this(len);
		append(src, off, len);
	}

	public ByteChars append(byte[] barr, int off, int len)
	{
		if (len == 0) return this;
		ensureSpareCapacity(len);
		copyIn(barr, off, size(), len);
		incrementSize(len);
		return this;
	}

	public ByteChars append(char[] carr, int off, int len)
	{
		if (len == 0) return this;
		ensureSpareCapacity(len);
		final int lmt = off + len;
		int boff = size();
		for (int idx = off; idx != lmt; idx++) {
			setByte(boff++, carr[idx]);
		}
		incrementSize(len);
		return this;
	}

	public ByteChars append(int bval)
	{
		if (spareCapacity() == 0) ensureSpareCapacity(INCR);
		setByte(size(), bval);
		incrementSize(1);
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
		Class<?> clss = cs.getClass();

		if (clss == ByteChars.class) {
			ByteChars bc = (ByteChars)cs;
			return append(bc.buffer(), bc.offset(csoff), cslen);
		}
		ensureSpareCapacity(cslen);
		final byte[] buf = buffer();
		int bcoff = limit();
		incrementSize(cslen);

		if (clss == String.class) {
			// tried the deprecated str.getBytes() - simply doesn't work, even for simple ASCII text
			final String str = (String)cs;
			for (int idx = 0; idx != cslen; idx++) {
				buf[bcoff++] = (byte)str.charAt(csoff++);
			}
		} else if (clss == StringBuilder.class) {
			final StringBuilder str = (StringBuilder)cs;
			for (int idx = 0; idx != cslen; idx++) {
				buf[bcoff++] = (byte)str.charAt(csoff++);
			}
		} else {
			for (int idx = 0; idx != cslen; idx++) {
				buf[bcoff++] = (byte)cs.charAt(csoff++);
			}
		}
		return this;
	}

	public int indexOf(int off, int val)
	{
		off = ByteOps.indexOf(buffer(), offset(off), size() - off, val);
		if (off == -1) return -1;
		return off - offset();
	}

	public int indexOf(int off, byte[] seq, int soff, int slen)
	{
		off = ByteOps.indexOf(buffer(), offset(off), size() - off, seq, soff, slen);
		if (off == -1) return -1;
		return off - offset();
	}

	public int lastIndexOf(int off, int val)
	{
		final byte[] buf = buffer();
		final int lmt = offset() - 1;
		off += offset();

		while (off != lmt) {
			if (buf[off] == val) return off - offset();
			off--;
		}
		return -1;
	}

	public int indexOf(int bcoff, CharSequence cs, int csoff, int cslen)
	{
		if (cslen == 0) return -1; //need to check this before we index cs
		final byte[] buf = buffer();
		final int bc0 = offset();
		final int bclen = size();
		final int cslimit = csoff + cslen;
		final byte char1 = (byte)cs.charAt(csoff);

		while (bcoff != bclen)
		{
			bcoff = indexOf(bcoff, char1);
			if (bcoff == -1 || bcoff > bclen - cslen) break;
			int bcoff_phys = bc0 + bcoff + 1;
			int idx = csoff + 1;

			while (idx != cslimit)
			{
				if ((buf[bcoff_phys++] & 0xff) != cs.charAt(idx)) break;
				idx++;
			}
			if (idx == cslimit) return bcoff;
			bcoff++;
		}
		return -1;
	}

	public boolean startsWith(CharSequence cs, int csoff, int cslen)
	{
		if (cs == null || cslen > size()) return false;
		final byte[] buf = buffer();
		int off = offset();
		for (int loop = 0; loop != cslen; loop++) {
			if ((buf[off++] & 0xff) != cs.charAt(csoff++)) return false;
		}
		return true;
	}

	public boolean endsWith(CharSequence cs, int csoff, int cslen)
	{
		if (cs == null || cslen > size()) return false;
		final byte[] buf = buffer();
		int off = limit() - cslen;
		for (int loop = 0; loop != cslen; loop++) {
			if ((buf[off++] & 0xff) != cs.charAt(csoff++)) return false;
		}
		return true;
	}

	// Obviously this will truncate char values larger than a byte
	public boolean equalsChars(char[] carr, int coff, int clen)
	{
		if (clen != size()) return false;
		final byte[] buf = buffer();
		int off = offset();
		final int lmt = off + size();

		while (off != lmt) {
			if (carr[coff++] != buf[off++]) return false;
		}
		return true;
	}

	public boolean equalsIgnoreCase(CharSequence str)
	{
		if (str == null || str.length() != size()) return false;
		final byte[] buf = buffer();
		int off = offset();
		final int lmt = off + size();
		int coff = 0;

		while (off != lmt) {
			if (Character.toUpperCase(buf[off++]&0xff) != Character.toUpperCase(str.charAt(coff++))) return false;
		}
		return true;
	}

	@Override
	public int compareTo(ByteChars bc2)
	{
		if (bc2 == this) return 0;
		int off = offset();
		int off2 = bc2.offset();
		final byte[] buf = buffer();
		final byte[] buf2 = bc2.buffer();
		final int lmt = off + Math.min(size(), bc2.size());

		while (off != lmt) {
			byte b1 = buf[off++];
			byte b2 = buf2[off2++];
			if (b1 != b2) return b1 - b2;
		}
		return size() - bc2.size();
	}

	@Override
	public String toString()
	{
		return toString(0, size());
	}

	public String toString(int off, int len)
	{
		return subSequence(off, off+len).toString();
	}

	@Override
	public CharSequence subSequence(int start, int end)
	{
		return new StringBuilder().append(this, start, end);
	}

	public char[] toCharArray(int off, int len, char[] chbuf)
	{
		if (chbuf == null || chbuf.length < len) chbuf = new char[len];
		off += offset();
		final int lmt = off + len;
		final byte[] buf = buffer();

		for (int idx = off, offch = 0; idx != lmt; idx++, offch++) {
			chbuf[offch] = (char)buf[idx];
		}
		return chbuf;
	}

	public ByteChars toLowerCase()
	{
		final byte[] buf = buffer();
		final int lmt = limit();
		for (int off = offset(); off != lmt; off++) {
			buf[off] = (byte)Character.toLowerCase(buf[off] & 0xff);
		}
		return this;
	}

	public ByteChars toUpperCase()
	{
		final byte[] buf = buffer();
		final int lmt = limit();
		for (int off = offset(); off != lmt; off++) {
			buf[off] = (byte)Character.toUpperCase(buf[off] & 0xff);
		}
		return this;
	}

	// We could simply call StringOps.parseNumber() but it's more efficient to loop round our own array, than let it loop on
	// the virtual CharSequence.charAt()
	private long parseNumber(int off, int len, int radix)
	{
		if (len == 0) return 0;
		final byte[] buf = buffer();
		int base = offset(off); //this is aligned to detect leading minus sign - will subtract 1 if not found
		long numval = 0;
		long sign = 1;
		long power = 1;
		long digit;

		if (buf[base] == '-') {
			len--;
			sign = -1;
		} else if (buf[base] == '+') {
			len--;
		} else {
			base--;
		}

		for (int idx = base + len; idx != base; idx--) {
			if ((digit = Character.digit(buf[idx], radix)) == -1) {
				throw new NumberFormatException((char)buf[idx]+"@"+idx+" in "+off+":"+len+" - "+subSequence(off, off+len));
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
	 * @param target Specifies which term to return, where the first term is 0, the second is 1, etc
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
			off++; // advance past delimiter
		}
		int limit = (to_end ? -1 : indexOf(off, dlm));

		if (limit == -1)
		{
			// trailing blank term might as well not exist, eg. the strings "+" and "abc+" both contain one term (terminated by dlm=+)
			if ((limit = length()) == off) return null;
		}
		if (ptr == null) ptr = new ByteChars(-1); // lightweight ByteChars object, without own storage
		ptr.set(this, off, limit - off);
		return ptr;
	}

	public ByteChars digest(java.security.MessageDigest digestproc)
	{
		digestproc.reset();
		if (size() == 0) {
			digestproc.update(ByteOps.EMPTYBUF, 0, 0);
		} else {
			digestproc.update(buffer(), offset(), size());
		}
		byte[] digest = digestproc.digest();
		return new ByteChars(digest);
	}

	public static ByteChars convertCharSequence(CharSequence src, ByteChars buf)
	{
		if (src == null || src.getClass() == ByteChars.class) return (ByteChars)src;
		if (buf == null) buf = new ByteChars(src.length());
		return buf.populate(src);
	}

	public static java.util.ArrayList<ByteChars> parseTerms(byte[] barr, int off, int len, byte dlm,
			java.util.ArrayList<ByteChars> terms, com.grey.base.collections.ObjectWell<ByteChars> store)
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
					term.populate(barr, arg0, termlen);
					terms.add(term);
				}
				arg0 = idx + 1;
			}
		}
		return terms;
	}

	public static java.util.ArrayList<ByteChars> parseTerms(CharSequence str, int off, int len, byte dlm,
			java.util.ArrayList<ByteChars> terms, com.grey.base.collections.ObjectWell<ByteChars> store, ByteChars bctmp)
	{
		if (bctmp == null) bctmp = new ByteChars(len);
		bctmp.populate(str, off, len);
		return ByteChars.parseTerms(bctmp.buffer(), bctmp.offset(), bctmp.size(), dlm, terms, store);
	}

	private static int arraySize(char[] buf) {
		return (buf == null ? 0 : buf.length);
	}

	private static int sequenceLength(CharSequence cs) {
		return (cs == null ? 0 : cs.length());
	}
}
