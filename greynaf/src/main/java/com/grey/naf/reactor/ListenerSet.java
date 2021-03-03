/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.XmlConfig;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;

public class ListenerSet
	implements com.grey.naf.EntityReaper
{
	public final String name;
	private final Dispatcher dsptch;
	private final com.grey.naf.EntityReaper reaper;
	private final CM_Listener[] listeners;
	private int listen_cnt;

	public int configured() {return listeners == null ? 0 : listeners.length;}
	public int count() {return listen_cnt;}
	public CM_Listener getListener(int idx) {return listeners[idx];}

	public ListenerSet(String grpname, Dispatcher d, Object controller, com.grey.naf.EntityReaper rpr, ConcurrentListenerConfig[] config) throws java.io.IOException
	{
		dsptch = d;
		reaper = rpr;
		name = "Listeners-"+d.getName()+"-"+grpname;
		if (config == null || config.length == 0) {
			listeners = null;
			d.getLogger().warn(name+": No listeners defined");
			return;
		}
		d.getLogger().info(name+": Creating Listeners="+config.length);
		listeners = new CM_Listener[config.length];

		for (int idx = 0; idx != config.length; idx++) {
			if (controller instanceof IterativeListener.ServerFactory) {
				listeners[idx] = new IterativeListener(d, (IterativeListener.ServerFactory)controller, reaper, config[idx]);
			} else {
				listeners[idx] = new ConcurrentListener(d, controller, this, (ConcurrentListenerConfig)config[idx]);
			}
		}
	}

	public void start() throws java.io.IOException
	{
		if (listeners == null) return;
		dsptch.getLogger().info(name+": Launching Listeners="+listeners.length);

		for (int idx = 0; idx != listeners.length; idx++) {
			listen_cnt++;
			listeners[idx].start();
		}
	}

	public boolean stop()
	{
		if (listeners == null) return true;
		dsptch.getLogger().info(name+": Stopping Listeners="+listen_cnt+"/"+listeners.length);
		boolean stopped = true;

		for (int idx = 0; idx != listeners.length; idx++) {
			if (listeners[idx] != null) {
				if (listeners[idx].stop()) {
					listeners[idx] = null;
					listen_cnt--;
				}
			}
			if (listeners[idx] != null) stopped = false;
		}
		return stopped;
	}

	@Override
	public void entityStopped(Object obj)
	{
		CM_Listener lstnr = CM_Listener.class.cast(obj);

		for (int idx = 0; idx != listeners.length; idx++) {
			if (listeners[idx] == lstnr) {
				listeners[idx] = null;
				listen_cnt--;
				break;
			}
		}
		String extra = (listen_cnt==0 ? " - reaper="+reaper : "");
		dsptch.getLogger().info(name+": Listener="+lstnr.getName()+" has terminated - remaining="+listen_cnt+extra);
		if (listen_cnt == 0 && reaper != null) reaper.entityStopped(this);
	}

	public void setReporter(CM_Listener.Reporter r)
	{
		for (int idx = 0; idx != listeners.length; idx++) {
			if (listeners[idx] != null) listeners[idx].setReporter(r);
		}
	}

	public static ConcurrentListenerConfig[] makeConfig(String grpname, Dispatcher d, String xpath, XmlConfig xmlcfg,
															int port, int sslport, Class<? extends ConcurrentListener.ServerFactory> factoryClass)
			throws java.io.IOException {
		XmlConfig[] lxmlcfg = xmlcfg.getSections(xpath+XmlConfig.XPATH_ENABLED);
		int cnt = (lxmlcfg == null ? 0 : lxmlcfg.length);
		ConcurrentListenerConfig[] lcfg = new ConcurrentListenerConfig[cnt];
		for (int idx = 0; idx != cnt; idx++) {
			lcfg[idx] = new ConcurrentListenerConfig.Builder<>()
					.withName(grpname+"-"+idx)
					.withPort(port)
					.withPortSSL(sslport)
					.withServerFactoryClass(factoryClass)
					.withXmlConfig(lxmlcfg[idx], d.getApplicationContext())
					.build();
		}
		return lcfg;
	}
}