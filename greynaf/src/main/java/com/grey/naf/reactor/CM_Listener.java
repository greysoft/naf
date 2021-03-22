/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.naf.EntityReaper;
import com.grey.naf.SSLConfig;
import com.grey.naf.reactor.config.ListenerConfig;

public abstract class CM_Listener
	extends ChannelMonitor
	implements DispatcherRunnable, EntityReaper
{
	public interface Reporter
	{
		enum EVENT {STARTED, STOPPED}
		void listenerNotification(EVENT evt, CM_Server s);
	}

	private final String name;
	private final Object controller;
	private final SSLConfig sslconfig;
	private final java.net.InetAddress srvip;
	private final int srvport;

	private Reporter reporter;
	private boolean inShutdown;
	private boolean has_stopped;

	public abstract Class<?> getServerType();

	@Override
	public String getName() {return name;}
	public int getPort() {return srvport;}
	public java.net.InetAddress getIP() {return srvip;}
	public Object getController() {return controller;}
	protected Reporter getReporter() {return reporter;}
	protected void setReporter(Reporter r) {reporter = r;}

	protected void listenerStopped() {}
	protected boolean stopListener() {return true;}
	protected boolean inShutdown() {return inShutdown;}

	SSLConfig getSSLConfig() {return sslconfig;}

	protected CM_Listener(Dispatcher d, Object controller, EntityReaper rpr, ListenerConfig config) throws java.io.IOException {
		super(d);
		this.controller = controller;
		setReaper(rpr);
		sslconfig = config.getConfigSSL();
		String iface = config.getInterface();
		int port = config.getPort();
		int srvbacklog = config.getBacklog();
		String lname = config.getName();
		if (lname == null) lname = getDispatcher().getName()+":"+port;
		name = lname;
		getLogger().info("Listener="+name+" in Dispatcher="+getDispatcher().getName()+" initialising on interface="+iface+", port="+port
				+" with controller="+controller+", reaper="+getReaper()+" - ssl="+sslconfig);

		// set up our listening socket
		java.net.InetAddress ipaddr = (iface == null ? null : com.grey.base.utils.IP.getHostByName(iface));
		java.nio.channels.ServerSocketChannel srvchan = java.nio.channels.ServerSocketChannel.open();
		java.net.ServerSocket srvsock = srvchan.socket();
		srvsock.bind(new java.net.InetSocketAddress(ipaddr, port), srvbacklog);
		srvip = srvsock.getInetAddress();
		srvport = srvsock.getLocalPort();

		getDispatcher().getApplicationContext().register(this);
		initChannel(srvchan, true);

		getLogger().info("Listener="+name+" bound to "+srvsock.getInetAddress()+":"+srvport+(port==0?"/dynamic":"")
				         +(iface==null ? "" : " on interface="+iface)+"; Backlog="+srvbacklog);
	}

	@Override
	public void startDispatcherRunnable() throws java.io.IOException {
		getLogger().info("Listener="+getName()+": Starting up");
		registerChannel();
		enableListen();
	}

	@Override
	public boolean stopDispatcherRunnable() {
		return stop(false);
	}

	protected boolean stop(boolean notify) {
		getLogger().info("Listener="+getName()+": Received Stop request with notify="+notify+" - in-shutdown="+inShutdown+", stopped="+has_stopped);
		if (inShutdown || has_stopped) return has_stopped; //break up possible mutually recursive calling chains
		inShutdown = true;
		disconnect(false, true); //don't want to notify our reaper till stopped()
		boolean done = stopListener();
		if (done) stopped(notify);
		return done;
	}

	protected void stopped(boolean notify) {
		if (has_stopped) return;
		has_stopped = true;
		EntityReaper rpr = getReaper();
		getLogger().info("Listener="+getName()+" has stopped with notify="+notify+" - reaper="+rpr);
		listenerStopped();
		getDispatcher().getApplicationContext().deregister(this);
		if (notify && rpr != null) rpr.entityStopped(this);
		setReaper(null);
	}

	@Override
	public String toString() {
		Class<?> srvclass = getServerType();
		return super.toString()+" - name="+getName()+" with server="+(srvclass==null?null:srvclass.getName())+", controller="+getController()
				+" on "+getIP()+":"+getPort()+" - ssl="+getSSLConfig();
	}
}