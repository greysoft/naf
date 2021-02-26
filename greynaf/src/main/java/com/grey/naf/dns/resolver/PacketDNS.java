/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.IP;
import com.grey.base.collections.HashedMapIntValue;

// DNS protocol definitions and utility methods
// RFC-1035 is the main authority - See also http://www.iana.org/assignments/dns-parameters
public class PacketDNS
{
	public interface MessageCallback extends com.grey.naf.reactor.TimerNAF.TimeProvider
	{
		//Return=False means we are rejecting the question (and hence the packet)
		public boolean handleMessageQuestion(int qid, int qnum, int qcnt, byte qtype, byte qclass,
				com.grey.base.utils.ByteChars qname, java.net.InetSocketAddress remote_addr);
		//Return=True means the remaining RRs in this section can be discarded (ie. don't call handleMessageRR() for them)
		public boolean handleMessageRR(int qid, int sectiontype, int rrnum, int rrcnt,
				com.grey.base.utils.ByteChars rrname, ResourceData rr, java.net.InetSocketAddress remote_addr);
	}

	public static final int INETPORT = 53;
	public static final int UDPMAXMSG = 512;
	public static final int PKTHDRSIZ = 12;
	public static final int TCPMSGLENSIZ = 2; //TCP DNS message is prefixed with a 2-byte length field

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
	public static final int SECT_SUBQUERY = 5; //pseudo id indicating a subquery, not an actual packet section

	// RFC-4035 also defines AD=0x0020 and CD=0x0010, but we don't support them
	public static final int FLAGS_QR = 0x8000;
	public static final int FLAGS_AA = 0x0400;
	public static final int FLAGS_TC = 0x0200;
	public static final int FLAGS_RD = 0x0100;
	public static final int FLAGS_RA = 0x0080;

	private static final int FLDSIZ_RRTYPE = 2;
	private static final int FLDSIZ_RRCLASS = 2;
	private static final int FLDSIZ_RRTTL = 4;
	private static final int FLDSIZ_RRLEN = 2;
	private static final int FLDSIZ_MXPREF = 2;

	private static final String[] sect_txt = DynLoader.generateSymbolNames(PacketDNS.class, "SECT_", 5);
	public static String getSectionType(int stype) {return sect_txt[stype];}

	private byte[] pktbuf;
	private int hdrflags;
	private int msgbase; //offset of Packet header within pktbuf (excludes TCP length field)
	private int msglmt; //marks boundary of received message - only used for decoding mode
	private final long minttl_rr;

	int hdr_qid;
	int hdr_qcnt;
	int hdr_anscnt;
	int hdr_authcnt;
	int hdr_infocnt;

	//these fields are only used for the encoding mode
	private final HashedMapIntValue<String> cmprseqs = new HashedMapIntValue<String>(); //maps compressed name sequences to offsets
	private final java.nio.ByteBuffer xmtniobuf;
	private final byte[] xmtbuf;
	private final int pktbase; //offset of Packet within xmtbuf (includes TCP length field)
	private boolean is_tcp;
	private boolean use_compression;

	// these represent the last instance of these data types to be parsed by the decoder
	private final com.grey.base.utils.ByteChars rr_name = new com.grey.base.utils.ByteChars();
	private final ResourceData.RR_A rr_a = new ResourceData.RR_A(null, null);
	private final ResourceData.RR_PTR rr_ptr = new ResourceData.RR_PTR(null);
	private final ResourceData.RR_SOA rr_soa = new ResourceData.RR_SOA(null, null);
	private final ResourceData.RR_NS rr_ns = new ResourceData.RR_NS(null, null);
	private final ResourceData.RR_MX rr_mx = new ResourceData.RR_MX(null, null);
	private final ResourceData.RR_TXT rr_txt = new ResourceData.RR_TXT(null, null);
	private final ResourceData.RR_SRV rr_srv = new ResourceData.RR_SRV(null, null);
	private final ResourceData.RR_AAAA rr_aaaa = new ResourceData.RR_AAAA(null, null);
	private final ResourceData.RR_CNAME rr_cname = new ResourceData.RR_CNAME(null, null);

