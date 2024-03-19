/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import java.util.concurrent.atomic.AtomicInteger;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.DynLoader;
import com.grey.naf.errors.NAFConfigException;

/**
 * This class represents the NAF config for an ApplicationContextNAF instance.
 * If the application context is driven by a naf.xml style config file, then this class represents (and parses) that config file.
 */
public class NAFConfig
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

	public static final int RSVPORT_NAFMAN = 0;
	public static final int RSVPORT_ANON = -1; //not reserved at all
	private static final int RSVPORT_MAX = 5;

	public static final String PFX_CLASSPATH = "cp:";

	private final String pathRoot;
	private final String pathConf;
	private final String pathVar;
	private final String pathLogs;
	private final String pathTemp;

	private final int basePort;
	private final int threadPoolSize;
	private final XmlConfig configRoot;

	//next port number to assign - will never get used if baseport is ANON
	private final AtomicInteger nextPort = new AtomicInteger();

	private NAFConfig(Builder bldr) {
		configRoot = bldr.configRoot;
		threadPoolSize = bldr.threadPoolSize;

		try {
			pathRoot = getPath(bldr.pathRoot, null);
			pathVar = getPath(bldr.pathVar, null);
			pathTemp = getPath(bldr.pathTemp, null);
			pathLogs = getPath(bldr.pathLogs, null);
			pathConf = getPath(bldr.pathConf, null);
		} catch (Exception ex) {
			throw new NAFConfigException("Failed to set up paths", ex);
		}

		basePort = bldr.basePort;
		nextPort.set(basePort == RSVPORT_ANON ? 0 : basePort + RSVPORT_MAX + 1);

		// The GreyLog logging framework may not be in use at all, but if it is, this will align the path
		// tokens in its logging.xml config file with the settings we've determined above.
		// One thing we can't do however, is link the location of logging.xml to our pathConf setting,
		// since GreyLog has probably already set its path in stone before this class ever got invoked.
		String val = SysProps.get(SYSPROP_DIRPATH_LOGS);
		if (val == null || val.isEmpty()) SysProps.set(SYSPROP_DIRPATH_LOGS, pathLogs);
	}

	public String getPathRoot() {
		return pathRoot;
	}

	public String getPathVar() {
		return pathVar;
	}

	public String getPathTemp() {
		return pathTemp;
	}

	public String getPathLogs() {
		return pathLogs;
	}

	public String getPathConf() {
		return pathConf;
	}

	public int getThreadPoolSize() {
		return threadPoolSize;
	}

	public int getBasePort() {
		return basePort;
	}

	// if we really don't care which port gets assigned, ephemeral ports assigned by the OS will often be preferred to this
	public int assignPort(int id) {
		if (basePort == RSVPORT_ANON) return 0; //want to bind to a totally random port
		if (id == RSVPORT_ANON) return nextPort.getAndIncrement(); //want to bind to a random port within the defined baseport range
		return getPort(id);
	}

	// This should be called by clients to discover where to connect to, while assignPort() is called by listeners to bind to
	public int getPort(int id) {
		if (basePort == RSVPORT_ANON) //clients need to know server port, if baseport is randon
			throw new IllegalStateException("Reserved NAF port="+id+" is undefined when baseport is ANON");
		return basePort + id;
	}

	public boolean isAnonymousBasePort() {
		return (basePort == RSVPORT_ANON);
	}

	public XmlConfig[] getDispatchers() {
		String xpath = "dispatchers/dispatcher"+XmlConfig.XPATH_ENABLED;
		return configRoot.getSections(xpath);
	}

	public XmlConfig getDispatcherConfigNode(String name) {
		if (name == null || name.isEmpty()) {
			XmlConfig[] all = getDispatchers();
			return (all != null && all.length == 1 ? all[0] : null);
		}
		String xpath = "dispatchers/dispatcher[@name='"+name+"']"+XmlConfig.XPATH_ENABLED;
		XmlConfig cfg = getNode(xpath);
		return (cfg.exists() ? cfg : null);
	}

	public XmlConfig getNode(String xpath) {
		return configRoot.getSection(xpath);
	}

	public String getPath(XmlConfig cfg, String xpath, String propname, boolean mdty, String dflt, Class<?> clss) throws java.io.IOException
	{
		String path = get(cfg, xpath, propname, mdty, dflt);
		return getPath(path, clss);
	}

	public String getPath(String path, Class<?> clss) throws java.io.IOException
	{
		if (path != null && path.startsWith(PFX_CLASSPATH)) {
			java.net.URL url = DynLoader.getResource(path.substring(PFX_CLASSPATH.length()), clss);
			path = (url == null ? null : url.toString()); //url.getPath() doesn't let us reconstruct URL object, but this does
		}
		return makePath(path);
	}

	public java.net.URL getURL(XmlConfig cfg, String xpath, String propname, boolean mdty, String dflt, Class<?> clss) throws java.io.IOException
	{
		String path = getPath(cfg, xpath, propname, mdty, dflt, clss);
		if (path == null) return null;
		java.net.URL url = FileOps.makeURL(path);
		if (url == null) url = new java.io.File(path).toURI().toURL(); //must be a straight pathname, so convert to URL syntax
		return url;
	}

	public String get(XmlConfig cfg, String xpath, String propnam, boolean mdty, String dflt)
	{
		if (cfg == null) cfg = configRoot;
		if (propnam != null) dflt = SysProps.get(propnam, dflt);
		return cfg.getValue(xpath, mdty, dflt);
	}

	// NB: This is purely about constructing a path, not necessarily one that corresponds to an existing file
	private String makePath(String path) throws java.io.IOException
	{
		if (path == null || path.isEmpty()) return path;
		path = tokenisePaths(path);
		if (path.startsWith(FileOps.URLPFX_FILE+"/")) path = path.substring(FileOps.URLPFX_FILE.length());
		java.io.File fh = new java.io.File(path);
		path = fh.getCanonicalPath();
		return path;
	}

	private String tokenisePaths(String template)
	{
		if (template == null || template.isEmpty()) return template;
		if (pathRoot != null) template = template.replace(DIRTOKEN_ROOT, pathRoot);
		if (pathConf != null) template = template.replace(DIRTOKEN_CONF, pathConf);
		if (pathVar != null) template = template.replace(DIRTOKEN_VAR, pathVar);
		if (pathLogs != null) template = template.replace(DIRTOKEN_LOGS, pathLogs);
		if (pathTemp != null) template = template.replace(DIRTOKEN_TMP, pathTemp);
		return template;
	}


	public static Object createEntity(XmlConfig cfg, Class<?> dflt_clss, Class<?> basetype, boolean hasName, Class<?>[] ctorSig, Object[] ctorArgs)
	{
		Class<?> clss = getEntityClass(cfg, dflt_clss, basetype);
		if (hasName && cfg != null) ctorArgs[0] = cfg.getValue("@name", false, null);

		java.lang.reflect.Constructor<?> ctor = null;
		try {
			ctor = clss.getConstructor(ctorSig);
			return ctor.newInstance(ctorArgs);
		} catch (Exception ex) {
			throw new NAFConfigException("Failed to create configured entity="+clss.getName()+", ctor="+ctor, ex);
		}
	}

	public static Object createEntity(Class<?> clss, Class<?>[] ctorSig, Object[] ctorArgs)
	{
		java.lang.reflect.Constructor<?> ctor = null;
		try {
			ctor = clss.getConstructor(ctorSig);
			ctor.setAccessible(true);
			return ctor.newInstance(ctorArgs);
		} catch (Exception ex) {
			throw new NAFConfigException("Failed to create configured entity="+clss.getName()+", ctor="+ctor, ex);
		}
	}

	public static Class<?> getEntityClass(XmlConfig cfg, Class<?> dflt_clss, Class<?> basetype)
	{
		Class<?> clss = dflt_clss;
		String cfgclass = null;
		if (cfg != null) {
			cfgclass = cfg.getValue("@factory", false, null);
			if (cfgclass == null) {
				cfgclass = cfg.getValue("@class", clss==null, null);
			}
			if (cfgclass != null) {
				try {
					clss = DynLoader.loadClass(cfgclass);
				} catch (Exception ex) {
					throw new NAFConfigException("Failed to load configured class="+cfgclass, ex);
				}
			}
		}

		if (basetype != null && !basetype.isAssignableFrom(clss)) {
			throw new NAFConfigException("Configured class="+clss+" is not of type "+basetype.getName());
		}
		return clss;
	}


	public static class Builder {
		private int basePort = SysProps.get(SYSPROP_BASEPORT, 13000);
		private String pathRoot = SysProps.get(SYSPROP_DIRPATH_ROOT, ".");
		private String pathVar = SysProps.get(SYSPROP_DIRPATH_VAR, pathRoot+"/var");
		private String pathTemp = SysProps.get(SYSPROP_DIRPATH_TMP, pathVar+"/tmp");
		private String pathLogs = SysProps.get(SYSPROP_DIRPATH_LOGS, pathVar+"/logs");
		private String pathConf = SysProps.get(SYSPROP_DIRPATH_CONF, pathRoot+"/conf");
		private XmlConfig configRoot = XmlConfig.BLANKCFG;
		private int threadPoolSize = -1;

		// The param is the naf.xml config filename
		public Builder withConfigFile(String cfgpath) {
			XmlConfig cfg = XmlConfig.getSection(cfgpath, "naf");
			return withXmlConfig(cfg);
		}

		public Builder withXmlConfig(XmlConfig cfg) {
			configRoot = cfg;
			basePort = cfg.getInt("baseport", false, basePort);
			threadPoolSize = cfg.getInt("threadpoolsize", false, threadPoolSize);

			XmlConfig cfgpaths = cfg.getSection("dirpaths");
			pathRoot = cfgpaths.getValue("root", false, pathRoot);
			pathConf = cfgpaths.getValue("config", false, pathRoot+"/conf");
			pathVar = cfgpaths.getValue("var", false, pathRoot+"/var");
			pathLogs = cfgpaths.getValue("logs", false, pathVar+"/logs");
			pathTemp = cfgpaths.getValue("tmp", false, pathVar+"/tmp");			
			return this;
		}

		public Builder withBasePort(int v) {
			basePort = v;
			return this;
		}

		public Builder withPathRoot(String v) {
			pathRoot = v;
			return this;
		}

		public Builder withPathVar(String v) {
			pathVar = v;
			return this;
		}

		public Builder withPathTemp(String v) {
			pathTemp = v;
			return this;
		}

		public Builder withPathLogs(String v) {
			pathLogs = v;
			return this;
		}

		public Builder withPathConf(String v) {
			pathConf = v;
			return this;
		}

		public Builder withThreadPoolSize(int v) {
			threadPoolSize = v;
			return this;
		}

		public NAFConfig build() {
			return new NAFConfig(this);
		}
	}
}
