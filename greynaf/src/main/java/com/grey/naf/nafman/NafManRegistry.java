/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.config.SysProps;
import com.grey.base.collections.HashedMap;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.errors.NAFConfigException;

/*
 * Application-wide registrations and defs.
 * Anything specific to one Dispatcher is held in the Agent class.
 */
public class NafManRegistry
{
	private static final boolean THROW_ON_DUP = SysProps.get("greynaf.nafman.registry.dupthrow", false);

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
	public static final String CMD_DNSLOADROOTS = "DNSLOADROOTS";
	public static final String CMD_FLUSH = "FLUSH";
	public static final String CMD_LOGLVL = "LOGLEVEL";

	//some of the built-in Resource names (those that get referenced from elsewhere)
	public static final String RSRC_PLAINTEXT = "plaintext"; //pseudo resource name, indicating no XSL stylesheet
	public static final String RSRC_CMDSTATUS = "cmdstatus";
	private static final String RSRC_CMDREG = "cmdreg";

	private static final String FAMILY_NAFCORE = "NAF-Core";
	private static final String FAMILY_NAFDNS = "NAF-DNS";

	public static class DefCommand
	{
		public final String code;
		public final String family;
		public final String descr;
		public final String autopublish; //corresponds to a DefResources.path XSL file
		public final boolean neutral; //alters nothing
		public DefCommand(String c, String f, String d, String autopub, boolean rdonly) {
			code=c; family=f; descr=d; autopublish=autopub; neutral=rdonly;
		}
		@Override
		public String toString() {return "DefCommand="+code+"/"+family+" with autopub="+autopublish+"/"+neutral+" - "+descr;}
	}

	public static class DefResource
	{
		public interface DataGenerator {
			byte[] generateResourceData(Dispatcher d) throws java.io.IOException;
		}
		public final String name; //must not match any DefCommand.code values
		public final String path;
		public final String mimetype; //only applies to static resources, dynamic output is typed at runtime
		public final DataGenerator gen;
		public DefResource(String n, String p, String t, DataGenerator g) {name=n; path=p; mimetype=t; gen=g;}
		@Override
		public String toString() {return "DefResource="+name+", data="+mimetype+"/"+path+" - gen="+gen;}
	}

