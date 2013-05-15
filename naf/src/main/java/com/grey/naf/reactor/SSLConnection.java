/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import javax.net.ssl.SSLEngineResult;
import com.grey.logging.Logger.LEVEL;

// See http://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/samples/sslengine/SSLEngineSimpleDemo.java
final class SSLConnection
	implements Timer.Handler
{
	private static final int S_STARTED = 1 << 0;   //initial handshake completed
	private static final int S_HANDSHAKE = 1 << 1; //currently in a handshake
	private static final int S_CLOSING = 1 << 2;
	private static final int S_ABORTED = 1 << 3;

	private final javax.net.ssl.SSLEngine engine;
	private final ChannelMonitor cm;
	private final java.nio.channels.SocketChannel rawsock;
	private final java.nio.ByteBuffer sslprotoXmtBuf;
	private final java.nio.ByteBuffer sslprotoRcvBuf;
	private final java.nio.ByteBuffer appdataRcvBuf;
	private final java.nio.ByteBuffer dummyShakeBuf; //for SSL-handshake Wrap ops, where source buf is ignored
	private final String logpfx;

	private XmitQueue xmitq;
	private byte iostate;
	private long lastExpireTime;
	private long lastShakeTime;
	private Timer tmr_shake;

	private boolean setFlag(int f) {if (isFlagSet(f)) return false; iostate |= f; return true;}
	private boolean clearFlag(int f) {if (!isFlagSet(f)) return false; iostate &= ~f; return true;}
	private boolean isFlagSet(int f) {return ((iostate & f) != 0);}

	public SSLConnection(ChannelMonitor chanmon)
	{
		cm = chanmon;
		rawsock = java.nio.channels.SocketChannel.class.cast(cm.iochan);
		com.grey.naf.SSLConfig sslcfg = cm.getSSLConfig();
		engine = sslcfg.isClient ?
				sslcfg.ctx.createSSLEngine(sslcfg.peerCertName, rawsock.socket().getPort())
				: sslcfg.ctx.createSSLEngine();
		javax.net.ssl.SSLSession sess = engine.getSession();
		int netbufsiz = sess.getPacketBufferSize();
		int appbufsiz = sess.getApplicationBufferSize(); //needs to be comparable to SSL bufsize rather than IOExecReader's
		sslprotoXmtBuf = com.grey.base.utils.NIOBuffers.create(netbufsiz, com.grey.naf.BufferSpec.directniobufs);
		sslprotoRcvBuf = com.grey.base.utils.NIOBuffers.create(netbufsiz, com.grey.naf.BufferSpec.directniobufs);
		appdataRcvBuf = com.grey.base.utils.NIOBuffers.create(appbufsiz, com.grey.naf.BufferSpec.directniobufs);
		dummyShakeBuf = com.grey.base.utils.NIOBuffers.create(1, false);
		engine.setUseClientMode(sslcfg.isClient); //must call this in both modes - even if getUseClientMode() already seems correct

		if (!sslcfg.isClient) {
			if (sslcfg.clientAuth == 1) {
				engine.setWantClientAuth(true);
			} else if (sslcfg.clientAuth == 2) {
				engine.setNeedClientAuth(true);
			}
		}
		lastExpireTime = sess.getCreationTime();
		logpfx = "SSL-"+(sslcfg.isClient ? "Client" : "Server")+": ";
	}

	void start() throws com.grey.base.FaultException, java.io.IOException
	{
		if (cm.getSSLConfig().isClient) {
			// Initiate handshake. The dummy transmit calls wrap(), which will start the handshake.
			transmit(dummyShakeBuf, false);
		}
		long tmt = cm.getSSLConfig().shakeTimeout;
		if (tmt != 0) tmr_shake = cm.dsptch.setTimer(tmt, 0, this);
	}

	void close()
	{
		cancelTimer();
		setFlag(S_CLOSING);
		if (isFlagSet(S_ABORTED)) return;

		try {
			if (!engine.isOutboundDone()) {
				engine.closeOutbound();
			} else if (!engine.isInboundDone()) {
				//in practice, we'll never end up calling this
				engine.closeInbound();
			}
			// send our close_notify, but no need to wait for incoming one (may even have received it already)
			transmit(dummyShakeBuf, false);
		} catch (Throwable ex) {
			LEVEL lvl = LEVEL.TRC3;
			if (cm.dsptch.logger.isActive(lvl)) cm.dsptch.logger.log(lvl, ex, false, logpfx+"close failed on "+cm.iochan);
		}
		// This queue will only be populated if we're currently in a handshake, in which case we wouldn't be
		// be able to flush it here anyway.
		if (xmitq != null) xmitq.clear();
	}

	private void disconnect(boolean aborted, CharSequence diag) throws com.grey.base.FaultException, java.io.IOException
	{
		cancelTimer();
		if (aborted) setFlag(S_ABORTED);
		cm.sslDisconnected(diag);
	}

	void handleIO(java.nio.ByteBuffer srcbuf) throws com.grey.base.FaultException, java.io.IOException
	{
		int nbytes = cm.dsptch.transfer(srcbuf, sslprotoRcvBuf);
		if (nbytes == 0) return;
		ioReceived();
	}

	void handleIO() throws com.grey.base.FaultException, java.io.IOException
	{
		int nbytes = -1;
		try {
			nbytes = rawsock.read(sslprotoRcvBuf);
		} catch (Exception ex) {
			LEVEL lvl = LEVEL.TRC3;
			if (cm.dsptch.logger.isActive(lvl)) cm.dsptch.logger.log(lvl, ex, false, logpfx+"read() failed on "+cm.iochan);
		}
		if (nbytes == 0) return;

		if (nbytes == -1) {
			disconnect(false, "Remote disconnect");
			return;
		}
		ioReceived();
	}

	private void ioReceived() throws com.grey.base.FaultException, java.io.IOException
	{
		SSLEngineResult.Status engineStatus;
		do {
			try {
				engineStatus = decode();
			} catch (Exception ex) {
				LEVEL lvl = LEVEL.TRC3;
				if (cm.dsptch.logger.isActive(lvl)) cm.dsptch.logger.log(lvl, ex, false, logpfx+"Unwrap failed on "+cm.iochan);
				disconnect(true, "SSL handshake failed");
				return;
			}

			switch (engineStatus)
			{
			case OK:
				break;
			case BUFFER_UNDERFLOW: //need to wait for more SSL protocol data
				return;
			case CLOSED: //typically means we'received SSL close_notify
				disconnect(false, "Remote SSL-disconnect");
				return;
			default:
				throw new java.io.IOException("SSLConnection: engine-status="+engineStatus+" on Receive");
			}
			while (doHandshakeAction());

			// note that we can still receive app data while re-handshaking
			if (isFlagSet(S_STARTED) && appdataRcvBuf.position() != 0) {
				appdataRcvBuf.limit(appdataRcvBuf.position());
				appdataRcvBuf.position(0);
				while (appdataRcvBuf.remaining() != 0) {
					cm.chanreader.handleIO(appdataRcvBuf);
				}
				appdataRcvBuf.clear();
			}
		} while (engineStatus == SSLEngineResult.Status.OK && sslprotoRcvBuf.position() != 0);
	}

	public boolean transmit(java.nio.channels.FileChannel fchan, long pos, long limit) throws java.io.IOException
	{
		boolean done = true;
		java.nio.ByteBuffer buf = cm.dsptch.allocBuffer(engine.getSession().getApplicationBufferSize());
		while (pos != limit) {
			buf.clear();
			int nbytes = fchan.read(buf, pos);
			buf.flip();
			done = transmit(buf);
			pos += nbytes;
		}
		//final status dominates - it can only transition from non-blocked (true) to blocked (false)
		return done;
	}

	public boolean transmit(java.nio.ByteBuffer xmtbuf) throws java.io.IOException
	{
		try {
			return transmit(xmtbuf, false);
		} catch (com.grey.base.FaultException ex) {
			throw new java.io.IOException("SSL transmit failed - "+ex, ex);
		}
	}

	private boolean transmit(java.nio.ByteBuffer xmtbuf, boolean fromq) throws com.grey.base.FaultException, java.io.IOException
	{
		if ((xmtbuf != dummyShakeBuf) && isFlagSet(S_HANDSHAKE)) {
			//even though we can receive app data during a handshake, it seems we can't send any
			if (!fromq) {
				if (xmitq == null) xmitq = new XmitQueue(cm);
				xmitq.enqueue(xmtbuf);
			}
			return false;
		}
		SSLEngineResult.Status engineStatus;

		// Prepare to loop, on the off chance that xmtbuf is too large to stuff into sslprotoXmtBuf in one go
		while (xmtbuf.hasRemaining()) {
			try {
				engineStatus = encode(xmtbuf);
			} catch (Exception ex) {
				LEVEL lvl = LEVEL.TRC3;
				if (cm.dsptch.logger.isActive(lvl)) cm.dsptch.logger.log(lvl, ex, false, logpfx+"Wrap failed on "+cm.iochan);
				disconnect(true, "SSL handshake failed");
				return false;
			}

			switch (engineStatus)
			{
			case OK:
				break;
			case CLOSED:
				if (isFlagSet(S_CLOSING)) break; //carry on
				disconnect(false, "SSL shutdown");
				return false;
			default:
				throw new java.io.IOException("SSLConnection: engine-status="+engineStatus+" on Transmit");
			}
			sslprotoXmtBuf.flip();
			cm.chanwriter.write(sslprotoXmtBuf);
			sslprotoXmtBuf.clear();
			if (xmtbuf == dummyShakeBuf) break;
		}
		return true;
	}

	private boolean doHandshakeAction() throws com.grey.base.FaultException, java.io.IOException
	{
		SSLEngineResult.Status engineStatus = SSLEngineResult.Status.OK;
		SSLEngineResult.HandshakeStatus shakeStatus = engine.getHandshakeStatus();

		switch (shakeStatus)
		{
		case FINISHED:
		case NOT_HANDSHAKING:
			com.grey.naf.SSLConfig sslcfg = cm.getSSLConfig();
			if (!clearFlag(S_HANDSHAKE)) {
				// We are not (and were not) in a handshake - but maybe it's time we did one.
				boolean force_handshake = false;
				if (sslcfg.sessionTimeout != 0 && cm.dsptch.systime() - lastExpireTime > sslcfg.sessionTimeout) {
					// Invalidate current SSL session, to force a full renegotiation.
					engine.getSession().invalidate(); //this forces full handshake
					force_handshake = true;
				} else if (sslcfg.shakeFreq != 0 && cm.dsptch.systime() - lastShakeTime > sslcfg.shakeFreq) {
					force_handshake = true;
				}
				if (force_handshake) {
					engine.beginHandshake();
					return true;
				}
				return false;
			}
			// Handshake has just completed, so verify the remote peer's identity.
			String target = sslcfg.peerCertName;
			if (target != null) {
				java.security.cert.X509Certificate peercert = (java.security.cert.X509Certificate)(engine.getSession().getPeerCertificates()[0]);
				boolean matches = false;
				try {
					matches = com.grey.base.crypto.SSLCertificate.matchSubjectHost(peercert, target);
				} catch (java.security.cert.CertificateException ex) {
					throw new com.grey.base.FaultException(ex, "Failed to parse certificate on "+cm.iochan);
				}
				if (!matches) {
					cm.dsptch.logger.warn(logpfx+"Received cert ["+peercert.getSubjectDN().getName()+"] doesn't match target="+target+" on "+cm.iochan);
					disconnect(false, "Invalid remote SSL-certificate ID");
					return false;
				}
			}
			lastExpireTime = engine.getSession().getCreationTime();
			lastShakeTime = cm.dsptch.systime();

			if (setFlag(S_STARTED)) {
				// this was the initial handshake, so indicate that connection is now ready
				cancelTimer();
				cm.sslStarted();
			} else {
				if (xmitq != null) xmitq.drain();
			}
			return false;

		case NEED_TASK:
			// TODO: Delegated tasks could be run in a static Dispatcher threadpool, and a timer could check on them every
			// half second - or maybe 100ms, then every half sec. They could set a volatile flag to indicate they're done.
			setFlag(S_HANDSHAKE);
			Runnable task;
			while ((task = engine.getDelegatedTask()) != null) {
				task.run();
			}
			break;

		case NEED_UNWRAP:
			// we will do the unwrap in the normal course of the handleIO() loop on decode()
			setFlag(S_HANDSHAKE);
			return false;

		case NEED_WRAP:
			setFlag(S_HANDSHAKE);
			return transmit(dummyShakeBuf, false);

		default:
			throw new RuntimeException("SSLConnection: Missing case for Handshake status="+shakeStatus);
		}
		return (engineStatus == SSLEngineResult.Status.OK);
	}

	private SSLEngineResult.Status encode(java.nio.ByteBuffer srcbuf) throws java.io.IOException
	{
		sslprotoXmtBuf.clear();
		SSLEngineResult.Status engineStatus = engine.wrap(srcbuf, sslprotoXmtBuf).getStatus();
		return engineStatus;
	}

	private SSLEngineResult.Status decode() throws java.io.IOException
	{
		sslprotoRcvBuf.flip();
		SSLEngineResult.Status engineStatus = engine.unwrap(sslprotoRcvBuf, appdataRcvBuf).getStatus();
		sslprotoRcvBuf.compact();
		return engineStatus;
	}

	// NB: It seems the server simply discards untrusted client certs (and returns null here) rather than choking on them
	// Likewise, the server discards client certs if engine.getWantClientAuth() or engine.getWantClientAuth() aren't set.
	public java.security.cert.Certificate[] getPeerChain()
	{
		try {
			return engine.getSession().getPeerCertificates();
		} catch (javax.net.ssl.SSLPeerUnverifiedException ex) {
			return null;
		}
	}

	public java.security.cert.X509Certificate getPeerCertificate()
	{
		java.security.cert.Certificate[] chain = getPeerChain();
		return (chain == null ? null : (java.security.cert.X509Certificate)chain[0]);
	}

	@Override
	public void timerIndication(Timer tmr, Dispatcher d) throws com.grey.base.FaultException, java.io.IOException
	{
		tmr_shake = null;
		disconnect(false, "Timeout on SSL handshake");
	}

	@Override
	public void eventError(Timer tmr, Dispatcher d, Throwable ex) throws com.grey.base.FaultException, java.io.IOException
	{
		disconnect(false, "Error handling SSL timeout");
	}

	private void cancelTimer()
	{
		if (tmr_shake == null) return;
		tmr_shake.cancel();
		tmr_shake = null;
	}


	/*
	 * Holds queue of plaintext buffers to be transmitted.
	 * We allocate and discard ByteBuffers as need be. There's enough memory churn in this class that
	 * there's no point bothering with an ObjectWell to reuse them.
	 */
	private final class XmitQueue
	{
		private final ChannelMonitor cm;
		private final com.grey.base.utils.ObjectQueue<java.nio.ByteBuffer> bufq;

		public XmitQueue(ChannelMonitor cm) {
			this.cm = cm;
			bufq = new com.grey.base.utils.ObjectQueue<java.nio.ByteBuffer>(java.nio.ByteBuffer.class, 1, 1);
		}

		public void enqueue(java.nio.ByteBuffer inbuf) {
			int bufsiz = inbuf.remaining();
			java.nio.ByteBuffer qbuf = com.grey.base.utils.NIOBuffers.create(bufsiz, com.grey.naf.BufferSpec.directniobufs);
			cm.dsptch.transfer(inbuf, qbuf);
			qbuf.flip();
			bufq.add(qbuf);
		}

		public void drain() throws com.grey.base.FaultException, java.io.IOException {
			while (bufq.size() != 0) {
				java.nio.ByteBuffer buf = bufq.peek();
				if (!transmit(buf, true)) break;
				bufq.remove();
			}
		}

		public void clear() {
			bufq.clear();
		}
	}
}