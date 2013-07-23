/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

// Links:
// http://download.oracle.com/javase/1.3/docs/guide/jar/jar.html
// http://download.oracle.com/javase/tutorial/deployment/jar/packageman.html
public class PkgInfo
{
	public static String getName(Class<?> clss) {return getName(clss.getPackage().getName());}
	public static StringBuilder getVersion(Class<?> clss, StringBuilder strbuf) {return getVersion(clss.getPackage().getName(), strbuf);}
	public static StringBuilder getSummary(Class<?> clss, StringBuilder strbuf) {return getSummary(clss.getPackage().getName(), strbuf);}

	private static final StringBuilder loadedJARs = new StringBuilder();

	public static String getName(String pkgnam)
	{
		Package pkg = getVersionedPackage(pkgnam);
		String str = (pkg == null ? null : pkg.getSpecificationTitle());
		if (str != null) str = StringOps.stripQuotes(str);
		return str;
	}

	// Version qualifier will be: Dev for dev build, Beta2 for Beta 2 etc, RC1 for RC 1 etc, with nothing for Release
	public static StringBuilder getVersion(String pkgnam, StringBuilder strbuf)
	{
		Package pkg = getVersionedPackage(pkgnam);
		if (pkg == null) return null;

		String str = pkg.getSpecificationVersion();
		if (str == null) return null;
		if (strbuf == null) strbuf = new StringBuilder();
		strbuf.append(StringOps.stripQuotes(str));

		str = pkg.getImplementationVersion();
		if (str != null) strbuf.append('_').append(StringOps.stripQuotes(str));

		return strbuf;
	}

	public static StringBuilder getSummary(String pkgnam, StringBuilder strbuf)
	{
		if (strbuf == null) strbuf = new StringBuilder();
		strbuf.append(getName(pkgnam)).append(' ');
		return getVersion(pkgnam, strbuf);
	}

	public static void announceJAR(Class<?> memberclass, String nickname, String txt)
	{
		CharSequence official = com.grey.base.utils.PkgInfo.getSummary(memberclass, null);
		System.out.println("Loaded "+(official==null ? nickname:official)+(txt==null ? "":": "+txt));

		if (official != null) {
			synchronized (loadedJARs) {
				if (loadedJARs.length() != 0) {
					loadedJARs.append("; ");
				}
				loadedJARs.append(official);
			}
		}
	}

	public static String getLoadedJARs()
	{
		synchronized (loadedJARs) {
			if (loadedJARs.length() == 0) {
				return null;
			}
			return loadedJARs.toString();
		}
	}

	// Version info is only accessible at the level of a JAR's root package, so rewind to the nearest parent package (possibly this
	// one) which has a Specification-Title defined.
	// Beware of gaps in the package hierarchy if no classes exist at that level - just keep rewinding.
	private static Package getVersionedPackage(String pkgnam)
	{
		Package pkg = Package.getPackage(pkgnam);
		String title = (pkg == null ? null : pkg.getSpecificationTitle());

		if (title == null) {
			int lastdot = pkgnam.lastIndexOf('.');
			if (lastdot == -1) return null;
			return getVersionedPackage(pkgnam.substring(0, lastdot));
		}
		return Package.getPackage(pkgnam);
	}

	public static void dumpEnvironment(Class<?> clss, java.io.PrintStream strm)
	{
		if (strm == null) strm = System.out;

		ClassLoader clssld = clss.getClassLoader();
		strm.println("Class=:"+clss);
		strm.println("ClassLoader="+clssld);
	
		java.security.Provider[] secproviders = java.security.Security.getProviders();
		strm.println("Secproviders="+(secproviders==null?-1:secproviders.length));
	    for (int idx = 0; secproviders != null && idx != secproviders.length; idx++) {
	    	strm.println("\tSecProvider="+secproviders[idx].getName()+" - "+secproviders[idx]);
	    }
		java.security.ProtectionDomain protdom = clss.getProtectionDomain();
		strm.println("ProtDom:"+protdom);

		java.security.Principal[] princips = protdom.getPrincipals();
		int cnt = (princips == null ? -1 : princips.length);
		strm.println("Principals="+cnt);
	    for (int idx = 0; idx != cnt; idx++) {
	    	strm.println("\tPrincipal="+princips[idx].getName()+" - "+princips[idx]);
	    }
		java.security.CodeSource codesrc = protdom.getCodeSource();
		strm.println("CodeLocation:"+codesrc.getLocation()+"\n\t"+clssld.getResource(clss.getCanonicalName()));
		strm.println("AbsDir: "+new java.io.File(".").getAbsolutePath());
		try {
			String path = new java.io.File(".").getCanonicalPath();
			strm.println("CanonPath: "+path);
		} catch (Exception ex) {
			strm.println("Failed to obtain CanonicalPath of current dir - "+com.grey.base.GreyException.summary(ex));
		}
		strm.println("java.class.path: "+System.getProperty("java.class.path"));
		strm.println("user.home: "+System.getProperty("user.home"));
		strm.println("Directory: "+new java.io.File(".").getAbsolutePath());
	}
}
