/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public abstract class Listener
	extends ChannelMonitor
	implements com.grey.naf.EntityReaper
{
	public static final String CFGMAP_IFACE = "interface";
	public static final String CFGMAP_PORT = "port";
	public static final String CFGMAP_SSLPORT = "sslport";
	public static final String CFGMAP_CLASS = "serverclass";
	public static final String CFGMAP_BACKLOG = "backlog";
	public static final String CFGMAP_INITSPAWN = "srvmin";
	public static final String CFGMAP_MAXSPAWN = "srvmax";
	public static final String CFGMAP_INCRSPAWN = "srvincr";

	private static final java.util.concurrent.ConcurrentHashMap<String, Listener> idmap = new java.util.concurrent.ConcurrentHashMap<String, Listener>();
	static final String[] getNames() {return idmap.keySet().toArray(new String[idmap.size()]);} //deliberately package-private
	public static final Listener getByName(String id) {return idmap.get(id);}

	public final String name;
	public final Object controller;
	public final com.grey.naf.SSLConfig sslconfig;
	private final java.net.InetAddress srvip;
	private final int srvport;
	private final com.grey.naf.EntityReaper reaper;
	protected final com.grey.logging.Logger log;

	protected boolean inShutdown;
	private boolean has_stopped;

	public abstract Class<?> getServerType();
	@Override
	public final int getLocalPort() {return srvport;}
	@Override
	public java.net.InetAddress getLocalIP() {return srvip;}

	protected void listenerStopped() {}
	protected boolean stopListener() {return true;}
	protected abstract void connectionReceived() throws java.io.IOException;

	@Override
	protected com.grey.naf.SSLConfig getSSLConfig() {return sslconfig;}

	public Listener(String lname, Dispatcher d, Object controller, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		super(d);
		this.controller = controller;
		reaper = rpr;
		log = dsptch.logger;
		try {
			com.grey.base.config.XmlConfig sslcfg = (cfg == null ? null : new com.grey.base.config.XmlConfig(cfg, "ssl"));
			sslconfig = com.grey.naf.SSLConfig.create(sslcfg, dsptch.nafcfg, null, false);
		} catch (java.security.GeneralSecurityException ex) {
			throw new com.grey.base.FaultException(ex, "Failed to configure SSL - "+ex);
		}
		String iface = (cfgdflts==null ? null : String.class.cast(cfgdflts.get(CFGMAP_IFACE)));
		int port = getInt(cfgdflts, sslconfig == null || sslconfig.latent ? CFGMAP_PORT : CFGMAP_SSLPORT, 0);
		int srvbacklog = getInt(cfgdflts, CFGMAP_BACKLOG, 1000);

		if (cfg != null) {
			iface = cfg.getValue("@interface", false, iface);
			port = cfg.getInt("@port", false, port);
			srvbacklog = cfg.getInt("@backlog", false, srvbacklog);
			lname = cfg.getValue("@name", false, lname);
		}
		if (lname == null) lname = dsptch.name+":"+port;
		name = lname;
		log.trace("Dispatcher="+dsptch.name+" - Listener="+name+": Initialising on interface="+iface+", port="+port);

		// set up our listening socket
		srvip = (iface == null ? null : com.grey.base.utils.IP.getHostByName(iface));
		java.nio.channels.ServerSocketChannel srvchan = java.nio.channels.ServerSocketChannel.open();
		java.net.ServerSocket srvsock = srvchan.socket();
		srvsock.bind(new java.net.InetSocketAddress(srvip, port), srvbacklog);
		srvport = srvsock.getLocalPort();  // if port had been zero, Listener will have bound to ephemeral port
		initChannel(srvchan, true, false);

		Listener dup = idmap.putIfAbsent(name, this);
		if (dup != null) {
			throw new com.grey.base.GreyException("Duplicate listeners with name="+name+" on ports "+dup.getLocalPort()+" and "+getLocalPort());
		}
		log.info("Listener="+name+" bound to "+srvsock.getInetAddress()+":"+srvport
				+(iface==null ? "" : " on interface="+iface)+"; Backlog="+srvbacklog);
		if (sslconfig != null) sslconfig.declare("Listener="+name+": ", dsptch.logger);
	}

	public void start() throws java.io.IOException
	{
		log.info("Listener="+name+": Starting up");
		enableListen();
	}

	public boolean stop()
	{
		log.info("Listener="+name+": Received Stop request - in-shutdown="+inShutdown);
		return stop(false);
	}

	protected boolean stop(boolean notify)
	{
		if (inShutdown) return false;  // break up possible mutually recursive calling chains
		inShutdown = true;
		disconnect();
		boolean done = stopListener();
		idmap.remove(name);
		if (done) stopped(notify);
		return done;
	}

	protected void stopped(boolean notify)
	{
		log.info("Listener="+name+" has stopped with notify="+notify+"/dup="+has_stopped+" - reaper="+reaper);
		if (has_stopped) return;
		listenerStopped();
		has_stopped = true;
		if (notify && reaper != null) reaper.entityStopped(this);
	}

	// We know that the readyOps argument must indicate an Accept (that's all we registered for), so don't bother checking it.
	@Override
	protected void ioIndication(int readyOps) throws java.io.IOException
	{
		connectionReceived();
	}

	protected static java.util.Map<String,Object> makeDefaults(Class<?> clss, String iface, int port)
	{
		java.util.Map<String,Object> dflts = new java.util.HashMap<String,Object>();
		if (clss != null) dflts.put(CFGMAP_CLASS, clss);
		if (iface != null) dflts.put(CFGMAP_IFACE, iface);
		if (port != 0) dflts.put(CFGMAP_PORT, Integer.valueOf(port));
		return dflts;
	}

	// Integer can't be cast to null, so need this vetter method
	protected static Integer getInt(java.util.Map<String,Object> cfg, String key, int dflt)
	{
		Object obj = (cfg == null ? null : cfg.get(key));
		if (obj == null) return dflt;
		return Integer.class.cast(obj);
	}
}
