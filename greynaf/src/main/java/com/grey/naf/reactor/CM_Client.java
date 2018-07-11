/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public abstract class CM_Client extends CM_TCP
{
	protected abstract void connected(boolean success, CharSequence diagnostic, Throwable error)
			throws java.io.IOException;

	public CM_Client(Dispatcher d, com.grey.naf.BufferSpec rbufspec, com.grey.naf.BufferSpec wbufspec)
	{
		super(d, rbufspec, wbufspec);
	}

	@Override
	void indicateConnection() throws java.io.IOException
	{
		setFlagCM(S_APPCONN);
		connected(true, null, null);
	}
	
	@Override
	void ioIndication(int readyOps) throws java.io.IOException
	{
		if ((readyOps & java.nio.channels.SelectionKey.OP_CONNECT) != 0) {
			boolean success = true;
			Throwable exconn = null;
			try {
				java.nio.channels.SocketChannel sock = (java.nio.channels.SocketChannel)getChannel();
				if (!sock.finishConnect()) return; //don't expect False return to ever happen, but do the check anyway
				disableConnect();
			} catch (Throwable ex) {
				//JDK seems to mark SelectionKey as invalid when finishConnect() throws!
				success = false;
				exconn = ex;
			}
			clientConnected(success, exconn);
			return;
		}
		super.ioIndication(readyOps);
	}

	public void connect(java.net.InetSocketAddress remaddr) throws java.io.IOException
	{
		if (!isFlagSetCM(S_INIT)) {
			//subclasses must call initChannelMonitor() before each call to connect()
			throw new IllegalStateException("CM_Client instances must init before connect() - state="+dumpMonitorState(false, null)+" - "+this);
		}

		if (getChannel() != null) {
			// We're being reused to make a new connection - probably means initial connection attempt failed
			disconnect(false, true);
		}
		java.nio.channels.SocketChannel sockchan = java.nio.channels.SocketChannel.open();
		registerChannel(sockchan, true, false, false);

		// NB: This bloody method can only report connection failure by throwing - either here or in finishConnect()
		try {
			if (sockchan.connect(remaddr)) {
				clientConnected(true, null);
				return;
			}
		} catch (Throwable ex) {
			clientConnected(false, ex);
			return;
		}
		enableConnect();
	}

	private void clientConnected(boolean success, Throwable ex) throws java.io.IOException
	{
		if (success) {
			setFlagCM(S_ISCONN);
			if (isPureSSL()) {
				startSSL();
			} else {
				indicateConnection();
			}
		} else {
			connected(false, null, ex);
		}
	}

	@Override
	void sslDisconnected(CharSequence diag) throws java.io.IOException
	{
		if (isFlagSetCM(S_APPCONN)) {
			super.sslDisconnected(diag);
		} else {
			connected(false, diag, null);
		}
	}
}