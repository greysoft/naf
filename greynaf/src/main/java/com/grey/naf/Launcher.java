/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import java.util.ArrayList;
import java.util.List;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.CommandParser;
import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.nafman.NafManClient;
import com.grey.naf.nafman.NafManConfig;
import com.grey.naf.errors.NAFConfigException;
import com.grey.logging.Factory;
import com.grey.logging.Parameters;
import com.grey.logging.SinkLogger;
import com.grey.logging.Logger;

public class Launcher
{
	private static final String SYSPROP_BOOTLVL = "greynaf.boot.loglevel";

	private static final int F_DUMPENV = 1 << 0;
	private static final int F_QUIET = 1 << 1;  //mainly for the benefit of the unit tests

	static final String[] options = new String[]{"c:", "appname:", "cmd:", "remote:", "logger:", "dumpenv", "q"};

	private static boolean announcedNAF;
	static {
		announceNAF();
	}

	protected final BaseOptsHandler baseOptions = new BaseOptsHandler();
	protected final CommandParser cmdParser;
	protected final String[] cmdlineArgs;

	/**
	 * Create the default NAFMAN config.
	 * Applications that with to customise the NAFMAN options (before loading naf.xml config file) should override this.
	 * They can disable NAFMAN by returning null.
	 */
	protected NafManConfig.Builder createNafManConfig(NAFConfig nafcfg) {return new NafManConfig.Builder(nafcfg);}

	/**
	 * Applications that need to install NAFMAN commands and resources should override this
	 */
	protected void setupNafMan(ApplicationContextNAF appctx) {}

	/**
	 * This launches default NAF applications driven entirely by command-line options and typically with a naf.xml style config file.
	 * Custom applications (which don't necessarily even have a naf.xml config) would subclass the Launcher and implement their
	 * own main() which would:
	 * - Instantiate their Launcher class
	 * - Call one of the app.execute() variants
	 */
	public static void main(String[] args) throws Exception {
		Launcher app = new Launcher(args);
		app.execute("nafconfig");
	}

	public Launcher(String[] args) {
		cmdlineArgs = args;
		cmdParser = new CommandParser(baseOptions);
	}

	/**
	 * Applications that don't wish to customise NAFConfig can call this from their main() to accept the default setup.
	 * Otherwise they should call the execute(String, NAFConfig.Builder) variant.
	 */
	protected void execute(String dflt_appname) throws Exception {
		execute(dflt_appname, new NAFConfig.Builder());
	}

	/**
	 * This initialises and executes the application, by performing the following actions:
	 * - Parses the command line.
	 * - Loads the naf.xml config file if any, creating the resulting NAFConfig instance.
	 * - Creates the NAFMAN config
	 * - Creates the ApplicationContextNAF instance for this application
	 * - Calls app.Execute() to launch the application
	 * This method also recognises if we're issuing a NAFMAN command rather than launching an application.
	 *
	 * Applications which don't use a naf.xml config file can call this method without specifying one, to get the benefit of
	 * its command-line parsing, while still being able to supply their required NAFConfig and NAFMAN settings.
	 * Then again, NAF applications don't need to use the Launcher at all and can have an independent main() that manually creates
	 * an ApplicationContextNAF and the required Dispatchers, Naflets, listeners, etc.
	 */
	protected void execute(String dflt_appname, NAFConfig.Builder bldrNafConfig) throws Exception {
		int param1 = cmdParser.parse(cmdlineArgs);
		if (param1 == -1) return;

		if (baseOptions.isFlagSet(F_DUMPENV)) {
			SysProps.dump(System.getProperties(), System.out);
			com.grey.base.utils.PkgInfo.dumpEnvironment(getClass(), System.out);
		}
		String appname = baseOptions.appname;
		if (appname == null) appname = dflt_appname;

		Logger bootlog = createBootLogger(baseOptions);
		Logger.setThreadLogger(bootlog);
		System.out.println("Created NAF Boot Logger="+bootlog.getClass().getName()+" for application="+appname);

		NAFConfig nafcfg = loadConfigFile(bldrNafConfig, bootlog);
		if (nafcfg == null) return;

		if (baseOptions.cmd != null) {
			// in client mode - we are simply issuing a NAMAN command
			try {
				issueCommand(appname, nafcfg, bootlog);
			} catch (Throwable ex) {
				System.out.println("Failed to issue NAFMAN command - "+com.grey.base.ExceptionUtils.summary(ex));
			}
			return;
		}

		bootlog.info("NAF Paths - Root = "+nafcfg.getPathRoot());
		bootlog.info("NAF Paths - Config = "+nafcfg.getPathConf());
		bootlog.info("NAF Paths - Var = "+nafcfg.getPathVar());
		bootlog.info("NAF Paths - Logs = "+nafcfg.getPathLogs());
		bootlog.info("NAF Paths - Temp = "+nafcfg.getPathTemp());
		if (nafcfg.getBasePort() != NAFConfig.RSVPORT_ANON) bootlog.info("Base Port="+nafcfg.getBasePort());

		NafManConfig nafmanConfig = configureNafMan(nafcfg);
		bootlog.info("Configured NAFMAN config="+nafmanConfig);

		ApplicationContextNAF appctx = ApplicationContextNAF.create(appname, nafcfg, nafmanConfig);
		bootlog.info("Created Application Context - "+appctx);

		if (nafmanConfig != null) {
			setupNafMan(appctx);
		}
		appExecute(appctx, param1, bootlog);
	}

