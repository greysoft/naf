/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;

// Note that even if the Dispatcher supports full-duplex mode (ie. simultaneous monitoring of reads and writes), this object is still
// implemented as being resolutely half-duplex, with Writes masking Reads.
public abstract class ChannelMonitor
{
	public static final boolean halfduplex = SysProps.get("greynaf.io.halfduplex", false);

	// Most of our virtual methods are conditionally optional. That is, the subclass doesn't have to implement them unless it makes
	// use of functionality which invokes them.
	// For example: If monitoring for Read, then ioIndication() must be provided if the subclass has not defined an IOExecReader,
	// and ioReceived() must be defined if it has.
	// It wouldn't make sense to subclass this if you weren't interested in monitoring anything at all, whether that be Read events,
	// incoming connections, or the conclusion of outgoing connections, hence this class has been declared as abstract even though no
	// particular method is actually abstract.
	protected void ioDisconnected()
			throws java.io.IOException {disconnect();}
	protected void ioTransmitted()
			throws com.grey.base.FaultException, java.io.IOException {}
	protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata, java.net.SocketAddress remaddr)
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("CM.ioReceived() not implemented");}
	protected void ioReceived(com.grey.base.utils.ArrayRef<byte[]> rcvdata)
			throws com.grey.base.FaultException, java.io.IOException {ioReceived(rcvdata, null);}
	protected void ioIndication(int ops)
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("CM.ioIndication() not implemented - Ops=0x"+Integer.toHexString(ops));}
	// client mode - this indicates the completion of a connect() call by us
	protected void connected(boolean success, Throwable ex)
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("Client-CM.connected() not implemented");}
	// server mode - this indicates we've accepted a connection from a remote client
	protected void connected()
			throws com.grey.base.FaultException, java.io.IOException {throw new Error("Server-CM.connected() not implemented");}
	protected void eventError(ChannelMonitor cm, Throwable ex) {}

	private static final byte S_INREAD = 1 << 0;
	private static final byte S_INWRITE = 1 << 1;
	private static final byte S_ISCONN = 1 << 2;
	private static final byte S_WECLOSE = 1 << 3;
	private static final byte S_CLOSELINGER = 1 << 4;

	public final Dispatcher dsptch;
	protected IOExecReader chanreader;
	protected IOExecWriter chanwriter;
	public java.nio.channels.SelectableChannel iochan;
	protected java.nio.channels.SelectionKey regkey;
	private byte cmstate;  //records which of the S_... state flags above are in effect
	private byte regOps;   //JDK flags - shadows/mirrors regkey.interestOps()
	private com.grey.naf.EntityReaper reaper;

	public final boolean isConnected() {return ((cmstate & S_ISCONN) != 0);}
	public final void disconnect() {disconnect(true);}
	protected final void setReaper(com.grey.naf.EntityReaper rpr) {reaper = rpr;}

	protected ChannelMonitor(Dispatcher d)
	{
		dsptch = d;
	}

	protected final void initChannel(java.nio.channels.SelectableChannel chan, boolean takeOwnership, boolean isconn) throws java.io.IOException
	{
		iochan = chan;
		regkey = null;
		regOps = 0;
		cmstate = (isconn ? S_ISCONN : 0);
		setOwnership(takeOwnership);
		dsptch.registerIO(this);
		iochan.configureBlocking(false);
		if (chanreader != null) chanreader.initChannel(this);
		if (chanwriter != null) chanwriter.initChannel(this);
	}

	public final boolean disconnect(boolean lingerOnClose)
	{
		if (lingerOnClose && chanwriter != null && chanwriter.isBlocked()) {
			// Still waiting for a write to complete, so linger-on-close till it does
			// This is irrespective of the S_WECLOSE setting
			cmstate |= S_CLOSELINGER;
			return false;
		}
		if (iochan == null) return true;
		if (chanreader != null) chanreader.clearChannel();
		if (chanwriter != null) chanwriter.clearChannel();

		if ((cmstate & S_WECLOSE) != 0) {
			try {
				dsptch.deregisterIO(this);
				iochan.close();
			} catch (Exception ex) {
				dsptch.logger.error("Dispatcher="+dsptch.name+": Failed to close ChannelMonitor="+iochan+" in state="+Integer.toHexString(cmstate), ex);
			}
		}
		iochan = null;
		regOps = 0;
		cmstate = 0;

		if (reaper != null) {
			com.grey.naf.EntityReaper rpr = reaper;
			reaper = null;
			rpr.entityStopped(this);
		}
		return true;
	}

	protected final void accepted(java.nio.channels.SocketChannel sockchan, com.grey.naf.EntityReaper rpr)
			throws com.grey.base.FaultException, java.io.IOException
	{
		initChannel(sockchan, true, true);
		setReaper(rpr);
		connected();
	}

	public final void connect(java.net.InetSocketAddress remaddr) throws com.grey.base.FaultException, java.io.IOException
	{
		java.nio.channels.SocketChannel sockchan = java.nio.channels.SocketChannel.open();
		initChannel(sockchan, true, false);

		// NB: This bloody method can only report connection failure by throwing - either here or in finishConnect()
		if (sockchan.connect(remaddr)) {
			cmstate |= S_ISCONN;
			connected(true, null);
			return;
		}
		monitorIO(regOps | java.nio.channels.SelectionKey.OP_CONNECT);
	}

	protected final void transmitCompleted() throws com.grey.base.FaultException, java.io.IOException
	{
		if ((cmstate & S_CLOSELINGER) != 0) {
			disconnect(false);
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
	protected final void handleIO(int readyOps) throws com.grey.base.FaultException, java.io.IOException
	{
		int validReadyOps = readyOps & regOps;

		if (chanreader != null && ((validReadyOps & java.nio.channels.SelectionKey.OP_READ) != 0)) {
			chanreader.handleIO();
			return;
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
				cmstate |= S_ISCONN;
			} catch (Throwable ex) {
				success = false;
				exconn = ex;
			}
			connected(success, exconn);
			return;
		}

		if (validReadyOps != 0) ioIndication(validReadyOps);
	}

	public final boolean enableRead() throws java.io.IOException
	{
		cmstate |= S_INREAD;
		if (halfduplex && (cmstate & S_INWRITE) != 0) return false;
		return monitorIO(regOps | java.nio.channels.SelectionKey.OP_READ);
	}

	public final boolean disableRead() throws java.io.IOException
	{
		cmstate &= ~S_INREAD;
		return monitorIO(regOps & ~java.nio.channels.SelectionKey.OP_READ);
	}

	// Writes mask Reads, so disable any existing Read interest
	public final boolean enableWrite() throws java.io.IOException
	{
		cmstate |= S_INWRITE;
		int opflags = (regOps | java.nio.channels.SelectionKey.OP_WRITE);
		if (halfduplex) opflags &= ~java.nio.channels.SelectionKey.OP_READ;
		return monitorIO(opflags);
	}

	public final boolean disableWrite() throws java.io.IOException
	{
		cmstate &= ~S_INWRITE;
		int opflags = regOps & ~java.nio.channels.SelectionKey.OP_WRITE;
		// if in half-duplex mode, restore the read that was blocked by this write
		if (halfduplex && (cmstate & S_INREAD) != 0) opflags |= java.nio.channels.SelectionKey.OP_READ;
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

	protected final void setOwnership(boolean take)
	{
		if (take) {
			cmstate |= S_WECLOSE;
		} else {
			cmstate &= ~S_WECLOSE;
		}
	}

	public void dumpState(StringBuilder sb)
	{
		int jdkOps = regkey.interestOps();
		sb.append("Exec=").append(chanreader==null?"":"R").append(chanwriter==null?"":"W");
		sb.append(" State=");
		if ((cmstate & S_ISCONN) != 0) sb.append('C');
		if ((cmstate & S_INREAD) != 0) sb.append('R');
		if ((cmstate & S_INWRITE) != 0) sb.append('W');
		if ((cmstate & S_CLOSELINGER) != 0) sb.append('L');
		if ((cmstate & S_WECLOSE) == 0) sb.append('X'); //eXternally owned
		sb.append(" Ops=");
		dumpInterestOps(jdkOps, sb);
		if (regOps != jdkOps) {
			//should never happen
			sb.append("/RegOps=");
			dumpInterestOps(regOps, sb);
		}
		sb.append(" - ").append(iochan);
	}

	private static void dumpInterestOps(int ops, StringBuilder sb)
	{
		if ((ops & java.nio.channels.SelectionKey.OP_READ) != 0) sb.append('R');
		if ((ops & java.nio.channels.SelectionKey.OP_WRITE) != 0) sb.append('W');
		if ((ops & java.nio.channels.SelectionKey.OP_ACCEPT) != 0) sb.append('A');
		if ((ops & java.nio.channels.SelectionKey.OP_CONNECT) != 0) sb.append('C');
	}
}
