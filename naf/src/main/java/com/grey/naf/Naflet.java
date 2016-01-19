/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

/**
 * This class defines a NAF application, aka a NAF task. It aggregates one or more fragments of callback code
 * into a coherent whole.
 * <p>
 * In addition to overriding the explicit methods of this interface, subclasses must also provide a constructor
 * with this signature:<br>
 * <code>classname(String naflet_name, com.grey.naf.reactor.Dispatcher, com.grey.base.config.XmlConfig)</code><br>
 * That subclass constructor must in turn call the
 * <code>Naflet(String naflet_name, com.grey.naf.reactor.Dispatcher, com.grey.base.config.XmlConfig)</code>
 * constructor shown below.
 */
abstract public class Naflet
{
	public final String naflet_name;
	public final java.util.Map<String,Object> cfgdflts = new java.util.HashMap<String,Object>();
	public final com.grey.naf.reactor.Dispatcher dsptch;
	protected final String cfgfile;
	private com.grey.base.config.XmlConfig appcfg;
	private com.grey.naf.EntityReaper reaper;
	private boolean aborted_startup;

	abstract protected void startNaflet() throws java.io.IOException;
	protected boolean stopNaflet() {return true;}
	protected void abortOnStartup() {aborted_startup = true;}
	public com.grey.base.config.XmlConfig taskConfig() {return appcfg;}

	public Naflet(String name, com.grey.naf.reactor.Dispatcher dsptch_p, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException, java.io.IOException
	{
		naflet_name = name;
		dsptch = dsptch_p;
		cfgfile = dsptch.nafcfg.getPath(cfg, "configfile", null, false, null, null);
		dsptch.logger.info("Naflet="+naflet_name+": Initialising "+getClass().getName()+" - config="+cfgfile);

		if (cfgfile != null) {
			String cfgroot = cfg.getValue("configfile/@root", false, null);
			appcfg = com.grey.base.config.XmlConfig.getSection(cfgfile, cfgroot);
		} else {
			appcfg = cfg;
		}
	}

	public final void start(com.grey.naf.EntityReaper rpr) throws java.io.IOException
	{
		appcfg = null; //hand memory back to the GC
		reaper = rpr;
		if (aborted_startup) {
			dsptch.logger.info("Naflet="+naflet_name+": Aborting startup");
			nafletStopped();
			return;
		}
		dsptch.logger.info("Naflet="+naflet_name+": Starting - reaper="+reaper);
		startNaflet();
	}

	public final boolean stop()
	{
		dsptch.logger.info("Naflet="+naflet_name+": Received Stop request");
		boolean done = stopNaflet();
		if (done) nafletStopped();
		return done;
	}
	
	protected final void nafletStopped()
	{
		dsptch.logger.info("Naflet="+naflet_name+" has terminated - reaper="+reaper);
		if (reaper != null) reaper.entityStopped(this);
	}
}
