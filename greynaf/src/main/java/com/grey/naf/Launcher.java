/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.CommandParser;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.nafman.NafManClient;
import com.grey.naf.errors.NAFConfigException;
import com.grey.logging.Factory;
import com.grey.logging.Parameters;
import com.grey.logging.Logger;

public class Launcher
{
	private static final String SYSPROP_CP = "greynaf.cp";
	private static final String SYSPROP_BOOTLVL = "greynaf.boot.loglevel";

	private static final int F_DUMPENV = 1 << 0;
	private static final int F_QUIET = 1 << 1;  //mainly for the benefit of the unit tests

	static final String[] options = new String[]{"c:", "appname:", "cmd:", "remote:", "logger:", "dumpenv", "q"};

	private static boolean announcedNAF;
	static {
		announceNAF();
	}

	public static void main(String[] args) throws Exception
	{
		Launcher app = new Launcher(args);
		app.exec("nafconfig", true);
	}

	public static class BaseOptsHandler
		extends CommandParser.OptionsHandler
	{
		public String cfgpath;
		public String appname;
		public String cmd;
		public String hostport;  //qualifies cmd option
		public String logname;
		public int flags;

		public BaseOptsHandler() {super(options, 0, -1);}
		public void setFlag(int f) {flags |= f;}
		public boolean isFlagSet(int f) {return ((flags & f) != 0);}

		@Override
		public void setOption(String opt) {
			if (opt.equals("dumpenv")) {
				setFlag(F_DUMPENV);
			} else if (opt.equals("q")) {
				setFlag(F_QUIET);
			} else {
				throw new Error("Missing case for bool-option="+opt);
			}
		}

		@Override
		public void setOption(String opt, String val) {
			if (opt.equals("c")) {
				cfgpath = val;
			} else if (opt.equals("appname")) {
				appname = val;
			} else if (opt.equals("cmd")) {
				cmd = val;
			} else if (opt.equals("remote")) {
				hostport = val;
			} else if (opt.equals("logger")) {
				logname = val;
			} else {
				throw new Error("Missing case for value-option="+opt);
			}
		}

		@Override
		public String displayUsage() {
			return Launcher.displayUsage();
		}

		@Override
		public String toString() {
			return super.toString()+" - cfgpath="+cfgpath+", appname="+appname+", cmd="+cmd+", hostport="+hostport+", logname="+logname+", flags=0x"+Integer.toHexString(flags);
		}
	}

	public static String displayUsage()
	{
		String txt = "\t[-c naf-config-file] [-appname name] [-logger name] [-dumpenv]";
		txt += "\nClient Mode:";
		txt += "\n\t-cmd cmd-URL [-c naf-config-file] [-q]";
		txt += "\n\t-cmd cmd-URL -remote host:port [-q]";
		return txt;
	}

	protected final BaseOptsHandler baseOptions = new BaseOptsHandler();
	protected final CommandParser cmdParser;
	protected final String[] cmdlineArgs;

	// applications that need to install NAFMAN commands and resources should override this
	protected void setupNafMan(ApplicationContextNAF appctx) {}

	public Launcher(String[] args) {
		cmdlineArgs = args;
		cmdParser = new CommandParser(baseOptions);
	}

	private void issueCommand() throws java.io.IOException {
		Logger log = null; //use stdout
		if (baseOptions.isFlagSet(F_QUIET)) log = Factory.getLogger("no-such-logger"); //sink logger
		String rsp;
		if (baseOptions.hostport != null) {
			rsp = NafManClient.submitCommand(baseOptions.cmd, baseOptions.hostport, log);
		} else {
			rsp = NafManClient.submitLocalCommand(baseOptions.cmd, baseOptions.cfgpath, log);
		}
		if (!baseOptions.isFlagSet(F_QUIET)) System.out.println("NAFMAN Response="+rsp.length()+":\n\n"+rsp);
	}

	public void exec(String dflt_appname, boolean withNafMan, NAFConfig.Defs defaults) throws Exception {
		int param1 = cmdParser.parse(cmdlineArgs);
		if (param1 == -1) return;

		if (baseOptions.isFlagSet(F_DUMPENV)) {
			SysProps.dump(System.getProperties(), System.out);
			com.grey.base.utils.PkgInfo.dumpEnvironment(getClass(), System.out);
		}

		String cp = SysProps.get(SYSPROP_CP);
		if (cp != null) {
			System.out.println("Loading classpath - "+cp);
			DynLoader.load(cp);
		}

		if (baseOptions.cmd != null) {
			// in client mode
			try {
				issueCommand();
			} catch (java.io.IOException ex) {
				System.out.println("Failed to issue NAFMAN command - "+com.grey.base.ExceptionUtils.summary(ex));
			}
			return;
		}
		String appname = baseOptions.appname;
		if (appname == null) appname = dflt_appname;

		Logger bootlog = createBootLogger(baseOptions);
		Logger.setThreadLogger(bootlog);
		System.out.println("Created NAF Boot Logger="+bootlog.getClass().getName()+" for application="+appname);

		NAFConfig nafcfg = loadConfig(baseOptions.cfgpath, defaults, bootlog);
		if (nafcfg == null) return;
		nafcfg.announce(bootlog);

		ApplicationContextNAF appctx = ApplicationContextNAF.create(appname, nafcfg);
		bootlog.info("Created Application Context - "+appctx);
		if (withNafMan) setupNafMan(appctx);

		appExec(appctx, param1, bootlog);
	}