	/**
	 * This is the default NAF application, which launches a set of Dispatchers driven by a naf.xml style config file.
	 * Custom applications should override this to redirect the execution flow into their own code.
	 * It doesn't make any sense for applications without a naf.xml config file to enter this method as it would be a
	 * no-op without any Dispatchers defined in there.
	 * Conversely, there isn't much point in overriding this for applications which do have a naf.xml config file, as
	 * this represents the logical control flow for them.
	 */
	protected void appExecute(ApplicationContextNAF appctx, int param1, Logger bootlog) throws Exception {
		if (param1 != cmdlineArgs.length) { //we don't expect any params
			cmdParser.usage(cmdlineArgs, 0, "Excess params="+(cmdlineArgs.length-param1));
			return;
		}
		executeDispatchers(appctx, bootlog);
	}

	private NAFConfig loadConfigFile(NAFConfig.Builder bldrNafConfig, Logger bootlog) {
		if (baseOptions.cfgpath != null && !baseOptions.cfgpath.isEmpty()) {
			java.io.File fhConfig = new java.io.File(baseOptions.cfgpath);
			bootlog.info("Loading NAF config file: "+baseOptions.cfgpath+" => "+fhConfig.getAbsolutePath());

			if (!fhConfig.exists()) {
				System.out.println("NAF Config file not found: "+baseOptions.cfgpath);
				return null;
			}
			bldrNafConfig = bldrNafConfig.withConfigFile(fhConfig.getAbsolutePath());
		}
		return bldrNafConfig.build();
	}

	private NafManConfig configureNafMan(NAFConfig nafcfg) {
		NafManConfig nafmanConfig = null;
		NafManConfig.Builder bldrNafMan = createNafManConfig(nafcfg);
		XmlConfig xmlcfgNafMan = nafcfg.getNode("nafman");

		if (xmlcfgNafMan == null || !xmlcfgNafMan.getBool("@enabled", true)) {
			// This means the naf.xml config contains a nafman block that explicitly disables NAFMAN.
			// Even if the application supplied a non-null builder, this overrides.
			bldrNafMan = null;
		}

		if (bldrNafMan != null) {
			bldrNafMan.withXmlConfig(xmlcfgNafMan);
			nafmanConfig = bldrNafMan.build();
		}
		return nafmanConfig;
	}

