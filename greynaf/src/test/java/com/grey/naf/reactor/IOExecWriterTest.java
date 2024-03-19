/*
 * Copyright 2012-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.BufferGenerator;
import com.grey.naf.TestUtils;

public class IOExecWriterTest
{
	private static final String rootdir = TestUtils.initPaths(IOExecWriterTest.class);
	private static final String rdwrdata = "This goes into an xmtpool buffer";  //deliberately larger than IOExecWriter's buffer-size (BufferSpec in testBlocking)
	private static final String rdonlydata = "This is a read-only buffer";  //deliberately larger than IOExecWriter's buffer-size
	private static final java.nio.ByteBuffer rdonlybuf = com.grey.base.utils.NIOBuffers.encode(rdonlydata, null, false).asReadOnlyBuffer();
	private static final int filesize = 8 * 1024 * 1024;
	private static final String initialchar = "z";

	private static final ApplicationContextNAF appctx = TestUtils.createApplicationContext("IOWtest", true, null);

	@org.junit.Test
	public void testBlocking() throws Exception
	{
		FileOps.deleteDirectory(rootdir);
		BufferGenerator.BufferConfig bufcfg = new BufferGenerator.BufferConfig(0, true, null, null);
		BufferGenerator bufspec = new BufferGenerator(bufcfg);
		BlockingQueue<BlockingTestData> blockingQueue = new ArrayBlockingQueue<>(5);

		com.grey.naf.reactor.config.DispatcherConfig def = com.grey.naf.reactor.config.DispatcherConfig.builder()
				.withAppContext(appctx)
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(def);
		java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
		java.nio.channels.Pipe.SourceChannel rep = pipe.source();
		java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
		rep.configureBlocking(false);
		CMW cm = new CMW(dsptch, rep, wep, bufspec, null, blockingQueue);
		dsptch.loadRunnable(cm);

		// start the Dispatcher and wait for writer to execute
		dsptch.start();
		BlockingTestData data = blockingQueue.take();

		// wait for writer to drain its backlog
		com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars();
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(data.xmitcnt+10, false); //a few bytes to spare
		int rdbytes = 0;
		rcvbuf.clear();
		int nbytes;
		while ((nbytes = rep.read(rcvbuf)) != -1) {
			if (nbytes == 0) continue;
			for (int idx = 0; idx != nbytes; idx++) {
				bc.append(rcvbuf.get(idx));
			}
			rcvbuf.clear();
			rdbytes += nbytes;
		}
		org.junit.Assert.assertEquals(data.xmitcnt - data.pipesize, rdbytes);
		org.junit.Assert.assertTrue(StringOps.sameSeq(data.expectdata, bc));

		dsptch.stop();
		dsptch.waitStopped();
		synchronized (cm) {
			org.junit.Assert.assertTrue(cm.completed);
		}
		rep.close();
		org.junit.Assert.assertFalse(cm.isConnected());
		org.junit.Assert.assertFalse(dsptch.isRunning());
	}

	// Test file-send, with blocking conditions again
	// We know from the above test that the NIO pipe seems to be 8K in size, so a 64K
	// file should be bound to block.
	@org.junit.Test
	public void testFile() throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		BufferGenerator.BufferConfig bufcfg = new BufferGenerator.BufferConfig(0, false, null, null);
		BufferGenerator bufspec = new BufferGenerator(bufcfg);

		// create the file
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
		com.grey.naf.reactor.config.DispatcherConfig def = com.grey.naf.reactor.config.DispatcherConfig.builder()
				.withAppContext(appctx)
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(def);
		java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
		java.nio.channels.Pipe.SourceChannel rep = pipe.source();
		java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
		rep.configureBlocking(false); //else rep.read() will hang below if Dispatcher fails, rather than return 0
		CMW cm = new CMW(dsptch, null, wep, bufspec, fh, null);
		dsptch.loadRunnable(cm);
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(filesize, false);

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
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertEquals(cm.sendbytes, rdbytes);
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


	private static class CMW extends CM_Stream implements DispatcherRunnable
	{
		private final BlockingQueue<BlockingTestData> blockingQueue;
		private final java.nio.channels.SelectableChannel wchan;
		private final java.nio.channels.Pipe.SourceChannel rchan;
		private final java.io.File fh;
		public boolean completed;
		public int sendbytes;

		@Override
		public String getName() {return "IOExecWriterTest.CMW";}
		@Override //not used
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException {}

		public CMW(Dispatcher d, java.nio.channels.Pipe.SourceChannel r, java.nio.channels.SelectableChannel w, BufferGenerator bufspec, java.io.File fh,
				BlockingQueue<BlockingTestData> q) throws java.io.IOException {
			super(d, null, bufspec);
			blockingQueue = q;
			rchan = r;
			wchan = w;
			this.fh = fh;
		}

		@Override
		public void startDispatcherRunnable() throws java.io.IOException {
			registerConnectedChannel(wchan, true);
			org.junit.Assert.assertTrue(isConnected());
			if (rchan == null) {
				doFileTest();
			} else {
				doBlockingTest();
			}
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

		//keep pumping files out until writer blocks
		public void doFileTest() throws java.io.IOException {
			while (!getWriter().isBlocked()) {
				// istrm is closed by the transmit() call
				@SuppressWarnings("resource") java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
				java.nio.channels.FileChannel fchan = istrm.getChannel();
				getWriter().transmit(fchan);
				sendbytes += filesize;
			}
			//and now send 2 more
			for (int idx = 0; idx != 2; idx++) {
				@SuppressWarnings("resource") java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
				java.nio.channels.FileChannel fchan = istrm.getChannel();
				getWriter().transmit(fchan);
				sendbytes += filesize;
			}
			org.junit.Assert.assertTrue(getWriter().isBlocked());
			//and two more sends via the other send-file methods
			getWriter().transmit(fh.toPath());
			sendbytes += filesize;
			@SuppressWarnings("resource") java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
			java.nio.channels.FileChannel fchan = istrm.getChannel();
			int off = (1024*1024*2)+100;
			int lmt = (1024*1024*6)+200;
			getWriter().transmitChunked(fchan, off, lmt, 900*1000, false);
			sendbytes += (lmt - off);
			org.junit.Assert.assertTrue(getWriter().isBlocked());

			// tell the writer to disconnect - linger-on-close will safely prevent it closing till we're finished
			boolean done = disconnect(true);
			org.junit.Assert.assertFalse(done);
			org.junit.Assert.assertTrue(isConnected());
		}

		// write to the pipe till it blocks
		public void doBlockingTest() throws java.io.IOException {
			org.junit.Assert.assertFalse(getWriter().isBlocked());
			String expectdata = initialchar;
			int pipesize = 0;
			while (write(initialchar)) pipesize++;
			org.junit.Assert.assertTrue(getWriter().isBlocked());
			// pipe has now blocked, so do some more sends
			for (int loop = 0; loop != 3; loop++) {
				getWriter().transmit(rdonlybuf);
				org.junit.Assert.assertTrue(getWriter().isBlocked());
				expectdata += rdonlydata;
			}
			com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars(rdwrdata);
			for (int loop = 0; loop != 10; loop++) {
				boolean done = write(bc);
				org.junit.Assert.assertFalse(done);
				org.junit.Assert.assertTrue(getWriter().isBlocked());
				expectdata += rdwrdata;
			}
			//need to do this send to test main enqueue() loop
			java.nio.ByteBuffer niobuf = com.grey.base.utils.NIOBuffers.encode(rdwrdata, null, false);
			boolean done = write(niobuf);
			org.junit.Assert.assertFalse(done);
			org.junit.Assert.assertTrue(getWriter().isBlocked());
			expectdata += rdwrdata;
			final int xmitcnt = pipesize + expectdata.length();

			// Read the first pipe-load of data.
			// Even though we allocate a big enough rcvbuf, we're not guaranteed to read it all in one go.
			java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(xmitcnt+10, false); //a few bytes to spare
			int rcvcnt = 0;
			while (rcvcnt < pipesize) {
				int nbytes = rchan.read(rcvbuf);
				rcvcnt += nbytes;
				org.junit.Assert.assertTrue("Last read="+nbytes, rcvcnt <= pipesize);
				for (int idx = 0; idx != nbytes; idx++) {
					org.junit.Assert.assertEquals(initialchar.charAt(0), rcvbuf.get(idx));
				}
			}
			//there will be no more data to read till Dispatcher triggers the Writer
			int nbytes = rchan.read(rcvbuf);
			org.junit.Assert.assertEquals(0, nbytes);
			org.junit.Assert.assertTrue(getWriter().isBlocked());
			done = disconnect(); //should be delayed by linger
			org.junit.Assert.assertFalse(done);

			BlockingTestData data = new BlockingTestData(expectdata, xmitcnt, pipesize);
			blockingQueue.add(data);
		}
	}

	private static class BlockingTestData {
		public final String expectdata;
		public final int xmitcnt;
		public final int pipesize;

		public BlockingTestData(String expectdata, int xmitcnt, int pipesize) {
			this.expectdata = expectdata;
			this.xmitcnt = xmitcnt;
			this.pipesize = pipesize;
		}

	}
}