	// these are just temporary work areas, pre-allocated for efficiency
	private final com.grey.base.utils.ByteChars bctmp = new com.grey.base.utils.ByteChars();
	private final StringBuilder sbtmp = new StringBuilder();

	private static final int MASK_RCODE = 0xF;
	private static final int MASK_OPCODE = 0xF << 11;
	public int rcode() {return hdrflags & 0xF;}
	public void rcode(int val) {hdrflags = (hdrflags & ~MASK_RCODE) | (val & 0xF);}
	public int opcode() {return (hdrflags >> 11) & 0xF;}
	public void opcode(int val) {hdrflags = (hdrflags & ~MASK_OPCODE) | ((val & 0xF) << 11);}

	public boolean isResponse() {return ((hdrflags & FLAGS_QR) != 0);}
	public void setResponse() {hdrflags |= FLAGS_QR;}
	public boolean isAuth() {return ((hdrflags & FLAGS_AA) != 0);}
	public void setAuth() {hdrflags |= FLAGS_AA;}
	public boolean isTruncated() {return ((hdrflags & FLAGS_TC) != 0);}
	public void setTruncated() {hdrflags |= FLAGS_TC;}
	public boolean recursionDesired() {return ((hdrflags & FLAGS_RD) != 0);}
	public void setRecursionDesired() {hdrflags |= FLAGS_RD;}
	public boolean recursionAvailable() {return ((hdrflags & FLAGS_RA) != 0);}
	public void setRecursionAvailable() {hdrflags |= FLAGS_RA;}

	public int getQID() {return hdr_qid;}
	public int getQuestionCount() {return hdr_qcnt;}
	public int getAnswerCount() {return hdr_anscnt;}
	public int getAuthorityCount() {return hdr_authcnt;}
	public int getInfoCount() {return hdr_infocnt;}

	private static final int COMPRESSION_PTRPREFIX = 0xC0;
	private static final int FLDSIZ_PTRCOMPRESS = 2;
	private boolean isCompressedLabel(int off) {return ((pktbuf[off] & COMPRESSION_PTRPREFIX) != 0);}
	private int compressedLabelPointer(int off) {return msgbase + (decodeInt(off, FLDSIZ_PTRCOMPRESS) & ~(COMPRESSION_PTRPREFIX << 8));}

	public PacketDNS() {this(0);} //minttl=0 would suit users who never encode a Packet and don't parse non-Question sections
	public PacketDNS(long minttl) {this(0, false, minttl);} //would suit users who never encode a Packet

	public PacketDNS(int bsiz, boolean bdirect, long minttl)
	{
		minttl_rr = minttl;

		if (bsiz == 0) {
			xmtniobuf = null;
			xmtbuf = null;
			pktbase = 0;
			return;
		}
		xmtniobuf = com.grey.base.utils.NIOBuffers.create(bsiz, bdirect);

		if (!xmtniobuf.hasArray()) {
			xmtbuf = new byte[bsiz];
			pktbase = 0;
		} else {
			xmtbuf = xmtniobuf.array();
			pktbase = xmtniobuf.arrayOffset();
		}
	}

	public void resetEncoder(boolean tcp, boolean compress)
	{
		if (xmtbuf == null) throw new UnsupportedOperationException("This Packet is not set up for encoding/transmission");
		is_tcp = tcp;
		use_compression = compress;
		pktbuf = xmtbuf;
		msgbase = (is_tcp ? pktbase + TCPMSGLENSIZ : pktbase);
		reset();
	}

	public void resetDecoder(byte[] buf, int off, int len)
	{
		pktbuf = buf;
		msgbase = off;
		msglmt = off + len;
		reset();
	}

	public java.nio.ByteBuffer completeEncoding(int off)
	{
		int pktlen = off - pktbase;
		if (is_tcp) encodeInt(pktbase, pktlen - TCPMSGLENSIZ, TCPMSGLENSIZ); //go back to start to encode response-length
		xmtniobuf.limit(pktlen); //must do this before the possible bulk-put below

		// recall that if NIO buffer does have backing array, xmtniobuf already points at it
		if (!xmtniobuf.hasArray()) {
			xmtniobuf.position(0);
			xmtniobuf.put(pktbuf, 0, pktlen); //NB: bulk-put checks limit, but does not update it
		}
		xmtniobuf.position(0);
		return xmtniobuf;
	}

