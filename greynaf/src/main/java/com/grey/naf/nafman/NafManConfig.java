/*
 * Copyright 2013-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import java.time.Duration;
import java.util.function.Supplier;

import com.grey.base.config.XmlConfig;
import com.grey.naf.BufferGenerator;
import com.grey.naf.NAFConfig;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;

public class NafManConfig
{
	private final boolean surviveDownstream; //if false, Primary agent halts its Dispatcher if any secondary agents halt
	private final long idleConnectionTimeout; //idle timeout the NAFMAN server applies to incoming connections
	private final ConcurrentListenerConfig listenerConfig; //the NAFMAN server
	private final BufferGenerator.BufferConfig bufferConfig;

	// these are TTLs for cached responses in NAFMAN server
	private final long dynamicResourceTTL;
	private final long declaredStaticTTL; //this is for permanently cached static content, but we have to declare a finite time to clients

	private NafManConfig(Builder bldr) {
		surviveDownstream = bldr.surviveDownstream;
		dynamicResourceTTL = bldr.dynamicResourceTTL;
		declaredStaticTTL = bldr.declaredStaticTTL;
		idleConnectionTimeout = bldr.idleConnectionTimeout;
		listenerConfig = bldr.getListenerConfig().build();
		bufferConfig = bldr.bufferConfig;
	}

	public boolean isSurviveDownstream() {
		return surviveDownstream;
	}

	public long getIdleConnectionTimeout() {
		return idleConnectionTimeout;
	}

	public long getDynamicResourceTTL() {
		return dynamicResourceTTL;
	}

	public long getDeclaredStaticTTL() {
		return declaredStaticTTL;
	}

	public ConcurrentListenerConfig getListenerConfig() {
		return listenerConfig;
	}

	public BufferGenerator.BufferConfig getBufferConfig() {
		return bufferConfig;
	}

	@Override
	public String toString() {
		return super.toString()+" on port="+getListenerConfig().getPort()+" with survive-downstream="+isSurviveDownstream();
	}


	public static class Builder {
		private final NAFConfig nafConfig;
		private final ConcurrentListenerConfig.Builder<?> listenerConfig;
		private boolean surviveDownstream = true;
		private long dynamicResourceTTL = Duration.ofSeconds(5).toMillis();
		private long declaredStaticTTL = Duration.ofDays(1).toMillis();
		private long idleConnectionTimeout = Duration.ofSeconds(30).toMillis();
		private BufferGenerator.BufferConfig bufferConfig = new BufferGenerator.BufferConfig(1024, true, BufferGenerator.directniobufs, null);

		public Builder(NAFConfig nafConfig) {
			this.nafConfig = nafConfig;
			listenerConfig = defaultListener();
		}

		public ConcurrentListenerConfig.Builder<?> getListenerConfig() {
			return listenerConfig;
		}

		public Builder withXmlConfig(XmlConfig cfg) {
			surviveDownstream = cfg.getBool("@survive_downstream", surviveDownstream);
			dynamicResourceTTL = cfg.getTime("@dyncache", dynamicResourceTTL);
			declaredStaticTTL = cfg.getTime("@permcache", declaredStaticTTL);
			idleConnectionTimeout = cfg.getTime("@timeout", idleConnectionTimeout);
			bufferConfig = BufferGenerator.BufferConfig.create(cfg, "niobuffers", bufferConfig);

			XmlConfig lxmlcfg = cfg.getSection("listener");
			getListenerConfig().withXmlConfig(lxmlcfg, nafConfig);
			return this;
		}

		public Builder withSurviveDownstream(boolean v) {
			surviveDownstream = v;
			return this;
		}

		public Builder withDynamicResourceTTL(long v) {
			dynamicResourceTTL = v;
			return this;
		}

		public Builder withDeclaredStaticTTL(long v) {
			declaredStaticTTL = v;
			return this;
		}

		public Builder withIdleConnectionTimeout(long v) {
			idleConnectionTimeout = v;
			return this;
		}

		public Builder withBufferConfig(BufferGenerator.BufferConfig v) {
			bufferConfig = v;
			return this;
		}

		public NafManConfig build() {
			return new NafManConfig(this);
		}

		private ConcurrentListenerConfig.Builder<?> defaultListener() {
			Supplier<NafManConfig> srvcfg = () -> this.build();
			ConcurrentListenerConfig.Builder<?> bldr = new ConcurrentListenerConfig.Builder<>()
					.withName("NAFMAN-Primary")
					.withServerFactory(NafManServer.Factory.class, srvcfg);
			if (nafConfig != null) {
				int port = nafConfig.assignPort(NAFConfig.RSVPORT_NAFMAN);
				bldr.withPort(port);
			}
			return bldr;
		}
	}
}
