/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;

/*
 * This is the only class which is passed around between different threads, so here is how synchronisation
 * works:
 * - Each newly connected Server grabs a Command from an ObjectWell and initialises it, before passing it to
 *   the Primary Agent. This all happens in the primary agent's Dispatcher thread.
 * - Before passing this object to other threads, the Primary synchronises by calling its getAttachedAgents()
 *   method
 * - When it receives a Command, the first thing a Secondary does is synchronise by calling its attached() method,
 *   which ensures it has visibility of the Command fields.
 * - Agents also synchronise on detach() and addHandlerResponse() to ensure the consistency and visibility of any
 *   changes they make.
 * - After being processed by all relevant agent threads, the Command.completed() method is called in the
 *   primary thread, which synchronises one final time to see changes performed by the other threads.
 * Note that no thread ever has ownership of this object. The sequence of attached()-addHandlerResponse()-detach()
 * calls made by each thread is interleaved with other threads.
 */
public final class Command
{
	public interface Handler
	{
		CharSequence handleNAFManCommand(Command cmd) throws com.grey.base.FaultException, java.io.IOException;
	}

	public static final String ATTR_DISPATCHER = "d";
	public static final String ATTR_NAFLET = "n";
	public static final String ATTR_XSL = "st";
	public static final String ATTR_KEY = "key";
	public static final String ATTR_LOGLVL = "log";
	public static final String ATTR_VERBOSE = "v";
	public static final String ATTR_TIME = "t";
	public static final String ATTR_RESET = "rst";
	public static final String ATTR_NOHTTP = "nohttp"; //omit HTTP header from response

	static final int DETACH_NOT = 1;
	static final int DETACH_FINAL = 2;
	static final int DETACH_NONFINAL = 3;

	private final java.util.ArrayList<Agent> routeTo = new java.util.ArrayList<Agent>();  //agents we'll be routed to
	private final com.grey.base.utils.HashedMap<String, String> args = new com.grey.base.utils.HashedMap<String, String>();
	private final com.grey.base.utils.ByteChars response = new com.grey.base.utils.ByteChars();

	public Registry.DefCommand def;
	private Server srvr;

	public String getArg(String nam) {return args.get(nam);}
	com.grey.base.utils.HashedMap<String, String> getArgs() {return args;}
	void setArg(String nam, String val) {args.put(nam, val);}

	Command init(Registry.DefCommand def, Server srvr)
	{
		clear();
		this.def = def;
		this.srvr = srvr;
		response.append("<nafman><agents>");
		return this;
	}

	Command clear()
	{
		routeTo.clear();
		response.clear();
		args.clear();
		return this;
	}

	// called at end in Primary thread
	void completed() throws java.io.IOException, com.grey.base.FaultException
	{
		com.grey.base.utils.ByteChars bcrsp;
		synchronized (this) { //synchronise to see updates performed by other threads
			bcrsp = response;
		}
		bcrsp.append("</agents></nafman>");
		boolean ok = false;
		try {
			srvr.commandCompleted(bcrsp);
			ok = true;
		} finally {
			if (!ok) srvr.endConnection();
		}
	}

	//only called by Primary agent - before it calls getAttachedAgents()
	boolean attach(Agent agent)
	{
		if (!isMatch(agent)) return false;
		routeTo.add(agent);
		return true;
	}

	// Only called by Primary thread, but need to synchronise against future multi-threaded calls to
	// detach() and attached(), to ensure visibility of this object in the Secondary threads.
	synchronized void getAttachedAgents(java.util.ArrayList<Agent> lst)
	{
		for (int idx = 0; idx != routeTo.size(); idx++) {
			lst.add(routeTo.get(idx));
		}
	}

	//can be called by any thread
	synchronized boolean attached(Agent agent)
	{
		return (routeTo.contains(agent));
	}

	//can be called by any thread - 'agent' arg does not necessarily represent the calling thread
	synchronized int detach(Agent agent)
	{
		if (!routeTo.remove(agent)) return DETACH_NOT;
		if (routeTo.size() == 0) return DETACH_FINAL;
		return DETACH_NONFINAL;
	}

	//can be called by any thread
	synchronized void addHandlerResponse(com.grey.naf.reactor.Dispatcher dsptch, CharSequence msg)
	{
		response.append("<agent name=\"").append(dsptch.name).append("\">");
		response.append(msg).append("</agent>");
	}

	private boolean isMatch(Agent agent)
	{
		if (!Registry.get().isCommandRegistered(def.code, agent.dsptch)) {
			return false;
		}
		if (def.code.equals(Registry.CMD_APPSTOP)
				|| def.code.equals(Registry.CMD_DLIST)
				|| def.code.equals(Registry.CMD_SHOWCMDS)) {
			//these commands are only ever handled by the Primary, regardless of the specified Dispatcher
			return agent.isPrimary();
		}
		String target = args.get(ATTR_DISPATCHER);
		if (target != null) {
			//sent to a specific Dispatcher, so are we that Dispatcher?
			return StringOps.sameSeq(agent.dsptch.name, target);
		}
		if (def.code.equals(Registry.CMD_STOP)) {
			//unless sent to a specific Dispatcher, STOP is only handled by the Primary
			return agent.isPrimary();
		}
		//all other commands which are not addressed to a specific Dispatcher can be sent to all of them
		return true;
	}
}