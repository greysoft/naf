/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.config;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.grey.base.utils.IntValue;
import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;

public class SysProps
{
	private static final Map<String,String> AppEnv = new ConcurrentHashMap<>(); //primarily intended for the benefit of tests

	public static final String NULLMARKER = "-";  // placeholder value that translates to null - prevents us traversing a chain of defaults
	public static final String EOL = System.getProperty("line.separator", "\n");
	public static final String DirSep = System.getProperty("file.separator", "/");
	public static final String PathSep = System.getProperty("path.separator", ":");

	public static final String SYSPROP_DIRPATH_TMP = "grey.paths.tmp";
	public static final String DIRTOKEN_TMP = "%DIRTMP%";
	public static final String TMPDIR = getTempDir();

	public static final boolean isWindows = System.getProperty("os.name", "").startsWith("Windows");

	public static final int JAVA_VERSION_MAJOR;

	static {
		String ver = System.getProperty("java.version");
		int dot1 = ver.indexOf('.');
		int dot2 = ver.indexOf('.', dot1+1);
		JAVA_VERSION_MAJOR = (int)IntValue.parseDecimal(ver, dot1+1, dot2-dot1-1);
		loadGreyProps();
	}

	public static String get(String name)
	{
		return get(name, null);
	}

	// As long as people access the system props via this method, they're guaranteed to see the LoadGreyProps() overrides
	public static String get(String name, String dflt)
	{
		String envName = name.replace('.', '_').toUpperCase();
		String val = AppEnv.get(envName);
		if (val == null || val.isEmpty()) val = System.getenv(envName);
		if (val == null || val.isEmpty()) val = System.getProperty(name);
		if (val == null || val.isEmpty()) val = dflt;
		if (val == null || val.isEmpty() || NULLMARKER.equals(val)) val = null;
		return val;
	}

	public static boolean get(String name, boolean dflt)
	{
		return StringOps.stringAsBool(get(name, StringOps.boolAsString(dflt)));
	}

	public static int get(String name, int dflt)
	{
		return Integer.parseInt(get(name, Integer.toString(dflt)));
	}

	public static long getTime(String name, long dflt)
	{
		String val = get(name, Long.toString(dflt));
		return TimeOps.parseMilliTime(val);
	}

	public static long getTime(String name, String dflt)
	{
		long msecs = com.grey.base.utils.TimeOps.parseMilliTime(dflt);
		return getTime(name, msecs);
	}

	public static String set(String name, String newval)
	{
		java.util.Properties props = System.getProperties();
		String oldval = (newval == null || newval.isEmpty() ? (String)props.remove(name) : (String)props.setProperty(name, newval));
		if (oldval != null && oldval.isEmpty()) oldval = null;
		return oldval;
	}

	public static boolean set(String name, boolean val)
	{
		String oldval = set(name, StringOps.boolAsString(val));
		return StringOps.stringAsBool(oldval);
	}

	public static int set(String name, int val)
	{
		String oldval = set(name, Integer.toString(val));
		return (oldval == null ? 0 : Integer.parseInt(oldval));
	}

	public static long setTime(String name, long val)
	{
		String oldval = set(name, Long.toString(val));
		return (oldval == null ? 0L : TimeOps.parseMilliTime(oldval));
	}

	public static void setAppEnv(String name, String val) {
		name = name.toUpperCase();
		if (val == null || val.isEmpty()) {
			AppEnv.remove(name);
		} else {
			AppEnv.put(name, val);
		}
	}

	public static void clearAppEnv() {
		AppEnv.clear();
	}

	public static Map<String,String> getAppEnv() {
		return Collections.unmodifiableMap(AppEnv);
	}

	public static java.util.Properties load(String pthnam) throws java.io.IOException
	{
		java.io.File fh = new java.io.File(pthnam);
		if (!fh.exists()) return null;
		java.util.Properties props = new java.util.Properties();
		java.io.FileInputStream strm = new java.io.FileInputStream(fh);
		try {
			props.load(strm);
		} finally {
			strm.close();
		}
		return props;
	}

	public static String[] sort(java.util.Properties props)
	{
		String[] parr = new String[props.size()];
		java.util.Set<Object> pset = props.keySet();
		java.util.Iterator<Object> pit = pset.iterator();
		int idx = 0;
		while (pit.hasNext()) {
			parr[idx++] = (String)pit.next();
		}
		java.util.Arrays.sort(parr, String.CASE_INSENSITIVE_ORDER);
		return parr;
	}

	public static String[] dump(java.util.Properties props, java.io.PrintStream strm)
	{
		String[] parr = sort(props);
		if (strm != null) {
			strm.println("Properties = "+parr.length+":");
			for (int idx2 = 0; idx2 != parr.length; idx2++) {
				strm.println("- "+parr[idx2]+" = "+SysProps.get(parr[idx2]));
			}
		}
		return parr;
	}

	private static void loadGreyProps()
	{
		String pthnam = get("grey.properties");
		if (pthnam == null) {
			String[] huntpath = new String[]{"./grey.properties", "./conf/grey.properties",
					System.getProperty("user.home", ".")+"/grey.properties"};
			for (int idx = 0; idx != huntpath.length; idx++) {
				if (huntpath[idx] == null) continue;
				java.io.File fh = new java.io.File(huntpath[idx]);
				if (fh.exists()) {
					pthnam = fh.getAbsolutePath();
					break;
				}
			}
		}
		//PkgInfo will fail to get a handle on the root GreyBase package if none of its immediate member
		//classes have been loaded yet, so reference one of them to make sure announceJAR() succeeds.
		Class<?> clss = com.grey.base.ExceptionUtils.class;
		java.util.Properties props = null;
		try {
			String txt;
			if (pthnam == null || pthnam.isEmpty() || NULLMARKER.equals(pthnam)) {
				txt = "No Grey-Properties found";
			} else {
				props = load(pthnam);
				if (props == null) {
					txt = "Grey-Properties file="+pthnam+" not found";
				} else {
					txt = "Grey-Properties="+props.size()+" loaded from "+pthnam;
				}
			}
			com.grey.base.utils.PkgInfo.announceJAR(clss, "greybase", txt+" - Java="+JAVA_VERSION_MAJOR+"/"+System.getProperty("java.version"));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load Grey-Properties from "+pthnam, ex);
		}
		if (props != null) System.getProperties().putAll(props);
	}

	private static String getTempDir()
	{
		String dflt = System.getProperty("java.io.tmpdir", System.getProperty("user.home", "/")+"/tmp");
		return System.getProperty(SYSPROP_DIRPATH_TMP, dflt);
	}
}
