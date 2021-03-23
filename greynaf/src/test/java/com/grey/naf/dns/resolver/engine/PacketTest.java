/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.engine;

import com.grey.base.utils.ByteOps;
import com.grey.base.utils.ByteChars;
import com.grey.base.utils.DynLoader;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.engine.PacketDNS;
import com.grey.naf.dns.resolver.engine.ResourceData;
import com.grey.naf.reactor.TimerNAF;

import java.net.InetSocketAddress;
import java.time.Clock;

public class PacketTest
{
	private static final TimerNAF.TimeProvider TimeProvider = new TimerNAF.TimeProvider() {
		private final Clock clock = Clock.systemUTC();
		@Override
		public long getRealTime() {return clock.millis();}
		@Override
		public long getSystemTime() {return getRealTime();}
	};

	@org.junit.Test
	public void testHeader() {
		verifyHeader(false, false);
		verifyHeader(false, true);
		verifyHeader(true, false);
		verifyHeader(true, true);
	}

	@org.junit.Test
	public void testQuestion() {
		PacketDNS pkt = new PacketDNS(200, false, 0, TimeProvider);
		for (int loop = 0; loop != 2; loop++) {
			verifyQuestion(false, pkt, pkt);
			verifyQuestion(true, pkt, pkt);
		}
		pkt = new PacketDNS(200, true, 0, TimeProvider);
		for (int loop = 0; loop != 2; loop++) {
			verifyQuestion(false, pkt, pkt);
			verifyQuestion(true, pkt, pkt);
		}
	}

	@org.junit.Test
	public void testCompressedNames() throws Exception {
		verifyCompressedNames(false, false);
		verifyCompressedNames(false, true);
		verifyCompressedNames(true, false);
		verifyCompressedNames(true, true);
	}

	private void verifyQuestion(boolean is_tcp, PacketDNS pkt, PacketDNS pkt2) {
		verifyQuestion(false, is_tcp, pkt, pkt2);
		verifyQuestion(true, is_tcp, pkt, pkt2);
	}

	private void verifyQuestion(boolean use_compression, boolean is_tcp, PacketDNS pkt, PacketDNS pkt2) {
		int qid = Short.MAX_VALUE;
		byte qtype = ResolverDNS.QTYPE_A;
		String qname = "anyoldname.nowhere.nosuchdomain";
		pkt.resetEncoder(is_tcp, use_compression);
		pkt.setRecursionDesired();
		pkt.setHeader(qid, 1, 0, 0, 0);
		int off = pkt.encodeHeader();
		off = pkt.encodeQuestion(off, qtype, new ByteChars(qname));
		java.nio.ByteBuffer niobuf = pkt.completeEncoding(off);
		org.junit.Assert.assertEquals(0, niobuf.position());
		byte[] barr = new byte[niobuf.limit()];
		niobuf.get(barr, 0, barr.length);
		int boff = (is_tcp ? PacketDNS.TCPMSGLENSIZ : 0);

		ParseQuestionHandler cb = new ParseQuestionHandler();
		pkt2.resetDecoder(barr, boff, barr.length - boff);
		off = pkt2.decodeHeader();
		org.junit.Assert.assertEquals(PacketDNS.PKTHDRSIZ + boff, off);
		org.junit.Assert.assertEquals(qid, pkt2.getQID());
		org.junit.Assert.assertEquals(0, pkt2.opcode());
		org.junit.Assert.assertEquals(0, pkt2.rcode());
		org.junit.Assert.assertFalse(pkt2.isResponse());
		org.junit.Assert.assertFalse(pkt2.isAuth());
		org.junit.Assert.assertFalse(pkt2.isTruncated());
		org.junit.Assert.assertTrue(pkt2.recursionDesired());
		org.junit.Assert.assertFalse(pkt2.recursionAvailable());
		org.junit.Assert.assertEquals(1, pkt2.getQuestionCount());
		org.junit.Assert.assertEquals(0, pkt2.getAnswerCount());
		org.junit.Assert.assertEquals(0, pkt2.getAuthorityCount());
		org.junit.Assert.assertEquals(0, pkt2.getInfoCount());
		off = pkt2.parseQuestion(off, pkt2.getQuestionCount(), null, cb);
		org.junit.Assert.assertEquals(barr.length, off);
		org.junit.Assert.assertEquals(1, pkt2.getQuestionCount());
		org.junit.Assert.assertEquals(pkt2.getQuestionCount(), cb.qcallcnt);
		org.junit.Assert.assertEquals(0, cb.rrcallcnt);
		org.junit.Assert.assertEquals(0, cb.cb_qnum);
		org.junit.Assert.assertEquals(pkt2.getQuestionCount(), cb.cb_qcnt);
		org.junit.Assert.assertEquals(qid, cb.cb_qid);
		org.junit.Assert.assertEquals(qtype, cb.cb_qtype);
		org.junit.Assert.assertEquals(PacketDNS.QCLASS_INET, cb.cb_qclass);
		org.junit.Assert.assertEquals(qname, cb.cb_qname.toString());
		org.junit.Assert.assertNull(cb.cb_addr);

		// decode again, with a Skip action on the questions section
		cb = new ParseQuestionHandler();
		pkt2.resetDecoder(barr, boff, barr.length - boff);
		off = pkt2.decodeHeader();
		org.junit.Assert.assertEquals(PacketDNS.PKTHDRSIZ + boff, off);
		org.junit.Assert.assertEquals(1, pkt2.getQuestionCount());
		off = pkt2.skipSection(off, PacketDNS.SECT_QUESTIONS, pkt2.getQuestionCount());
		org.junit.Assert.assertEquals(barr.length, off);
		org.junit.Assert.assertEquals(1, pkt2.getQuestionCount());
		org.junit.Assert.assertEquals(0, cb.qcallcnt);
		org.junit.Assert.assertEquals(0, cb.rrcallcnt);
	}

