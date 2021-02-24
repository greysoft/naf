/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

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

	public ListenerSet(String lname, Dispatcher d, Object controller, com.grey.naf.EntityReaper rpr,
			String xpath, com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts) throws java.io.IOException
	{
		dsptch = d;
		reaper = rpr;
		name = "Listeners-"+(lname == null ? dsptch.getName() : lname);
		com.grey.base.config.XmlConfig[] listencfg = cfg.getSections(xpath+com.grey.base.config.XmlConfig.XPATH_ENABLED);

		if (listencfg == null) {
			listeners = null;
			d.getLogger().warn(lname+": No listeners defined");
			return;
		}
		d.getLogger().info(lname+": Creating Listeners="+listencfg.length);
		listeners = new CM_Listener[listencfg.length];

		for (int idx = 0; idx != listencfg.length; idx++) {
			if (controller instanceof IterativeListener.ServerFactory) {
				listeners[idx] = IterativeListener.create(lname, d, (IterativeListener.ServerFactory)controller,
															reaper, listencfg[idx], cfgdflts);
			} else {
				listeners[idx] = ConcurrentListener.create(lname, d, controller, this, listencfg[idx], cfgdflts);
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
		dsptch.getLogger().info(name+": Listener="+lstnr.name+" has terminated - remaining="+listen_cnt+extra);
		if (listen_cnt == 0 && reaper != null) reaper.entityStopped(this);
	}

	public void setReporter(CM_Listener.Reporter r)
	{
		for (int idx = 0; idx != listeners.length; idx++) {
			if (listeners[idx] != null) listeners[idx].setReporter(r);
		}
	}
}