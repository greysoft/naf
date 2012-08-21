/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

// DNS protocol definitions and utility methods
// RFC-1035 is the main authority - See also http://www.iana.org/assignments/dns-parameters
public final class Packet
{
	public static final int INETPORT = 53;
	public static final int UDPMAXMSG = 512;
	public static final int PKTHDRSIZ = 12;
	private static final int TCPMSGLENSIZ = 2;    // TCP DNS message is prefixed with a 2-byte length field

	public static final int OPCODE_QRY = 0;
	public static final int OPCODE_IQRY = 1;
	public static final int OPCODE_STATUS = 2;
	public static final int OPCODE_NOTIFY = 4;
	public static final int OPCODE_UPDATE = 5;

	public static final int RCODE_OK = 0;
	public static final int RCODE_BADFMT = 1;
	public static final int RCODE_SRVFAIL = 2;
	public static final int RCODE_NXDOM = 3;
	public static final int RCODE_NOTIMPL = 4;
	public static final int RCODE_REJ = 5;
	public static final int RCODE_YXDOMAIN = 6;
	public static final int RCODE_YXRRSET = 7;
	public static final int RCODE_NXRRSET = 8;
	public static final int RCODE_NOTAUTH = 9;
	public static final int RCODE_NOTZONE = 10;
	public static final int RCODE_BADVERS = 16;
	public static final int RCODE_BADTRUNC = 22;

	public static final byte QCLASS_INET = 1;
	public static final byte QCLASS_CHAOS = 3;
	public static final byte QCLASS_HESIOD = 4;
	public static final byte QCLASS_NONE = (byte)254;
	public static final byte QCLASS_ALL = (byte)255;
	
	public static final int SECT_QUESTIONS = 1;
	public static final int SECT_ANSWERS = 2;
	public static final int SECT_AUTH = 3;
	public static final int SECT_INFO = 4;

	// RFC-4035 also defines AD=0x0020 and CD=0x0010, but we don't support them
	public static final int FLAGS_QR = 0x8000;
	public static final int FLAGS_AA = 0x0400;
	public static final int FLAGS_TC = 0x0200;
	public static final int FLAGS_RD = 0x0100;
	public static final int FLAGS_RA = 0x0080;


	public static final class Factory
		implements com.grey.base.utils.ObjectWell.ObjectFactory
	{
		private final boolean isTCP;
		private final com.grey.naf.BufferSpec bufspec;

		public Factory(boolean tcp, com.grey.naf.BufferSpec spec)
		{
			isTCP = tcp;
			bufspec = spec;
		}

		@Override
		public Packet factory_create()
		{
			return new Packet(isTCP, bufspec);
		}
	}


	private final java.nio.ByteBuffer pktniobuf;
	private final byte[] pktbuf;
	private final int bufsiz;
	private final int pktbase;
	private final int msgbase;
	public final boolean isTCP;

	private int hdrflags;
	private int pktlen;
	private int msglen;

	public int qid;
	public int qcnt;
	public int anscnt;
	public int authcnt;
	public int infocnt;

	public int rcode() {return hdrflags & 0xF;}
	public void rcode(int val) {hdrflags = hdrflags | val;}
	public int opcode() {return (hdrflags >> 11) & 0xF;}
	public void opcode(int val) {hdrflags = hdrflags | ((val & 0xF) << 11);}

	public boolean isResponse() {return ((hdrflags & FLAGS_QR) != 0);}
	public void setResponse() {hdrflags |= FLAGS_QR;}
	public boolean isAuth() {return ((hdrflags & FLAGS_AA) != 0);}
	public void setAuth() {hdrflags |= FLAGS_AA;}
	public boolean isTrunc() {return ((hdrflags & FLAGS_TC) != 0);}
	public void setTrunc() {hdrflags |= FLAGS_TC;}
	public boolean recursionWanted() {return ((hdrflags & FLAGS_RD) != 0);}
	public void wantRecursion() {hdrflags |= FLAGS_RD;}
	public boolean recursionOffered() {return ((hdrflags & FLAGS_RA) != 0);}
	public void offerRecursion() {hdrflags |= FLAGS_RA;}
	
	private boolean isCompressedLabel(int off) {return ((pktbuf[off] & 0xC0) != 0);}
	private int compressedLabelPointer(int off) {return msgbase + (decodeInt16(off) & ~0xC000);}

	public Packet(boolean tcp, com.grey.naf.BufferSpec bufspec)
	{
		isTCP = tcp;
		bufsiz = bufspec.rcvbufsiz;
	
		pktniobuf = com.grey.base.utils.NIOBuffers.create(bufsiz, bufspec.directbufs);
	
		if (!pktniobuf.hasArray()) {
			pktbuf = new byte[bufsiz];
			pktbase = 0;
		} else {
			pktbuf = pktniobuf.array();
			pktbase = pktniobuf.arrayOffset();
		}
		msgbase = (isTCP ? pktbase + TCPMSGLENSIZ : pktbase);
	}

