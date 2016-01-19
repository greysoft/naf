/*
 * Copyright 2010-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class IterativeListener
	extends CM_Listener
{
	public interface ServerFactory
	{
		public CM_Server createServer(CM_Listener l);
	}

	public final CM_Server cnxhandler;

	@Override
	public Class<?> getServerType() {return cnxhandler.getClass();}

	public IterativeListener(String lname, Dispatcher d,  ServerFactory fact, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, String iface, int port)
					throws com.grey.base.GreyException, java.io.IOException
	{
		this(lname, d, fact, rpr, cfg, makeDefaults(null, iface, port));
	}

	public IterativeListener(String lname, Dispatcher d,  ServerFactory fact, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		super(lname, d, fact, rpr, cfg, cfgdflts);
		cnxhandler = fact.createServer(this);
		log.info("Listener="+name+": Iterative handler is "+cnxhandler.getClass().getName());
	}

	@Override
	public void entityStopped(Object obj)
	{
		if (reporter != null) reporter.listenerNotification(Reporter.EVENT.STOPPED, cnxhandler);
		try {
			enableListen();
		} catch (Throwable ex) {
			log.log(LEVEL.ERR, ex, true, "Listener="+name+" failed to resume listening");
			stop(true);
		}
	}

	// We know that the readyOps argument must indicate an Accept (that's all we registered for), so don't bother checking it.
	@Override
	void ioIndication(int readyOps) throws java.io.IOException
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
			if (reporter != null) reporter.listenerNotification(Reporter.EVENT.STARTED, cnxhandler);

			try {
				cnxhandler.accepted(connsock, this);
			} catch (Throwable ex) {
				LEVEL lvl = (ex instanceof RuntimeException ? LEVEL.ERR : LEVEL.TRC);
				log.log(lvl, ex, lvl==LEVEL.ERR, "Listener="+name+": Error fielding connection");
				dsptch.conditionalDeregisterIO(cnxhandler);
			}
		}
	}
}