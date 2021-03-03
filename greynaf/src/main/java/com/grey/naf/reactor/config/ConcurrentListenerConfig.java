/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor.config;

import java.util.function.Function;
import java.io.IOException;

import com.grey.base.config.XmlConfig;
import com.grey.naf.NAFConfig;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.reactor.CM_Listener;
import com.grey.naf.reactor.ConcurrentListener;
import com.grey.naf.errors.NAFConfigException;

public class ConcurrentListenerConfig extends ListenerConfig
{
	private final Function<ConcurrentListener,ConcurrentListener.ServerFactory> serverFactoryGenerator;
	private final int serversMin;
	private final int serversMax;
	private final int serversIncrement;

	public ConcurrentListenerConfig(Builder<?> bldr) {
		super(bldr);
		serverFactoryGenerator = bldr.serverFactoryGenerator;
		serversMin = bldr.serversMin;
		serversMax = bldr.serversMax;
		serversIncrement = bldr.serversIncrement;
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


	public static class Builder<T extends Builder<T>> extends ListenerConfig.Builder<T> {
		private Class<? extends ConcurrentListener.ServerFactory> serverFactoryClass;
		private Function<ConcurrentListener,ConcurrentListener.ServerFactory> serverFactoryGenerator;
		private int serversMin;
		private int serversMax;
		private int serversIncrement;

		@Override
		public T withXmlConfig(XmlConfig cfg, ApplicationContextNAF appctx) throws IOException {
			cfg = getLinkConfig(cfg);
			super.withXmlConfig(cfg, appctx);

			XmlConfig servercfg = cfg.getSection("server");
			serverFactoryClass = getServerFactoryClass(servercfg, serverFactoryClass);
			serverFactoryGenerator = createServerFactoryGenerator(serverFactoryClass, servercfg);

			serversMin = cfg.getInt("@initservers", false, serversMin);
			serversMax = cfg.getInt("@maxservers", false, serversMax);
			serversIncrement = cfg.getInt("@incrservers", false, serversIncrement);
			return self();
		}

		// This should be called instead of withXmlConfig() if we have no XML config, else it should be called before it, to set the default.
		public T withServerFactoryClass(Class<? extends ConcurrentListener.ServerFactory> v) {
			serverFactoryClass = v;
			serverFactoryGenerator = createServerFactoryGenerator(serverFactoryClass, null);
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

		private static Function<ConcurrentListener,ConcurrentListener.ServerFactory> createServerFactoryGenerator(Class<? extends ConcurrentListener.ServerFactory> factoryClass, Object cfg) {
			Function<ConcurrentListener,ConcurrentListener.ServerFactory> func = (lstnr) -> {
				Class<?>[] ctorSig = new Class<?>[]{CM_Listener.class, Object.class};
				Object[] ctorArgs = new Object[]{lstnr, cfg};
				Object factory;
				try {
					java.lang.reflect.Constructor<?> ctor = factoryClass.getConstructor(ctorSig);
					factory = ctor.newInstance(ctorArgs);
				} catch (Exception ex) {
					throw new NAFConfigException("Failed to create configured entity="+factoryClass.getName(), ex);
				}
				return ConcurrentListener.ServerFactory.class.cast(factory);
			};
			return func;
		}

		@SuppressWarnings("unchecked")
		private static Class<? extends ConcurrentListener.ServerFactory> getServerFactoryClass(XmlConfig cfg, Class<? extends ConcurrentListener.ServerFactory> dflt) {
			return (Class<? extends ConcurrentListener.ServerFactory>) NAFConfig.getEntityClass(cfg, dflt, ConcurrentListener.ServerFactory.class);
		}
	}
}
