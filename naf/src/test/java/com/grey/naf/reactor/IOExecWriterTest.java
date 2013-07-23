/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.StringOps;

public class IOExecWriterTest
{
	private static final String rootdir = DispatcherTest.initPaths(IOExecWriterTest.class);

	private static class CMW extends ChannelMonitor
	{
		public boolean completed;

		public CMW(Dispatcher d, java.nio.channels.SelectableChannel w, com.grey.naf.BufferSpec bufspec)
				throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException {
			super(d);
			chanwriter = new IOExecWriter(bufspec);
			initChannel(w, true, true);
		}

		public boolean write(CharSequence data) throws java.io.IOException {
			chanwriter.transmit(data, 0, data.length());
			return !chanwriter.isBlocked();
		}

		public boolean write(com.grey.base.utils.ByteChars data) throws java.io.IOException {
			chanwriter.transmit(data);
			return !chanwriter.isBlocked();
		}

		public boolean write(java.nio.ByteBuffer data) throws java.io.IOException {
			chanwriter.transmit(data);
			return !chanwriter.isBlocked();
		}

		@Override
		protected void disconnectLingerDone(boolean ok, CharSequence info, Throwable ex) {
			if (!ok) org.junit.Assert.fail("Linger failed - "+info+" - "+ex);
			boolean done = disconnect(true); //make sure repeated call is ok
			org.junit.Assert.assertTrue(done);
			try {
				dsptch.stop(dsptch);
			} catch (Exception ex2) {
				org.junit.Assert.fail("Failed to stop Dispatcher - "+ex2);
				return;
			}
			synchronized (this) {
				completed = true;
			}
		}
	}

