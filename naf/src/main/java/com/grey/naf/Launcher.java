/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;
import com.grey.base.utils.CommandParser;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.DynLoader;

public class Launcher
{
	private static final String SYSPROP_CP = "greynaf.cp";

	private static final int F_DUMPENV = 1 << 0;
	private static final int F_QUIET = 1 << 1;  //mainly for the benefit of the unit tests
	private static final int F_NOCHECK = 1 << 2;  //allows us to probe NAFMAN response to bad args, for test purposes

	private static final String[] opts = new String[]{"c:", "cmd:", "remote:", "logger:", "dumpenv", "q", "nocheck"};

	private volatile static boolean announcedNAF;
	static {
		announceNAF();
	}

	public static void main(String[] args) throws Exception
	{
		Launcher app = new Launcher(args);
		app.exec();
	}

	public static class BaseOptsHandler
		extends CommandParser.OptionsHandler
	{
		public String cfgpath = "naf.xml";
		public String logname = "";
		public String cmdname;
		public String hostport;  //qualifies command-name
		public int flags;

		public BaseOptsHandler() {super(opts, 0, -1);}
		public void setFlag(int f) {flags |= f;}
		public boolean isFlagSet(int f) {return ((flags & f) != 0);}

		@Override
		public void setOption(String opt) {
			if (opt.equals("dumpenv")) {
				setFlag(F_DUMPENV);
			} else if (opt.equals("q")) {
				setFlag(F_QUIET);
			} else if (opt.equals("nocheck")) {
				setFlag(F_NOCHECK);
			} else {
				throw new RuntimeException("Missing case for bool-option="+opt);
			}
		}

		@Override
		public void setOption(String opt, String val) {
			if (opt.equals("c")) {
				cfgpath = val;
			} else if (opt.equals("cmd")) {
				cmdname = val;
			} else if (opt.equals("remote")) {
				hostport = val;
			} else if (opt.equals("logger")) {
				logname = val;
			} else {
				throw new RuntimeException("Missing case for value-option="+opt);
			}
		}

		@Override
		public String displayUsage()
		{
			String txt = "\t[-c naf-config-file] [-logger name] [-dumpenv]";
			txt += "\n\t-cmd cmdname [-c naf-config-file] [-q] [-nocheck] command-args";
			txt += "\n\t-cmd cmdname -remote host:port [-q] [-nocheck] command-args";
			txt += "\nThe config file defaults to ./naf.xml";
			return txt;
		}
	}

	protected final BaseOptsHandler baseOptions = new BaseOptsHandler();
	protected final com.grey.base.utils.CommandParser cmdParser;
	protected final String[] cmdlineArgs;

	public Launcher(String[] args)
	{
		cmdlineArgs = args;
		cmdParser = new com.grey.base.utils.CommandParser(baseOptions);
	}

	public final void exec() throws Exception
	{
		int param1 = cmdParser.parse(cmdlineArgs);
		if (param1 == -1) return;

        if (baseOptions.isFlagSet(F_DUMPENV)) {
        	SysProps.dump(System.getProperties(), System.out);
        	com.grey.base.utils.PkgInfo.dumpEnvironment(getClass(), System.out);
        }

		if (baseOptions.cmdname != null) {
			try {
				issueCommand(param1);
			} catch (java.io.IOException ex) {
				System.out.println("Failed to issue NAFMAN command - "+com.grey.base.GreyException.summary(ex));
			}
		} else {
			appExec(param1);
		}
	}

	// It's up to the user how they configure the logging framework, but the intention is that ideally bootlog,
	// stdio and stderr all point at the same stream. The advantage of writing to it with bootlog rather than
	// PrintStream.println() is that we get properly timestamped entries.
	protected void appExec(int param1) throws Exception
	{
		if (param1 != cmdlineArgs.length) {
			cmdParser.usage(cmdlineArgs, 0, "Excess params="+(cmdlineArgs.length-param1));
			return;
		}
		long systime_boot = System.currentTimeMillis();
        System.out.println(new java.util.Date(systime_boot)+" - NAF BOOTING in "+new java.io.File(".").getCanonicalPath());

		String cp = SysProps.get(SYSPROP_CP);
		if (cp != null) {
			System.out.println("Loading classpath - "+cp);
			DynLoader.load(cp);
		}
		System.out.println("Loading NAF config file: "+baseOptions.cfgpath+" => "+new java.io.File(baseOptions.cfgpath).getCanonicalPath());
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(baseOptions.cfgpath);
		com.grey.logging.Logger bootlog = com.grey.logging.Factory.getLogger(baseOptions.logname);
		bootlog.info("Created NAF Boot Logger");
		nafcfg.announce(bootlog);
		com.grey.naf.reactor.Dispatcher[] dispatchers;

		try {
			dispatchers = com.grey.naf.reactor.Dispatcher.launchConfigured(nafcfg, bootlog);
			if (dispatchers == null) throw new com.grey.base.ConfigException("No Dispatchers are configured");
		} catch (Throwable ex) {
			String errmsg = "Failed to launch NAF - "+com.grey.base.GreyException.summary(ex, true);
			System.out.println(errmsg);
			return;
		}
        long systime2 = System.currentTimeMillis();
		bootlog.info("NAF BOOTED - Dispatchers="+dispatchers.length+", Time="+(float)(systime2-systime_boot)/1000f+"s");
		FileOps.flush(bootlog);

		for (int idx = 0; idx != dispatchers.length; idx++) {
			dispatchers[idx].waitStopped();
		}
		bootlog.info("All Dispatchers have exited - terminating\n\n");
		FileOps.flush(bootlog);
	}

	private void issueCommand(int param1) throws com.grey.base.ConfigException, java.io.IOException
	{
		int argc = cmdlineArgs.length - param1;
		com.grey.naf.nafman.Command.Def def = com.grey.naf.nafman.Client.parseCommand(baseOptions.cmdname, argc, baseOptions.isFlagSet(F_NOCHECK), null);
		if (def == null) return;
		String[] cmdargs = new String[argc];
		for (int idx = param1; idx != cmdlineArgs.length; idx++) cmdargs[idx - param1] = cmdlineArgs[idx];

		com.grey.logging.Logger log = (baseOptions.isFlagSet(F_QUIET) ? com.grey.logging.Factory.getLogger("no-such-logger") : null);
		String rsp;
		if (baseOptions.hostport != null) {
			rsp = com.grey.naf.nafman.Client.submitCommand(baseOptions.hostport, def, cmdargs, log);
		} else {
			rsp = com.grey.naf.nafman.Client.submitLocalCommand(baseOptions.cfgpath, def, cmdargs, log);
		}
		if (rsp.length() != 0 && !baseOptions.isFlagSet(F_QUIET)) {
			if (rsp.length() != 0) System.out.println("\nNAFMAN Response:\n"+rsp.replaceAll("^\\s+", "")+"\n");
		}
	}

	// This may not look MT-safe, but it only gets called early on during startup, when still single-threaded
	public static void announceNAF()
	{
		if (announcedNAF) return;
		announcedNAF = true;
		com.grey.base.utils.PkgInfo.announceJAR(Launcher.class, "NAF", null);
	}
}