	public void exec(String dflt_appname, boolean withNafMan) throws Exception {
		NAFConfig.Defs defaults = new NAFConfig.Defs(withNafMan ? NAFConfig.RSVPORT_NAFMAN : NAFConfig.RSVPORT_ANON);
		exec(dflt_appname, withNafMan, defaults);
	}

	protected void appExec(ApplicationContextNAF appctx, int param1, Logger bootlog) throws Exception {
		if (param1 != cmdlineArgs.length) { //we don't expect any params
			cmdParser.usage(cmdlineArgs, 0, "Excess params="+(cmdlineArgs.length-param1));
			return;
		}
		launchDispatchers(appctx, bootlog);
	}

	private static NAFConfig loadConfig(String cfgpath, NAFConfig.Defs defaults, Logger bootlog) throws java.io.IOException
	{
		if (cfgpath == null) {
			return NAFConfig.load(defaults);
		}
		bootlog.info("Loading NAF config file: "+cfgpath+" => "+new java.io.File(cfgpath).getCanonicalPath());
		java.io.File fh = new java.io.File(cfgpath);

		if (!fh.exists()) {
			//avoid big ugly stack dump for what is likely to be a common problem
			System.out.println("NAF Config file not found: "+cfgpath);
			return null;
		}
		return NAFConfig.load(cfgpath, defaults);
	}

	private static void launchDispatchers(ApplicationContextNAF appctx, Logger bootlog) throws java.io.IOException
	{
		long systime_boot = System.currentTimeMillis();
		bootlog.info("NAF BOOTING in "+new java.io.File(".").getCanonicalPath());
		java.util.List<Dispatcher> dispatchers;
		try {
			dispatchers = Dispatcher.launchConfigured(appctx, bootlog);
			if (dispatchers == null) throw new NAFConfigException("No Dispatchers are configured");
		} catch (Throwable ex) {
			String errmsg = "Failed to launch NAF - "+com.grey.base.ExceptionUtils.summary(ex, true);
			System.out.println(errmsg);
			return;
		}
		long systime2 = System.currentTimeMillis();
		bootlog.info("NAF BOOTED in time="+(systime2-systime_boot)+"ms - Dispatchers="+dispatchers.size());
		FileOps.flush(bootlog);

		while (dispatchers.size() != 0) {
			for (int idx = 0; idx != dispatchers.size(); idx++) {
				Dispatcher d = dispatchers.get(idx);
				Dispatcher.STOPSTATUS stopsts = d.waitStopped(5000, false);
				if (stopsts != Dispatcher.STOPSTATUS.ALIVE) {
					dispatchers.remove(d);
					bootlog.info("Launcher has reaped Dispatcher="+d.getName()+" - remaining="+dispatchers.size());
					break;
				}
			}
		}
		bootlog.info("All Dispatchers have exited - terminating\n\n");
		FileOps.flush(bootlog);
	}

	// It's ultimately up to the user how they configure the logging framework, but some initialisation code
	// here and there writes directly to stdout, so by default we direct the boot logger to stdout as well, so
	// that it and the raw stdout writes can be captured/redirected as one.
	// The advantage of writing to stdout with bootlog rather than System.out() is that we get properly
	// timestamped entries.
	private static Logger createBootLogger(BaseOptsHandler opts) throws java.io.IOException
	{
		if (opts.logname != null) return Factory.getLogger(opts.logname);
		Logger.LEVEL lvl = Logger.LEVEL.valueOf(SysProps.get(SYSPROP_BOOTLVL, Logger.LEVEL.TRC.toString()));
		Parameters params = new Parameters.Builder()
				.withStream(System.out)
				.withLogLevel(lvl)
				.build();
		return Factory.getLogger(params, "NAF-bootlog");
	}

	// This may not look MT-safe, but it only gets called early on during startup, when still single-threaded
	public static synchronized void announceNAF()
	{
		if (announcedNAF) return;
		announcedNAF = true;
		com.grey.base.utils.PkgInfo.announceJAR(Launcher.class, "NAF", null);
	}
}