	public void reset()
	{
		qcnt = 0;
		anscnt = 0;
		authcnt = 0;
		infocnt = 0;
		hdrflags = 0;
		msglen = 0;
		pktlen = 0;
	}
	
	public int endMessage(int off)
	{
		pktlen = off - pktbase;
		msglen = pktlen;

		if (isTCP) {
			msglen -= TCPMSGLENSIZ;
			encodeInt16(pktbase, msglen);	
		}
		return pktlen;
	}

	public int encodeHeader()
	{
		int off = msgbase;
		off = encodeInt16(off, qid);
		off = encodeInt16(off, hdrflags);
		off = encodeInt16(off, qcnt);
		off = encodeInt16(off, anscnt);
		off = encodeInt16(off, authcnt);
		off = encodeInt16(off, infocnt);
		return off;
	}
	
	public int decodeHeader()
	{
		int off = msgbase;
		qid = decodeInt16(off);
		off += 2;
		hdrflags = decodeInt16(off);
		off += 2;
		qcnt = decodeInt16(off);
		off += 2;
		anscnt = decodeInt16(off);
		off += 2;
		authcnt = decodeInt16(off);
		off += 2;
		infocnt = decodeInt16(off);
		off += 2;
		return off;
	}
	
	public int skipSection(int off, int sectiontype, int rrcnt)
	{
		for (int idx = 0; idx != rrcnt; idx++) {
			off = decodeName(off, null);
			off += 4; //skip past Type and Class
			
			if (sectiontype != SECT_QUESTIONS) {
				off += 4;  //skip past TTL
				int rrlen = decodeInt16(off);
				off += 2 + rrlen; // skip past both RDATA field and its length
			}
		}
		return off;
	}

	public int parseSection(int off, int sectiontype, int rrcnt, QueryHandle qryh, ResourceData rrbuf, com.grey.base.utils.ByteChars namebuf)
	{
		for (int idx = 0; idx != rrcnt; idx++) {
			if (namebuf != null) {
				namebuf.ar_off = 0;
				namebuf.ar_len = 0;
			}
			off = decodeName(off, namebuf);
			byte rrtype = (byte)decodeInt16(off);
			off += 2;
			byte rrclass = (byte)decodeInt16(off);
			off += 2;

			if (sectiontype == SECT_QUESTIONS) {
				qryh.rslvr.loadQuestion(qryh, rrtype, rrclass, idx, namebuf);
			} else {
				long ttl = decodeInt32(off);
				off += 4;
				int rrlen = decodeInt16(off);
				off += 2;
				ttl = (ttl * 1000) + qryh.dsptch.systime(); //convert from interval seconds to absolute system time
				rrbuf.set(this, off, rrtype, rrclass, ttl, rrlen, namebuf);
				off += rrlen;

				qryh.rslvr.loadRR(qryh, rrbuf, sectiontype, idx);
			}
		}
		return off;
	}
	
	public int encodeQuestion(int off, byte qtype, com.grey.base.utils.ByteChars domnam)
	{
		off = encodeName(off, domnam);
		off = encodeInt16(off, qtype);
		return encodeInt16(off, Packet.QCLASS_INET);
	}

	// we copy the domain name in literally first, then replace dots with label lengths
	public int encodeName(int off, com.grey.base.utils.ByteChars domnam)
	{
		int sizmark = off++;
		int endmark = off + domnam.ar_len;  // account for terminating NUL
		System.arraycopy(domnam.ar_buf, domnam.ar_off, pktbuf, off, domnam.ar_len);

		while (off != endmark)
		{
			if (pktbuf[off] == '.')
			{
				pktbuf[sizmark] = (byte)(off - sizmark - 1);
				sizmark = off;
			}
			off++;
		}
		pktbuf[sizmark] = (byte)(off - sizmark - 1);
		pktbuf[off] = 0;
		return off + 1;
	}

	// Don't bother checking for bounds violations while walking the name, as all we could do is throw, and Java already does that for us.
	// The question is how do we handle that, and that's up to the calling code.
	public int decodeName(int off, com.grey.base.utils.ByteChars domnam)
	{
		while (pktbuf[off] != 0)
		{
			// copy current label from packet to domnam - we're currently on the size byte that prefixes every label
			if (isCompressedLabel(off))
			{
				// compression - name branches to repeated sequence
				decodeName(compressedLabelPointer(off), domnam);
				return off + 2;
			}
			int len = pktbuf[off++];

			if (domnam != null)
			{
				byte[] dbuf = domnam.ar_buf;
				int doff = domnam.ar_off + domnam.ar_len;

				for (int idx = 0; idx != len; idx++)
				{
					dbuf[doff++] = (byte)Character.toLowerCase(pktbuf[off++]);
				}
				dbuf[doff++] = '.';
				domnam.ar_len += len + 1;
			}
			else
			{
				off += len;	
			}
		}
		if (domnam != null && domnam.ar_len != 0) domnam.ar_len--;  // strip final dot, which is DNS-speak for an absolute FQDN
		return off + 1;  // skip past terminating NUL
	}

