/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;

/*
 * This is the only class which is passed around between different threads, so here is how synchronisation
 * works:
 * - Each newly connected Server grabs a Command from an ObjectPool and initialises it, before passing it to
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
public class NafManCommand
{
	public interface Handler
	{
		CharSequence handleNAFManCommand(NafManCommand cmd) throws java.io.IOException;
		CharSequence nafmanHandlerID();
	}

	public static final String ATTR_DISPATCHER = "d";
	public static final String ATTR_NAFLET = "n";
	public static final String ATTR_XSL = "st";
	public static final String ATTR_NOXSL = "noxsl";
	public static final String ATTR_KEY = "key";
	public static final String ATTR_LOGLVL = "log";
	public static final String ATTR_VERBOSE = "v";
	public static final String ATTR_TIME = "t";
	public static final String ATTR_RESET = "rst";
	public static final String ATTR_NOHTTP = "nohttp"; //omit HTTP header from response

	static final int DETACH_NOT = 1;
	static final int DETACH_FINAL = 2;
	static final int DETACH_NONFINAL = 3;

	private final java.util.ArrayList<NafManAgent> routeTo = new java.util.ArrayList<>();  //agents we'll be routed to
	private final com.grey.base.collections.HashedMap<String, String> args = new com.grey.base.collections.HashedMap<>();
	private final com.grey.base.utils.ByteChars response = new com.grey.base.utils.ByteChars();

	private NafManRegistry.DefCommand def;
	private NafManServer srvr;

	public NafManRegistry.DefCommand getCommandDef() {return def;}
	public String getArg(String nam) {return args.get(nam);}
	com.grey.base.collections.HashedMap<String, String> getArgs() {return args;}
	void setArg(String nam, String val) {args.put(nam, val);}

	NafManCommand init(NafManRegistry.DefCommand d, NafManServer s)
	{
		clear();
		def = d;
		srvr = s;
		if (!isPlaintextResponse()) response.append("<nafman><handlers>");
		return this;
	}

	NafManCommand clear()
	{
		routeTo.clear();
		response.clear();
		args.clear();
		return this;
	}

	// called at end in Primary thread
	void completed() throws java.io.IOException
	{
		//The response object is not contended here, but we do need to synchronise for the sake of memory visibility,
		//so as to see updates performed by other threads.
		synchronized (this) {
			if (!isPlaintextResponse()) response.append("</handlers></nafman>");
		}
		boolean ok = false;
		try {
			srvr.commandCompleted(response);
			ok = true;
		} finally {
			if (!ok) srvr.endConnection();
		}
	}

	//only called by Primary agent - before it calls getAttachedAgents()
	boolean attach(NafManAgent agent)
	{
		if (!isMatch(agent)) return false;
		routeTo.add(agent);
		return true;
	}

	// Only called by Primary thread, but need to synchronise against future multi-threaded calls to
	// detach() and attached(), to ensure visibility of this object in the Secondary threads.
	synchronized void getAttachedAgents(java.util.ArrayList<NafManAgent> lst)
	{
		lst.addAll(routeTo);
	}

	//can be called by any thread
	synchronized boolean attached(NafManAgent agent)
	{
		return (routeTo.contains(agent));
	}

	//can be called by any thread - 'agent' arg does not necessarily represent the calling thread
	synchronized int detach(NafManAgent agent)
	{
		if (!routeTo.remove(agent)) return DETACH_NOT;
		if (routeTo.size() == 0) return DETACH_FINAL;
		return DETACH_NONFINAL;
	}

	//can be called by any thread
	synchronized void addHandlerResponse(com.grey.naf.reactor.Dispatcher dsptch, Handler handler, CharSequence msg)
	{
		if (isPlaintextResponse()) {
			response.append(msg);
			return;
		}
		response.append("<handler dname=\"").append(dsptch.getName()).append("\"");
		response.append(" hname=\"").append(handler.nafmanHandlerID()).append("\"");
		response.append(" hclass=\"").append(handler.getClass().getName()).append("\">");
		response.append(msg).append("</handler>");
	}

	private boolean isMatch(NafManAgent agent)
	{
		if (!agent.getRegistry().isCommandRegistered(def.code, agent.getDispatcher())) {
			return false;
		}
		if (def.code.equals(NafManRegistry.CMD_APPSTOP)
				|| def.code.equals(NafManRegistry.CMD_DLIST)
				|| def.code.equals(NafManRegistry.CMD_SHOWCMDS)) {
			//these commands are only ever handled by the Primary, regardless of the specified Dispatcher
			return agent.isPrimary();
		}
		String target = args.get(ATTR_DISPATCHER);
		if (target != null) {
			//sent to a specific Dispatcher, so are we that Dispatcher?
			return StringOps.sameSeq(agent.getDispatcher().getName(), target);
		}
		if (def.code.equals(NafManRegistry.CMD_STOP)) {
			//unless sent to a specific Dispatcher, STOP is only handled by the Primary
			return agent.isPrimary();
		}
		//all other commands which are not addressed to a specific Dispatcher can be sent to all of them
		return true;
	}

	private boolean isPlaintextResponse() {
		return (def != null && NafManRegistry.RSRC_PLAINTEXT.equals(def.autopublish));
	}
}