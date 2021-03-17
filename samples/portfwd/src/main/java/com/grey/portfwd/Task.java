/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.base.config.XmlConfig;
import com.grey.naf.Naflet;
import com.grey.naf.EntityReaper;
import com.grey.naf.NAFConfig;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.ListenerSet;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;
import com.grey.naf.nafman.NafManCommand;
import com.grey.naf.nafman.NafManRegistry;

public class Task
	extends Naflet
	implements NafManCommand.Handler, EntityReaper
{
	public static final String CMD_SHOWCONNS = "SHOWCONNS";

	private final java.util.ArrayList<Relay> active = new java.util.ArrayList<>();
	private final ListenerSet listeners;

	@Override
	public CharSequence nafmanHandlerID() {return getName();}

	public Task(String name, Dispatcher dsptch, XmlConfig cfg) throws java.io.IOException {
		super(name, dsptch, cfg);
		String lname = "Task="+getName();
		NAFConfig nafcfg = dsptch.getApplicationContext().getConfig();
		ConcurrentListenerConfig[] lcfg = ConcurrentListenerConfig.buildMultiConfig(lname, nafcfg, "listeners/listener", taskConfig(), 0, 0, null, null);
		listeners = new ListenerSet(lname, dsptch, this, this, lcfg);
		if (dsptch.getNafManAgent() != null) {
			NafManRegistry reg = dsptch.getNafManAgent().getRegistry();
			reg.registerHandler(CMD_SHOWCONNS, 0, this, dsptch);
		}
	}

	@Override
	protected void startNaflet() throws java.io.IOException {
		getDispatcher().getLogger().info("Loaded JARs: "+com.grey.base.utils.PkgInfo.getLoadedJARs());
		listeners.start(true);
	}

	@Override
	protected boolean stopNaflet() {
		return listeners.stop(true);
	}

	@Override
	public void entityStopped(Object obj) {
		ListenerSet.class.cast(obj); //can only be our ListenerSet, but do a cast to assert that
		nafletStopped();
	}

	@Override
	public CharSequence handleNAFManCommand(NafManCommand cmd) {
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
			getDispatcher().getLogger().error("NAFMAN="+getDispatcher().getName()+": Missing case for cmd="+cmd.getCommandDef().code);
			return null;
		}
		return sb;
	}

	public void connectionStarted(Relay relay) {
		active.add(relay);
	}

	public void connectionEnded(Relay relay) {
		active.remove(relay);
	}
}
