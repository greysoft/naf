/*
 * Copyright 2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.IP;

// This tests the UDP mode of IOExecReader
public class UDPReaderTest
{
	private static final String rootdir = DispatcherTest.initPaths(UDPReaderTest.class);
	private static final String[] iomessages = new String[]{"Message 1", "Message 2", "Here is another message", "The final message"};

	@org.junit.Test
	public void test() throws com.grey.base.GreyException, java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);

		// set up Dispatcher
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(def, null, com.grey.logging.Factory.getLogger("no-such-logger"));

		// set up UDP reader
		Reader rdr = new Reader(dsptch);
		org.junit.Assert.assertFalse(rdr.isConnected());

		// queue up incoming messages on the UDP reader
		java.net.DatagramSocket wsock = new java.net.DatagramSocket();
        byte[] ipbytes = IP.ip2net(IP.IP_LOCALHOST, null, 0);
        java.net.InetAddress ipaddr = java.net.InetAddress.getByAddress(ipbytes);
		java.net.DatagramPacket pkt = new java.net.DatagramPacket(new byte[0], 0, ipaddr, rdr.getLocalPort());
		wsock.send(pkt);
		for (int idx = 0; idx != iomessages.length; idx++) {
			pkt.setData(iomessages[idx].getBytes());
			wsock.send(pkt);
		}
		int senderPort = wsock.getLocalPort();
		rdr.senderPort = senderPort;

		// launch
		dsptch.start();
		dsptch.waitStopped();
		org.junit.Assert.assertTrue(rdr.completed);
		org.junit.Assert.assertFalse(rdr.isConnected());
	}


	private static class Reader
		extends ChannelMonitor
	{
		public boolean completed;
		private final java.nio.channels.DatagramChannel udpchan;
		private int msgcnt;
		private int senderPort;

		public Reader(Dispatcher d) throws com.grey.base.GreyException, java.io.IOException
		{
			super(d);
			udpchan = java.nio.channels.DatagramChannel.open();
			java.net.DatagramSocket sock = udpchan.socket();
			sock.bind(null);

			com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(1024, 1024, false);
			chanreader = new IOExecReader(bufspec);
			initChannel(udpchan, true, false);
			chanreader.receive(0);
		}

		@Override
		public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data, java.net.InetSocketAddress remaddr)
				throws com.grey.base.FaultException, java.io.IOException
		{
			org.junit.Assert.assertEquals(iomessages[msgcnt++], new String(data.ar_buf, data.ar_off, data.ar_len));
			org.junit.Assert.assertEquals(IP.IP_LOCALHOST, IP.convertIP(remaddr.getAddress()));
			org.junit.Assert.assertEquals(senderPort, remaddr.getPort());
			if (msgcnt == iomessages.length) {
				completed = true;
				disconnect();
				dsptch.stop(null);
			}
		}
	}
}