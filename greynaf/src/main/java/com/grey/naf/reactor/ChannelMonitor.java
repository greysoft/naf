/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger;
import com.grey.logging.Logger.LEVEL;
import com.grey.naf.errors.NAFException;

/**
 * This is the base class for all entities who wish to monitor an NIO-based I/O channel, and receive
 * event callbacks for it.
 */
public abstract class ChannelMonitor
{
	static final boolean halfduplex = SysProps.get("greynaf.io.halfduplex", false);
	private static final long minbootdiff = SysProps.getTime("greynaf.dumpcm.minbootdiff", 5000);

	static final int S_ISCONN = 1 << 0; //the iochan endpoint is connected to its remote peer
	static final int S_APPCONN = 1 << 1; //the application has been notified that we are connected
	static final int S_WECLOSE = 1 << 2;
	static final int S_CLOSELINGER = 1 << 3;
	static final int S_INREAD = 1 << 4;
	static final int S_INWRITE = 1 << 5;
	static final int S_INDISC = 1 << 6;
	static final int S_BRKPIPE = 1 << 7;
	static final int S_INIT = 1 << 8;

	private final Dispatcher dsptch;
	private java.nio.channels.SelectableChannel iochan;
	private java.nio.channels.SelectionKey regkey;

	private final int cm_id;
	private short cmstate; //records which of the S_... state flags above are in effect
	private byte regOps; //JDK flags - shadows/mirrors regkey.interestOps()
	private long start_time;
	private com.grey.naf.EntityReaper reaper;

	abstract void ioIndication(int readyOps) throws java.io.IOException;

	boolean shutdownChannel(boolean linger) {return true;}
	protected void ioDisconnected(CharSequence diagnostic) throws java.io.IOException {disconnect();}
	protected void eventError(Throwable ex) throws java.io.IOException {}
	protected StringBuilder dumpAppState(StringBuilder sb) {return sb;}

	public int getCMID() {return cm_id;}
	public long getStartTime() {return start_time;}
	public Dispatcher getDispatcher() {return dsptch;}

	public java.nio.channels.SelectableChannel getChannel() {return iochan;}
	public java.nio.channels.SocketChannel getSocketChannel() {return (java.nio.channels.SocketChannel)getChannel();}
	public java.nio.channels.DatagramChannel getDatagramChannel() {return (java.nio.channels.DatagramChannel)getChannel();}

	public com.grey.naf.EntityReaper getReaper() {return reaper;}
	public void setReaper(com.grey.naf.EntityReaper rpr) {reaper = rpr;}

	java.nio.channels.SelectionKey getRegistrationKey() {return regkey;}
	void setRegistrationKey(java.nio.channels.SelectionKey key) {regkey = key;}

	public boolean disconnect() {return disconnect(true);}
	public boolean disconnect(boolean linger) {return disconnect(linger, false);}

	boolean isFlagSetCM(int f) {return ((cmstate & f) == f);}
	void setFlagCM(int f) {cmstate |= f;}
	void clearFlagCM(int f) {cmstate &= ~f;}

	// conveniences to enable shorter references to common methods than via getDispatcher()
	public Logger getLogger() {return getDispatcher().getLogger();}
	public long getSystemTime() {return getDispatcher().getSystemTime();}

	ChannelMonitor(Dispatcher d)
	{
		dsptch = d;
		cm_id = d.allocateChannelId();
	}

	// This can optionally be called before registerChannel() and some subclasses might choose to insist on it.
	// A typical user would be a subclass that does some preparatory work before calling registerChannel() and can fail
	// before ever making that call, such that it ends up calling disconnect() without ever having attempted to connect.
	// Calling this method first ensures that it at least arrives in disconnect() in a properly initialised state.
	protected void initChannelMonitor()
	{
		start_time = getSystemTime();
		iochan = null;
		regkey = null;
		regOps = 0;
		cmstate = S_INIT;
	}

	//Note that setReaper() can be called before this
	void initChannel(java.nio.channels.SelectableChannel chan, boolean takeOwnership) throws java.io.IOException
	{
		if (!isFlagSetCM(S_INIT)) initChannelMonitor();
		iochan = chan;
		if (takeOwnership) setFlagCM(S_WECLOSE);
		iochan.configureBlocking(false);
	}

	void registerChannel()
	{
		getDispatcher().registerIO(this);
	}

	void registerChannel(java.nio.channels.SelectableChannel chan, boolean takeOwnership) throws java.io.IOException
	{
		initChannel(chan, takeOwnership);
		registerChannel();
	}

