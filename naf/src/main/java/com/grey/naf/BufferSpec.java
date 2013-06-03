/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;

public final class BufferSpec
{
	//defaults to false as Direct buffers don't have backing array (on Windows JDK6 at least)
	public static final boolean directniobufs = SysProps.get("greynaf.nio.directbufs", false);

	public final int rcvbufsiz;  //receive-buffer size
	public final int xmtbufsiz;  //transmit buffer size
	public final boolean directbufs; //false by default, because direct buffers don't have a backing array in standard JDK implementation
	public final com.grey.base.utils.ObjectWell<java.nio.ByteBuffer> xmtpool;
	private final java.nio.charset.CharsetEncoder chenc;

	public String charsetName() {return (chenc == null ? "n/a" : chenc.charset().displayName());}

	public BufferSpec(int rcvsiz, int xmtsiz) throws com.grey.base.ConfigException
	{
		this(null, null, rcvsiz, xmtsiz);
	}

	public BufferSpec(int rcvsiz, int xmtsiz, boolean direct) throws com.grey.base.ConfigException
	{
		this(null, null, rcvsiz, xmtsiz, direct);
	}

	public BufferSpec(com.grey.base.config.XmlConfig cfg, String xpath, int rcvsiz, int xmtsiz) throws com.grey.base.ConfigException
	{
		this(cfg, xpath, rcvsiz, xmtsiz, directniobufs);
	}

	public BufferSpec(com.grey.base.config.XmlConfig cfg, String xpath, int rcvsiz, int xmtsiz, boolean direct) throws com.grey.base.ConfigException
	{
		String charset = null;
		if (cfg != null) {
			xpath = (xpath == null ? "" : xpath+"/");
			rcvbufsiz = (int)cfg.getSize(xpath+"@recvsize", rcvsiz);
			xmtbufsiz = (int)cfg.getSize(xpath+"@xmitsize", xmtsiz);
			directbufs = cfg.getBool(xpath+"@direct", direct);
			charset = cfg.getValue(xpath+"@charset", false, null);
		} else {
			rcvbufsiz = rcvsiz;
			xmtbufsiz = xmtsiz;
			directbufs = direct;
		}

		// ISO-8859-1 should be ok, but best to omit, so we can default to direct byte-copy
		if (charset != null) {
			java.nio.charset.Charset chset = java.nio.charset.Charset.forName(charset);
			chenc = chset.newEncoder();
		} else {
			chenc = null;
		}

		if (xmtbufsiz == 0) {
			xmtpool = null;
		} else {
			com.grey.base.utils.NIOBuffers.BufferFactory factory = new com.grey.base.utils.NIOBuffers.BufferFactory(xmtbufsiz, directbufs);
			xmtpool = new com.grey.base.utils.ObjectWell<java.nio.ByteBuffer>(java.nio.ByteBuffer.class, factory,
								"BufferSpecPool_"+xmtbufsiz+"/"+directbufs, 0, 0, 1);
		}
	}

	public java.nio.ByteBuffer encode(CharSequence content, java.nio.ByteBuffer bybuf)
	{
		if (content instanceof com.grey.base.utils.ByteChars) {
			com.grey.base.utils.ByteChars bc = com.grey.base.utils.ByteChars.class.cast(content);
			return com.grey.base.utils.NIOBuffers.encode(bc.ar_buf, bc.ar_off, bc.ar_len, bybuf, directbufs);
		}
		return com.grey.base.utils.NIOBuffers.encode(content, chenc, null, null, bybuf, directbufs);
	}

	//convenience method which supports creating ByteBuffers with 8-bit string values
	public java.nio.ByteBuffer create(CharSequence content)
	{
		return com.grey.base.utils.NIOBuffers.encode(content, null, directbufs);
	}

	@Override
	public String toString()
	{
		String txt = "rcvbuf="+rcvbufsiz+", xmtbuf="+xmtbufsiz+", directbufs="+directbufs+", xmtpool="+(xmtpool != null);
		if (chenc != null) txt += " - charset="+chenc.charset().displayName();
		return txt;
	}
}