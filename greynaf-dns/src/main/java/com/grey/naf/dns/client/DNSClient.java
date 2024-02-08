/*
 * Copyright 2014-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.client;

import java.util.concurrent.CompletableFuture;
import java.io.IOException;

import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.engine.ResolverAnswer;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;
import com.grey.naf.errors.NAFException;
import com.grey.base.utils.ByteChars;
import com.grey.logging.Logger;

public class DNSClient
{
	private final Dispatcher dsptch;
	private final Producer<RequestBlock> reqSubmitter; //the consumer side of this runs inside Dispatcher

	public DNSClient(ResolverDNS resolver) throws IOException {
		dsptch = resolver.getMasterDispatcher();
		RequestHandler reqHandler = new RequestHandler(resolver);
		reqSubmitter = new Producer<>("DNS-Client-requests", dsptch, reqHandler);
		dsptch.loadRunnable(reqSubmitter);
	}

	// Can be called from any thread and is safe to call multiple times.
	public void shutdown() throws IOException {
		dsptch.unloadRunnable(reqSubmitter);
	}

	public CompletableFuture<ResolverAnswer> resolveHostname(CharSequence hostname) throws IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_A, hostname);
	}

	public CompletableFuture<ResolverAnswer> resolveNameServer(CharSequence domnam) throws IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_NS, domnam);
	}

	public CompletableFuture<ResolverAnswer> resolveMailDomain(CharSequence maildom) throws IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_MX, maildom);
	}

	public CompletableFuture<ResolverAnswer> resolveSOA(CharSequence domnam) throws IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_SOA, domnam);
	}

	public CompletableFuture<ResolverAnswer> resolveSRV(CharSequence domnam) throws IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_SRV, domnam);
	}

	public CompletableFuture<ResolverAnswer> resolveTXT(CharSequence domnam) throws IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_TXT, domnam);
	}

	public CompletableFuture<ResolverAnswer> resolveAAAA(CharSequence domnam) throws IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_AAAA, domnam);
	}

	private CompletableFuture<ResolverAnswer> issueDomainRequest(byte qtype, CharSequence qname) throws IOException {
		RequestBlock dnsreq = new RequestBlock(qtype, qname);
		return issueRequest(dnsreq);
	}

	public CompletableFuture<ResolverAnswer> resolveIP(int ipaddr) throws IOException {
		RequestBlock dnsreq = new RequestBlock(ResolverDNS.QTYPE_PTR, ipaddr);
		return issueRequest(dnsreq);
	}

	private CompletableFuture<ResolverAnswer> issueRequest(RequestBlock dnsreq) throws IOException {
		dnsreq.answer.set(null, (byte)0, null);
		reqSubmitter.produce(dnsreq);
		return dnsreq.future;
	}


	private static final class RequestHandler
		implements Producer.Consumer<RequestBlock>, ResolverDNS.Client
	{
		private final ResolverDNS resolver;

		public RequestHandler(ResolverDNS resolver) {
			this.resolver = resolver;
		}

		@Override
		public void producerIndication(Producer<RequestBlock> prod) {
			Dispatcher dsptch = prod.getDispatcher();
			RequestBlock dnsreq;
			while ((dnsreq = prod.consume()) != null) {
				ResolverAnswer answer = null;
				try {
					switch (dnsreq.qtype) {
					case ResolverDNS.QTYPE_A:
						answer = resolver.resolveHostname(dnsreq.qname, this, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_AAAA:
						answer = resolver.resolveAAAA(dnsreq.qname, this, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_PTR:
						answer = resolver.resolveIP(dnsreq.qip, this, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_NS:
						answer = resolver.resolveNameServer(dnsreq.qname, this, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_MX:
						answer = resolver.resolveMailDomain(dnsreq.qname, this, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_SOA:
						answer = resolver.resolveSOA(dnsreq.qname, this, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_SRV:
						answer = resolver.resolveSRV(dnsreq.qname, this, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_TXT:
						answer = resolver.resolveTXT(dnsreq.qname, this, dnsreq, 0);
						break;
					default:
						throw new Error("SynchDNS: Missing case for reqtype="+dnsreq.qtype);
					}
				} catch (Throwable ex) {
					boolean dumpstack = NAFException.isError(ex);
					dsptch.getLogger().log(Logger.LEVEL.ERR, ex, dumpstack, "Synchronous-DNS query failed for "+dnsreq.qtype+"/"+dnsreq.qname+"/"+dnsreq.qip+" - "+ex);
					answer = dnsreq.answer;
					if (dnsreq.qtype == ResolverDNS.QTYPE_PTR) {
						answer.set(ResolverAnswer.STATUS.ERROR, dnsreq.qtype, dnsreq.qip);
					} else {
						answer.set(ResolverAnswer.STATUS.ERROR, dnsreq.qtype, dnsreq.qname);
					}
				}
				
				if (answer != null)
					issueResponse(answer, dnsreq); //synchronous response
			}
		}

		@Override
		public void dnsResolved(Dispatcher d, ResolverAnswer answer, Object cbdata) {
			issueResponse(answer, (RequestBlock)cbdata);
		}

		private void issueResponse(ResolverAnswer answer, RequestBlock dnsreq) {
			if (answer != dnsreq.answer)
				dnsreq.answer.set(answer); //because 'answer' param will be overwritten when this call returns
			dnsreq.future.complete(dnsreq.answer);
		}
	}


	private static final class RequestBlock
	{
		public final byte qtype;
		public final ByteChars qname;
		public final int qip;
		public final ResolverAnswer answer = new ResolverAnswer();
		public final CompletableFuture<ResolverAnswer> future = new CompletableFuture<>();

		public RequestBlock(byte qt, CharSequence qn) {
			qtype = qt;
			qname = new ByteChars(qn);
			qip = 0;
		}

		public RequestBlock(byte qt, int ip) {
			qtype = qt;
			qip = ip;
			qname = null;
		}
	}
}