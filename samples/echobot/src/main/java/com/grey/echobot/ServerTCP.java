/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import org.slf4j.LoggerFactory;

import com.grey.base.utils.ByteArrayRef;

public class ServerTCP
	extends com.grey.naf.reactor.CM_Server
{
	private static final org.slf4j.Logger Logger = LoggerFactory.getLogger(ServerTCP.class);

	public static final class Factory
		implements com.grey.naf.reactor.CM_Listener.ServerFactory
	{
		private final com.grey.naf.reactor.CM_Listener lstnr;
		public Factory(com.grey.naf.reactor.CM_Listener l, Object cfg) {lstnr = l;}
		@Override
		public ServerTCP createServer() {return new ServerTCP(lstnr);}
	}


	ServerTCP(com.grey.naf.reactor.CM_Listener l)
	{
		super(l, App.class.cast(l.getController()).sbufspec, App.class.cast(l.getController()).sbufspec);
		Logger.info("Created TCP server="+this);
	}

	@Override
	protected void connected() throws java.io.IOException
	{
		Logger.info("Connected - "+this);
		getReader().receive(0);
	}

	@Override
	public void ioReceived(ByteArrayRef data) throws java.io.IOException
	{
		getWriter().transmit(data);
	}
}