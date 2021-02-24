/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;

public class IterativeListenerTest
	implements com.grey.naf.EntityReaper
{
	private static class Factory implements IterativeListener.ServerFactory
	{
		private final java.net.Socket[] clients;
		public Factory(java.net.Socket[] c) {clients = c;}
		@Override
		public CM_Server createServer(CM_Listener l) {return new TestServer(l, clients);}
	}

	private static final String rootdir = DispatcherTest.initPaths(IterativeListenerTest.class);
	private static final ApplicationContextNAF appctx = ApplicationContextNAF.create("IterativeListenerTest");
	private static final int NUMCONNS = 5;
	private static final int INTSIZE = 4;

	private int reapcnt;

	@org.junit.Test
	public void test() throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		java.net.Socket clients[] = new java.net.Socket[NUMCONNS];

		// set up Dispatcher
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef.Builder()
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));

		Factory fact = new Factory(clients);
		IterativeListener lstnr = IterativeListener.create("utest_IterativeListener", dsptch, fact, this, null, null, 0);
		TestServer srvr = (TestServer)lstnr.getConnectionHandler();
		org.junit.Assert.assertEquals(TestServer.class, srvr.getClass());
		org.junit.Assert.assertEquals(TestServer.class, lstnr.getServerType());
		lstnr.start();

		for (int idx = 0; idx != clients.length; idx++) {
			clients[idx] = new java.net.Socket("127.0.0.1", lstnr.getPort());
			org.junit.Assert.assertEquals(clients[idx].getPort(), lstnr.getPort());
			org.junit.Assert.assertTrue(clients[idx].isConnected());
		}
		dsptch.start();

		for (int idx = 0; idx != clients.length; idx++) {
			int req = (idx + 1) * (int)System.nanoTime();
			byte[] buf = new byte[INTSIZE * 10];
			ByteOps.encodeInt(req, buf, 0, INTSIZE);
			clients[idx].getOutputStream().write(buf, 0, INTSIZE);
			clients[idx].getOutputStream().flush();
			int nbytes = clients[idx].getInputStream().read(buf);
			org.junit.Assert.assertEquals("Client="+idx+"/"+clients.length, INTSIZE, nbytes);
			int rsp = ByteOps.decodeInt(buf, 0, nbytes);
			org.junit.Assert.assertEquals("Client="+idx+"/"+clients.length, req+1, rsp);
			clients[idx].close();
		}
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertTrue(srvr.completed);
		org.junit.Assert.assertEquals(clients.length, srvr.conncount);
		org.junit.Assert.assertEquals(1, reapcnt);
		boolean done = lstnr.stop();
		org.junit.Assert.assertTrue(done);
	}

	@Override
	public void entityStopped(Object obj) {
		reapcnt++;
		org.junit.Assert.assertEquals(IterativeListener.class, obj.getClass());
	}


	private static class TestServer
		extends CM_Server
	{
		private static final com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(32, 64);
		public boolean completed;
		public int conncount;
		private int opencount;
		private final java.net.Socket clients[];

		public TestServer(CM_Listener l, java.net.Socket c[])
		{
			super(l, bufspec, bufspec);
			clients = c;
			org.junit.Assert.assertNull(getSSLConfig());
		}

		@Override
		protected void connected() throws java.io.IOException
		{
			conncount++;
			opencount++;
			boolean ok = false;
			try {
				org.junit.Assert.assertEquals(1, opencount);
				org.junit.Assert.assertEquals(clients[conncount-1].getPort(), getLocalPort());
				org.junit.Assert.assertEquals(clients[conncount-1].getLocalPort(), getRemotePort());
				getReader().receive(INTSIZE);
				ok = true;
			} finally {
				// Can't depend on Dispatcher's error handling here as connected() errors are trapped and discarded within the Listener,
				// so we have to detect and act on any errors ourself.
				if (!ok) terminate(false);
			}
		}

		@Override
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException
		{
			org.junit.Assert.assertEquals(INTSIZE, rcvdata.size());
			int req = ByteOps.decodeInt(rcvdata.buffer(), rcvdata.offset(), rcvdata.size());
			byte[] buf = ByteOps.encodeInt(req+1, rcvdata.size());
			getWriter().transmit(buf);
		}

		@Override
		public void ioDisconnected(CharSequence diagnostic)
		{
			boolean done = disconnect();
			opencount--;
			org.junit.Assert.assertTrue(done);
			org.junit.Assert.assertEquals(0, opencount);
			if (conncount == NUMCONNS) terminate(true);
		}

		private void terminate(boolean success) {
			getDispatcher().stop();
			completed = success;
		}
	}
}