	// This is based on the example in RFC 1035 section 4.1.4
	// We're not concerned with header contents or a valid preamble to the encoded names here. We just want to
	// initialise the packet buffer sufficiently to establish offsets.
	private void verifyCompressedNames(boolean is_tcp, boolean directbufs) throws Exception {
		java.lang.reflect.Method meth_encode = PacketDNS.class.getDeclaredMethod("encodeName", int.class, ByteChars.class);
		meth_encode.setAccessible(true);
		java.lang.reflect.Method meth_decode = PacketDNS.class.getDeclaredMethod("decodeName", int.class, ByteChars.class);
		meth_decode.setAccessible(true);
		String name_f = "F.ISI.ARPA";
		String label_foo = "FOO";
		String name_foo = label_foo+"."+name_f;
		String name_arpa = "ARPA";
		String name_last = "Final_Single-Label-Name";
		PacketDNS pkt = new PacketDNS(200, directbufs, 0, TimeProvider); //more than big enough
		byte filler = 125;

		// encode the names with compression enabled
		pkt.resetEncoder(is_tcp, true);
		byte[] pktbuf = (byte[])DynLoader.getField(pkt, "pktbuf");
		int msgbase = (int)DynLoader.getField(pkt, "msgbase");
		int off_f = pkt.encodeHeader();
		java.util.Arrays.fill(pktbuf, filler);
		int off_foo = (int)meth_encode.invoke(pkt, off_f, new ByteChars(name_f));
		int off_arpa = (int)meth_encode.invoke(pkt, off_foo, new ByteChars(name_foo));
		int off_root = (int)meth_encode.invoke(pkt, off_arpa, new ByteChars(name_arpa));
		int off_last = (int)meth_encode.invoke(pkt, off_root, new ByteChars("")); //encode the root domain name
		//this next one is not part of the RFC-1035 example, but I want to follow the root domain name with another name
		int lmt = (int)meth_encode.invoke(pkt, off_last, new ByteChars(name_last));
		int enclen = lmt - off_f;

		//manually verify the encoded names
		int ptr_arpa = off_f + 6; //calculate where the ARPA label should begin (F+ISI plus 2 label sizes)
		int off = off_f;
		for (String label : new String[]{"F", "ISI", name_arpa}) {
			if (label.equals(name_arpa)) org.junit.Assert.assertEquals(ptr_arpa, off); //sanity check ptr_arpa calculation
			org.junit.Assert.assertEquals(label.length(), pktbuf[off++]);
			org.junit.Assert.assertEquals(label, new ByteChars(pktbuf, off, label.length(), false).toString());
			off += label.length();
		}
		org.junit.Assert.assertEquals(0, pktbuf[off++]); //terminating NUL of F.ISI.ARPA
		String label = label_foo;
		org.junit.Assert.assertEquals(off_foo, off);
		org.junit.Assert.assertEquals(label.length(), pktbuf[off++]);
		org.junit.Assert.assertEquals(label, new ByteChars(pktbuf, off, label.length(), false).toString());
		off += label.length();
		org.junit.Assert.assertEquals(0xC0 & 0xff, pktbuf[off++] & 0xff); //next comes compressed pointer to name_f
		org.junit.Assert.assertEquals(off_f - msgbase, pktbuf[off++]);
		org.junit.Assert.assertEquals(off_arpa, off); //next comes "ARPA", represented entirely by a compressed pointer
		org.junit.Assert.assertEquals(0xC0 & 0xff, pktbuf[off++] & 0xff);
		org.junit.Assert.assertEquals(ptr_arpa - msgbase, pktbuf[off++]);
		org.junit.Assert.assertEquals(off_root, off); //next is the root domain name
		org.junit.Assert.assertEquals(0, pktbuf[off++]);
		label = name_last;
		org.junit.Assert.assertEquals(off_last, off);
		org.junit.Assert.assertEquals(label.length(), pktbuf[off++]);
		org.junit.Assert.assertEquals(label, new ByteChars(pktbuf, off, label.length(), false).toString());
		off += label.length();
		org.junit.Assert.assertEquals(0, pktbuf[off++]); //terminating NUL of final name
		org.junit.Assert.assertEquals(lmt, off);
		for (int idx = 0; idx != off_f; idx++) org.junit.Assert.assertEquals(filler, pktbuf[idx]);
		for (int idx = lmt; idx != pktbuf.length; idx++) org.junit.Assert.assertEquals(filler, pktbuf[idx]);

		//now test the decoding of the compressed names - first is F.ISI.ARPA
		ByteChars domnam = new ByteChars(1);
		pkt.resetDecoder(pktbuf, msgbase, enclen);
		off_f = pkt.decodeHeader();
		off = (int)meth_decode.invoke(pkt, off_f, domnam);
		org.junit.Assert.assertEquals(name_f.toLowerCase(), domnam.toString());
		//then FOO.F.ISI.ARPA
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(name_foo.toLowerCase(), domnam.toString());
		//then ARPA
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(name_arpa.toLowerCase(), domnam.toString());
		//then the root domain
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(0, domnam.size());
		//then the final name we encoded
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(name_last.toLowerCase(), domnam.toString());
		org.junit.Assert.assertEquals(enclen, off - off_f);

		// encode the names with compression disabled
		pkt.resetEncoder(is_tcp, false);
		pktbuf = (byte[])DynLoader.getField(pkt, "pktbuf");
		msgbase = (int)DynLoader.getField(pkt, "msgbase");
		off_f = pkt.encodeHeader();
		off = (int)meth_encode.invoke(pkt, off_f, new ByteChars(name_f));
		off = (int)meth_encode.invoke(pkt, off, new ByteChars(name_foo));
		off = (int)meth_encode.invoke(pkt, off, new ByteChars(name_arpa));
		off = (int)meth_encode.invoke(pkt, off, new ByteChars("")); //encode the root domain name
		off = (int)meth_encode.invoke(pkt, off, new ByteChars(name_last));
		int enclen2 = off - off_f;
		org.junit.Assert.assertTrue(enclen2 > enclen);

		//now test the decoding of the uncompressed names
		pkt.resetDecoder(pktbuf, msgbase, enclen);
		off_f = pkt.decodeHeader();
		off = (int)meth_decode.invoke(pkt, off_f, domnam.clear());
		org.junit.Assert.assertEquals(name_f.toLowerCase(), domnam.toString());
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(name_foo.toLowerCase(), domnam.toString());
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(name_arpa.toLowerCase(), domnam.toString());
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(0, domnam.size());
		off = (int)meth_decode.invoke(pkt, off, domnam.clear());
		org.junit.Assert.assertEquals(name_last.toLowerCase(), domnam.toString());
		org.junit.Assert.assertEquals(enclen2, off - off_f);
	}

