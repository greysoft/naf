/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.ByteArrayRef;
import com.grey.logging.Logger.LEVEL;

public abstract class CM_Stream extends ChannelMonitor
{
	private final IOExecReaderStream chanreader;
	private final IOExecWriter chanwriter;
	private SSLConnection sslconn;

	protected abstract void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException;

	protected com.grey.naf.SSLConfig getSSLConfig() {return null;}
	protected void startedSSL() throws java.io.IOException {}
	protected boolean usingSSL() {return sslconn != null;}
	protected java.security.cert.Certificate[] getPeerChain() {return sslconn == null ? null : sslconn.getPeerChain();}
	protected java.security.cert.X509Certificate getPeerCertificate() {return sslconn == null ? null : sslconn.getPeerCertificate();}

	public boolean isConnected() {return isFlagSetCM(S_ISCONN);}
	public boolean isBrokenPipe() {return isFlagSetCM(S_BRKPIPE);}

	protected IOExecReaderStream getReader() {return chanreader;}
	protected IOExecWriter getWriter() {return chanwriter;}
	SSLConnection sslConnection() {return sslconn;}

	void indicateConnection() throws java.io.IOException {}
	protected void disconnectLingerDone(boolean ok, CharSequence info, Throwable ex) {} //called later, if disconnect() returns False

	public CM_Stream(Dispatcher d, com.grey.naf.BufferSpec rbufspec, com.grey.naf.BufferSpec wbufspec)
	{
		super(d);
		chanreader = (rbufspec == null ? null : new com.grey.naf.reactor.IOExecReaderStream(rbufspec));
		chanwriter = (wbufspec == null ? null : new com.grey.naf.reactor.IOExecWriter(wbufspec));
	}

	protected void registerConnectedChannel(java.nio.channels.SelectableChannel chan, boolean takeOwnership)
		throws java.io.IOException
	{
		registerChannel(chan, takeOwnership, true, true);
	}

	void registerChannel(java.nio.channels.SelectableChannel chan, boolean takeOwnership, boolean isconn, boolean app_knows)
		throws java.io.IOException
	{
		registerChannel(chan, takeOwnership);
		if (isconn) setFlagCM(S_ISCONN);
		if (app_knows) setFlagCM(S_APPCONN);
		if (chanreader != null) chanreader.initChannel(this);
		if (chanwriter != null) chanwriter.initChannel(this);
	}

	@Override
	boolean shutdownChannel(boolean linger)
	{
		if (sslconn != null) {
			sslconn.close();
			sslconn = null;
		}
		if (chanwriter != null) {
			if (linger && chanwriter.isBlocked() && !isFlagSetCM(S_BRKPIPE)) {
				// Still waiting for a blocked write to complete, so linger-on-close till it does.
				// This is irrespective of the S_WECLOSE setting.
				setFlagCM(S_CLOSELINGER);
				return false;
			}
			chanwriter.clearChannel();
		}
		if (chanreader != null) chanreader.clearChannel();
		return true;
	}

	@Override
	void ioIndication(int readyOps) throws java.io.IOException
	{
		if ((readyOps & java.nio.channels.SelectionKey.OP_READ) != 0) {
			if (sslconn != null) {
				sslconn.handleRead();
				return;
			}
			if (chanreader != null) {
				chanreader.handleIO(null);
				return;
			}
		}

		if (chanwriter != null && ((readyOps & java.nio.channels.SelectionKey.OP_WRITE) != 0)) {
			chanwriter.handleIO();
			return;
		}
	}

	// The I/O operation is already over, so just swallow any exceptions.
	// They are probably due to a remote disconnect, and we can handle that later if/when we do any more I/O on this channel
	void transmitCompleted()
	{
		disableWrite();

		if (isFlagSetCM(S_CLOSELINGER)) {
			disconnectLingerDone(true, null, null); //notify app first
			disconnect(false); //now disconnect
			return;
		}
	}

	public void startSSL() throws java.io.IOException
	{
		if (!isFlagSetCM(S_APPCONN)) {
			// This is just to register as a reader with the Dispatcher.
			// If the application is already connected, we don't want to interfere with its chanreader settings.
			chanreader.receive(0);
		}
		sslconn = new SSLConnection(this);
		sslconn.start();
	}

	void sslStarted() throws java.io.IOException
	{
		if (isFlagSetCM(S_APPCONN)) {
			//the application must have switched into SSL mode after it established the connection
			startedSSL();
		} else {
			//a pure SSL connection has now established the SSL layer
			indicateConnection();
		}
	}

	void sslDisconnected(CharSequence diag) throws java.io.IOException
	{
		if (isFlagSetCM(S_APPCONN)) {
			ioDisconnected(diag);
		} else {
			disconnect();
		}
	}

	boolean isPureSSL()
	{
		com.grey.naf.SSLConfig sslcfg = getSSLConfig();
		return (sslcfg != null && !sslcfg.latent);
	}

	@Override
	StringBuilder dumpChannelState(StringBuilder sb, String dlm)
	{
		if (sb == null) sb = new StringBuilder();
		String wsts = (chanwriter == null ? "none" : (chanwriter.isBlocked() ? "blocked" : "ready"));
		sb.append(dlm).append("Reader=");
		if (chanreader == null) {
			sb.append("none");
		} else {
			chanreader.dumpState(sb, dlm);
		}
		sb.append(dlm).append("Writer=").append(wsts);
		sb.append("<br/>Endpoint: ").append(usingSSL()?"SSL/":"").append(getChannel());
		return sb;
	}

	void brokenPipe(LEVEL lvl, CharSequence discmsg, CharSequence errmsg, Throwable ex) throws BrokenPipeException
	{
		if (getLogger().isActive(lvl)) {
			getLogger().log(lvl, ex, false, getClass().getName()+"/E"+getCMID()+" "+errmsg
					+" - cmstate="+dumpMonitorState(false, null)+"/SSL="+usingSSL()
					+"; blocked="+(chanwriter != null && chanwriter.isBlocked()));
		}
		if (discmsg == null) discmsg = errmsg;
		setFlagCM(S_BRKPIPE);

		if (isFlagSetCM(S_CLOSELINGER)) {
			//Connection has been lost while we're lingering on close to flush a blocked IOExcWriter.
			//Clearly no point lingering now that connnection is lost (or we'd get stuck in infinite loop).
			//First, notify app that the connection it thought was closed has in fact failed.
			disconnectLingerDone(false, discmsg, ex);
			disconnect(false);
			return;
		}
		if (!isFlagSetCM(S_INDISC)) throw new BrokenPipeException(this, discmsg);
	}


	/*
	 * This exception tells the Dispatcher to call handler's eventError() AFTER unwinding the call chain, and
	 * without logging a big ugly stack dump.
	 * It is thrown in places where we used to call chanmon.ioDisconnected(), but that allowed the application to
	 * continue processing without being aware of the deeply nested re-entrant disconnect indication, and proved
	 * almost impossible to guard against - certainly not without inserting state checks after every call to
	 * IOExecWriter and IOExecReader.
	 */
	public static final class BrokenPipeException extends java.io.IOException
	{
		private static final long serialVersionUID = 1L;
		public final ChannelMonitor cm;
		BrokenPipeException(ChannelMonitor c, CharSequence msg) {super(msg.toString()); cm=c;}
	}
}