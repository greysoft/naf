/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.ConfigException;
import com.grey.naf.reactor.Dispatcher;

public abstract class Agent
	implements Registry.CommandHandler,
		com.grey.naf.reactor.Producer.Consumer
{
	public final Dispatcher dsptch;
	protected final com.grey.base.utils.HashedMapIntKey<Registry.CommandHandler> handlers;
	protected boolean in_shutdown;
	//temp objects pre-allocated merely for efficiency
	private final StringBuilder sbtmp = new StringBuilder();

	public abstract boolean isPrimary();
	public abstract int getPort();
	public abstract void stop();
	public void start() throws com.grey.base.FaultException, java.io.IOException {}

	protected Agent(Dispatcher d, com.grey.base.config.XmlConfig cfg) throws com.grey.base.ConfigException
	{
		dsptch = d;
		handlers = new com.grey.base.utils.HashedMapIntKey<Registry.CommandHandler>();
		registerHandler(Registry.CMD_STOP, this);
		registerHandler(Registry.CMD_DSHOW, this);
		registerHandler(Registry.CMD_FLUSH, this);
		registerHandler(Registry.CMD_SHOWCMDS, this);
	}

	// If there are multiple handlers for a command, the latest registrant supercedes the rest
	public void registerHandler(int cmdcode, Registry.CommandHandler handler) throws com.grey.base.ConfigException
	{
		Command.Def cmd = Registry.get().getCommand(cmdcode);
		if (cmd == null) throw new ConfigException("Handler="+handler.getClass().getName()+" trying to load unregistered cmd="+cmdcode);
		handlers.put(cmd.code, handler);
		dsptch.logger.debug("NAFMAN="+dsptch.name+" has registered Handler="+handler.getClass().getName()+" for Command="
				+cmd.code+"/"+cmd.name);
	}

	protected void commandReceived(Command cmd) throws com.grey.base.FaultException, java.io.IOException
	{
		if (!cmd.attached(this)) return;
		try {
			processCommand(cmd);
		} finally {
			if (cmd.detach(this) == 1) cmd.completed(dsptch);
		}
	}

	protected void processCommand(Command cmd) throws com.grey.base.FaultException, java.io.IOException
	{
		Registry.CommandHandler handler = handlers.get(cmd.def.code);
		String logmsg = "NAFMAN="+dsptch.name+" received command="+cmd.getDescription()+" - Handler="+handler;
		if (handler == null) {
			dsptch.logger.debug(logmsg);
		} else {
			dsptch.logger.info(logmsg);
		}
		if (handler != null) handler.handleNAFManCommand(cmd);
	}

	@Override
	public void handleNAFManCommand(Command cmd) throws java.io.IOException
	{
		sbtmp.setLength(0);

		switch (cmd.def.code)
		{
		case Registry.CMD_STOP:
			stopDispatcher();
			break;
		case Registry.CMD_DLIST:
			Dispatcher[] dlist = Dispatcher.getDispatchers();
			sbtmp.append("Dispatchers=").append(dlist.length).append(':');
			for (int idx = 0; idx != dlist.length; idx++) {
				sbtmp.append("\n- ").append(dlist[idx].name).append(": NAFMAN=");
				String nafman = "N";
				if (dlist[idx].nafman != null) nafman = (dlist[idx].nafman.isPrimary() ? "Primary" : "Secondary");
				sbtmp.append(nafman);
			}
			break;
		case Registry.CMD_DSHOW:
			dsptch.dumpState(sbtmp);
			break;
		case Registry.CMD_FLUSH:
			dsptch.flusher.flushAll();
			break;
		case Registry.CMD_SHOWCMDS:
			sbtmp.append("Handlers=").append(handlers.size()).append(':');
			com.grey.base.utils.IteratorInt iter = handlers.keysIterator();
			while (iter.hasNext()) {
				Command.Def def = Registry.get().getCommand(iter.next());
				Registry.CommandHandler handler = handlers.get(def.code);
				sbtmp.append("\n- ").append("Command=").append(def.code).append('/').append(def.name);
				sbtmp.append(": Handler=").append(handler.getClass().getName());
			}
			break;
		case Registry.CMD_APPSTOP:
			String dname = cmd.getArg(0).toString();
			Dispatcher d = Dispatcher.getDispatcher(dname);
			if (d == null) {
				sbtmp.append("Unrecognised Dispatcher="+dname);
				break;
			}
			String naflet = cmd.getArg(1).toString();
			d.unloadNaflet(naflet, dsptch);
			break;
		default:
			// we've obviously registered for this command, so we must be missing a Case label - clearly a bug
			dsptch.logger.error("NAFMAN="+dsptch.name+": Missing case for cmd="+cmd.def.code);
			return;
		}
		cmd.sendResponse(dsptch, sbtmp);
	}

	// signal our Dispatcher to stop, and it will in turn stop us when it shuts down
	protected void stopDispatcher() throws java.io.IOException
	{
		if (in_shutdown) return;  //Dispatcher has already told us to stop
		dsptch.stop(dsptch);
	}
}
