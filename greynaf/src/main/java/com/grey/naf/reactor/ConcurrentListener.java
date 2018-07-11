/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.XmlConfig;
import com.grey.base.collections.HashedSet;
import com.grey.base.collections.ObjectWell;
import com.grey.naf.EntityReaper;
import com.grey.naf.NAFConfig;
import com.grey.naf.errors.NAFConfigException;
import com.grey.logging.Logger.LEVEL;

public class ConcurrentListener
	extends CM_Listener
{
	/**
	 * In addition to providing the explicit interface methods, factory classes must also provide a constructor
	 * with this signature:<br>
	 * <code>classname(com.grey.naf.reactor.CM_Listener, com.grey.base.config.XmlConfig)</code>
	 */
	public interface ServerFactory
		extends ObjectWell.ObjectFactory
	{
		public Class<? extends CM_Server> getServerClass();
		public void shutdown();
	}

	public static ConcurrentListener create(String lname, Dispatcher d, Object controller, EntityReaper rpr,
			XmlConfig cfg, java.util.Map<String,Object> cfgdflts) throws java.io.IOException {
		ConcurrentListener l = new ConcurrentListener(lname, d, controller, rpr, cfg, cfgdflts);
		d.getApplicationContext().register(l);
		return l;
	}

	public static ConcurrentListener create(String lname, Dispatcher d, Object controller, EntityReaper rpr,
			XmlConfig cfg, Class<?> factclass, String iface, int port) throws java.io.IOException {
		return create(lname, d, controller, rpr, cfg, makeDefaults(factclass, iface, port));
	}

	private final ServerFactory serverFactory;
	private final HashedSet<CM_Server> activeservers = new HashedSet<>();
	private final ObjectWell<CM_Server> spareservers;
	private boolean in_sync_stop;

	@Override
	public Class<?> getServerType() {return serverFactory.getServerClass();}

	private ConcurrentListener(String lname, Dispatcher d, Object controller, EntityReaper rpr,
			XmlConfig cfg, java.util.Map<String,Object> cfgdflts) throws java.io.IOException
	{
		super(lname, d, controller, rpr, cfg, cfgdflts);

		// So far, we've only read attributes from the main config block. Check if this Listener's config is linked to another's, before
		// delving into the contents of its Server config block.
		if (cfg != null) {
			String linkname = cfg.getValue("@configlink", false, null);
			if (linkname != null) cfg = cfg.getSection("../listener[@name='"+linkname+"']");
		}
		Class<?> clss_fact = (cfgdflts == null ? null : (Class<?>)cfgdflts.get(CFGMAP_FACTCLASS));
		XmlConfig servercfg = (cfg == null ? null : cfg.getSection("server"));
		Object f = NAFConfig.createEntity(servercfg, clss_fact, ServerFactory.class, false,
				new Class<?>[]{CM_Listener.class, XmlConfig.class},
				new Object[]{this, servercfg});
		serverFactory = ServerFactory.class.cast(f);

		if (!CM_Server.class.isAssignableFrom(serverFactory.getServerClass())) {
			throw new NAFConfigException("Listener="+name+": Factory="+serverFactory.getClass().getName()
					+" has incompatible server-type="+serverFactory.getServerClass().getName()
					+" - require subclass of "+CM_Server.class.getName());
		}

		int srvmin = getInt(cfgdflts, CFGMAP_INITSPAWN, 0);
		int srvmax = getInt(cfgdflts, CFGMAP_MAXSPAWN, 0);
		int srvincr = getInt(cfgdflts, CFGMAP_INCRSPAWN, 0);
		if (cfg != null) {
			srvmin = cfg.getInt("@initservers", false, srvmin);
			srvmax = cfg.getInt("@maxservers", false, srvmax);
			srvincr = cfg.getInt("@incrservers", false, srvincr);
		}
		spareservers = new ObjectWell<>(serverFactory.getServerClass(), serverFactory,
				"Listener_"+name+":"+getPort()+"_Servers", srvmin, srvmax, srvincr);

		getLogger().info("Listener="+name+": Server="+serverFactory.getServerClass().getName()
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
		getLogger().info("Listener="+name+" stopped "+(arr.length - activeservers.size())+"/"+arr.length+" servers");
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
					getLogger().log(routine ? LEVEL.TRC : LEVEL.INFO, ex, !routine, "Listener="+name+": Error fielding connection="+connsock);
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
			getLogger().info("Listener="+name+" dropping connection because no spare servers - "+connsock);
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
