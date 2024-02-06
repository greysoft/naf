/*
 * Copyright 2015-2024 Yusef Badri - All rights reserved.
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
	private final boolean recursionOffered;

	public DnsServerConfig(Builder bldr) {
		listenerConfig = bldr.getListenerConfig().build();
		recursionOffered = bldr.recursionOffered;
	}

	public ConcurrentListenerConfig getListenerConfig() {
		return listenerConfig;
	}

	public boolean getRecursionOffered() {
		return recursionOffered;
	}


	public static class Builder {
		private final ConcurrentListenerConfig.Builder<?> listenerConfig = defaultListener();
		private boolean recursionOffered;

		public ConcurrentListenerConfig.Builder<?> getListenerConfig() {
			return listenerConfig;
		}

		public Builder withXmlConfig(XmlConfig cfg, NAFConfig nafConfig) {
			XmlConfig lxmlcfg = cfg.getSection("listener");
			getListenerConfig().withXmlConfig(lxmlcfg, nafConfig);
			return this;
		}

		public Builder withRecursionOffered(boolean v) {
			recursionOffered = v;
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
