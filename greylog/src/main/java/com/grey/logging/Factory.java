/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.DynLoader;

public class Factory
{
	private static final String SYSPROP_CFGFILE = "grey.logger.configfile";
	private static final String SYSPROP_SINKSTDIO = "grey.logger.sinkstdio";
	private static final String SYSPROP_LEADNAMEPARTS = "grey.logger.leadnameparts";
	public static final String DFLT_CFGFILE = getConfigFile();
	public static final String DFLT_LOGNAME = "default";

	public static final boolean sinkstdio = SysProps.get(SYSPROP_SINKSTDIO, false);
	private static final boolean diagtrace = SysProps.get(Logger.SYSPROP_DIAG, false);
	private static final java.util.Map<String, Logger> loggers = new com.grey.base.utils.HashedMap<String, Logger>();

	/*
	 * These 2 fields support Logger clients who expect the JUL or LOG4J style usage of logger names, where the name effectively merely
	 * specifies a prefix to prepend to their log messages (typically a class name) and multiple loggers point to "Appenders" which
	 * are the objects that actually point at an output file stream.
	 * However the Grey Logger is effectively what they call an Appender and such logging clients would end up specifying hundreds of
	 * loggers, none of whom would exist in the config file. The SYSPROP_LEADNAMEPARTS supports these clients by letting you specify
	 * the max number of leading name parts that are significant, so that instead of specifying hundreds of loggers in the log file,
	 * you just need to specify one or two, that capture the common roots of your package names.
	 */
	private static int leadingNameParts = 0;
	private static String delimiterNameParts = ".";

	static {
		com.grey.base.utils.PkgInfo.announceJAR(Factory.class, "GreyLogger", null);
		java.io.File fh = new java.io.File(DFLT_CFGFILE);
		if (!fh.exists()) {
			System.out.println("* * * GreyLogger: Default config file not found - "+DFLT_CFGFILE);
		} else {
			if (diagtrace) System.out.println("* * * GreyLogger: Default config file is "+DFLT_CFGFILE);
		}
		String str = SysProps.get(SYSPROP_LEADNAMEPARTS, null);
		if (str != null) {
			String[] tuple = str.split(":");
			leadingNameParts = Integer.valueOf(tuple[0]);
			if (tuple.length > 1) delimiterNameParts = tuple[1];
		}
	}

	/**
	 * Creates a logger based on the default entry in the default logging.xml config file.
	 */
	public static Logger getLogger() throws com.grey.base.ConfigException, java.io.IOException
	{
		return getLogger(null);
	}

	/**
	 * Creates a logger based on the named entry in the default logging.xml config file.
	 */
	public static Logger getLogger(String name) throws com.grey.base.ConfigException, java.io.IOException
	{
		if (leadingNameParts != 0 && name != null) {
			name = StringOps.keepLeadingParts(name, delimiterNameParts, leadingNameParts);
		}
		return getLogger(DFLT_CFGFILE, name);
	}

	/**
	 * Creates a logger based on the named entry in the specified logging.xml config file.
	 */
	public static Logger getLogger(String cfgpath, String name) throws com.grey.base.ConfigException, java.io.IOException
	{
		return getNamedLogger(cfgpath, name, null);
	}

	public static Logger getLogger(XmlConfig cfg, String name) throws com.grey.base.ConfigException, java.io.IOException
	{
		Parameters params = new Parameters(cfg);
		return getLogger(params, name);
	}

	public static Logger getLogger(Parameters params, String name) throws com.grey.base.ConfigException, java.io.IOException
	{
		if (params == null) params = new Parameters();
		params.reconcile();
		Logger log = null;
		try {
			Class<?> clss = DynLoader.loadClass(params.logclass);
			java.lang.reflect.Constructor<?> ctor = clss.getDeclaredConstructor(Parameters.class, String.class);
			log = Logger.class.cast(ctor.newInstance(params, name));
		} catch (Exception ex) {
			throw new com.grey.base.ConfigException(ex, "Failed to create logger="+params.logclass+" - "+com.grey.base.GreyException.summary(ex));
		}
		log.init();
		if (diagtrace) System.out.println("GreyLogger: Created Logger - "+log);
		return log;
	}

