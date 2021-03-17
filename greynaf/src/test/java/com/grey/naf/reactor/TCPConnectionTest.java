/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.ByteOps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;
import com.grey.naf.TestUtils;

public class TCPConnectionTest
	implements com.grey.naf.EntityReaper, CM_Listener.Reporter
{
	private static final String rootdir = TestUtils.initPaths(TCPConnectionTest.class);
	private static final int NUM_CLIENTS = 10; //must be even and greater than 2
	private static final int INTSIZE = 4;
	private static final int REQ_INCR = 1000;
	private static final int RSP_INCR = 1;

	private static final ApplicationContextNAF appctx = TestUtils.createApplicationContext("TCPConnectionTest", true);

	private Dispatcher dsptch;
	private int reapcnt_clients;
	private int reapcnt_servers;
	private int startcnt_servers;
	private int reapcnt;
	private boolean completed_ok;
	int clientcnt_good;
	int clientcnt_bad;
	int servercnt_good;

	@org.junit.Test
	public void test() throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);

		// create the Dispatcher
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef.Builder()
				.withSurviveHandlers(false)
				.build();
		dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));

		// set up the server component
		ConcurrentListenerConfig lcfg = new ConcurrentListenerConfig.Builder<>()
				.withName("utest_TCPCon")
				.withServerFactory(TestServerFactory.class, null)
				.withInterface("127.0.0.1")
				.withPort(0)
				.build();
		CM_Listener lstnr = ConcurrentListener.create(dsptch, this, this, lcfg);
		lstnr.setReporter(this);
		dsptch.loadRunnable(lstnr);

		// set up the clients
		for (int loop = 0; loop != NUM_CLIENTS / 2; loop++) {
			ClientTCP clnt_good = new ClientTCP(dsptch, lstnr.getIP(), lstnr.getPort(), true, this);
			clnt_good.start(this);
			//this is hopefully an invalid port - a bad port will fail immediately, whereas bad IP takes 75 secs
			int cport = lstnr.getPort()+1000;
			if (cport > (Short.MAX_VALUE & 0xffff)) cport = Short.MAX_VALUE - 10;
			ClientTCP clnt_bad1 = new ClientTCP(dsptch, lstnr.getIP(), cport, false, this);
			clnt_bad1.start(this);
		}

		// launch
		dsptch.start(); //Dispatcher launches in separate thread
		//we join() Dispatcher thread, so its memory changes will be visible on return
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertTrue(completed_ok);
		org.junit.Assert.assertEquals(NUM_CLIENTS, reapcnt_clients);
		org.junit.Assert.assertEquals(reapcnt_clients+1, reapcnt); //+1 for listener
		org.junit.Assert.assertEquals(NUM_CLIENTS / 2, clientcnt_good);
		org.junit.Assert.assertEquals(NUM_CLIENTS / 2, clientcnt_bad);
		org.junit.Assert.assertEquals(NUM_CLIENTS / 2, reapcnt_servers); //only half the clients connected
		org.junit.Assert.assertEquals(reapcnt_servers, startcnt_servers);
		org.junit.Assert.assertEquals(reapcnt_servers, servercnt_good);
	}

	@Override
	public void entityStopped(Object obj)
	{
		reapcnt++;
		if (obj.getClass() == ClientTCP.class) entityStoppedTCP((ClientTCP)obj);
	}

	@Override
	public void listenerNotification(EVENT evt, CM_Server s)
	{
		if (evt == EVENT.STARTED) {
			startcnt_servers++;
			return;
		}
		if (evt == EVENT.STOPPED) {
			org.junit.Assert.assertEquals(INTSIZE*2, ((ServerTCP)s).rcvbytes);
			entityStoppedTCP(s);
			return;
		}
		throw new IllegalArgumentException("Missing handler for Reporter.EVENT="+evt);
	}

	private void entityStoppedTCP(CM_TCP obj)
	{
		if (obj.getClass() == ClientTCP.class) {
			reapcnt_clients++;
		} else {
			reapcnt_servers++;
		}
		if (reapcnt_clients == NUM_CLIENTS && reapcnt_servers == (NUM_CLIENTS / 2)) {
			dsptch.stop();
			completed_ok = true;
		}
	}


	private static class ClientTCP
		extends CM_Client
	{
		private final TCPConnectionTest harness;
		private static final com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(32, 64);
		private final int req = System.identityHashCode(this);
		private final java.net.InetSocketAddress srvaddr;
		private final boolean expect_ok;

		public ClientTCP(Dispatcher d, java.net.InetAddress ipaddr, int port, boolean ok, TCPConnectionTest h)
		{
			super(d, bufspec, bufspec);
			harness = h;
			expect_ok = ok;
			srvaddr = new java.net.InetSocketAddress(ipaddr, port);
			org.junit.Assert.assertNull(getSSLConfig());
		}

		public void start(com.grey.naf.EntityReaper rpr) throws java.io.IOException
		{
			org.junit.Assert.assertFalse(isConnected());
			initChannelMonitor();
			setReaper(rpr);
			connect(srvaddr);
		}

		@Override
		protected void connected(boolean success, CharSequence diagnostic, Throwable ex)
			throws java.io.IOException
		{
			org.junit.Assert.assertTrue(expect_ok == success);

			if (!success) {
				java.nio.channels.SelectableChannel chan = getChannel();
				org.junit.Assert.assertFalse(isConnected());
				org.junit.Assert.assertFalse(isBrokenPipe());
				org.junit.Assert.assertNotNull(getChannel());
				org.junit.Assert.assertFalse(chan.isOpen());
				disconnect();
				org.junit.Assert.assertNull(getChannel());
				org.junit.Assert.assertFalse(chan.isOpen());
				harness.clientcnt_bad++;
				return;
			}
			org.junit.Assert.assertTrue(isConnected());
			org.junit.Assert.assertEquals(srvaddr.getPort(), getRemotePort());
			org.junit.Assert.assertFalse(srvaddr.getPort()==getLocalPort());
			org.junit.Assert.assertEquals(getRemoteIP().toString(), IP.IP_LOCALHOST, IP.convertIP(getRemoteIP()));
			org.junit.Assert.assertEquals(getLocalIP().toString(), IP.IP_LOCALHOST, IP.convertIP(getLocalIP()));
			org.junit.Assert.assertEquals(getLocalIP(), getLocalAddress().getAddress());
			org.junit.Assert.assertEquals(getLocalPort(), getLocalAddress().getPort());
			org.junit.Assert.assertEquals(getRemoteIP(), getRemoteAddress().getAddress());
			org.junit.Assert.assertEquals(getRemotePort(), getRemoteAddress().getPort());

			byte[] buf = new byte[INTSIZE * 2];
			ByteOps.encodeInt(req, buf, 0, INTSIZE);
			ByteOps.encodeInt(req+REQ_INCR, buf, INTSIZE, INTSIZE);
			getWriter().transmit(buf);
			getReader().receive(INTSIZE*2);
		}

		@Override
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException
		{
			org.junit.Assert.assertEquals(INTSIZE*2, rcvdata.size());
			int rsp = ByteOps.decodeInt(rcvdata.buffer(), rcvdata.offset(), INTSIZE);
			org.junit.Assert.assertEquals(req+RSP_INCR, rsp);
			rsp = ByteOps.decodeInt(rcvdata.buffer(), rcvdata.offset() + INTSIZE, INTSIZE);
			org.junit.Assert.assertEquals(req+REQ_INCR+RSP_INCR, rsp);
			java.nio.channels.SelectableChannel chan = getChannel();
			org.junit.Assert.assertTrue(isConnected());
			org.junit.Assert.assertTrue(chan.isOpen());
			disconnect();
			org.junit.Assert.assertFalse(isBrokenPipe());
			org.junit.Assert.assertFalse(isConnected());
			org.junit.Assert.assertNull(getChannel());
			org.junit.Assert.assertFalse(chan.isOpen());
			harness.clientcnt_good++;
		}
	}


	private static class ServerTCP
		extends CM_Server
	{
		private static final com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(32, 64);
		private java.nio.channels.SelectableChannel chan;
		private boolean sent_response;
		public int rcvbytes;

		public ServerTCP(CM_Listener l, com.grey.base.config.XmlConfig cfg)
		{
			super(l, bufspec, bufspec);
			org.junit.Assert.assertNull(getSSLConfig());
			org.junit.Assert.assertFalse(isConnected());
		}

		@Override
		protected void connected() throws java.io.IOException
		{
			boolean ok = false;
			try {
				org.junit.Assert.assertTrue(isConnected());
				chan = getChannel();
				getReader().receive(INTSIZE);
				ok = true;
			} finally {
				// Can't depend on Dispatcher's error handling here as connected() errors are trapped and discarded within the Listener,
				// so we have to detect and act on any errors ourself.
				if (!ok) getDispatcher().stop();
			}
		}

		@Override
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException
		{
			org.junit.Assert.assertEquals(INTSIZE, rcvdata.size());
			rcvbytes += rcvdata.size();
			byte[] buf = new byte[INTSIZE];
			int req = ByteOps.decodeInt(rcvdata.buffer(), rcvdata.offset(), rcvdata.size());
			ByteOps.encodeInt(req+RSP_INCR, buf, 0, INTSIZE);
			getWriter().transmit(buf);
			sent_response = true;
		}

		@Override
		public void ioDisconnected(CharSequence diagnostic) throws java.io.IOException
		{
			super.ioDisconnected(diagnostic);
			org.junit.Assert.assertFalse(isConnected());
			org.junit.Assert.assertNull(getChannel());
			org.junit.Assert.assertFalse(chan.isOpen());
			org.junit.Assert.assertFalse(isBrokenPipe());
			org.junit.Assert.assertTrue(sent_response);
			//appears to be the end of a successful connection
			TCPConnectionTest harness = (TCPConnectionTest)getListener().getController();
			harness.servercnt_good++;
		}
	}


	public static final class TestServerFactory
		implements com.grey.naf.reactor.ConcurrentListener.ServerFactory
	{
		private final CM_Listener lstnr;

		public TestServerFactory(com.grey.naf.reactor.CM_Listener l, Object cfg) {
			lstnr = l;
		}
		@Override
		public ServerTCP factory_create() {return new ServerTCP(lstnr, null);}
		@Override
		public Class<ServerTCP> getServerClass() {return ServerTCP.class;}
		@Override
		public void shutdown() {}
	}
}