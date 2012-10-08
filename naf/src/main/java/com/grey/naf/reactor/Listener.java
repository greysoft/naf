/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public abstract class Listener
	extends ChannelMonitor
	implements com.grey.naf.EntityReaper
{
	public static final String CFGMAP_IFACE = "interface";
	public static final String CFGMAP_PORT = "port";
	public static final String CFGMAP_CLASS = "serverclass";
	public static final String CFGMAP_BACKLOG = "backlog";
	public static final String CFGMAP_INITSPAWN = "srvmin";
	public static final String CFGMAP_MAXSPAWN = "srvmax";
	public static final String CFGMAP_INCRSPAWN = "srvincr";

	public final String name;
	public final int srvport;
	public final Object controller;

	protected final com.grey.logging.Logger log;
	protected boolean inShutdown;

	private com.grey.naf.EntityReaper reaper;

	public int getPort() {return srvport;}
	public abstract Class<?> getServerType();

	protected void listenerStopped() {}
	protected boolean stopListener() {return true;}
	protected abstract void connectionReceived() throws java.io.IOException;

	public Listener(String lname, com.grey.naf.reactor.Dispatcher d, Object controller,
			com.grey.base.config.XmlConfig cfg, java.util.Map<String,Object> cfgdflts)
					throws com.grey.base.GreyException, java.io.IOException
	{
		super(d);
		this.controller = controller;
		log = dsptch.logger;
		String iface = (cfgdflts==null ? null : String.class.cast(cfgdflts.get(CFGMAP_IFACE)));
		int port = getInt(cfgdflts, CFGMAP_PORT, 0);
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
		java.net.InetAddress ipaddr = null;
		if (iface != null) ipaddr = com.grey.base.utils.IP.getHostByName(iface);
		java.nio.channels.ServerSocketChannel srvchan = java.nio.channels.ServerSocketChannel.open();
		java.net.ServerSocket srvsock = srvchan.socket();
		srvsock.bind(new java.net.InetSocketAddress(ipaddr, port), srvbacklog);
		srvport = srvsock.getLocalPort();  // if port had been zero, Listener will have bound to ephemeral port
		this.initChannel(srvchan, true, false);

		log.info("Listener="+name+" bound to "+srvsock.getInetAddress()+":"+srvport
				+(iface==null ? "" : " on interface="+iface)+"; Backlog="+srvbacklog);
	}

	public void start(com.grey.naf.EntityReaper rpr) throws java.io.IOException
	{
		log.info("Listener="+name+": Starting up");
		reaper = rpr;
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
		if (done) stopped(notify);
		return done;
	}

	protected void stopped(boolean notify)
	{
		log.trace("Listener="+name+" has stopped - notify="+notify);
		listenerStopped();
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
