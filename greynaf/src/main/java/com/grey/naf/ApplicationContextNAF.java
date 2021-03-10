/*
 * Copyright 2018-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.CM_Listener;
import com.grey.naf.nafman.NafManAgent;
import com.grey.naf.nafman.NafManConfig;
import com.grey.naf.nafman.PrimaryAgent;
import com.grey.naf.errors.NAFException;
import com.grey.naf.errors.NAFConfigException;

public class ApplicationContextNAF {

	public interface ItemFactory<T> {
		T create(ApplicationContextNAF appctx) throws Exception;
	}

	private static final ConcurrentMap<String,ApplicationContextNAF> contextsByName = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Integer,ApplicationContextNAF> contextsByPort = new ConcurrentHashMap<>();
	public static ApplicationContextNAF getContext(String name) {return contextsByName.get(name);}

	private static final AtomicInteger anonCount = new AtomicInteger();

	private final ConcurrentMap<String, Dispatcher> dispatchers = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, CM_Listener> listeners = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Object> namedItems = new ConcurrentHashMap<>();

	private final String ctxname;
	private final NAFConfig nafConfig;
	private final NafManConfig nafmanConfig;
	private final ExecutorService threadpool;

	private PrimaryAgent primaryAgent;
	private final Object primaryLock = new Object();

	public String getName() {return ctxname;}
	public NAFConfig getConfig() {return nafConfig;}
	public NafManConfig getNafManConfig() {return nafmanConfig;}
	public ExecutorService getThreadpool() {return threadpool;}

	public static ApplicationContextNAF create(String name, NAFConfig cfg, NafManConfig nafmanConfig) {
		if (name == null) name = "AnonAppContext-"+anonCount.incrementAndGet();
		ApplicationContextNAF ctx = new ApplicationContextNAF(name, cfg, nafmanConfig);
		
		ApplicationContextNAF dup = contextsByName.putIfAbsent(ctx.getName(), ctx);
		if (dup != null) throw new NAFConfigException("Duplicate app-context="+ctx+" - prev="+dup);
		
		if (ctx.getConfig().getBasePort() != NAFConfig.RSVPORT_ANON) {
			dup = contextsByPort.putIfAbsent(ctx.getConfig().getBasePort(), ctx);
			if (dup != null) {
				contextsByName.remove(ctx.getName());
				throw new NAFConfigException("Duplicate app-context port for "+ctx+" - prev="+dup);
			}
		}
		return ctx;
	}

	private ApplicationContextNAF(String name, NAFConfig cfg, NafManConfig nafmanConfig) {
		ctxname = name;
		nafConfig = cfg;
		this.nafmanConfig = nafmanConfig;

		if (nafConfig.getThreadPoolSize() == -1) {
			threadpool = Executors.newCachedThreadPool();
		} else {
			threadpool = Executors.newFixedThreadPool(nafConfig.getThreadPoolSize());
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
			synchronized (primaryLock) {
				// there's no reason why a primary agent wouldn't be the registered one, but do defensive check anyway
				if (agent == primaryAgent) primaryAgent = null;
			}
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

	public PrimaryAgent registerPrimaryAgent(Supplier<PrimaryAgent> supplier) {
		synchronized (primaryLock) {
			if (primaryAgent == null) primaryAgent = supplier.get();
			return primaryAgent;
		}
	}

	public PrimaryAgent getPrimaryAgent() {
		synchronized (primaryLock) {
			return primaryAgent;
		}
	}

	public <T> T getNamedItem(String name, ItemFactory<T> fact) {
		@SuppressWarnings("unchecked") T item = (T)namedItems.get(name);
		if (item == null && fact != null) {
			item = createNamedItem(name, fact);
		}
		return item;
	}

	public <T> T setNamedItem(String name, T item) {
		@SuppressWarnings("unchecked") T prev = (T)namedItems.put(name, item);
		return prev;
	}

	public void deregisterNamedItem(String name) {
		namedItems.remove(name);
	}

	private <T> T createNamedItem(String name, ItemFactory<T> fact) {
		@SuppressWarnings("unchecked") T item = (T)namedItems.computeIfAbsent(name, k -> {
			try {
				return fact.create(this);
			} catch (Exception ex) {
				throw new NAFException(ex);
			}
		});
		return item;
	}

	@Override
	public String toString() {
		String txt = "ApplicationContext-"+getName();
		if (getConfig().getBasePort() != NAFConfig.RSVPORT_ANON) txt += ":"+getConfig().getBasePort();
		return txt;
	}
}