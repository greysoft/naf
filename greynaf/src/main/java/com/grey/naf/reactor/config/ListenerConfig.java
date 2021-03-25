/*
 * Copyright 2015-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor.config;

import java.util.function.Function;

import com.grey.base.config.XmlConfig;
import com.grey.naf.NAFConfig;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.reactor.CM_Listener;

public class ListenerConfig
{
	private final String name;
	private final String iface;
	private final int port;
	private final int backlog;
	private final SSLConfig configSSL;
	private final Function<CM_Listener,CM_Listener.ServerFactory> serverFactoryGenerator; //server factory creates server instance to handle incoming connection

	protected ListenerConfig(Builder<?> bldr) {
		name = bldr.name;
		iface = bldr.iface;
		port = bldr.port;
		backlog = bldr.backlog;
		configSSL = bldr.configSSL;
		serverFactoryGenerator = bldr.serverFactoryGenerator;
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

	public Function<CM_Listener,CM_Listener.ServerFactory> getServerFactoryGenerator() {
		return serverFactoryGenerator;
	}


	public static class Builder<T extends Builder<T>> {
		private String name;
		private String iface;
		private int port;
		private int portSSL;
		private SSLConfig configSSL;
		private int backlog = 5000;
		private Function<CM_Listener,CM_Listener.ServerFactory> serverFactoryGenerator;
		private Class<? extends CM_Listener.ServerFactory> serverFactoryClass;
		private Object serverFactoryParam;

		// Call the other setter methods before this to set any defaults for name, iface, port, backlog
		public T withXmlConfig(XmlConfig cfg, NAFConfig nafConfig) {
			cfg = getLinkConfig(cfg);

			XmlConfig servercfg = cfg.getSection("server");
			serverFactoryClass = getServerFactoryClass(servercfg, serverFactoryClass);
			if (serverFactoryParam == null) serverFactoryParam = servercfg;
			withServerFactory(serverFactoryClass, serverFactoryParam);

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

		// This specifies the server-factory class traditionally used in the XML config. The factory's constructor takes the
		// Listener as one arg and a factory-specific object as another. If the factory constructor requires more parameters than
		// that, you must supply them as an array in the second arg.
		// This server-factory type is not limited to XML config, can be called instead of withXmlConfig() if we're not using XML config,
		// else it should be called before it to set the default. If the factory param is null, future calls to withXmlConfig() will set it
		// to the server XmlConfig block
		public T withServerFactory(Class<? extends CM_Listener.ServerFactory> clss, Object param) {
			serverFactoryClass = clss;
			serverFactoryParam = param;
			serverFactoryGenerator = createServerFactoryGenerator(serverFactoryClass, serverFactoryParam);
			return self();
		}

		// This is an alternative to withServerFactory() which specifies a server-factory method with fewer restrictions on its constructor signature.
		// All server factory constructors still require at least one arg, which ius the Listener.
		public T withServerFactoryGenerator(Function<CM_Listener,CM_Listener.ServerFactory> v) {
			serverFactoryGenerator = v;
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

		@SuppressWarnings("unchecked")
		private static Class<? extends CM_Listener.ServerFactory> getServerFactoryClass(XmlConfig cfg, Class<? extends CM_Listener.ServerFactory> dflt) {
			return (Class<? extends CM_Listener.ServerFactory>) NAFConfig.getEntityClass(cfg, dflt, CM_Listener.ServerFactory.class);
		}

		private static Function<CM_Listener,CM_Listener.ServerFactory> createServerFactoryGenerator(Class<? extends CM_Listener.ServerFactory> factoryClass,
				                                                                                    Object factoryParam) {
			Function<CM_Listener,CM_Listener.ServerFactory> func = (lstnr) -> {
				Class<?>[] ctorSig = new Class<?>[]{CM_Listener.class, Object.class};
				Object[] ctorArgs = new Object[]{lstnr, factoryParam};
				Object factory = NAFConfig.createEntity(factoryClass, ctorSig, ctorArgs);
				return CM_Listener.ServerFactory.class.cast(factory);
			};
			return func;
		}
	}
}
