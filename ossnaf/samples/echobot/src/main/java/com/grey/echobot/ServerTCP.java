/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import com.grey.base.utils.PrototypeFactory.PrototypeObject;

public class ServerTCP
	extends com.grey.naf.reactor.ConcurrentListener.Server
{
	private final App app;

	// This is the prototype object which the Listener uses to create the rest
	public ServerTCP(com.grey.naf.reactor.ConcurrentListener l, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException
	{
		super(l);
		this.app = App.class.cast(lstnr.controller);
	}

	// This is (or will be) an active Server object
	private ServerTCP(ServerTCP proto)
	{
		super(proto.lstnr);
		app = proto.app;
		chanreader = new com.grey.naf.reactor.IOExecReader(app.sbufspec);
		chanwriter = new com.grey.naf.reactor.IOExecWriter(app.sbufspec);
	}

	// This is (or will be) an active Server object
	@Override
	public PrototypeObject prototype_create()
	{
		return new ServerTCP(this);
	}

	@Override
	public boolean stopServer()
	{
		return false;
	}

	@Override
	protected void connected() throws com.grey.base.FaultException, java.io.IOException
	{
		chanreader.receive(0);
	}

	@Override
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data) throws java.io.IOException
	{
		chanwriter.transmit(data.ar_buf, data.ar_off, data.ar_len);
	}

	@Override
	public void eventError(com.grey.naf.reactor.ChannelMonitor cm, Throwable ex) 
	{
		try {
			ioDisconnected("I/O handler error");
		} catch (Exception ex2) {
			dsptch.logger.error("Server failed to signal Disconnect - "+com.grey.base.GreyException.summary(ex2)
					+" - Due to "+com.grey.base.GreyException.summary(ex));
		}
	}
}