	boolean disconnect(boolean linger, boolean no_reap)
	{
		clearFlagCM(S_INIT); //in case we never got as far as calling CM_Client.connect()
		//avoid re-entrancy, ie. calling ourself recursively due to a failure in these disconnect ops
		if (isFlagSetCM(S_INDISC)) return true; //we have already completed a disconnect
		setFlagCM(S_INDISC);

		if (iochan != null) {
			if (!shutdownChannel(linger)) {
				clearFlagCM(S_INDISC); //enable future disconnect() call when linger completes
				return false;
			}
			clearFlagCM(S_CLOSELINGER);
			try {
				getDispatcher().deregisterIO(this);
				if (isFlagSetCM(S_WECLOSE)) {
					iochan.close();
					clearFlagCM(S_ISCONN);
				}
			} catch (Exception ex) {
				getLogger().log(LEVEL.ERR, ex, true, "Dispatcher="+getDispatcher().getName()+": Failed to close ChannelMonitor=E"+cm_id+"/"+iochan
						+" in state=0x"+Integer.toHexString(cmstate));
			}
			iochan = null;
		}

		if (reaper != null && !no_reap) {
			com.grey.naf.EntityReaper rpr = reaper;
			reaper = null;
			rpr.entityStopped(this);
		}
		return true;
	}

