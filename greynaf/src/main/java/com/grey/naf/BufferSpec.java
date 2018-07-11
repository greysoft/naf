/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;

public class BufferSpec
{
	//defaults to false as Direct buffers don't have backing array
	public static final boolean directniobufs = SysProps.get("greynaf.nio.directbufs", false);

	public final int rcvbufsiz;  //receive-buffer size
	public final boolean directbufs; //false by default, because direct buffers don't have a backing array in standard JDK implementation
	public final com.grey.base.collections.ObjectWell<java.nio.ByteBuffer> xmtpool; //NB: ignores xmtsiz
	private final java.nio.charset.CharsetEncoder chenc;

	public String charsetName() {return (chenc == null ? "n/a" : chenc.charset().displayName());}

	public BufferSpec(int rcvsiz, int xmtsiz)
	{
		this(null, null, rcvsiz, xmtsiz);
	}

	public BufferSpec(int rcvsiz, int xmtsiz, boolean direct)
	{
		this(null, null, rcvsiz, xmtsiz, direct);
	}

	public BufferSpec(com.grey.base.config.XmlConfig cfg, String xpath, int rcvsiz, int xmtsiz)
	{
		this(cfg, xpath, rcvsiz, xmtsiz, directniobufs);
	}

	public BufferSpec(com.grey.base.config.XmlConfig cfg, String xpath, int rcvsiz, int xmtsiz, boolean direct)
	{
		String charset = null;
		if (cfg != null) {
			xpath = (xpath == null ? "" : xpath+"/");
			rcvsiz = (int)cfg.getSize(xpath+"@recvsize", rcvsiz);
			xmtsiz = (int)cfg.getSize(xpath+"@xmitsize", xmtsiz);
			direct = cfg.getBool(xpath+"@direct", direct);
			charset = cfg.getValue(xpath+"@charset", false, null);
		}
		rcvbufsiz = rcvsiz;
		directbufs = direct;

		// ISO-8859-1 should be ok, but best to omit, so we can default to direct byte-copy
		if (charset != null) {
			java.nio.charset.Charset chset = java.nio.charset.Charset.forName(charset);
			chenc = chset.newEncoder();
		} else {
			chenc = null;
		}

		if (xmtsiz == 0) {
			xmtpool = null;
		} else {
			// initial alloc of pool buffers should be as small as possible, since IOExecWriter will expand them on demand
			com.grey.base.utils.NIOBuffers.BufferFactory factory = new com.grey.base.utils.NIOBuffers.BufferFactory(1, directbufs);
			xmtpool = new com.grey.base.collections.ObjectWell<java.nio.ByteBuffer>(java.nio.ByteBuffer.class, factory,
								"BufferSpecPool_"+rcvbufsiz+":"+xmtsiz+":"+directbufs, 0, 0, 1);
		}
	}

	public java.nio.ByteBuffer encode(CharSequence content, java.nio.ByteBuffer bybuf)
	{
		if (content.getClass() == com.grey.base.utils.ByteChars.class) {
			com.grey.base.utils.ByteChars bc = (com.grey.base.utils.ByteChars)content;
			return com.grey.base.utils.NIOBuffers.encode(bc.buffer(), bc.offset(), bc.size(), bybuf, directbufs);
		}
		return com.grey.base.utils.NIOBuffers.encode(content, chenc, null, null, bybuf, directbufs);
	}

	public java.nio.ByteBuffer createReadBuffer()
	{
		return com.grey.base.utils.NIOBuffers.create(rcvbufsiz, directbufs);
	}

	//convenience method which supports creating ByteBuffers with 8-bit string values
	public java.nio.ByteBuffer create(CharSequence content)
	{
		return com.grey.base.utils.NIOBuffers.encode(content, null, directbufs);
	}

	@Override
	public String toString()
	{
		String txt = "rcvbuf="+rcvbufsiz+", directbufs="+directbufs+", xmtpool="+(xmtpool != null);
		if (chenc != null) txt += " - charset="+chenc.charset().displayName();
		return txt;
	}
}