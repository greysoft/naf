/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public final class EmailAddress
	implements Comparable<EmailAddress>
{
	public static final char DLM_DOM = '@';
	public static final char DLM_RT = '%'; //preferred delimiter for source-routing (there is no standard)

	public final ByteChars domain = new ByteChars(-1);  // lightweight object without own storage
	public final ByteChars mailbox = new ByteChars(-1);  // lightweight object without own storage
	public final ByteChars full;

	public EmailAddress decompose() {return decompose(false);}

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
		full.clear();
		domain.clear();
		mailbox.clear();
		return this;
	}

	public EmailAddress set(CharSequence addr)
	{
		reset();
		full.populate(addr);
		return this;
	}

	public EmailAddress decompose(boolean bare_is_domain)
	{
		if (mailbox.size() != 0 || domain.size() != 0) return this; //already broken down
		int off = full.lastIndexOf((byte)DLM_DOM);

		if (off == -1) {
			if (bare_is_domain) {
				domain.set(full);
				mailbox.clear();
			} else {
				mailbox.set(full);
				domain.clear();
			}
		} else {
			mailbox.set(full, 0, off);
			domain.set(full, off + 1);
		}
		return this;
	}

	public EmailAddress stripDomain()
	{
		full.setSize(mailbox.size());
		domain.clear();
		return this;
	}

	// This is a bit too oriented towards Mailismus and SMTP
	// We also convert to lower-case, for the sake of the Mailismus' DNS-Resolver.
	public void parse(ByteArrayRef data)
	{
		reset();
		final byte[] dbuf = data.buffer();
		int doff = data.offset();
		int len = data.size();
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
			full.clear();
			return;
		}
		full.ensureCapacity(len);
		full.setSize(len);
		full.copyIn(dbuf, doff, len);
	}

	@Override
	public int compareTo(EmailAddress em2)
	{
		if (em2 == this) return 0;
		if (em2 == null) return 1;
		return full.compareTo(em2.full);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == this) return true;
		if (obj == null || obj.getClass() != EmailAddress.class) return false;
		EmailAddress addr2 = (EmailAddress)obj;
		if (addr2.mailbox.size() != mailbox.size()) return false;
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