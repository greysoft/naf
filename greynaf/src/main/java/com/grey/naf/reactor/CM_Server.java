/*
 * Copyright 2014-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.naf.EventListenerNAF;

public abstract class CM_Server extends CM_TCP
{
	private final CM_Listener lstnr;

	abstract protected void connected() throws java.io.IOException;

	protected boolean abortServer() {return false;} //returns True if termination completes before returning
	@Override
	protected com.grey.naf.reactor.config.SSLConfig getSSLConfig() {return lstnr.getSSLConfig();}

	public CM_Listener getListener() {return lstnr;}

	public CM_Server(CM_Listener l, com.grey.naf.BufferGenerator rbufspec, com.grey.naf.BufferGenerator wbufspec) {
		super(l.getDispatcher(), rbufspec, wbufspec);
		lstnr=l;
	}

	void accepted(java.nio.channels.SocketChannel sockchan, EventListenerNAF evtl)
			throws java.io.IOException
	{
		registerChannel(sockchan, true, true, false);
		setEventListener(evtl);

		if (isPureSSL()) {
			startSSL();
		} else {
			indicateConnection();
		}
	}
	
	@Override
	void indicateConnection() throws java.io.IOException
	{
		setFlagCM(S_APPCONN);
		connected();
	}
}