	public void setHeader(int qid, int qcnt, int anscnt, int authcnt, int infocnt)
	{
		hdr_qid = qid;
		hdr_qcnt = qcnt;
		hdr_anscnt = anscnt;
		hdr_authcnt = authcnt;
		hdr_infocnt = infocnt;
	}

	public int encodeHeader()
	{
		int off = msgbase;
		off = encodeInt(off, hdr_qid, 2);
		off = encodeInt(off, hdrflags, 2);
		off = encodeInt(off, hdr_qcnt, 2);
		off = encodeInt(off, hdr_anscnt, 2);
		off = encodeInt(off, hdr_authcnt, 2);
		off = encodeInt(off, hdr_infocnt, 2);
		return off;
	}

	public int decodeHeader()
	{
		int off = msgbase;
		hdr_qid = decodeInt(off, 2);
		off += 2;
		hdrflags = decodeInt(off, 2);
		off += 2;
		hdr_qcnt = decodeInt(off, 2);
		off += 2;
		hdr_anscnt = decodeInt(off, 2);
		off += 2;
		hdr_authcnt = decodeInt(off, 2);
		off += 2;
		hdr_infocnt = decodeInt(off, 2);
		off += 2;
		if (off > msglmt) return -1;
		return off;
	}

	public int skipSection(int off, int sectiontype, int rrcnt)
	{
		for (int idx = 0; idx != rrcnt; idx++) {
			if (off > msglmt) return -1;
			off = decodeName(off, null);
			off += FLDSIZ_RRTYPE + FLDSIZ_RRCLASS; //skip past Type and Class

			if (sectiontype != SECT_QUESTIONS) {
				off += FLDSIZ_RRTTL; //skip past TTL
				int rrlen = decodeInt(off, FLDSIZ_RRLEN);
				off += FLDSIZ_RRLEN + rrlen; //skip past both RDATA field and its length
			}
		}
		if (off > msglmt) return -1;
		return off;
	}

	//Despite the singular name, this will parse multiple questions, according to rrcnt
	public int parseQuestion(int off, int rrcnt, java.net.InetSocketAddress remote_addr, MessageCallback handler)
	{
		return parseSection(off, (byte)0, SECT_QUESTIONS, rrcnt, remote_addr, handler);
	}

	public int parseSection(int off, byte qtype, int sectiontype, int rrcnt, java.net.InetSocketAddress remote_addr, MessageCallback handler)
	{
		boolean skip = false;
		for (int idx = 0; idx != rrcnt; idx++) {
			// This initial NAME field will be overwritten by parseRDATA() below in most circumstances, but since we
			// don't yet know rrtype, we can't tell at this stage and it's not really worth calling decodeName() with
			// a null buffer and then going back to this offset to repeat it if needed.
			if (off > msglmt) return -1;
			if (skip) return skipSection(off, sectiontype, rrcnt - idx);
			rr_name.clear();
			off = decodeName(off, rr_name);
			byte rrtype = (byte)decodeInt(off, FLDSIZ_RRTYPE);
			off += FLDSIZ_RRTYPE;
			byte rrclass = (byte)decodeInt(off, FLDSIZ_RRCLASS);
			off += FLDSIZ_RRCLASS;

			if (sectiontype == SECT_QUESTIONS) {
				if (handler != null && !handler.handleMessageQuestion(hdr_qid, idx, rrcnt, rrtype, rrclass, rr_name, remote_addr)) return -1;
			} else {
				int ttl = decodeInt(off, FLDSIZ_RRTTL);
				off += FLDSIZ_RRTTL;
				int rrlen = decodeInt(off, FLDSIZ_RRLEN);
				off += FLDSIZ_RRLEN;
				ResourceData rr = parseRDATA(off, qtype, rrlen, rrtype, rrclass, ttl, handler);
				if (rr != null && handler != null) skip = handler.handleMessageRR(hdr_qid, sectiontype, idx, rrcnt, rr_name, rr, remote_addr);
				off += rrlen;
			}
		}
		if (off > msglmt) return -1;
		return off;
	}