	public int encodeIP(int off, int val)
	{
		com.grey.base.utils.IP.ip2net(val, pktbuf, off);
		return off + com.grey.base.utils.IP.IPADDR_OCTETS;
	}

	public int decodeIP(int off)
	{
		return com.grey.base.utils.IP.net2ip(pktbuf, off);
	}

	public int encodeInt16(int off, int val)
	{
		com.grey.base.utils.ByteOps.encodeInt(val, pktbuf, off, 2);
		return off + 2;
	}

	public int encodeInt32(int off, int val)
	{
		com.grey.base.utils.ByteOps.encodeInt(val, pktbuf, off, 4);
		return off + 4;
	}

	public int decodeInt16(int off)
	{
		return com.grey.base.utils.ByteOps.decodeInt(pktbuf, off, 2);
	}

	public int decodeInt32(int off)
	{
		return com.grey.base.utils.ByteOps.decodeInt(pktbuf, off, 4);
	}

	public int receive(boolean doReset, ServerHandle srvr, java.nio.channels.ReadableByteChannel chan) throws java.io.IOException
	{
		if (doReset) reset();
		int nbytes = 0;

		try {
			if (chan == null) {
				pktniobuf.clear();
				srvr.receive(pktniobuf);
				nbytes = pktniobuf.position();
			} else {
				// reset ByteBuffer if at start of message, so make sure we're not partway through a TCP response
				if (msglen == 0) {
					pktniobuf.clear();
					pktniobuf.limit(TCPMSGLENSIZ);
				}
				nbytes = chan.read(pktniobuf);  // returns -1 on EOF, while 0 simply means no data currently available
			}
		} catch (java.net.PortUnreachableException ex) {
			// this is likely to happen occasionally, so log something friendlier than a stack trace
			srvr.dsptch.logger.warn("DNS-Resolver: Configured DNS server appears to be down - "+srvr.address()+"/TCP="+isTCP);
		}
		if (nbytes <= 0) return nbytes;

		if (isTCP)
		{
			if (msglen == 0)
			{
				// if we didn't even get a large enough chunk to convey the 2-byte length, treat that as a failure by the remote peer
				if (nbytes == TCPMSGLENSIZ)
				{
					if (!pktniobuf.hasArray())
					{
						pktniobuf.position(0);
						pktniobuf.get(pktbuf, 0, TCPMSGLENSIZ);
					}
					msglen = decodeInt16(pktbase);
				}
				pktlen = msglen + TCPMSGLENSIZ;

				if ((msglen == 0) || (pktlen > bufsiz))
				{
					if (srvr.dsptch.logger.isDebugEnabled()) srvr.dsptch.logger.debug("Bad DNS/TCP msglen="+msglen+" (nbytes="+nbytes+") - max="+bufsiz);
					return -1;
				}
				pktniobuf.limit(pktlen);
				nbytes = chan.read(pktniobuf);
				if (nbytes == -1) return nbytes;  // EOF
			}
			if (pktniobuf.position() < pktlen) return 0;  // message not yet complete
		}
		else
		{
			msglen = nbytes;
			pktlen = msglen;
		}

		if (!pktniobuf.hasArray()) {
			pktniobuf.position(0);
			pktniobuf.get(pktbuf, 0, pktlen);
		}
		return nbytes;
	}

	public int send(ServerHandle srvr, java.nio.channels.WritableByteChannel tcpchan)
	{
		int errcod = 0;  // any non-zero value means error
		String errmsg = null;
		pktniobuf.limit(pktlen); //have to do this before possible bulk-put below

		// recall that if pktniobuf does have backing array, pktniobuf already points at it
		if (!pktniobuf.hasArray()) {
			pktniobuf.position(0);
			pktniobuf.put(pktbuf, 0, pktlen);
		}
		pktniobuf.position(0);

		try {
			if (isTCP) {
				// we never expect TCP channels to block, since we only write out one puny little DNS request packet per connection
				int nbytes = tcpchan.write(pktniobuf);
				if (nbytes != pktlen) errmsg = "Partial send: nbytes=" + nbytes;
			} else {
				srvr.send(pktniobuf, pktlen);
			}
		} catch (Throwable ex) {
			errmsg = com.grey.base.GreyException.summary(ex);
		}

		if (errmsg != null) {
			org.slf4j.Logger log = srvr.dsptch.logger;
			if (log.isDebugEnabled()) log.debug("DNS send: pktlen="+pktlen+" failed on proto="+(isTCP?"TCP":"UDP")+" - "+errmsg);
			errcod = 1;
		}
		return errcod;
	}
}
