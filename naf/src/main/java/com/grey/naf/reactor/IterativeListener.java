/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public final class IterativeListener
	extends Listener
{
	private final Class<?> serverType;
	private final ChannelMonitor cnxhandler;  // pre-existing iterative connection handler, which Listener does not own

	@Override
	public Class<?> getServerType() {return serverType;}

	public IterativeListener(String lname, com.grey.naf.reactor.Dispatcher d,  ChannelMonitor handler,
			com.grey.base.config.XmlConfig cfg, String iface, int port)
					throws com.grey.base.GreyException, java.io.IOException
	{
		this(lname, d, handler, cfg, makeDefaults(null, iface, port));
	}

	public IterativeListener(String lname, com.grey.naf.reactor.Dispatcher d,  ChannelMonitor handler,
			com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		super(lname, d, handler, cfg, cfgdflts);
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
			log.error("Listener="+name+" failed to resume listening", ex);
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
				try {
					disableListen();
				} catch (Throwable ex) {
					log.error("Listener="+name+" failed to suspend listening", ex);
					stop(true);
					return;
				}
				cnxhandler.accepted(connsock, this);
			} catch (Throwable ex) {
				log.info("Listener="+name+": Error fielding connection", ex);
			}
		}
	}
}
