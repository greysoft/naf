/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

public abstract class Client
	extends com.grey.naf.reactor.ChannelMonitor
{
	protected final ClientGroup grp;
	protected final String logpfx;

	protected int msgnum; //current message - 1 means on first message
	protected int msgbytes; //number of bytes of current message echoed back so far
	protected long time_start;

	abstract protected void transmit() throws java.io.IOException;

	public Client(int id, ClientGroup g)
			throws com.grey.base.FaultException, java.io.IOException
	{
		super(g.dsptch);
		grp = g;
		logpfx = "Client "+dsptch.name+"/"+id+": ";
	}

	protected void completed(boolean success) throws java.io.IOException
	{
		disconnect();
		grp.terminated(this, success, System.nanoTime() - time_start);
	}

	protected void send() throws java.io.IOException
	{
		msgnum++;
		msgbytes = 0;
		transmit();
	}

	@Override
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data) throws com.grey.base.FaultException, java.io.IOException
	{
		if (grp.verify) {
			for (int idx = 0; idx != data.ar_len; idx++) {
				if (data.ar_buf[data.ar_off + idx] != grp.msgbuf[msgbytes + idx]) {
					dsptch.logger.info(logpfx+" Invalid reply="+data.ar_buf[data.ar_off + idx]+" vs "+grp.msgbuf[msgbytes + idx]
							+" - msgnum="+msgnum+"/"+grp.msgcnt+", msgbytes="+msgbytes+"/"+grp.msgbuf.length);
					completed(false);
					return;
				}
			}
		}
		msgbytes += data.ar_len;
		if (msgbytes != grp.msgbuf.length) return;

		// the message we sent has now been echoed back in full
		if (msgnum == grp.msgcnt) {
			// and we've sent the full complement of messages
			completed(true);
			return;
		}
		send();
	}

	@Override
	public void ioDisconnected(CharSequence diag)
	{
		dsptch.logger.info(logpfx+" Unsolicited disconnect - msgnum="+msgnum+"/"+grp.msgcnt+", msgbytes="+msgbytes+"/"+grp.msgbuf.length);
		disconnect();
		try {
			completed(false);
		} catch (Exception ex) {
			dsptch.logger.error(logpfx+" Failed to signal ClientGroup - "+com.grey.base.GreyException.summary(ex));
		}
	}
}
