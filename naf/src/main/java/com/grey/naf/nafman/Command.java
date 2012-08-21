/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.ByteChars;
import com.grey.base.utils.StringOps;

/*
 * This is the only class which is passed around between different threads, so here is now synchronisation
 * works:
 * - Each newly connected Server grabs a Command from an ObjectWell and fully initialises it, before passing it
 *   to the Primary Agent. This all happens in that agent's Dispatcher thread.
 * - Before passing Command object to other threads, the Primary synchronises by calling its getAttachedAgents()
 *   method
 * - When it receives a Command, the first thing a Secondary does is synchronise by calling its attached() method,
 *   which ensures it has visibility of the Command fields.
 *   The attached() call also resolves the question of who owns the command, a question which comes into play if
 *   the Primary tries to detach a Secondary from it, after failing to send the command to its Producer.
 *
 * There is also an parallel synchronisation performed on the 'response' field. Callers synchronise on it to update
 * it, by calling sendResponse().
 * Back in the Primary thread once the command has completed, the Server synchronises on it to retrieve it, by
 * calling getResponse().
 * The 'processedBy' field is co-synchronised with 'response'.
 */
public class Command
{
	protected static final int FLDLEN_CODE = 1;
	protected static final int FLDLEN_ARGSLEN = 2;
	protected static final int HDRLEN = FLDLEN_CODE + FLDLEN_ARGSLEN;
	protected static final byte ARGSDLM = '|';
	private static final String DISPLAYDLM = " | ";

	public static class Def
	{
		public final int code;
		public final String name;
		public final int min_args;
		public final int max_args;
		public final boolean dispatcher1;  //first arg is a Dispatcher name (if present)
		public final String usage;
		public Def(int c, String n, int min, int max, boolean d1, String u) {
			code=c; name=n; min_args=min; max_args = max; dispatcher1 = (max==0?false:d1); usage = u;}
	}

	protected static final class Factory
		implements com.grey.base.utils.ObjectWell.ObjectFactory
	{
		private final Primary agent;

		public Factory(Primary agent) {
			this.agent = agent;
		}

		@Override
		public Command factory_create() {
			return new Command(agent);
		}
	}

	private final Primary primary;
	private final java.util.ArrayList<Agent> routeTo = new java.util.ArrayList<Agent>();  //agents we'll be routed to
	private final com.grey.base.utils.HashedSet<String> processedBy = new com.grey.base.utils.HashedSet<String>();  //agents that actually processed us
	private final java.util.ArrayList<ByteChars> args = new java.util.ArrayList<ByteChars>(); //replace with ByteChars
	private final ByteChars response = new ByteChars();
	private final StringBuilder description = new StringBuilder();

	public Def def;
	protected Server srvr;
	private CharSequence target; //non-null means this command is targetted at the named dispatcher

	public CharSequence getDescription() {return description;}
	public int getArgCount() {return args.size();}
	public CharSequence getArg(int idx) {return args.get(idx);}

	protected Command(Primary agent)
	{
		primary = agent;
	}

	protected void set(Def def, Server srvr)
	{
		clear();
		this.def = def;
		this.srvr = srvr;
		description.append(def.code).append('/').append(def.name);
	}

	protected boolean setArgs(byte[] arr, int off, int len, StringBuilder sb)
	{
		com.grey.base.utils.ByteChars.parseTerms(arr, off, len, ARGSDLM, args, primary.bcstore);
		sb = Registry.get().validateArgCount(def, args.size(), sb);
		if (sb != null) {
			primary.dsptch.logger.info("NAFMAN="+primary.dsptch.name+" discarding command="+def.name+" due to bad argcnt="
					+args.size() +" vs "+def.min_args+"-"+def.max_args);
			return false;
		}

		if (def.dispatcher1 && args.size() != 0) {
			target = args.get(0);
		}

		// now augment the descriptive info for current context
		if (target != null) description.append(" - Target=").append(target);
		description.append(" Args=").append(args.size());
		int arg1 = (target == null ? 0 : 1);  //don't repeat target, for sake of brevity
		String pfx = ": ";
		for (int idx = arg1; idx != args.size(); idx++) {
			description.append(pfx).append(args.get(idx));
			pfx = DISPLAYDLM;
		}
		return true;
	}

	protected boolean attach(Agent agent)
	{
		if (!isMatch(agent)) return false;
		routeTo.add(agent);
		return true;
	}

	protected int detach(Agent agent)
	{
		synchronized (routeTo) {
			if (!routeTo.remove(agent)) return -1;
			if (routeTo.size() == 0) return 1;
		}
		return 0;
	}

	protected boolean attached(Agent agent)
	{
		synchronized (routeTo) {
			return (routeTo.contains(agent));
		}
	}

	protected void getAttachedAgents(java.util.ArrayList<Agent> lst)
	{
		synchronized (routeTo) {
			for (int idx = 0; idx != routeTo.size(); idx++) {
				lst.add(routeTo.get(idx));
			}
		}
	}

	// The parameter is the Dispatcher in whose thread this is being called, not necessarily the
	// one whose agent has just completed this command.
	protected void completed(com.grey.naf.reactor.Dispatcher dsptch) throws java.io.IOException
	{
		primary.commandCompleted(this, dsptch);
	}

	public void sendResponse(com.grey.naf.reactor.Dispatcher dsptch, CharSequence msg)
	{
		synchronized (response) {
			if (msg != null && msg.length() != 0) {
				response.append("\nDispatcher=").append(dsptch.name).append(" issued ");
				response.append(String.valueOf(msg.length())).append("-byte response:\n");
				response.append(msg).append((byte)'\n');
			}
			processedBy.add(dsptch.name);
		}
	}

	protected ByteChars getResponse()
	{
		synchronized (response) {
			return (response.ar_len == 0 ? null : response);
		}
	}

	// This needs to be synchronised by calling getResponse() first
	protected java.util.Collection<String> getProcessedBy()
	{
		return processedBy;
	}

	private boolean isMatch(Agent agent)
	{
		if (def.code == Registry.CMD_APPSTOP || def.code == Registry.CMD_DLIST) return agent.isPrimary();
		if (target != null) return StringOps.sameSeq(agent.dsptch.name, target);
		if (def.code == Registry.CMD_STOP) return agent.isPrimary();
		return true;
	}

	protected void clear()
	{
		while (args.size() != 0) {
			primary.bcstore.store(args.remove(0));
		}
		routeTo.clear();
		processedBy.clear();
		response.clear();
		description.setLength(0);
		target = null;
	}
}
