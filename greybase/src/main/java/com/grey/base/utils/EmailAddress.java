/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class EmailAddress
{
	public static final String DLMSTR = "@";
	public static final byte DLM = (byte)DLMSTR.charAt(0);

	public final ByteChars domain = new ByteChars(-1);  // lightweight object without own storage
	public final ByteChars mailbox = new ByteChars(-1);  // lightweight object without own storage
	public final ByteChars full;

	public EmailAddress()
	{
		full = new ByteChars(25);
	}

	public EmailAddress(CharSequence addr)
	{
		full = new ByteChars(addr);
	}

	public EmailAddress reset()
	{
		full.ar_len = 0;
		domain.ar_len = 0;
		mailbox.ar_len = 0;
		return this;
	}

	public void set(CharSequence addr)
	{
		full.set(addr);
		domain.ar_len = 0;
		mailbox.ar_len = 0;
	}

	public void decompose()
	{
		if (mailbox.ar_len != 0) return; //already broken down
		int off = full.lastIndexOf(DLM);

		if (off == -1) {
			mailbox.pointAt(full);
			domain.ar_len = 0;
		} else {
			mailbox.pointAt(full, 0, off);
			domain.pointAt(full, off + 1);
		}
	}

	public void parse(ArrayRef<byte[]> data)
	{
		reset();
		int idx = data.ar_off;
		int limit = idx + data.ar_len;
		byte[] dbuf = data.ar_buf;

		// seek to start of address
		while ((idx != limit) && (dbuf[idx] == ' ' || dbuf[idx] == '\t')) idx++;
		int doff = idx;

		// Seek to end of address.
		// Convert to lower-case at same time, as DNS Resolver's cache is case sensitive
		while ((idx != limit) && dbuf[idx] > 32) {
			dbuf[idx] = (byte)Character.toLowerCase(dbuf[idx]);
			idx++;
		}
		int len = idx - doff;

		if (len != 0) {
			// Strip surrounding brackets (RFC-2821 section 4.1.3 specifies square brackets for literal dotted IPs)
			byte lastchar = dbuf[doff + len - 1];

			if ((dbuf[doff] == '<' && lastchar == '>')
					|| (dbuf[doff] == '[' && lastchar == ']')) {
				doff++;
				len -= 2;
				if (len != 0) lastchar = dbuf[doff + len - 1];
			}
			if (lastchar == '.') len--;  // strip one trailing dot, in case address had an odd absolute FQDN format
		}

		// copy data to address buffer
		if ((full.ar_len = len) == 0) return;
		full.ensureCapacity(len + 10);  // margin of 10 to avoid growth increments smaller than that
		System.arraycopy(dbuf, doff, full.ar_buf, full.ar_off, len);
	}

	@Override
	public String toString()
	{
		return full.toString();
	}
}