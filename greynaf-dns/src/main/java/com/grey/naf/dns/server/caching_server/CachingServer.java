/*
 * Copyright 2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server.caching_server;

import java.net.InetSocketAddress;
import javax.naming.NamingException;
import java.io.IOException;

import com.grey.base.collections.ObjectPool;
import com.grey.base.utils.ByteChars;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.DispatcherRunnable;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.dns.resolver.engine.ResolverAnswer;
import com.grey.naf.dns.resolver.engine.ResolverService;
import com.grey.naf.dns.resolver.engine.ResourceData;
import com.grey.naf.dns.server.DnsServerConfig;
import com.grey.naf.dns.server.ServerDNS;
import com.grey.naf.dns.server.ServerDNS.DNSQuestionResolver;
import com.grey.naf.errors.NAFException;

/**
 * Experimental caching DNS server.
 * Does not return Authority or Addition-Info RRs in response.
 */
public class CachingServer implements DNSQuestionResolver, ResolverDNS.Client, DispatcherRunnable {
	private final ServerDNS server;
	private final ResolverService resolver;
	private final ObjectPool<QueryParams> qryParamsPool;

	@Override
	public String getName() {return "DNS-CachingServer";}
	@Override
	public Dispatcher getDispatcher() {return resolver.getDispatcher();}

	public CachingServer(Dispatcher dsptch, DnsServerConfig cfgServer, ResolverConfig cfgResolver) throws IOException, NamingException {
		resolver = new ResolverService(dsptch, cfgResolver);
		server = new ServerDNS(dsptch, this, cfgServer);
		qryParamsPool = new ObjectPool<>(() -> new QueryParams());
	}

	@Override
	public void startDispatcherRunnable() throws java.io.IOException {
		resolver.start();
		server.startDispatcherRunnable();
	}

	@Override
	public boolean stopDispatcherRunnable() {
		resolver.stop();
		return server.stopDispatcherRunnable();
	}

	@Override
	public void dnsResolveQuestion(int qid, byte qtype, ByteChars qname, boolean recursion_desired, InetSocketAddress remote_addr, Object questionCallbackParam) {
		QueryParams params = qryParamsPool.extract();
		params.reset(qid, qtype, qname, recursion_desired, remote_addr, questionCallbackParam);
		ResolverAnswer answer = resolver.resolve(qtype, qname, this, params, 0);
		if (answer != null) {
			issueResponse(answer, params);
		}
	}

	@Override
	public void dnsResolved(Dispatcher dsptch, ResolverAnswer answer, Object callerParam) {
		issueResponse(answer, (QueryParams)callerParam);
	}
	
	private void issueResponse(ResolverAnswer answer, QueryParams params) {
		int rcode = answer.getRCODE();
		ResourceData[] rr = answer.rrdata.toArray(new ResourceData[answer.rrdata.size()]);
		try {
			boolean trunc = server.sendResponse(params.qid, params.qtype, params.qname, rcode, false, params.recursionDesired, rr, null, null,
													params.remoteAddr, params.questionCallbackParam);
			if (trunc) resolver.getDispatcher().getLogger().warn("DNS-Server truncated response for "+params+" => "+answer);
		} catch (Exception ex) {
			throw new NAFException("DNS-Server failed to issue response: "+params+" => "+answer, ex);
		} finally {
			qryParamsPool.store(params);
		}
	}
	
	
	private static class QueryParams {
		private int qid;
		private byte qtype;
		private ByteChars qname;
		private boolean recursionDesired;
		private InetSocketAddress remoteAddr;
		private Object questionCallbackParam;

		public QueryParams reset(int id, byte type, ByteChars name, boolean recursion, InetSocketAddress remote, Object param) {
			this.qid = id;
			this.qtype = type;
			this.qname = name;
			this.recursionDesired = recursion;
			this.remoteAddr = remote;
			this.questionCallbackParam = param;
			return this;
		}
		
		@Override
		public String toString() {
			return super.toString()+"/qid="+qid+"/qtype="+qtype+"/qname="+qname;
		}
	}
}
