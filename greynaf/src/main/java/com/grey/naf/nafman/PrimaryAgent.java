/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import java.util.ArrayList;

import com.grey.base.config.SysProps;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.CM_Listener;
import com.grey.naf.reactor.ConcurrentListener;
import com.grey.naf.reactor.Producer;
import com.grey.naf.reactor.TimerNAF;
import com.grey.logging.Logger.LEVEL;

public class PrimaryAgent
	extends NafManAgent
	implements Producer.Consumer<Object>
{
	private static final long shutdown_delay = SysProps.getTime("greynaf.nafman.shutdown.delay", 500);

	private final CM_Listener lstnr;
	private final ArrayList<SecondaryAgent> secondaries = new ArrayList<>();
	private final ArrayList<NafManCommand> activecmds = new ArrayList<>();
	private final Producer<Object> events;  //for receiving events from the secondary agents
	private final boolean surviveDownstream;

	//preallocated purely for efficiency
	private final ArrayList<NafManAgent> tmpagents = new ArrayList<>();
	private final ArrayList<NafManCommand> tmpcmds = new ArrayList<>();

	@Override
	public PrimaryAgent getPrimary() {return this;}
	@Override
	public int getPort() {return lstnr.getPort();}

	public PrimaryAgent(Dispatcher dsptch, NafManRegistry reg, NafManConfig cfg) throws java.io.IOException
	{
		super(dsptch, reg);
		this.surviveDownstream = cfg.isSurviveDownstream();
		events = new Producer<>("NAFMAN-Agent-events", dsptch, this);
		lstnr = ConcurrentListener.create(dsptch, this, null, cfg.getListenerConfig());
		dsptch.getLogger().info("NAFMAN-Primary="+dsptch.getName()+": survive_downstream="+surviveDownstream+", lstnr="+lstnr);

		// these commands are only fielded by the Primary NAFMAN agent
		reg.registerHandler(NafManRegistry.CMD_DLIST, 0, this, dsptch);
		reg.registerHandler(NafManRegistry.CMD_APPSTOP, 0, this, dsptch);
		reg.registerHandler(NafManRegistry.CMD_SHOWCMDS, 0, this, dsptch);
	}

	@Override
	public void start() throws java.io.IOException
	{
		if (getDispatcher().getLogger().isActive(LEVEL.TRC)) {
			CharSequence dump = getRegistry().dumpState(null, false);
			getDispatcher().getLogger().trace(dump);
		}
		super.start();
		events.startDispatcherRunnable();
		lstnr.startDispatcherRunnable();
	}

	@Override
	public void stop()
	{
		Dispatcher dsptch = getDispatcher();
		dsptch.getLogger().info("NAFMAN Primary="+dsptch.getName()+" shutdown with Secondaries="+secondaries.size()+", Commands="+activecmds.size());
		setShutdown();
		lstnr.stopDispatcherRunnable();

		// Loop on a copy of the secondaries list, as the Dispatcher stop() command may reentrantly call
		// back into our producerIndication() method below and modify the list while we're looping on it.
		SecondaryAgent[] arr = secondaries.toArray(new SecondaryAgent[0]);
		for (int idx = 0; idx != arr.length; idx++) {
			arr[idx].getDispatcher().stop();
		}

		if (secondaries.size() != 0) {
			// Give the secondaries time to shut down.
			// This is not critical, but it allows them to shut down gracefully, without lots of ugly logging
			// errors as they fail to contact the terminated Producer of a vanished Primary.
			TimerNAF.sleep(shutdown_delay);
		}
		events.shutdown(true);
		dsptch.getLogger().info("NAFMAN Primary shutdown completed - Secondaries="+secondaries.size()+", Commands="+activecmds.size());
	}

	void handleCommand(NafManCommand cmd) throws java.io.IOException
	{
		for (int idx = 0; idx != secondaries.size(); idx++) {
			cmd.attach(secondaries.get(idx));
		}
		Dispatcher dsptch = getDispatcher();
		NafManRegistry.DefCommand def = cmd.getCommandDef();
		cmd.attach(this);
		tmpagents.clear();
		cmd.getAttachedAgents(tmpagents); //synchronises on cmd, before we pass it to secondaries
		LEVEL lvl = LEVEL.TRC;
		if (dsptch.getLogger().isActive(lvl)) {
			dsptch.getLogger().log(lvl, "NAFMAN Primary fielding command="+def.code+" for Agents="
						+tmpagents.size()+"/"+(secondaries.size()+1)+" - ActiveCmds="+activecmds.size());
		}
		activecmds.add(cmd);
		boolean completed = (tmpagents.size() == 0);
		boolean deadsecs = false;

		for (int idx = 0; idx != tmpagents.size(); idx++) {
			if (tmpagents.get(idx) == this) {
				commandReceived(cmd);
			} else {
				SecondaryAgent agent = (SecondaryAgent)tmpagents.get(idx);
				try {
					agent.injectCommand(cmd);
				} catch (java.io.IOException ex) {
					int status = cmd.detach(agent);
					if (status == NafManCommand.DETACH_NOT) continue;
					if (status == NafManCommand.DETACH_FINAL) completed = true;
					deadsecs = true; //assume the Secondary has died
					dsptch.getLogger().info("NAFMAN="+dsptch.getName()+" failed to forward cmd="+def.code+" to Secondary="
							+agent.getDispatcher().getName()+" - "+com.grey.base.ExceptionUtils.summary(ex));
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
	public void producerIndication(Producer<Object> p) throws java.io.IOException
	{
		Dispatcher dsptch = getDispatcher();
		Object event;
		while ((event = events.consume()) != null) {
			Class<?> clss = event.getClass();
			if (clss == SecondaryAgent.class) {
				SecondaryAgent agent = (SecondaryAgent)event;
				dsptch.getLogger().info("NAFMAN="+dsptch.getName()+" received subscription from Secondary="+agent.getDispatcher().getName());
				secondaries.add(agent);
			} else if (clss == String.class) {
				SecondaryAgent agent = getSecondary((String)event);
				if (agent == null) {
					//can't see how this could happen in reality
					dsptch.getLogger().warn("NAFMAN="+dsptch.getName()+" received unsubscription from unidentified Secondary="+event);
					continue;
				}
				dsptch.getLogger().info("NAFMAN="+dsptch.getName()+" received unsubscription from Secondary="+agent.getDispatcher().getName());
				discardSecondary(agent);
				secondaries.remove(agent);
				if (!surviveDownstream) stopDispatcher();
			} else {
				NafManCommand cmd = (NafManCommand)event;
				activecmds.remove(cmd);
				cmd.completed();
			}
		}
	}

	void secondarySubscribed(SecondaryAgent agent) throws java.io.IOException
	{
		events.produce(agent);
	}

	void secondaryUnsubscribed(SecondaryAgent agent) throws java.io.IOException
	{
		events.produce(agent.getDispatcher().getName());
	}

	void commandCompleted(NafManCommand cmd) throws java.io.IOException
	{
		events.produce(cmd);
	}

	// Prune a dead Secondary from any active Commands to which it is attached.
	// Loop on temp copy of activecmds list, as the original may get modified during the loop.
	private void discardSecondary(SecondaryAgent agent) throws java.io.IOException
	{
		tmpcmds.clear();
		tmpcmds.addAll(activecmds);
		for (int idx = 0; idx != tmpcmds.size(); idx++) {
			NafManCommand cmd = tmpcmds.get(idx);
			if (cmd.detach(agent) == NafManCommand.DETACH_FINAL) commandCompleted(cmd);
		}
	}

	private SecondaryAgent getSecondary(String name)
	{
		for (int idx = 0; idx != secondaries.size(); idx++) {
			if (secondaries.get(idx).getDispatcher().getName().equals(name)) return secondaries.get(idx);
		}
		return null;
	}
}
