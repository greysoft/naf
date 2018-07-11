/*
 * Copyright 2014-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.synchronous;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.DispatcherDef;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;
import com.grey.naf.dns.ResolverDNS;
import com.grey.naf.dns.ResolverAnswer;
import com.grey.naf.errors.NAFException;
import com.grey.logging.Logger;

public class SynchronousResolver
{
	private static final String LOGNAME = SysProps.get("greynaf.synchdns.logname", "SynchDNS");
	private static final String LOGLVL = SysProps.get("greynaf.synchdns.loglevel", Logger.LEVEL.TRC.toString());
	private static final long TMT_SHUTDOWN = SysProps.getTime("greynaf.synchdns.shutdowntime", "5s");

	private final Dispatcher dsptch;
	private final Producer<RequestBlock> prod_reqs;
	private final RequestHandler reqhandler;
	private final ResponseHandler rsphandler;

	public SynchronousResolver(ApplicationContextNAF appctx, String dname, boolean withNafman, com.grey.logging.Logger logger)
			throws java.io.IOException
	{
		DispatcherDef def = null;
		if (dname != null) {
			XmlConfig dcfg = appctx.getConfig().getDispatcher(dname);
			if (dcfg == null) {
				def = new DispatcherDef(dname);
			} else {
				def = new DispatcherDef(dcfg);
			}
		}
		if (def == null) def = new DispatcherDef();
		def.hasDNS = true;
		def.hasNafman = withNafman;
		if (def.logname == null) def.logname = LOGNAME;

		if (logger == null) {
			Logger.LEVEL lvl = Logger.LEVEL.valueOf(LOGLVL);
			com.grey.logging.Parameters params = new com.grey.logging.Parameters(lvl, System.out);
			logger = com.grey.logging.Factory.getLogger(params, def.logname);
		}
		dsptch = Dispatcher.create(appctx, def, logger);
		rsphandler = new ResponseHandler();
		reqhandler = new RequestHandler(rsphandler);
		prod_reqs = new Producer<RequestBlock>(RequestBlock.class, dsptch, reqhandler);
	}

	/**
	 * Launches the background thread in which the resolution of DNS requests is performed asynchronously.
	 * This should only be invoked once, and it then supports any number of synchronous-DNS threads.
	 */
	public void init()
	{
		if (dsptch.isRunning()) throw new IllegalStateException("SynchDNS: Dispatcher="+dsptch.name+" already running");
		dsptch.start();
	}

	/*
	 * This shuts down the background DNS-Resolver thread spawned by init().
	 * It can be called from any thread and is safe to call multiple times.
	 */
	public Dispatcher.STOPSTATUS shutdown()
	{
		dsptch.stop();
		prod_reqs.shutdown();
		return dsptch.waitStopped(TMT_SHUTDOWN, true);
	}

	public ResolverAnswer resolveHostname(CharSequence hostname) throws java.io.IOException {
		return issueDomainRequest(ResolverDNS.QTYPE_A, hostname);
	}

	public ResolverAnswer resolveIP(int ipaddr) throws java.io.IOException {
		RequestBlock dnsreq = new RequestBlock(ResolverDNS.QTYPE_PTR, ipaddr);
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

	private ResolverAnswer issueDomainRequest(byte qtype, CharSequence qname) throws java.io.IOException
	{
		RequestBlock dnsreq = new RequestBlock(qtype, qname);
		return issueRequest(dnsreq);
	}

	private ResolverAnswer issueRequest(RequestBlock dnsreq) throws java.io.IOException
	{
		dnsreq.answer.set(null, (byte)0, null);
		synchronized (dnsreq.lock) {
			prod_reqs.produce(dnsreq);
			while (dnsreq.answer.result == null) {
				try {
					dnsreq.lock.wait();
				} catch (InterruptedException ex) {}
			}
		}
		return dnsreq.answer;
	}

	static void issueResponse(ResolverAnswer answer, RequestBlock dnsreq)
	{
		synchronized (dnsreq.lock) {
			//Answer will be reused as soon as this method returns, so copy to persistent instance for requesting thread
			dnsreq.answer.set(answer);
			dnsreq.lock.notify();
		}
	}


	private static final class RequestHandler implements Producer.Consumer<RequestBlock>
	{
		private final ResponseHandler rsphandler;
		RequestHandler(ResponseHandler rh) {rsphandler = rh;}

		@Override
		public void producerIndication(Producer<RequestBlock> prod)
		{
			Dispatcher dsptch = prod.getDispatcher();
			ResolverDNS resolver = dsptch.getResolverDNS();
			RequestBlock dnsreq;
			while ((dnsreq = prod.consume()) != null) {
				ResolverAnswer answer = null;
				try {
					switch (dnsreq.qtype) {
					case ResolverDNS.QTYPE_A:
						answer = resolver.resolveHostname(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_AAAA:
						answer = resolver.resolveAAAA(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_PTR:
						answer = resolver.resolveIP(dnsreq.qip, rsphandler, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_NS:
						answer = resolver.resolveNameServer(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_MX:
						answer = resolver.resolveMailDomain(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_SOA:
						answer = resolver.resolveSOA(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_SRV:
						answer = resolver.resolveSRV(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case ResolverDNS.QTYPE_TXT:
						answer = resolver.resolveTXT(dnsreq.qname, rsphandler, dnsreq, 0);
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
				if (answer != null) issueResponse(answer, dnsreq);
			}
		}
	}


	private static final class ResponseHandler implements ResolverDNS.Client
	{
		ResponseHandler() {} //make explicit with non-private access, to eliminate synthetic accessor

		@Override
		public void dnsResolved(Dispatcher dsptch, ResolverAnswer answer, Object cbdata)
		{
			issueResponse(answer, (RequestBlock)cbdata);
		}
	}


	private static final class RequestBlock
	{
		public final byte qtype;
		public final com.grey.base.utils.ByteChars qname;
		public final int qip;
		public final ResolverAnswer answer = new ResolverAnswer();
		public final Object lock = new Object();

		public RequestBlock(byte qt, CharSequence qn) {
			qtype = qt;
			qname = new com.grey.base.utils.ByteChars(qn);
			qip = 0;
		}

		public RequestBlock(byte qt, int ip) {
			qtype = qt;
			qip = ip;
			qname = null;
		}
	}
}