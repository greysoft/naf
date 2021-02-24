/*
 * Copyright 2011-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.logging;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.DynLoader;
import com.grey.base.config.XmlConfig.XmlConfigException;

public class Factory
{
	private static final String SYSPROP_CFGFILE = "grey.logger.configfile";
	public static final String DFLT_CFGFILE = getConfigFile();
	public static final String DFLT_LOGNAME = "default";

	static {
		com.grey.base.utils.PkgInfo.announceJAR(Factory.class, "GreyLogger", null);
		java.io.File fh = new java.io.File(DFLT_CFGFILE);
		if (Logger.DIAGNOSTICS) {
			if (!fh.exists()) {
				System.out.println(Logger.DIAGMARK+"Default config file not found - "+DFLT_CFGFILE);
			} else {
				System.out.println(Logger.DIAGMARK+"Default config file is "+DFLT_CFGFILE);
			}
		}
	}

	/*
	 * Creates a logger based on the default entry in the default logging.xml config file.
	 */
	public static Logger getLogger() throws java.io.IOException
	{
		return getLogger(DFLT_LOGNAME);
	}

	/*
	 * Creates a logger based on the named entry in the default logging.xml config file.
	 */
	public static Logger getLogger(String name) throws java.io.IOException
	{
		return getLogger(DFLT_CFGFILE, name);
	}

	/*
	 * Creates a logger based on the named entry in the specified logging.xml config file.
	 */
	public static Logger getLogger(String cfgpath, String name) throws java.io.IOException
	{
		if (Logger.DIAGNOSTICS) System.out.println(Logger.DIAGMARK+"Parsing logging config="+cfgpath+" - PWD="+new java.io.File(".").getAbsolutePath());
		XmlConfig cfg = parseConfig(cfgpath, name);
		return getLogger(cfg, name);
	}

	public static Logger getLogger(XmlConfig cfg, String name) throws java.io.IOException
	{
		Parameters params = new Parameters(cfg);
		return getLogger(params, name);
	}

	public static Logger getLogger(Parameters params, String name) throws java.io.IOException
	{
		if (name == null || name.isEmpty()) name = DFLT_LOGNAME;
		if (params == null) params = new Parameters.Builder().build();
		Logger log = null;
		if (Logger.DIAGNOSTICS) System.out.println(Logger.DIAGMARK+"Creating Logger="+params.getLogClass());
		try {
			Class<?> clss = DynLoader.loadClass(params.getLogClass());
			java.lang.reflect.Constructor<?> ctor = clss.getDeclaredConstructor(Parameters.class, String.class);
			ctor.setAccessible(true);
			log = Logger.class.cast(ctor.newInstance(params, name));
		} catch (ReflectiveOperationException ex) {
			throw new IllegalArgumentException("Failed to create logger="+params.getLogClass(), ex);
		}
		if (Logger.DIAGNOSTICS) System.out.println(Logger.DIAGMARK+"Created Logger - "+log);
		log.init();
		if (Logger.DIAGNOSTICS) System.out.println(Logger.DIAGMARK+"Initialised Logger - "+log);
		return log;
	}

	private static XmlConfig parseConfig(String cfgpath, String name) throws java.io.IOException
	{
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

		if (cfg != null) {
			String alias = cfg.getValue("alias", false, null);
			if (name != null && name.equals(alias)) throw new XmlConfigException("GreyLogger: Infinite loop between "+name+" and "+alias+" - "+cfgpath);
			if (alias != null && !alias.isEmpty()) return parseConfig(cfgpath, alias);
			cfg = cfg.getSection("file");
		}
		return cfg;
	}

	private static String getConfigFile()
	{
		String pthnam = SysProps.get(SYSPROP_CFGFILE);
		java.io.File fh = null;

		if (pthnam == null) {
			String[] huntpath = new String[]{"./logging.xml",
					"./conf/logging.xml",
					System.getProperty("user.home", ".")+"/logging.xml"};
			for (int idx = 0; idx != huntpath.length; idx++) {
				if (huntpath[idx] == null) continue;
				fh = new java.io.File(huntpath[idx]);
				if (fh.exists()) {
					pthnam = huntpath[idx];
					break;
				}
			}
			if (pthnam == null) return huntpath[0];  //doesn't exist, but gives us a filename to refer to
		}
		if (fh == null) fh = new java.io.File(pthnam);

		try {
			return fh.getCanonicalPath();
		} catch (Exception ex) {
			String msg = Logger.DIAGMARK+"Failed to get full pathname of config="+pthnam;
			System.out.println(msg+" - "+com.grey.base.ExceptionUtils.summary(ex));
			throw new IllegalArgumentException(msg, ex);
		}
	}

	public static Logger getLoggerNoEx(String name)
	{
		try {
			return getLogger(name);
		} catch (Exception ex) {
			throw new IllegalStateException("GreyLog-Factory failed to create logger="+name, ex);
		}
	}
}