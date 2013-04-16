/*
 * Copyright 2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class DuplexPipeTest
{
	@org.junit.Test
	public void testSingle() throws java.io.IOException
	{
		DuplexPipe p = DuplexPipe.create();
		runtests(p);
	}

	@org.junit.Test
	public void testFactory() throws java.io.IOException
	{
		DuplexPipe.Factory f = new DuplexPipe.Factory();
		DuplexPipe p1 = f.create();
		DuplexPipe p2 = f.create();
		runtests(p1);
		runtests(p2);
	}

	private void runtests(DuplexPipe p) throws java.io.IOException
	{
		byte[] req = "Hello, I am endpoint 1".getBytes();
		byte[] rsp = "And I am endpoint 2".getBytes();
		java.io.InputStream ep1_in = p.ep1.getInputStream();
		java.io.OutputStream ep1_out = p.ep1.getOutputStream();
		java.io.InputStream ep2_in = p.ep2.getInputStream();
		java.io.OutputStream ep2_out = p.ep2.getOutputStream();
		verifyIO(ep1_out, ep2_in, req);
		verifyIO(ep2_out, ep1_in, rsp);
		p.close();
		try {
			p.ep1.getInputStream();
			org.junit.Assert.fail("getInputStream didn't fail as expected on closed pipe");
		} catch (java.io.IOException ex) {}
		try {
			ep1_in.read();
			org.junit.Assert.fail("Read didn't fail as expected on closed pipe");
		} catch (java.io.IOException ex) {}
		try {
			p.ep2.getInputStream();
			org.junit.Assert.fail("getInputStream didn't fail as expected on closed pipe");
		} catch (java.io.IOException ex) {}
		try {
			ep2_in.read();
			org.junit.Assert.fail("Read didn't fail as expected on closed pipe");
		} catch (java.io.IOException ex) {}
	}

	private void verifyIO(java.io.OutputStream ostrm, java.io.InputStream istrm, byte[] data) throws java.io.IOException
	{
		byte[] rcvbuf = new byte[data.length + 10];
		ostrm.write(data);
		int nbytes = istrm.read(rcvbuf);
		org.junit.Assert.assertEquals(data.length, nbytes);
		for (int idx = 0; idx != nbytes; idx++) {
			org.junit.Assert.assertEquals(data[idx], rcvbuf[idx]);
		}
	}
}