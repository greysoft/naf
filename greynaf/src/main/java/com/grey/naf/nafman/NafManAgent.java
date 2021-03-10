/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;
import com.grey.base.collections.HashedMap;
import com.grey.naf.Naflet;
import com.grey.naf.reactor.Dispatcher;
import com.grey.logging.Logger;

public abstract class NafManAgent
	implements NafManCommand.Handler
{
	private final Dispatcher dsptch;
	private final NafManRegistry registry;
	private final HashedMap<String, java.util.ArrayList<NafManCommand.Handler>> handlers = new HashedMap<>();
	private boolean in_shutdown;

	//temp objects pre-allocated merely for efficiency
	private final StringBuilder sbtmp = new StringBuilder();

	public abstract PrimaryAgent getPrimary();
	public abstract int getPort();
	public abstract void stop();

	@Override
	public CharSequence nafmanHandlerID() {return "Agent";}

	public boolean isPrimary() {return (this == getPrimary());}
	public Dispatcher getDispatcher() {return dsptch;}
	public NafManRegistry getRegistry() {return registry;}
	protected void setShutdown() {in_shutdown = true;}

	protected NafManAgent(Dispatcher d, NafManRegistry reg)
	{
		dsptch = d;
		registry = reg;
		reg.registerHandler(NafManRegistry.CMD_STOP, 0, this, dsptch);
		reg.registerHandler(NafManRegistry.CMD_DSHOW, 0, this, dsptch);
		reg.registerHandler(NafManRegistry.CMD_FLUSH, 0, this, dsptch);
		reg.registerHandler(NafManRegistry.CMD_LOGLVL, 0, this, dsptch);
		reg.registerHandler(NafManRegistry.CMD_KILLCONN, 0, this, dsptch);
	}

	public void start() throws java.io.IOException
	{
		getRegistry().getHandlers(dsptch, handlers);
		dsptch.getLogger().info("NAFMAN Agent="+dsptch.getName()+" registered handlers="+handlers.size()+": "+handlers.keySet());
	}

	protected void commandReceived(NafManCommand cmd) throws java.io.IOException
	{
		// See Primary's calls to Command.detach() to see why we might find ourselves unattached here.
		// Very unlikely, but not impossible, especially during shutdown.
		if (!cmd.attached(this)) {
			return;
		}

		try {
			processCommand(cmd);
		} finally {
			if (cmd.detach(this) == NafManCommand.DETACH_FINAL) {
				PrimaryAgent primary = getPrimary();
				if (primary != null) primary.commandCompleted(cmd);
			}
		}
	}

	protected void processCommand(NafManCommand cmd) throws java.io.IOException
	{
		NafManRegistry.DefCommand def = cmd.getCommandDef();
		java.util.ArrayList<NafManCommand.Handler> lst = handlers.get(def.code);
		sbtmp.setLength(0);
		sbtmp.append("NAFMAN=").append(dsptch.getName()).append(" received command=").append(def.code);
		if (lst == null) {
			sbtmp.append(" - no Handlers");
			dsptch.getLogger().log(Logger.LEVEL.INFO, sbtmp);
		} else {
			Logger.LEVEL lvl = Logger.LEVEL.TRC2;
			if (dsptch.getLogger().isActive(lvl)) {
				sbtmp.append(" - Handlers=").append(lst.size()).append('/').append(lst);
				dsptch.getLogger().log(lvl, sbtmp);
			}
			for (int idx = 0; idx != lst.size(); idx++) {
				NafManCommand.Handler handler = lst.get(idx);
				CharSequence rsp = handler.handleNAFManCommand(cmd);
				if (rsp != null && rsp.length() != 0) cmd.addHandlerResponse(dsptch, handler, rsp);
			}
		}
	}

	@Override
	public CharSequence handleNAFManCommand(NafManCommand cmd) throws java.io.IOException
	{
		NafManRegistry.DefCommand def = cmd.getCommandDef();
		sbtmp.setLength(0);

		if (def.code.equals(NafManRegistry.CMD_STOP)) {
			boolean done = stopDispatcher();
			sbtmp.append("Dispatcher is ").append(done ? "halted" : "halting");
		} else if (def.code.equals(NafManRegistry.CMD_DLIST)) {
			listDispatchers(sbtmp);
		} else if (def.code.equals(NafManRegistry.CMD_DSHOW)) {
			dsptch.dumpState(sbtmp, StringOps.stringAsBool(cmd.getArg(NafManCommand.ATTR_VERBOSE)));
		} else if (def.code.equals(NafManRegistry.CMD_KILLCONN)) {
			String val = cmd.getArg(NafManCommand.ATTR_TIME);
			int id = Integer.parseInt(cmd.getArg(NafManCommand.ATTR_KEY));
			long stime = (val == null ? 0 : Long.parseLong(val));
			boolean done = dsptch.killConnection(id, stime, "Killed via NAFMAN");
			sbtmp.append("Connection ID=").append(id).append(' ');
			sbtmp.append(done ? "has been terminated" : "is no longer registered");
		} else if (def.code.equals(NafManRegistry.CMD_FLUSH)) {
			dsptch.getFlusher().flushAll();
			sbtmp.append("Logs have been flushed");
		} else if (def.code.equals(NafManRegistry.CMD_LOGLVL)) {
			String arg = cmd.getArg(NafManCommand.ATTR_LOGLVL);
			Logger.LEVEL newlvl = null;
			try {
				newlvl = Logger.LEVEL.valueOf(arg.toUpperCase());
			} catch (Exception ex) {
				dsptch.getLogger().info("NAFMAN discarding "+def.code+" command for bad level="+arg+" - "+ex);
				return null;
			}
			Logger.LEVEL oldlvl = dsptch.getLogger().setLevel(newlvl);
			sbtmp.append("Log level has been changed from ").append(oldlvl).append(" to ").append(newlvl);
		} else if (def.code.equals(NafManRegistry.CMD_SHOWCMDS)) {
			getRegistry().dumpState(sbtmp, true);
		} else if (def.code.equals(NafManRegistry.CMD_APPSTOP)) {
			String dname = cmd.getArg(NafManCommand.ATTR_DISPATCHER);
			String naflet = cmd.getArg(NafManCommand.ATTR_NAFLET);
			if (naflet == null ||  naflet.length() == 0 || naflet.equals("-")) return null;
			Dispatcher d = (dname == null ? null : dsptch.getApplicationContext().getDispatcher(dname));
			if (d == null) {
				sbtmp.append("Unrecognised Dispatcher="+dname);
			} else {
				d.unloadNaflet(naflet);
				sbtmp.append("NAFlet=").append(naflet).append(" has been told to stop");
			}
		} else {
			//we've obviously registered to handle this command, so missing If clause is a bug
			dsptch.getLogger().error("NAFMAN="+dsptch.getName()+": Missing case for cmd="+def.code);
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
		sb.append("<dispatchers>");
		for (Dispatcher d : dsptch.getApplicationContext().getDispatchers()) {
			Naflet[] apps = d.listNaflets();
			String nafman = "No";
			if (d.getNafManAgent() != null) nafman = (d.getNafManAgent().isPrimary() ? "Primary" : "Secondary");
			sb.append("<dispatcher name=\"").append(d.getName());
			sb.append("\" log=\"").append(d.getLogger().getLevel());
			sb.append("\" nafman=\"").append(nafman);
			sb.append("\" dns=\"").append(d.getResolverDNS() == null ? "No" : d.getResolverDNS()).append("\">");
			sb.append("<naflets>");
			for (int idx2 = 0; idx2 != apps.length; idx2++) {
				Naflet app = apps[idx2];
				sb.append("<naflet name=\"").append(app.getName()).append("\"/>");
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

	@Override
	public String toString() {
		// guard against null fields during initialisation
		String dname = (getDispatcher() == null ? null : getDispatcher().getName());
		Dispatcher pd = (getPrimary() == null ? null : getPrimary().getDispatcher());
		return super.toString()+" for Dispatcher="+dname+" with primary="+(pd==null?null:pd.getName());
	}
}