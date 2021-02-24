/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.XmlConfig;
import com.grey.naf.EntityReaper;
import com.grey.naf.SSLConfig;
import com.grey.naf.errors.NAFConfigException;

public abstract class CM_Listener
	extends ChannelMonitor
	implements EntityReaper
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

	public final String name;
	private final Object controller;
	private final SSLConfig sslconfig;
	private final java.net.InetAddress srvip;
	private final int srvport;

	private Reporter reporter;
	private boolean inShutdown;
	private boolean has_stopped;

	public abstract Class<?> getServerType();
	public int getPort() {return srvport;}
	public java.net.InetAddress getIP() {return srvip;}
	public Object getController() {return controller;}
	protected Reporter getReporter() {return reporter;}
	void setReporter(Reporter r) {reporter = r;}

	protected void listenerStopped() {}
	protected boolean stopListener() {return true;}
	protected boolean inShutdown() {return inShutdown;}

	SSLConfig getSSLConfig() {return sslconfig;}

	public CM_Listener(String lname, Dispatcher d, Object controller, EntityReaper rpr, XmlConfig cfg,
			java.util.Map<String,Object> cfgdflts) throws java.io.IOException
	{
		super(d);
		this.controller = controller;
		setReaper(rpr);
		try {
			XmlConfig sslcfg = (cfg == null ? null : cfg.getSection("ssl"));
			sslconfig = SSLConfig.create(sslcfg, getDispatcher().getApplicationContext().getConfig(), null, false);
		} catch (java.security.GeneralSecurityException ex) {
			throw new NAFConfigException("Failed to configure SSL", ex);
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
		if (lname == null) lname = getDispatcher().getName()+":"+port;
		name = lname;
		getLogger().trace("Dispatcher="+getDispatcher().getName()+" - Listener="+name+": Initialising on interface="+iface+", port="+port);

		// set up our listening socket
		java.net.InetAddress ipaddr = (iface == null ? null : com.grey.base.utils.IP.getHostByName(iface));
		java.nio.channels.ServerSocketChannel srvchan = java.nio.channels.ServerSocketChannel.open();
		java.net.ServerSocket srvsock = srvchan.socket();
		srvsock.bind(new java.net.InetSocketAddress(ipaddr, port), srvbacklog);
		srvip = srvsock.getInetAddress();
		srvport = srvsock.getLocalPort();
		registerChannel(srvchan, true);

		getLogger().info("Listener="+name+" bound to "+srvsock.getInetAddress()+":"+srvport+(port==0?"/dynamic":"")
				         +(iface==null ? "" : " on interface="+iface)+"; Backlog="+srvbacklog);
		if (sslconfig != null) sslconfig.declare("Listener="+name+": ", getLogger());
	}

	public void start() throws java.io.IOException
	{
		getLogger().info("Listener="+name+": Starting up");
		enableListen();
	}

	public boolean stop()
	{
		return stop(false);
	}

	protected boolean stop(boolean notify)
	{
		getLogger().info("Listener="+name+": Received Stop request - in-shutdown="+inShutdown+", stopped="+has_stopped);
		if (inShutdown || has_stopped) return has_stopped; //break up possible mutually recursive calling chains
		inShutdown = true;
		disconnect(false, true); //don't want to notify our reaper till stopped()
		boolean done = stopListener();
		if (done) stopped(notify);
		return done;
	}

	protected void stopped(boolean notify)
	{
		if (has_stopped) return;
		has_stopped = true;
		EntityReaper rpr = getReaper();
		getLogger().info("Listener="+name+" has stopped with notify="+notify+" - reaper="+rpr);
		listenerStopped();
		getDispatcher().getApplicationContext().deregister(this);
		if (notify && rpr != null) rpr.entityStopped(this);
		setReaper(null);
	}

	protected static java.util.Map<String,Object> makeDefaults(Class<?> clss, String iface, int port)
	{
		java.util.Map<String,Object> dflts = new java.util.HashMap<>();
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

	@Override
	public String toString() {
		return getClass().getName()+"="+name+" for "+getServerType().getName()+" with controller="+controller
				+" on "+getIP()+":"+getPort()+" - ssl="+getSSLConfig();
	}
}