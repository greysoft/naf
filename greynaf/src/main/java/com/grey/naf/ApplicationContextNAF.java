/*
 * Copyright 2018-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import java.util.Map;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.CM_Listener;
import com.grey.naf.nafman.NafManConfig;
import com.grey.naf.nafman.NafManAgent;
import com.grey.naf.nafman.PrimaryAgent;
import com.grey.logging.Parameters;
import com.grey.logging.Factory;
import com.grey.logging.Logger;
import com.grey.naf.errors.NAFConfigException;

public class ApplicationContextNAF {

	private static final Map<String,ApplicationContextNAF> contextsByName = new ConcurrentHashMap<>();
	private static final Map<Integer,ApplicationContextNAF> contextsByPort = new ConcurrentHashMap<>();
	public static ApplicationContextNAF getContext(String name) {return contextsByName.get(name);}

	private static final AtomicInteger anonCount = new AtomicInteger();

	private final Map<String, Dispatcher> dispatchers = new ConcurrentHashMap<>();
	private final Map<String, CM_Listener> listeners = new ConcurrentHashMap<>();
	private final Map<String, Object> namedItems = new ConcurrentHashMap<>();

	private final String ctxname;
	private final NAFConfig nafConfig;
	private final NafManConfig nafmanConfig;
	private final ExecutorService threadpool;
	private final Logger bootLogger; //refered to as boot logger as each Dispatcher typically has its own

	public String getName() {return ctxname;}
	public NAFConfig getNafConfig() {return nafConfig;}
	public NafManConfig getNafManConfig() {return nafmanConfig;}
	public ExecutorService getThreadpool() {return threadpool;}
	public Logger getBootLogger() {return bootLogger;}

	private static ApplicationContextNAF create(Builder bldr) throws IOException {
		ApplicationContextNAF ctx = new ApplicationContextNAF(bldr);
		register(ctx);
		return ctx;
	}

	private static void register(ApplicationContextNAF ctx) {
		ApplicationContextNAF dup = contextsByName.putIfAbsent(ctx.getName(), ctx);
		if (dup != null) throw new NAFConfigException("Duplicate app-context="+ctx+" - prev="+dup);

		if (ctx.getNafConfig().getBasePort() != NAFConfig.RSVPORT_ANON) {
			dup = contextsByPort.putIfAbsent(ctx.getNafConfig().getBasePort(), ctx);
			if (dup != null) {
				contextsByName.remove(ctx.getName());
				throw new NAFConfigException("Duplicate app-context port for "+ctx+" - prev="+dup);
			}
		}
	}

	public static void unregister(ApplicationContextNAF ctx) {
		if (ctx.getNafConfig().getBasePort() != NAFConfig.RSVPORT_ANON) {
			contextsByPort.remove(ctx.nafConfig.getBasePort());
		}
		contextsByName.remove(ctx.ctxname);
	}

	private ApplicationContextNAF(Builder bldr) throws IOException {
		ctxname = (bldr.ctxname == null ? "AnonAppContext-"+anonCount.incrementAndGet() : bldr.ctxname);
		bootLogger = (bldr.bootLogger == null ? Factory.getLogger(new Parameters.Builder().build(), "bootlogger-appcx-"+ctxname) : bldr.bootLogger);
		nafConfig = (bldr.nafConfig == null ? new NAFConfig.Builder().build() : bldr.nafConfig);
		nafmanConfig = bldr.nafmanConfig;

		if (bldr.threadpool == null) {
			if (nafConfig.getThreadPoolSize() == -1) {
				threadpool = Executors.newCachedThreadPool();
			} else {
				threadpool = Executors.newFixedThreadPool(nafConfig.getThreadPoolSize());
			}
		} else {
			threadpool = bldr.threadpool;
		}
	}

	public void register(Dispatcher d) {
		Dispatcher dup = dispatchers.putIfAbsent(d.getName(), d);
		if (dup != null) throw new NAFConfigException("Duplicate Dispatcher="+d+" - prev="+dup);
	}

	public void deregister(Dispatcher d) {
		dispatchers.remove(d.getName());
		NafManAgent agent = d.getNafManAgent();
		if (agent != null && agent.isPrimary()) {
			removeNamedItem(PrimaryAgent.class.getName());
		}
	}

	public Dispatcher getDispatcher(String name) {
		return dispatchers.get(name);
	}

	public Collection<Dispatcher> getDispatchers() {
		return dispatchers.values();
	}

	public void register(CM_Listener l) {
		CM_Listener dup = listeners.putIfAbsent(l.getName(), l);
		if (dup != null) throw new NAFConfigException("Duplicate listener="+l+" - prev="+dup);
	}

	public void deregister(CM_Listener l) {
		listeners.remove(l.getName());
	}

	public CM_Listener getListener(String name) {
		return listeners.get(name);
	}

	public Collection<CM_Listener> getListeners() {
		return listeners.values();
	}

	@SuppressWarnings("unchecked")
	public <T> T getNamedItem(String name, Supplier<T> supplier) {
		if (supplier == null) return (T)namedItems.get(name);
		return (T)namedItems.computeIfAbsent(name, k -> supplier.get());
	}

	public <T> T setNamedItem(String name, T item) {
		@SuppressWarnings("unchecked") T prev = (T)namedItems.put(name, item);
		return prev;
	}

	public <T> T removeNamedItem(String name) {
		@SuppressWarnings("unchecked") T prev = (T)namedItems.remove(name);
		return prev;
	}

	@Override
	public String toString() {
		return "ApplicationContextNAF["
				+"ctxname=" + ctxname
				+", nafConfig=" + nafConfig
				+", nafmanConfig=" + nafmanConfig
				+", bootLogger=" + bootLogger
				+", threadpool="+ threadpool
				+", dispatchers=" + dispatchers
				+", listeners=" + listeners
				+", namedItems=" + namedItems
				+"]";
	}

	public static Builder builder() {
		return new Builder();
	}


	public static class Builder {
		private String ctxname;
		private NAFConfig nafConfig;
		private NafManConfig nafmanConfig;
		private ExecutorService threadpool;
		private Logger bootLogger;

		private Builder() {}

		public Builder withName(String v) {
			ctxname = v;
			return this;
		}

		public Builder withNafConfig(NAFConfig v) {
			nafConfig = v;
			return this;
		}

		public Builder withNafManConfig(NafManConfig v) {
			nafmanConfig = v;
			return this;
		}

		public Builder withThreadPool(ExecutorService v) {
			threadpool = v;
			return this;
		}

		public Builder withBootLogger(Logger v) {
			bootLogger = v;
			return this;
		}

		public ApplicationContextNAF build() {
			try {
				return ApplicationContextNAF.create(this);
			} catch (Exception ex) {
				throw new NAFConfigException("Failed to create ApplicationContext="+ctxname, ex);
			}
		}
	}
}