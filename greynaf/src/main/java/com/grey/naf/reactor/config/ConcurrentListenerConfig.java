/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor.config;

import java.util.function.Function;

import com.grey.base.config.XmlConfig;
import com.grey.naf.NAFConfig;
import com.grey.naf.reactor.CM_Listener;

public class ConcurrentListenerConfig extends ListenerConfig
{

	private final int serversMin;
	private final int serversMax;
	private final int serversIncrement;

	private ConcurrentListenerConfig(Builder<?> bldr) {
		super(bldr);
		serversMin = bldr.serversMin;
		serversMax = bldr.serversMax;
		serversIncrement = (bldr.serversIncrement == 0 ? 1 : bldr.serversIncrement);
	}

	public int getMinServers() {
		return serversMin;
	}

	public int getMaxServers() {
		return serversMax;
	}

	public int getServersIncrement() {
		return serversIncrement;
	}


	public static ConcurrentListenerConfig[] buildMultiConfig(String grpname, NAFConfig nafConfig, String xpath, XmlConfig xmlcfg, int port, int sslport,
															  Class<? extends CM_Listener.ServerFactory> serverFactory, Object factoryParam) {
		return buildMultiConfig(grpname, nafConfig, xpath, xmlcfg, port, sslport, null, serverFactory, factoryParam);
	}

	public static ConcurrentListenerConfig[] buildMultiConfig(String grpname, NAFConfig nafConfig, String xpath, XmlConfig xmlcfg, int port, int sslport,
															  Function<CM_Listener,CM_Listener.ServerFactory> serverFactoryGenerator) {
		return buildMultiConfig(grpname, nafConfig, xpath, xmlcfg, port, sslport, serverFactoryGenerator, null, null);
	}

	private static ConcurrentListenerConfig[] buildMultiConfig(String grpname, NAFConfig nafConfig, String xpath, XmlConfig xmlcfg, int port, int sslport,
															   Function<CM_Listener,CM_Listener.ServerFactory> serverFactoryGenerator,
															   Class<? extends CM_Listener.ServerFactory> serverFactory, Object factoryParam) {
		XmlConfig[] lxmlcfg = xmlcfg.getSections(xpath+XmlConfig.XPATH_ENABLED);
		int cnt = (lxmlcfg == null ? 0 : lxmlcfg.length);
		ConcurrentListenerConfig[] lcfg = new ConcurrentListenerConfig[cnt];
		for (int idx = 0; idx != cnt; idx++) {
			ConcurrentListenerConfig.Builder<?> bldr = new ConcurrentListenerConfig.Builder<>()
					.withName(grpname+"-"+idx)
					.withPort(port)
					.withPortSSL(sslport);
			if (serverFactoryGenerator == null) {
				bldr = bldr.withServerFactory(serverFactory, factoryParam)
					.withXmlConfig(lxmlcfg[idx], nafConfig);
			} else {
				bldr = bldr.withServerFactoryGenerator(serverFactoryGenerator);
			}
			lcfg[idx] = bldr.build();
		}
		return lcfg;
	}


	public static class Builder<T extends Builder<T>> extends ListenerConfig.Builder<T> {
		private int serversMin;
		private int serversMax;
		private int serversIncrement;

		@Override
		public T withXmlConfig(XmlConfig cfg, NAFConfig nafConfig) {
			cfg = getLinkConfig(cfg);
			super.withXmlConfig(cfg, nafConfig);
			serversMin = cfg.getInt("@initservers", false, serversMin);
			serversMax = cfg.getInt("@maxservers", false, serversMax);
			serversIncrement = cfg.getInt("@incrservers", false, serversIncrement);
			return self();
		}

		public T withMinServers(int v) {
			serversMin = v;
			return self();
		}

		public T withMaxServers(int v) {
			serversMax = v;
			return self();
		}

		public T withServersIncrement(int v) {
			serversIncrement = v;
			return self();
		}

		@Override
		public ConcurrentListenerConfig build()  {
			return new ConcurrentListenerConfig(this);
		}
	}
}