	void failed(boolean disconnect, Throwable ex) throws java.io.IOException
	{
		if (isFlagSetCM(S_CLOSELINGER)) {
			// This entity is already in shutdown mode waiting to flush its final transmissions.
			// Time to discard any blocked sends and terminate it immediately.
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC2);
			getLogger().log(lvl, ex, lvl==LEVEL.ERR, "Failed during close-linger on "+iochan);
			disconnect(false);
		} else {
			if (disconnect) {
				// Tell the application to initiate the disconnect
				ioDisconnected(ex==null ? "ChannelMonitor failed" : ex.toString());
			} else {
				eventError(ex);
			}
		}
	}

	// We should not receive Read events after disabling OP_READ, and same goes for Write/OP_WRITE, but beware that some IO events could
	// have already fired before we disabled them. and we're just receiving those IO event later on in the same Dispatcher cycle.
	// We therefore have to filter out our current Interest set from the supplied Ready set.
	// Furthermore, if we do have a Reader then we must never pass Read events up to the Owner even if reads are currently disabled, and
	// the same goes for Writer.
	// Note that even if both Reader and Writer events have been simultaneously signalled to us, we only handle one of them in a single
	// call, in case its handler drastically changes our state.
	void handleIO(int readyOps) throws java.io.IOException
	{
		readyOps &= regOps;
		if (readyOps != 0) ioIndication(readyOps);
	}

	boolean enableRead() throws java.io.IOException
	{
		setFlagCM(S_INREAD);
		if (halfduplex && isFlagSetCM(S_INWRITE)) return false;
		return monitorIO_HandleError(regOps | java.nio.channels.SelectionKey.OP_READ, false, "register-Read");
	}

	// The I/O operation is already over, so just swallow any exceptions.
	// They are probably due to a remote disconnect, and we can handle that later if/when we do any more I/O on this channel.
	void disableRead()
	{
		clearFlagCM(S_INREAD);
		try {
			monitorIO_HandleError(regOps & ~java.nio.channels.SelectionKey.OP_READ, true, "deregister-Read");
		} catch (Exception ex) {
			throw new NAFException(true, "Unexpected Exception on disableRead for "+getClass().getName()+"/E"+cm_id+"/"+iochan, ex);
		}
	}

	// Note that enableWrite() and disableWrite() only apply to CM_Stream, but they're defined here because they rely
	// on private ChannelMonitor members.
	void enableWrite() throws java.io.IOException
	{
		setFlagCM(S_INWRITE);
		int opflags = (regOps | java.nio.channels.SelectionKey.OP_WRITE);
		if (halfduplex) opflags &= ~java.nio.channels.SelectionKey.OP_READ;
		monitorIO_HandleError(opflags, false, "register-Write");
	}

	// if in half-duplex mode, we restore the read that was disabled by the OP_WRITE
	void disableWrite()
	{
		clearFlagCM(S_INWRITE);
		int opflags = regOps & ~java.nio.channels.SelectionKey.OP_WRITE;
		if (halfduplex && isFlagSetCM(S_INREAD)) opflags |= java.nio.channels.SelectionKey.OP_READ;
		try {
			monitorIO_HandleError(opflags, true, "deregister-Write");
		} catch (Exception ex) {
			throw new NAFException(true, "Unexpected Exception on disableWrite for "+getClass().getName()+"/E"+cm_id+"/"+iochan, ex);
		}
	}

	void enableListen() throws java.nio.channels.ClosedChannelException
	{
		monitorIO(regOps | java.nio.channels.SelectionKey.OP_ACCEPT);
	}

	void disableListen() throws java.nio.channels.ClosedChannelException
	{
		monitorIO(regOps & ~java.nio.channels.SelectionKey.OP_ACCEPT);
	}

	void enableConnect() throws java.nio.channels.ClosedChannelException
	{
		monitorIO(regOps | java.nio.channels.SelectionKey.OP_CONNECT);
	}

	void disableConnect() throws java.nio.channels.ClosedChannelException
	{
		monitorIO(regOps & ~java.nio.channels.SelectionKey.OP_CONNECT);
	}

	private boolean monitorIO_HandleError(int opflags, boolean nothrow, String opdesc) throws java.io.IOException
	{
		try {
			return monitorIO(opflags);
		} catch (Exception ex) {
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC2);
			if (nothrow) {
				if (getLogger().isActive(lvl)) {
					String errmsg = "Dispatcher failed on "+opdesc+" for "+getClass().getName()+"/E"+cm_id+"/"+iochan;
					getLogger().log(lvl, ex, lvl==LEVEL.ERR, errmsg);
				}
			} else {
				String errmsg = "Dispatcher failed on "+opdesc+" for "+getClass().getName()+"/E"+cm_id+"/"+iochan;
				if (this instanceof CM_Stream) {
					((CM_Stream)this).brokenPipe(lvl, "Error on "+opdesc, errmsg, ex);
				} else {
					throw new java.io.IOException(errmsg);
				}
			}
			return false;
		}
	}

	private boolean monitorIO(int opflags) throws java.nio.channels.ClosedChannelException
	{
		if (opflags == regOps || iochan == null) return false;
		regOps = (byte)opflags;	
		getDispatcher().monitorIO(this, regOps);
		return true;
	}

	public StringBuilder dumpState(StringBuilder sb, boolean verbose)
	{
		final Class<?> clss = getClass();
		if (sb == null) sb = new StringBuilder();
		int prevlen = sb.length();
		sb.append("ID=").append(cm_id).append(": ");
		if (this instanceof CM_Listener) {
			sb.append(clss.getSimpleName())
				.append('/').append(((CM_Listener)this).getServerType().getName())
				.append('/').append(((CM_Listener)this).getName());
		} else if (clss == Producer.AlertsPipe.class) {
			if (!verbose) {
				//omit this object altogether
				sb.setLength(prevlen);
				return sb;
			}
			sb.append("Producer/").append(((Producer.AlertsPipe<?>)this).getProducer().getName());
		} else {
			if (this instanceof CM_UDP) {
				sb.append("UDP/");
			} else if (this instanceof CM_TCP) {
				sb.append("TCP/");
			} else if (this instanceof CM_Stream) {
				sb.append("Stream/");
			}
			sb.append(clss.getName());
		}
		if (start_time - getDispatcher().getTimeBoot() > minbootdiff) {
			//we don't show start-time if this CM apparently dates back to the birth of this Dispatcher
			getDispatcher().getCalendar().setTimeInMillis(start_time);
			sb.append(" - ");
			TimeOps.makeTimeLogger(getDispatcher().getCalendar(), sb, true, true);
		}
		String dlm = "; ";
		sb.append("<br/>State=");
		dumpMonitorState(false, sb);
		int jdkOps = 0;
		sb.append(dlm).append("Ops=");
		if (regkey == null) {
			sb.append("Null");
		} else {
			if (regkey.isValid()) {
				jdkOps = regkey.interestOps();
				dumpInterestOps(jdkOps, sb);
			} else {
				sb.append("CANCELLED");
			}
		}
		if (regOps != jdkOps) {
			//should never happen
			sb.append("/RegOps=");
			dumpInterestOps(regOps, sb);
		}
		dumpChannelState(sb, dlm);

		int prevlen1 = sb.length();
		sb.append("<br/><span class=\"cmapp\">App: ");
		int prevlen2 = sb.length();
		dumpAppState(sb);
		if (sb.length() == prevlen2) {
			sb.setLength(prevlen1);
		} else {
			sb.append("</span>");
		}
		return sb;
	}

	StringBuilder dumpChannelState(StringBuilder sb, String dlm)
	{
		if (sb == null) sb = new StringBuilder();
		sb.append("<br/>Endpoint: ").append(iochan);
		return sb;
	}

	CharSequence dumpMonitorState(boolean hex, StringBuilder sb)
	{
		if (hex) {
			String str = "0x"+Integer.toHexString(cmstate);
			if (sb == null) return str;
			sb.append(str);
		} else {
			if (sb == null) sb = new StringBuilder();
			if (isFlagSetCM(S_ISCONN)) sb.append('C');
			if (isFlagSetCM(S_INDISC)) sb.append('D');
			if (isFlagSetCM(S_BRKPIPE)) sb.append('P');
			if (isFlagSetCM(S_APPCONN)) sb.append('A');
			if (isFlagSetCM(S_WECLOSE)) sb.append('X');
			if (isFlagSetCM(S_CLOSELINGER)) sb.append('L');
			if (isFlagSetCM(S_INREAD)) sb.append('R');
			if (isFlagSetCM(S_INWRITE)) sb.append('W');
		}
		return sb;
	}

	private static StringBuilder dumpInterestOps(int ops, StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder();
		if ((ops & java.nio.channels.SelectionKey.OP_READ) != 0) sb.append('R');
		if ((ops & java.nio.channels.SelectionKey.OP_WRITE) != 0) sb.append('W');
		if ((ops & java.nio.channels.SelectionKey.OP_ACCEPT) != 0) sb.append('A');
		if ((ops & java.nio.channels.SelectionKey.OP_CONNECT) != 0) sb.append('C');
		return sb;
	}

	@Override
	public String toString() {
		return super.toString()+"/E"+getCMID()+" with iochan="+getChannel();
	}
}