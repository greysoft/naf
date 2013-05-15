/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public class ListenerSet
	implements com.grey.naf.EntityReaper
{
	public final String name;
	private final Dispatcher dsptch;
	private final com.grey.naf.EntityReaper reaper;
	private final Listener[] listeners;
	private int listen_cnt;

	public int configured() {return listeners == null ? 0 : listeners.length;}
	public int count() {return listen_cnt;}
	public Listener getListener(int idx) {return listeners[idx];}

	public ListenerSet(String name, Dispatcher dsptch, Object controller, com.grey.naf.EntityReaper rpr,
			String xpath, com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		this.dsptch = dsptch;
		this.reaper = rpr;
		this.name = "Listeners-"+name;
		com.grey.base.config.XmlConfig[] listencfg = cfg.subSections(xpath+com.grey.base.config.XmlConfig.XPATH_ENABLED);

		if (listencfg == null) {
			listeners = null;
			dsptch.logger.warn(name+": No listeners defined");
			return;
		}
		dsptch.logger.info(name+": Creating Listeners="+listencfg.length);
		listeners = new Listener[listencfg.length];

		for (int idx = 0; idx != listencfg.length; idx++) {
			if (controller instanceof ChannelMonitor) {
				ChannelMonitor handler = ChannelMonitor.class.cast(controller);
				listeners[idx] = new IterativeListener(null, dsptch, handler, reaper, listencfg[idx], cfgdflts);
			} else {
				listeners[idx] = new ConcurrentListener(null, dsptch, controller, this, listencfg[idx], cfgdflts);
			}
		}
	}

	public void start() throws java.io.IOException
	{
		if (listeners == null) return;
		dsptch.logger.info(name+": Launching Listeners="+listeners.length);

		for (int idx = 0; idx != listeners.length; idx++) {
			listen_cnt++;
			listeners[idx].start();
		}
	}

	public boolean stop()
	{
		if (listeners == null) return true;
		dsptch.logger.info(name+": Stopping Listeners="+listen_cnt+"/"+listeners.length);
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
		Listener lstnr = Listener.class.cast(obj);

		for (int idx = 0; idx != listeners.length; idx++) {
			if (listeners[idx] == lstnr) {
				listeners[idx] = null;
				listen_cnt--;
				break;
			}
		}
		String extra = (listen_cnt==0 ? " - reaper="+reaper : "");
		dsptch.logger.info(name+": Listener="+lstnr.name+" has terminated - remaining="+listen_cnt+extra);
		if (listen_cnt == 0 && reaper != null) reaper.entityStopped(this);
	}
}