/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.config.SysProps;
import com.grey.naf.reactor.Dispatcher;

public final class Primary
	extends Agent
{
	private static final long shutdown_delay = SysProps.getTime("greynaf.nafman.shutdown.delay", 500);

	private final com.grey.naf.reactor.Listener lstnr;
	private final java.util.ArrayList<Secondary> secondaries = new java.util.ArrayList<Secondary>();
	private final java.util.ArrayList<Command> activecmds = new java.util.ArrayList<Command>();
	private final com.grey.naf.reactor.Producer<Object> events;
	protected final com.grey.naf.BufferSpec bufspec;
	protected final com.grey.base.utils.ObjectWell<com.grey.base.utils.ByteChars> bcstore
				= new com.grey.base.utils.ObjectWell<com.grey.base.utils.ByteChars>(com.grey.base.utils.ByteChars.class);
	protected final com.grey.base.utils.ObjectWell<Command> cmdstore;

	private final java.util.ArrayList<Agent> tmpagents = new java.util.ArrayList<Agent>();
	private final java.util.ArrayList<Command> tmpcmds = new java.util.ArrayList<Command>();

	// Primary is implicitly a singleton - duplicate instances would fail when binding Listener port
	public static Primary get()
	{
		Dispatcher[] arr = Dispatcher.getDispatchers();
		for (int idx = 0; idx != arr.length; idx++) {
			if (arr[idx].nafman != null && arr[idx].nafman.isPrimary()) return Primary.class.cast(arr[idx].nafman);
		}
		return null;
	}

	@Override
	public boolean isPrimary() {return true;}
	@Override
	public int getPort() {return lstnr.getPort();}

	public Primary(Dispatcher d, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.GreyException, java.io.IOException
	{
		super(d, cfg);
		bufspec = new com.grey.naf.BufferSpec(cfg, "niobuffers", 128, 4096, true);

		events = new com.grey.naf.reactor.Producer<Object>(Object.class, dsptch, this);

		Command.Factory fact = new Command.Factory(this);
		cmdstore = new com.grey.base.utils.ObjectWell<Command>(fact);

		int lstnport = d.nafcfg.assignPort(com.grey.naf.Config.RSVPORT_NAFMAN);
		com.grey.base.config.XmlConfig lstncfg = new com.grey.base.config.XmlConfig(cfg, "listener");
		lstnr = new com.grey.naf.reactor.ConcurrentListener("NAFMAN-"+dsptch.name, dsptch, this, lstncfg, Server.class, null, lstnport);

		// these commands are only fielded by the Primary NAFMAN agent
		registerHandler(Registry.CMD_DLIST, this);
		registerHandler(Registry.CMD_APPSTOP, this);
	}

	@Override
	public void start() throws com.grey.base.FaultException, java.io.IOException
	{
		lstnr.start(null);
	}

	@Override
	public void stop()
	{
		dsptch.logger.info("NAFMAN Primary shutdown with Secondaries="+secondaries.size()+", Commands="+activecmds.size());
		in_shutdown = true;
		lstnr.stop();

		// No danger of list changing in mid-iteration - same reason as processCommand() comments below
		for (int idx = 0; idx != secondaries.size(); idx++) {
			try {
				secondaries.get(idx).dsptch.stop(dsptch);
			} catch (java.io.IOException ex) {
				dsptch.logger.info("NAFMAN="+dsptch.name+" failed to issue shutdown on Dispatcher="
						+secondaries.get(idx).dsptch.name+" - "+com.grey.base.GreyException.summary(ex));
			}
		}

		if (secondaries.size() != 0) {
			// Give the secondaries time to shut down.
			// This is not critical, but it allows them to shut down gracefully, without lots of ugly logging
			// errors as they fail to contact the terminated Producer of a vanished Primary.
			try {Thread.sleep(shutdown_delay);} catch (Exception ex) {}
		}
		events.shutdown(true);
		dsptch.logger.info("Primary shutdown completed - Secondaries="+secondaries.size()+", Commands="+activecmds.size());
	}

	protected void forwardCommand(Command cmd) throws com.grey.base.FaultException, java.io.IOException
	{
		for (int idx = 0; idx != secondaries.size(); idx++) {
			cmd.attach(secondaries.get(idx));
		}
		cmd.attach(this);
		tmpagents.clear();

		//NB: This synchronises on cmd, before we pass it to secondaries
		cmd.getAttachedAgents(tmpagents);
		dsptch.logger.info("NAFMAN Primary fielding command="+cmd.getDescription()+" for Agents="+tmpagents.size()+"/"+(secondaries.size()+1)
				+" - ActiveCmds="+activecmds.size());
		activecmds.add(cmd);
		boolean completed = (tmpagents.size() == 0);
		boolean deadsecs = false;

		for (int idx = 0; idx != tmpagents.size(); idx++) {
			if (tmpagents.get(idx) == this) {
				commandReceived(cmd);
			} else {
				Secondary agent = Secondary.class.cast(tmpagents.get(idx));
				try {
					agent.requests.produce(cmd, dsptch);
				} catch (java.io.IOException ex) {
					int status = cmd.detach(agent);
					if (status == -1) continue;
					if (status == 1) completed = true;
					deadsecs = true; //assume the Secondary has died
					dsptch.logger.info("NAFMAN="+dsptch.name+" failed to forward cmd="+cmd.def.name+" to Secondary="
							+agent.dsptch.name+" - "+com.grey.base.GreyException.summary(ex));
				}
			}
		}

		if (completed) {
			cmd.completed(dsptch);
		}

		if (deadsecs && !dsptch.surviveDownstream) {
			stopDispatcher();
		}
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<?> p) throws java.io.IOException
	{
		Object event;
		while ((event = events.consume()) != null) {
			if (event instanceof Secondary) {
				Secondary agent = Secondary.class.cast(event);
				dsptch.logger.info("NAFMAN="+dsptch.name+" received subscription from Secondary="+agent.dsptch.name);
				secondaries.add(agent);
			} else if (event instanceof String) {
				Secondary agent = getSecondary(String.class.cast(event));
				dsptch.logger.info("NAFMAN="+dsptch.name+" received unsubscription from Secondary="+agent.dsptch.name);
				discardSecondary(agent);
				secondaries.remove(agent);
				if (!dsptch.surviveDownstream) stopDispatcher();
			} else if (event instanceof Command) {
				Command cmd = Command.class.cast(event);
				activecmds.remove(cmd);
				cmd.srvr.sendResponse();
			}
		}
	}

	protected void secondarySubscribed(Secondary agent) throws java.io.IOException
	{
		events.produce(agent, agent.dsptch);
	}

	protected void secondaryUnsubscribed(Secondary agent) throws java.io.IOException
	{
		events.produce(agent.dsptch.name, agent.dsptch);
	}

	protected void commandCompleted(Command cmd, com.grey.naf.reactor.Dispatcher dsptch) throws java.io.IOException
	{
		events.produce(cmd, dsptch);
	}

	// Prune a dead Secondary from any active Commands to which it is attached.
	// Loop on temp copy of activecmds list, as the original may get modified during the loop.
	private void discardSecondary(Secondary agent) throws java.io.IOException
	{
		tmpcmds.clear();
		tmpcmds.addAll(activecmds);
		for (int idx = 0; idx != tmpcmds.size(); idx++) {
			Command cmd = tmpcmds.get(idx);
			if (cmd.detach(agent) == 1) {
				cmd.completed(dsptch);
			}
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
