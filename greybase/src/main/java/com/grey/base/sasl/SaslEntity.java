/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteChars;

// Base SASL standard is RFC-2222 (Oct 1997), latest is RFC-4422 (Jun 2006)
// Official list of SASL mechanisms: www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xml
// There is also the non-standard LOGIN mechanism, which has been obsoleted by PLAIN:
// - http://tools.ietf.org/id/draft-murchison-sasl-login-00.txt
// - http://stackoverflow.com/questions/8974283/gmail-auth-login-smtp-authentication
public abstract class SaslEntity
{
	public static enum MECH {PLAIN, CRAM_MD5, EXTERNAL}
	public static final String MECHNAME_PLAIN = MECH.PLAIN.toString();
	public static final String MECHNAME_CMD5 = MECH.CRAM_MD5.toString().replace('_', '-');
	public static final String MECHNAME_EXTERNAL = MECH.EXTERNAL.toString();

	public final MECH mechanism;
	private final ByteChars base64buf; //temp working buffer preallocated for efficiency

	public SaslEntity init() {return this;}

	public SaslEntity(MECH id, boolean base64)
	{
		mechanism = id;
		base64buf = (base64 ? new ByteChars() : null);
	}

	// encodes an existing portion of 'buf', starting at base_len
	protected ByteChars encode(ByteChars buf, int base_len)
	{
		if (base64buf == null) return buf;
		com.grey.base.crypto.Base64.encodeBytes(buf.buffer(), buf.offset(base_len), buf.size() - base_len, 0, base64buf.clear());
		buf.setSize(base_len);
		buf.append(base64buf);
		return buf;
	}

	protected void encode(ByteArrayRef inbuf, ByteChars outbuf)
	{
		if (base64buf != null) {
			com.grey.base.crypto.Base64.encodeBytes(inbuf.buffer(), inbuf.offset(), inbuf.size(), 0, outbuf);
		} else {
			outbuf.append(inbuf.buffer(), inbuf.offset(), inbuf.size());
		}
	}

	protected ByteArrayRef decode(ByteArrayRef buf)
	{
		if (base64buf != null && buf.size() != 0) {
			com.grey.base.crypto.Base64.decodeBytes(buf.buffer(), buf.offset(), buf.size(), base64buf.clear());
			buf = base64buf;
		}
		return buf;
	}

	public static void setNonce(ByteChars buf, CharSequence tagstr, int tagnum, StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder();
		java.lang.management.RuntimeMXBean rt = java.lang.management.ManagementFactory.getRuntimeMXBean();
		buf.append("<T").append(Thread.currentThread().getId(), sb);
		if (tagstr != null) buf.append('_').append(tagstr);
		if (tagnum != 0) buf.append('_').append(tagnum, sb);
		buf.append("_").append(System.nanoTime() & 0xf4240, sb); //hex value for million, isolates final 6 digits
		buf.append("_").append(System.currentTimeMillis(), sb);
		buf.append("_").append(rt.getName()).append(">");
	}
}