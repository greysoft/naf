/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

/*
 * Application-wide registrations and defs.
 * Anything specific to one Dispatcher is held in the Agent class.
 * Note that this class represents a singleton, and it must be initialised before the multi-threaded
 * phase starts, ie. before any calls to Dispatcher.start(). No updates allowed after that.
 */
public final class Registry
{
	static {
		com.grey.naf.Launcher.announceNAF();
	}

	// the built-in NAF commands
	public static final String CMD_STOP = "STOP";
	public static final String CMD_APPSTOP = "APPSTOP";
	public static final String CMD_DLIST = "DSPLIST";
	public static final String CMD_DSHOW = "DSPSHOW";
	public static final String CMD_SHOWCMDS = "SHOWCMDS";
	public static final String CMD_KILLCONN = "KILLCONN";
	public static final String CMD_DNSDUMP = "DNSDUMP";
	public static final String CMD_DNSPRUNE = "DNSPRUNE";
	public static final String CMD_DNSQUERY = "DNSQUERY";
	public static final String CMD_FLUSH = "FLUSH";
	public static final String CMD_LOGLVL = "LOGLEVEL";

	//some of the built-in Resource names (those that get referenced from elsewhere)
	public static final String RSRC_CMDSTATUS = "cmdstatus";
	private static final String RSRC_CMDREG = "cmdreg";

	public static class DefCommand
	{
		public final String code;
		public final String family;
		public final String descr;
		public final String autopublish; //corresponds to a DefResources.path XSL file
		public final boolean neutral; //alters nothing
		public DefCommand(String c, String f, String d, String p, boolean rdonly) {
			code=c; family=f; descr=d; autopublish=p; neutral=rdonly;
		}
	}

	private static class CommandHandlerReg
	{
		final Command.Handler handler;
		final com.grey.naf.reactor.Dispatcher dsptch;
		final int pref;
		CommandHandlerReg(Command.Handler h, com.grey.naf.reactor.Dispatcher d, int p) {handler=h; dsptch=d; pref=p;}
	}

	private static final String FAMILY_NAFCORE = "NAF-Core";
	private static final String FAMILY_NAFDNS = "NAF-DNS";

	private static final DefCommand[] nafcmds = new DefCommand[] {
		new DefCommand(CMD_STOP, FAMILY_NAFCORE, "Stop specified Dispatcher - stop-all halts the entire NAF Context", null, false),
		new DefCommand(CMD_APPSTOP, FAMILY_NAFCORE, "Stop specified NAFlet", null, false),
		new DefCommand(CMD_DLIST, FAMILY_NAFCORE, "List all Dispatchers", null, true),
		new DefCommand(CMD_DSHOW, FAMILY_NAFCORE, "Show internal Dispatcher details", null, true),
		new DefCommand(CMD_SHOWCMDS, FAMILY_NAFCORE, "List all NAFMAN command registrations", RSRC_CMDREG, true),
		new DefCommand(CMD_KILLCONN, FAMILY_NAFCORE, "Kill specified connection", null, false),
		new DefCommand(CMD_FLUSH, FAMILY_NAFCORE, "Flush buffered logfiles to disk", RSRC_CMDSTATUS, true),
		new DefCommand(CMD_LOGLVL, FAMILY_NAFCORE, "Dynamically alter the logging level", null, false),
		new DefCommand(CMD_DNSDUMP, FAMILY_NAFDNS, "Dump DNS-Resolver cache and stats", RSRC_CMDSTATUS, true),
		new DefCommand(CMD_DNSPRUNE, FAMILY_NAFDNS, "Prune aged entries from DNS cache", RSRC_CMDSTATUS, false),
		new DefCommand(CMD_DNSQUERY, FAMILY_NAFDNS, "Do synchronous lookup on DNS cache (testing aid)", null, true)
	};


	public static class DefResource
	{
		public interface DataGenerator {
			byte[] generateResourceData(DefResource def, com.grey.naf.reactor.Dispatcher d) throws java.io.IOException;
		}
		public final String name; //must not match any DefCommand.code values
		public final String path;
		public final String mimetype; //only applies to static resources, dynamic output is typed at runtime
		public final DataGenerator gen;
		public DefResource(String n, String p, String t, DataGenerator g) {name=n; path=p; mimetype=t; gen=g;}
	}

	private static final DefResource[] nafresources = new DefResource[] {
		new DefResource("nafhome", "home.xsl", null, null), //must be first entry, subsequent order doesn't matter
		new DefResource("favicon.ico", "favicon.png", HTTP.CTYPE_PNG, null),
		new DefResource("nafman.css", "nafman.css", HTTP.CTYPE_CSS, null),
		new DefResource(RSRC_CMDSTATUS, "cmdstatus.xsl", null, null),
		new DefResource("dspdetails", "dspdetails.xsl", null, null),
		new DefResource(RSRC_CMDREG, "cmdreg.xsl", null, null)
	};