	private void verifyHeader(boolean is_tcp, boolean directbufs) {
		int pktsiz = PacketDNS.PKTHDRSIZ;
		if (is_tcp) pktsiz += PacketDNS.TCPMSGLENSIZ;
		PacketDNS pkt = new PacketDNS(pktsiz+10, directbufs, 5, TimeProvider);
		verifyBlankHeader(pkt);
		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.opcode(0xf);
		org.junit.Assert.assertEquals(0xf, pkt.opcode());
		org.junit.Assert.assertEquals(0, pkt.rcode());
		org.junit.Assert.assertFalse(pkt.isResponse());
		org.junit.Assert.assertFalse(pkt.isAuth());
		org.junit.Assert.assertFalse(pkt.isTruncated());
		org.junit.Assert.assertFalse(pkt.recursionDesired());
		org.junit.Assert.assertFalse(pkt.recursionAvailable());

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.rcode(0xf);
		org.junit.Assert.assertEquals(0xf, pkt.rcode());
		org.junit.Assert.assertEquals(0, pkt.opcode());
		org.junit.Assert.assertFalse(pkt.isResponse());
		org.junit.Assert.assertFalse(pkt.isAuth());
		org.junit.Assert.assertFalse(pkt.isTruncated());
		org.junit.Assert.assertFalse(pkt.recursionDesired());
		org.junit.Assert.assertFalse(pkt.recursionAvailable());

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.setResponse();
		org.junit.Assert.assertEquals(0, pkt.rcode());
		org.junit.Assert.assertEquals(0, pkt.opcode());
		org.junit.Assert.assertTrue(pkt.isResponse());
		org.junit.Assert.assertFalse(pkt.isAuth());
		org.junit.Assert.assertFalse(pkt.isTruncated());
		org.junit.Assert.assertFalse(pkt.recursionDesired());
		org.junit.Assert.assertFalse(pkt.recursionAvailable());

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.setAuth();
		org.junit.Assert.assertEquals(0, pkt.rcode());
		org.junit.Assert.assertEquals(0, pkt.opcode());
		org.junit.Assert.assertFalse(pkt.isResponse());
		org.junit.Assert.assertTrue(pkt.isAuth());
		org.junit.Assert.assertFalse(pkt.isTruncated());
		org.junit.Assert.assertFalse(pkt.recursionDesired());
		org.junit.Assert.assertFalse(pkt.recursionAvailable());

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.setTruncated();
		org.junit.Assert.assertEquals(0, pkt.rcode());
		org.junit.Assert.assertEquals(0, pkt.opcode());
		org.junit.Assert.assertFalse(pkt.isResponse());
		org.junit.Assert.assertFalse(pkt.isAuth());
		org.junit.Assert.assertTrue(pkt.isTruncated());
		org.junit.Assert.assertFalse(pkt.recursionDesired());
		org.junit.Assert.assertFalse(pkt.recursionAvailable());

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.setRecursionDesired();
		org.junit.Assert.assertEquals(0, pkt.rcode());
		org.junit.Assert.assertEquals(0, pkt.opcode());
		org.junit.Assert.assertFalse(pkt.isResponse());
		org.junit.Assert.assertFalse(pkt.isAuth());
		org.junit.Assert.assertFalse(pkt.isTruncated());
		org.junit.Assert.assertTrue(pkt.recursionDesired());
		org.junit.Assert.assertFalse(pkt.recursionAvailable());

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.setRecursionAvailable();
		org.junit.Assert.assertEquals(0, pkt.rcode());
		org.junit.Assert.assertEquals(0, pkt.opcode());
		org.junit.Assert.assertFalse(pkt.isResponse());
		org.junit.Assert.assertFalse(pkt.isAuth());
		org.junit.Assert.assertFalse(pkt.isTruncated());
		org.junit.Assert.assertFalse(pkt.recursionDesired());
		org.junit.Assert.assertTrue(pkt.recursionAvailable());

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		pkt.setResponse();
		pkt.opcode(9);
		pkt.rcode(6);
		pkt.setTruncated();
		pkt.setRecursionAvailable();
		pkt.setHeader(251, 1, 2, 3, 4);
		int off = pkt.encodeHeader();
		java.nio.ByteBuffer niobuf = pkt.completeEncoding(off);
		org.junit.Assert.assertEquals(pktsiz, niobuf.limit());
		org.junit.Assert.assertEquals(0, niobuf.position());
		int tcp_incr = (is_tcp ? PacketDNS.TCPMSGLENSIZ : 0);
		int boff = 1; //add an extra unused byte at both ends of array
		byte[] barr = new byte[pktsiz+boff+1];
		niobuf.get(barr, boff, pktsiz);
		if (is_tcp) org.junit.Assert.assertEquals(pktsiz - PacketDNS.TCPMSGLENSIZ, ByteOps.decodeInt(barr, boff, PacketDNS.TCPMSGLENSIZ));

		PacketDNS pkt2 = new PacketDNS(TimeProvider);
		boff += tcp_incr;
		pkt2.resetDecoder(barr, boff, pktsiz - tcp_incr);
		off = pkt2.decodeHeader();
		org.junit.Assert.assertEquals(PacketDNS.PKTHDRSIZ+boff, off);
		org.junit.Assert.assertEquals(251, pkt2.getQID());
		org.junit.Assert.assertEquals(9, pkt2.opcode());
		org.junit.Assert.assertEquals(6, pkt2.rcode());
		org.junit.Assert.assertTrue(pkt2.isResponse());
		org.junit.Assert.assertFalse(pkt2.isAuth());
		org.junit.Assert.assertTrue(pkt2.isTruncated());
		org.junit.Assert.assertFalse(pkt2.recursionDesired());
		org.junit.Assert.assertTrue(pkt2.recursionAvailable());
		org.junit.Assert.assertEquals(1, pkt2.getQuestionCount());
		org.junit.Assert.assertEquals(2, pkt2.getAnswerCount());
		org.junit.Assert.assertEquals(3, pkt2.getAuthorityCount());
		org.junit.Assert.assertEquals(4, pkt2.getInfoCount());
		try {
			pkt2.resetEncoder(is_tcp, false);
			org.junit.Assert.fail("resetEncoder() is expected to fail on read-only Packet");
		} catch (UnsupportedOperationException ex) {}

		pkt.resetEncoder(is_tcp, false);
		verifyBlankHeader(pkt);
		org.junit.Assert.assertEquals(0, DynLoader.getField(pkt, "hdrflags"));
		pkt.opcode(0xf);
		org.junit.Assert.assertEquals(0xf, pkt.opcode());
		pkt.opcode(0);
		verifyBlankHeader(pkt);
		org.junit.Assert.assertEquals(0, DynLoader.getField(pkt, "hdrflags"));
		pkt.rcode(0xf);
		org.junit.Assert.assertEquals(0xf, pkt.rcode());
		pkt.rcode(0);
		verifyBlankHeader(pkt);
		org.junit.Assert.assertEquals(0, DynLoader.getField(pkt, "hdrflags"));
	}

