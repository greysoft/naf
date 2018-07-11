/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;

public class IOExecWriterTest
{
	private static final String rootdir = DispatcherTest.initPaths(IOExecWriterTest.class);
	private static final ApplicationContextNAF appctx = ApplicationContextNAF.create("IOWtest");

	private static class CMW extends CM_Stream
	{
		public boolean completed;

		public CMW(Dispatcher d, java.nio.channels.SelectableChannel w, com.grey.naf.BufferSpec bufspec)
				throws java.io.IOException {
			super(d, null, bufspec);
			registerConnectedChannel(w, true);
		}

		public boolean write(CharSequence data) throws java.io.IOException {
			getWriter().transmit(data, 0, data.length());
			return !getWriter().isBlocked();
		}

		public boolean write(com.grey.base.utils.ByteChars data) throws java.io.IOException {
			getWriter().transmit(data);
			return !getWriter().isBlocked();
		}

		public boolean write(java.nio.ByteBuffer data) throws java.io.IOException {
			getWriter().transmit(data);
			return !getWriter().isBlocked();
		}

		@Override
		protected void disconnectLingerDone(boolean ok, CharSequence info, Throwable ex) {
			if (!ok) org.junit.Assert.fail("Linger failed - "+info+" - "+ex);
			boolean done = disconnect(true); //make sure repeated call is ok
			org.junit.Assert.assertTrue(done);
			try {
				getDispatcher().stop();
			} catch (Exception ex2) {
				org.junit.Assert.fail("Failed to stop Dispatcher - "+ex2);
				return;
			}
			synchronized (this) {
				completed = true;
			}
		}
		
		@Override //not used
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException {}
	}

	// Pipe seems to be 8K in size
	@org.junit.Test
	public void testBlocking() throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		final com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(0, 10);
		final String rdwrdata = "This goes into an xmtpool buffer";  //deliberately larger than IOExecWriter's buffer-size
		final String rdonlydata = "This is a read-only buffer";  //deliberately larger than IOExecWriter's buffer-size
		final String initialchar = "z";
		final java.nio.ByteBuffer rdonlybuf = com.grey.base.utils.NIOBuffers.encode(rdonlydata, null, false).asReadOnlyBuffer();

		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));
		java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
		java.nio.channels.Pipe.SourceChannel rep = pipe.source();
		java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
		rep.configureBlocking(false);
		CMW cm = new CMW(dsptch, wep, bufspec);
		org.junit.Assert.assertTrue(cm.isConnected());

		// write to the pipe till it blocks
		org.junit.Assert.assertFalse(cm.getWriter().isBlocked());
		int pipesize = 0;
		while (cm.write(initialchar)) pipesize++;
		org.junit.Assert.assertTrue(cm.getWriter().isBlocked());
		String expectdata = initialchar;
		// pipe has now blocked, so do some more sends
		for (int loop = 0; loop != 3; loop++) {
			cm.getWriter().transmit(rdonlybuf);
			org.junit.Assert.assertTrue(cm.getWriter().isBlocked());
			expectdata += rdonlydata;
		}
		com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars(rdwrdata);
		for (int loop = 0; loop != 10; loop++) {
			boolean done = cm.write(bc);
			org.junit.Assert.assertFalse(done);
			org.junit.Assert.assertTrue(cm.getWriter().isBlocked());
			expectdata += rdwrdata;
		}
		//need to do this send to test main enqueue() loop
		java.nio.ByteBuffer niobuf = com.grey.base.utils.NIOBuffers.encode(rdwrdata, null, false);
		boolean done = cm.write(niobuf);
		org.junit.Assert.assertFalse(done);
		org.junit.Assert.assertTrue(cm.getWriter().isBlocked());
		expectdata += rdwrdata;
		int xmitcnt = pipesize + expectdata.length();

		// Read the first pipe-load of data.
		// Even though we allocate a big enough rcvbuf, we're not guaranteed to read it all in one go.
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(xmitcnt+10, false); //a few bytes to spare
		int rcvcnt = 0;
		while (rcvcnt < pipesize) {
			int nbytes = rep.read(rcvbuf);
			rcvcnt += nbytes;
			org.junit.Assert.assertTrue("Last read="+nbytes, rcvcnt <= pipesize);
			for (int idx = 0; idx != nbytes; idx++) {
				org.junit.Assert.assertEquals(initialchar.charAt(0), rcvbuf.get(idx));
			}
		}
		//there will be no more data to read till Dispatcher triggers the Writer
		int nbytes = rep.read(rcvbuf);
		org.junit.Assert.assertEquals(0, nbytes);
		org.junit.Assert.assertTrue(cm.getWriter().isBlocked());
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

		dsptch.stop();
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
	public void testFile() throws java.io.IOException
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
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));
		java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
		java.nio.channels.Pipe.SourceChannel rep = pipe.source();
		java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
		rep.configureBlocking(false); //else rep.read() will hang below if Dispatcher fails, rather than return 0
		CMW cm = new CMW(dsptch, wep, bufspec);
		org.junit.Assert.assertTrue(cm.isConnected());
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(filesize, false);

		//keep pumping files out until writer blocks
		int sendbytes = 0;
		while (!cm.getWriter().isBlocked()) {
			// istrm is closed by the transmit() call
			@SuppressWarnings("resource") java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
			java.nio.channels.FileChannel fchan = istrm.getChannel();
			cm.getWriter().transmit(fchan);
			sendbytes += filesize;
		}
		//and now send 2 more
		for (int idx = 0; idx != 2; idx++) {
			@SuppressWarnings("resource") java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
			java.nio.channels.FileChannel fchan = istrm.getChannel();
			cm.getWriter().transmit(fchan);
			sendbytes += filesize;
		}
		org.junit.Assert.assertTrue(cm.getWriter().isBlocked());
		//and two more sends via the other send-file methods
		cm.getWriter().transmit(fh.toPath());
		sendbytes += filesize;
		@SuppressWarnings("resource") java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
		java.nio.channels.FileChannel fchan = istrm.getChannel();
		int off = (1024*1024*2)+100;
		int lmt = (1024*1024*6)+200;
		cm.getWriter().transmitChunked(fchan, off, lmt, 900*1000, false);
		sendbytes += (lmt - off);
		org.junit.Assert.assertTrue(cm.getWriter().isBlocked());

		// tell the writer to disconnect - linger-on-close will safely prevent it closing till we're finished
		boolean done = cm.disconnect(true);
		org.junit.Assert.assertFalse(done);
		org.junit.Assert.assertTrue(cm.isConnected());

		// start the Dispatcher and wait for writer to drain its backlog
		dsptch.start();
		rcvbuf.clear();
		int rdbytes = 0;
		int nbytes;
		while ((nbytes = rep.read(rcvbuf)) != -1) {
			if (nbytes == 0) continue;
			for (int idx = 0; idx != nbytes; idx++) {
				org.junit.Assert.assertEquals(chval, rcvbuf.get(idx));
			}
			rcvbuf.clear();
			rdbytes += nbytes;
		}
		org.junit.Assert.assertEquals(sendbytes, rdbytes);
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		synchronized (cm) {
			org.junit.Assert.assertTrue(cm.completed);
		}
		rep.close();
		org.junit.Assert.assertFalse(cm.isConnected());
		org.junit.Assert.assertFalse(dsptch.isRunning());
		//delete the file just to make sure it has no dangling open streams
		org.junit.Assert.assertTrue(fh.exists());
		boolean ok = fh.delete();
		org.junit.Assert.assertTrue(ok);
	}
}