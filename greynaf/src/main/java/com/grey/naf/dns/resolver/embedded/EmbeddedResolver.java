/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.resolver.embedded;

import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.resolver.ResolverService;

/**
 * This class provides a ResolverDNS API to create and access a resolver engine in the same Dispatcher thread.
 */
public class EmbeddedResolver
	extends com.grey.naf.dns.resolver.ResolverDNS
{
	private final com.grey.naf.dns.resolver.ResolverService rslvr;

	public EmbeddedResolver(com.grey.naf.reactor.Dispatcher dsptch, ResolverConfig config)
			throws java.io.IOException, javax.naming.NamingException
	{
		super(dsptch);
		rslvr = new ResolverService(dsptch, config);
	}

	@Override
	public void start() throws java.io.IOException
	{
		rslvr.start();
	}

	@Override
	public void stop()
	{
		rslvr.stop();
	}

	@Override
	protected com.grey.naf.dns.resolver.ResolverAnswer resolve(byte qtype, com.grey.base.utils.ByteChars qname, com.grey.naf.dns.resolver.ResolverDNS.Client caller, Object cbdata, int flags)
	{
		return rslvr.resolve(qtype, qname, caller, cbdata, flags);
	}

	@Override
	protected com.grey.naf.dns.resolver.ResolverAnswer resolve(byte qtype, int qip, com.grey.naf.dns.resolver.ResolverDNS.Client caller, Object cbdata, int flags)
	{
		return rslvr.resolve(qtype, qip, caller, cbdata, flags);
	}

	@Override
	public int cancel(com.grey.naf.dns.resolver.ResolverDNS.Client caller)
	{
		return rslvr.cancel(caller);
	}
}
