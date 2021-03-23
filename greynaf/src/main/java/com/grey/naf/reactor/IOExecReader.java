/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.ByteArrayRef;

public abstract class IOExecReader
{
	protected static final int F_ARRBACK = 1 << 0; //rcvbuf has backing array
	protected static final int F_ENABLED = 1 << 1; //receive is currently enabled
	protected static final int F_HASDLM = 1 << 2;  //current receive phase is delimited by particular byte value (rcvdlm)
	protected static final int F_INRCVCB = 1 << 3; //inside ChannelMonitor.ioReceived() callback

	private final ByteArrayRef userbuf;  //for passing data back to user (ie. the callback entity)
	private final java.nio.ByteBuffer rcvbuf;

	private ChannelMonitor chanmon;
	private byte iostate;

	protected ByteArrayRef getUserBuffer() {return userbuf;}
	protected java.nio.ByteBuffer getReceiveBuffer() {return rcvbuf;}
	protected ChannelMonitor getCM() {return chanmon;}

	protected void setFlag(int f) {iostate |= f;}
	protected void clearFlag(int f) {iostate &= ~f;}
	protected boolean isFlagSet(int f) {return ((iostate & f) != 0);}

	protected IOExecReader(com.grey.naf.BufferGenerator spec)
	{
		if (spec == null || spec.rcvbufsiz == 0) {
			//app wants to perform the reads for itself
			rcvbuf = null;
			userbuf = null;
			return;
		}
		rcvbuf = spec.createReadBuffer();

		int off = 0;
		byte[] arrb;
		if (rcvbuf.hasArray()) {
			setFlag(F_ARRBACK);
			arrb = rcvbuf.array();
			off = rcvbuf.arrayOffset();
		} else {
			arrb = new byte[spec.rcvbufsiz];
		}
		userbuf = new ByteArrayRef(arrb, off, 0);
	}

	protected void initChannel(ChannelMonitor cm)
	{
		chanmon = cm;
		iostate &= F_ARRBACK; //turn off all flags except F_ARRBACK, which lasts for our lifetime
	}

	protected void clearChannel()
	{
		chanmon = null;
		clearFlag(F_ENABLED);
	}

	protected boolean enableReceive() throws java.io.IOException
	{
		if (chanmon == null) return false; //disconnected
		boolean ok = true;
		if (!isFlagSet(F_ENABLED)) {
			ok = chanmon.enableRead();
			setFlag(F_ENABLED);
		}
		return ok;
	}

	public void endReceive()
	{
		if (chanmon != null && isFlagSet(F_ENABLED)) {
			chanmon.disableRead();
		}
		clearFlag(F_ENABLED);
	}

	protected void dumpState(StringBuilder sb, String dlm)
	{
		char buftype = (rcvbuf == null ? 'U' : (isFlagSet(F_ARRBACK) ? 'H' : 'D'));
		sb.append(isFlagSet(F_ENABLED)?"on":"off").append('/').append(buftype);
	}
}