/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;
import com.grey.logging.Logger;

public abstract class Agent
	implements Command.Handler
{
	public final com.grey.naf.reactor.Dispatcher dsptch;
	protected final com.grey.base.utils.HashedMap<String, java.util.ArrayList<Command.Handler>> handlers;
	protected boolean in_shutdown;
	//temp objects pre-allocated merely for efficiency
	private final StringBuilder sbtmp = new StringBuilder();

	public abstract boolean isPrimary();
	public abstract Primary getPrimary();
	public abstract int getPort();
	public abstract void stop();

	protected Agent(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg) throws com.grey.base.ConfigException
	{
		dsptch = d;
		handlers = new com.grey.base.utils.HashedMap<String, java.util.ArrayList<Command.Handler>>();
		Registry reg = Registry.get();
		reg.registerHandler(Registry.CMD_STOP, 0, this, dsptch);
		reg.registerHandler(Registry.CMD_DSHOW, 0, this, dsptch);
		reg.registerHandler(Registry.CMD_FLUSH, 0, this, dsptch);
		reg.registerHandler(Registry.CMD_LOGLVL, 0, this, dsptch);
		reg.registerHandler(Registry.CMD_KILLCONN, 0, this, dsptch);
	}

	public void start() throws com.grey.base.FaultException, java.io.IOException
	{
		Registry.get().getHandlers(dsptch, handlers);
		dsptch.logger.info("NAFMAN Agent="+dsptch.name+" registered handlers="+handlers.size()+": "+handlers.keySet());
	}

	protected void commandReceived(Command cmd) throws com.grey.base.FaultException, java.io.IOException
	{
		// See Primary's calls to Command.detach() to see why we might find ourselves unattached here.
		// Very unlikely, but not impossible, especially during shutdown.
		if (!cmd.attached(this)) {
			return;
		}

		try {
			processCommand(cmd);
		} finally {
			if (cmd.detach(this) == Command.DETACH_FINAL) getPrimary().commandCompleted(cmd);
		}
	}

	protected void processCommand(Command cmd) throws com.grey.base.FaultException, java.io.IOException
	{
		java.util.ArrayList<Command.Handler> lst = handlers.get(cmd.def.code);
		sbtmp.setLength(0);
		sbtmp.append("NAFMAN=").append(dsptch.name).append(" received command=").append(cmd.def.code);
		if (lst == null) {
			sbtmp.append(" - no Handlers");
			dsptch.logger.log(Logger.LEVEL.TRC3, sbtmp);
		} else {
			sbtmp.append(" - Handlers=").append(lst.size());
			for (int idx = 0; idx != lst.size(); idx++) {
				Command.Handler handler = lst.get(idx);
				sbtmp.append(idx==0?": ":", ").append(handler.getClass().getName());
				dsptch.logger.log(Logger.LEVEL.TRC2, sbtmp);
				CharSequence rsp = handler.handleNAFManCommand(cmd);
				if (rsp != null && rsp.length() != 0) cmd.addHandlerResponse(dsptch, handler, rsp);
			}
		}
	}

	@Override
	public CharSequence handleNAFManCommand(Command cmd) throws java.io.IOException
	{
		sbtmp.setLength(0);

		if (cmd.def.code.equals(Registry.CMD_STOP)) {
			boolean done = stopDispatcher();
			sbtmp.append("Dispatcher is ").append(done ? "halted" : "halting");
		} else if (cmd.def.code.equals(Registry.CMD_DLIST)) {
			listDispatchers(sbtmp);
		} else if (cmd.def.code.equals(Registry.CMD_DSHOW)) {
			dsptch.dumpState(sbtmp, StringOps.stringAsBool(cmd.getArg(Command.ATTR_VERBOSE)));
		} else if (cmd.def.code.equals(Registry.CMD_KILLCONN)) {
			String val = cmd.getArg(Command.ATTR_TIME);
			int id = Integer.parseInt(cmd.getArg(Command.ATTR_KEY));
			long stime = (val == null ? 0 : Long.parseLong(val));
			boolean done = dsptch.killConnection(id, stime, "Killed via NAFMAN");
			sbtmp.append("Connection ID=").append(id).append(' ');
			sbtmp.append(done ? "has been terminated" : "is no longer registered");
		} else if (cmd.def.code.equals(Registry.CMD_FLUSH)) {
			dsptch.flusher.flushAll();
			sbtmp.append("Logs have been flushed");
		} else if (cmd.def.code.equals(Registry.CMD_LOGLVL)) {
			String arg = cmd.getArg(Command.ATTR_LOGLVL);
			Logger.LEVEL newlvl = null;
			try {
				newlvl = Logger.LEVEL.valueOf(arg.toUpperCase());
			} catch (Exception ex) {
				dsptch.logger.info("NAFMAN discarding "+cmd.def.code+" command for bad level="+arg+" - "+ex);
				return null;
			}
			Logger.LEVEL oldlvl = dsptch.logger.setLevel(newlvl);
			sbtmp.append("Log level has been changed from ").append(oldlvl).append(" to ").append(newlvl);
		} else if (cmd.def.code.equals(Registry.CMD_SHOWCMDS)) {
			Registry.get().dumpState(sbtmp, true);
		} else if (cmd.def.code.equals(Registry.CMD_APPSTOP)) {
			String dname = cmd.getArg(Command.ATTR_DISPATCHER);
			String naflet = cmd.getArg(Command.ATTR_NAFLET);
			if (naflet == null ||  naflet.length() == 0 || naflet.equals("-")) return null;
			com.grey.naf.reactor.Dispatcher d = (dname == null ? null : com.grey.naf.reactor.Dispatcher.getDispatcher(dname));
			if (d == null) {
				sbtmp.append("Unrecognised Dispatcher="+dname);
			} else {
				d.unloadNaflet(naflet);
				sbtmp.append("NAFlet=").append(naflet).append(" has been told to stop");
			}
		} else {
			//we've obviously registered to handle this command, so missing If clause is a bug
			dsptch.logger.error("NAFMAN="+dsptch.name+": Missing case for cmd="+cmd.def.code);
			return null;
		}
		return sbtmp;
	}

	public CharSequence listDispatchers()
	{
		sbtmp.setLength(0);
		listDispatchers(sbtmp);
		return sbtmp;
	}

	private void listDispatchers(StringBuilder sb)
	{
		com.grey.naf.reactor.Dispatcher[] lst = com.grey.naf.reactor.Dispatcher.getDispatchers();
		sb.append("<dispatchers>");
		for (int idx = 0; idx != lst.length; idx++) {
			com.grey.naf.reactor.Dispatcher d = lst[idx];
			com.grey.naf.Naflet[] apps = d.listNaflets();
			String nafman = "No";
			if (d.nafman != null) nafman = (d.nafman.isPrimary() ? "Primary" : "Secondary");
			sb.append("<dispatcher name=\"").append(d.name);
			sb.append("\" log=\"").append(d.logger.getLevel());
			sb.append("\" nafman=\"").append(nafman);
			sb.append("\" dns=\"").append(d.dnsresolv == null ? "No" : d.dnsresolv).append("\">");
			sb.append("<naflets>");
			for (int idx2 = 0; idx2 != apps.length; idx2++) {
				com.grey.naf.Naflet app = apps[idx2];
				sb.append("<naflet name=\"").append(app.naflet_name).append("\"/>");
			}
			sb.append("</naflets>");
			sb.append("</dispatcher>");
		}
		sb.append("</dispatchers>");
	}

	// signal our Dispatcher to stop, and it will in turn stop us when it shuts down
	protected boolean stopDispatcher()
	{
		if (in_shutdown) return true;  //Dispatcher has already told us to stop
		return dsptch.stop();
	}
}