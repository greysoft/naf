/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.config;

import com.grey.base.utils.StringOps;
import com.grey.base.utils.TimeOps;

public class SysProps
{
	static {
		loadGreyProps();
	}
	public static final String EOL = get("line.separator", "\n");
	public static final String DirSep = get("file.separator", "/");
	public static final String PathSep = get("path.separator", ":");

	public static final String SYSPROP_DIRPATH_TMP = "grey.paths.tmp";
	public static final String DIRTOKEN_TMP = "%DIRTMP%";
	public static final String TMPDIR = getTempDir();

	public static String get(String name)
	{
		return get(name, null);
	}

	// As long as people access the system props via this method, they're guaranteed to see the LoadGreyProps() overrides
	public static String get(String name, String dflt)
	{
		return System.getProperty(name, dflt);
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
		return (newval == null ? (String)props.remove(name) : (String)props.setProperty(name, newval));
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

	// NB: Can't call this class's get() methods here, as they refer to the override properties which
	// this method exists to load.
	private static void loadGreyProps()
	{
		String sysprop_override = "grey.properties";
		String pthnam = System.getProperty(sysprop_override);
		if (pthnam == null) {
			String[] huntpath = new String[]{"./grey.properties",
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
		Class<?> clss = com.grey.base.GreyException.class;
		if (pthnam == null || pthnam.equals("-")) {
			com.grey.base.utils.PkgInfo.announceJAR(clss, "greybase", "No extra properties loaded - "+sysprop_override+"="+pthnam);
			return;
		}
		java.util.Properties props = null;
		try {
			props = load(pthnam);
			com.grey.base.utils.PkgInfo.announceJAR(clss, "greybase", "Set properties="+props.size()+" from "+pthnam);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load properties from "+pthnam, ex);
		}
		if (props.size() != 0) System.getProperties().putAll(props);
	}

	private static String getTempDir()
	{
		String home = System.getProperty("user.home");
		String dflt = (home == null ? System.getProperty("java.io.tmpdir") : home+"/tmp");
		return System.getProperty(SYSPROP_DIRPATH_TMP, dflt);
	}
}
