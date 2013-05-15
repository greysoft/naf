/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger.LEVEL;

public abstract class ChannelMonitor
{
	private static final boolean halfduplex = SysProps.get("greynaf.io.halfduplex", false);

	private static final int S_ISCONN = 1 << 0; //the iochan endpoint is connected to its remote peer
	private static final int S_APPCONN = 1 << 1; //the application has been notified that we are connected
	private static final int S_WECLOSE = 1 << 2;
	private static final int S_CLOSELINGER = 1 << 3;
	private static final int S_INREAD = 1 << 4;
	private static final int S_INWRITE = 1 << 5;
	private static final int S_UDP = 1 << 6;
	private static final int S_INDISC = 1 << 7;

	public final int cm_id;
	public final Dispatcher dsptch;
	public java.nio.channels.SelectableChannel iochan;
	protected IOExecReader chanreader;
	protected IOExecWriter chanwriter;
	java.nio.channels.SelectionKey regkey;
	SSLConnection sslconn;

	private byte cmstate;  //records which of the S_... state flags above are in effect
	private byte regOps;   //JDK flags - shadows/mirrors regkey.interestOps()
	private com.grey.naf.EntityReaper reaper;
	private long start_time;

	// Most of our virtual methods are conditionally optional. That is, the subclass doesn't have to implement them unless it makes
	// use of functionality which invokes them.
	// For example: If monitoring for Read, then ioIndication() must be provided if the subclass has not defined an IOExecReader,
	// and ioReceived() must be defined if it has.
	// It wouldn't make sense to subclass this if you weren't interested in monitoring anything at all, whether that be Read events,
	// incoming connections, or the conclusion of outgoing connections, hence this class has been declared as abstract even though no
	// particular method is actually abstract.
	protected void ioDisconnected(CharSequence diagnostic)
			throws java.io.IOException {disconnect(false);}
	protected void ioTransmitted()
			throws com.grey.base.FaultException, java.io.IOException {}
	protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata, java.net.InetSocketAddress remaddr)
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("UDP CM.ioReceived() not implemented");}
	protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata)
			throws com.grey.base.FaultException, java.io.IOException {ioReceived(rcvdata, null);}
	protected void ioIndication(int ops)
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("CM.ioIndication() not implemented - Ops=0x"+Integer.toHexString(ops));}
	// client mode - this indicates the completion of a connect() call by us
	protected void connected(boolean success, CharSequence diagnostic, Throwable ex)
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("Client-CM.connected() not implemented");}
	// server mode - this indicates we've accepted a connection from a remote client
	protected void connected()
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("Server-CM.connected() not implemented");}
	protected void initServer() {} //called before connected() for TCP servers, irrelevant for all other entities
	protected void disconnectLingerDone(boolean ok, CharSequence info, Throwable ex) {} //called later, if disconnect() returns False
	protected void eventError(ChannelMonitor cm, Throwable ex) {}
	protected void dumpAppState(StringBuilder sb) {}

	public final boolean isConnected() {return isFlagSet(S_ISCONN);}
	public final boolean isUDP() {return isFlagSet(S_UDP);}
	public final boolean disconnect() {return disconnect(true);}
	protected final void setReaper(com.grey.naf.EntityReaper rpr) {reaper = rpr;}
	final boolean canKill() {return (isConnected() && !getClass().equals(Producer.AlertsPipe.class));}
	final long getStartTime() {return start_time;}

	protected final boolean usingSSL() {return sslconn != null;}
	protected final java.security.cert.Certificate[] getPeerChain() {return sslconn == null ? null : sslconn.getPeerChain();}
	protected final java.security.cert.X509Certificate getPeerCertificate() {return sslconn == null ? null : sslconn.getPeerCertificate();}
	protected void startedSSL() throws com.grey.base.FaultException, java.io.IOException {}
	protected com.grey.naf.SSLConfig getSSLConfig() {return null;}

	private final boolean isFlagSet(int f) {return ((cmstate & f) != 0);}
	private void setFlag(int f) {cmstate |= f;}
	private void clearFlag(int f) {cmstate &= ~f;}

	protected ChannelMonitor(Dispatcher d)
	{
		dsptch = d;
		cm_id = dsptch.allocateChannelId();
	}

	protected final void initChannel(java.nio.channels.SelectableChannel chan, boolean takeOwnership, boolean isconn) throws java.io.IOException
	{
		iochan = chan;
		sslconn = null;
		regkey = null;
		regOps = 0;
		cmstate = (isconn ? (byte)S_ISCONN : 0);
		if (takeOwnership) setFlag(S_WECLOSE);
		if (iochan instanceof java.nio.channels.DatagramChannel) setFlag(S_UDP);
		iochan.configureBlocking(false);
		dsptch.registerIO(this);
		if (chanreader != null) chanreader.initChannel(this);
		if (chanwriter != null) chanwriter.initChannel(this);
		start_time = dsptch.systime(); //catches clients, servers and listeners
	}

	public final void connect(java.net.InetSocketAddress remaddr) throws com.grey.base.FaultException, java.io.IOException
	{
		if (iochan != null) {
			// We're being reused to make a new connection - probably means initial connection attempt failed
			com.grey.naf.EntityReaper rpr = reaper;
			reaper = null;
			disconnect(false);
			reaper = rpr;
		}
		java.nio.channels.SocketChannel sockchan = java.nio.channels.SocketChannel.open();
		initChannel(sockchan, true, false);

		// NB: This bloody method can only report connection failure by throwing - either here or in finishConnect()
		if (sockchan.connect(remaddr)) {
			clientConnected(true, null);
			return;
		}
		monitorIO(regOps | java.nio.channels.SelectionKey.OP_CONNECT);
	}

	private final void clientConnected(boolean success, Throwable ex) throws com.grey.base.FaultException, java.io.IOException
	{
		if (success) {
			setFlag(S_ISCONN);
			if (isPureSSL()) {
				startSSL();
			} else {
				indicateConnection(true);
			}
		} else {
			connected(false, null, ex);
		}
	}

	final void accepted(java.nio.channels.SocketChannel sockchan, com.grey.naf.EntityReaper rpr)
			throws com.grey.base.FaultException, java.io.IOException
	{
		initChannel(sockchan, true, true);
		setReaper(rpr);
		initServer();

		if (isPureSSL()) {
			startSSL();
		} else {
			indicateConnection(false);
		}
	}

	private final void indicateConnection(boolean isClient)
			throws com.grey.base.FaultException, java.io.IOException
	{
		setFlag(S_APPCONN);
		if (isClient) {
			connected(true, null, null);
		} else {
			connected();
		}
	}

	public final boolean disconnect(boolean linger)
	{
		//avoid re-entrancy, ie. calling ourself recursively due to a failure in these disconnect ops
		if (isFlagSet(S_INDISC)) return false;
		setFlag(S_INDISC);

		if (iochan != null) {
			if (sslconn != null) {
				sslconn.close();
				sslconn = null;
			}
			if (chanwriter != null) {
				if (linger && !isFlagSet(S_CLOSELINGER) && chanwriter.isBlocked()) {
					// Still waiting for a write to complete, so linger-on-close till it does.
					// This is irrespective of the S_WECLOSE setting.
					// If we were already lingering, then the repeated disconnect call means no-linger.
					setFlag(S_CLOSELINGER);
					clearFlag(S_INDISC); //enable future call when writer has drained
					return false;
				}
				chanwriter.clearChannel();
			}
			if (chanreader != null) chanreader.clearChannel();

			try {
				dsptch.deregisterIO(this);
				if (isFlagSet(S_WECLOSE)) iochan.close();
			} catch (Exception ex) {
				dsptch.logger.log(LEVEL.ERR, ex, true, "Dispatcher="+dsptch.name+": Failed to close ChannelMonitor=E"+cm_id+"/"+iochan
						+" in state=0x"+Integer.toHexString(cmstate));
			}
			iochan = null;
		}
		cmstate = 0; //need to reset this, as isConnected() may subsequently get called

		if (reaper != null) {
			com.grey.naf.EntityReaper rpr = reaper;
			reaper = null;
			rpr.entityStopped(this);
		}
		return true;
	}

	public final void startSSL() throws com.grey.base.FaultException, java.io.IOException
	{
		if (!isFlagSet(S_APPCONN)) {
			// This is just to register as a reader with the Dispatcher.
			// If the application is already connected, we don't want to interfere with its chanreader settings.
			chanreader.receive(0);
		}
		sslconn = new SSLConnection(this);
		sslconn.start();
	}

	final void sslStarted() throws com.grey.base.FaultException, java.io.IOException
	{
		if (isFlagSet(S_APPCONN)) {
			//the application must have switched into SSL mode after it established the connection
			startedSSL();
		} else {
			//a pure SSL connection has now established the SSL layer
			indicateConnection(getSSLConfig().isClient);
		}
	}

	final void sslDisconnected(CharSequence diag) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!getSSLConfig().isClient || isFlagSet(S_APPCONN)) {
			ioDisconnected(diag);
		} else {
			connected(false, diag, null);
		}
	}

	private final boolean isPureSSL()
	{
		com.grey.naf.SSLConfig sslcfg = getSSLConfig();
		return (sslcfg != null && !sslcfg.latent);
	}

	final void transmitCompleted() throws com.grey.base.FaultException, java.io.IOException
	{
		if (isFlagSet(S_CLOSELINGER)) {
			disconnectLingerDone(true, null, null); //notify app first
			disconnect(false); //now disconnect
			return;
		}
		ioTransmitted();
	}

	// We should not receive Read events after disabling OP_READ, and same goes for Write/OP_WRITE, but beware that some IO events could
	// have already fired before we disabled them. and we're just receiving those IO event later on in the same Dispatcher cycle.
	// We therefore have to filter out our current Interest set from the supplied Ready set.
	// Furthermore, if we do have a Reader then we must never pass Read events up to the Owner even if reads are currently disabled, and
	// the same goes for Writer.
	// Note that even if both Reader and Writer events have been simultaneously signalled to us, we only handle one of them in a single
	// call, in case its handler drastically changes our state.
	final void handleIO(int readyOps) throws com.grey.base.FaultException, java.io.IOException
	{
		int validReadyOps = readyOps & regOps;

		if ((validReadyOps & java.nio.channels.SelectionKey.OP_READ) != 0) {
			if (sslconn != null) {
				sslconn.handleIO();
				return;
			}
			if (chanreader != null) {
				chanreader.handleIO();
				return;
			}
		}

		if (chanwriter != null && ((validReadyOps & java.nio.channels.SelectionKey.OP_WRITE) != 0)) {
			chanwriter.handleIO();
			return;
		}

		if ((validReadyOps & java.nio.channels.SelectionKey.OP_CONNECT) != 0) {
			boolean success = true;
			Throwable exconn = null;
			try {
				java.nio.channels.SocketChannel sock = (java.nio.channels.SocketChannel)iochan;
				if (!sock.finishConnect()) return;  // don't expect False return to ever happen, but do the check anyway
				monitorIO(regOps & ~java.nio.channels.SelectionKey.OP_CONNECT);
			} catch (Throwable ex) {
				success = false;
				exconn = ex;
			}
			clientConnected(success, exconn);
			return;
		}

		if (validReadyOps != 0) ioIndication(validReadyOps);
	}

	public final boolean enableRead() throws java.io.IOException
	{
		setFlag(S_INREAD);
		if (halfduplex && isFlagSet(S_INWRITE)) return false;
		return monitorIO(regOps | java.nio.channels.SelectionKey.OP_READ);
	}

	public final boolean disableRead() throws java.io.IOException
	{
		clearFlag(S_INREAD);
		return monitorIO(regOps & ~java.nio.channels.SelectionKey.OP_READ);
	}

	// Writes mask Reads, so disable any existing Read interest
	public final boolean enableWrite() throws java.io.IOException
	{
		setFlag(S_INWRITE);
		int opflags = (regOps | java.nio.channels.SelectionKey.OP_WRITE);
		if (halfduplex) opflags &= ~java.nio.channels.SelectionKey.OP_READ;
		return monitorIO(opflags);
	}

	public final boolean disableWrite() throws java.io.IOException
	{
		clearFlag(S_INWRITE);
		int opflags = regOps & ~java.nio.channels.SelectionKey.OP_WRITE;
		// if in half-duplex mode, restore the read that was blocked by this write
		if (halfduplex && isFlagSet(S_INREAD)) opflags |= java.nio.channels.SelectionKey.OP_READ;
		return monitorIO(opflags);
	}

	public final boolean enableListen() throws java.io.IOException
	{
		return monitorIO(regOps | java.nio.channels.SelectionKey.OP_ACCEPT);
	}

	public final boolean disableListen() throws java.io.IOException
	{
		return monitorIO(regOps & ~java.nio.channels.SelectionKey.OP_ACCEPT);
	}

	private final boolean monitorIO(int opflags) throws java.io.IOException
	{
		if (opflags == regOps || iochan == null) return false;
		regOps = (byte)opflags;	
		dsptch.monitorIO(this, regOps);
		return true;
	}

	public int getLocalPort() {
		if (isFlagSet(S_UDP)) return ((java.nio.channels.DatagramChannel)iochan).socket().getLocalPort();
		return ((java.nio.channels.SocketChannel)iochan).socket().getLocalPort();
	}
	public int getRemotePort() {
		if (isFlagSet(S_UDP)) return ((java.nio.channels.DatagramChannel)iochan).socket().getPort();
		return ((java.nio.channels.SocketChannel)iochan).socket().getPort();
	}
	public java.net.InetAddress getLocalIP() {
		if (isFlagSet(S_UDP)) return ((java.nio.channels.DatagramChannel)iochan).socket().getLocalAddress();
		return ((java.nio.channels.SocketChannel)iochan).socket().getLocalAddress();
	}
	public java.net.InetAddress getRemoteIP() {
		if (isFlagSet(S_UDP)) return ((java.nio.channels.DatagramChannel)iochan).socket().getInetAddress();
		return ((java.nio.channels.SocketChannel)iochan).socket().getInetAddress();
	}

	final void dumpState(StringBuilder sb, boolean verbose)
	{
		boolean is_producer = false;
		int prevlen = sb.length();
		sb.append("ID=").append(cm_id).append(": ");
		if (Listener.class.isInstance(this)) {
			sb.append(getClass().getSimpleName()).append('/').append(((Listener)this).getServerType().getName());
		} else if (getClass().equals(Producer.AlertsPipe.class)) {
			if (!verbose) {
				sb.setLength(prevlen);
				return;
			}
			is_producer = true;
			sb.append("Producer/").append(((Producer.AlertsPipe)this).producer.consumerType);
		} else {
			sb.append(getClass().getName());
		}
		sb.append("<br/>State=");
		if (isFlagSet(S_UDP)) sb.append('U');
		if (isFlagSet(S_ISCONN)) sb.append('C');
		if (isFlagSet(S_INDISC)) sb.append('D');
		if (isFlagSet(S_APPCONN)) sb.append('A');
		if (isFlagSet(S_WECLOSE)) sb.append('X');
		if (isFlagSet(S_CLOSELINGER)) sb.append('L');
		if (isFlagSet(S_INREAD)) sb.append('R');
		if (isFlagSet(S_INWRITE)) sb.append('W');
		if (chanwriter != null && chanwriter.isBlocked()) sb.append("/blocked");
		if (usingSSL()) sb.append("/SSL");
		int jdkOps = 0;
		sb.append(" Ops=");
		if (regkey == null) {
			sb.append("Null");
		} else {
			jdkOps = regkey.interestOps();
			dumpInterestOps(jdkOps, sb);
		}
		if (regOps != jdkOps) {
			//should never happen
			sb.append("/RegOps=");
			dumpInterestOps(regOps, sb);
		}
		sb.append(" Exec=").append(chanreader==null?"":"R").append(chanwriter==null?"":"W");
		if (is_producer) return; //remainder of info is boring and repetitive

		sb.append("<br/>Endpoint: ").append(iochan);
		if (isFlagSet(S_UDP)) sb.append("/UDP-Port=").append(getLocalPort()); //JDK toString() not very helpful in this case

		int prevlen1 = sb.length();
		sb.append("<br/><span class=\"cmapp\">App: ");
		int prevlen2 = sb.length();
		dumpAppState(sb);
		if (sb.length() == prevlen2) {
			sb.setLength(prevlen1);
		} else {
			sb.append("</span>");
		}
		sb.append("<br/>Since ");
		dsptch.dtcal.setTimeInMillis(start_time);
		TimeOps.makeTimeLogger(dsptch.dtcal, sb, true, false);
	}

	final void brokenPipe(LEVEL lvl, CharSequence discmsg, CharSequence errmsg, Throwable ex) throws BrokenPipeException
	{
		if (dsptch.logger.isActive(lvl)) {
			dsptch.logger.log(lvl, ex, false, "E"+cm_id+": "+errmsg
					+" - cmstate=0x"+Integer.toHexString(cmstate)
					+"; blocked="+(chanwriter != null && chanwriter.isBlocked()));
		}
		if (isFlagSet(S_CLOSELINGER)) {
			//Connection has been lost while we're lingering on close to flush a blocked IOExcWriter.
			//Clearly no point lingering now that connnection is lost (or we'd get stuck in infinite loop).
			//First, notify app that the connection it thought was closed has in fact failed.
			disconnectLingerDone(false, discmsg, ex);
			disconnect(false);
			return;
		}
		//Make sure we don't enter linger-on-close phase when disconnect() is subsequently called, since as
		//we said above, there's no point now.
		setFlag(S_CLOSELINGER);
		if (!isFlagSet(S_INDISC)) throw new BrokenPipeException(discmsg);
	}

	private static void dumpInterestOps(int ops, StringBuilder sb)
	{
		if ((ops & java.nio.channels.SelectionKey.OP_READ) != 0) sb.append('R');
		if ((ops & java.nio.channels.SelectionKey.OP_WRITE) != 0) sb.append('W');
		if ((ops & java.nio.channels.SelectionKey.OP_ACCEPT) != 0) sb.append('A');
		if ((ops & java.nio.channels.SelectionKey.OP_CONNECT) != 0) sb.append('C');
	}


	/*
	 * This exception tells the Dispatcher to call handler's eventError() AFTER unwinding the call chain, and
	 * without logging a big ugly stack dump.
	 * It is thrown in places where we used to call chanmon.ioDisconnected(), but that allowed the application to
	 * continue processing without being aware of the deeply nested re-entrant disconnect indication, and proved
	 * almost impossible to guard against - certainly not without inserting state checks after every call to
	 * IOExecWriter and IOExecReader.
	 */
	public static class BrokenPipeException extends java.io.IOException
	{
		private static final long serialVersionUID = 1L;
		public BrokenPipeException(CharSequence msg) {super(msg.toString());}
	}
}