	private static class SingletonHolder {
		public static final Registry instance = new Registry();
	}
	public static Registry get() {return SingletonHolder.instance;}

	private final java.util.HashMap<String, java.util.ArrayList<CommandHandlerReg>> cmd_handlers
								= new java.util.HashMap<String, java.util.ArrayList<CommandHandlerReg>>();
	private final java.util.HashSet<com.grey.naf.reactor.Dispatcher> inviolate_handlers
								= new java.util.HashSet<com.grey.naf.reactor.Dispatcher>();
	private final java.util.HashMap<String, DefCommand> active_commands = new java.util.HashMap<String, DefCommand>();
	private final java.util.HashMap<String, DefResource> active_resources = new java.util.HashMap<String, DefResource>();
	private String homepage;

	synchronized public String getHomePage() {return homepage;}
	synchronized public boolean isCommandRegistered(String code) {return isCommandRegistered(code, null);}
	synchronized DefCommand getCommand(String code) {return active_commands.get(code);}
	synchronized DefResource getResource(String name) {return active_resources.get(name);}
	synchronized java.util.Collection<DefCommand> getCommands() {return active_commands.values();}
	synchronized java.util.Set<String> getResourceNames() {return active_resources.keySet();}

	private Registry()
	{
		try {
			loadCommands(nafcmds);
			loadResources(nafresources);
			setHomePage(nafresources[0].name);
		} catch (Exception ex) {
			throw new RuntimeException("Fatal error loading core NAFMAN defs - "+ex, ex);
		}
	}

	synchronized public void setHomePage(String rsrc_name) throws com.grey.base.ConfigException
	{
		if (getResource(rsrc_name) == null) throw new com.grey.base.ConfigException("NAFMAN: Unknown homepage="+rsrc_name);
		homepage = rsrc_name;
	}

	synchronized public void loadCommands(DefCommand[] defs) throws com.grey.base.ConfigException
	{
		for (int idx = 0; idx != defs.length; idx++) {
			DefCommand def = defs[idx];
			if (active_commands.containsKey(def.code)) throw new com.grey.base.ConfigException("NAFMAN: Duplicate cmd="+def.code);
			if (active_resources.containsKey(def.code)) throw new com.grey.base.ConfigException("NAFMAN: Command="+def.code+" conflicts with Resources");
			active_commands.put(def.code, def);
		}
	}

	// The Server class looks up the URL path as first a Command code and then a Resource name, so their IDs must be disjoint.
	// Unlike Commands however, duplicate Resources are allowed, and simply oveerride.
	synchronized public void loadResources(DefResource[] defs) throws com.grey.base.ConfigException
	{
		for (int idx = 0; idx != defs.length; idx++) {
			DefResource def = defs[idx];
			if (active_commands.containsKey(def.name)) throw new com.grey.base.ConfigException("NAFMAN: Resource="+def.path+" conflicts with cmd="+def.name);
			active_resources.put(def.name, def);
		}
	}

	synchronized boolean isCommandRegistered(String code, com.grey.naf.reactor.Dispatcher d)
	{
		java.util.ArrayList<CommandHandlerReg> lst = cmd_handlers.get(code);
		if (lst == null) return false;
		if (d == null) return true;  //just wanted to know if it was registered by anybody
		for (int idx = 0; idx != lst.size(); idx++) {
			if (lst.get(idx).dsptch == d) return true;
		}
		return false;
	}

