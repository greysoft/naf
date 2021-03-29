/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.grey.base.config.SysProps;
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
	public static final String CMD_FLUSH = "FLUSH";
	public static final String CMD_LOGLVL = "LOGLEVEL";

	//some of the built-in Resource names (those that get referenced from elsewhere)
	public static final String RSRC_PLAINTEXT = "plaintext"; //pseudo resource name, indicating no XSL stylesheet
	public static final String RSRC_CMDSTATUS = "cmdstatus";
	private static final String RSRC_CMDREG = "cmdreg";

	private static final String FAMILY_NAFCORE = "NAF-Core";

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

	public static class CommandFamily
	{
		private CommandFamily(NafManRegistry reg, DefCommand[] commands, DefResource[] resources, String homePage) {
			if (commands != null) reg.loadCommands(commands);
			if (resources != null) reg.loadResources(resources);
			if (homePage != null) reg.setHomePage(homePage);
		}
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
		new DefCommand(CMD_LOGLVL, FAMILY_NAFCORE, "Dynamically alter the logging level", null, false)
	};

	private static final DefResource[] nafresources = new DefResource[] {
		new DefResource("nafhome", "home.xsl", null, null), //must be first entry, subsequent order doesn't matter
		new DefResource("favicon.ico", "favicon.png", HTTP.CTYPE_PNG, null),
		new DefResource("nafman.css", "nafman.css", HTTP.CTYPE_CSS, null),
		new DefResource(RSRC_CMDSTATUS, "cmdstatus.xsl", null, null),
		new DefResource("dspdetails", "dspdetails.xsl", null, null),
		new DefResource(RSRC_CMDREG, "cmdreg.xsl", null, null)
	};

	private final Map<String, CommandFamily> commandFamilies = new ConcurrentHashMap<>();
	private final Map<String, List<CommandHandlerReg>> commandHandlers = new ConcurrentHashMap<>();
	private final Map<String, DefCommand> activeCommands = new ConcurrentHashMap<>();
	private final Map<String, DefResource> activeResources = new ConcurrentHashMap<>();
	private final Set<Dispatcher> inviolateHandlers = Collections.synchronizedSet(new HashSet<>()); //handlers that can't be replaced by another Dispatcher
	private volatile String homePage;

	String getHomePage() {return homePage;}
	DefCommand getCommand(String code) {return activeCommands.get(code);}
	Collection<DefCommand> getCommands() {return activeCommands.values();}
	DefResource getResource(String name) {return activeResources.get(name);}
	Set<String> getResourceNames() {return activeResources.keySet();}

	public boolean isCommandRegistered(String code) {return isCommandRegistered(code, null);}

	public static NafManRegistry get(ApplicationContextNAF appctx) {
		return appctx.getNamedItem(NafManRegistry.class.getName(), () -> new NafManRegistry(appctx));
	}

	private NafManRegistry(ApplicationContextNAF appctx) {
		registerCommandFamily(FAMILY_NAFCORE, nafcmds, nafresources, nafresources[0].name);
	}

	public void registerCommandFamily(String family, DefCommand[] commands, DefResource[] resources, String home) {
		commandFamilies.computeIfAbsent(family, (k) -> new CommandFamily(this, commands, resources, home));
	}

	// Supports commands whose name is not statically defined, and is only known at runtime
	public boolean registerDynamicCommand(String cmdcode, NafManCommand.Handler handler, Dispatcher dsptch,
				String family, String autopub, boolean rdonly, String descr) {
		if (activeCommands.containsKey(cmdcode)) return false;
		DefCommand def = new DefCommand(cmdcode, family, descr, autopub, rdonly);
		loadCommand(def);
		return registerHandler(cmdcode, 0, handler, dsptch);
	}

	void loadCommands(DefCommand[] defs) {
		for (int idx = 0; idx != defs.length; idx++) {
			loadCommand(defs[idx]);
		}
	}

	// Duplicate commands are discarded
	private void loadCommand(DefCommand def) {
		if (activeCommands.containsKey(def.code)) {
			if (THROW_ON_DUP) throw new NAFConfigException("NAFMAN: Duplicate cmd="+def.code+" - "+def);
			return;
		}
		if (activeResources.containsKey(def.code)) throw new NAFConfigException("NAFMAN: Command="+def.code+" conflicts with Resources");
		activeCommands.put(def.code, def);
	}

	// The NAFMAN server looks up the URL path as first a Command code and then a Resource name, so their IDs must be disjoint.
	// Unlike Commands, duplicate Resources override the original.
	void loadResources(DefResource[] defs) {
		for (int idx = 0; idx != defs.length; idx++) {
			DefResource def = defs[idx];
			if (activeCommands.containsKey(def.name)) throw new NAFConfigException("NAFMAN: Resource="+def.path+" conflicts with cmd="+def.name);
			activeResources.put(def.name, def);
		}
	}

	boolean isCommandRegistered(String code, Dispatcher d) {
		List<CommandHandlerReg> lst = commandHandlers.get(code);
		if (lst == null) return false;
		if (d == null) return true;  //just wanted to know if it was registered by anybody
		synchronized (lst) {
			for (CommandHandlerReg reg : lst) {
				if (reg.dsptch == d) return true;
			}
		}
		return false;
	}

	void setHomePage(String rsrc_name) {
		if (getResource(rsrc_name) == null) throw new NAFConfigException("NAFMAN: Unknown homepage="+rsrc_name);
		homePage = rsrc_name;
	}

	// Register handlers for various commands.
	// Some commands can have multiple handlers, while others can only have one, which is regulated by the preference
	// arg - lower values indicate higher priority.
	// A non-zero preference indicates a conditional registration, which will be blocked or displaced by higher-priority ones.
	// pref==0 means this is an unconditional registration, which will co-exist with any other unconditional registrations, but
	// evict any conditional ones.
	// All else being equal, later registrations within same Dispatcher supercede earlier ones.
	public boolean registerHandler(String cmdcode, int pref, NafManCommand.Handler handler, Dispatcher dsptch) {
		if (dsptch.getNafManAgent() == null) {
			// If this is an internal NAFMAN handler (primary or secondary agent) then obviously its Dispatcher is NAFMAN-enabled
			// but we have to insert this get-out clause for internal handlers because the dsptch.nafman field is not set until
			// after the Agent constructor returns, and that's where they call this method from.
			if (!(handler instanceof NafManAgent)) {
				return false;
			}
		}
		if (!activeCommands.containsKey(cmdcode)) {
			dsptch.getLogger().warn("NAFMAN discarding undefined cmd="+cmdcode+" for handler="+dsptch.getName()+"/"+handler.getClass().getName());
			return false;
		}
		List<CommandHandlerReg> lst = commandHandlers.computeIfAbsent(cmdcode, (c) -> new ArrayList<>());
		CommandHandlerReg newReg;

		synchronized (lst) {
			// this list can hold multiple unconditional (zero-preference) handlers, or one conditional one
			for (CommandHandlerReg reg : lst) {
				if (dsptch == reg.dsptch && handler == reg.handler) {
					// there are valid reasons for this, eg. multiple listeners with same server
					dsptch.getLogger().warn("Dispatcher="+dsptch.getName()+" skipping NAFMAN registration cmd="+cmdcode+"/"+handler+" due to existing - "+reg.handler);
					return false;
				}
				if (pref == 0) {
					if (reg.pref == 0) continue; //new handler can co-exist with existing one
				} else {
					if (pref >= reg.pref) {
						dsptch.getLogger().trace("NAFMAN cmd="+cmdcode+": Existing "+reg+" has higher priority than "
								+handler.getClass().getName()+"/"+dsptch.getName()+"/pref="+pref);
						return false; //existing handler has higher priority
					}
				}
				// new handler has higher priority, so displace existing one
				if (inviolateHandlers.contains(reg.dsptch)) {
					dsptch.getLogger().trace("NAFMAN cmd="+cmdcode+": Permanent "+reg+" supercedes "
							+handler.getClass().getName()+"/"+dsptch.getName()+"/pref="+pref);
					return false; //but existing handler has been marked permanent
				}
				dsptch.getLogger().trace("NAFMAN cmd="+cmdcode+": "+handler.getClass().getName()+"/"+dsptch.getName()+"/pref="+pref+" replaces "+reg);
				lst.remove(reg);
				break; //any further handlers can only be unconditional ones, so can co-exist
			}
			newReg = new CommandHandlerReg(handler, dsptch, pref);
			lst.add(newReg);
		}
		dsptch.getLogger().trace("Dispatcher="+dsptch.getName()+" registered NAFMAN cmd="+cmdcode+" handler - "+newReg);
		return true;
	}

	void getHandlers(Dispatcher dsptch, Map<String, List<NafManCommand.Handler>> handlers) {
		List<String> cmdCodes = new ArrayList<>(commandHandlers.keySet());
		for (String cmdcode : cmdCodes) {
			List<CommandHandlerReg> reglst = commandHandlers.get(cmdcode);
			if (reglst == null) continue;
			synchronized (reglst) {
				for (CommandHandlerReg reg : reglst) {
					if (reg.dsptch != dsptch) continue;
					List<NafManCommand.Handler> dlst = handlers.computeIfAbsent(cmdcode, (c) -> new ArrayList<>());
					dlst.add(reg.handler);
				}
			}
		}
		inviolateHandlers.add(dsptch); //now that we've propagated its handlers, this Dispatcher becomes inviolate
	}

	public List<DefCommand> getMatchingCommands(String stem) {
		List<DefCommand> lst = new ArrayList<>();
		List<String> cmdCodes = new ArrayList<>(commandHandlers.keySet());
		for (String cmdcode : cmdCodes) {
			if (!cmdcode.toLowerCase().startsWith(stem)) continue;
			DefCommand def = activeCommands.get(cmdcode);
			if (def != null) lst.add(def);
		}
		return lst;
	}

	public CharSequence dumpState(StringBuilder sb, boolean xml) {
		if (sb == null) sb = new StringBuilder();
		Set<String> cmdCodes = new HashSet<>(commandHandlers.keySet());
		List<String> activeCodes = new ArrayList<>(activeCommands.keySet());
		int hcnt = 0;

		if (xml) {
			sb.append("<commands>");
		} else {
			sb.append("NAFMAN registered commands=").append(cmdCodes.size()).append('/').append(activeCodes.size());
		}

		for (String cmdcode : cmdCodes) {
			List<CommandHandlerReg> lst = commandHandlers.get(cmdcode);
			if (lst == null) continue;
			DefCommand cdef = getCommand(cmdcode);
			synchronized (lst) {
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
				for (CommandHandlerReg hdef : lst) {
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
			}
			if (xml) sb.append("</handlers></command>");
		}

		if (xml) {
			sb.append("</commands>");
		} else {
			sb.append("\nTotal handlers=").append(hcnt);
			if (cmdCodes.size() != activeCodes.size()) {
				sb.append("\nUnused Commands");
				String dlm = ": ";
				for (String cmdcode : activeCodes) {
					if (cmdCodes.contains(cmdcode)) continue;
					sb.append(dlm).append(cmdcode);
					dlm = ", ";
				}
			}
			sb.append("\nNAFMAN registered resources=").append(activeResources.size()).append(", Home=").append(getHomePage());
		}
		return sb;
	}
}