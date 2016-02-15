/*
 * Copyright 2010-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public abstract class CM_Listener
	extends ChannelMonitor
	implements com.grey.naf.EntityReaper
{
	public interface Reporter
	{
		enum EVENT {STARTED, STOPPED}
		void listenerNotification(EVENT evt, CM_Server s);
	}
	public static final String CFGMAP_IFACE = "interface";
	public static final String CFGMAP_PORT = "port";
	public static final String CFGMAP_SSLPORT = "sslport";
	public static final String CFGMAP_FACTCLASS = "factoryclass";
	public static final String CFGMAP_BACKLOG = "backlog";
	public static final String CFGMAP_INITSPAWN = "srvmin";
	public static final String CFGMAP_MAXSPAWN = "srvmax";
	public static final String CFGMAP_INCRSPAWN = "srvincr";

	private static final java.util.concurrent.ConcurrentHashMap<String, CM_Listener> idmap = new java.util.concurrent.ConcurrentHashMap<String, CM_Listener>();
	static String[] getNames() {return idmap.keySet().toArray(new String[idmap.size()]);} //deliberately package-private
	public static CM_Listener getByName(String id) {return idmap.get(id);}

	public final String name;
	public final Object controller;
	private final com.grey.naf.SSLConfig sslconfig;
	private final java.net.InetAddress srvip;
	private final int srvport;
	protected final com.grey.logging.Logger log;
	protected Reporter reporter;

	protected boolean inShutdown;
	private boolean has_stopped;

	public abstract Class<?> getServerType();
	public final int getPort() {return srvport;}
	public final java.net.InetAddress getIP() {return srvip;}
	public final void setReporter(Reporter r) {reporter = r;}

	protected void listenerStopped() {}
	protected boolean stopListener() {return true;}

	com.grey.naf.SSLConfig getSSLConfig() {return sslconfig;}

	public CM_Listener(String lname, Dispatcher d, Object controller, com.grey.naf.EntityReaper rpr,
			com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		super(d);
		log = dsptch.logger;
		this.controller = controller;
		setReaper(rpr);
		try {
			com.grey.base.config.XmlConfig sslcfg = (cfg == null ? null : cfg.getSection("ssl"));
			sslconfig = com.grey.naf.SSLConfig.create(sslcfg, dsptch.nafcfg, null, false);
		} catch (java.security.GeneralSecurityException ex) {
			throw new com.grey.base.FaultException(ex, "Failed to configure SSL - "+ex);
		}
		String iface = (cfgdflts==null ? null : String.class.cast(cfgdflts.get(CFGMAP_IFACE)));
		int port = getInt(cfgdflts, sslconfig == null || sslconfig.latent ? CFGMAP_PORT : CFGMAP_SSLPORT, 0);
		int srvbacklog = getInt(cfgdflts, CFGMAP_BACKLOG, 5000);

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
		java.net.InetAddress ipaddr = (iface == null ? null : com.grey.base.utils.IP.getHostByName(iface));
		java.nio.channels.ServerSocketChannel srvchan = java.nio.channels.ServerSocketChannel.open();
		java.net.ServerSocket srvsock = srvchan.socket();
		srvsock.bind(new java.net.InetSocketAddress(ipaddr, port), srvbacklog);
		srvip = srvsock.getInetAddress();
		srvport = srvsock.getLocalPort();  // if port had been zero, Listener will have bound to ephemeral port
		registerChannel(srvchan, true);

		CM_Listener dup = idmap.putIfAbsent(name, this);
		if (dup != null) {
			throw new com.grey.base.GreyException("Duplicate listeners with name="+name+" on ports "+dup.getPort()+" and "+getPort());
		}
		log.info("Listener="+name+" bound to "+srvsock.getInetAddress()+":"+srvport
				+(iface==null ? "" : " on interface="+iface)+"; Backlog="+srvbacklog);
		if (sslconfig != null) sslconfig.declare("Listener="+name+": ", dsptch.logger);
	}

	public final void start() throws java.io.IOException
	{
		log.info("Listener="+name+": Starting up");
		enableListen();
	}

	public final boolean stop()
	{
		return stop(false);
	}

	protected final boolean stop(boolean notify)
	{
		log.info("Listener="+name+": Received Stop request - in-shutdown="+inShutdown+", stopped="+has_stopped);
		if (inShutdown || has_stopped) return has_stopped; //break up possible mutually recursive calling chains
		inShutdown = true;
		disconnect(false, true); //don't want to notify our reaper till stopped()
		boolean done = stopListener();
		if (done) stopped(notify);
		return done;
	}

	protected final void stopped(boolean notify)
	{
		if (has_stopped) return;
		has_stopped = true;
		com.grey.naf.EntityReaper rpr = getReaper();
		log.info("Listener="+name+" has stopped with notify="+notify+" - reaper="+rpr);
		listenerStopped();
		idmap.remove(name);
		if (notify && rpr != null) rpr.entityStopped(this);
		setReaper(null);
	}

	protected static java.util.Map<String,Object> makeDefaults(Class<?> clss, String iface, int port)
	{
		java.util.Map<String,Object> dflts = new java.util.HashMap<String,Object>();
		if (clss != null) dflts.put(CFGMAP_FACTCLASS, clss);
		if (iface != null) dflts.put(CFGMAP_IFACE, iface);
		if (port != 0) dflts.put(CFGMAP_PORT, Integer.valueOf(port));
		return dflts;
	}

	// Integer can't be cast to null, so need this vetter method
	protected static int getInt(java.util.Map<String,Object> cfg, String key, int dflt)
	{
		Object obj = (cfg == null ? null : cfg.get(key));
		if (obj == null) return dflt;
		return Integer.class.cast(obj).intValue();
	}
}