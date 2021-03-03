/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.collections.HashedSet;
import com.grey.base.collections.ObjectWell;
import com.grey.naf.EntityReaper;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;
import com.grey.logging.Logger.LEVEL;

public class ConcurrentListener
	extends CM_Listener
{
	/**
	 * In addition to providing the explicit interface methods, factory classes must also provide a constructor
	 * with this signature:<br>
	 * <code>classname(com.grey.naf.reactor.CM_Listener listener, Object config)</code>
	 */
	public interface ServerFactory
		extends ObjectWell.ObjectFactory
	{
		public Class<? extends CM_Server> getServerClass();
		public void shutdown();
	}

	private final ServerFactory serverFactory;
	private final HashedSet<CM_Server> activeservers = new HashedSet<>();
	private final ObjectWell<CM_Server> spareservers;

	private boolean in_sync_stop;

	@Override
	public Class<?> getServerType() {return serverFactory.getServerClass();}

	public ConcurrentListener(Dispatcher d, Object controller, EntityReaper rpr, ConcurrentListenerConfig config) throws java.io.IOException
	{
		super(d, controller, rpr, config);
		int srvmin = config.getMinServers();
		int srvmax = config.getMaxServers();
		int srvincr = config.getServersIncrement();
		serverFactory = config.getServerFactoryGenerator().apply(this);

		spareservers = new ObjectWell<>(serverFactory.getServerClass(), serverFactory,
				"Listener_"+getName()+":"+getPort()+"_Servers", srvmin, srvmax, srvincr);

		getLogger().info("Listener="+getName()+" created with Server="+serverFactory.getServerClass().getName()
				+", Factory="+serverFactory.getClass().getName()
				+" - init/max/incr="+spareservers.size()+"/"+srvmax+"/"+srvincr);
	}

	@Override
	protected boolean stopListener()
	{
		//cannot iterate on activeservers as it gets modified during the loop, so take a copy
		in_sync_stop = true;
		CM_Server[] arr = activeservers.toArray(new CM_Server[activeservers.size()]);
		for (int idx = 0; idx != arr.length; idx++) {
			CM_Server srvr = arr[idx];
			boolean stopped = srvr.abortServer();
			if (stopped || !getDispatcher().isRunning()) deallocateServer(srvr);
		}
		getLogger().info("Listener="+getName()+" stopped "+(arr.length - activeservers.size())+"/"+arr.length+" servers");
		in_sync_stop = false;
		return (activeservers.size() == 0);
	}

	@Override
	protected void listenerStopped()
	{
		serverFactory.shutdown();
	}

	@Override
	public void entityStopped(Object obj)
	{
		CM_Server srvr = (CM_Server)obj;
		if (getReporter() != null) getReporter().listenerNotification(Reporter.EVENT.STOPPED, srvr);
		boolean not_dup = deallocateServer(srvr);
		if (not_dup && inShutdown() && !in_sync_stop && activeservers.size() == 0) stopped(true);
	}

	// We know that the readyOps argument must indicate an Accept (that's all we registered for), so don't bother checking it.
	// NB: Can't loop on SelectionKey.isAcceptable(), as it will remain True until we return to Dispatcher
	@Override
	void ioIndication(int readyOps) throws java.io.IOException
	{
		java.nio.channels.ServerSocketChannel srvsock = (java.nio.channels.ServerSocketChannel)getChannel();
		java.nio.channels.SocketChannel connsock;

		while ((connsock = srvsock.accept()) != null) {
			try {
				handleConnection(connsock);
			} catch (Throwable ex) {
				if (!(ex instanceof CM_Stream.BrokenPipeException)) { //BrokenPipe already logged
					boolean routine = ex instanceof java.io.IOException;
					getLogger().log(routine ? LEVEL.TRC : LEVEL.INFO, ex, !routine, "Listener="+getName()+": Error fielding connection="+connsock);
				}
				connsock.close();
			}
		}
	}

	private void handleConnection(java.nio.channels.SocketChannel connsock)
		throws java.io.IOException
	{
		CM_Server srvr = spareservers.extract();
		if (srvr == null) {
			// we're at max capacity - can't allocate any more server objects
			getLogger().info("Listener="+getName()+" dropping connection because no spare servers - "+connsock);
			connsock.close();
			return;
		}
		activeservers.add(srvr);
		if (getReporter() != null) getReporter().listenerNotification(Reporter.EVENT.STARTED, srvr);
		boolean ok = false;

		try {
			srvr.accepted(connsock, this);
			ok = true;
		} finally {
			if (!ok) {
				entityStopped(srvr);
				getDispatcher().conditionalDeregisterIO(srvr);
				connsock.close();
			}
		}
	}

	private boolean deallocateServer(CM_Server srvr)
	{
		//guard against duplicate entityStopped() notifications
		if (!activeservers.remove(srvr)) {
			return false;
		}
		spareservers.store(srvr);
		return true;
	}
}
