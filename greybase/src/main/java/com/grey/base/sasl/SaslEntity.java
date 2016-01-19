/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

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
	private final com.grey.base.utils.ByteChars base64buf; //temp working buffer preallocated for efficiency

	public SaslEntity init() {return this;}

	public SaslEntity(MECH id, boolean base64)
	{
		mechanism = id;
		base64buf = (base64 ? new com.grey.base.utils.ByteChars() : null);
	}

	// encodes an existing portion of 'buf', starting at base_len
	protected com.grey.base.utils.ByteChars encode(com.grey.base.utils.ByteChars buf, int base_len)
	{
		if (base64buf == null) return buf;
		com.grey.base.crypto.Base64.encodeBytes(buf.ar_buf, buf.ar_off + base_len, buf.ar_len - base_len, 0, base64buf.clear());
		buf.ar_len = base_len;
		buf.append(base64buf);
		return buf;
	}

	protected void encode(com.grey.base.utils.ArrayRef<byte[]> inbuf, com.grey.base.utils.ByteChars outbuf)
	{
		if (base64buf != null) {
			com.grey.base.crypto.Base64.encodeBytes(inbuf.ar_buf, inbuf.ar_off, inbuf.ar_len, 0, outbuf);
		} else {
			outbuf.append(inbuf.ar_buf, inbuf.ar_off, inbuf.ar_len);
		}
	}

	protected com.grey.base.utils.ArrayRef<byte[]> decode(com.grey.base.utils.ArrayRef<byte[]> buf)
	{
		if (base64buf != null && buf.ar_len != 0) {
			com.grey.base.crypto.Base64.decodeBytes(buf.ar_buf, buf.ar_off, buf.ar_len, base64buf.clear());
			buf = base64buf;
		}
		return buf;
	}

	public static void setNonce(com.grey.base.utils.ByteChars buf, CharSequence tagstr, int tagnum, StringBuilder sb)
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