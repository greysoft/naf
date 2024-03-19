/*
 * Copyright 2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server.caching_server;

import java.io.IOException;
import javax.naming.NamingException;

import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.dns.resolver.ResolverConfig;
import com.grey.naf.dns.server.DnsServerConfig;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.config.DispatcherConfig;

public class ServerApp
	extends com.grey.naf.Launcher
{
	private final ServerOptions options = new ServerOptions();

	public static void main(String[] args) throws Exception {
		ServerApp app = new ServerApp(args);
		app.execute("DNS-server");
	}

	public ServerApp(String[] args) {
		super(args);
		cmdParser.addHandler(options);
	}

	@Override
	protected void appExecute(ApplicationContextNAF appctx, int param1) throws IOException, NamingException {
		DnsServerConfig.Builder bldr = new DnsServerConfig.Builder()
				.withRecursionOffered(true);
		bldr.getListenerConfig().withPort(options.getPort());
		DnsServerConfig cfgServer = bldr.build();

		ResolverConfig cfgResolver = new ResolverConfig.Builder()
				.withLocalNameServers(options.getNameServers())
				.withAlwaysTCP(options.isAlwaysTCP())
				.build();

		DispatcherConfig dcfg = DispatcherConfig.builder()
				.withName("Dispatcher-"+appctx.getName())
				.withAppContext(appctx)
				.build();
		Dispatcher dsptch = Dispatcher.create(dcfg);

		CachingServer server = new CachingServer(dsptch, cfgServer, cfgResolver);
		dsptch.loadRunnable(server);

		appctx.getBootLogger().info("DNS-Server: Starting Dispatcher="+dsptch.getName());
		dsptch.start();
		dsptch.waitStopped();
		appctx.getBootLogger().info("DNS-Server: Dispatcher="+dsptch.getName()+" has halted");
	}
}