	private void verifyBlankHeader(PacketDNS pkt) {
		org.junit.Assert.assertEquals(0, pkt.getQID());
		org.junit.Assert.assertEquals(0, pkt.opcode());
		org.junit.Assert.assertEquals(0, pkt.rcode());
		org.junit.Assert.assertFalse(pkt.isResponse());
		org.junit.Assert.assertFalse(pkt.isAuth());
		org.junit.Assert.assertFalse(pkt.isTruncated());
		org.junit.Assert.assertFalse(pkt.recursionDesired());
		org.junit.Assert.assertFalse(pkt.recursionAvailable());
		org.junit.Assert.assertEquals(0, pkt.getQuestionCount());
		org.junit.Assert.assertEquals(0, pkt.getAnswerCount());
		org.junit.Assert.assertEquals(0, pkt.getAuthorityCount());
		org.junit.Assert.assertEquals(0, pkt.getInfoCount());
	}


	private static class ParseQuestionHandler implements PacketDNS.MessageCallback {
		public int qcallcnt;
		public int rrcallcnt;
		public int cb_qnum;
		public int cb_qcnt;
		public int cb_qid;
		public byte cb_qtype;
		public byte cb_qclass;
		public ByteChars cb_qname;
		public Object cb_addr;

		public ParseQuestionHandler() {}

		@Override
		public boolean handleMessageQuestion(int qid, int qnum, int qcnt, byte qtype, byte qclass, ByteChars qname, InetSocketAddress addr) {
			qcallcnt++;
			cb_qnum = qnum;
			cb_qcnt = qcnt;
			cb_qid = qid;
			cb_qtype = qtype;
			cb_qclass = qclass;
			cb_qname = new ByteChars(qname);
			cb_addr = addr;
			return true;
		}

		@Override
		public boolean handleMessageRR(int qid, int sectiontype, int rrnum, int rrcnt, ByteChars rrname,ResourceData rr, InetSocketAddress addr) {
			rrcallcnt++;
			return false;
		}
	}
}