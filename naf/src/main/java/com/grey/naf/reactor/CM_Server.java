/*
 * Copyright 2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public abstract class CM_Server extends CM_TCP
{
	public final CM_Listener lstnr;

	abstract protected void connected() throws com.grey.base.FaultException, java.io.IOException;

	protected boolean abortServer() {return false;} //returns True if termination completes before returning
	@Override
	protected com.grey.naf.SSLConfig getSSLConfig() {return lstnr.getSSLConfig();}

	public CM_Server(CM_Listener l, com.grey.naf.BufferSpec rbufspec, com.grey.naf.BufferSpec wbufspec) {
		super(l.dsptch, rbufspec, wbufspec);
		lstnr=l;
	}

	final void accepted(java.nio.channels.SocketChannel sockchan, com.grey.naf.EntityReaper rpr)
			throws com.grey.base.FaultException, java.io.IOException
	{
		registerChannel(sockchan, true, true, false);
		setReaper(rpr);

		if (isPureSSL()) {
			startSSL();
		} else {
			indicateConnection();
		}
	}
	
	@Override
	final void indicateConnection() throws com.grey.base.FaultException, java.io.IOException
	{
		setFlag(S_APPCONN);
		connected();
	}
}