/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

public class ClientTCP
	extends Client
	implements com.grey.naf.reactor.Timer.Handler
{
	public ClientTCP(int id, ClientGroup g, com.grey.naf.BufferSpec bufspec)
			throws com.grey.base.FaultException, java.io.IOException
	{
		super(id, g);
		chanreader = new com.grey.naf.reactor.IOExecReader(bufspec);
		chanwriter = new com.grey.naf.reactor.IOExecWriter(bufspec);

		//connect once Dispatcher starts up
		dsptch.setTimer(0, 0, this);
	}

	@Override
	protected void connected(boolean success, CharSequence diag, Throwable ex) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!success) {
			dsptch.logger.info(logpfx+" TCP connect failed - "+grp.tsap);
			completed(false);
			return;
		}
		time_start = System.nanoTime();
		chanreader.receive(0);
		send();
	}

	@Override
	public void ioDisconnected(CharSequence diag)
	{
		dsptch.logger.info(logpfx+" Unsolicited disconnect - msgnum="+msgnum+"/"+grp.msgcnt+", msgbytes="+msgbytes+"/"+grp.msgbuf.length);
		try {
			completed(false);
		} catch (Exception ex) {
			dsptch.logger.error(logpfx+" Failed to signal ClientGroup - "+com.grey.base.GreyException.summary(ex));
		}
	}

	@Override
	protected void transmit() throws java.io.IOException
	{
		chanwriter.transmit(grp.msgbuf, 0, grp.msgbuf.length);
	}

	@Override
	public void timerIndication(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d)
			throws com.grey.base.FaultException, java.io.IOException
	{
		connect(grp.tsap.sockaddr);
	}

	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex) {}
}
