/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public class ListenerSet
	implements com.grey.naf.EntityReaper
{
	public final String name;
	private final com.grey.naf.reactor.Dispatcher dsptch;
	private final com.grey.naf.EntityReaper reaper;
	private final com.grey.naf.reactor.Listener[] listeners;
	private int listen_cnt;

	public ListenerSet(String name, com.grey.naf.reactor.Dispatcher dsptch, Object controller, com.grey.naf.EntityReaper reaper,
			String xpath, com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		this.dsptch = dsptch;
		this.reaper = reaper;
		this.name = name;
		com.grey.base.config.XmlConfig[] listencfg = cfg.subSections(xpath);

		if (listencfg == null) {
			throw new com.grey.base.ConfigException(name+": No listeners found");
		}
		dsptch.logger.info(name+": Creating Listeners="+listencfg.length);
		listeners = new com.grey.naf.reactor.Listener[listencfg.length];

		for (int idx = 0; idx != listencfg.length; idx++) {
			if (controller instanceof ChannelMonitor) {
				ChannelMonitor handler = ChannelMonitor.class.cast(controller);
				listeners[idx] = new com.grey.naf.reactor.IterativeListener(null, dsptch, handler, listencfg[idx], cfgdflts);
			} else {
				listeners[idx] = new com.grey.naf.reactor.ConcurrentListener(null, dsptch, controller, listencfg[idx], cfgdflts);
			}
		}
	}

	public void start() throws java.io.IOException
	{
		dsptch.logger.info(name+": Launching Listeners="+listeners.length);

		for (int idx = 0; idx != listeners.length; idx++) {
			listen_cnt++;
			listeners[idx].start(this);
		}
	}

	public boolean stop()
	{
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
		com.grey.naf.reactor.Listener lstnr = com.grey.naf.reactor.Listener.class.cast(obj);

		for (int idx = 0; idx != listeners.length; idx++) {
			if (listeners[idx] == lstnr) {
				listeners[idx] = null;
				listen_cnt--;
				break;
			}
		}
		dsptch.logger.info(name+": Listener="+lstnr.name+" has terminated - remaining="+listen_cnt);
		if (listen_cnt == 0 && reaper != null) reaper.entityStopped(this);
	}
}
