/*
 * Copyright 2014-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class IOExecReaderUDP
	extends IOExecReader
{
	IOExecReaderUDP(com.grey.naf.BufferSpec spec)
	{
		super(spec);
		if (userbuf != null) userbuf.ar_off = rcvbuf0;
	}

	public void receive() throws java.io.IOException
	{
		enableReceive();
	}

	void handleIO() throws com.grey.base.FaultException, java.io.IOException
	{
		if (rcvbuf == null) {
			((CM_UDP)chanmon).ioReceived(null, null);
			return;
		}
		java.net.InetSocketAddress remaddr;

		try {
			final java.nio.channels.DatagramChannel iochan = (java.nio.channels.DatagramChannel)chanmon.iochan;
			rcvbuf.clear();
			remaddr = (java.net.InetSocketAddress)iochan.receive(rcvbuf);
		} catch (Exception ex) {
			if (ex instanceof java.net.PortUnreachableException) return;  //we've received associated ICMP packet - discard
			LEVEL lvl = (ex instanceof RuntimeException ? LEVEL.ERR : LEVEL.TRC3);
			if (chanmon.dsptch.logger.isActive(lvl)) {
				chanmon.dsptch.logger.log(lvl, ex, lvl==LEVEL.ERR, "IOExecUDP: read() failed on "+chanmon.getClass().getName()+"/E"+chanmon.getCMID()+"/"+chanmon.iochan);
			}
			chanmon.ioDisconnected("Datagram read failed");
			return;
		}
		int nbytes = (remaddr == null ? 0 : rcvbuf.position());
		if (nbytes == 0) return;

		if (!isFlagSet(F_ARRBACK)) {
			// rewind to start of the block we just read, to copy it - the get() will then restore rcvbuf position to where it was after read()
			rcvbuf.position(0);
			rcvbuf.get(userbuf.ar_buf, 0, nbytes);
		}
		userbuf.ar_len = nbytes;
		((CM_UDP)chanmon).ioReceived(userbuf, remaddr);
	}
}