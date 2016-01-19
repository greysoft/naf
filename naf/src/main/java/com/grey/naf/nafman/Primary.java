/*
 * Copyright 2010-2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.logging.Logger.LEVEL;
import com.grey.base.config.SysProps;

public final class Primary
	extends Agent
	implements com.grey.naf.reactor.Producer.Consumer<Object>
{
	private static final long shutdown_delay = SysProps.getTime("greynaf.nafman.shutdown.delay", 500);

	private final com.grey.naf.reactor.CM_Listener lstnr;
	private final java.util.ArrayList<Secondary> secondaries = new java.util.ArrayList<Secondary>();
	private final java.util.ArrayList<Command> activecmds = new java.util.ArrayList<Command>();
	private final com.grey.naf.reactor.Producer<Object> events;
	private final boolean surviveDownstream;

	//preallocated purely for efficiency
	private final java.util.ArrayList<Agent> tmpagents = new java.util.ArrayList<Agent>();
	private final java.util.ArrayList<Command> tmpcmds = new java.util.ArrayList<Command>();

	// Primary is implicitly a singleton - duplicate instances would fail when binding Listener port
	public static Primary get()
	{
		com.grey.naf.reactor.Dispatcher[] arr = com.grey.naf.reactor.Dispatcher.getDispatchers();
		for (int idx = 0; idx != arr.length; idx++) {
			if (arr[idx].nafman != null && arr[idx].nafman.isPrimary()) return (Primary)arr[idx].nafman;
		}
		return null;
	}

	@Override
	public boolean isPrimary() {return true;}
	@Override
	public Primary getPrimary() {return this;}
	@Override
	public int getPort() {return lstnr.getPort();}

	public Primary(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg, com.grey.naf.DispatcherDef def)
			throws com.grey.base.GreyException, java.io.IOException
	{
		super(d, cfg);
		surviveDownstream = def.surviveDownstream;
		events = new com.grey.naf.reactor.Producer<Object>(Object.class, dsptch, this);
		dsptch.logger.info("NAFMAN="+dsptch.name+": survive_downstream="+surviveDownstream);

		int lstnport = d.nafcfg.assignPort(com.grey.naf.Config.RSVPORT_NAFMAN);
		com.grey.base.config.XmlConfig lstncfg = cfg.getSection("listener");
		lstnr = new com.grey.naf.reactor.ConcurrentListener("NAFMAN-Primary", dsptch, this, null, lstncfg, Server.Factory.class, null, lstnport);

		// these commands are only fielded by the Primary NAFMAN agent
		Registry reg = Registry.get();
		reg.registerHandler(Registry.CMD_DLIST, 0, this, dsptch);
		reg.registerHandler(Registry.CMD_APPSTOP, 0, this, dsptch);
		reg.registerHandler(Registry.CMD_SHOWCMDS, 0, this, dsptch);
	}

	@Override
	public void start() throws com.grey.base.FaultException, java.io.IOException
	{
		if (dsptch.logger.isActive(LEVEL.TRC)) dsptch.logger.trace(Registry.get().dumpState(null, false));
		super.start();
		lstnr.start();
	}

	@Override
	public void stop()
	{
		dsptch.logger.info("NAFMAN Primary shutdown with Secondaries="+secondaries.size()+", Commands="+activecmds.size());
		in_shutdown = true;
		lstnr.stop();

		// Loop on a copy of the secondaries list, as the Dispatcher stop() command may reentrantly call
		// back into our producerIndication() method below and modify the list while we're looping on it.
		Secondary[] arr = secondaries.toArray(new Secondary[secondaries.size()]);
		for (int idx = 0; idx != arr.length; idx++) {
			arr[idx].dsptch.stop();
		}

		if (secondaries.size() != 0) {
			// Give the secondaries time to shut down.
			// This is not critical, but it allows them to shut down gracefully, without lots of ugly logging
			// errors as they fail to contact the terminated Producer of a vanished Primary.
			com.grey.naf.reactor.Timer.sleep(shutdown_delay);
		}
		events.shutdown(true);
		dsptch.logger.info("Primary shutdown completed - Secondaries="+secondaries.size()+", Commands="+activecmds.size());
	}

	void handleCommand(Command cmd) throws com.grey.base.FaultException, java.io.IOException
	{
		for (int idx = 0; idx != secondaries.size(); idx++) {
			cmd.attach(secondaries.get(idx));
		}
		cmd.attach(this);
		tmpagents.clear();
		cmd.getAttachedAgents(tmpagents); //synchronises on cmd, before we pass it to secondaries
		LEVEL lvl = LEVEL.TRC2;
		if (dsptch.logger.isActive(lvl)) {
			dsptch.logger.log(lvl, "NAFMAN Primary fielding command="+cmd.def.code+" for Agents="
						+tmpagents.size()+"/"+(secondaries.size()+1)+" - ActiveCmds="+activecmds.size());
		}
		activecmds.add(cmd);
		boolean completed = (tmpagents.size() == 0);
		boolean deadsecs = false;

		for (int idx = 0; idx != tmpagents.size(); idx++) {
			if (tmpagents.get(idx) == this) {
				commandReceived(cmd);
			} else {
				Secondary agent = (Secondary)tmpagents.get(idx);
				try {
					agent.requests.produce(cmd);
				} catch (java.io.IOException ex) {
					int status = cmd.detach(agent);
					if (status == Command.DETACH_NOT) continue;
					if (status == Command.DETACH_FINAL) completed = true;
					deadsecs = true; //assume the Secondary has died
					dsptch.logger.info("NAFMAN="+dsptch.name+" failed to forward cmd="+cmd.def.code+" to Secondary="
							+agent.dsptch.name+" - "+com.grey.base.GreyException.summary(ex));
				}
			}
		}

		if (completed) {
			commandCompleted(cmd);
		}

		if (deadsecs && !surviveDownstream) {
			stopDispatcher();
		}
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<Object> p) throws java.io.IOException, com.grey.base.FaultException
	{
		Object event;
		while ((event = events.consume()) != null) {
			Class<?> clss = event.getClass();
			if (clss == Secondary.class) {
				Secondary agent = (Secondary)event;
				dsptch.logger.info("NAFMAN="+dsptch.name+" received subscription from Secondary="+agent.dsptch.name);
				secondaries.add(agent);
			} else if (clss == String.class) {
				Secondary agent = getSecondary((String)event);
				if (agent == null) {
					//can't see how this could happen in reality
					dsptch.logger.warn("NAFMAN="+dsptch.name+" received unsubscription from unidentified Secondary="+event);
					continue;
				}
				dsptch.logger.info("NAFMAN="+dsptch.name+" received unsubscription from Secondary="+agent.dsptch.name);
				discardSecondary(agent);
				secondaries.remove(agent);
				if (!surviveDownstream) stopDispatcher();
			} else {
				Command cmd = (Command)event;
				activecmds.remove(cmd);
				cmd.completed();
			}
		}
	}

	void secondarySubscribed(Secondary agent) throws java.io.IOException
	{
		events.produce(agent);
	}

	void secondaryUnsubscribed(Secondary agent) throws java.io.IOException
	{
		events.produce(agent.dsptch.name);
	}

	void commandCompleted(Command cmd) throws java.io.IOException
	{
		events.produce(cmd);
	}

	// Prune a dead Secondary from any active Commands to which it is attached.
	// Loop on temp copy of activecmds list, as the original may get modified during the loop.
	private void discardSecondary(Secondary agent) throws java.io.IOException
	{
		tmpcmds.clear();
		tmpcmds.addAll(activecmds);
		for (int idx = 0; idx != tmpcmds.size(); idx++) {
			Command cmd = tmpcmds.get(idx);
			if (cmd.detach(agent) == Command.DETACH_FINAL) commandCompleted(cmd);
		}
	}

	private Secondary getSecondary(String name)
	{
		for (int idx = 0; idx != secondaries.size(); idx++) {
			if (secondaries.get(idx).dsptch.name.equals(name)) return secondaries.get(idx);
		}
		return null;
	}
}