	private ResourceData parseRDATA(int off, byte qtype, int datalen, byte rrtype, byte clss, int raw_ttl, MessageCallback handler)
	{
		ResourceData rrbuf = null;
		if (rrtype == ResolverDNS.QTYPE_SOA) {
			if (qtype != ResolverDNS.QTYPE_SOA) return null;
			rrbuf = rr_soa;
		} else if (rrtype == ResolverDNS.QTYPE_A) {
			rrbuf = rr_a;
		} else if (rrtype == ResolverDNS.QTYPE_PTR) {
			rrbuf = rr_ptr;
		} else if (rrtype == ResolverDNS.QTYPE_NS) {
			rrbuf = rr_ns;
		} else if (rrtype == ResolverDNS.QTYPE_MX) {
			rrbuf = rr_mx;
		} else if (rrtype == ResolverDNS.QTYPE_TXT) {
			rrbuf = rr_txt;
		} else if (rrtype == ResolverDNS.QTYPE_SRV) {
			rrbuf = rr_srv;
		} else if (rrtype == ResolverDNS.QTYPE_CNAME) {
			rrbuf = rr_cname;
		} else if (rrtype == ResolverDNS.QTYPE_AAAA) {
			if (qtype != ResolverDNS.QTYPE_AAAA) return null; //ignore Additional AAAAs
			rrbuf = rr_aaaa;
		} else {
			return null; //unsupported RR
		}
		long systime = (handler == null ? System.currentTimeMillis() : handler.getSystemTime());
		if (raw_ttl < minttl_rr) raw_ttl = (int)minttl_rr;
		rrbuf.reset(clss);
		rrbuf.setExpiry(raw_ttl, systime);
		if (rrtype != ResolverDNS.QTYPE_PTR) rrbuf.setName(rr_name);

		switch (rrtype)
		{
		case ResolverDNS.QTYPE_A:
			rr_a.setIP(decodeIP(off)); //datalen is assumed to be 4 octets
			break;

		case ResolverDNS.QTYPE_AAAA:
			rr_aaaa.setIP6(pktbuf, off); //datalen is assumed to be 16 octets
			break;

		case ResolverDNS.QTYPE_PTR:
			decodeName(off, rrbuf.getName());
			break;

		case ResolverDNS.QTYPE_NS:
			decodeName(off, rr_ns.hostname);
			break;

		case ResolverDNS.QTYPE_CNAME:
			decodeName(off, rr_cname.hostname);
			break;

		case ResolverDNS.QTYPE_MX:
			rr_mx.pref = decodeInt(off, FLDSIZ_MXPREF);
			off += FLDSIZ_MXPREF;
			decodeName(off, rr_mx.relay);
			break;

		case ResolverDNS.QTYPE_TXT:
			//This is a sequence of counted strings (though invariably only one string)
			int lmt = off + datalen;
			while (off != lmt) {
				int len = pktbuf[off++];
				rr_txt.addData(new String(pktbuf, off, len));
				off += len;
			}
			break;

		case ResolverDNS.QTYPE_SOA:
			off = decodeName(off, rr_soa.mname);
			off = decodeName(off, rr_soa.rname);
			rr_soa.serial = decodeInt(off, 4);
			off += 4;
			rr_soa.refresh = decodeInt(off, 4);
			off += 4;
			rr_soa.retry = decodeInt(off, 4);
			off += 4;
			rr_soa.expire = decodeInt(off, 4);
			off += 4;
			rr_soa.minttl = decodeInt(off, 4);
			break;

		case ResolverDNS.QTYPE_SRV:
			rr_srv.priority = decodeInt(off, 2);
			off += 2;
			rr_srv.weight = decodeInt(off, 2);
			off += 2;
			rr_srv.port = decodeInt(off, 2);
			off += 2;
			decodeName(off, rr_srv.target);
			break;

		default:
			// ignore unrecognised RDATA
			return null;
		}
		return rrbuf;
	}

