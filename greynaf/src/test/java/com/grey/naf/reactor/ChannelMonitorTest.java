/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.TestUtils;

public class ChannelMonitorTest
	implements com.grey.naf.EntityReaper
{
	private static final String rootdir = TestUtils.initPaths(ChannelMonitorTest.class);
	private static final int xmtsiz = 33 * 1024;

	private int reapcnt_receivers;
	private int reapcnt_senders;
	int cmcnt;
	boolean completed;

	@org.junit.Test
	public void testDirect() throws java.io.IOException
	{
		launch(true);
	}

	@org.junit.Test
	public void testHeap() throws java.io.IOException
	{
		launch(false);
	}

	private void launch(boolean directbufs) throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		final int paircnt = 64;
		final int numsends = 5;  //only the write() calls from CMR count towards this total, not the calls below
		final CMR[] receivers = new CMR[paircnt];
		final com.grey.naf.BufferGenerator bufspec = new com.grey.naf.BufferGenerator(xmtsiz - 1, xmtsiz, directbufs, null);

		ApplicationContextNAF appctx = TestUtils.createApplicationContext("CMTEST-"+directbufs, true);
		com.grey.naf.reactor.config.DispatcherConfig def = new com.grey.naf.reactor.config.DispatcherConfig.Builder()
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));

		for (int idx = 0; idx != receivers.length; idx++) {
			java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
			java.nio.channels.Pipe.SourceChannel rep = pipe.source();
			java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
			CMW cmw = new CMW(dsptch, wep, bufspec, this);
			CMR cmr = new CMR(dsptch, rep, bufspec, numsends, cmw, this);
			dsptch.loadRunnable(cmw);
			dsptch.loadRunnable(cmr);
			receivers[idx] = cmr;
		}
		long systime1 = System.currentTimeMillis();
		dsptch.start();
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		long systime2 = System.currentTimeMillis();
		org.junit.Assert.assertTrue(completed);
		org.junit.Assert.assertEquals(0, cmcnt);
		org.junit.Assert.assertEquals(paircnt, reapcnt_receivers);
		org.junit.Assert.assertEquals(paircnt, reapcnt_senders);
		System.out.println("BulkTest: "+paircnt+"/"+numsends+"/"+xmtsiz+"/direct="+directbufs+" = "+TimeOps.expandMilliTime(systime2 - systime1)+"ms");

		for (int idx = 0; idx != receivers.length; idx++) {
			CMR cmr = receivers[idx];
			CMW cmw = cmr.writer;
			TestMonitor[] cmpair = new TestMonitor[]{cmr, cmw};
			for (int idx2 = 0; idx2 != cmpair.length; idx2++) {
				TestMonitor cm = cmpair[idx2];
				org.junit.Assert.assertTrue(cm.toString(), cm.completed);
				org.junit.Assert.assertFalse(cm.isBrokenPipe());
				org.junit.Assert.assertNull(cm.getChannel());
				org.junit.Assert.assertNull(cm.getChannel());
				org.junit.Assert.assertNull(cm.getReaper());
			}
			org.junit.Assert.assertEquals(cmw.xmtbytes, cmr.rcvbytes);
			org.junit.Assert.assertFalse(cmr.isConnected());
			org.junit.Assert.assertTrue(cmw.pipe.isOpen()); //because CMW didn't take ownership
			org.junit.Assert.assertTrue(cmw.isConnected());
			org.junit.Assert.assertFalse(cmw.pipe.isRegistered());
			cmw.pipe.close();
			org.junit.Assert.assertFalse(cmw.pipe.isOpen());
		}
	}

	@Override
	public void entityStopped(Object obj) {
		if (obj.getClass() == CMR.class) {
			reapcnt_receivers++;
		} else if (obj.getClass() == CMW.class) {
			reapcnt_senders++;
		} else {
			throw new IllegalArgumentException("Reaped unexpected object="+obj.getClass().getName()+" - "+obj);
		}
		TestMonitor cm = (TestMonitor)obj;
		org.junit.Assert.assertNull(cm.getReaper());
	}


	private abstract static class TestMonitor
		extends CM_Stream implements DispatcherRunnable
	{
		protected final ChannelMonitorTest harness;
		private final java.nio.channels.SelectableChannel chan;
		private final boolean takeOwnership;
		public boolean completed;

		public TestMonitor(Dispatcher d, java.nio.channels.SelectableChannel c, boolean owner,
				com.grey.naf.BufferGenerator rbufspec, com.grey.naf.BufferGenerator wbufspec,
				ChannelMonitorTest h) throws java.io.IOException {
			super(d, rbufspec, wbufspec);
			chan = c;
			takeOwnership = owner;
			harness = h;
			harness.cmcnt++;
			org.junit.Assert.assertFalse(isConnected());
			org.junit.Assert.assertNull(getReaper());
			setReaper(harness);
			org.junit.Assert.assertTrue(getReaper() == harness);
		}

		@Override
		public void startDispatcherRunnable() throws java.io.IOException {
			registerConnectedChannel(chan, takeOwnership);
			org.junit.Assert.assertTrue(getReaper() == harness);
			org.junit.Assert.assertTrue(isConnected());
			org.junit.Assert.assertTrue(getChannel() == chan);
			org.junit.Assert.assertFalse(getChannel().isRegistered());
		}
	}

	private static class CMR extends TestMonitor
	{
		public final CMW writer;
		private final int numsends; //total number of transmits to do
		private int sendcnt;  //number of transmits performed
		public int rcvbytes;  //total number of bytes received
		private byte expectval;

		@Override
		public String getName() {return "ChannelMonitorTest.CMR";}

		public CMR(Dispatcher d, java.nio.channels.SelectableChannel c, com.grey.naf.BufferGenerator bufspec, int sends,
					CMW cmw, ChannelMonitorTest harness) throws java.io.IOException {
			super(d, c, true, bufspec, null, harness);
			numsends = sends;
			writer = cmw;
			org.junit.Assert.assertNull(getWriter());
		}

		@Override
		public void startDispatcherRunnable() throws java.io.IOException {
			super.startDispatcherRunnable();
			getReader().receive(0);
			org.junit.Assert.assertTrue(getChannel().isRegistered());
		}

		@Override
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException
		{
			for (int idx = 0; idx != rcvdata.size(); idx++) {
				org.junit.Assert.assertEquals(expectval++, rcvdata.buffer()[rcvdata.offset(idx)]);
			}
			rcvbytes += rcvdata.size();

			if (sendcnt < numsends) {
				sendcnt++;
				writer.write(rcvdata.buffer(), rcvdata.offset(), rcvdata.size());
			} else {
				//writes are complete (but probably blocked)
				writer.stop();
			}

			if (sendcnt == numsends && rcvbytes == writer.xmtbytes) {
				//and now the reads are complete as well
				getReader().endReceive();
				java.nio.channels.SelectableChannel chan = getChannel();
				org.junit.Assert.assertTrue(isConnected());
				org.junit.Assert.assertTrue(chan.isOpen());
				disconnect();
				org.junit.Assert.assertFalse(chan.isOpen());
				org.junit.Assert.assertFalse(isConnected());
				disconnect();//make sure twice is safe

				if (--harness.cmcnt == 0) {
					getDispatcher().stop();
					harness.completed = true;
				}
				completed = true;
			}
		}
	}

	private static class CMW extends TestMonitor
	{
		public final java.nio.channels.SelectableChannel pipe;
		public int xmtbytes;  //total number of bytes transmitted
		public byte sendval;

		@Override
		public String getName() {return "ChannelMonitorTest.CMW";}

		public CMW(Dispatcher d, java.nio.channels.SelectableChannel w, com.grey.naf.BufferGenerator bufspec, ChannelMonitorTest runner)
				throws java.io.IOException {
			super(d, w, false, null, bufspec, runner);
			pipe = w;
			org.junit.Assert.assertNull(getReader());
		}

		@Override
		public void startDispatcherRunnable() throws java.io.IOException {
			super.startDispatcherRunnable();
			byte[] xmtbuf = new byte[xmtsiz];
			do {
				for (int idx = 0; idx != xmtbuf.length; idx++) {
					xmtbuf[idx] = sendval++;
				}
				write(xmtbuf, 0, xmtbuf.length);
			} while (!getWriter().isBlocked());
			org.junit.Assert.assertTrue(getChannel().isRegistered());
		}

		public void write(byte[] buf, int off, int len) throws java.io.IOException {
			for (int idx = 0; idx != len; idx++) {
				buf[off+idx] = sendval++;
			}
			getWriter().transmit(buf, off, len);
			xmtbytes += len;
		}

		public void stop() throws java.io.IOException {
			boolean done = disconnect();
			if (getWriter().isBlocked()) {
				org.junit.Assert.assertFalse(done);
				org.junit.Assert.assertTrue(getChannel().isRegistered());
				return;
			}
			org.junit.Assert.assertTrue(done);
			terminated();
		}

		@Override
		protected void disconnectLingerDone(boolean ok, CharSequence info, Throwable ex) {
			if (!ok) org.junit.Assert.fail("Disconnect failed - "+info+" - "+ex);
			boolean done = disconnect(true); //make sure repeated call is ok
			org.junit.Assert.assertTrue(done);
			terminated();
		}

		private void terminated() {
			boolean done = disconnect(); //make sure repeated call is safe
			org.junit.Assert.assertTrue(done);
			if (!completed) harness.cmcnt--;
			completed = true;
		}

		@Override //not used
		public void ioReceived(ByteArrayRef rcvdata) {}
	}
}
