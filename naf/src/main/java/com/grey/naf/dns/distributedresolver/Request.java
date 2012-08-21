/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.distributedresolver;

// This class specifies a Proxy request
public class Request
{
	// this is the reply channel to the Proxy client
	public final com.grey.naf.reactor.Producer<Request> client;

	// the DNS request-response block
	public final com.grey.naf.dns.Answer answer = new com.grey.naf.dns.Answer();

	// additional params for the Proxy request
	public com.grey.naf.dns.Resolver.Client caller;
	public Object cbdata;
	public int flags;

	public Request(com.grey.naf.reactor.Producer<Request> p)
	{
		client = p;
	}

	public Request set(com.grey.naf.dns.Resolver.Client caller, byte qtype,
			com.grey.base.utils.ByteChars qname, int qip, Object cbdata, int flags)
	{
		answer.initQuery(qtype, qname, qip);
		this.caller = caller;
		this.cbdata = cbdata;
		this.flags = flags;
		return this;
	}
}