	public int encodeQuestion(int off, byte qtype, com.grey.base.utils.ByteChars domnam)
	{
		off = encodeName(off, domnam);
		off = encodeInt(off, qtype, FLDSIZ_RRTYPE);
		return encodeInt(off, PacketDNS.QCLASS_INET, FLDSIZ_RRCLASS);
	}

	public int encodeSection(int off, int sectiontype, ResourceData[] rr, com.grey.naf.reactor.TimerNAF.TimeProvider time)
	{
		for (int idx = 0; idx != rr.length; idx++) {
			off = encodeRR(off, rr[idx], time);
		}
		return off;
	}

	private int encodeRR(int off, ResourceData rr, com.grey.naf.reactor.TimerNAF.TimeProvider time)
	{
		long systime = (time == null ? System.currentTimeMillis() : time.getSystemTime());
		int ttl = rr.getTTL(systime);
		if (ttl < 0) ttl = 0;
		if (rr.rrType() == ResolverDNS.QTYPE_PTR) {
			off = encodeArpaDomain(off, rr.getIP());
		} else {
			off = encodeName(off, rr.getName());
		}
		off = encodeInt(off, rr.rrType(), FLDSIZ_RRTYPE);
		off = encodeInt(off, rr.rrClass(), FLDSIZ_RRCLASS);
		off = encodeInt(off, ttl, FLDSIZ_RRTTL);
		int off_rrlen = off;
		off += FLDSIZ_RRLEN; //skip past RR length field for now
		off = encodeRDATA(off, rr);
		encodeInt(off_rrlen, off - off_rrlen - FLDSIZ_RRLEN, FLDSIZ_RRLEN);
		return off;
	}

	private int encodeRDATA(int off, ResourceData rr)
	{
		switch (rr.rrType()) {
		case ResolverDNS.QTYPE_A: {
			off = encodeIP(off, rr.getIP());
			break;
		}
		case ResolverDNS.QTYPE_AAAA: {
			ResourceData.RR_AAAA rrdef = (ResourceData.RR_AAAA)rr;
			byte[] addr = rrdef.getIP6();
			System.arraycopy(addr, 0, pktbuf, off, addr.length);
			off += addr.length;
			break;
		}
		case ResolverDNS.QTYPE_PTR: {
			off = encodeName(off, rr.getName());
			break;
		}
		case ResolverDNS.QTYPE_MX: {
			ResourceData.RR_MX rrdef = (ResourceData.RR_MX)rr;
			off = encodeInt(off, rrdef.pref, FLDSIZ_MXPREF);
			off = encodeName(off, rrdef.relay);
			break;
		}
		case ResolverDNS.QTYPE_CNAME:
		case ResolverDNS.QTYPE_NS: {
			ResourceData.RR_NS rrdef = (ResourceData.RR_NS)rr;
			off = encodeName(off, rrdef.hostname);
			break;
		}
		case ResolverDNS.QTYPE_TXT: {
			ResourceData.RR_TXT rrdef = (ResourceData.RR_TXT)rr;
			for (int idx = 0; idx != rrdef.count(); idx++) {
				String rec = rrdef.getData(idx);
				pktbuf[off++] = (byte)rec.length();
				for (int idx2 = 0; idx2 != rec.length(); idx2++) {
					pktbuf[off++] = (byte)rec.charAt(idx2);
				}
			}
			break;
		}
		case ResolverDNS.QTYPE_SOA: {
			ResourceData.RR_SOA rrdef = (ResourceData.RR_SOA)rr;
			off = encodeName(off, rrdef.mname);
			off = encodeName(off, rrdef.rname);
			off = encodeInt(off, rrdef.serial, 4);
			off = encodeInt(off, rrdef.refresh, 4);
			off = encodeInt(off, rrdef.retry, 4);
			off = encodeInt(off, rrdef.expire, 4);
			off = encodeInt(off, rrdef.minttl, 4);
			break;
		}
		case ResolverDNS.QTYPE_SRV: {
			ResourceData.RR_SRV rrdef = (ResourceData.RR_SRV)rr;
			off = encodeInt(off, rrdef.priority, 2);
			off = encodeInt(off, rrdef.weight, 2);
			off = encodeInt(off, rrdef.port, 2);
			off = encodeName(off, rrdef.target);
			break;
		}
		default:
			throw new UnsupportedOperationException(getClass().getName()+" cannot encode RR type="+rr.rrType());
		}
		return off;
	}

