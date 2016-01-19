/*
 * Copyright 2012-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

// This class specifies a Proxy request, and its instances are exchanged between the master thread (which
// is in-situ with the ResolverService object) and the secondaries.
final class Request
{
	// this is the reply channel to the Proxy client
	public final com.grey.naf.reactor.Producer<Request> client;

	// the DNS request-response block
	public final com.grey.naf.dns.Answer answer = new com.grey.naf.dns.Answer();

	// additional params for the Proxy request
	public com.grey.naf.dns.Resolver.Client caller;
	public Object cbdata;
	public int flags;

	// we need this to preserve fields that can be reused while this object travels through Producer
	private final com.grey.base.utils.ByteChars qnamebuf = new com.grey.base.utils.ByteChars();

	public Request(com.grey.naf.reactor.Producer<Request> p)
	{
		client = p;
	}

	public void setQuery(com.grey.naf.dns.Resolver.Client caller, byte qtype,
			com.grey.base.utils.ByteChars qname, int qip, Object cbdata, int flags)
	{
		if (qname == null) {
			answer.set(null, qtype, qip);
			qnamebuf.ar_len = 0;
		} else {
			// qname could be reused by Client thread before ResolverService thread sees it
			qnamebuf.set(qname);
			answer.set(null, qtype, qnamebuf);
		}
		this.caller = caller;
		this.cbdata = cbdata;
		this.flags = flags;
	}

	public void setResponse(com.grey.naf.dns.Answer ans2)
	{
		if (ans2.qname == null) {
			qnamebuf.ar_len = 0;
		} else {
			// ans2.qname will be reused by ResolverService thread before Client sees it
			qnamebuf.set(ans2.qname);
		}
		answer.set(ans2);
	}
}
