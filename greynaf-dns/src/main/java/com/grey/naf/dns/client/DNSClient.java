/*
 * Copyright 2014-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.client;

import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.engine.ResolverAnswer;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;
import com.grey.naf.errors.NAFException;
import com.grey.logging.Logger;

public class DNSClient
{
	public interface QueryCallback {
		void dnsQueryCompleted(ResolverAnswer answer);
	}

	private final Dispatcher dsptch;
	private final Producer<RequestBlock> reqSubmitter; //the consumer side of this runs inside 'dsptch'
	private final QueryCallback asyncCallback;

	public DNSClient(ResolverDNS resolver, QueryCallback cb) throws java.io.IOException {
		dsptch = resolver.getMasterDispatcher();
		asyncCallback = cb;
		RequestHandler reqHandler = new RequestHandler(resolver);
		reqSubmitter = new Producer<RequestBlock>("DNS-Client-reqs", RequestBlock.class, dsptch, reqHandler);
		dsptch.loadRunnable(reqSubmitter);
	}

	// Can be called from any thread and is safe to call multiple times.
	public void shutdown() throws java.io.IOException {
		dsptch.unloadRunnable(reqSubmitter);
	}

	public ResolverAnswer resolveHostname(CharSequence hostname) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_A, hostname);
	}

	public ResolverAnswer resolveIP(int ipaddr) throws java.io.IOException {
		RequestBlock dnsreq = new RequestBlock(ResolverDNS.QTYPE_PTR, ipaddr, asyncCallback);
		return issueRequest(dnsreq);
	}

	public ResolverAnswer resolveNameServer(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_NS, domnam);
	}

	public ResolverAnswer resolveMailDomain(CharSequence maildom) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_MX, maildom);
	}

	public ResolverAnswer resolveSOA(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_SOA, domnam);
	}

	public ResolverAnswer resolveSRV(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_SRV, domnam);
	}

	public ResolverAnswer resolveTXT(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_TXT, domnam);
	}

	public ResolverAnswer resolveAAAA(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_AAAA, domnam);
	}

	private ResolverAnswer issueDomainRequest(byte qtype, CharSequence qname) throws java.io.IOException {
		RequestBlock dnsreq = new RequestBlock(qtype, qname, asyncCallback);
		return issueRequest(dnsreq);
	}

	private ResolverAnswer issueRequest(RequestBlock dnsreq) throws java.io.IOException {
		dnsreq.answer.set(null, (byte)0, null);
		reqSubmitter.produce(dnsreq);
		if (asyncCallback != null) return null;

		synchronized (dnsreq.lock) {
			long t1 = dsptch.getRealTime();
			long limit = t1 + 120_000; //DNS Resolver should always indicate timeout, so this is safety measure
			while (dnsreq.answer.result == null) {
				long tmt = limit - dsptch.getRealTime();
				if (tmt <= 0) throw new NAFException("Timed out on DNS query="+dnsreq.qtype+"/"+dnsreq.qname);
				try {
					dnsreq.lock.wait(tmt);
				} catch (InterruptedException ex) {}
			}
		}
		return dnsreq.answer;
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
					answer = new ResolverAnswer();
					if (dnsreq.qtype == ResolverDNS.QTYPE_PTR) {
						answer.set(ResolverAnswer.STATUS.ERROR, dnsreq.qtype, dnsreq.qip);
					} else {
						answer.set(ResolverAnswer.STATUS.ERROR, dnsreq.qtype, dnsreq.qname);
					}
				}
				if (answer != null) issueResponse(answer, dnsreq); //synchronous answer
			}
		}

		@Override
		public void dnsResolved(Dispatcher d, ResolverAnswer answer, Object cbdata) {
			issueResponse(answer, (RequestBlock)cbdata);
		}

		private void issueResponse(ResolverAnswer answer, RequestBlock dnsreq) {
			if (dnsreq.asyncCallback != null) {
				dnsreq.asyncCallback.dnsQueryCompleted(answer);
				return;
			}
			synchronized (dnsreq.lock) {
				//Answer will be reused as soon as this method returns, so copy to persistent instance for requesting thread
				dnsreq.answer.set(answer);
				dnsreq.lock.notify();
			}
		}
	}


	private static final class RequestBlock
	{
		public final byte qtype;
		public final com.grey.base.utils.ByteChars qname;
		public final int qip;
		public final ResolverAnswer answer = new ResolverAnswer();
		private final QueryCallback asyncCallback;
		public final Object lock = new Object();

		public RequestBlock(byte qt, CharSequence qn, QueryCallback cb) {
			qtype = qt;
			qname = new com.grey.base.utils.ByteChars(qn);
			qip = 0;
			asyncCallback = cb;
		}

		public RequestBlock(byte qt, int ip, QueryCallback cb) {
			qtype = qt;
			qip = ip;
			qname = null;
			asyncCallback = cb;
		}
	}
}