	// Pipe seems to be 8K in size
	@org.junit.Test
	public void testBlocking() throws com.grey.base.GreyException, java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		final com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(0, 10);
		final String rdwrdata = "This goes into an xmtpool buffer";  //deliberately larger than IOExecWriter's buffer-size
		final String rdonlydata = "This is a read-only buffer";  //deliberately larger than IOExecWriter's buffer-size
		final String initialchar = "z";
		final java.nio.ByteBuffer rdonlybuf = com.grey.base.utils.NIOBuffers.encode(rdonlydata, null, false).asReadOnlyBuffer();

		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, null, com.grey.logging.Factory.getLogger("no-such-logger"));
		java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
		java.nio.channels.Pipe.SourceChannel rep = pipe.source();
		java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
		rep.configureBlocking(false);
		CMW cm = new CMW(dsptch, wep, bufspec);
		org.junit.Assert.assertTrue(cm.isConnected());

		// write to the pipe till it blocks
		org.junit.Assert.assertFalse(cm.chanwriter.isBlocked());
		int pipesize = 0;
		while (cm.write(initialchar)) pipesize++;
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
		String expectdata = initialchar;
		// pipe has now blocked, so do some more sends
		for (int loop = 0; loop != 3; loop++) {
			cm.chanwriter.transmit(rdonlybuf);
			org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
			expectdata += rdonlydata;
		}
		com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars(rdwrdata);
		for (int loop = 0; loop != 10; loop++) {
			boolean done = cm.write(bc);
			org.junit.Assert.assertFalse(done);
			org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
			expectdata += rdwrdata;
		}
		//need to do this send to test main enqueue() loop
		java.nio.ByteBuffer niobuf = com.grey.base.utils.NIOBuffers.encode(rdwrdata, null, false);
		boolean done = cm.write(niobuf);
		org.junit.Assert.assertFalse(done);
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
		expectdata += rdwrdata;
		int xmitcnt = pipesize + expectdata.length();

		// read the first pipe-load of data
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(xmitcnt+10, false); //a few bytes to spare
		int nbytes = rep.read(rcvbuf);
		org.junit.Assert.assertEquals(pipesize, nbytes);
		for (int idx = 0; idx != pipesize; idx++) {
			org.junit.Assert.assertEquals(initialchar.charAt(0), rcvbuf.get(idx));
		}
		//there will be no more data to read till Dispatcher triggers the Writer
		nbytes = rep.read(rcvbuf);
		org.junit.Assert.assertEquals(0, nbytes);
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
		done = cm.disconnect(); //should be delayed by linger
		org.junit.Assert.assertFalse(done);

		// start the Dispatcher and wait for writer to drain its backlog
		dsptch.start();
		bc = new com.grey.base.utils.ByteChars();
		int rdbytes = 0;
		rcvbuf.clear();
		while ((nbytes = rep.read(rcvbuf)) != -1) {
			if (nbytes == 0) continue;
			for (int idx = 0; idx != nbytes; idx++) {
				bc.append(rcvbuf.get(idx));
			}
			rcvbuf.clear();
			rdbytes += nbytes;
		}
		org.junit.Assert.assertEquals(xmitcnt - pipesize, rdbytes);
		org.junit.Assert.assertTrue(StringOps.sameSeq(expectdata, bc));

		dsptch.stop(null);
		dsptch.waitStopped();
		synchronized (cm) {
			org.junit.Assert.assertTrue(cm.completed);
		}
		rep.close();
		org.junit.Assert.assertFalse(cm.isConnected());
		org.junit.Assert.assertFalse(dsptch.isRunning());
		org.junit.Assert.assertEquals(bufspec.xmtpool.size(), bufspec.xmtpool.population());
	}

	// Test file-send, with blocking conditions again
	// We know from the above test that the NIO pipe seems to be 8K in size, so a 64K
	// file should be bound to block.
	@org.junit.Test
	public void testFile() throws com.grey.base.GreyException, java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		final com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(0, 0);

		// create the file
		final int filesize = 8 * 1024 * 1024;
		final char chval = 'A';
		final String pthnam = rootdir+"/sendfile";
		final java.io.File fh = new java.io.File(pthnam);
		com.grey.base.utils.FileOps.ensureDirExists(fh.getParentFile());
		org.junit.Assert.assertFalse(fh.exists());

		byte[] filebody = new byte[filesize];
		java.util.Arrays.fill(filebody, (byte)chval);
		java.io.FileOutputStream ostrm = new java.io.FileOutputStream(fh, false);
		try {
			ostrm.write(filebody);
		} finally {
			ostrm.close();
		}
		org.junit.Assert.assertTrue(fh.exists());
		org.junit.Assert.assertEquals(filebody.length, fh.length());

		// create the Dispatcher and write channel
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, null, com.grey.logging.Factory.getLogger("no-such-logger"));
		java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
		java.nio.channels.Pipe.SourceChannel rep = pipe.source();
		java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
		rep.configureBlocking(false); //else rep.read() will hang below if Dispatcher fails, rather than return 0
		CMW cm = new CMW(dsptch, wep, bufspec);
		org.junit.Assert.assertTrue(cm.isConnected());
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(filesize, false);

		// This send loop seems to block at different stages on different platforms, but it
		// is always blocked by the end.
		int sendbytes = 0;
		for (int idx = 0; idx != 3; idx++) {
			java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
			java.nio.channels.FileChannel fchan = istrm.getChannel();
			cm.chanwriter.transmit(fchan);
			sendbytes += filesize;
		}
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
		cm.chanwriter.transmit(fh);
		sendbytes += filesize;
		java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
		java.nio.channels.FileChannel fchan = istrm.getChannel();
		int off = (1024*1024*2)+100;
		int lmt = (1024*1024*6)+200;
		cm.chanwriter.transmitChunked(fchan, off, lmt, 900*1000, false);
		sendbytes += (lmt - off);
		boolean done = cm.disconnect(true); //should be delayed by linger
		org.junit.Assert.assertFalse(done);

		// start the Dispatcher and wait for writer to drain its backlog
		dsptch.start();
		int rdbytes = 0;
		int nbytes;
		rcvbuf.clear();
		while ((nbytes = rep.read(rcvbuf)) != -1) {
			if (nbytes == 0) continue;
			for (int idx = 0; idx != nbytes; idx++) {
				org.junit.Assert.assertEquals(chval, rcvbuf.get(idx));
			}
			rcvbuf.clear();
			rdbytes += nbytes;
		}
		org.junit.Assert.assertEquals(sendbytes, rdbytes);
		dsptch.waitStopped();
		synchronized (cm) {
			org.junit.Assert.assertTrue(cm.completed);
		}
		rep.close();
		org.junit.Assert.assertFalse(cm.isConnected());
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}
}