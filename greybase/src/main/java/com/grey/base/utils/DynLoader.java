/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public class DynLoader
{
	// classpath is delimited by standard OS separator, eg ":" on UNIX, "," on Windows
	static public final String CPDLM = SysProps.get("grey.path.separator", ":");
	static public final boolean CLDHACK = SysProps.get("grey.classloader.hack", false);

	// Note that the naive Class.forName(classname) uses the class loader that loaded the current class (probably the system classloader),
	// which may produce unexpected results in alien managed environments.
	// More precisely: Class.forName("foo") = Class.forName("Foo", true, this.getClass().getClassLoader())
	// The difference between this and ClassLoader.loadClass() is that the latter delays initialisation (ie. execution of static initialisers)
	// until the class is used for the first time.
	public static Class<?> loadClass(String targetclass)
			throws java.net.MalformedURLException, ClassNotFoundException
	{
		ClassLoader cld = getClassLoader();
		return Class.forName(targetclass, true, cld);
	}

	public static java.net.URL getResource(String path)
	{
		return getLoaderResource(path, getClassLoader());
	}

	public static java.net.URL getResource(String path, Class<?> clss)
	{
		if (clss == null) return getResource(path);
		return clss.getResource(path);
	}

	public static java.net.URL getLoaderResource(String path, ClassLoader cld)
	{
		java.net.URL url = (cld == null ? null : cld.getResource(path));

		if (url == null) {
			ClassLoader cld_thrd = getClassLoader();
			if (cld_thrd != null && cld_thrd != cld) url = cld_thrd.getResource(path);
			if (url == null) {
				ClassLoader cld_sys = ClassLoader.getSystemClassLoader();
				if (cld_sys != null && cld_sys != cld_thrd) url = cld_sys.getResource(path);
			}
		}
		return url;
	}

	public static java.net.URL getResource(CharSequence stem, boolean classpfx, Class<?> clss)
	{
		String name = (classpfx ? clss.getSimpleName()+"-"+stem : stem.toString());
		return getResource(name, clss);
	}

	public static java.util.ArrayList<java.net.URL> loadFromDir(String dirpath)
			throws java.io.IOException, NoSuchMethodException,
				IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		java.io.File dh = new java.io.File(dirpath);
		java.io.File[] jarfiles = getJARs(dh);
		if (jarfiles == null) return null;
		return load(java.util.Arrays.asList(jarfiles));
	}

	public static java.util.ArrayList<java.net.URL> load(String cp)
			throws java.io.IOException, NoSuchMethodException,
				IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		return load(cp.split(CPDLM));
	}

	public static java.util.ArrayList<java.net.URL> load(String[] cp)
			throws java.io.IOException, NoSuchMethodException,
				IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		java.util.ArrayList<java.io.File> expandedcp = new java.util.ArrayList<java.io.File>();

		for (int idx = 0; idx != cp.length; idx++) {
			String path = cp[idx].trim();
			if (path.length() == 0) continue;
			java.io.File fh = new java.io.File(path);
			if (fh.isDirectory()) {
				if (path.charAt(path.length() - 1) == '/') {
					// load any JAR files within this directory
					java.io.File[] jarfiles = getJARs(fh);
					if (jarfiles != null) {
						for (int idx2 = 0; idx2 != jarfiles.length; idx2++) {
							expandedcp.add(jarfiles[idx2]);
						}
					}
				} else {
					// just put the directory path itself on the classpath
					expandedcp.add(fh);
				}
			} else {
				if (fh.exists()) expandedcp.add(fh);
			}
		}
		return load(expandedcp);
	}

	// The cp arg consists of file or directory pathnames that are known to exist, and
	// are to be individually added to our classpath without further expansion (but
	// with possible contraction, ie. some can be filtered out).
	private static java.util.ArrayList<java.net.URL> load(java.util.List<java.io.File> cp)
			throws java.io.IOException, NoSuchMethodException,
				IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		ClassLoader cld = getClassLoader();
		java.util.HashSet<java.net.URL> cp_existing = getClassPath(cld);
		java.util.ArrayList<java.net.URL> cp_extra = new java.util.ArrayList<java.net.URL>();

		// Weed out duplicates
		for (int idx = 0; idx != cp.size(); idx++) {
			java.net.URL url = cp.get(idx).toURI().toURL();
			if (cp_existing.contains(url) || cp_extra.contains(url)) continue;
			cp_extra.add(url);
		}
		if (cp_extra.size() == 0) return null;

		// Dynamically add the URLs to our existing classpath
		if (CLDHACK) {
			// popular but presumptious procedure - allow it to be enabled for troubleshooting.
			// In fact, it turns out SLF4J won't find bridging JARs loaded without this hack.
			// Also note that this breaks our unit tests.
		    cld = (java.net.URLClassLoader)ClassLoader.getSystemClassLoader();
			Class<?> clss = java.net.URLClassLoader.class;
		    java.lang.reflect.Method method = clss.getDeclaredMethod("addURL", new Class[]{java.net.URL.class});
		    method.setAccessible(true);
		    for (int idx = 0; idx != cp_extra.size(); idx++) {
			    method.invoke(cld, new Object[]{cp_extra.get(idx)});
		    }
		} else {
			java.net.URL[] arr = cp_extra.toArray(new java.net.URL[cp_extra.size()]);
			cld = new java.net.URLClassLoader(arr, cld);
			Thread.currentThread().setContextClassLoader(cld);
		}
		return cp_extra;
	}

	public static boolean unload(ClassLoader cld)
	{
		if (cld == null) return false;
		ClassLoader live = getClassLoader();
		while (live != cld) {
			if (live == null) return false;
			live = live.getParent();
		}
		Thread.currentThread().setContextClassLoader(live.getParent());
		return true;
	}

	public static String getClassLoaderPath(String rsrcname, Class<?> clss)
	{
		return clss.getPackage().getName().replace('.', '/')+"/"+rsrcname;
	}

	public static Object getField(Class<?> clss, String fldnam, Object obj)
	{
		try {
			java.lang.reflect.Field fld = clss.getDeclaredField(fldnam);
			fld.setAccessible(true);
			return fld.get(obj);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get field="+clss.getName()+":"+fldnam+", Object="+obj, ex);
		}
	}

	public static void setField(Class<?> clss, String fldnam, Object fldval, Object obj)
	{
		try {
			java.lang.reflect.Field fld = clss.getDeclaredField(fldnam);
			fld.setAccessible(true);
			java.lang.reflect.Field meta = java.lang.reflect.Field.class.getDeclaredField("modifiers");
			if ((fld.getModifiers() & java.lang.reflect.Modifier.FINAL) != 0) {
				meta.setAccessible(true);
				meta.setInt(fld, fld.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
			}
			fld.set(obj, fldval);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to set field="+clss.getName()+":"+fldnam+", Object="+obj, ex);
		}
	}

	public static Object getField(Object obj, String fldnam)
	{
		return getField(obj.getClass(), fldnam, obj);
	}

	public static void setField(Object obj, String fldnam, Object fldval)
	{
		setField(obj.getClass(), fldnam, fldval, obj);
	}

	protected static ClassLoader getClassLoader()
	{
		ClassLoader cld = Thread.currentThread().getContextClassLoader(); // gets class loader for current environment
		if (cld == null) cld = DynLoader.class.getClassLoader();  	  // this is typically ClassLoader.getSystemClassLoader()
		return cld;
	}

	private static java.util.HashSet<java.net.URL> getClassPath(ClassLoader cld)
	{
		java.util.HashSet<java.net.URL> cp = new java.util.HashSet<java.net.URL>();
		do {
			if (!(cld instanceof java.net.URLClassLoader)) break;
			java.net.URL[] urls = ((java.net.URLClassLoader)cld).getURLs();
			if (urls != null) {
				for (int idx = 0; idx != urls.length; idx++) {
					cp.add(urls[idx]); 
				}
			}
			cld = cld.getParent();
		} while (cld != null);

		return cp;
	}

	private static java.io.File[] getJARs(java.io.File dh)
	{
		FileOps.Filter_EndsWith filter = new FileOps.Filter_EndsWith(new String[]{".jar"}, true, false, false);
		java.io.File[] jarfiles = dh.listFiles(filter);
		if (jarfiles != null && jarfiles.length == 0) jarfiles = null;
		return jarfiles;
	}

	public static byte[] readBinaryResource(String path, Class<?> clss)
	{
		try {
			return FileOps.readResource(path, clss);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to read resource="+path+" as class="+clss+" - "+ex, ex);
		}
	}

	public static String readTextResource(String path, Class<?> clss)
	{
		byte[] buf = readBinaryResource(path, clss);
		try {
			return StringOps.convert(buf, null);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to convert resource="+clss+"/"+path+" to text"+ex, ex);
		}
	}
}
