/*
 * Copyright 2014-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.synchronous;

import com.grey.base.config.SysProps;
import com.grey.logging.Logger;
import com.grey.logging.Logger.LEVEL;
import com.grey.naf.reactor.Dispatcher.STOPSTATUS;

public final class SynchronousResolver
{
	private static final String LOGNAME = SysProps.get("greynaf.synchdns.logname", "SynchDNS");
	private static final String LOGLVL = SysProps.get("greynaf.synchdns.loglevel", Logger.LEVEL.TRC.toString());
	private static final long TMT_SHUTDOWN = SysProps.getTime("greynaf.synchdns.shutdowntime", "5s");

	private final com.grey.naf.reactor.Dispatcher dsptch;
	private final com.grey.naf.reactor.Producer<RequestBlock> prod_reqs;
	private final RequestHandler reqhandler;
	private final ResponseHandler rsphandler;

	public SynchronousResolver() throws com.grey.base.GreyException, java.io.IOException {
		this(null, null, false, null);
	}

	public SynchronousResolver(String nafxml_cfgpath, String dname, boolean withNafman, com.grey.logging.Logger logger)
			throws com.grey.base.GreyException, java.io.IOException
	{
		com.grey.naf.DispatcherDef def = null;
		com.grey.naf.Config nafcfg;
		if (nafxml_cfgpath == null) {
			nafcfg = com.grey.naf.Config.synthesise("<naf/>");
		} else {
			nafcfg = com.grey.naf.Config.load(nafxml_cfgpath);
			if (dname != null) {
				com.grey.base.config.XmlConfig dcfg = nafcfg.getDispatcher(dname);
				def = new com.grey.naf.DispatcherDef(dcfg);
			}
		}
		if (def == null) def = new com.grey.naf.DispatcherDef();
		def.hasDNS = true;
		def.hasNafman = withNafman;
		if (def.logname == null) def.logname = LOGNAME;

		if (logger == null) {
			Logger.LEVEL lvl = Logger.LEVEL.valueOf(LOGLVL);
			com.grey.logging.Parameters params = new com.grey.logging.Parameters(lvl, System.out);
			logger = com.grey.logging.Factory.getLogger(params, def.logname);
		}
		dsptch = com.grey.naf.reactor.Dispatcher.create(def, nafcfg, logger);
		rsphandler = new ResponseHandler();
		reqhandler = new RequestHandler(rsphandler);
		prod_reqs = new com.grey.naf.reactor.Producer<RequestBlock>(RequestBlock.class, dsptch, reqhandler);
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
	public STOPSTATUS shutdown()
	{
		dsptch.stop();
		prod_reqs.shutdown();
		return dsptch.waitStopped(TMT_SHUTDOWN, true);
	}

	public com.grey.naf.dns.Answer resolveHostname(CharSequence hostname) throws java.io.IOException {
		return issueDomainRequest(com.grey.naf.dns.Resolver.QTYPE_A, hostname);
	}

	public com.grey.naf.dns.Answer resolveIP(int ipaddr) throws java.io.IOException {
		RequestBlock dnsreq = new RequestBlock(com.grey.naf.dns.Resolver.QTYPE_PTR, ipaddr);
		return issueRequest(dnsreq);
	}

	public com.grey.naf.dns.Answer resolveNameServer(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(com.grey.naf.dns.Resolver.QTYPE_NS, domnam);
	}

	public com.grey.naf.dns.Answer resolveMailDomain(CharSequence maildom) throws java.io.IOException {
		return issueDomainRequest(com.grey.naf.dns.Resolver.QTYPE_MX, maildom);
	}

	public com.grey.naf.dns.Answer resolveSOA(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(com.grey.naf.dns.Resolver.QTYPE_SOA, domnam);
	}

	public com.grey.naf.dns.Answer resolveSRV(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(com.grey.naf.dns.Resolver.QTYPE_SRV, domnam);
	}

	public com.grey.naf.dns.Answer resolveTXT(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(com.grey.naf.dns.Resolver.QTYPE_TXT, domnam);
	}

	public com.grey.naf.dns.Answer resolveAAAA(CharSequence domnam) throws java.io.IOException {
		return issueDomainRequest(com.grey.naf.dns.Resolver.QTYPE_AAAA, domnam);
	}

	private com.grey.naf.dns.Answer issueDomainRequest(byte qtype, CharSequence qname) throws java.io.IOException
	{
		RequestBlock dnsreq = new RequestBlock(qtype, qname);
		return issueRequest(dnsreq);
	}

	private com.grey.naf.dns.Answer issueRequest(RequestBlock dnsreq) throws java.io.IOException
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

	static void issueResponse(com.grey.naf.dns.Answer answer, RequestBlock dnsreq)
	{
		synchronized (dnsreq.lock) {
			//Answer will be reused as soon as this method returns, so copy to persistent instance for requesting thread
			dnsreq.answer.set(answer);
			dnsreq.lock.notify();
		}
	}


	private static final class RequestHandler
		implements com.grey.naf.reactor.Producer.Consumer<RequestBlock>
	{
		private final ResponseHandler rsphandler;
		RequestHandler(ResponseHandler rh) {rsphandler = rh;}

		@Override
		public void producerIndication(com.grey.naf.reactor.Producer<RequestBlock> prod)
		{
			com.grey.naf.reactor.Dispatcher dsptch = prod.getDispatcher();
			RequestBlock dnsreq;
			while ((dnsreq = prod.consume()) != null) {
				com.grey.naf.dns.Answer answer = null;
				try {
					switch (dnsreq.qtype) {
					case com.grey.naf.dns.Resolver.QTYPE_A:
						answer = dsptch.dnsresolv.resolveHostname(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case com.grey.naf.dns.Resolver.QTYPE_AAAA:
						answer = dsptch.dnsresolv.resolveAAAA(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case com.grey.naf.dns.Resolver.QTYPE_PTR:
						answer = dsptch.dnsresolv.resolveIP(dnsreq.qip, rsphandler, dnsreq, 0);
						break;
					case com.grey.naf.dns.Resolver.QTYPE_NS:
						answer = dsptch.dnsresolv.resolveNameServer(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case com.grey.naf.dns.Resolver.QTYPE_MX:
						answer = dsptch.dnsresolv.resolveMailDomain(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case com.grey.naf.dns.Resolver.QTYPE_SOA:
						answer = dsptch.dnsresolv.resolveSOA(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case com.grey.naf.dns.Resolver.QTYPE_SRV:
						answer = dsptch.dnsresolv.resolveSRV(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					case com.grey.naf.dns.Resolver.QTYPE_TXT:
						answer = dsptch.dnsresolv.resolveTXT(dnsreq.qname, rsphandler, dnsreq, 0);
						break;
					default:
						throw new com.grey.base.GreyException("SynchDNS: Missing case for reqtype="+dnsreq.qtype);
					}
				} catch (Throwable ex) {
					dsptch.logger.log(LEVEL.ERR, ex, ex instanceof RuntimeException, "Synchronous-DNS query failed for "+dnsreq.qtype+"/"+dnsreq.qname+"/"+dnsreq.qip+" - "+ex);
					answer = new com.grey.naf.dns.Answer();
					if (dnsreq.qtype == com.grey.naf.dns.Resolver.QTYPE_PTR) {
						answer.set(com.grey.naf.dns.Answer.STATUS.ERROR, dnsreq.qtype, dnsreq.qip);
					} else {
						answer.set(com.grey.naf.dns.Answer.STATUS.ERROR, dnsreq.qtype, dnsreq.qname);
					}
				}
				if (answer != null) issueResponse(answer, dnsreq);
			}
		}
	}


	private static final class ResponseHandler
		implements com.grey.naf.dns.Resolver.Client
	{
		ResponseHandler() {} //make explicit with non-private access, to eliminate synthetic accessor

		@Override
		public void dnsResolved(com.grey.naf.reactor.Dispatcher dsptch, com.grey.naf.dns.Answer answer, Object cbdata)
		{
			issueResponse(answer, (RequestBlock)cbdata);
		}
	}


	private static final class RequestBlock
	{
		public final byte qtype;
		public final com.grey.base.utils.ByteChars qname;
		public final int qip;
		public final com.grey.naf.dns.Answer answer = new com.grey.naf.dns.Answer();
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