	private int encodeName(int off_pkt, com.grey.base.utils.ByteChars domnam)
	{
		if (domnam != null && domnam.size() != 0) { //beware, the root domain name has no labels
			int labelcnt = domnam.count(ResolverDNS.DOMDLM) + 1;
			int off_label = 0;
			for (int loop = 0; loop != labelcnt; loop++) {
				if (use_compression) {
					String trailseq = domnam.toString(off_label, domnam.size() - off_label);
					int cmpr_ptr = cmprseqs.get(trailseq);
					if (cmpr_ptr == 0) { //a name will never begin at start of packet
						cmprseqs.put(trailseq, off_pkt - msgbase);
					} else {
						encodeInt(off_pkt, cmpr_ptr, FLDSIZ_PTRCOMPRESS);
						pktbuf[off_pkt] |= COMPRESSION_PTRPREFIX;
						return off_pkt + FLDSIZ_PTRCOMPRESS;
					}
				}
				int lmt_label = (loop == labelcnt - 1 ? domnam.size() : domnam.indexOf(off_label, ResolverDNS.DOMDLM));
				int len_label = lmt_label - off_label;
				pktbuf[off_pkt++] = (byte)len_label;
				System.arraycopy(domnam.buffer(), domnam.offset(off_label), pktbuf, off_pkt, len_label);
				off_pkt += len_label;
				off_label += len_label + 1;
			}
		}
		pktbuf[off_pkt++] = 0;
		return off_pkt;
	}

	private int decodeName(int off, com.grey.base.utils.ByteChars domnam)
	{
		while (pktbuf[off] != 0) {
			// copy current label from packet to domnam - we're currently on the size byte that prefixes every label
			if (isCompressedLabel(off)) {
				// compression - this name branches to a repeated sequence
				decodeName(compressedLabelPointer(off), domnam);
				return off + FLDSIZ_PTRCOMPRESS;
			}
			int len = pktbuf[off++];

			if (domnam != null) {
				domnam.ensureCapacity(domnam.size() + len + 1);
				byte[] dbuf = domnam.buffer();
				int doff = domnam.limit();
				for (int idx = 0; idx != len; idx++) {
					dbuf[doff++] = (byte)Character.toLowerCase(pktbuf[off++]);
				}
				if (pktbuf[off] != 0) { //there is another label to come
					dbuf[doff] = ResolverDNS.DOMDLM;
					domnam.incrementSize(1);
				}
				domnam.incrementSize(len);
			} else {
				off += len;
			}
		}
		return off + 1; //skip past terminating NUL
	}

	private int encodeArpaDomain(int off, int ip)
	{
		sbtmp.setLength(0);
		IP.displayArpaDomain(ip, sbtmp);
		bctmp.clear().append(sbtmp);
		return encodeName(off, bctmp);
	}

	private int encodeIP(int off, int ip)
	{
		com.grey.base.utils.IP.ip2net(ip, pktbuf, off);
		return off + com.grey.base.utils.IP.IPADDR_OCTETS;
	}

	private int decodeIP(int off)
	{
		return com.grey.base.utils.IP.net2ip(pktbuf, off);
	}

	private int encodeInt(int off, int val, int siz)
	{
		return com.grey.base.utils.ByteOps.encodeInt(val, pktbuf, off, siz);
	}

	private int decodeInt(int off, int siz)
	{
		return com.grey.base.utils.ByteOps.decodeInt(pktbuf, off, siz);
	}

	private void reset()
	{
		hdrflags = 0;
		cmprseqs.clear();
		setHeader(0, 0, 0, 0, 0);
	}
}
