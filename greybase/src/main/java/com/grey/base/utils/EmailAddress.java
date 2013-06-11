/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
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
		full = new ByteChars();
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

	public EmailAddress set(CharSequence addr)
	{
		full.set(addr);
		domain.ar_len = 0;
		mailbox.ar_len = 0;
		return this;
	}

	public EmailAddress decompose()
	{
		if (mailbox.ar_len != 0) return this; //already broken down
		int off = full.lastIndexOf(DLM);

		if (off == -1) {
			mailbox.pointAt(full);
			domain.ar_len = 0;
		} else {
			mailbox.pointAt(full, 0, off);
			domain.pointAt(full, off + 1);
		}
		return this;
	}

	public EmailAddress stripDomain()
	{
		full.ar_len = mailbox.ar_len;
		domain.ar_len = 0;
		return this;
	}

	// This is a bit too oriented towards Mailismus and SMTP
	// We also convert to lower-case, for the sake of the Mailismus' DNS-Resolver.
	public void parse(ArrayRef<byte[]> data)
	{
		reset();
		final byte[] dbuf = data.ar_buf;
		int doff = data.ar_off;
		int len = data.ar_len;
		final int lmt = doff + len;

		int pos = ByteOps.indexOf(dbuf, doff, len, (byte)'<');
		if (pos != -1) {
			// strip enclosing <> brackets - allow final one to be absent
			doff = pos + 1;
			int idx = doff;
			while (idx != lmt && dbuf[idx] > 32 && dbuf[idx] != '>') {
				dbuf[idx] = (byte)Character.toLowerCase(dbuf[idx]);
				idx++;
			}
			len = idx - doff;
		} else {
			// seek to start of address
			while ((doff != lmt) && (dbuf[doff] == ' ' || dbuf[doff] == '\t')) doff++;

			// Seek to end of address.
			// Convert to lower-case at same time, as DNS Resolver's cache is case sensitive
			int idx = doff;
			while (idx != lmt && dbuf[idx] > 32) {
				dbuf[idx] = (byte)Character.toLowerCase(dbuf[idx]);
				idx++;
			}
			len = idx - doff;

			if (len != 0) {
				// Strip surrounding brackets (RFC-2821 section 4.1.3 specifies square brackets for literal dotted IPs)
				byte lastchar = dbuf[doff + len - 1];
				if (dbuf[doff] == '[' && lastchar == ']') {
					doff++;
					len -= 2;
				} else {
					if (lastchar == '.') len--;  // strip one trailing dot, in case address had an odd absolute FQDN format
				}
			}
		}

		// copy data to address buffer
		if (len == 0) {
			full.ar_len = 0;
			return;
		}
		full.ensureCapacity(len);
		full.ar_len = len;
		System.arraycopy(dbuf, doff, full.ar_buf, full.ar_off, len);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == this) return true;
		if (obj == null || obj.getClass() != EmailAddress.class) return false;
		EmailAddress addr2 = (EmailAddress)obj;
		if (addr2.mailbox.ar_len != mailbox.ar_len) return false;
		return full.equals(addr2.full);
	}

	@Override
	public int hashCode()
	{
		return full.hashCode();
	}

	@Override
	public String toString()
	{
		return full.toString();
	}
}