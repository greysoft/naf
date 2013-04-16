/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;

/*
 * Application-wide registrations and defs.
 * Anything specific to one Dispatcher is held in the Agent class.
 */
public final class Registry
{
	static {
		com.grey.naf.Launcher.announceNAF();
	}

	public interface CommandHandler
	{
		void handleNAFManCommand(Command cmd) throws com.grey.base.FaultException, java.io.IOException;
	}

	private static class CandidateHandler
	{
		final com.grey.naf.reactor.Dispatcher dsptch;
		final CommandHandler handler;
		final Command.Def cmd;
		final int pref;
		CandidateHandler(com.grey.naf.reactor.Dispatcher d, CommandHandler h, Command.Def c, int p) {dsptch=d; handler=h; cmd=c; pref=p;}
	}

	public static final int CMD_STOP = 1;
	public static final int CMD_DNSDUMP = 2;
	public static final int CMD_DNSPRUNE = 3;
	public static final int CMD_DLIST = 4;
	public static final int CMD_DSHOW = 5;
	public static final int CMD_APPSTOP = 6;
	public static final int CMD_SHOWCMDS = 7;
	public static final int CMD_FLUSH = 8;
	public static final int CMD_LOGLVL = 9;
	public static final int CMD_NAFRESERVED = 100; //commands 1-99 are reserved for NAF

	private static final Command.Def[] nafcmds = new Command.Def[] {
		new Command.Def(CMD_STOP, "stop", 0, 1, true, null),
		new Command.Def(CMD_DLIST, "dsplist", 0, 0, false, null),
		new Command.Def(CMD_DSHOW, "dspshow", 0, 1, true, null),
		new Command.Def(CMD_SHOWCMDS, "showcmds", 0, 1, true, null),
		new Command.Def(CMD_APPSTOP, "appstop", 2, 2, true, "naflet-name"),
		new Command.Def(CMD_FLUSH, "flush", 0, 1, true, null),
		new Command.Def(CMD_LOGLVL, "loglevel", 1, 2, false, "level [dispatcher-name]"),
		new Command.Def(CMD_DNSDUMP, "dnsdump", 0, 1, true, null),
		new Command.Def(CMD_DNSPRUNE, "dnsprune", 0, 1, true, null)
	};


	private static class SingletonHolder {
		public static final Registry instance = new Registry();
	}
	public static Registry get() {return SingletonHolder.instance;}

	private final java.util.concurrent.ConcurrentHashMap<Integer, Command.Def> commands
								= new java.util.concurrent.ConcurrentHashMap<Integer, Command.Def>();
	private com.grey.base.utils.HashedMapIntKey<CandidateHandler> candidates = new com.grey.base.utils.HashedMapIntKey<CandidateHandler>();

	private Registry()
	{
		try {
			loadCommands(nafcmds);
		} catch (Exception ex) {
			throw new RuntimeException("Fatal error loading NAF NAFMAN commands", ex);
		}
	}

	public void loadCommands(Command.Def[] defs) throws com.grey.base.ConfigException {
		for (int idx = 0; idx != defs.length; idx++) {
			loadCommand(defs[idx]);
		}
	}

	public Command.Def loadCommand(Command.Def def) throws com.grey.base.ConfigException
	{
		if (def.name == null || def.name.length() == 0) {
			throw new com.grey.base.ConfigException("Invalid NAFMAN command def - code="+def.code+", name="+def.name);
		}
		Command.Def def1 = commands.putIfAbsent(def.code, def);
		if (def1 != null) {
			if (!def1.name.equals(def.name)
					|| def1.min_args != def.min_args
					|| def1.max_args != def.max_args
					|| def1.dispatcher1 != def.dispatcher1
					|| !StringOps.sameSeq(def1.usage, def.usage)) {
				throw new com.grey.base.ConfigException("Inconsistent NAFMAN command def for code="+def.code+" - "+def.name+" conflicts with "+def.name);
			}
			def = def1;
		}
		return def;
	}

	public java.util.Collection<Command.Def> getCommands()
	{
		return commands.values();
	}

	public Command.Def getCommand(String name)
	{
		return getCommand(name, null);
	}

