/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.util.Arrays;

import com.grey.base.config.SysProps;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.StringOps;
import com.grey.naf.reactor.ChannelMonitor;
import com.grey.naf.reactor.Dispatcher;

public class IOExecWriterTest
{
	static {
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_VAR, SysProps.TMPDIR+"/utest/IOW");
	}
	private static String workdir = SysProps.TMPDIR+"/utest/iow";

	private static class CMW extends ChannelMonitor
	{
		public boolean completed;

		public CMW(Dispatcher d, java.nio.channels.SelectableChannel w, com.grey.naf.BufferSpec bufspec)
				throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException {
			super(d);
			chanwriter = new com.grey.naf.reactor.IOExecWriter(bufspec);
			initChannel(w, true, true);
		}

		public boolean write(CharSequence data) throws java.io.IOException {
			return chanwriter.transmit(data, 0, data.length());
		}

		@Override
		protected void ioTransmitted()
				throws com.grey.base.FaultException, java.io.IOException {
			synchronized (this) {
				completed = true;
			}
			disconnect();
			disconnect();//make sure twice is safe
			dsptch.stop(dsptch);
		}

		@Override
		protected void eventError(ChannelMonitor cm, Throwable ex)
		{
			throw new RuntimeException("Throwing fatal error to halt Dispatcher");
		}
	}

	// Pipe seems to be 8K in size
	@org.junit.Test
	public void testBlocking() throws com.grey.base.GreyException, java.io.IOException
	{
		com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(0, 10, true);
		String finalseq = "FinalSequence";  //deliberately larger than IOExecWriter's buffer-size

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

		// write to the pipe till it blocks, and then write some more
		org.junit.Assert.assertFalse(cm.chanwriter.isBlocked());
		int pipesize = 0;
		while (cm.write("z")) pipesize++;
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
		boolean done = cm.write(finalseq);
		org.junit.Assert.assertFalse(done);
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());
		int xmitcnt = pipesize + 1 + finalseq.length();

		// read the first pipe-load of data
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(xmitcnt+10, false); //a few bytes to spare
		int nbytes = rep.read(rcvbuf);
		org.junit.Assert.assertEquals(pipesize, nbytes);
		for (int idx = 0; idx != pipesize; idx++) {
			org.junit.Assert.assertEquals('z', rcvbuf.get(idx));
		}
		//there will be no more data to read till Dispatcher triggers the Writer
		nbytes = rep.read(rcvbuf);
		org.junit.Assert.assertEquals(0, nbytes);
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());

		// start the Dispatcher and wait for writer to drain its backlog
		cm.disconnect();  //make sure it lingers
		dsptch.start();
		ByteChars bc = new ByteChars();
		int rdbytes = 0;
		rcvbuf.clear();
		while ((nbytes = rep.read(rcvbuf)) != -1) {
			if (nbytes == 0) continue;
			rdbytes += nbytes;
			for (int idx = 0; idx != nbytes; idx++) {
				bc.append(rcvbuf.get(idx));
			}
			rcvbuf.clear();
		}
		org.junit.Assert.assertEquals(xmitcnt - pipesize, rdbytes);
		org.junit.Assert.assertTrue(StringOps.sameSeq("z"+finalseq, bc));

		dsptch.stop(null);
		dsptch.waitStopped();
		synchronized (cm) {
			org.junit.Assert.assertFalse(cm.completed);
		}
		rep.close();
		org.junit.Assert.assertFalse(cm.isConnected());
		org.junit.Assert.assertEquals(bufspec.xmtpool.size(), bufspec.xmtpool.population());
	}

	// Test file-send, with blocking conditions again
	// We know from the above test that the NIO pipe seems to be 8K in size, so a 64K
	// file should be bound to block.
	@org.junit.Test
	public void testFile() throws com.grey.base.GreyException, java.io.IOException
	{
		int filesize = 8 * 1024 * 1024;
		int xmitbuf = 2 * 1024 * 1024;
		com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(0, 0, false);

		// create the file
		String pthnam = workdir+"/sendfile";
		java.io.File fh = new java.io.File(pthnam);
		com.grey.base.utils.FileOps.ensureDirExists(fh.getParentFile());
		fh.delete();
		org.junit.Assert.assertFalse(fh.exists());

		byte[] filebody = new byte[filesize];
		Arrays.fill(filebody, (byte)'A');
		java.io.FileOutputStream ostrm = new java.io.FileOutputStream(fh);
		ostrm.write(filebody);
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
		rep.configureBlocking(false);
		CMW cm = new CMW(dsptch, wep, bufspec);
		org.junit.Assert.assertTrue(cm.isConnected());
		java.nio.ByteBuffer rcvbuf = com.grey.base.utils.NIOBuffers.create(filesize, false);

		// send the file down the pipe
		java.io.FileInputStream istrm = new java.io.FileInputStream(pthnam);
		java.nio.channels.FileChannel fchan = istrm.getChannel();
		boolean done = cm.chanwriter.transmit(fchan, xmitbuf, 0, 0);
		org.junit.Assert.assertFalse(done);
		org.junit.Assert.assertTrue(cm.chanwriter.isBlocked());

		// start the Dispatcher and wait for writer to drain its backlog
		dsptch.start();
		int rdbytes = 0;
		int nbytes;
		rcvbuf.clear();
		while ((nbytes = rep.read(rcvbuf)) != -1) {
			if (nbytes == 0) continue;
			rdbytes += nbytes;
			for (int idx = 0; idx != nbytes; idx++) {
				org.junit.Assert.assertEquals('A', rcvbuf.get(idx));
			}
			rcvbuf.clear();
		}
		org.junit.Assert.assertEquals(filesize, rdbytes);
		dsptch.waitStopped();
		synchronized (cm) {
			org.junit.Assert.assertTrue(cm.completed);
		}
		rep.close();
		org.junit.Assert.assertFalse(cm.isConnected());
	}
}
