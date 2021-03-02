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

import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.CM_Listener;
import com.grey.naf.nafman.NafManAgent;
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
	private final NAFConfig config;
	private final ExecutorService threadpool;

	public String getName() {return ctxname;}
	public NAFConfig getConfig() {return config;}
	public ExecutorService getThreadpool() {return threadpool;}

	public static ApplicationContextNAF create(String name, NAFConfig cfg) {
		if (name == null) name = "AnonAppContext-"+anonCount.incrementAndGet();
		ApplicationContextNAF ctx = new ApplicationContextNAF(name, cfg);
		ApplicationContextNAF dup = contextsByName.putIfAbsent(ctx.getName(), ctx);
		if (dup != null) throw new NAFConfigException("Duplicate app-context="+ctx+" - prev="+dup);
		if (ctx.getConfig().getBasePort() != NAFConfig.RSVPORT_ANON) {
			dup = contextsByPort.putIfAbsent(ctx.getConfig().getBasePort(), ctx);
			if (dup != null) throw new NAFConfigException("Duplicate app-context="+ctx+" - prev="+dup);
		}
		return ctx;
	}

	public static ApplicationContextNAF create(String name, NAFConfig.Defs defs) {
		NAFConfig cfg;
		try {
			cfg = NAFConfig.load(defs);
		} catch (Exception ex) { //this exception can't really happen in this code path
			throw new NAFConfigException("Failed to synthesise NAFConfig");
		}
		return create(name, cfg);
	}

	public static ApplicationContextNAF create(String name) {
		return create(name, new NAFConfig.Defs(NAFConfig.RSVPORT_ANON));
	}

	private ApplicationContextNAF(String name, NAFConfig cfg) {
		ctxname = name;
		config = cfg;
		threadpool = Executors.newFixedThreadPool(config.getThreadPoolSize());
	}

	public void register(Dispatcher d) {
		Dispatcher dup = dispatchers.putIfAbsent(d.getName(), d);
		if (dup != null) throw new NAFConfigException("Duplicate Dispatcher="+d+" - prev="+dup);
	}

	public void deregister(Dispatcher d) {
		dispatchers.remove(d.getName());
	}

	public Dispatcher getDispatcher(String name) {
		return dispatchers.get(name);
	}

	public Collection<Dispatcher> getDispatchers() {
		return dispatchers.values();
	}

	public void register(CM_Listener l) {
		CM_Listener dup = listeners.putIfAbsent(l.name, l);
		if (dup != null) throw new NAFConfigException("Duplicate listener="+l+" - prev="+dup);
	}

	public void deregister(CM_Listener l) {
		listeners.remove(l.name);
	}

	public CM_Listener getListener(String name) {
		return listeners.get(name);
	}

	public Collection<CM_Listener> getListeners() {
		return listeners.values();
	}

	public PrimaryAgent getPrimaryAgent() {
		for (Dispatcher d : getDispatchers()) {
			NafManAgent agent = d.getAgent();
			if (agent != null && agent.isPrimary()) return (PrimaryAgent)agent;
		}
		return null;
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