/*
 * Copyright 2012-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

public class ServerTCP
	extends com.grey.naf.reactor.CM_Server
{
	public static final class Factory
		implements com.grey.naf.reactor.ConcurrentListener.ServerFactory
	{
		private final com.grey.naf.reactor.CM_Listener lstnr;

		@Override
		public ServerTCP factory_create() {return new ServerTCP(lstnr);}
		@Override
		public Class<ServerTCP> getServerClass() {return ServerTCP.class;}
		@Override
		public void shutdown() {}

		public Factory(com.grey.naf.reactor.CM_Listener l, com.grey.base.config.XmlConfig cfg) {lstnr = l;}
	}


	ServerTCP(com.grey.naf.reactor.CM_Listener l)
	{
		super(l, App.class.cast(l.controller).sbufspec, App.class.cast(l.controller).sbufspec);
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
}