	public Command.Def getCommand(String name, StringBuilder sb)
	{
		java.util.Iterator<Command.Def> iter = commands.values().iterator();
		while (iter.hasNext()) {
			Command.Def def = iter.next();
			if (def.name.equals(name)) return def;
		}
		if (sb != null) {
			sb.append("'").append(name).append("' is not a recognised NAFMAN command.\n");
			showCommands(sb);
		}
		return null;
	}

	public Command.Def getCommand(Integer code)
	{
		return getCommand(code, null);
	}

	public Command.Def getCommand(Integer code, StringBuilder sb)
	{
		Command.Def def = commands.get(code);
		if (def == null && sb != null) {
			sb.append("Code=").append(code).append(" is not a recognised NAFMAN command.\n");
			showCommands(sb);
		}
		return def;
	}

	// convenience method which saves callers having to check for presence of NAFMAN agent
	public void registerHandler(int cmdcode, Registry.CommandHandler handler, com.grey.naf.reactor.Dispatcher dsptch)
			throws com.grey.base.ConfigException
	{
		if (dsptch.nafman == null) return;
		dsptch.nafman.registerHandler(cmdcode, handler);
	}

	// Provisional registration, which will be finalised by confirmCandidates(). This method is used for
	// commands which can only be handled by a single Dispatcher, as each lower-preference candidate replaces
	// the previous one.
	// Therefore this method can only be used by Dispatchers that are created and launched by the wired naf.xml
	// container. Any Dispatchers that are subsequently dynamically created can only call their their own NAFMAN's
	// registerHandler() method, which categorically registers the specified handler within that Dispatcher.
	// Pref=0 indicates an absolute preference, ie. it replaces any prior candidate.
	// That is also why there's no need to synchronise the 'candidates' map. confirmCandidates is called after the
	// naf.xml container initialises its wired Dispatchers, but before it activates them.
	public boolean registerCandidate(Command.Def cmd, int pref, CommandHandler handler, com.grey.naf.reactor.Dispatcher dsptch)
	{
		if (dsptch.nafman == null) return false;
		CandidateHandler candidate = (pref == 0 ? null : candidates.get(cmd.code));
		if (candidate != null && candidate.pref < pref) return false; //existing candidate is better
		candidate = new CandidateHandler(dsptch, handler, cmd, pref);
		candidates.put(cmd.code, candidate); //replace previous candidate
		return true;
	}

	public void confirmCandidates() throws com.grey.base.ConfigException
	{
		java.util.Iterator<CandidateHandler> iter = candidates.valuesIterator();
		while (iter.hasNext()) {
			CandidateHandler candidate = iter.next();
			candidate.dsptch.nafman.registerHandler(candidate.cmd.code, candidate.handler);
		}
		candidates = null;
	}

	public StringBuilder validateArgCount(Command.Def def, int argc, StringBuilder sb)
	{
		if (argc >= def.min_args && argc <= def.max_args) {
			// number of args is valid
			return null;
		}
		if (sb == null) sb = new StringBuilder();
		sb.append("Invalid parameters for NAFMAN command=").append(def.name);
		sb.append(": args=").append(argc).append(" vs min=").append(def.min_args).append("/max=").append(def.max_args);
		sb.append("\nUSAGE: ").append(def.name);
		int argpos = 0;
		if (def.dispatcher1) {
			argpos++;
			String dname = "Dispatcher-name";
			if (def.min_args == 0) dname = "["+dname+"]";
			sb.append(' ').append(dname);
		}
		if (def.usage != null) {
			sb.append(' ').append(def.usage);
		} else if (def.max_args > argpos) {
			sb.append(" ...");
		}
		return sb;
	}

	private CharSequence showCommands(StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder();
		sb.append("Supported commands: ");
		java.util.Collection<Command.Def> defs = getCommands();
		java.util.List<String> cmdnames = new java.util.ArrayList<String>();
		java.util.Iterator<Command.Def> iter = defs.iterator();
		while (iter.hasNext()) cmdnames.add(iter.next().name);
		java.util.Collections.sort(cmdnames);
		for (int idx = 0; idx != cmdnames.size(); idx++) {
			if (idx != 0) sb.append(", ");
			sb.append(cmdnames.get(idx)).append('/').append(getCommand(cmdnames.get(idx)).code);
		}
		return sb;
	}
}
