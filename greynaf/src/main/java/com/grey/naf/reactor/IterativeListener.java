/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;
import com.grey.naf.reactor.config.ListenerConfig;
import com.grey.naf.EntityReaper;
import com.grey.naf.errors.NAFException;

public class IterativeListener
	extends CM_Listener
{
	private final CM_Server cnxhandler;

	public CM_Server getConnectionHandler() {return cnxhandler;}

	public static IterativeListener create(Dispatcher d, EntityReaper rpr, ListenerConfig config) throws java.io.IOException {
		return new IterativeListener(d, rpr, config);
	}

	private IterativeListener(Dispatcher d, EntityReaper rpr, ListenerConfig config) throws java.io.IOException {
		super(d, null, rpr, config);
		cnxhandler = getServerFactory().createServer();
		getLogger().info("Iterative Listener="+getName()+" created with handler="+cnxhandler.getClass().getName());
	}

	@Override
	public void entityStopped(Object obj) {
		if (getReporter() != null) getReporter().listenerNotification(Reporter.EVENT.STOPPED, cnxhandler);
		try {
			enableListen();
		} catch (Throwable ex) {
			getLogger().log(LEVEL.ERR, ex, true, "Listener="+getName()+" failed to resume listening");
			stop(true);
		}
	}

	// We know that the readyOps argument must indicate an Accept (that's all we registered for), so don't bother checking it.
	@Override
	void ioIndication(int readyOps) throws java.io.IOException {
		java.nio.channels.ServerSocketChannel srvsock = (java.nio.channels.ServerSocketChannel)getChannel();
		java.nio.channels.SocketChannel connsock = srvsock.accept();

		if (connsock != null) {
			try {
				disableListen();
			} catch (Throwable ex) {
				getLogger().log(LEVEL.ERR, ex, true, "Listener="+getName()+" failed to suspend listening");
				stop(true);
				return;
			}
			if (getReporter() != null) getReporter().listenerNotification(Reporter.EVENT.STARTED, cnxhandler);

			try {
				cnxhandler.accepted(connsock, this);
			} catch (Throwable ex) {
				LEVEL lvl = (NAFException.isError(ex) ? LEVEL.ERR : LEVEL.TRC);
				getLogger().log(lvl, ex, lvl==LEVEL.ERR, "Listener="+getName()+": Error fielding connection");
				getDispatcher().conditionalDeregisterIO(cnxhandler);
			}
		}
	}
}