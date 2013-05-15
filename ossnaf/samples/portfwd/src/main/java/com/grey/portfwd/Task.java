/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.naf.nafman.Registry;

public class Task
	extends com.grey.naf.Naflet
	implements com.grey.naf.nafman.Command.Handler, com.grey.naf.EntityReaper
{
	public static final String CMD_SHOWCONNS = "SHOWCONNS";

	private static final com.grey.naf.nafman.Registry.DefCommand[] nafman_cmds = new com.grey.naf.nafman.Registry.DefCommand[] {
		new com.grey.naf.nafman.Registry.DefCommand(CMD_SHOWCONNS, "Port-Forwarder", "Show connection details", Registry.RSRC_CMDSTATUS, true)
	};

	private final java.util.ArrayList<Relay> active = new java.util.ArrayList<Relay>();
	private final com.grey.naf.reactor.ListenerSet listeners;

	public static void main(String[] args) throws Exception
	{
		com.grey.base.utils.PkgInfo.announceJAR(Task.class, "portfwd", null);
		com.grey.naf.nafman.Registry.get().loadCommands(nafman_cmds);
		com.grey.naf.Launcher.main(args);
	}

	public Task(String name, com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException, java.io.IOException
	{
		super(name, dsptch, cfg);
		listeners = new com.grey.naf.reactor.ListenerSet("Task="+naflet_name, dsptch, this, this, "listeners/listener", appcfg, null);
		com.grey.naf.nafman.Registry.get().registerHandler(CMD_SHOWCONNS, 0, this, dsptch);
	}

	@Override
	protected void startNaflet() throws java.io.IOException
	{
		dsptch.logger.info("Loaded JARs: "+com.grey.base.utils.PkgInfo.getLoadedJARs());
		listeners.start();
	}

	@Override
	protected boolean stopNaflet()
	{
		return listeners.stop();
	}

	// the terminated entity can only be our ListenerSet, but do a cast to assert that
	@Override
	public void entityStopped(Object obj)
	{
		com.grey.naf.reactor.ListenerSet.class.cast(obj);
		nafletStopped();
	}

	@Override
	public CharSequence handleNAFManCommand(com.grey.naf.nafman.Command cmd)
	{
		StringBuilder sb = new StringBuilder(512);

		if (cmd.def.code.equals(CMD_SHOWCONNS)) {
			sb.append("Total Connections: ").append(active.size());
			if (active.size() != 0) sb.append("<ul>");
			for (int idx = 0; idx != active.size(); idx++) {
				sb.append("<li>");
				active.get(idx).dumpState(sb);
				sb.append("</li>");
			}
			if (active.size() != 0) sb.append("</ul>");
		} else {
			// we've obviously registered for this command, so we must be missing a Case label - clearly a bug
			dsptch.logger.error("NAFMAN="+dsptch.name+": Missing case for cmd="+cmd.def.code);
			return null;
		}
		return sb;
	}

	public void connectionStarted(Relay relay)
	{
		active.add(relay);
	}

	public void connectionEnded(Relay relay)
	{
		active.remove(relay);
	}
}