	private static void executeDispatchers(ApplicationContextNAF appctx, Logger bootlog) throws java.io.IOException {
		long systime_boot = System.currentTimeMillis();
		bootlog.info("NAF BOOTING in "+new java.io.File(".").getCanonicalPath());

		java.util.List<Dispatcher> dispatchers = launchConfiguredDispatchers(appctx, bootlog);
		if (dispatchers == null) throw new NAFConfigException("No Dispatchers are configured");

		long systime2 = System.currentTimeMillis();
		bootlog.info("NAF BOOTED in time="+(systime2-systime_boot)+"ms - Dispatchers="+dispatchers.size());
		FileOps.flush(bootlog);

		// wait for Dispatchers to exit - if they ever do
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

	public static List<Dispatcher> launchConfiguredDispatchers(ApplicationContextNAF appctx, Logger log)
		throws java.io.IOException
	{
		NAFConfig nafcfg = appctx.getConfig();
		XmlConfig[] cfgdispatchers = nafcfg.getDispatchers();
		if (cfgdispatchers == null) return null;
		List<Dispatcher> dlst = new ArrayList<>(cfgdispatchers.length);
		log.info("NAF: Launching configured Dispatchers="+cfgdispatchers.length);

		// Do separate loops to create and start the Dispatchers, so that they're all guaranteed to be in single-threaded
		// mode until all have initialised.
		for (XmlConfig dcfg : cfgdispatchers) {
			DispatcherDef def = new DispatcherDef.Builder().withXmlConfig(dcfg).build();
			Dispatcher dsptch = Dispatcher.create(appctx, def, log);
			dlst.add(dsptch);

			/*
			 * Create any Naflets that are defined in the config file.
			 * Of course applications can also create their own Naflets and inject them into a Dispatcher via the same
			 * loadNaflet() method used here, which can be called before or after the Dispatcher's start() method.
			 */
			XmlConfig[] nafletsConfig = dcfg.getSections("naflets/naflet"+XmlConfig.XPATH_ENABLED);
			if (nafletsConfig != null) {
				dsptch.getLogger().info("Dispatcher="+dsptch.getName()+": Creating Naflets="+nafletsConfig.length);
				for (XmlConfig appcfg : nafletsConfig) {
					Object obj = NAFConfig.createEntity(appcfg, null, Naflet.class, true,
							new Class<?>[]{String.class, dsptch.getClass(), appcfg.getClass()},
							new Object[]{null, dsptch, appcfg});
					Naflet app = Naflet.class.cast(obj);
					if (app.getName().charAt(0) == '_') {
						throw new NAFConfigException("Dispatcher="+dsptch.getName()+" has invalid Naflet name (starts with underscore) - "+app.getName());
					}
					dsptch.loadNaflet(app);
				}
			}
		}

		// log the initial config
		String txt = Dispatcher.dumpConfig(appctx);
		log.info("Initialisation of the configured NAF Dispatchers is now complete\n"+txt);

		// Now starts the multi-threaded phase
		for (int idx = 0; idx != dlst.size(); idx++) {
			dlst.get(idx).start();
		}
		return dlst;
	}

	private void issueCommand(String appname, NAFConfig nafcfg, Logger log) throws java.io.IOException {
		if (baseOptions.isFlagSet(F_QUIET)) log = new SinkLogger(appname);
		String rsp;
		if (baseOptions.hostport != null) {
			rsp = NafManClient.submitCommand(baseOptions.cmd, baseOptions.hostport, log);
		} else {
			rsp = NafManClient.submitLocalCommand(baseOptions.cmd, nafcfg, log);
		}
		if (rsp != null && !baseOptions.isFlagSet(F_QUIET)) System.out.println("NAFMAN Response="+rsp.length()+":\n\n"+rsp);
	}

	// It's ultimately up to the user how they configure the logging framework, but some initialisation code
	// here and there writes directly to stdout, so by default we direct the boot logger to stdout as well, so
	// that it and the raw stdout writes can be captured/redirected as one.
	// The advantage of writing to stdout with bootlog rather than System.out() is that we get properly
	// timestamped entries.
	private static Logger createBootLogger(BaseOptsHandler opts) throws java.io.IOException {
		if (opts.logname != null) return Factory.getLogger(opts.logname);
		Logger.LEVEL lvl = Logger.LEVEL.valueOf(SysProps.get(SYSPROP_BOOTLVL, Logger.LEVEL.TRC.toString()));
		Parameters params = new Parameters.Builder()
				.withStream(System.out)
				.withLogLevel(lvl)
				.build();
		return Factory.getLogger(params, "NAF-bootlog");
	}

	// This may not look MT-safe, but it only gets called early on during startup, when still single-threaded
	public static synchronized void announceNAF() {
		if (announcedNAF) return;
		announcedNAF = true;
		com.grey.base.utils.PkgInfo.announceJAR(Launcher.class, "NAF", null);
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
}