/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.base.config.XmlConfig;
import com.grey.naf.Naflet;
import com.grey.naf.EntityReaper;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.ListenerSet;
import com.grey.naf.nafman.NafManCommand;
import com.grey.naf.nafman.NafManRegistry;

public class Task
	extends Naflet
	implements NafManCommand.Handler, EntityReaper
{
	public static final String CMD_SHOWCONNS = "SHOWCONNS";

	private final java.util.ArrayList<Relay> active = new java.util.ArrayList<>();
	private final ListenerSet listeners;

	public Task(String name, Dispatcher dsptch, XmlConfig cfg) throws java.io.IOException
	{
		super(name, dsptch, cfg);
		listeners = new ListenerSet("Task="+getName(), dsptch, this, this, "listeners/listener", taskConfig(), null);
		if (dsptch.getAgent() != null) {
			NafManRegistry reg = dsptch.getAgent().getRegistry();
			reg.registerHandler(CMD_SHOWCONNS, 0, this, dsptch);
		}
	}

	@Override
	protected void startNaflet() throws java.io.IOException
	{
		getLogger().info("Loaded JARs: "+com.grey.base.utils.PkgInfo.getLoadedJARs());
		listeners.start();
	}

	@Override
	protected boolean stopNaflet()
	{
		return listeners.stop();
	}

	@Override
	public void entityStopped(Object obj)
	{
		ListenerSet.class.cast(obj); //can only be our ListenerSet, but do a cast to assert that
		nafletStopped();
	}

	@Override
	public CharSequence handleNAFManCommand(NafManCommand cmd)
	{
		StringBuilder sb = new StringBuilder(512);

		if (cmd.getCommandDef().code.equals(CMD_SHOWCONNS)) {
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
			getLogger().error("NAFMAN="+getDispatcher().getName()+": Missing case for cmd="+cmd.getCommandDef().code);
			return null;
		}
		return sb;
	}
	
	@Override
	public CharSequence nafmanHandlerID() {return getName();}

	public void connectionStarted(Relay relay)
	{
		active.add(relay);
	}

	public void connectionEnded(Relay relay)
	{
		active.remove(relay);
	}
}
