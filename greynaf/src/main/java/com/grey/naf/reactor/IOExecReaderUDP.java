/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;
import com.grey.naf.errors.NAFException;

public class IOExecReaderUDP
	extends IOExecReader
{
	IOExecReaderUDP(com.grey.naf.BufferGenerator spec)
	{
		super(spec);
	}

	public void receive() throws java.io.IOException
	{
		enableReceive();
	}

	void handleIO() throws java.io.IOException
	{
		if (getReceiveBuffer() == null) {
			((CM_UDP)getCM()).ioReceived(null, null);
			return;
		}
		java.net.InetSocketAddress remaddr;

		try {
			final java.nio.channels.DatagramChannel iochan = getCM().getDatagramChannel();
			getReceiveBuffer().clear();
			remaddr = (java.net.InetSocketAddress)iochan.receive(getReceiveBuffer());
		} catch (Exception ex) {
			if (ex instanceof java.net.PortUnreachableException) return;  //we've received associated ICMP packet - discard
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC3);
			if (getCM().getLogger().isActive(lvl)) {
				getCM().getLogger().log(lvl, ex, lvl==LEVEL.ERR, "IOExecUDP: read() failed on "+getCM().getClass().getName()+"/E"+getCM().getCMID()+"/"+getCM().getChannel());
			}
			return;
		}
		int nbytes = (remaddr == null ? 0 : getReceiveBuffer().position());
		if (nbytes == 0) return;

		if (!isFlagSet(F_ARRBACK)) {
			// rewind to start of the block we just read, to copy it - the get() will then restore rcvbuf position to where it was after read()
			getReceiveBuffer().position(0);
			getReceiveBuffer().get(getUserBuffer().buffer(), 0, nbytes);
		}
		getUserBuffer().setSize(nbytes);
		((CM_UDP)getCM()).ioReceived(getUserBuffer(), remaddr);
	}
}