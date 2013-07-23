/*
 * Copyright 2013 Grey Software (Yusef Badri) - All rights reserved
 */
package com.grey.naf.reactor;

import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;

/*
 * Note that this test class also exercises ListenerSet, ConcurrentListener and the Naflet class.
 */
public class SSLConnectionTest
	implements com.grey.naf.EntityReaper, Timer.Handler
{
	private static final String rootdir = DispatcherTest.initPaths(SSLConnectionTest.class);
	private static int filesize = (int)(IOExecWriter.MAXBUFSIZ * 1.5) + 1;
	private static int filexmtsiz = filesize - 30;
	private static byte filebyte = (byte)143; //deliberately 8-bit

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
	private static final String srvcfg_badclient = srvcfg_nonssl.replace("<ssl", "<ssl mandatory=\"Y\"").replace(" peercert=\"", " peercert=\"x");

	private static final String clntcfg_puressl = "<ssl cert=\"client1key\" peercert=\""+srv_certname+"\""
			+" tspath=\""+tspath+"\" kspath=\""+kspath+"\" certpass=\"client1pass\"/>";
	private static final String clntcfg_nonssl = clntcfg_puressl.replace("<ssl", "<ssl latent=\"Y\"");
	private static final String clntcfg_switchssl = clntcfg_nonssl.replace("<ssl", "<ssl mandatory=\"Y\"");
	private static final String clntcfg_anonclient = clntcfg_puressl.replace(" cert=", " x=");
	private static final String clntcfg_badclient = clntcfg_switchssl;

	private static final String iomessages[] = {"Hello, I am the client", "This is the 2nd message", "The final message"};

	private Dispatcher dsptch;
	private SSLTask ctask;
	private int srvport;
	private boolean usedSSL;
	private int srvcnt;
	private SSLS lastsrv;
	private int reapcnt;
	private Object lastreaped;

	@org.junit.Test
	public void testNonSSL() throws com.grey.base.GreyException, java.io.IOException, java.security.GeneralSecurityException
	{
		String sxml = "<x>"+srvcfg_nonssl+"</x>";
		String cxml = "<x>"+clntcfg_nonssl+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, "x");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, false, -1);
		org.junit.Assert.assertFalse(usedSSL);
	}

	@org.junit.Test
	public void testInitialSSL() throws com.grey.base.GreyException, java.io.IOException, java.security.GeneralSecurityException
	{
		String sxml = "<listeners><listener>"+srvcfg_puressl+"</listener></listeners>";
		String cxml = "<x>"+clntcfg_puressl+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, ".");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, true, -1);
		org.junit.Assert.assertTrue(usedSSL);
	}

	@org.junit.Test
	public void testSwitchSSL() throws com.grey.base.GreyException, java.io.IOException, java.security.GeneralSecurityException
	{
		String sxml = "<listeners><listener>"+srvcfg_switchssl+"</listener></listeners>";
		String cxml = "<x>"+clntcfg_switchssl+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, ".");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, true, -1);
		org.junit.Assert.assertTrue(usedSSL);
	}

	@org.junit.Test
	public void testAnonClient() throws com.grey.base.GreyException, java.io.IOException, java.security.GeneralSecurityException
	{
		String sxml = "<x>"+srvcfg_anonclient+"</x>";
		String cxml = "<x>"+clntcfg_anonclient+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, "x");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, false, -1);
		org.junit.Assert.assertTrue(usedSSL);
	}

	@org.junit.Test
	public void testBadClient() throws com.grey.base.GreyException, java.io.IOException, java.security.GeneralSecurityException
	{
		String sxml = "<x>"+srvcfg_badclient+"</x>";
		String cxml = "<x>"+clntcfg_badclient+"</x>";
		XmlConfig srvcfg = XmlConfig.makeSection(sxml, "x");
		XmlConfig clntcfg = XmlConfig.makeSection(cxml, "x");
		runtest(clntcfg, srvcfg, false, 1);
		org.junit.Assert.assertFalse(usedSSL); //server won't have completed SSL switch
	}

	private void runtest(XmlConfig clntcfg, XmlConfig srvcfg, boolean lset, int fail_step)
			throws com.grey.base.GreyException, java.io.IOException, java.security.GeneralSecurityException
	{
		FileOps.deleteDirectory(rootdir);
		ctask = null;
		usedSSL = false;
		srvcnt = 0;
		lastsrv = null;
		reapcnt = 0;
		lastreaped = 0;

		// create the Dispatcher
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.zeroNafletsOK = false;
		def.hasNafman = false;
		def.surviveHandlers = false;
		dsptch = Dispatcher.create(def, null, com.grey.logging.Factory.getLogger("no-such-logger"));

		// set up the server component
		java.util.Map<String,Object> cfgdflts = new java.util.HashMap<String,Object>();
		cfgdflts.put(Listener.CFGMAP_CLASS, SSLS.class);
		ListenerSet listeners = null;
		ConcurrentListener lstnr = null;
		if (lset) {
			listeners = new ListenerSet("utest_SSL", dsptch, this, this, "listeners/listener", srvcfg, cfgdflts);
			listeners.start();
			org.junit.Assert.assertEquals(1, listeners.configured());
			org.junit.Assert.assertEquals(1, listeners.count());
			srvport = listeners.getListener(0).getLocalPort();
		} else {
			lstnr = new ConcurrentListener("utest_SSL", dsptch, this, this, srvcfg, cfgdflts);
			srvport = lstnr.getLocalPort();
			lstnr.start();
		}

		// set up the client component
		SSLC clnt = new SSLC(dsptch, clntcfg, srvport);
		clnt.start(this);

		// set up a no-op Naflet which simply goes through the motions
		ctask = new SSLTask("utest_sslc", dsptch);
		dsptch.loadNaflet(ctask, dsptch);
		org.junit.Assert.assertTrue(ctask.started);

		// launch
		dsptch.start(); //Dispatcher launches in separate thread
		dsptch.waitStopped(); //we join() Dispatcher thread, so its memory changes will be visible on return
		org.junit.Assert.assertTrue(ctask.stopped);
		if (fail_step != -1) {
			//don't check client's progress, as it may have sent an extra message before it received disconnect
			org.junit.Assert.assertEquals(fail_step, lastsrv.step);
		} else {
			//this tests for successful completion, ie. it ran all the way through
			org.junit.Assert.assertEquals(iomessages.length+1, clnt.step);
			org.junit.Assert.assertEquals(iomessages.length+1, lastsrv.step);
		}
		org.junit.Assert.assertEquals(1, srvcnt);
		org.junit.Assert.assertEquals(1, reapcnt);
		org.junit.Assert.assertSame(clnt, lastreaped);
		if (listeners != null) {
			boolean done = listeners.getListener(0).stop(true);
			org.junit.Assert.assertTrue(done);
			org.junit.Assert.assertEquals(1, listeners.configured());
			org.junit.Assert.assertEquals(0, listeners.count());
			org.junit.Assert.assertEquals(1, srvcnt);
			org.junit.Assert.assertEquals(2, reapcnt);
			org.junit.Assert.assertSame(listeners, lastreaped);
			done = listeners.stop();
			org.junit.Assert.assertTrue(done);
		} else {
			boolean done = lstnr.stop(true);
			org.junit.Assert.assertTrue(done);
		}
	}

	@Override
	public void entityStopped(Object obj)
	{
		reapcnt++;
		lastreaped = obj;
		if (obj.getClass().equals(SSLC.class)) dsptch.setTimer(100, 0, this); //give server time to receive disconnect event
	}

	@Override
	public void timerIndication(Timer tmr, Dispatcher d) throws java.io.IOException {
		boolean done = ctask.stop();
		org.junit.Assert.assertTrue(done);
	}
	@Override
	public void eventError(Timer tmr, Dispatcher d, Throwable ex) {}

	private static void verifyReceivedMessage(String expected, com.grey.base.utils.ArrayRef<byte[]> actual)
	{
		org.junit.Assert.assertEquals('\n', actual.ar_buf[actual.ar_off+actual.ar_len-1]);
		actual.ar_len--;
		org.junit.Assert.assertEquals(expected.length(), actual.ar_len);
		org.junit.Assert.assertEquals(expected, new String(actual.ar_buf, actual.ar_off, actual.ar_len));
	}

	private static boolean verifySSL(ChannelMonitor cm, SSLConnectionTest harness, boolean havePeer, String expectedPeer)
			throws com.grey.base.FaultException, java.io.IOException
	{
		boolean switched = false;
		java.security.cert.X509Certificate peercert = cm.getPeerCertificate();
		if (cm.usingSSL()) {
			if (harness != null) harness.usedSSL = true;
			if (havePeer) {
				org.junit.Assert.assertNotNull(peercert);
				String cn = com.grey.base.crypto.SSLCertificate.getCN(peercert);
				org.junit.Assert.assertEquals(expectedPeer, cn);
			} else {
				org.junit.Assert.assertNull(peercert);
			}
		} else {
			if (cm.getSSLConfig().mdty) {
				org.junit.Assert.assertTrue(cm.getSSLConfig().latent);
				cm.startSSL();
				switched = true;
			}
			org.junit.Assert.assertNull(peercert);
		}
		return switched;
	}

	private static String resourcePath(String cp)
	{
		try {
			java.net.URL url = com.grey.base.utils.DynLoader.getResource(cp);
			return url.toURI().toString();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get resource-path="+cp+" - "+ex, ex);
		}
	}


	private static class SSLTask
		extends com.grey.naf.Naflet
	{
		public boolean started;
		public boolean stopped;

		public SSLTask(String name, Dispatcher dsptch)
				throws com.grey.base.GreyException, java.io.IOException {
			super(name, dsptch, null);
		}

		@Override
		protected void startNaflet() throws java.io.IOException {
			started = true;
		}

		@Override
		protected boolean stopNaflet() {
			stopped = true;
			return true;
		}
	}


	private static class SSLC extends ChannelMonitor
	{
		private final com.grey.naf.SSLConfig sslconfig;
		private final int srvport;
		private int step;
		private String expectedRsp;

		@Override
		protected com.grey.naf.SSLConfig getSSLConfig() {return sslconfig;}

		public SSLC(Dispatcher d, XmlConfig cfg, int port)
				throws com.grey.base.ConfigException, java.io.IOException, java.security.GeneralSecurityException
		{
			super(d);
			srvport = port;
			XmlConfig sslcfg = (cfg == null ? XmlConfig.NULLCFG : new XmlConfig(cfg, "ssl"));
			sslconfig = com.grey.naf.SSLConfig.create(sslcfg, null, null, true);
			com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(cfg, "niobuffers", 256, 128);
			chanreader = new IOExecReader(bufspec);
			chanwriter = new IOExecWriter(bufspec);
			org.junit.Assert.assertNotNull(getSSLConfig());
		}

		public void start(com.grey.naf.EntityReaper rpr) throws com.grey.base.FaultException, java.io.IOException, java.net.UnknownHostException
		{
			com.grey.base.utils.TSAP srvr = com.grey.base.utils.TSAP.build(null, srvport);
			setReaper(rpr);
			connect(srvr.sockaddr);
		}

		@Override
		protected void connected(boolean success, CharSequence diagnostic, Throwable ex) throws com.grey.base.FaultException, java.io.IOException
		{
			if (!success) {
				disconnect();
				return;
			}
			if (getSSLConfig().latent) {
				org.junit.Assert.assertFalse(usingSSL());
			} else {
				org.junit.Assert.assertTrue(usingSSL());
			}
			action();
			chanreader.receiveDelimited((byte)'\n');

			//for want of anywhere better to test these
			org.junit.Assert.assertEquals(srvport, getRemotePort());
			org.junit.Assert.assertFalse(srvport==getLocalPort());
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getRemoteIP()));
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getLocalIP()));
		}

		@Override
		public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata) throws com.grey.base.FaultException, java.io.IOException
		{
			verifyReceivedMessage(expectedRsp, rcvdata);
			if (!verifySSL(this, null, true, srv_certname)) {
				action();
			}
		}

		@Override
		protected void startedSSL() throws java.io.IOException
		{
			org.junit.Assert.assertTrue(usingSSL());
			action();
		}

		private void action() throws java.io.IOException
		{
			if (step == iomessages.length+1) {
				disconnect();
				return;
			}
			if (step == iomessages.length) {
				sendfile();
			} else {
				chanwriter.transmit(iomessages[step]+"\n");
			}
			step++;
			expectedRsp = "OK"+step;
		}

		private void sendfile() throws java.io.IOException
		{
			final String pthnam = rootdir+"/sendfile";
			final java.io.File fh = new java.io.File(pthnam);
			com.grey.base.utils.FileOps.ensureDirExists(fh.getParentFile());
			org.junit.Assert.assertFalse(fh.exists());

			final byte[] filebody = new byte[filesize];
			final int off = 10;
			final int lmt = off + filexmtsiz;
			org.junit.Assert.assertTrue(lmt < filesize); //sanity check
			java.util.Arrays.fill(filebody, (byte)0);
			java.util.Arrays.fill(filebody, off, lmt, filebyte);
			java.io.FileOutputStream ostrm = new java.io.FileOutputStream(fh, false);
			try {
				ostrm.write(filebody);
			} finally {
				ostrm.close();
			}
			org.junit.Assert.assertTrue(fh.exists());
			org.junit.Assert.assertEquals(filebody.length, fh.length());
			java.io.FileInputStream istrm = new java.io.FileInputStream(fh);
			java.nio.channels.FileChannel fchan = istrm.getChannel();
			chanwriter.transmit(fchan, off, lmt, false);
		}
	}


	public static class SSLS extends ConcurrentListener.Server
	{
		private final com.grey.naf.BufferSpec bufspec;
		private int step;
		private int filebytes;

		// This is the constructor for the prototype server object
		public SSLS(ConcurrentListener l, com.grey.base.config.XmlConfig cfg)
				throws com.grey.base.ConfigException, java.io.IOException, java.security.GeneralSecurityException
		{
			super(l);
			bufspec = new com.grey.naf.BufferSpec(cfg, "niobuffers", 8 * 1024, 128);
			org.junit.Assert.assertNotNull(getSSLConfig());
		}

		// This is the constructor for the active server objects, which actually participate in a TCP connnection
		private SSLS(SSLS proto)
		{
			super(proto.lstnr);
			bufspec = proto.bufspec;
			chanreader = new IOExecReader(bufspec);
			chanwriter = new IOExecWriter(bufspec);
			org.junit.Assert.assertNotNull(getSSLConfig());
		}

		// This method is only invoked in the prototype server object
		@Override
		public com.grey.base.utils.PrototypeFactory.PrototypeObject prototype_create()
		{
			return new SSLS(this);
		}

		@Override
		protected void connected() throws com.grey.base.FaultException, java.io.IOException
		{
			SSLConnectionTest harness = (SSLConnectionTest)lstnr.controller;
			harness.srvcnt++;
			harness.lastsrv = this;
			if (getSSLConfig().latent) {
				org.junit.Assert.assertFalse(usingSSL());
			} else {
				org.junit.Assert.assertTrue(usingSSL());
			}
			chanreader.receiveDelimited((byte)'\n');

			//for want of anywhere better to test these
			org.junit.Assert.assertEquals(harness.srvport, getLocalPort());
			org.junit.Assert.assertFalse(harness.srvport==getRemotePort());
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getRemoteIP()));
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(getLocalIP()));
		}

		@Override
		public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata) throws com.grey.base.FaultException, java.io.IOException
		{
			if (step == iomessages.length) {
				int lmt = rcvdata.ar_off + rcvdata.ar_len;
				for (int idx = rcvdata.ar_off; idx != lmt; idx++) {
					org.junit.Assert.assertEquals(filebyte, rcvdata.ar_buf[idx]);
				}
				filebytes += rcvdata.ar_len;
				if (filebytes != filexmtsiz) return;
			} else {
				verifyReceivedMessage(iomessages[step], rcvdata);
			}
			step++;
			chanwriter.transmit("OK"+(step)+"\n");
			if (step == iomessages.length) chanreader.receive(0);
			verifySSL(this, (SSLConnectionTest)lstnr.controller, getSSLConfig().peerCertName != null, clnt_certname);
		}

		@Override
		protected void startedSSL() throws java.io.IOException
		{
			org.junit.Assert.assertTrue(usingSSL());
		}
	}
}