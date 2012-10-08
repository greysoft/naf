/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.reactor.ChannelMonitor;
import com.grey.naf.reactor.Dispatcher;

public class ChannelMonitorTest
{
	static {
		SysProps.set(com.grey.naf.Config.SYSPROP_DIRPATH_VAR, SysProps.TMPDIR+"/utest/CM");
	}
	protected int cmcnt;
	protected boolean completed;

	private static class CMR extends ChannelMonitor
	{
		private final ChannelMonitorTest runner;
		public final CMW writer;
		private final int xmtmax; //total number of transmits to do
		private int rcvmax; //total number of bytes to receive before we exit
		private int rcvbytes;  //total number of bytes received
		private int xmtcnt;  //number of transmits performed

		public CMR(Dispatcher d, java.nio.channels.SelectableChannel r, java.nio.channels.SelectableChannel w,
						com.grey.naf.BufferSpec bufspec, int xmtmax,
						ChannelMonitorTest runner)
				throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException {
			super(d);
			this.runner = runner;
			this.xmtmax = xmtmax;
			writer = new CMW(d, w, bufspec, runner);
			chanreader = new com.grey.naf.reactor.IOExecReader(bufspec);
			initChannel(r, true, true);
			chanreader.receive(0, true);
			runner.cmcnt++;
		}

		@Override
		protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata)
				throws com.grey.base.FaultException, java.io.IOException
		{
			rcvbytes += rcvdata.ar_len;

			if (xmtcnt < xmtmax) {
				xmtcnt++;
				writer.write(rcvdata.ar_buf, rcvdata.ar_off, rcvdata.ar_len);
			} else {
				if (rcvmax == 0) {
					rcvmax = writer.xmtbytes;
					writer.stop();
				}
			}

			if (rcvbytes != 0 && rcvbytes == rcvmax) {
				chanreader.endReceive();
				disconnect();
				disconnect();//make sure twice is safe
				if (--runner.cmcnt == 0) {
					runner.completed = true;
					dsptch.stop(dsptch);
				}
			}
		}

		@Override
		protected void eventError(ChannelMonitor cm, Throwable ex)
		{
			throw new RuntimeException("Throwing fatal error to halt Dispatcher");
		}
	}

	private static class CMW extends ChannelMonitor
	{
		private final ChannelMonitorTest runner;
		public int xmtbytes;  //total number of bytes transmitted
		private boolean inshutdown;

		public CMW(Dispatcher d, java.nio.channels.SelectableChannel w, com.grey.naf.BufferSpec bufspec, ChannelMonitorTest runner)
				throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException {
			super(d);
			this.runner = runner;
			chanwriter = new com.grey.naf.reactor.IOExecWriter(bufspec);
			initChannel(w, true, true);
			runner.cmcnt++;
		}

		public void write(byte[] data, int off, int len) throws java.io.IOException {
			xmtbytes += len;
			chanwriter.transmit(data, off, len);
		}

		public void stop() throws java.io.IOException {
			inshutdown = true;
			if (chanwriter.isBlocked()) return;
			disconnect();
			disconnect();//make sure twice is safe
			if (--runner.cmcnt == 0) {
				runner.completed = true;
				dsptch.stop(dsptch);
			}
		}

		@Override
		protected void ioTransmitted()
				throws com.grey.base.FaultException, java.io.IOException {
			if (inshutdown) stop();
		}
	}

	@org.junit.Test
	public void testDirect() throws com.grey.base.GreyException, java.io.IOException
	{
		launch(true);
	}

	@org.junit.Test
	public void testHeap() throws com.grey.base.GreyException, java.io.IOException
	{
		launch(false);
	}

	private void launch(boolean directbufs) throws com.grey.base.GreyException, java.io.IOException
	{
		int entitycnt = 64;
		int xmtmax = 5;  //only the write() calls from reader CM count towards this, not the calls below
		int xmtsiz = 10 * 1024;

		completed = false;
		cmcnt = 0;
		int rcvcap = xmtsiz - 1;
		com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(rcvcap, xmtsiz, true, directbufs);

		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, null, com.grey.logging.Factory.getLogger("no-such-logger"));
		CMR[] entities = new CMR[entitycnt];
		byte[] xmtbuf = new byte[xmtsiz];
		java.util.Arrays.fill(xmtbuf, (byte)1);

		for (int idx = 0; idx != entities.length; idx++) {
			java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
			java.nio.channels.Pipe.SourceChannel rep = pipe.source();
			java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
			CMR cm = new CMR(dsptch, rep, wep, bufspec, xmtmax, this);
			entities[idx] = cm;
			org.junit.Assert.assertTrue(cm.isConnected());
			org.junit.Assert.assertTrue(cm.writer.isConnected());
			// Although a pipe is only 8K, an initial write never blocks, no matter large.
			// So throw in a second write to trigger the IOExecWriter backlog processing,
			// if xmtsiz is meant to be large enough to cause it.
			cm.writer.write(xmtbuf, 0, xmtbuf.length);
			if (xmtbuf.length > 8 * 1024) cm.writer.write(xmtbuf, 0, xmtbuf.length);
		}
		long systime1 = System.currentTimeMillis();
		dsptch.start();
		dsptch.waitStopped();
		long systime2 = System.currentTimeMillis();
		org.junit.Assert.assertTrue(completed);
		System.out.println("BulkTest-"+entitycnt+"/"+xmtmax+"/"+xmtsiz+"/direct="+directbufs+" = "+TimeOps.expandMilliTime(systime2 - systime1));

		for (int idx = 0; idx != entities.length; idx++) {
			CMR cm = entities[idx];
			org.junit.Assert.assertFalse(cm.isConnected());
			org.junit.Assert.assertFalse(cm.writer.isConnected());
			org.junit.Assert.assertEquals(cm.writer.xmtbytes, cm.rcvbytes);
		}
		org.junit.Assert.assertEquals(bufspec.xmtpool.size(), bufspec.xmtpool.population());
	}
}
