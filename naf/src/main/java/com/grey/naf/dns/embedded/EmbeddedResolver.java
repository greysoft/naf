/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.embedded;

public final class EmbeddedResolver
	extends com.grey.naf.dns.Resolver
{
	private final com.grey.naf.dns.ResolverService rslvr;

	public EmbeddedResolver(com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, java.io.IOException, javax.naming.NamingException
	{
		super(dsptch, cfg);
		rslvr = new com.grey.naf.dns.ResolverService(dsptch, cfg);
	}

	@Override
	public void start() throws java.io.IOException
	{
		rslvr.start();
	}

	@Override
	public boolean stop()
	{
		return rslvr.stop();
	}

	@Override
	protected com.grey.naf.dns.Answer resolve(byte qtype, com.grey.base.utils.ByteChars qname, com.grey.naf.dns.Resolver.Client caller, Object cbdata, int flags)
	{
		return rslvr.resolve(qtype, qname, caller, cbdata, flags);
	}

	@Override
	protected com.grey.naf.dns.Answer resolve(byte qtype, int qip, com.grey.naf.dns.Resolver.Client caller, Object cbdata, int flags)
	{
		return rslvr.resolve(qtype, qip, caller, cbdata, flags);
	}

	@Override
	public int cancel(com.grey.naf.dns.Resolver.Client caller)
	{
		return rslvr.cancel(caller);
	}
}
