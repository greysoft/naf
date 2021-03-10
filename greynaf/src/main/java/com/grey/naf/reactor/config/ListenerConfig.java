/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor.config;

import com.grey.base.config.XmlConfig;
import com.grey.naf.SSLConfig;
import com.grey.naf.NAFConfig;
import com.grey.naf.errors.NAFConfigException;

public class ListenerConfig
{
	private final String name;
	private final String iface;
	private final int port;
	private final int backlog;
	private final SSLConfig configSSL;

	protected ListenerConfig(Builder<?> bldr) {
		name = bldr.name;
		iface = bldr.iface;
		port = bldr.port;
		backlog = bldr.backlog;
		configSSL = bldr.configSSL;
	}

	public String getName() {
		return name;
	}

	public String getInterface() {
		return iface;
	}

	public int getPort() {
		return port;
	}

	public int getBacklog() {
		return backlog;
	}

	public SSLConfig getConfigSSL() {
		return configSSL;
	}


	public static class Builder<T extends Builder<T>> {
		private String name;
		private String iface;
		private int port;
		private int portSSL;
		private SSLConfig configSSL;
		private int backlog = 5000;

		// Call the other setter methods before this to set any defaults for name, iface, port, backlog
		public T withXmlConfig(XmlConfig cfg, NAFConfig nafConfig) {
			cfg = getLinkConfig(cfg);

			XmlConfig xmlSSL = cfg.getSection("ssl");
			try {
				if (xmlSSL != null && xmlSSL.exists()) {
					configSSL = new SSLConfig.Builder()
							.withXmlConfig(xmlSSL, nafConfig)
							.build();
					if (portSSL != 0) port = portSSL;
				}
			} catch (Exception ex) {
				throw new NAFConfigException("Failed to configure SSL", ex);
			}

			name = cfg.getValue("@name", false, name);
			iface = cfg.getValue("@interface", false, iface);
			port = cfg.getInt("@port", false, port);
			backlog = cfg.getInt("@backlog", false, backlog);
			return self();
		}

		public T withName(String v) {
			name = v;
			return self();
		}

		public T withInterface(String v) {
			iface = v;
			return self();
		}

		public T withPort(int v) {
			port = v;
			return self();
		}

		public T withPortSSL(int v) {
			portSSL = v;
			return self();
		}

		public T withBacklog(int v) {
			backlog = v;
			return self();
		}

		public T withConfigSSL(SSLConfig v) {
			configSSL = v;
			return self();
		}

		protected XmlConfig getLinkConfig(XmlConfig cfg) {
			String linkname = cfg.getValue("@configlink", false, null);
			if (linkname != null) cfg = cfg.getSection("../listener[@name='"+linkname+"']");
			return cfg;
		}

		protected T self() {
			@SuppressWarnings("unchecked") T b = (T)this;
			return b;
		}

		public ListenerConfig build()  {
			return new ListenerConfig(this);
		}
	}
}
