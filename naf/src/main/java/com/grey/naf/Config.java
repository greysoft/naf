/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.DynLoader;
import com.grey.base.config.XmlConfig;

/** Represents (and parses) a naf.xml config file.
 */
public class Config
{
	static {
		Launcher.announceNAF();
	}
	public static final String SYSPROP_DIRPATH_ROOT = "greynaf.paths.root";
	public static final String SYSPROP_DIRPATH_CONF = "greynaf.paths.conf";
	public static final String SYSPROP_DIRPATH_VAR = "greynaf.paths.var";
	public static final String SYSPROP_DIRPATH_TMP = SysProps.SYSPROP_DIRPATH_TMP;
	public static final String SYSPROP_DIRPATH_LOGS = com.grey.logging.Parameters.SYSPROP_LOGSDIR;
	public static final String SYSPROP_BASEPORT = "greynaf.baseport";

	public static final String DIRTOKEN_ROOT = "%DIRTOP%";
	public static final String DIRTOKEN_CONF = "%DIRCONF%";
	public static final String DIRTOKEN_VAR = "%DIRVAR%";
	public static final String DIRTOKEN_TMP = SysProps.DIRTOKEN_TMP;
	public static final String DIRTOKEN_LOGS = com.grey.logging.Parameters.TOKEN_LOGSDIR;

	public static final String PFX_CLASSPATH = "cp:";

	private static final int DFLT_BASEPORT = SysProps.get(SYSPROP_BASEPORT, 12000);
	public static final int RSVPORT_NAFMAN = 1;
	private static final int RSVPORT_MAX = 5;
	private static final int RSVPORT_ANON = 0;  // not reserved at all

	public final String path_root;
	public final String path_conf;
	public final String path_var;
	public final String path_logs;
	public final String path_tmp;  //NB: Diverges from SysProps.TMPDIR, unless SYSPROP_DIRPATH_TMP is set
	public XmlConfig cfgroot;

	private final int baseport;  // base port
	private int nextport;  // next port number to assign

	// The param is the naf.xml config filename
	public static Config load(String cfgpath) throws com.grey.base.ConfigException, java.io.IOException
	{
		XmlConfig cfg = XmlConfig.getSection(cfgpath, "naf");
		return new Config(cfg, 0);
	}

	public static Config synthesise(String cfgxml, int baseport) throws com.grey.base.ConfigException, java.io.IOException
	{
		XmlConfig cfg = XmlConfig.makeSection(cfgxml, "/naf");
		return new Config(cfg, baseport);
	}

	public static Config synthesise(String cfgxml) throws com.grey.base.ConfigException, java.io.IOException
	{
		return synthesise(cfgxml, 0);
	}

	private Config(XmlConfig cfg, int port) throws com.grey.base.ConfigException, java.io.IOException
	{
		cfgroot = cfg;

		XmlConfig cfgpaths = new XmlConfig(cfgroot, "dirpaths");
		path_root = getPath(cfgpaths, "root", SYSPROP_DIRPATH_ROOT, false, ".", null);
		path_conf = getPath(cfgpaths, "config", SYSPROP_DIRPATH_CONF, false, path_root+"/conf", null);
		path_var = getPath(cfgpaths, "var", SYSPROP_DIRPATH_VAR, false, path_root+"/var", null);
		path_logs = getPath(cfgpaths, "logs", SYSPROP_DIRPATH_LOGS, false, path_var+"/logs", null);
		path_tmp = getPath(cfgpaths, "tmp", SYSPROP_DIRPATH_TMP, false, path_var+"/tmp", null);

		baseport = cfgroot.getInt("baseport", true, port == 0 ? DFLT_BASEPORT : port);
		nextport = baseport + RSVPORT_MAX + 1;

		// JARs that are required to bootstrap this JVM (such as the logging framework) have to be
		// specified in Properties (see App.SYSPROP_CP) but NAFlet code can be loaded either via
		// this config item or from the same Properties path as the logger - it's up to the user.
		String cp = cfgroot.getValue("dependjars", false, null);
		cp = tokenisePaths(cp);
		if (cp != null) {
			try {
				java.util.ArrayList<java.net.URL> urls = DynLoader.load(cp);
				System.out.println("Loaded URLs="+(urls==null ? "NULL" : (urls.size()+": "+urls)));
			} catch (Throwable ex) {
				throw new com.grey.base.ConfigException(ex, "Failed to load NAF classpath");
			}
		}

		// The GreyLog logging framework may not be in use at all, but if it is, this will align the path
		// tokens in its logging.xml config file with the settings we've determined above.
		// One thing we can't do however, is link the location of logging.xml to our path_conf setting,
		// since GreyLog has probably already set its path in stone before this class ever got invoked.
		String val = SysProps.get(SYSPROP_DIRPATH_LOGS);
		if (val == null || val.length() == 0) SysProps.set(SYSPROP_DIRPATH_LOGS, path_logs);
	}

	public XmlConfig[] getDispatchers() throws com.grey.base.ConfigException
	{
		String xpath = "dispatchers/dispatcher"+XmlConfig.XPATH_ENABLED;
		return cfgroot.subSections(xpath);
	}

