/*
 * Copyright 2012-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import com.grey.logging.Logger;

public class ClientTCP
	extends com.grey.naf.reactor.CM_Client
	implements com.grey.naf.reactor.Timer.Handler
{
	private final ClientGroup grp;
	private final byte[] echobuf;
	private final String logpfx;

	private long time_start; //time at which this client started
	private int msgnum; //current message - 1 means on first message
	private int msgbytes; //number of bytes of current message echoed back so far
	private long time_xmit; //time at which current message was sent

	public ClientTCP(int id, ClientGroup g, com.grey.naf.BufferSpec bufspec, byte[] msgbuf)
	{
		super(g.dsptch, bufspec, bufspec);
		grp = g;
		echobuf = java.util.Arrays.copyOf(msgbuf, msgbuf.length);
		logpfx = "Client "+dsptch.name+"/"+id+": ";
	}

	public void start() {
		dsptch.setTimer(0, 0, this); //connect once Dispatcher starts up
	}

	@Override
	protected void connected(boolean success, CharSequence diag, Throwable ex) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!success) {
			Logger.LEVEL lvl = (ex instanceof java.io.IOException ? Logger.LEVEL.INFO : Logger.LEVEL.ERR);
			dsptch.logger.log(lvl, ex, lvl == Logger.LEVEL.ERR, logpfx+" TCP connect failed on "+grp.tsap);
			completed(false);
			return;
		}
		time_start = System.nanoTime();
		chanreader.receive(0);
		transmit();
	}

	@Override
	public void ioDisconnected(CharSequence diag)
	{
		dsptch.logger.info(logpfx+" Unsolicited disconnect - msgnum="+msgnum+"/"+grp.msgcnt+", msgbytes="+msgbytes+"/"+grp.echosize);
		try {
			completed(false);
		} catch (Exception ex) {
			dsptch.logger.error(logpfx+" Failed to signal ClientGroup - "+com.grey.base.GreyException.summary(ex));
		}
	}

	@Override
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data) throws com.grey.base.FaultException, java.io.IOException
	{
		if (grp.verify) {
			for (int idx = 0; idx != data.ar_len; idx++) {
				byte rcv = data.ar_buf[data.ar_off + idx];
				byte exp = echobuf[msgbytes + idx];
				if (rcv != exp) {
					dsptch.logger.info(logpfx+" Invalid reply@"+idx+"="+rcv+" vs "+exp
							+" - msgnum="+msgnum+"/"+grp.msgcnt+", msgbytes="+msgbytes+"/"+grp.echosize);
					completed(false);
					return;
				}
			}
		}
		msgbytes += data.ar_len;
		if (msgbytes != grp.echosize) return;
		grp.latencies.add(System.nanoTime() - time_xmit);

		// the message we sent has now been echoed back in full
		if (msgnum == grp.msgcnt) {
			// and we've sent the full complement of messages
			completed(true);
			return;
		}
		transmit();
	}

	private void transmit() throws java.io.IOException
	{
		msgnum++;
		msgbytes = 0;
		time_xmit = System.nanoTime();
		chanwriter.transmit(echobuf, 0, grp.echosize);
	}

	private void completed(boolean success)
	{
		disconnect();
		grp.terminated(success, System.nanoTime() - time_start);
	}

	@Override
	public void timerIndication(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d)
			throws com.grey.base.FaultException, java.io.IOException
	{
		initChannelMonitor();
		try {
			connect(grp.tsap.sockaddr);
		} catch (Throwable ex) {
			Logger.LEVEL lvl = (ex instanceof java.io.IOException ? Logger.LEVEL.INFO : Logger.LEVEL.ERR);
			dsptch.logger.log(lvl, ex, lvl == Logger.LEVEL.ERR, logpfx+" Failed to connect to "+grp.tsap);
			completed(false);
		}
	}

	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex) {}
}
