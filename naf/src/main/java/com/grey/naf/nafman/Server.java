/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.ByteOps;
import com.grey.base.utils.PrototypeFactory.PrototypeObject;

public class Server
	extends com.grey.naf.reactor.ConcurrentListener.Server
{
	private static final int S_RCV = 1;  //initial state, where we're waiting to receive the command from the client
	private static final int S_PROC = 2; //command has been accepted and is being processed
	private static final int S_DISCON = 3; //client has disconnected

	private final Primary primary;
	private Command cmd;
	private int state;

	//pre-allocated purely for efficiency
	private final ByteChars bctmp = new ByteChars(); 
	private final StringBuilder sbtmp = new StringBuilder();

	// This is the prototype object which the Listener uses to create the rest
	public Server(com.grey.naf.reactor.ConcurrentListener l, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException
	{
		super(l);
		primary = Primary.class.cast(lstnr.controller);
	}

	// This is (or will be) an active Server object
	private Server(Server proto)
	{
		super(proto.lstnr);
		primary = proto.primary;
		chanreader = new com.grey.naf.reactor.IOExecReader(primary.bufspec);
		chanwriter = new com.grey.naf.reactor.IOExecWriter(primary.bufspec);
	}

	@Override
	public PrototypeObject prototype_create()
	{
		return new Server(this);
	}

	// This is called by the Listener - we will signal it when we naturally complete this connection
	@Override
	public boolean stopServer()
	{
		return false;
	}

	@Override
	protected void connected() throws com.grey.base.FaultException, java.io.IOException
	{
		cmd = null;
		state = S_RCV;
		chanreader.receive(Command.HDRLEN, true);
	}

	@Override
	public void ioDisconnected()
	{
		int prev_state = state;
		state = S_DISCON;

		if (prev_state == S_PROC) {
			// the command is already in play, so wait for the Dispatchers to finish processing it
			return;
		}
		endConnection(null);
	}

	@Override
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data) throws com.grey.base.FaultException, java.io.IOException
	{
		sbtmp.setLength(0);
		sbtmp.append("Command rejected - ");
		if (cmd != null) {
			// we've just completed the second read - this one was the args
			if (!cmd.setArgs(data.ar_buf, data.ar_off, data.ar_len, sbtmp)) {
				endConnection(bctmp.set(sbtmp));
				return;
			}
		} else {
			// we've only read the header so far - there may be parameters to follow
			int cmdcode = data.ar_buf[data.ar_off] & 0xFF;
			int argslen = ByteOps.decodeInt(data.ar_buf, data.ar_off+1, Command.FLDLEN_ARGSLEN);
			Command.Def def = Registry.get().getCommand(cmdcode, sbtmp);

			if (def == null) {
				primary.dsptch.logger.debug("NAFMAN="+primary.dsptch.name+" discarding unrecogised command="+cmdcode);
				endConnection(bctmp.set(sbtmp));
				return;
			}
			cmd = primary.cmdstore.extract();
			cmd.set(def, this);

			if (argslen != 0) {
				chanreader.receive(argslen, true);
				return;
			}
			if (def.min_args != 0) {
				endConnection(bctmp.set("Command rejected - no parameters"));
				return;
			}
		}
		state = S_PROC;
		primary.forwardCommand(cmd);
	}

	protected void sendResponse()
	{
		ByteChars cmdrsp = cmd.getResponse();
		java.util.Collection<String> processedBy = cmd.getProcessedBy();
		ByteChars finalrsp = (cmdrsp == null ? bctmp.clear() : cmdrsp);
		finalrsp.append("\nNAFMAN has completed command=").append(cmd.getDescription());
		finalrsp.append("\nCommand was processed by Dispatchers=").append(String.valueOf(processedBy.size()));
		if (processedBy.size() != 0) finalrsp.append((byte)' ').append(processedBy.toString());
		endConnection(finalrsp);
	}

	private void endConnection(ByteChars response)
	{
		if (state != S_DISCON && response != null) {
			try {
				chanwriter.transmit(response.ar_buf, response.ar_off, response.ar_len);
			} catch (Exception ex) {
				primary.dsptch.logger.info("NAFMAN="+primary.dsptch.name+" failed to send response="+response.ar_len, ex);
			}
		}

		if (cmd != null) {
			cmd.clear();
			primary.cmdstore.store(cmd);
		}
		disconnect();
	}

	@Override
	public void eventError(com.grey.naf.reactor.ChannelMonitor cm, Throwable ex)
	{
		ioDisconnected();
	}
}