	private static Logger getNamedLogger(String cfgpath, String name, String alias) throws com.grey.base.ConfigException, java.io.IOException
	{
		String tag = (alias == null ? name : (alias+"/"+name));
		if (name == null || name.length() == 0) name = DFLT_LOGNAME;
		String xpath = "/loggers/logger[@name='"+name+"']"+XmlConfig.XPATH_ENABLED;
		java.io.File fh = new java.io.File(cfgpath);
		java.net.URL url = null;
		XmlConfig cfg = null;

		if (fh.exists()) {
			cfg = XmlConfig.getSection(cfgpath, xpath);
		} else {
			url = DynLoader.getLoaderResource(cfgpath, Factory.class.getClassLoader());
			if (url != null ) {
				cfgpath = url.toString();
				String xmltxt = FileOps.readAsText(url, null);
				cfg = XmlConfig.makeSection(xmltxt, xpath);
			}
		}
		if (url == null) cfgpath = fh.getCanonicalPath();
		Parameters params = null;

		if (cfg != null) {
			alias = cfg.getValue("alias", false, null);
			if (name.equals(alias)) throw new com.grey.base.ConfigException("GreyLogger: Infinite loop between "+name+" and "+alias+" - "+cfgpath);
			if (alias != null && alias.length() != 0) return getNamedLogger(cfgpath, alias, tag);
			cfg = new XmlConfig(cfg, "file");
		}
		if ((cfg == null || !cfg.exists())
				&& !name.equalsIgnoreCase(DFLT_LOGNAME)) {
			if (sinkstdio) {
				if (diagtrace) System.out.println("GreyLogger: Logger="+tag+" has been directed to stdio - "+cfgpath);
				params = new Parameters();
			} else {
				if (diagtrace) System.out.println("GreyLogger: Logger="+tag+" has been directed to Sink - "+cfgpath);
				return new SinkLogger(name);
			}
		}
		if (params == null) params = new Parameters(cfg);
		params.reconcile();
		String key = params.pthnam+":"+params.strm;
		Logger log = null;

		synchronized (loggers) {
			log = loggers.get(key);
			if (log != null) {
				if (diagtrace) System.out.println("GreyLogger: Logger="+tag+" has been directed to existing Logger "+log+" - "+cfgpath);
				return log;
			}
			log = getLogger(params, name);
			loggers.put(key, log);
			if (diagtrace) System.out.println("GreyLogger: Named Logger="+tag+" yielded "+log+" - "+cfgpath);
		}
		return log;
	}

	protected static boolean removeMappedLogger(Logger log)
	{
		synchronized (loggers) {
			java.util.Iterator<String> it = loggers.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next();
				if (loggers.get(key) == log) {
					loggers.remove(key);
					return true;
				}
			}
		}
		return false;
	}

	private static String getConfigFile()
	{
		String pthnam = System.getProperty(SYSPROP_CFGFILE);
		String[] huntpath = new String[]{"./logging.xml",
				"./conf/logging.xml",
				System.getProperty("user.home", ".")+"/logging.xml"};
		java.io.File fh = null;

		if (pthnam == null) {
			for (int idx = 0; idx != huntpath.length; idx++) {
				if (huntpath[idx] == null) continue;
				fh = new java.io.File(huntpath[idx]);
				if (fh.exists()) {
					pthnam = huntpath[idx];
					break;
				}
			}
		}
		if (pthnam == null) return huntpath[0];  //doesn't exist, but gives us a filename to refer to
		if (fh == null) fh = new java.io.File(pthnam);
		try {
			return fh.getCanonicalPath();
		} catch (Exception ex) {
			String msg = "GreyLogger: Failed to canonise config="+pthnam+" - "+com.grey.base.GreyException.summary(ex);
			System.out.println(msg);
			throw new RuntimeException(msg, ex);
		}
	}
}