	public XmlConfig getDispatcher(String name) throws com.grey.base.ConfigException
	{
		if (name == null || name.length() == 0) {
			XmlConfig[] all = getDispatchers();
			return (all != null && all.length == 1 ? all[0] : null);
		}
		String xpath = "dispatchers/dispatcher[@name='"+name+"']"+XmlConfig.XPATH_ENABLED;
		XmlConfig cfg = new XmlConfig(cfgroot, xpath);
		return (cfg.exists() ? cfg : null);
	}

	public XmlConfig getNafman() throws com.grey.base.ConfigException
	{
		return new XmlConfig(cfgroot, "nafman");
	}

	public XmlConfig getDNS() throws com.grey.base.ConfigException
	{
		return new XmlConfig(cfgroot, "dnsresolver");
	}

	public String get(XmlConfig cfg, String xpath, String propnam, boolean mdty, String dflt)
			throws com.grey.base.ConfigException
	{
		if (cfg == null) cfg = cfgroot;
		if (propnam != null) dflt = SysProps.get(propnam, dflt);
		return cfg.getValue(xpath, mdty, dflt);
	}

	public XmlConfig getNode(String xpath)
			throws com.grey.base.ConfigException
	{
		return new XmlConfig(cfgroot, xpath);
	}

	public String getPath(XmlConfig cfg, String xpath, String propname, boolean mdty, String dflt, Class<?> clss)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		String path = get(cfg, xpath, propname, mdty, dflt);
		return getPath(path, clss);
	}

	public String getPath(String path, Class<?> clss)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		if (path != null && path.startsWith(PFX_CLASSPATH)) {
			java.net.URL url = DynLoader.getResource(path.substring(PFX_CLASSPATH.length()), clss);
			path = (url == null ? null : url.toString()); //url.getPath() doesn't let us reconstruct URL object, but this does
		}
		return makePath(path);
	}

	public java.net.URL getURL(XmlConfig cfg, String xpath, String propname, boolean mdty, String dflt, Class<?> clss)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		String path = getPath(cfg, xpath, propname, mdty, dflt, clss);
		if (path == null) return null;
		java.net.URL url = FileOps.makeURL(path);
		if (url == null) url = new java.io.File(path).toURI().toURL();  // must be a straight pathname, so convert to URL syntax
		return url;
	}

	public Class<?> getClass(XmlConfig cfg, Class<?> clss) throws com.grey.base.ConfigException
	{
		String cfgclass = null;
		if (cfg != null) cfgclass = cfg.getValue("@class", clss==null, null);
		try {
			if (cfgclass != null) clss = DynLoader.loadClass(cfgclass);
		} catch (Exception ex) {
			throw new com.grey.base.ConfigException(ex, "Failed to load configured class="+cfgclass+" - "+ex);
		}
		return clss;
	}

	public Object createEntity(XmlConfig cfg, Class<?> dflt_clss, Class<?> basetype, boolean hasName, Class<?>[] ctorSig, Object[] ctorArgs)
			throws com.grey.base.ConfigException
	{
		Class<?> clss = getClass(cfg, dflt_clss);
		if (hasName && cfg != null) ctorArgs[0] = cfg.getValue("@name", false, null);

		if (basetype != null && !basetype.isAssignableFrom(clss)) {
			throw new com.grey.base.ConfigException("Configured class="+clss.getName()+" is not of type "+basetype.getName());
		}
		java.lang.reflect.Constructor<?> ctor = null;
		try {
			ctor = clss.getConstructor(ctorSig);
			return ctor.newInstance(ctorArgs);
		} catch (Exception ex) {
			throw new com.grey.base.ConfigException(ex, "Failed to create configured entity="+clss.getName()+", ctor="+ctor+" - "+ex);
		}
	}

	// in reality, if we really don't care which port gets assigned, ephemeral ports assigned by the OS will always be preferred to this
	public int assignPort(int id)
	{
		if (id == RSVPORT_ANON) {
			synchronized (Config.class) {
				return nextport++;
			}
		}
		return getPort(id);
	}

	public int getPort(int id)
	{
		return baseport + id;
	}

	// NB: This is purely about constructing a path, not necessarily one that corresponds to an existing file
	private String makePath(String path) throws java.io.IOException
	{
		path = tokenisePaths(path);

		if (path != null && path.length() != 0) {
			if (path.startsWith(FileOps.URLPFX_FILE+"/")) path = path.substring(FileOps.URLPFX_FILE.length());
			java.io.File fh = new java.io.File(path);
			path = fh.getCanonicalPath();
		}
		return path;
	}

	private String tokenisePaths(String template)
	{
		if (template == null || template.length() == 0) return template;
		if (path_root != null) template = template.replace(DIRTOKEN_ROOT, path_root);
		if (path_conf != null) template = template.replace(DIRTOKEN_CONF, path_conf);
		if (path_var != null) template = template.replace(DIRTOKEN_VAR, path_var);
		if (path_logs != null) template = template.replace(DIRTOKEN_LOGS, path_logs);
		if (path_tmp != null) template = template.replace(DIRTOKEN_TMP, path_tmp);
		return template;
	}

	public void announce(com.grey.logging.Logger log)
	{
		log.info("NAF Paths - Root = "+path_root);
		log.info("NAF Paths - Config = "+path_conf);
		log.info("NAF Paths - Var = "+path_var);
		log.info("NAF Paths - Logs = "+path_logs);
		log.info("NAF Paths - Temp = "+path_tmp);
		log.info("Base Port="+baseport+", Next="+nextport);
	}
}
