/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;
import com.grey.base.utils.CommandParser;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.DynLoader;
import com.grey.logging.Logger;

public class Launcher
{
	private static final String SYSPROP_CP = "greynaf.cp";
	private static final String SYSPROP_BOOTLVL = "greynaf.boot.loglevel";

	private static final int F_DUMPENV = 1 << 0;
	private static final int F_QUIET = 1 << 1;  //mainly for the benefit of the unit tests

	private static final String[] opts = new String[]{"c:", "cmd:", "remote:", "logger:", "dumpenv", "q"};

	private static boolean announcedNAF;
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
		public String cmd;
		public String hostport;  //qualifies cmd option
		public String logname;
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
			} else {
				throw new RuntimeException("Missing case for bool-option="+opt);
			}
		}

		@Override
		public void setOption(String opt, String val) {
			if (opt.equals("c")) {
				cfgpath = val;
			} else if (opt.equals("cmd")) {
				cmd = val;
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
			txt += "\n\t-cmd cmd-URL [-c naf-config-file] [-q]";
			txt += "\n\t-cmd cmd-URL -remote host:port [-q]";
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

		if (baseOptions.cmd != null) {
			try {
				issueCommand(param1);
			} catch (java.io.IOException ex) {
				System.out.println("Failed to issue NAFMAN command - "+com.grey.base.GreyException.summary(ex));
			}
		} else {
			appExec(param1);
		}
	}

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
		java.io.File fh = new java.io.File(baseOptions.cfgpath);
		if (!fh.exists()) {
			//avoid big ugly stack dump for what is likely to be a common problem
			System.out.println("NAF Config file not found");
			return;
		}
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(baseOptions.cfgpath);
		com.grey.logging.Logger bootlog = createBootLogger(baseOptions);
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
		com.grey.logging.Logger log = null; //use stdout
		if (baseOptions.isFlagSet(F_QUIET)) log = com.grey.logging.Factory.getLogger("no-such-logger"); //sink logger
		String rsp;
		if (baseOptions.hostport != null) {
			rsp = com.grey.naf.nafman.Client.submitCommand(baseOptions.cmd, baseOptions.hostport, log);
		} else {
			rsp = com.grey.naf.nafman.Client.submitLocalCommand(baseOptions.cmd, baseOptions.cfgpath, log);
		}
		System.out.println("NAFMAN Response="+rsp.length()+":\n\n"+rsp);
	}

	// It's ultimately up to the user how they configure the logging framework, but some initialisation code
	// here and there writes directly to stdout, so by default we direct the boot logger to stdout as well, so
	// that it and the raw stdout writes can be captured/redirected as one.
	// The advantage of writing to stdout with bootlog rather than System.out() is that we get properly
	// timestamped entries.
	private static com.grey.logging.Logger createBootLogger(BaseOptsHandler opts)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		if (opts.logname != null) return com.grey.logging.Factory.getLogger(opts.logname);
		Logger.LEVEL lvl = Logger.LEVEL.valueOf(SysProps.get(SYSPROP_BOOTLVL, Logger.LEVEL.TRC.toString()));
		com.grey.logging.Parameters params = new com.grey.logging.Parameters(lvl, System.out);
		return com.grey.logging.Factory.getLogger(params, "NAF-bootlog");
	}

	// This may not look MT-safe, but it only gets called early on during startup, when still single-threaded
	public static synchronized void announceNAF()
	{
		if (announcedNAF) return;
		announcedNAF = true;
		com.grey.base.utils.PkgInfo.announceJAR(Launcher.class, "NAF", null);
	}
}