	private static class CommandHandlerReg
	{
		final NafManCommand.Handler handler;
		final Dispatcher dsptch;
		final int pref;
		CommandHandlerReg(NafManCommand.Handler h, Dispatcher d, int p) {handler=h; dsptch=d; pref=p;}
		@Override
		public String toString() {return "CommandHandlerReg="+handler.getClass().getName()+"/"+dsptch.getName()+"/pref="+pref;}
	}

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
		new DefCommand(CMD_DNSLOADROOTS, FAMILY_NAFDNS, "Reload DNS roots in DNS-Resolver", RSRC_CMDSTATUS, false),
		new DefCommand(CMD_DNSQUERY, FAMILY_NAFDNS, "Do synchronous lookup on DNS cache (testing aid)", null, true)
	};

	private static final DefResource[] nafresources = new DefResource[] {
		new DefResource("nafhome", "home.xsl", null, null), //must be first entry, subsequent order doesn't matter
		new DefResource("favicon.ico", "favicon.png", HTTP.CTYPE_PNG, null),
		new DefResource("nafman.css", "nafman.css", HTTP.CTYPE_CSS, null),
		new DefResource(RSRC_CMDSTATUS, "cmdstatus.xsl", null, null),
		new DefResource("dspdetails", "dspdetails.xsl", null, null),
		new DefResource(RSRC_CMDREG, "cmdreg.xsl", null, null)
	};

	private final java.util.HashMap<String, java.util.ArrayList<CommandHandlerReg>> cmd_handlers = new java.util.HashMap<>();
	private final java.util.HashSet<Dispatcher> inviolate_handlers = new java.util.HashSet<>();
	private final java.util.HashMap<String, DefCommand> active_commands = new java.util.HashMap<>();
	private final java.util.HashMap<String, DefResource> active_resources = new java.util.HashMap<>();
	private String homepage;

	synchronized public String getHomePage() {return homepage;}

	synchronized DefCommand getCommand(String code) {return active_commands.get(code);}
	synchronized java.util.Collection<DefCommand> getCommands() {return active_commands.values();}
	public boolean isCommandRegistered(String code) {return isCommandRegistered(code, null);}

	synchronized DefResource getResource(String name) {return active_resources.get(name);}
	synchronized java.util.Set<String> getResourceNames() {return active_resources.keySet();}

	public static NafManRegistry get(ApplicationContextNAF appctx) {
		return appctx.getNamedItem(NafManRegistry.class.getName(), () -> new NafManRegistry(appctx));
	}

	private NafManRegistry(ApplicationContextNAF appctx) {
		loadCommands(nafcmds);
		loadResources(nafresources);
		setHomePage(nafresources[0].name);
	}

	synchronized public void setHomePage(String rsrc_name)
	{
		if (getResource(rsrc_name) == null) throw new NAFConfigException("NAFMAN: Unknown homepage="+rsrc_name);
		homepage = rsrc_name;
	}

	synchronized public void loadCommands(DefCommand[] defs)
	{
		for (int idx = 0; idx != defs.length; idx++) {
			loadCommand(defs[idx]);
		}
	}

	// Duplicate commands are discarded
	synchronized private void loadCommand(DefCommand def)
	{
		if (active_commands.containsKey(def.code)) {
			if (THROW_ON_DUP) throw new NAFConfigException("NAFMAN: Duplicate cmd="+def.code+" - "+def);
			return;
		}
		if (active_resources.containsKey(def.code)) throw new NAFConfigException("NAFMAN: Command="+def.code+" conflicts with Resources");
		active_commands.put(def.code, def);
	}

	// The Server class looks up the URL path as first a Command code and then a Resource name, so their IDs must be disjoint.
	// Unlike Commands, duplicate Resources override the original.
	synchronized public void loadResources(DefResource[] defs)
	{
		for (int idx = 0; idx != defs.length; idx++) {
			DefResource def = defs[idx];
			if (active_commands.containsKey(def.name)) throw new NAFConfigException("NAFMAN: Resource="+def.path+" conflicts with cmd="+def.name);
			active_resources.put(def.name, def);
		}
	}

	synchronized boolean isCommandRegistered(String code, Dispatcher d)
	{
		java.util.ArrayList<CommandHandlerReg> lst = cmd_handlers.get(code);
		if (lst == null) return false;
		if (d == null) return true;  //just wanted to know if it was registered by anybody
		for (int idx = 0; idx != lst.size(); idx++) {
			if (lst.get(idx).dsptch == d) return true;
		}
		return false;
	}

	// Supports commands whose name is not statically defined, and is only known at runtime
	synchronized public boolean registerDynamicCommand(String cmdcode, NafManCommand.Handler handler, Dispatcher dsptch,
			String family, String autopub, boolean rdonly, String descr)
	{
		if (active_commands.containsKey(cmdcode)) return false;
		DefCommand def = new DefCommand(cmdcode, family, descr, autopub, rdonly);
		loadCommand(def);
		return registerHandler(cmdcode, 0, handler, dsptch);
	}

	// Register handlers for various commands.
	// Some commands can have multiple handlers, while others can only have one, which is regulated by the preference
	// arg - lower values indicate higher priority.
	// A non-zero preference indicates a conditional registration, which will be blocked or displaced by higher-priority ones.
	// pref==0 means this is an unconditional registration, which will co-exist with any other unconditional registrations, but
	// evict any conditional ones.
	// All else being equal, later registrations within same Dispatcher supercede earlier ones.
	synchronized public boolean registerHandler(String cmdcode, int pref, NafManCommand.Handler handler, Dispatcher dsptch)
	{
		if (dsptch.getNafManAgent() == null) {
			// If this is an internal NAFMAN handler (primary or secondary agent) then obviously its Dispatcher is NAFMAN-enabled
			// but we have to insert this get-out clause for internal handlers because the dsptch.nafman field is not set until
			// after the Agent constructor returns, and that's where they call this method from.
			if (!(handler instanceof NafManAgent)) {
				return false;
			}
		}
		if (!active_commands.containsKey(cmdcode)) {
			dsptch.getLogger().warn("NAFMAN discarding undefined cmd="+cmdcode+" for handler="+dsptch.getName()+"/"+handler.getClass().getName());
			return false;
		}
		java.util.ArrayList<CommandHandlerReg> lst = cmd_handlers.get(cmdcode);
		if (lst != null) {
			// this list can hold multiple unconditional (zero-preference) handlers, or one conditional one
			for (int idx = 0; idx != lst.size(); idx++) {
				CommandHandlerReg def = lst.get(idx);
				if (dsptch == def.dsptch && handler == def.handler) {
					// there are valid reasons for this, eg. multiple listeners with same server
					dsptch.getLogger().warn("Dispatcher="+dsptch.getName()+" skipping NAFMAN registration cmd="+cmdcode+"/"+handler+" due to existing - "+def.handler);
					return false;
				}
				if (pref == 0) {
					if (def.pref == 0) continue; //new handler can co-exist with existing one
				} else {
					if (pref >= def.pref) {
						dsptch.getLogger().trace("NAFMAN cmd="+cmdcode+": Existing "+def+" has higher priority than "
								+handler.getClass().getName()+"/"+dsptch.getName()+"/pref="+pref);
						return false; //existing handler has higher priority
					}
				}
				// new handler has higher priority, so displace existing one
				if (inviolate_handlers.contains(def.dsptch)) {
					dsptch.getLogger().trace("NAFMAN cmd="+cmdcode+": Permanent "+def+" supercedes "
							+handler.getClass().getName()+"/"+dsptch.getName()+"/pref="+pref);
					return false; //but existing handler has been marked permanent
				}
				dsptch.getLogger().trace("NAFMAN cmd="+cmdcode+": "+handler.getClass().getName()+"/"+dsptch.getName()+"/pref="+pref
						+" replaces "+def);
				lst.remove(idx);
				break; //any further handlers can only be unconditional ones, so can co-exist
			}
		} else {
			lst = new java.util.ArrayList<>();
			cmd_handlers.put(cmdcode, lst);
		}
		CommandHandlerReg def = new CommandHandlerReg(handler, dsptch, pref);
		lst.add(def);
		dsptch.getLogger().trace("Dispatcher="+dsptch.getName()+" registered NAFMAN cmd="+cmdcode+" handler - "+def);
		return true;
	}

	synchronized void getHandlers(Dispatcher d, HashedMap<String, java.util.ArrayList<NafManCommand.Handler>> h)
	{
		java.util.Iterator<String> it = cmd_handlers.keySet().iterator();
		while (it.hasNext()) {
			String cmdcode = it.next();
			java.util.ArrayList<CommandHandlerReg> reglst = cmd_handlers.get(cmdcode);
			for (int idx = 0; idx != reglst.size(); idx++) {
				CommandHandlerReg def = reglst.get(idx);
				if (def.dsptch != d) continue;
				java.util.ArrayList<NafManCommand.Handler> dlst = h.get(cmdcode);
				if (dlst == null) {
					dlst = new java.util.ArrayList<>();
					h.put(cmdcode, dlst);
				}
				dlst.add(def.handler);
			}
		}
		inviolate_handlers.add(d); //now that we've propagated its handlers, this Dispatcher becomes inviolate
	}

	synchronized public java.util.List<DefCommand> getMatchingCommands(String stem)
	{
		java.util.ArrayList<DefCommand> lst = new java.util.ArrayList<>();
		stem = stem.toLowerCase();
		java.util.Iterator<String> it = cmd_handlers.keySet().iterator();
		while (it.hasNext()) {
			String cmdcode = it.next();
			if (!cmdcode.toLowerCase().startsWith(stem)) continue;
			DefCommand def = active_commands.get(cmdcode);
			if (def != null) lst.add(def);
		}
		return lst;
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
					sb.append("<handler dispatcher=\"").append(hdef.dsptch.getName());
					sb.append("\" hid=\"").append(hdef.handler.nafmanHandlerID());
					sb.append("\" pref=\"").append(hdef.pref).append("\">");
					sb.append(hdef.handler.getClass().getName()).append("</handler>");
				} else {
					sb.append(dlm).append(hdef.dsptch.getName()).append('/').append(hdef.handler.getClass().getName());
					sb.append("/pref=").append(hdef.pref);
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