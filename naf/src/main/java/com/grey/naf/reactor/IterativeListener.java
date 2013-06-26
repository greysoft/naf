/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class IterativeListener
	extends Listener
{
	private final Class<?> serverType;
	private final ChannelMonitor cnxhandler;  // pre-existing iterative connection handler, which Listener does not own

	@Override
	public Class<?> getServerType() {return serverType;}

	public IterativeListener(String lname, Dispatcher d,  ChannelMonitor handler, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, String iface, int port)
					throws com.grey.base.GreyException, java.io.IOException
	{
		this(lname, d, handler, rpr, cfg, makeDefaults(null, iface, port));
	}

	public IterativeListener(String lname, Dispatcher d,  ChannelMonitor handler, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		super(lname, d, handler, rpr, cfg, cfgdflts);
		cnxhandler = handler;
		serverType = cnxhandler.getClass();
		log.info("Listener="+name+": Iterative handler is "+cnxhandler.getClass().getName());
	}

	@Override
	public void entityStopped(Object obj)
	{
		try {
			enableListen();
		} catch (Throwable ex) {
			log.log(LEVEL.ERR, ex, true, "Listener="+name+" failed to resume listening");
			stop(true);
		}
	}

	@Override
	protected void connectionReceived() throws java.io.IOException
	{
		java.nio.channels.ServerSocketChannel srvsock = (java.nio.channels.ServerSocketChannel)iochan;
		java.nio.channels.SocketChannel connsock = srvsock.accept();

		if (connsock != null) {
			try {
				disableListen();
			} catch (Throwable ex) {
				log.log(LEVEL.ERR, ex, true, "Listener="+name+" failed to suspend listening");
				stop(true);
				return;
			}
			try {
				cnxhandler.accepted(connsock, this);
			} catch (Throwable ex) {
				log.log(LEVEL.TRC, ex, true, "Listener="+name+": Error fielding connection");
				dsptch.conditionalDeregisterIO(cnxhandler);
			}
		}
	}
}