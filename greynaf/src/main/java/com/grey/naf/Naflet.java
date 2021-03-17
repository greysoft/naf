/*
 * Copyright 2010-2019 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.XmlConfig;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.DispatcherRunnable;

/**
 * This class defines a NAF application, aka a NAF task. It aggregates one or more fragments of callback code
 * into a coherent whole.
 * <p>
 * In addition to overriding the explicit methods of this interface, subclasses that want to be automatically
 * created from the naf.xml config must also provide a constructor with this signature:<br>
 * <code>classname(String naflet_name, com.grey.naf.reactor.Dispatcher, com.grey.base.config.XmlConfig)</code><br>
 * That subclass constructor must in turn call the Naflet constructor of the same signature (see below).
 */
abstract public class Naflet implements DispatcherRunnable
{
	private final String naflet_name;
	private final Dispatcher dsptch;
	private final XmlConfig taskcfg;
	private final String cfgfile;

	private volatile boolean aborted_startup;

	// applications can override these
	protected void startNaflet() throws java.io.IOException {}
	protected boolean stopNaflet() {return true;}
	protected void abortOnStartup() {aborted_startup = true;}

	public XmlConfig taskConfig() {return taskcfg;}
	public String taskConfigFile() {return cfgfile;}
	@Override
	public String getName() {return naflet_name;}
	@Override
	public Dispatcher getDispatcher() {return dsptch;}

	/**
	 * Applications that are not based on naf.xml style config would pass a null XmlConfig arg in here
	 */
	protected Naflet(String name, Dispatcher d, XmlConfig cfg) throws java.io.IOException {
		naflet_name = name;
		dsptch = d;
		NAFConfig nafcfg = dsptch.getApplicationContext().getConfig();
		cfgfile = nafcfg.getPath(cfg, "configfile", null, false, null, null);
		getDispatcher().getLogger().info("Naflet="+naflet_name+": Initialising "+getClass().getName()+" in Dispatcher="+d.getName()+" - config="+cfgfile);

		if (cfgfile != null) {
			if (cfgfile.endsWith(".xml")) {
				String cfgroot = nafcfg.get(cfg, "configfile/@root", null, true, null);
				taskcfg = XmlConfig.getSection(cfgfile, cfgroot);
			} else {
				taskcfg = null; //application will have to use taskConfigFile() instead
			}
		} else {
			taskcfg = cfg;
		}
	}

	@Override
	public void startDispatcherRunnable() throws java.io.IOException {
		boolean abort = aborted_startup;
		getDispatcher().getLogger().info("Naflet="+naflet_name+" in Dispatcher="+dsptch.getName()+": Starting - abort="+abort);
		if (abort) {
			nafletStopped();
			return;
		}
		startNaflet();
	}

	@Override
	public boolean stopDispatcherRunnable() {
		getDispatcher().getLogger().info("Naflet="+naflet_name+" in Dispatcher="+dsptch.getName()+": Stopping");
		boolean done = stopNaflet();
		if (done) nafletStopped();
		return done;
	}

	protected void nafletStopped() {
		getDispatcher().getLogger().info("Naflet="+naflet_name+" in Dispatcher="+dsptch.getName()+" has terminated");
		dsptch.entityStopped(this);
	}

	@Override
	public String toString() {
		return super.toString()+"/Naflet="+getName()+" in Dispatcher="+getDispatcher().getName();
	}
}
