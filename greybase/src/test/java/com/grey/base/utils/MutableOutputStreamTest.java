/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public class MutableOutputStreamTest
{
	private final String workdir = SysProps.TMPDIR+"/utest/"+getClass().getSimpleName();

	@org.junit.Test
	public void testBuffering() throws Exception {
		int bufsiz = 16;
		java.io.File fh = new java.io.File(workdir+"/file1");
		FileOps.ensureDirExists(fh.getParentFile());
		java.io.OutputStream s = new java.io.FileOutputStream(fh, false);
		MutableOutputStream ostrm = new MutableOutputStream(s, bufsiz);
		java.io.ByteArrayOutputStream bstrm = new java.io.ByteArrayOutputStream();
		org.junit.Assert.assertEquals(bufsiz, ostrm.getBufferCap());

		byte[] buf = new byte[1];
		buf[0] = 1;
		bstrm.write(buf[0]);
		ostrm.write(buf[0]);
		int buflen = 1;
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(1, ostrm.getBufferedByte(0));
		try {
			ostrm.getBufferedByte(1);
			org.junit.Assert.fail("Expected get-1 to fail");
		} catch (IllegalArgumentException ex) {}
		try {
			ostrm.setBufferedByte(1, 255);
			org.junit.Assert.fail("Expected set-1 to fail");
		} catch (IllegalArgumentException ex) {}

		buf = new byte[] {2, 3, 4};
		bstrm.write(buf);
		ostrm.write(buf);
		buflen += buf.length;
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());

		buf = new byte[] {5, 6, 7, 8};
		int off = 1; int len = 2;
		bstrm.write(buf, off, len);
		ostrm.write(buf, off, len);
		buflen += len;
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(1, ostrm.getBufferedByte(0));
		org.junit.Assert.assertEquals(7, ostrm.getBufferedByte(buflen-1));

		buf = new byte[ostrm.getBufferCap() - ostrm.getBufferLen()];
		for (int idx = 0; idx != buf.length; idx++) buf[idx] = (byte)(9+idx);
		byte prevbyte = buf[buf.length - 1];
		bstrm.write(buf);
		ostrm.write(buf);
		buflen += buf.length;
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferCap());
		org.junit.Assert.assertEquals(1, ostrm.getBufferedByte(0));

		buf[0] = (byte)(prevbyte + 1);
		bstrm.write(buf, 0, 1);
		ostrm.write(buf, 0, 1);
		buflen = bufsiz;
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(2, ostrm.getBufferedByte(0));
		org.junit.Assert.assertEquals(prevbyte+1, ostrm.getBufferedByte(buflen-1));
		try {
			ostrm.getBufferedByte(buflen);
			org.junit.Assert.fail("Expected get-cap to fail");
		} catch (IllegalArgumentException ex) {}
		try {
			ostrm.setBufferedByte(buflen, 255);
			org.junit.Assert.fail("Expected set-cap to fail");
		} catch (IllegalArgumentException ex) {}

		buf = new byte[ostrm.getBufferCap()];
		for (int idx = 0; idx != buf.length; idx++) buf[idx] = (byte)(prevbyte+2+idx);
		prevbyte = buf[buf.length - 1];
		bstrm.write(buf);
		ostrm.write(buf); //buflen unchanged - at max
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(buf[0], ostrm.getBufferedByte(0));
		org.junit.Assert.assertEquals(prevbyte, ostrm.getBufferedByte(buflen-1));

		buf = new byte[ostrm.getBufferCap()+1];
		for (int idx = 0; idx != buf.length; idx++) buf[idx] = (byte)(prevbyte+1+idx);
		prevbyte = buf[buf.length - 1];
		bstrm.write(buf);
		ostrm.write(buf); //buflen unchanged - at max
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(buf[1], ostrm.getBufferedByte(0));
		org.junit.Assert.assertEquals(prevbyte, ostrm.getBufferedByte(buflen-1));

		buf = new byte[(2*ostrm.getBufferCap())+3];
		for (int idx = 0; idx != buf.length; idx++) buf[idx] = (byte)(prevbyte+1+idx);
		prevbyte = buf[buf.length - 1];
		ostrm.write(buf); //buflen unchanged - at max
		byte byte0 = buf[buf.length - bufsiz];
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(byte0, ostrm.getBufferedByte(0));
		org.junit.Assert.assertEquals(prevbyte, ostrm.getBufferedByte(buflen-1));

		ostrm.truncateBy(2);
		buflen -= 2;
		org.junit.Assert.assertEquals(buflen, ostrm.getBufferLen());
		org.junit.Assert.assertEquals(byte0, ostrm.getBufferedByte(0));
		org.junit.Assert.assertEquals(buf[buf.length - 3], ostrm.getBufferedByte(buflen-1));
		try {
			ostrm.getBufferedByte(buflen);
			org.junit.Assert.fail("Expected get-limit to fail");
		} catch (IllegalArgumentException ex) {}
		try {
			ostrm.setBufferedByte(buflen, 255);
			org.junit.Assert.fail("Expected set-limit to fail");
		} catch (IllegalArgumentException ex) {}

		ostrm.setBufferedByte(0, byte0+1);
		ostrm.setBufferedByte(buflen-1, buf[buf.length - 3]+1);
		org.junit.Assert.assertEquals(byte0+1, ostrm.getBufferedByte(0));
		org.junit.Assert.assertEquals(buf[buf.length - 3]+1, ostrm.getBufferedByte(buflen-1));
		buf[buf.length - bufsiz] = (byte)(byte0+1);
		buf[buf.length - 3]++;
		bstrm.write(buf, 0, buf.length - 2);

		ostrm.flush();
		org.junit.Assert.assertEquals(0, ostrm.getBufferLen());
		ostrm.flush();
		org.junit.Assert.assertEquals(0, ostrm.getBufferLen());

		buf = new byte[] {(byte)(prevbyte+1), (byte)(prevbyte+2), (byte)(prevbyte+3)};
		bstrm.write(buf);
		ostrm.write(buf);
		org.junit.Assert.assertEquals(buf.length, ostrm.getBufferLen());
		ostrm.close();
		org.junit.Assert.assertEquals(0, ostrm.getBufferLen());

		byte[] buf1 = bstrm.toByteArray();
		org.junit.Assert.assertEquals(buf1.length, fh.length());
		byte[] buf2 = new byte[(int)fh.length()];
		java.io.FileInputStream is = new java.io.FileInputStream(fh);
		try {
			int nbytes = is.read(buf2);
			org.junit.Assert.assertEquals(buf2.length, nbytes);
		} finally {
			is.close();
		}
		for (int idx = 0; idx != buf1.length; idx++) {
			org.junit.Assert.assertEquals("Pos="+idx+"/"+buf1.length, buf1[idx], buf2[idx]);
		}
	}
}