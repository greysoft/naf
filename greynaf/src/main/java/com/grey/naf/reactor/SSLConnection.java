/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import javax.net.ssl.SSLEngineResult;

import com.grey.base.config.SysProps;
import com.grey.logging.Logger.LEVEL;
import com.grey.naf.errors.NAFException;

// See http://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/samples/sslengine/SSLEngineSimpleDemo.java
class SSLConnection
	implements TimerNAF.Handler
{
	private static final int BUFSIZ_SSL = SysProps.get("greynaf.ssl.bufsiz_ssl", 0);
	private static final int BUFSIZ_APP = SysProps.get("greynaf.ssl.bufsiz_app", 0);

	private static final int S_STARTED = 1 << 0;   //initial handshake completed
	private static final int S_HANDSHAKE = 1 << 1; //currently in a handshake
	private static final int S_CLOSING = 1 << 2;
	private static final int S_ABORTED = 1 << 3;
	private static final int S_CMSTALLED = 1 << 4;

	private final javax.net.ssl.SSLEngine engine;
	private final CM_Stream cm;
	private final java.nio.ByteBuffer sslprotoXmtBuf;
	private final java.nio.ByteBuffer sslprotoRcvBuf;
	private final java.nio.ByteBuffer appdataRcvBuf;
	private final java.nio.ByteBuffer dummyShakeBuf; //for SSL-handshake Wrap ops, where source buf is ignored
	private final String logpfx;

	private final boolean isClient;
	private final String peerCertName;
	private final long sessionTimeout;
	private final long shakeTimeout;
	private final long shakeFreq;

	private XmitQueue xmitq;
	private byte iostate;
	private long lastExpireTime;
	private long lastShakeTime;
	private TimerNAF tmr_shake;

	private boolean setFlag(int f) {if (isFlagSet(f)) return false; iostate |= f; return true;}
	private boolean clearFlag(int f) {if (!isFlagSet(f)) return false; iostate &= ~f; return true;}
	private boolean isFlagSet(int f) {return ((iostate & f) != 0);}

	public SSLConnection(CM_Stream chanmon)
	{
		cm = chanmon;
		int peerport = (cm.getChannel() instanceof java.nio.channels.SocketChannel ?
				cm.getSocketChannel().socket().getPort() : 0);
		com.grey.naf.reactor.config.SSLConfig sslcfg = cm.getSSLConfig();
		isClient = sslcfg.isClient();
		peerCertName = sslcfg.getPeerCertName();
		sessionTimeout = sslcfg.getSessionTimeout();
		shakeTimeout = sslcfg.getShakeTimeout();
		shakeFreq = sslcfg.getShakeFreq();
		javax.net.ssl.SSLContext ctx = sslcfg.getContext();
		engine = isClient ?
				ctx.createSSLEngine(sslcfg.getPeerCertName(), peerport)
				: ctx.createSSLEngine();
		javax.net.ssl.SSLSession sess = engine.getSession();
		int netbufsiz = (BUFSIZ_SSL == 0 ? sess.getPacketBufferSize() : BUFSIZ_SSL);
		int appbufsiz = (BUFSIZ_APP == 0 ? sess.getApplicationBufferSize() : BUFSIZ_APP);
		sslprotoXmtBuf = com.grey.base.utils.NIOBuffers.create(netbufsiz, com.grey.naf.BufferGenerator.directniobufs);
		sslprotoRcvBuf = com.grey.base.utils.NIOBuffers.create(netbufsiz, com.grey.naf.BufferGenerator.directniobufs);
		appdataRcvBuf = com.grey.base.utils.NIOBuffers.create(appbufsiz, com.grey.naf.BufferGenerator.directniobufs);
		dummyShakeBuf = com.grey.base.utils.NIOBuffers.create(1, false); //could possibly be static?
		engine.setUseClientMode(isClient); //must call this in both modes - even if getUseClientMode() already seems correct

		if (!isClient) {
			int clientAuth = sslcfg.getClientAuth();
			if (clientAuth == 1) {
				engine.setWantClientAuth(true);
			} else if (clientAuth == 2) {
				engine.setNeedClientAuth(true);
			}
		}
		lastExpireTime = sess.getCreationTime();
		logpfx = "SSL-"+(isClient ? "Client" : "Server")+": ";
	}

	void start() throws java.io.IOException
	{
		if (isClient) {
			// Initiate handshake. The dummy transmit calls wrap(), which will start the handshake.
			transmit(dummyShakeBuf, false);
		}
		if (shakeTimeout != 0) tmr_shake = cm.getDispatcher().setTimer(shakeTimeout, 0, this);
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
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC3);
			if (cm.getLogger().isActive(lvl)) cm.getLogger().log(lvl, ex, lvl==LEVEL.ERR, logpfx+"SSL-close failed on "+cm+"/"+cm.getChannel());
		}
		// This queue will only be populated if we're currently in a handshake, in which case we wouldn't be
		// be able to flush it here anyway.
		if (xmitq != null) xmitq.clear();
	}

	private void disconnect(boolean aborted, CharSequence diag) throws java.io.IOException
	{
		cancelTimer();
		if (aborted) setFlag(S_ABORTED);
		cm.sslDisconnected(diag);
	}

	void handleIO(java.nio.ByteBuffer srcbuf) throws java.io.IOException
	{
		int nbytes = cm.getDispatcher().transfer(srcbuf, sslprotoRcvBuf);
		if (nbytes == 0) return;
		ioReceived();
	}

	void handleRead() throws java.io.IOException
	{
		int nbytes = -1;
		try {
			java.nio.channels.ReadableByteChannel chan = (java.nio.channels.ReadableByteChannel)cm.getChannel();
			nbytes = chan.read(sslprotoRcvBuf);
		} catch (Exception ex) {
			LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC3);
			if (cm.getLogger().isActive(lvl)) cm.getLogger().log(lvl, ex, lvl==LEVEL.ERR, logpfx+"SSL-read() failed on "+cm+"/"+cm.getChannel());
		}
		if (nbytes == 0) return;

		if (nbytes == -1) {
			disconnect(false, "Remote disconnect");
			return;
		}
		ioReceived();
	}
	
	void deliver() throws java.io.IOException
	{
		if (!isFlagSet(S_CMSTALLED)) return;
		// We were previously interrupted in mid-stream, so we may have buffered data sitting in both the application
		// (decoded) and protocol (SSL) buffers.
		// Deliver any decoded data first, and once we've emptied it (or as much as possible) we pull any data from the
		// SSL-encoded buffer.
		// Note that spurious calls to these two methods wouldn't break anything, but we use Stalled flag to avoid the
		// unnecessary work.
		forwardReceivedIO(); //pull from decoded-SSL-payload buffer
		ioReceived(); //pull from encoded-SSL-protocol buffer
	}

	private void ioReceived() throws java.io.IOException
	{
		SSLEngineResult.Status engineStatus;
		do {
			int pos = appdataRcvBuf.position();
			try {
				engineStatus = decode();
			} catch (Exception ex) {
				LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC3);
				if (cm.getLogger().isActive(lvl)) cm.getLogger().log(lvl, ex, lvl==LEVEL.ERR, logpfx+"SSL-Unwrap failed on "+cm+"/"+cm.getChannel());
				disconnect(true, "SSL handshake failed");
				return;
			}

			switch (engineStatus)
			{
			case OK:
				while (doHandshakeAction());
				break;
			case BUFFER_OVERFLOW: //need to empty appdataRcvBuf a bit more before we can unwrap into it
				if (pos == 0 || !isFlagSet(S_STARTED)) {
					//buffer was empty or is too small to complete the handshake
					throw new java.io.IOException("SSL OVERFLOW: appdata="+appdataRcvBuf+", sslproto="+sslprotoRcvBuf
						+" - recommended="+engine.getSession().getApplicationBufferSize()+"/"+engine.getSession().getPacketBufferSize());
				}
				break; //give IOExecReader a chance to drain appdataRcvBuf
			case BUFFER_UNDERFLOW: //need to wait for more SSL protocol data before we can decode sslprotoRcvBuf
				return;
			case CLOSED: //typically means we've received SSL close_notify
				disconnect(false, "Remote SSL-disconnect");
				return;
			default:
				throw new java.io.IOException("SSLConnection: engine-status="+engineStatus+" on Receive");
			}

			// note that we can still receive app data while re-handshaking
			if (isFlagSet(S_STARTED)) {
				if (!forwardReceivedIO()) break;
			}
		} while (engineStatus == SSLEngineResult.Status.OK && sslprotoRcvBuf.position() != 0);
	}

	private boolean forwardReceivedIO() throws java.io.IOException
	{
		if (appdataRcvBuf.remaining() == 0) {
			//theoretically impossible given that it is in the unflipped state, but anyway
			appdataRcvBuf.clear();
			return true;
		}
		appdataRcvBuf.flip();

		while (appdataRcvBuf.remaining() != 0) {
			if (cm.getReader().handleIO(appdataRcvBuf) == 0) {
				// IOExecReader can't consume any more right now, so leave the remainder of appdataRcvBuf pending
				// and prepare it to be appended to by our ioReceived() method.
				// I don't think this can actually happen, as IOExecReader will always deliver something to the
				// app if its own buffer fills up.
				// NB: IOExecReader safely prevents this making reentrant calls to our deliver() method
				LEVEL lvl = LEVEL.TRC2;
				if (cm.getLogger().isActive(lvl)) cm.getLogger().log(lvl, logpfx+"SSL stalled on "+cm+"/"+cm.getChannel()+" - "+appdataRcvBuf);
				setFlag(S_CMSTALLED);
				appdataRcvBuf.compact();
				int nbytes = appdataRcvBuf.remaining();
				appdataRcvBuf.limit(appdataRcvBuf.capacity());
				appdataRcvBuf.position(nbytes);
				return false;
			}
		}
		appdataRcvBuf.clear();
		clearFlag(S_CMSTALLED);
		return true;
	}

	public void transmit(java.nio.ByteBuffer xmtbuf) throws java.io.IOException
	{
		transmit(xmtbuf, false);
	}

	boolean transmit(java.nio.ByteBuffer xmtbuf, boolean fromq) throws java.io.IOException
	{
		if ((xmtbuf != dummyShakeBuf) && isFlagSet(S_HANDSHAKE)) {
			//even though we can receive app data during a handshake, it seems we can't send any
			if (!fromq) {
				if (xmitq == null) xmitq = new XmitQueue(this, cm);
				xmitq.enqueue(xmtbuf);
			}
			return false;
		}
		SSLEngineResult.Status engineStatus;

		// Prepare to loop, in case xmtbuf is too large to stuff into sslprotoXmtBuf in one go
		while (xmtbuf.hasRemaining()) {
			try {
				engineStatus = encode(xmtbuf);
			} catch (Exception ex) {
				LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC3);
				if (cm.getLogger().isActive(lvl)) cm.getLogger().log(lvl, ex, lvl==LEVEL.ERR, logpfx+"SSL-Wrap failed on "+cm+"/"+cm.getChannel());
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
			cm.getWriter().write(sslprotoXmtBuf, false);
			sslprotoXmtBuf.clear();
			if (xmtbuf == dummyShakeBuf) break;
		}
		return true;
	}

	private boolean doHandshakeAction() throws java.io.IOException
	{
		SSLEngineResult.Status engineStatus = SSLEngineResult.Status.OK;
		SSLEngineResult.HandshakeStatus shakeStatus = engine.getHandshakeStatus();

		switch (shakeStatus)
		{
		case FINISHED:
		case NOT_HANDSHAKING:
			if (!clearFlag(S_HANDSHAKE)) {
				// We are not (and were not) in a handshake - but maybe it's time we did one.
				boolean force_handshake = false;
				if (sessionTimeout != 0 && cm.getSystemTime() - lastExpireTime > sessionTimeout) {
					// Invalidate current SSL session, to force a full renegotiation.
					engine.getSession().invalidate(); //this forces full handshake
					force_handshake = true;
				} else if (shakeFreq != 0 && cm.getSystemTime() - lastShakeTime > shakeFreq) {
					force_handshake = true;
				}
				if (force_handshake) {
					engine.beginHandshake();
					return true;
				}
				return false;
			}
			// Handshake has just completed, so verify the remote peer's identity.
			if (peerCertName != null) {
				java.security.cert.X509Certificate peercert = (java.security.cert.X509Certificate)(engine.getSession().getPeerCertificates()[0]);
				boolean matches = false;
				try {
					matches = com.grey.base.crypto.SSLCertificate.matchSubjectHost(peercert, peerCertName);
				} catch (java.security.cert.CertificateException ex) {
					throw new java.io.IOException("Failed to parse certificate on "+cm.getChannel(), ex);
				}
				if (!matches) {
					cm.getLogger().warn(logpfx+"Received SSL-cert ["+peercert.getSubjectDN().getName()+"] doesn't match target="+peerCertName+" on "+cm+"/"+cm.getChannel());
					disconnect(false, "Invalid remote SSL-certificate ID");
					return false;
				}
			}
			lastExpireTime = engine.getSession().getCreationTime();
			lastShakeTime = cm.getSystemTime();

			if (setFlag(S_STARTED)) {
				// this was the initial handshake, so indicate that connection is now ready
				cancelTimer();
				cm.sslStarted();
			} else {
				if (xmitq != null) xmitq.drain();
			}
			return false;

		case NEED_TASK:
			// TODO: Delegated tasks could be run in a Dispatcher threadpool, and a timer could check on them every half
			// second - or maybe 100ms, then every half sec. They could set a volatile flag to indicate they're done.
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
			throw new Error("SSLConnection: Missing case for Handshake status="+shakeStatus);
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
	public void timerIndication(TimerNAF tmr, Dispatcher d) throws java.io.IOException
	{
		tmr_shake = null;
		disconnect(false, "Timeout on SSL handshake");
	}

	@Override
	public void eventError(TimerNAF tmr, Dispatcher d, Throwable ex) throws java.io.IOException
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
	private static final class XmitQueue
	{
		private final SSLConnection conn;
		private final CM_Stream cm;
		private final com.grey.base.collections.ObjectQueue<java.nio.ByteBuffer> bufq;

		public XmitQueue(SSLConnection conn, CM_Stream cm) {
			this.conn = conn;
			this.cm = cm;
			bufq = new com.grey.base.collections.ObjectQueue<java.nio.ByteBuffer>(java.nio.ByteBuffer.class, 1, 1);
		}

		public void enqueue(java.nio.ByteBuffer inbuf) {
			int bufsiz = inbuf.remaining();
			java.nio.ByteBuffer qbuf = com.grey.base.utils.NIOBuffers.create(bufsiz, com.grey.naf.BufferGenerator.directniobufs);
			cm.getDispatcher().transfer(inbuf, qbuf);
			qbuf.flip();
			bufq.add(qbuf);
		}

		public void drain() throws java.io.IOException {
			while (bufq.size() != 0) {
				java.nio.ByteBuffer buf = bufq.peek();
				if (!conn.transmit(buf, true)) break; //buffer will be sent in full or not at all
				bufq.remove();
			}
		}

		public void clear() {
			bufq.clear();
		}
	}
}