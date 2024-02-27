/*
 * Copyright 2012-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.base.config.XmlConfig;
import com.grey.naf.Naflet;
import com.grey.naf.EventListenerNAF;
import com.grey.naf.NAFConfig;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.ListenerSet;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;
import com.grey.naf.nafman.NafManCommand;
import com.grey.naf.nafman.NafManRegistry;

public class Task
	extends Naflet
	implements NafManCommand.Handler, EventListenerNAF
{
	private static final String NAFMAN_FAMILY = "Port-Forwarder";
	public static final String CMD_SHOWCONNS = "SHOWCONNS";

	private static final NafManRegistry.DefCommand[] nafman_cmds = new NafManRegistry.DefCommand[] {
			new NafManRegistry.DefCommand(CMD_SHOWCONNS, NAFMAN_FAMILY, "Show connection details", NafManRegistry.RSRC_CMDSTATUS, true)
	};

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
			reg.registerCommandFamily(NAFMAN_FAMILY, nafman_cmds, null, null);
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
	public void eventIndication(Object obj, String eventId) {
		if (!(obj instanceof ListenerSet) || !EventListenerNAF.EVENTID_ENTITY_STOPPED.equals(eventId)) {
			getDispatcher().getLogger().info("Discarding unexpected event="+obj.getClass().getName()+"/"+eventId);
			return;
		}
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
