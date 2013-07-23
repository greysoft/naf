/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.logging.Logger.LEVEL;

public final class ConcurrentListener
	extends Listener
{
	/**
	 * Servers that are spawned by the ConcurrentListener must be subclasses of this base class.
	 * <br/>
	 * In addition to overriding its explicit methods, the subclass must also provide a constructor
	 * with this signature:<br/>
	 * <code>classname(com.grey.naf.reactor.ConcurrentListener, com.grey.base.config.XmlConfig)</code><br/>
	 * That subclass constructor must in turn call the
	 * <code>Server(ConcurrentListener)</code>
	 * constructor shown below.
	 */
	public static abstract class Server
		extends ChannelMonitor
		implements com.grey.base.utils.PrototypeFactory.PrototypeObject
	{
		public final ConcurrentListener lstnr;
		public Server(ConcurrentListener l) {super(l.dsptch); lstnr=l;}
		public boolean stopServer() {return false;}
		@Override
		protected com.grey.naf.SSLConfig getSSLConfig() {return lstnr.sslconfig;}
	}

	private final Server protoServer;  //prototype object used to create actual connection handlers
	private final Class<?> serverType;
	private final com.grey.base.utils.HashedSet<Server> activeservers = new com.grey.base.utils.HashedSet<Server>();
	private final com.grey.base.utils.ObjectWell<Server> spareservers;
	private boolean in_sync_stop;

	@Override
	public Class<?> getServerType() {return serverType;}

	public ConcurrentListener(String lname, Dispatcher d, Object controller, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, Class<?> srvclass, String iface, int port)
					throws com.grey.base.GreyException, java.io.IOException
	{
		this(lname, d, controller, rpr, cfg, makeDefaults(srvclass, iface, port));
	}

	public ConcurrentListener(String lname, Dispatcher d, Object controller, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		super(lname, d, controller, rpr, cfg, cfgdflts);

		// So far, we've only read attributes from the main config block. Check if this Listener's config is linked to another's, before
		// delving into the contents of its Server config block.
		if (cfg != null) {
			String linkname = cfg.getValue("@configlink", false, null);
			if (linkname != null) cfg = new com.grey.base.config.XmlConfig(cfg, "../listener[@name='"+linkname+"']");
		}
		Class<?> srvclss = (cfgdflts == null ? null : (Class<?>)cfgdflts.get(CFGMAP_CLASS));
		com.grey.base.config.XmlConfig servercfg = (cfg == null ? null : new com.grey.base.config.XmlConfig(cfg, "server"));
		Object s = dsptch.nafcfg.createEntity(servercfg, srvclss, Server.class, false,
				new Class<?>[]{ConcurrentListener.class, com.grey.base.config.XmlConfig.class},
				new Object[]{this, servercfg});
		protoServer = Server.class.cast(s);
		serverType = protoServer.getClass();

		int srvmin = getInt(cfgdflts, CFGMAP_INITSPAWN, 0);
		int srvmax = getInt(cfgdflts, CFGMAP_MAXSPAWN, 0);
		int srvincr = getInt(cfgdflts, CFGMAP_INCRSPAWN, 0);
		if (cfg != null) {
			srvmin = cfg.getInt("@initservers", false, srvmin);
			srvmax = cfg.getInt("@maxservers", false, srvmax);
			srvincr = cfg.getInt("@incrservers", false, srvincr);
		}
		com.grey.base.utils.PrototypeFactory fact = new com.grey.base.utils.PrototypeFactory(protoServer);
		spareservers = new com.grey.base.utils.ObjectWell<Server>(protoServer.getClass(), fact,
				"Listener_"+name+":"+getLocalPort()+"_Servers", srvmin, srvmax, srvincr);

		log.info("Listener="+name+": Server is "+protoServer.getClass().getName()
				+" - init/max/incr="+spareservers.size()+"/"+srvmax+"/"+srvincr);
	}

	@Override
	public boolean stopListener()
	{
		//cannot iterate on activeservers as it may get modified by stopServer(), so take a copy
		in_sync_stop = true;
		Server[] arr = activeservers.toArray(new Server[activeservers.size()]);
		for (int idx = 0; idx != arr.length; idx++) {
			Server srvr = arr[idx];
			boolean stopped = srvr.stopServer();
			if (stopped || !dsptch.isRunning()) deallocateServer(srvr);
		}
		log.info("Listener="+name+" stopped "+(arr.length - activeservers.size())+"/"+arr.length+" servers");
		in_sync_stop = false;
		return (activeservers.size() == 0);
	}

	@Override
	protected void listenerStopped()
	{
		protoServer.stopServer();
	}

	@Override
	public void entityStopped(Object obj)
	{
		boolean not_dup = deallocateServer(Server.class.cast(obj));
		if (not_dup && inShutdown && !in_sync_stop && activeservers.size() == 0) stopped(true);
	}

	// NB: Can't loop on SelectionKey.isAcceptable(), as it will remain True until we return to Dispatcher
	@Override
	protected void connectionReceived() throws java.io.IOException
	{
		java.nio.channels.ServerSocketChannel srvsock = (java.nio.channels.ServerSocketChannel)iochan;
		java.nio.channels.SocketChannel connsock;

		while ((connsock = srvsock.accept()) != null) {
			try {
				handleConnection(connsock);
			} catch (Throwable ex) {
				log.log(LEVEL.TRC, ex, true, "Listener="+name+": Error fielding connection");
				connsock.close();
			}
		}
	}

	private void handleConnection(java.nio.channels.SocketChannel connsock) throws com.grey.base.FaultException, java.io.IOException
	{
		ChannelMonitor srvr = spareservers.extract();
		if (srvr == null) {
			// we're at max capacity - can't allocate any more server objects
			log.info("Listener=" + name + " dropping connection - no spare servers");
			connsock.close();
			return;
		}
		boolean ok = false;
		activeservers.add((Server)srvr);

		try {
			srvr.accepted(connsock, this);
			ok = true;
		} finally {
			if (!ok) {
				dsptch.conditionalDeregisterIO(srvr);
				entityStopped(srvr);
			}
		}
	}

	private boolean deallocateServer(Server srvr)
	{
		//guard against duplicate entityStopped() notifications
		if (!activeservers.remove(srvr)) {
			return false;
		}
		spareservers.store(srvr);
		return true;
	}
}
