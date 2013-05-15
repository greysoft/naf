/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.TSAP;
import com.grey.naf.BufferSpec;
import com.grey.naf.DispatcherDef;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Listener;
import com.grey.naf.reactor.ConcurrentListener;

public class App
	extends com.grey.naf.Launcher
{
	private static final String[] opts = new String[]{"udp", "server", "server-solo", "clients:", "msg:", "cbuf:", "sbuf:", "sockbuf:", "verify"};

	public static void main(String[] args) throws Exception
	{
		App app = new App(args);
		app.exec();
	}

	private static class OptsHandler
		extends com.grey.base.utils.CommandParser.OptionsHandler
	{
		boolean udpmode;
		boolean server_enabled;
		boolean server_solo; //true means standalone server, ie. has own exclusive Dispatcher
		int cgrpcnt = 1;
		int cgrpsiz = 0;
		int msgcnt = 1;
		int msgsiz = 4 * 1024;
		int cxmtbuf = msgsiz;
		int crcvbuf = cxmtbuf;
		int sxmtbuf = cxmtbuf;
		int srcvbuf = crcvbuf;
		int sockbufsiz = 64 * 1024;
		String msgpath;
		boolean verify;

		public OptsHandler() {super(opts, 1, 1);}

		@Override
		public void setOption(String opt) {
			if (opt.equals("udp")) {
				udpmode = true;
			} else if (opt.equals("server")) {
				server_enabled = true;
			} else if (opt.equals("server-solo")) {
				server_enabled = true;
				server_solo = true;
			} else if (opt.equals("verify")) {
				verify = true;
			} else {
				super.setOption(opt);
			}
		}

		@Override
		public void setOption(String opt, String val) {
			String[] parts;
			if (opt.equals("clients")) {
				parts = val.split(":");
				cgrpsiz = Integer.parseInt(parts[0]);
				if (parts.length != 1) cgrpcnt = Integer.parseInt(parts[1]);
			} else if (opt.equals("msg")) {
				parts = val.split(":");
				msgcnt = Integer.parseInt(parts[0]);
				if (parts[1].indexOf('/') == -1) {
					msgsiz = Integer.parseInt(parts[1]);
				} else {
					msgpath = parts[1];
				}
			} else if (opt.equals("cbuf")) {
				parts = val.split(":");
				crcvbuf = Integer.parseInt(parts[0]);
				cxmtbuf = Integer.parseInt(parts[1]);
			} else if (opt.equals("sbuf")) {
				parts = val.split(":");
				srcvbuf = Integer.parseInt(parts[0]);
				sxmtbuf = Integer.parseInt(parts[1]);
			} else if (opt.equals("sockbuf")) {
				sockbufsiz = Integer.parseInt(val);
			} else {
				super.setOption(opt, val);
			}
		}

		@Override
		public String displayUsage()
		{
			String txt = "\t-udp -server[-solo] -clients num[:groups] -msg msgcnt:msgsiz -cbuf rcv:xmt -sbuf rcv:xmt -sockbuf siz -verify address-spec";
			txt += "\nAll the params are optional, apart from the address-spec param, but at least one of -server or -clients must be specified";
			return txt;
		}
	}

	private final OptsHandler options = new OptsHandler();
	public BufferSpec sbufspec;
	private Dispatcher dserver;
	private int cgrpcnt;

	public App(String[] args) throws com.grey.base.GreyException, java.io.IOException
	{
		super(args);
		com.grey.base.utils.PkgInfo.announceJAR(getClass(), "echobot", null);
		cmdParser.addHandler(options);
	}

	@Override
	protected void appExec(int param1) throws com.grey.base.GreyException, java.io.IOException
	{
		if (!options.server_enabled && options.cgrpsiz == 0) {
			cmdParser.usage(cmdlineArgs, "Must specify whether client and/or server mode");
			return;
		}
		int arg = param1;
		String hostport = cmdlineArgs[arg++];

		if (options.udpmode) {
			int max = 512;
			if (options.msgsiz > max) options.msgsiz = max;
			if (options.crcvbuf > max) options.crcvbuf = max;
			if (options.cxmtbuf > max) options.cxmtbuf = max;
			if (options.srcvbuf > max) options.srcvbuf = max;
			if (options.sxmtbuf > max) options.sxmtbuf = max;
		}
		com.grey.logging.Logger bootlog = com.grey.logging.Factory.getLogger(baseOptions.logname);
		if (options.server_enabled && options.cgrpsiz == 0) options.server_solo = true;
		int dcnt = (options.server_solo ? options.cgrpcnt + 1 : options.cgrpcnt);
		DispatcherDef def = new DispatcherDef();
		Dispatcher[] cdispatchers = new Dispatcher[dcnt];
		ClientGroup[] cgroups = new ClientGroup[options.cgrpcnt];
		TSAP tsap = TSAP.build(hostport, 0);
		sbufspec = (options.server_enabled ? new BufferSpec(options.srcvbuf, options.sxmtbuf, true) : null);
		byte[] msgbuf = null;

		if (options.cgrpsiz != 0) {
			if (options.msgpath == null) {
				msgbuf = new byte[options.msgsiz];
				for (int idx = 0; idx != options.msgsiz; idx++) {
					msgbuf[idx] = (byte)('A' + (idx % 26));
				}
			} else {
				msgbuf = FileOps.read(options.msgpath, -1, null);
				options.msgsiz = msgbuf.length;
			}
		}
		if (options.server_enabled) System.out.println("Launching "+(options.server_solo?"stand-alone ":"")+"server");
		if (options.cgrpsiz != 0) {
			System.out.println("Launching clients="+(options.cgrpsiz * options.cgrpcnt)+" within Dispatchers="+options.cgrpcnt);
			System.out.println("Messages = "+options.msgcnt+"x "+options.msgsiz+(options.msgpath==null ? "" : " - "+options.msgpath));
		}
		System.out.println("Transport = "+(options.udpmode ? "UDP" : "TCP"));
		System.out.println("Buffers: Direct="+BufferSpec.directniobufs+", C="+options.crcvbuf+"/"+options.cxmtbuf
				+", S="+options.srcvbuf+"/"+options.sxmtbuf+", UDP="+options.sockbufsiz);
		int cgnum = 0;

		// create the Dispatchers and initialise their callback apps
		for (int idx = 0; idx != dcnt; idx++) {
			def.name = (options.server_enabled && idx == 0 ? "DS" : "");  //server resides in first Dispatcher
			if (options.cgrpsiz != 0 && (!options.server_solo || idx != 0)) def.name += "DC"+(cgnum+1); //this Dispatcher hosts clients
			cdispatchers[idx] = Dispatcher.create(def, null, bootlog);
			Dispatcher dsptch = cdispatchers[idx];

			if (idx == 0) {
				if (options.server_enabled) {
					dserver = dsptch;
					if (options.udpmode) {
						new ServerUDP(this, dsptch, tsap, sbufspec, options.sockbufsiz);
					} else {
						java.util.Map<String,Object> settings = new java.util.HashMap<String,Object>();
						settings.put(Listener.CFGMAP_CLASS, ServerTCP.class);
						settings.put(Listener.CFGMAP_IFACE, tsap.dotted_ip.toString());
						settings.put(Listener.CFGMAP_PORT, tsap.port);
						settings.put(Listener.CFGMAP_BACKLOG, options.cgrpcnt * options.cgrpsiz);
						ConcurrentListener lstnr = new ConcurrentListener("EchoBot-"+dsptch.name, dsptch, this, null, null, settings);
						lstnr.start();
					}
					if (options.server_solo) continue;
				}
			}
			BufferSpec bufspec = new BufferSpec(options.crcvbuf, options.cxmtbuf, true);
			cgroups[cgnum++] = new ClientGroup(this, dsptch, options.udpmode, tsap, options.cgrpsiz, bufspec, msgbuf, options.msgcnt,
					options.sockbufsiz, options.verify);
		}
		cgrpcnt = options.cgrpcnt;
		System.out.println("EchoBot initialisation complete\n"+Dispatcher.dumpConfig());

		// start the Dispatchers
		for (int idx = 0; idx != dcnt; idx++) {
			cdispatchers[idx].start();
		}

		// by the time all the Dispatchers terminate, all these Joins will have completed
		for (int idx = 0; idx != dcnt; idx++) {
			cdispatchers[idx].waitStopped();
			bootlog.info("Dispatcher "+idx+"/"+dcnt+" has been reaped - "+cdispatchers[idx]);
		}

		// report stats
		int failcnt = 0;
		long durations_sum = 0;
		long min_duration = Long.MAX_VALUE;
		long max_duration = 0;
		for (int idx = 0; idx != options.cgrpcnt; idx++) {
			ClientGroup grp = cgroups[idx];
			for (int idx2 = 0; idx2 != grp.durations.size(); idx2++) {
				long duration = grp.durations.get(idx2).longValue();
				durations_sum += duration;
				if (duration < min_duration) min_duration = duration;
				if (duration > max_duration) max_duration = duration;
			}
			failcnt += grp.failcnt;
		}
		java.text.DecimalFormat fmt = new java.text.DecimalFormat("###,###.######");
		long numclients = (options.cgrpcnt * options.cgrpsiz) - failcnt;
		long numbytes = options.msgcnt * options.msgsiz;
		double avg_duration = (double)durations_sum / ((double)numclients * 1000d * 1000d * 1000d);  //express as seconds
		if (failcnt != 0) System.out.println("Failed clients = "+failcnt+"/"+(failcnt + numclients));
		if (numclients != 0) {
			System.out.println("Average session time = "+fmt.format(avg_duration)+"s"
					+" - (Min="+fmt.format((double)min_duration/(1000d * 1000d * 1000d))+"s"
					+", Max="+fmt.format((double)max_duration/(1000d * 1000d * 1000d))+"s"
					+")");
			System.out.println("Rate = "+fmt.format((8d * numbytes)/(1024d * 1024d * avg_duration))+" Mbps");
		}
	}

	// This method can be called from multiple Dispatcher threads, but the values it updates were
	// initially set in the non-MT initial-setup phase.
	public synchronized void terminated(ClientGroup cg) throws java.io.IOException
	{
		if (cg.dsptch != dserver) cg.dsptch.stop(cg.dsptch);
		if (--cgrpcnt == 0 && dserver != null) {
			dserver.stop(cg.dsptch);
			dserver = null;
		}
	}
}
