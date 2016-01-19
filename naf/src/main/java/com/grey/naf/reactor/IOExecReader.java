/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public abstract class IOExecReader
{
	static final int F_ARRBACK = 1 << 0; //rcvbuf has backing array
	static final int F_ENABLED = 1 << 1; //receive is currently enabled
	static final int F_HASDLM = 1 << 2;  //current receive phase is delimited by particular byte value (rcvdlm)
	static final int F_INRCVCB = 1 << 3; //inside ChannelMonitor.ioReceived() callback

	final com.grey.base.utils.ArrayRef<byte[]> userbuf;  //for passing data back to user (ie. the callback entity)
	final java.nio.ByteBuffer rcvbuf;
	final int rcvbuf0;

	ChannelMonitor chanmon;
	private byte iostate;

	final void setFlag(int f) {iostate |= f;}
	final void clearFlag(int f) {iostate &= ~f;}
	final boolean isFlagSet(int f) {return ((iostate & f) != 0);}

	IOExecReader(com.grey.naf.BufferSpec spec)
	{
		if (spec.rcvbufsiz == 0) {
			//app wants to perform the reads for itself
			rcvbuf = null;
			userbuf = null;
			rcvbuf0 = 0;
			return;
		}
		rcvbuf = com.grey.base.utils.NIOBuffers.create(spec.rcvbufsiz, spec.directbufs);

		byte[] arrb;
		if (rcvbuf.hasArray()) {
			setFlag(F_ARRBACK);
			arrb = rcvbuf.array();
			rcvbuf0 = rcvbuf.arrayOffset();
		} else {
			arrb = new byte[spec.rcvbufsiz];
			rcvbuf0 = 0;
		}
		userbuf = new com.grey.base.utils.ArrayRef<byte[]>(arrb);
		userbuf.ar_len = 0;
	}

	final void initChannel(ChannelMonitor cm)
	{
		chanmon = cm;
		iostate &= F_ARRBACK; //turn off all flags except F_ARRBACK, which lasts for our lifetime
	}

	final void clearChannel()
	{
		chanmon = null;
		clearFlag(F_ENABLED);
	}

	final boolean enableReceive() throws java.io.IOException
	{
		if (chanmon == null) return false; //disconnected
		boolean ok = true;
		if (!isFlagSet(F_ENABLED)) {
			ok = chanmon.enableRead();
			setFlag(F_ENABLED);
		}
		return ok;
	}

	public final void endReceive()
	{
		if (chanmon != null && isFlagSet(F_ENABLED)) {
			chanmon.disableRead();
		}
		clearFlag(F_ENABLED);
	}

	void dumpState(StringBuilder sb, String dlm)
	{
		char buftype = (rcvbuf == null ? 'U' : (isFlagSet(F_ARRBACK) ? 'H' : 'D'));
		sb.append(isFlagSet(F_ENABLED)?"on":"off").append('/').append(buftype);
	}
}