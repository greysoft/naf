/*
 * Copyright 2013-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import java.time.Duration;
import java.util.function.Supplier;

import com.grey.base.config.XmlConfig;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.BufferSpec;
import com.grey.naf.NAFConfig;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;

public class NafManConfig
{
	private final boolean surviveDownstream; //if false, Primary agent halts its Dispatcher if any secondary agents halt
	private final long idleConnectionTimeout; //idle timeout the NAFMAN server applies to incoming connections
	private final ConcurrentListenerConfig listenerConfig; //the NAFMAN server
	private BufferSpec.BufferConfig bufferConfig;

	// these are TTLs for cached responses in NAFMAN server
	private final long dynamicResourceTTL;
	private final long declaredStaticTTL; //this is for permanently cached static content, but we have to declare a finite time to clients

	private NafManConfig(Builder bldr) {
		surviveDownstream = bldr.surviveDownstream;
		dynamicResourceTTL = bldr.dynamicResourceTTL;
		declaredStaticTTL = bldr.declaredStaticTTL;
		idleConnectionTimeout = bldr.idleConnectionTimeout;
		listenerConfig = bldr.listenerConfig;
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

	public BufferSpec.BufferConfig getBufferConfig() {
		return bufferConfig;
	}


	public static class Builder {
		private boolean surviveDownstream = true;
		private long dynamicResourceTTL = Duration.ofSeconds(5).toMillis();
		private long declaredStaticTTL = Duration.ofDays(1).toMillis();
		private long idleConnectionTimeout = Duration.ofSeconds(30).toMillis();
		private ConcurrentListenerConfig listenerConfig;
		private BufferSpec.BufferConfig bufferConfig = new BufferSpec.BufferConfig(1024, true, BufferSpec.directniobufs, null);

		public Builder withXmlConfig(XmlConfig cfg, ApplicationContextNAF appctx) {
			surviveDownstream = cfg.getBool("@survive_downstream", surviveDownstream);
			dynamicResourceTTL = cfg.getTime("@dyncache", dynamicResourceTTL);
			declaredStaticTTL = cfg.getTime("@permcache", declaredStaticTTL);
			idleConnectionTimeout = cfg.getTime("@timeout", idleConnectionTimeout);
			bufferConfig = BufferSpec.BufferConfig.create(cfg, "niobuffers", bufferConfig);

			XmlConfig lxmlcfg = cfg.getSection("listener");
			int lstnport = appctx.getConfig().assignPort(NAFConfig.RSVPORT_NAFMAN);
			Supplier<NafManConfig> srvcfg = () -> this.build();

			listenerConfig = new ConcurrentListenerConfig.Builder<>()
					.withName("NAFMAN-Primary")
					.withPort(lstnport)
					.withServerFactory(NafManServer.Factory.class, srvcfg)
					.withXmlConfig(lxmlcfg, appctx)
					.build();
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

		public Builder withListenerConfig(ConcurrentListenerConfig v) {
			listenerConfig = v;
			return this;
		}

		public Builder withBufferConfig(BufferSpec.BufferConfig v) {
			bufferConfig = v;
			return this;
		}

		public NafManConfig build() {
			return new NafManConfig(this);
		}
	}
}
