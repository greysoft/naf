/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.NIOBuffers;
import com.grey.base.collections.ObjectPool;

public class BufferGenerator
{
	//defaults to false as Direct buffers don't have backing array in standard JDK implementation
	public static final boolean directniobufs = SysProps.get("greynaf.nio.directbufs", false);
	private static final BufferConfig DefaultConfig = new BufferConfig(0, null, null, null);

	public final int rcvbufsiz;
	public final boolean directbufs;
	private final ObjectPool<java.nio.ByteBuffer> xmtpool;
	private final java.nio.charset.CharsetEncoder chenc;

	public String charsetName() {return (chenc == null ? "n/a" : chenc.charset().displayName());}
	
	public BufferGenerator(BufferConfig cfg) {
		rcvbufsiz = cfg.rcvbufsiz;
		directbufs = cfg.directbufs;

		// ISO-8859-1 should be ok, but best to omit, so we can default to direct byte-copy
		if (cfg.charset != null) {
			java.nio.charset.Charset chset = java.nio.charset.Charset.forName(cfg.charset);
			chenc = chset.newEncoder();
		} else {
			chenc = null;
		}

		if (cfg.withXmitPool) {
			// ByteBuffers are initially allocated with size of 1 bytes, since users (only IOEXecWriter?) will expand them on demand
			xmtpool = new ObjectPool<>(() -> NIOBuffers.create(1, directbufs));
		} else {
			xmtpool = null;
		}
	}

	public static BufferGenerator create(XmlConfig cfg, String xpath, int rcvsiz, int xmtsiz) {
		BufferGenerator.BufferConfig bufcfg = new BufferGenerator.BufferConfig(rcvsiz, xmtsiz==0?false:true, null, null);
		bufcfg = BufferGenerator.BufferConfig.create(cfg, xpath, bufcfg);
		return new BufferGenerator(bufcfg);
	}

	public java.nio.ByteBuffer encode(CharSequence content, java.nio.ByteBuffer bybuf)
	{
		if (content.getClass() == com.grey.base.utils.ByteChars.class) {
			com.grey.base.utils.ByteChars bc = (com.grey.base.utils.ByteChars)content;
			return NIOBuffers.encode(bc.buffer(), bc.offset(), bc.size(), bybuf, directbufs);
		}
		return NIOBuffers.encode(content, chenc, null, null, bybuf, directbufs);
	}

	public java.nio.ByteBuffer createReadBuffer()
	{
		return NIOBuffers.create(rcvbufsiz, directbufs);
	}

	//convenience method which supports creating ByteBuffers with 8-bit string values
	public java.nio.ByteBuffer create(CharSequence content)
	{
		return NIOBuffers.encode(content, null, directbufs);
	}

	public java.nio.ByteBuffer allocBuffer(int siz)
	{
		java.nio.ByteBuffer buf = xmtpool.extract();
		if (siz > buf.capacity()) buf = NIOBuffers.create(siz, buf.isDirect());
		buf.clear();
		return buf;
	}

	public void releaseBuffer(java.nio.ByteBuffer buf)
	{
		xmtpool.store(buf);
	}

	@Override
	public String toString() {
		String txt = "BufferGenerator[rcvbuf="+rcvbufsiz+", directbufs="+directbufs+", xmtpool="+(xmtpool != null);
		if (chenc != null) txt += ", charset="+chenc.charset().displayName();
		txt += "]";
		return txt;
	}


	public static class BufferConfig {
		public final int rcvbufsiz;
		public final boolean withXmitPool;
		public final boolean directbufs;
		public final String charset;

		public BufferConfig(int rcvbufsiz, Boolean withXmitPool, Boolean directbufs, String charset) {
			this.rcvbufsiz = rcvbufsiz;
			this.withXmitPool = (withXmitPool == null ? false : withXmitPool);
			this.directbufs = (directbufs == null ? directniobufs : directbufs);
			this.charset = charset;
		}

		@Override
		public String toString() {
			String txt = "BufferConfig[rcvbuf="+rcvbufsiz+", directbufs="+directbufs+", xmtpool="+withXmitPool;
			if (charset != null) txt += ", charset="+charset;
			txt += "]";
			return txt;
		}

		public static BufferConfig create(XmlConfig cfg, String xpath, BufferConfig dflts) {
			if (dflts == null) dflts = DefaultConfig;
			int rcvsiz = dflts.rcvbufsiz;
			int xmtsiz = (dflts.withXmitPool ? 1 : 0);
			boolean direct = dflts.directbufs;
			String charset = dflts.charset;
			if (cfg != null) {
				xpath = (xpath == null ? "" : xpath+"/");
				rcvsiz = (int)cfg.getSize(xpath+"@recvsize", rcvsiz);
				xmtsiz = (int)cfg.getSize(xpath+"@xmitsize", xmtsiz);
				direct = cfg.getBool(xpath+"@direct", direct);
				charset = cfg.getValue(xpath+"@charset", false, charset);
			}
			return new BufferConfig(rcvsiz, xmtsiz == 0 ? false : true, direct, charset);
		}
	}
}