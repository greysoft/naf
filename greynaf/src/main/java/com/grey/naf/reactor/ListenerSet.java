/*
 * Copyright 2012-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.util.concurrent.atomic.AtomicInteger;

import com.grey.naf.EventListenerNAF;
import com.grey.naf.errors.NAFConfigException;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;

public class ListenerSet
	implements EventListenerNAF
{
	public final String name;
	private final Dispatcher dsptch;
	private final EventListenerNAF eventListener;
	private final CM_Listener[] listeners;
	private final AtomicInteger listenerCount = new AtomicInteger();

	public int configured() {return listeners == null ? 0 : listeners.length;}
	public int count() {return listenerCount.get();}
	public CM_Listener getListener(int idx) {return listeners[idx];}

	public ListenerSet(String grpname, Dispatcher d, Object controller, EventListenerNAF evtl, ConcurrentListenerConfig[] config) throws java.io.IOException {
		this(grpname, d, controller, evtl, config, false);
	}

	public ListenerSet(String grpname, Dispatcher d, Object controller, EventListenerNAF evtl, ConcurrentListenerConfig[] config, boolean iterative) throws java.io.IOException {
		dsptch = d;
		eventListener = evtl;
		name = "Listeners-"+d.getName()+"-"+grpname;
		if (config == null || config.length == 0) {
			listeners = null;
			d.getLogger().warn(name+": No listeners defined");
			return;
		}
		d.getLogger().info(name+": Creating Listeners="+config.length);
		listeners = new CM_Listener[config.length];

		for (int idx = 0; idx != config.length; idx++) {
			if (iterative) {
				listeners[idx] = IterativeListener.create(d, this, config[idx]);
			} else {
				listeners[idx] = ConcurrentListener.create(d, controller, this, config[idx]);
			}
		}
	}

	// Can set foreground true if already running within Dispatcher thread, else must be false
	public void start(boolean foreground) throws java.io.IOException {
		if (listeners == null) return;
		dsptch.getLogger().info(name+": Launching Listeners="+listeners.length+" with foreground="+foreground);

		for (CM_Listener l : listeners) {
			if (foreground) {
				l.startDispatcherRunnable();
			} else {
				dsptch.loadRunnable(l);
			}
			listenerCount.incrementAndGet();
		}
	}

	// The foreground param should match what was used in start()
	public boolean stop(boolean foreground) {
		if (listeners == null || listenerCount.get() == 0) return true;
		dsptch.getLogger().info(name+": Stopping Listeners="+listenerCount.get()+"/"+listeners.length);

		for (int idx = 0; idx != listeners.length; idx++) {
			CM_Listener l = listeners[idx];
			if (l != null) {
				try {
					if (foreground) {
						if (l.stopDispatcherRunnable()) {
							listeners[idx] = null;
							listenerCount.decrementAndGet();
						}
					} else {
						dsptch.unloadRunnable(l);
					}
				} catch (Exception ex) {
					throw new NAFConfigException("Dispatcher="+dsptch.getName()+" failed to unload listener="+l.getName());
				}
			}
		}
		return false;
	}

	@Override
	public void eventIndication(String eventId, Object evtsrc, Object data) {
		if (!(evtsrc instanceof CM_Listener) || !EventListenerNAF.EVENTID_ENTITY_STOPPED.equals(eventId)) {
			if (eventListener != null) eventListener.eventIndication(eventId, evtsrc, data);
			return;
		}
		CM_Listener lstnr = (CM_Listener)evtsrc;

		for (int idx = 0; idx != listeners.length; idx++) {
			if (listeners[idx] == lstnr) {
				listeners[idx] = null;
				listenerCount.decrementAndGet();
				break;
			}
		}
		int cnt = listenerCount.get();
		dsptch.getLogger().info(name+": Listener="+lstnr.getName()+" has terminated - remaining="+cnt+" - event-listener="+eventListener);
		
		if (cnt == 0 && eventListener != null) {
			eventListener.eventIndication(EventListenerNAF.EVENTID_ENTITY_STOPPED, this, null);
		}
	}
}