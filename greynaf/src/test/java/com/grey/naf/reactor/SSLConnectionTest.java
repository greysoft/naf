/*
 * Copyright 2013-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.XmlConfig;
import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.SSLConfig;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;
import com.grey.naf.TestUtils;

/*
 * Note that this test class also exercises ListenerSet, ConcurrentListener and the Naflet class.
 */
public class SSLConnectionTest
	implements com.grey.naf.EntityReaper, CM_Listener.Reporter, TimerNAF.Handler
{
	private enum FAILTYPE {NONE, NOCONNECT, BADCERT_PURE, BADCERT_SWITCH};
	private static final String rootdir = TestUtils.initPaths(SSLConnectionTest.class);
	static final int filesize = (int)(IOExecWriter.MAXBUFSIZ * 1.5) + 1;
	static final String pthnam_sendfile = rootdir+"/sendfile";

	/* The required keys and certificates are generated with this script:
	keytool -genkeypair -v -alias server1key -keystore keystore.jks -keypass server1pass -validity 3650 -dname "CN=server1@localhost, O=Grey Software, ST=London, C=UK" -storepass kspass123
	keytool -exportcert -alias server1key -file tmp.cer -keystore keystore.jks -storepass kspass123
	keytool -importcert -v -alias server1cert -trustcacerts -file tmp.cer -keystore trustcerts.jks -storepass tspass123 -noprompt
	
	keytool -genkeypair -v -alias client1key -keyalg RSA -keystore keystore.jks -keypass client1pass -validity 3650 -dname "CN=client1@localhost, O=Grey Software, ST=London, C=UK" -storepass kspass123
	keytool -exportcert -alias client1key -file tmp.cer -keystore keystore.jks -storepass kspass123
	keytool -importcert -v -alias client1cert -trustcacerts -file tmp.cer -keystore trustcerts.jks -storepass tspass123 -noprompt
	rm tmp.cer
	 */
	private static final String srv_certname = "server1@localhost";
	private static final String clnt_certname = "client1@localhost";
	private static final String tspath = resourcePath("trustcerts.jks");
	private static final String kspath = resourcePath("keystore.jks");

	private static final String srvcfg_puressl = "<ssl cert=\"server1key\" clientauth=\"1\" peercert=\""+clnt_certname+"\""
			+" tspath=\""+tspath+"\" kspath=\""+kspath+"\" certpass=\"server1pass\"/>";
	private static final String srvcfg_nonssl = srvcfg_puressl.replace("<ssl", "<ssl latent=\"Y\"");
	private static final String srvcfg_switchssl = srvcfg_nonssl.replace("<ssl", "<ssl mandatory=\"Y\"").replace("clientauth=\"1\"", "clientauth=\"2\"");
	private static final String srvcfg_anonclient = srvcfg_puressl.replace(" peercert=", " x=");
	private static final String srvcfg_badcert_pure = srvcfg_puressl.replace(" peercert=\"", " peercert=\"x");
	private static final String srvcfg_badcert_switch = srvcfg_nonssl.replace("<ssl", "<ssl mandatory=\"Y\"").replace(" peercert=\"", " peercert=\"x");

	private static final String clntcfg_puressl = "<ssl cert=\"client1key\" peercert=\""+srv_certname+"\""
			+" tspath=\""+tspath+"\" kspath=\""+kspath+"\" certpass=\"client1pass\"/>";
	private static final String clntcfg_nonssl = clntcfg_puressl.replace("<ssl", "<ssl latent=\"Y\"");
	private static final String clntcfg_switchssl = clntcfg_nonssl.replace("<ssl", "<ssl mandatory=\"Y\"");
	private static final String clntcfg_anonclient = clntcfg_puressl.replace(" cert=", " x=");
	private static final String clntcfg_badcert_pure = clntcfg_puressl;
	private static final String clntcfg_badcert_switch = clntcfg_switchssl;

	static final String iomessages[] = {"Hello, I am the client and this is my first message",
		"This is the second message from the client",
		"The final message"};

	private static final ApplicationContextNAF appctx = TestUtils.createApplicationContext("SSLConnectionTest", true);

	private Dispatcher dsptch;
	private SSLTask ctask;
	private int reapcnt;
	private int reapcnt_clients;
	private int reapcnt_servers;
	private int startcnt_servers;
	private int expected_tcpentities;
	int srvport;
	SSLS lastsrv;

	@org.junit.Test
	public void testNonSSL() throws Exception
	{
		String sxml = "<x>"+srvcfg_nonssl+"</x>";
		String cxml = "<x>"+clntcfg_nonssl+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, "x");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, false, false, -1);
	}

	@org.junit.Test
	public void testPureSSL() throws Exception
	{
		String sxml = "<listeners><listener>"+srvcfg_puressl+"</listener></listeners>";
		String cxml = "<x>"+clntcfg_puressl+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, ".");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, true, true, -1);
	}

	@org.junit.Test
	public void testSwitchSSL() throws Exception
	{
		String sxml = "<listeners><listener>"+srvcfg_switchssl+"</listener></listeners>";
		String cxml = "<x>"+clntcfg_switchssl+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, ".");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, true, true, -1);
	}

	@org.junit.Test
	public void testAnonClient() throws Exception
	{
		String sxml = "<x>"+srvcfg_anonclient+"</x>";
		String cxml = "<x>"+clntcfg_anonclient+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, "x");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, true, false, -1);
	}

	// Server config specifies non-matching peercert
	@org.junit.Test
	public void testBadClient_wrongcert_pure() throws Exception
	{
		String sxml = "<x>"+srvcfg_badcert_pure+"</x>";
		String cxml = "<x>"+clntcfg_badcert_pure+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, "x");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, false, false, 1, FAILTYPE.BADCERT_PURE);
	}

	@org.junit.Test
	public void testBadClient_wrongcert_switch() throws Exception
	{
		String sxml = "<x>"+srvcfg_badcert_switch+"</x>";
		String cxml = "<x>"+clntcfg_badcert_switch+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, "x");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, false, false, 1, FAILTYPE.BADCERT_SWITCH); //server won't have completed SSL switch
	}

	@org.junit.Test
	public void testBadClient_noconnect() throws Exception
	{
		String sxml = "<listeners><listener>"+srvcfg_puressl+"</listener></listeners>";
		String cxml = "<x>"+clntcfg_puressl+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, ".");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, true, true, 0, FAILTYPE.NOCONNECT);
	}

	private void runtest(XmlConfig clntcfg, XmlConfig srvcfg, boolean sslmode, boolean lset, int fail_step, FAILTYPE failtype) throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);

		// create the Dispatcher
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef.Builder()
				.withSurviveHandlers(false)
				.build();
		dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));

		// set up the server component
		expected_tcpentities = (failtype == FAILTYPE.NOCONNECT ? 1 : 2);
		ListenerSet listeners = null;
		ConcurrentListener lstnr = null;
		String lname = "utest_SSL";
		if (lset) {
			ConcurrentListenerConfig[] lcfg = ConcurrentListenerConfig.buildMultiConfig(lname, appctx.getConfig(), "listeners/listener", srvcfg, 0, 0, TestServerFactory.class, null);
			listeners = new ListenerSet(lname, dsptch, this, this, lcfg);
			listeners.start(false);
			org.junit.Assert.assertEquals(1, listeners.configured());
			org.junit.Assert.assertEquals(1, listeners.count());
			srvport = listeners.getListener(0).getPort();
			listeners.setReporter(this);
		} else {
			ConcurrentListenerConfig lcfg = new ConcurrentListenerConfig.Builder<>()
					.withName(lname)
					.withServerFactory(TestServerFactory.class, null)
					.withXmlConfig(srvcfg, appctx.getConfig())
					.build();
			lstnr = ConcurrentListener.create(dsptch, this, this, lcfg);
			srvport = lstnr.getPort();
			lstnr.setReporter(this);
			dsptch.loadRunnable(lstnr);
		}

		// set up the client component
		int cport = srvport;
		if (failtype == FAILTYPE.NOCONNECT) {
			cport = srvport + 1000; //hopefully no such port exists
			if (cport > (Short.MAX_VALUE & 0xffff)) cport = Short.MAX_VALUE - 10;
		}
		SSLC clnt = new SSLC(dsptch, clntcfg, cport);
		clnt.start(this);

		// set up a no-op Naflet which simply goes through the motions
		ctask = new SSLTask("utest_sslc", dsptch);
		dsptch.loadRunnable(ctask);

		// launch
		dsptch.start(); //Dispatcher launches in separate thread
		//we join() Dispatcher thread, so its memory changes will be visible on return
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());

		if (failtype == FAILTYPE.BADCERT_PURE) {
			//connection does succeed, but then server immediately disconnects us
			org.junit.Assert.assertFalse(clnt.completed);
			org.junit.Assert.assertEquals(1, clnt.was_connected);
			org.junit.Assert.assertTrue(clnt.was_disconnected);
			org.junit.Assert.assertNull(lastsrv);
		} else if (failtype == FAILTYPE.BADCERT_SWITCH) {
			org.junit.Assert.assertFalse(clnt.completed);
			org.junit.Assert.assertEquals(1, clnt.was_connected);
			org.junit.Assert.assertTrue(clnt.was_disconnected);
			org.junit.Assert.assertTrue(lastsrv.completed);
		} else if (failtype == FAILTYPE.NOCONNECT) {
			org.junit.Assert.assertTrue(clnt.completed);
			org.junit.Assert.assertEquals(-1, clnt.was_connected);
			org.junit.Assert.assertFalse(clnt.was_disconnected);
			org.junit.Assert.assertNull(lastsrv);
		} else {
			org.junit.Assert.assertTrue(clnt.completed);
			org.junit.Assert.assertEquals(1, clnt.was_connected);
			org.junit.Assert.assertFalse(clnt.was_disconnected);
			org.junit.Assert.assertTrue(lastsrv.completed);
		}
		org.junit.Assert.assertTrue(ctask.stopped);
		org.junit.Assert.assertEquals(1, reapcnt_clients);
		org.junit.Assert.assertEquals(failtype == FAILTYPE.NOCONNECT ? 0 : 1, reapcnt_servers);
		org.junit.Assert.assertEquals(reapcnt_servers, startcnt_servers);
		org.junit.Assert.assertEquals(reapcnt_clients+1, reapcnt); //+1 for ListenerSet or Listener

		if (fail_step != -1) {
			//don't check client's progress, as it may have sent an extra message before it received disconnect
			if (lastsrv != null) org.junit.Assert.assertEquals(fail_step, lastsrv.step);
		} else {
			//this tests for successful completion, ie. it ran all the way through
			org.junit.Assert.assertEquals(2*(iomessages.length+1), clnt.recvstep);
			org.junit.Assert.assertEquals(iomessages.length+2, clnt.sendstep); //disconnect is counted as the final step
			org.junit.Assert.assertEquals(iomessages.length+1, lastsrv.step);
			org.junit.Assert.assertFalse(lastsrv.file_error);
		}

		if (listeners != null) {
			org.junit.Assert.assertEquals(1, listeners.configured());
			org.junit.Assert.assertEquals(0, listeners.count());
			boolean done = listeners.stop(true);
			org.junit.Assert.assertTrue(done);
		}

		if (failtype == FAILTYPE.NOCONNECT) {
			org.junit.Assert.assertFalse(clnt.state.usedSSL);
		} else {
			org.junit.Assert.assertTrue(sslmode == clnt.state.usedSSL);
			if (lastsrv != null) org.junit.Assert.assertTrue(sslmode == lastsrv.state.usedSSL);
		}
		//delete the file just to make sure nothing is holding a stream open
		final java.io.File fh = new java.io.File(pthnam_sendfile);
		boolean ok = fh.delete();
		if (fail_step == -1) org.junit.Assert.assertTrue(ok);
	}

	private void runtest(XmlConfig clntcfg, XmlConfig srvcfg, boolean sslmode, boolean lset, int fail_step)
			throws java.io.IOException, java.security.GeneralSecurityException
	{
		runtest(clntcfg, srvcfg, sslmode, lset, fail_step, FAILTYPE.NONE);
	}

	@Override
	public void entityStopped(Object obj)
	{
		reapcnt++;
		if (obj.getClass().equals(SSLC.class)) {
			reapcnt_clients++;
			entityStoppedTCP();
		}
	}

	@Override
	public void listenerNotification(EVENT evt, CM_Server s)
	{
		if (evt == EVENT.STARTED) {
			startcnt_servers++;
			return;
		}
		if (evt == EVENT.STOPPED) {
			reapcnt_servers++;
			entityStoppedTCP();
			return;
		}
		throw new IllegalArgumentException("Missing handler for Reporter.EVENT="+evt);
	}

	private void entityStoppedTCP()
	{
		if (reapcnt_clients + reapcnt_servers == expected_tcpentities) dsptch.setTimer(0, 0, this);
	}

	@Override
	public void timerIndication(TimerNAF tmr, Dispatcher d) throws java.io.IOException {
		boolean done = d.stop();
		org.junit.Assert.assertFalse(done);
	}

	@Override
	public void eventError(TimerNAF tmr, Dispatcher d, Throwable ex) {}

	private static String resourcePath(String cp)
	{
		try {
			java.net.URL url = com.grey.base.utils.DynLoader.getResource(cp);
			return url.toURI().toString();
		} catch (Throwable ex) {
			throw new RuntimeException("Failed to get resource-path="+cp+" - "+ex, ex);
		}
	}


	private static class SSLTask
		extends com.grey.naf.Naflet
	{
		public boolean stopped;

		public SSLTask(String name, Dispatcher dsptch)
				throws java.io.IOException {
			super(name, dsptch, null);
		}

		@Override
		protected boolean stopNaflet() {
			stopped = true;
			return true;
		}
	}


	private static class SSLC
		extends CM_Client
	{
		public final EntityState state = new EntityState();
		private final com.grey.naf.SSLConfig sslconfig;
		private java.nio.channels.SelectableChannel chan;
		public boolean completed;
		public int was_connected;
		public boolean was_disconnected;
		private final int srvport;
		public int recvstep;
		public int sendstep;

		@Override
		protected com.grey.naf.SSLConfig getSSLConfig() {return sslconfig;}

		public SSLC(Dispatcher d, XmlConfig cfg, int port) throws java.io.IOException
		{
			super(d, new com.grey.naf.BufferSpec(cfg, "niobuffers", 256, 128), new com.grey.naf.BufferSpec(cfg, "niobuffers", 256, 128));
			srvport = port;
			XmlConfig sslcfg = (cfg == null ? XmlConfig.NULLCFG : cfg.getSection("ssl"));
			if (sslcfg == null || !sslcfg.exists()) {
				sslconfig = null;
			} else {
				sslconfig = new SSLConfig.Builder()
						.withIsClient(true)
						.withXmlConfig(sslcfg, d.getApplicationContext().getConfig())
						.build();
				org.junit.Assert.assertNotNull(getSSLConfig());
			}
		}

		public void start(com.grey.naf.EntityReaper rpr) throws java.io.IOException, java.net.UnknownHostException
		{
			com.grey.base.utils.TSAP srvr = com.grey.base.utils.TSAP.build(null, srvport);
			initChannelMonitor();
			setReaper(rpr);
			connect(srvr.sockaddr);
		}

		@Override
		protected void connected(boolean success, CharSequence diagnostic, Throwable ex) throws java.io.IOException
		{
			was_connected = (success ? 1 : -1);
			chan = getChannel();
			if (!success) {
				org.junit.Assert.assertFalse(isConnected());
				org.junit.Assert.assertFalse(isBrokenPipe());
				org.junit.Assert.assertNotNull(getChannel());
				org.junit.Assert.assertFalse(chan.isOpen());
				disconnect();
				org.junit.Assert.assertNull(getChannel());
				org.junit.Assert.assertFalse(chan.isOpen());
				completed = true;
				return;
			}
			if (getSSLConfig().isLatent()) {
				org.junit.Assert.assertFalse(usingSSL());
			} else {
				org.junit.Assert.assertTrue(usingSSL());
			}
			org.junit.Assert.assertEquals(srvport, getRemotePort());
			org.junit.Assert.assertFalse(srvport==getLocalPort());
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getRemoteIP()));
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getLocalIP()));
			sendRequest();
		}

		@Override
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException
		{
			getReader().endReceive();
			int srvstep = (recvstep / 2) + 1;
			recvstep++;
			boolean send = (recvstep % 2 == 0);
			String expected = "OK "+srvstep+'a';
			if (send) expected = "Ready "+srvstep+'b';
			state.verifyReceivedMessage(expected, rcvdata);
			boolean switched = state.verifySSL(this, true, srv_certname, send);
			boolean finished = false;

			if (send) {
				if (switched) {
					// need to enable receiver but mode (delimited etc) doesn't matter, as SSL will now intercept
					getReader().receive(0);
				} else {
					finished = sendRequest();
				}
			} else {
				getReader().receiveDelimited((byte)'\n');
			}
			if (finished) completed = true;
		}

		@Override
		protected void startedSSL() throws java.io.IOException
		{
			org.junit.Assert.assertTrue(usingSSL());
			sendRequest();
		}

		private boolean sendRequest() throws java.io.IOException
		{
			boolean finished = false;
			if (sendstep == iomessages.length+1) {
				disconnect();
				finished = true;
			} else if (sendstep == iomessages.length) {
				sendfile();
			} else {
				getWriter().transmit(iomessages[sendstep]+"\n");
			}
			sendstep++;
			getReader().receiveDelimited((byte)'\n');
			return finished;
		}

		private void sendfile() throws java.io.IOException
		{
			java.io.File fh = new java.io.File(pthnam_sendfile);
			com.grey.base.utils.FileOps.ensureDirExists(fh.getParentFile());
			org.junit.Assert.assertFalse(fh.exists());

			byte[] filebody = new byte[filesize];
			for (int idx = 0; idx != filebody.length; idx++) {
				filebody[idx] = (byte)idx;
			};
			java.io.FileOutputStream ostrm = new java.io.FileOutputStream(fh, false);
			try {
				ostrm.write(filebody);
			} finally {
				ostrm.close();
			}
			getWriter().transmit(fh.toPath());
		}

		// We don't have to override ioDisconnected(), but if we do, we must call disconnect() ourselves
		@Override
		public void ioDisconnected(CharSequence diagnostic) throws java.io.IOException
		{
			super.ioDisconnected(diagnostic);
			was_disconnected = true;
		}
	}


	private static class SSLS
		extends CM_Server
	{
		public final EntityState state = new EntityState();
		public boolean completed;
		private java.nio.channels.SelectableChannel chan;
		private int filebytes;
		public int step;
		public boolean file_error;

		SSLS(TestServerFactory fact)
		{
			super(fact.lstnr, fact.bufspec, fact.bufspec);
			org.junit.Assert.assertNotNull(getSSLConfig());
		}

		@Override
		protected void connected() throws java.io.IOException
		{
			boolean ok = false;
			try {
				chan = getChannel();
				SSLConnectionTest harness = (SSLConnectionTest)getListener().getController();
				harness.lastsrv = this;
				if (getSSLConfig().isLatent()) {
					org.junit.Assert.assertFalse(usingSSL());
				} else {
					org.junit.Assert.assertTrue(usingSSL());
				}
				getReader().receiveDelimited((byte)'\n');

				//for want of anywhere better to test these
				org.junit.Assert.assertEquals(harness.srvport, getLocalPort());
				org.junit.Assert.assertFalse(harness.srvport == getRemotePort());
				org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getRemoteIP()));
				org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getLocalIP()));
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
			boolean sendack = true;
			getReader().endReceive();

			if (step < iomessages.length) {
				state.verifyReceivedMessage(iomessages[step], rcvdata);
			} else {
				int lmt = rcvdata.limit();
				for (int idx = rcvdata.offset(); idx != lmt; idx++) {
					if (!file_error && (filebytes & 0xff) != (rcvdata.buffer()[idx] & 0xff)) {
						//record the error status rather than aborting right away - want to see where this goes
						System.out.println("Bad filebyte at "+filebytes+"/"+filesize
								+" - "+(rcvdata.buffer()[idx] & 0xff)+" vs expected="+(filebytes & 0xff)
								+" - rcvdata="+rcvdata);
						file_error = true;
					}
					filebytes++;
				}
				if (filebytes != filesize) sendack = false;
			}
			
			if (sendack) {
				// send 2-line response, to make sure client's line-delimited receive works
				step++;
				getWriter().transmit("OK "+step+"a\nReady "+step+"b\n");
				state.verifySSL(this, getSSLConfig().getPeerCertName() != null, clnt_certname, true);
			}

			if (step == iomessages.length) {
				getReader().receive(0);
			} else {
				// as in the client, receiver also needs to be enabled if switching to SSL
				getReader().receiveDelimited((byte)'\n');
			}
		}

		@Override
		public void ioDisconnected(CharSequence diagnostic) throws java.io.IOException
		{
			super.ioDisconnected(diagnostic);
			org.junit.Assert.assertFalse(isConnected());
			org.junit.Assert.assertNull(getChannel());
			org.junit.Assert.assertFalse(chan.isOpen());
			org.junit.Assert.assertFalse(isBrokenPipe());
			completed = true;
		}

		@Override
		protected void startedSSL() throws java.io.IOException
		{
			org.junit.Assert.assertTrue(usingSSL());
			getReader().receiveDelimited((byte)'\n');
		}
	}


	public static final class TestServerFactory
		implements com.grey.naf.reactor.ConcurrentListener.ServerFactory
	{
		final CM_Listener lstnr;
		final com.grey.naf.BufferSpec bufspec;

		@Override
		public SSLS factory_create() {return new SSLS(this);}
		@Override
		public Class<SSLS> getServerClass() {return SSLS.class;}
		@Override
		public void shutdown() {}

		public TestServerFactory(com.grey.naf.reactor.CM_Listener l, Object cfg)
		{
			lstnr = l;
			com.grey.base.config.XmlConfig xmlcfg = (com.grey.base.config.XmlConfig)cfg;
			bufspec = new com.grey.naf.BufferSpec(xmlcfg, "niobuffers", 8 * 1024, 128);
			org.junit.Assert.assertNotNull(lstnr.getSSLConfig());
		}
	}


	private static class EntityState
	{
		public boolean usedSSL;
		EntityState() {} //make explicit with non-private access, to eliminate synthetic accessor

		void verifyReceivedMessage(String expected, ByteArrayRef actual)
		{
			org.junit.Assert.assertEquals('\n', actual.buffer()[actual.offset(actual.size()-1)]);
			actual.incrementSize(-1);
			org.junit.Assert.assertEquals(expected.length(), actual.size());
			org.junit.Assert.assertEquals(expected, new String(actual.buffer(), actual.offset(), actual.size()));
		}

		boolean verifySSL(CM_Stream cm, boolean havePeer, String expectedPeer, boolean switchssl)
				throws java.io.IOException
		{
			boolean switched = false;
			java.security.cert.X509Certificate peercert = cm.getPeerCertificate();
			java.security.cert.Certificate[] peerchain = cm.getPeerChain();
			if (cm.usingSSL()) {
				usedSSL = true;
				if (havePeer) {
					String cn = com.grey.base.crypto.SSLCertificate.getCN(peercert);
					org.junit.Assert.assertNotNull(peercert);
					org.junit.Assert.assertNotNull(peerchain);
					org.junit.Assert.assertEquals(expectedPeer, cn);
				} else {
					org.junit.Assert.assertNull(peercert);
					org.junit.Assert.assertNull(peerchain);
				}
			} else {
				if (cm.getSSLConfig().isMandatory() && switchssl) {
					org.junit.Assert.assertTrue(cm.getSSLConfig().isLatent());
					cm.startSSL();
					switched = true;
				}
				org.junit.Assert.assertNull(peercert);
			}
			return switched;
		}
	}
}