/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.naf.nafman.Command;

public class Task
	extends com.grey.naf.Naflet
	implements com.grey.naf.nafman.Registry.CommandHandler, com.grey.naf.EntityReaper
{
	private static final int CMD_BASE = com.grey.naf.nafman.Registry.CMD_NAFRESERVED;
	public static final int CMD_SHOWCONNS = CMD_BASE + 1;

	private static final Command.Def[] nafman_cmds = new Command.Def[] {
		new Command.Def(CMD_SHOWCONNS, "showconns", 0, 0, false, null),
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
		com.grey.naf.nafman.Registry.get().registerHandler(CMD_SHOWCONNS, this, dsptch);
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
	public void handleNAFManCommand(Command cmd)
	{
		StringBuilder sb = new StringBuilder(512);

		switch (cmd.def.code)
		{
		case CMD_SHOWCONNS:
			sb.append("Connections = ").append(active.size());
			if (active.size() != 0) sb.append(':');
			for (int idx = 0; idx != active.size(); idx++) {
				sb.append("\n- ").append(idx+1).append(": ");
				active.get(idx).dumpState(sb);
			}
			break;
		default:
			// we've obviously registered for this command, so we must be missing a Case label - clearly a bug
			dsptch.logger.error("NAFMAN="+dsptch.name+": Missing case for cmd="+cmd.def.code);
			return;
		}
		cmd.sendResponse(dsptch, sb);
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