	// Register handlers for various commands.
	// Some commands can have multiple handlers, while others can only have one, which is regulated by the preference
	// arg - lower values indicate higher priority.
	// A non-zero preference indicates a conditional registration, which will be blocked or displaced by higher-priority ones.
	// pref==0 means this is an unconditional registration, which will co-exist with any other unconditional registrations, but
	// evict any conditional ones.
	// All else being equal, later registrations within same Dispatcher supercede earlier ones.
	synchronized public boolean registerHandler(String cmdcode, int pref, Command.Handler handler, com.grey.naf.reactor.Dispatcher dsptch)
			throws com.grey.base.ConfigException
	{
		if (dsptch.nafman == null) {
			// If this is an internal NAFMAN handler (primary or secondary agent) then obviously its Dispatcher is NAFMAN-enabled
			// but we have to insert this get-out clause for internal handlers because the dsptch.nafman field is not set until
			// after the Agent constructor returns, and that's where they call this method from.
			if (!(handler instanceof Agent)) {
				return false;
			}
		}
		if (!active_commands.containsKey(cmdcode)) {
			dsptch.logger.warn("NAFMAN discarding undefined cmd="+cmdcode+" for handler="+dsptch.name+"/"+handler.getClass().getName());
			return false;
		}
		java.util.ArrayList<CommandHandlerReg> lst = cmd_handlers.get(cmdcode);
		if (lst != null) {
			// this list can hold multiple unconditional (zero-preference) handlers, or one conditional one
			for (int idx = 0; idx != lst.size(); idx++) {
				CommandHandlerReg def = lst.get(idx);
				if (dsptch == def.dsptch && handler == def.handler) {
					throw new com.grey.base.ConfigException("Duplicate handler for cmd="+cmdcode+" in Dispatcher="+dsptch.name);
				}
				if (pref == 0) {
					if (def.pref == 0) continue; //new handler can co-exist with existing one
				} else {
					if (pref >= def.pref) return false; //existing handler has higher priority
				}
				// new handler has higher priority, so displace existing one
				if (inviolate_handlers.contains(def.dsptch)) return false; //but existing handler has been marked permanent
				lst.remove(idx);
				break; //any further handlers can only be unconditional ones, so can co-exist
			}
		} else {
			lst = new java.util.ArrayList<CommandHandlerReg>();
			cmd_handlers.put(cmdcode, lst);
		}
		CommandHandlerReg def = new CommandHandlerReg(handler, dsptch, pref);
		lst.add(def);
		return true;
	}

	synchronized void getHandlers(com.grey.naf.reactor.Dispatcher d,
			com.grey.base.utils.HashedMap<String, java.util.ArrayList<Command.Handler>> h)
	{
		java.util.Iterator<String> it = cmd_handlers.keySet().iterator();
		while (it.hasNext()) {
			String cmdcode = it.next();
			java.util.ArrayList<CommandHandlerReg> reglst = cmd_handlers.get(cmdcode);
			for (int idx = 0; idx != reglst.size(); idx++) {
				CommandHandlerReg def = reglst.get(idx);
				if (def.dsptch != d) continue;
				java.util.ArrayList<Command.Handler> dlst = h.get(cmdcode);
				if (dlst == null) {
					dlst = new java.util.ArrayList<Command.Handler>();
					h.put(cmdcode, dlst);
				}
				dlst.add(def.handler);
			}
		}
		inviolate_handlers.add(d); //now that we've propagated its handlers, this Dispatcher becomes inviolate
	}

	synchronized CharSequence dumpState(StringBuilder sb, boolean xml)
	{
		if (sb == null) sb = new StringBuilder();
		int hcnt = 0;
		if (xml) {
			sb.append("<commands>");
		} else {
			sb.append("NAFMAN registered commands=").append(cmd_handlers.size()).append('/').append(active_commands.size());
		}
		java.util.Iterator<String> it = cmd_handlers.keySet().iterator();
		while (it.hasNext()) {
			String cmdcode = it.next();
			DefCommand cdef = getCommand(cmdcode);
			java.util.ArrayList<CommandHandlerReg> lst = cmd_handlers.get(cmdcode);
			hcnt += lst.size();
			if (xml) {
				sb.append("<command code=\"").append(cmdcode).append("\"");
				sb.append(" family=\"").append(cdef.family).append("\">");
				sb.append("<desc><![CDATA[").append(cdef.descr).append("]]></desc>");
				sb.append("<handlers>");
			} else {
				sb.append("\n- ").append(cmdcode).append('=').append(lst.size());
			}
			String dlm = ": ";
			for (int idx = 0; idx != lst.size(); idx++) {
				CommandHandlerReg hdef = lst.get(idx);
				if (xml) {
					sb.append("<handler dispatcher=\"").append(hdef.dsptch.name).append("\" pref=\"").append(hdef.pref).append("\">");
					sb.append(hdef.handler.getClass().getName()).append("</handler>");
				} else {
					sb.append(dlm).append(hdef.dsptch.name).append('/').append(hdef.handler.getClass().getName());
					sb.append('/').append(hdef.pref);
					dlm = "; ";
				}
			}
			if (xml) sb.append("</handlers></command>");
		}
		if (xml) {
			sb.append("</commands>");
		} else {
			sb.append("\nTotal handlers=").append(hcnt);
			if (cmd_handlers.size() != active_commands.size()) {
				sb.append("\nUnused Commands");
				String dlm = ": ";
				java.util.Iterator<String> itcmd = active_commands.keySet().iterator();
				while (itcmd.hasNext()) {
					String cmdcode = itcmd.next();
					if (cmd_handlers.containsKey(cmdcode)) continue;
					sb.append(dlm).append(cmdcode);
					dlm = ", ";
				}
			}
			sb.append("\nNAFMAN registered resources=").append(active_resources.size()).append(", Home=").append(homepage);
		}
		return sb;
	}
}