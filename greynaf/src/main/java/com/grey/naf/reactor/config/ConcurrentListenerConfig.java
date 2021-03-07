/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor.config;

import java.util.function.Function;

import com.grey.base.config.XmlConfig;
import com.grey.naf.NAFConfig;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.reactor.CM_Listener;
import com.grey.naf.reactor.ConcurrentListener;
import com.grey.naf.reactor.Dispatcher;

public class ConcurrentListenerConfig extends ListenerConfig
{
	// The server factory creates a server instance to handle each incoming connection on the listener.
	// The factory's constructor takes the Listener as one arg and a factory-specific object as another, so this config
	// needs to specify the factory class and its user-defined parameter.
	// If the factory constructor requires more args than that, you must supply them as an array.
	private final Function<ConcurrentListener,ConcurrentListener.ServerFactory> serverFactoryGenerator;

	private final int serversMin;
	private final int serversMax;
	private final int serversIncrement;

	private ConcurrentListenerConfig(Builder<?> bldr) {
		super(bldr);
		serversMin = bldr.serversMin;
		serversMax = bldr.serversMax;
		serversIncrement = bldr.serversIncrement;
		serverFactoryGenerator = createServerFactoryGenerator(bldr.serverFactoryClass, bldr.serverFactoryParam);
	}

	public Function<ConcurrentListener,ConcurrentListener.ServerFactory> getServerFactoryGenerator() {
		return serverFactoryGenerator;
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

	public static ConcurrentListenerConfig[] buildMultiConfig(String grpname, Dispatcher d, String xpath, XmlConfig xmlcfg,
															  int port, int sslport,
															  Class<? extends ConcurrentListener.ServerFactory> serverFactory, Object factoryParam) {
		XmlConfig[] lxmlcfg = xmlcfg.getSections(xpath+XmlConfig.XPATH_ENABLED);
		int cnt = (lxmlcfg == null ? 0 : lxmlcfg.length);
		ConcurrentListenerConfig[] lcfg = new ConcurrentListenerConfig[cnt];
		for (int idx = 0; idx != cnt; idx++) {
			lcfg[idx] = new ConcurrentListenerConfig.Builder<>()
					.withName(grpname+"-"+idx)
					.withPort(port)
					.withPortSSL(sslport)
					.withServerFactory(serverFactory, factoryParam)
					.withXmlConfig(lxmlcfg[idx], d.getApplicationContext())
					.build();
		}
		return lcfg;
	}

	private static Function<ConcurrentListener,ConcurrentListener.ServerFactory> createServerFactoryGenerator(Class<? extends ConcurrentListener.ServerFactory> factoryClass,
			                                                                                                  Object factoryParam) {
		Function<ConcurrentListener,ConcurrentListener.ServerFactory> func = (lstnr) -> {
			Class<?>[] ctorSig = new Class<?>[]{CM_Listener.class, Object.class};
			Object[] ctorArgs = new Object[]{lstnr, factoryParam};
			Object factory = NAFConfig.createEntity(factoryClass, ctorSig, ctorArgs);
			return ConcurrentListener.ServerFactory.class.cast(factory);
		};
		return func;
	}


	public static class Builder<T extends Builder<T>> extends ListenerConfig.Builder<T> {
		private Class<? extends ConcurrentListener.ServerFactory> serverFactoryClass;
		private Object serverFactoryParam;
		private int serversMin;
		private int serversMax;
		private int serversIncrement;

		@Override
		public T withXmlConfig(XmlConfig cfg, ApplicationContextNAF appctx) {
			cfg = getLinkConfig(cfg);
			super.withXmlConfig(cfg, appctx);

			XmlConfig servercfg = cfg.getSection("server");
			serverFactoryClass = getServerFactoryClass(servercfg, serverFactoryClass);
			if (serverFactoryParam == null) serverFactoryParam = servercfg;

			serversMin = cfg.getInt("@initservers", false, serversMin);
			serversMax = cfg.getInt("@maxservers", false, serversMax);
			serversIncrement = cfg.getInt("@incrservers", false, serversIncrement);
			return self();
		}

		// This should be called instead of withXmlConfig() if we have no XML config, else it should be called before it, to set the default.
		// If the factory param is null, future calls to withXmlConfig() will set it to the server XmlConfig block
		public T withServerFactory(Class<? extends ConcurrentListener.ServerFactory> clss, Object param) {
			serverFactoryClass = clss;
			serverFactoryParam = param;
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

		@SuppressWarnings("unchecked")
		private static Class<? extends ConcurrentListener.ServerFactory> getServerFactoryClass(XmlConfig cfg, Class<? extends ConcurrentListener.ServerFactory> dflt) {
			return (Class<? extends ConcurrentListener.ServerFactory>) NAFConfig.getEntityClass(cfg, dflt, ConcurrentListener.ServerFactory.class);
		}
	}
}
