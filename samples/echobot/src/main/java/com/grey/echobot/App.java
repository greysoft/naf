/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.echobot;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.TSAP;
import com.grey.base.utils.CommandParser;
import com.grey.logging.Logger;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.BufferSpec;
import com.grey.naf.DispatcherDef;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;
import com.grey.naf.reactor.ConcurrentListener;

import org.slf4j.LoggerFactory;

public class App
	extends com.grey.naf.Launcher
{
	private static final org.slf4j.Logger Logger = LoggerFactory.getLogger(App.class);

	static final String[] opts = new String[]{"udp", "server", "server-solo", "clients:", "msg:", "cbuf:", "sbuf:", "sockbuf:", "verify"};
	static final int HDRSIZ = 0; //no message header is defined

	public static void main(String[] args) throws Exception
	{
		Logger.info("Starting EchoBot with logger="+Logger.getClass().getName()+"/"+Logger);
		App app = new App(args);
		app.execute("echobot");
	}

	private static class OptsHandler extends CommandParser.OptionsHandler
	{
		boolean udpmode;
		boolean server_enabled;
		boolean server_solo; //true means standalone server, ie. has own exclusive Dispatcher with no clients
		int cgrpcnt = 0; //no. of client groups, ie. number of client Dispatchers
		int cgrpsiz = 1; //no. of clients in each group
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
				cgrpcnt = Integer.parseInt(parts[0]);
				if (parts.length != 1) cgrpsiz = Integer.parseInt(parts[1]);
			} else if (opt.equals("msg")) {
				parts = val.split(":");
				msgcnt = Integer.parseInt(parts[0]);
				try {
					msgsiz = Integer.parseInt(parts[1]);
				} catch (NumberFormatException ex) {
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
			String txt = "\t-udp -server[-solo] -clients groups[:num] -msg msgcnt:msgsiz -cbuf rcv:xmt -sbuf rcv:xmt -sockbuf siz -verify host:port";
			txt += "\nAll the params are optional, apart from the address-spec param, but at least one of -server or -clients must be specified";
			return txt;
		}
	}

	private final OptsHandler options = new OptsHandler();
	public BufferSpec sbufspec;
	private Dispatcher dserver;
	private int cgrpcnt;

	public App(String[] args)
	{
		super(args);
		com.grey.base.utils.PkgInfo.announceJAR(getClass(), "echobot", null);
		cmdParser.addHandler(options);
	}

	@Override
	protected void appExecute(ApplicationContextNAF appctx, int param1, Logger bootlog) throws java.io.IOException
	{
		if (!options.server_enabled && options.cgrpcnt == 0) {
			cmdParser.usage(cmdlineArgs, "Must specify client and/or server mode");
			return;
		}
		if (options.cgrpcnt != 0 && options.cgrpsiz == 0) {
			cmdParser.usage(cmdlineArgs, "Client-group size cannot be zero");
			return;
		}
		int arg = param1;
		String hostport = cmdlineArgs[arg++];
		int maxuserdata = Integer.MAX_VALUE;

		if (options.udpmode) {
			maxuserdata = 512 - HDRSIZ;
			if (options.msgsiz > maxuserdata) options.msgsiz = maxuserdata;
			if (options.crcvbuf > maxuserdata) options.crcvbuf = maxuserdata;
			if (options.cxmtbuf > maxuserdata) options.cxmtbuf = maxuserdata;
			if (options.srcvbuf > maxuserdata) options.srcvbuf = maxuserdata;
			if (options.sxmtbuf > maxuserdata) options.sxmtbuf = maxuserdata;
		}
		if (options.server_enabled && options.cgrpcnt == 0) options.server_solo = true;
		int dcnt = (options.server_solo ? options.cgrpcnt + 1 : options.cgrpcnt);
		if (dcnt == 0) dcnt++; //need at least one Dispatcher for the server

		DispatcherDef.Builder dispatcherDefsBuilder = new DispatcherDef.Builder();
		Dispatcher[] cdispatchers = new Dispatcher[dcnt];
		ClientGroup[] cgroups = new ClientGroup[options.cgrpcnt];
		TSAP tsap = TSAP.build(hostport, 0, true);
		sbufspec = (options.server_enabled ? new BufferSpec(options.srcvbuf, options.sxmtbuf) : null);
		byte[] msgbuf = null;

		if (options.cgrpcnt != 0) {
			if (options.msgpath == null) {
				msgbuf = new byte[HDRSIZ+options.msgsiz];
				for (int idx = 0; idx != options.msgsiz; idx++) {
					msgbuf[idx+HDRSIZ] = (byte)('A' + (idx % 26));
				}
			} else {
				byte[] b = FileOps.read(options.msgpath, -1, null).toArray(true);
				options.msgsiz = (b.length > maxuserdata ? maxuserdata : b.length);
				msgbuf = new byte[HDRSIZ+options.msgsiz];
				System.arraycopy(b, 0, msgbuf, HDRSIZ, options.msgsiz);
			}
		}
		System.out.println();
		if (options.server_enabled) System.out.println("Launching "+(options.server_solo?"stand-alone ":"")+"server");
		if (options.cgrpcnt != 0) {
			System.out.println("Launching clients="+(options.cgrpsiz * options.cgrpcnt)+" within Dispatchers="+options.cgrpcnt);
			System.out.println("Messages = "+options.msgcnt+"x "+options.msgsiz+" bytes"+(options.msgpath==null ? "" : " - "+options.msgpath));
		}
		System.out.println("Transport = "+(options.udpmode ? "UDP" : "TCP"));
		System.out.println("Buffers: Direct="+BufferSpec.directniobufs+", Client="+options.crcvbuf+"/"+options.cxmtbuf
				+", Server="+options.srcvbuf+"/"+options.sxmtbuf+", UDP-socket="+options.sockbufsiz);
		int cgnum = 0;

		// create the Dispatchers and initialise their callback apps
		for (int idx = 0; idx != dcnt; idx++) {
			boolean hasClients = (options.cgrpcnt != 0 && (!options.server_solo || idx != 0));
			String dname = (options.server_enabled && idx == 0 ? "DS" : "");  //server resides in first Dispatcher
			if (hasClients) dname += "DC"+(cgnum+1); //this Dispatcher hosts clients
			DispatcherDef def = dispatcherDefsBuilder.withName(dname).build();
			cdispatchers[idx] = Dispatcher.create(appctx, def, bootlog);
			Dispatcher dsptch = cdispatchers[idx];

			if (idx == 0) {
				if (options.server_enabled) {
					dserver = dsptch;
					if (options.udpmode) {
						ServerUDP srvr = new ServerUDP(this, dsptch, tsap, sbufspec, options.sockbufsiz);
						dsptch.loadRunnable(srvr);
					} else {
						ConcurrentListenerConfig lcfg = new ConcurrentListenerConfig.Builder<>()
								.withName("EchoBot-"+dsptch.getName())
								.withInterface(tsap.dotted_ip.toString())
								.withPort(tsap.port)
								.withBacklog(options.cgrpcnt * options.cgrpsiz)
								.withServerFactory(ServerTCP.Factory.class, null)
								.build();
						ConcurrentListener lstnr = ConcurrentListener.create(dsptch, this, null, lcfg);
						dsptch.loadRunnable(lstnr);
					}
					if (options.server_solo) continue;
				}
			}
			if (hasClients) {
				BufferSpec bufspec = new BufferSpec(options.crcvbuf, options.cxmtbuf);
				cgroups[cgnum++] = new ClientGroup(this, dsptch, options.udpmode, tsap, options.cgrpsiz, bufspec, msgbuf, options.msgcnt,
						options.sockbufsiz, options.verify);
			}
		}
		cgrpcnt = options.cgrpcnt;

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
		long min_duration = Long.MAX_VALUE;
		long max_duration = 0;
		long durations_sum = 0;
		int durations = 0;
		long latency_sum = 0;
		long min_latency = Long.MAX_VALUE;
		long max_latency = 0;
		int latencies = 0;
		for (int idx = 0; idx != options.cgrpcnt; idx++) {
			ClientGroup grp = cgroups[idx];
			for (int idx2 = 0; idx2 != grp.durations.size(); idx2++) {
				long time = grp.durations.get(idx2).longValue();
				durations_sum += time;
				if (time < min_duration) min_duration = time;
				if (time > max_duration) max_duration = time;
				durations++;
			}
			for (int idx2 = 0; idx2 != grp.latencies.size(); idx2++) {
				long time = grp.latencies.get(idx2).longValue();
				latency_sum += time;
				if (time < min_latency) min_latency = time;
				if (time > max_latency) max_latency = time;
				latencies++;
			}
			failcnt += grp.failcnt;
		}
		java.text.DecimalFormat fmt = new java.text.DecimalFormat("###,###.######");
		long numclients = (options.cgrpcnt * options.cgrpsiz) - failcnt;
		long numbytes = options.msgcnt * (HDRSIZ + options.msgsiz);
		double avg_duration = durations_sum / (durations * 1000d * 1000d * 1000d);  //express as seconds
		double avg_latency = latency_sum / (latencies * 1000d * 1000d * 1000d);  //express as seconds
		System.out.println();
		if (failcnt != 0) System.out.println("Failed clients = "+failcnt+"/"+(failcnt + numclients));
		if (numclients != 0) {
			System.out.println("Rate = "+fmt.format((8d * numbytes)/(1024d * 1024d * avg_duration))+" Mbps");
			System.out.println("Average round-trip latency = "+fmt.format(avg_latency)+"s"
					+" - (Min="+fmt.format(min_latency/(1000d * 1000d * 1000d))+"s"
					+", Max="+fmt.format(max_latency/(1000d * 1000d * 1000d))+"s"
					+")");
			System.out.println("Average session time = "+fmt.format(avg_duration)+"s"
					+" - (Min="+fmt.format(min_duration/(1000d * 1000d * 1000d))+"s"
					+", Max="+fmt.format(max_duration/(1000d * 1000d * 1000d))+"s"
					+")");
		}
	}

	// This method can be called from multiple Dispatcher threads, but the values it updates were
	// initially set in the non-MT initial-setup phase.
	public synchronized void terminated(ClientGroup cg)
	{
		if (cg.dsptch != dserver) cg.dsptch.stop();
		if (--cgrpcnt == 0 && dserver != null) {
			dserver.stop();
			dserver = null;
		}
	}
}