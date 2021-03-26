/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server;

import com.grey.base.config.XmlConfig;
import com.grey.naf.NAFConfig;
import com.grey.naf.dns.resolver.engine.PacketDNS;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;

public class DnsServerConfig
{
	private final ConcurrentListenerConfig listenerConfig;

	public DnsServerConfig(Builder bldr) {
		listenerConfig = bldr.getListenerConfig().build();
	}

	public ConcurrentListenerConfig getListenerConfig() {
		return listenerConfig;
	}


	public static class Builder {
		private ConcurrentListenerConfig.Builder<?> listenerConfig = defaultListener();

		public ConcurrentListenerConfig.Builder<?> getListenerConfig() {
			return listenerConfig;
		}

		public Builder withXmlConfig(XmlConfig cfg, NAFConfig nafConfig) {
			XmlConfig lxmlcfg = cfg.getSection("listener");
			getListenerConfig().withXmlConfig(lxmlcfg, nafConfig);
			return this;
		}

		public DnsServerConfig build() {
			return new DnsServerConfig(this);
		}

		private static ConcurrentListenerConfig.Builder<?> defaultListener() {
			return new ConcurrentListenerConfig.Builder<>()
					.withName("DNS-Server")
					.withPort(PacketDNS.INETPORT)
					.withServerFactory(TransportTCP.ServerFactory.class, null);
		}
	}
}
