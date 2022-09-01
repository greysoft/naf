/*
 * Copyright 2010-2022 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public final class DynLoaderTest
{
	private static final boolean announce = com.grey.base.config.SysProps.get("grey.test.dynload.announce", false);

	private static final int F_CLDHACK = 1 << 0;
	private static final int F_SYSLOAD = 1 << 1;

	private final String workdir = SysProps.TMPDIR+"/utest/"+getClass().getSimpleName();

	//these are for the testSymbolsArray() test - want mixture of public, private, static and fields/methods
	private static final byte PREFIX1_ONE = 1;
	private static final byte PREFIX1_TWO = 2;
	private static final byte PREFIX1_TWELVE = 12;
	private static final byte PREFIX1_maxminus1 = (byte)200;
	public String PREFIX1_fld1;
	public final int PREFIX1_fld2 = 99;
	private static final int prefix1_maxval = 201;
	private static final String[] prefix1_txt = DynLoader.generateSymbolNames(DynLoaderTest.class, "PREFIX1_", prefix1_maxval);
	private static String PREFIX1_get(byte id) {return prefix1_txt[id & 0xff];}

	//these are for the testSymbolsMap() test - want same mixture of types as above
	private static final int PREFIX2_ZERO = 0;
	private static final int PREFIX2_ONE = 1;
	private static final int PREFIX2_BIG = 299;
	private static final int PREFIX2_HUGE = Integer.MAX_VALUE;
	private static final int PREFIX2_TINY = Integer.MIN_VALUE;
	private static final int PREFIX2_NEG = -9001;
	public String PREFIX2_fld1;
	public final int PREFIX2_fld2 = 99;
	private static final com.grey.base.collections.HashedMapIntKey<String> prefix2_txt = DynLoader.generateSymbolNamess(DynLoaderTest.class, "PREFIX2_");
	private static String PREFIX2_get(int id) {return prefix2_txt.get(id);}

	@org.junit.Test
	public void testSymbolsArray()
	{
		org.junit.Assert.assertEquals(prefix1_maxval+1, prefix1_txt.length);
		org.junit.Assert.assertEquals("0", PREFIX1_get((byte)0));
		org.junit.Assert.assertEquals(Integer.toString(prefix1_maxval), PREFIX1_get((byte)prefix1_maxval));
		org.junit.Assert.assertEquals("99", PREFIX1_get((byte)99));
		org.junit.Assert.assertEquals("ONE", PREFIX1_get(PREFIX1_ONE));
		org.junit.Assert.assertEquals("TWO", PREFIX1_get(PREFIX1_TWO));
		org.junit.Assert.assertEquals("TWELVE", PREFIX1_get(PREFIX1_TWELVE));
		org.junit.Assert.assertEquals("maxminus1", PREFIX1_get(PREFIX1_maxminus1));
		int symcnt = 0;
		for (int idx = 0; idx != prefix1_txt.length; idx++) {
			org.junit.Assert.assertNotNull(prefix1_txt[idx]);
			if (!Integer.toString(idx).equals(prefix1_txt[idx])) symcnt++;
		}
		org.junit.Assert.assertEquals(4, symcnt);
	}

	@org.junit.Test
	public void testSymbolsMap()
	{
		org.junit.Assert.assertEquals(6, prefix2_txt.size());
		org.junit.Assert.assertEquals("ZERO", PREFIX2_get(PREFIX2_ZERO));
		org.junit.Assert.assertEquals("ONE", PREFIX2_get(PREFIX2_ONE));
		org.junit.Assert.assertEquals("BIG", PREFIX2_get(PREFIX2_BIG));
		org.junit.Assert.assertEquals("HUGE", PREFIX2_get(PREFIX2_HUGE));
		org.junit.Assert.assertEquals("TINY", PREFIX2_get(PREFIX2_TINY));
		org.junit.Assert.assertEquals("NEG", PREFIX2_get(PREFIX2_NEG));
		org.junit.Assert.assertNull(PREFIX2_get(99));
		org.junit.Assert.assertNull(PREFIX2_get(Integer.MAX_VALUE - 1));
		org.junit.Assert.assertNull(PREFIX2_get(-3));
	}

	@org.junit.Test
	public void testPath() throws java.net.MalformedURLException, ClassNotFoundException
	{
		String pkgspec = getClass().getPackage().getName().replace('.', '/')+"/";
		String rootrsrc = "RootResource.txt";
		String pkgrsrc = "DynLoader.txt";

		// This initial bit doesn't actually test any project code, but sanity-checks my understanding
		String rsrc = rootrsrc;
		String[] paths = new String[]{rsrc, null, "/"+rsrc};
		String[] expect = new String[]{"N", null, "Y",
				"Y", null, "N",
				"Y", null, "N"};
		String fails = verifyPaths(paths, expect);
		rsrc = pkgrsrc;
		paths = new String[]{rsrc, pkgspec+rsrc, "/"+pkgspec+rsrc};
		expect = new String[]{"Y", "N", "Y",
				"N", "Y", "N",
				"N", "Y", "N"};
		fails += verifyPaths(paths, expect);
		if (fails.length() != 0) System.out.println("Resource Errors: "+fails);
		org.junit.Assert.assertEquals(0, fails.length());

		org.junit.Assert.assertSame(String.class, Class.forName(String.class.getName()));
		org.junit.Assert.assertSame(String.class, DynLoader.loadClass(String.class.getName()));

		// now test the project code
		rsrc = rootrsrc;
		java.net.URL url = DynLoader.getResource(rsrc);
		org.junit.Assert.assertNotNull(url);
		url = DynLoader.getLoaderResource(rsrc, null);
		org.junit.Assert.assertNotNull(url);
		url = DynLoader.getResource(rsrc, getClass());
		org.junit.Assert.assertNull(url);
		url = DynLoader.getResource("/"+rsrc);
		org.junit.Assert.assertNull(url);
		url = DynLoader.getLoaderResource("/"+rsrc, null);
		org.junit.Assert.assertNull(url);
		url = DynLoader.getResource("/"+rsrc, getClass());
		org.junit.Assert.assertNotNull(url);
		rsrc = pkgrsrc;
		url = DynLoader.getResource(rsrc);
		org.junit.Assert.assertNull(url);
		url = DynLoader.getLoaderResource(rsrc, null);
		org.junit.Assert.assertNull(url);
		url = DynLoader.getResource(rsrc, getClass());
		org.junit.Assert.assertNotNull(url);
		url = DynLoader.getResource(pkgspec+rsrc);
		org.junit.Assert.assertNotNull(url);
		url = DynLoader.getLoaderResource(pkgspec+rsrc, null);
		org.junit.Assert.assertNotNull(url);
		url = DynLoader.getResource(pkgspec+rsrc, getClass());
		org.junit.Assert.assertNull(url);
		url = DynLoader.getResource("/"+pkgspec+rsrc);
		org.junit.Assert.assertNull(url);
		url = DynLoader.getLoaderResource("/"+pkgspec+rsrc, null);
		org.junit.Assert.assertNull(url);
		url = DynLoader.getResource("/"+pkgspec+rsrc, getClass());
		org.junit.Assert.assertNotNull(url);
	}

	@org.junit.Ignore //some ancient misbegotten nonsense that no longer works on Java 11
	@org.junit.Test
	public void testLoad() throws ClassNotFoundException, java.io.IOException, java.net.URISyntaxException, NoSuchMethodException,
		IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		String fails = verifyLoad("DynTest1", 0, true);
		ClassLoader cld_before2 = DynLoader.getClassLoader();
		fails += verifyLoad("DynTest2", F_SYSLOAD, false);
		ClassLoader cld_after2 = DynLoader.getClassLoader();
		fails += verifyLoad("DynTest3", F_CLDHACK, true);
		fails += verifyLoad("DynTest4", F_CLDHACK | F_SYSLOAD, true);
		org.junit.Assert.assertTrue(fails, fails.isEmpty());

		//now verify that a repeat load of DynTest4 would have no effect
		boolean prevhack = DynLoader.CLDHACK;
		DynLoader.setField(DynLoader.class, "CLDHACK", Boolean.valueOf(false), null);
		org.junit.Assert.assertSame(cld_after2, DynLoader.getClassLoader());
		String loadpath = getResourcePath("DynTest4.jar", getClass());
		java.util.List<java.net.URL> newcp = DynLoader.load(loadpath);
		org.junit.Assert.assertTrue(newcp==null?null:newcp.toString(), newcp == null || newcp.size() == 0);
		org.junit.Assert.assertTrue(DynLoader.getClassLoader() == cld_after2);
		//same for DynTest2, which was the last JAR we loaded without the hack
		loadpath = getResourcePath("DynTest2.jar", getClass());
		newcp = DynLoader.load(loadpath);
		org.junit.Assert.assertTrue(newcp == null || newcp.size() == 0);
		org.junit.Assert.assertTrue(DynLoader.getClassLoader() == cld_after2);
		DynLoader.setField(DynLoader.class, "CLDHACK", Boolean.valueOf(prevhack), null);
		//can still access all classes
		DynLoader.loadClass("greytest.DynTest1");
		DynLoader.loadClass("greytest.DynTest2");
		DynLoader.loadClass("greytest.DynTest3");
		DynLoader.loadClass("greytest.DynTest4");

		//unload the final JAR that was loaded via the context loader
		String classname = "greytest.DynTest2";
		DynLoader.loadClass(classname);
		boolean unloaded = DynLoader.unload(cld_after2);
		org.junit.Assert.assertTrue(unloaded);
		org.junit.Assert.assertTrue(cld_before2 == DynLoader.getClassLoader());
		try {
			DynLoader.loadClass(classname);
			org.junit.Assert.fail("Class="+classname+" still present after unloading JAR");
		} catch (ClassNotFoundException ex) {}

		//try to unload a classloader that's not present in the hierarchy
		unloaded = DynLoader.unload(cld_after2);
		org.junit.Assert.assertFalse(unloaded);
		org.junit.Assert.assertTrue(cld_before2 == DynLoader.getClassLoader());
		//and now verify Null has no effect
		unloaded = DynLoader.unload(null);
		org.junit.Assert.assertFalse(unloaded);
		org.junit.Assert.assertTrue(cld_before2 == DynLoader.getClassLoader());

		//can still access the other classes
		DynLoader.loadClass("greytest.DynTest1");
		DynLoader.loadClass("greytest.DynTest3");
		DynLoader.loadClass("greytest.DynTest4");
	}

	@org.junit.Test
	public void testLoadDirScan()
		throws ClassNotFoundException, java.io.IOException, java.net.URISyntaxException, NoSuchMethodException,
			IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		boolean hackflag = DynLoader.CLDHACK;
		DynLoader.setField(DynLoader.class, "CLDHACK", Boolean.valueOf(false), null);

		// populate a directory tree which we've ensured was previously empty
		FileOps.deleteDirectory(workdir);
		java.io.File dh = new java.io.File(workdir);
		org.junit.Assert.assertFalse(dh.exists());
		FileOps.ensureDirExists(workdir+"/dir1");
		FileOps.ensureDirExists(workdir+"/dir2");
		FileOps.ensureDirExists(workdir+"/dir3");
		FileOps.ensureDirExists(workdir+"/dir4");
		FileOps.writeTextFile(workdir+"/file1.jar", "jar1");
		FileOps.writeTextFile(workdir+"/file2.txt", "txt2");
		FileOps.writeTextFile(workdir+"/dir1/file11.jar", "jar11");
		FileOps.writeTextFile(workdir+"/dir2/file21.jar", "jar21");
		FileOps.writeTextFile(workdir+"/dir2/file22.jar", "jar22");
		FileOps.writeTextFile(workdir+"/dir2/file23.txt", "txt23");
		FileOps.writeTextFile(workdir+"/dir2/file24", "notype24");
		FileOps.writeTextFile(workdir+"/dir3/file31.jar", "jar31");
		FileOps.writeTextFile(workdir+"/dir4/file41.txt", "jar41");

		// Construct a path which consists of a mixture of simple directory paths, directories to be scanned
		// and individual files.
		// Then load it into the classpath - the subsequent tests have no dependence on the hack-flag setting.
		String loadpath = workdir+"/"+DynLoader.CPDLM
				+workdir+"/dir1"+DynLoader.CPDLM
				+workdir+"/dir2/"+DynLoader.CPDLM
				+workdir+"/dir3/file31.jar"+DynLoader.CPDLM
				+workdir+"/dir4/"+DynLoader.CPDLM
				+".";
		java.util.List<java.net.URL> newcp = DynLoader.load(loadpath);
		org.junit.Assert.assertTrue(newcp != null && newcp.size() == 6);
		String pos2 = workdir+"/dir2/file21.jar";
		String pos3 = workdir+"/dir2/file22.jar";
		if (!comparePath(pos2, newcp.get(2))) {
			// dir2 contents are arbitrarily sorted, so if the 2 JARs aren't in the one order, they will be in the other
			String tmp = pos2;
			pos2 = pos3;
			pos3 = tmp;
		}
		assertPath(workdir+"/file1.jar", newcp.get(0));
		assertPath(workdir+"/dir1", newcp.get(1));
		assertPath(pos2, newcp.get(2));
		assertPath(pos3, newcp.get(3));
		assertPath(workdir+"/dir3/file31.jar", newcp.get(4));
		assertPath(".", newcp.get(5));

		if (!hackflag) {
			DynLoader.unload(DynLoader.getClassLoader());
			// now test scanning a specific directory only
			newcp = DynLoader.loadFromDir(workdir+"/");
			org.junit.Assert.assertTrue(newcp==null?"Null":newcp.toString(), newcp != null && newcp.size() == 1);
			assertPath(workdir+"/file1.jar", newcp.get(0));
			if (!hackflag) DynLoader.unload(DynLoader.getClassLoader());
		}

		// we're now finished with out temp work area
		FileOps.deleteDirectory(workdir);
		org.junit.Assert.assertFalse(dh.exists());

		// now test loading from non-existent directory - disable hack so we can test classloader state as well
		boolean prevhack = DynLoader.CLDHACK;
		DynLoader.setField(DynLoader.class, "CLDHACK", Boolean.valueOf(false), null);
		ClassLoader cld = DynLoader.getClassLoader();
		newcp = DynLoader.loadFromDir(workdir);
		org.junit.Assert.assertTrue(newcp == null || newcp.size() == 0);
		org.junit.Assert.assertTrue(DynLoader.getClassLoader() == cld);

		// not actually a directory-scan, but this is a good place to test loading a file we know can't exist
		newcp = DynLoader.load(workdir+"/nonsuch.jar");
		org.junit.Assert.assertTrue(newcp == null || newcp.size() == 0);
		org.junit.Assert.assertTrue(DynLoader.getClassLoader() == cld);
		DynLoader.setField(DynLoader.class, "CLDHACK", Boolean.valueOf(prevhack), null);
	}

	private String verifyLoad(String target, int flags, boolean expect)
			throws ClassNotFoundException, java.io.IOException, java.net.URISyntaxException, NoSuchMethodException,
				IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		boolean hack = ((flags & F_CLDHACK) != 0);
		boolean sysload = ((flags & F_SYSLOAD) != 0);
		String failmsg = "";
		String targetjar = target+".jar";
		String targetclass = "greytest."+target;
		ClassLoader prev_cld = DynLoader.getClassLoader();
		//make sure not yet loaded
		try {
			Class.forName(targetclass);
			org.junit.Assert.fail("Target class already loaded at system level - "+targetjar+":"+targetclass);
		} catch (ClassNotFoundException ex) {}
		try {
			Class.forName(targetclass, true, prev_cld);
			org.junit.Assert.fail("Target class already loaded in current context - "+targetjar+":"+targetclass);
		} catch (ClassNotFoundException ex) {}
		try {
			DynLoader.loadClass(targetclass);
			org.junit.Assert.fail("Target class already loaded in DynLoader - "+targetjar+":"+targetclass);
		} catch (ClassNotFoundException ex) {}

		// Now load the JAR and verify we can access its member class
		boolean prevhack = DynLoader.CLDHACK;
		DynLoader.setField(DynLoader.class, "CLDHACK", Boolean.valueOf(hack), null);
		String loadpath = getResourcePath(targetjar, getClass());
		String cp = loadpath+" "+DynLoader.CPDLM+"  "+loadpath;  //double up to test duplicates detection and add white space
		cp += DynLoader.CPDLM+DynLoader.CPDLM;  //empty path elements
		cp += DynLoader.CPDLM+"/tmpx2/yyydir/n0nzushf1le";  //missing file
		cp += DynLoader.CPDLM+"/tmpx2/yyydir/n0nzushf1le/";  //missing file that looks like a directory spec
		java.util.List<java.net.URL> newcp = DynLoader.load(cp);
		org.junit.Assert.assertTrue(newcp != null && newcp.size() == 1);
		assertPath(loadpath, newcp.get(0));
		ClassLoader cld = DynLoader.getClassLoader();
		org.junit.Assert.assertTrue(hack ? cld == prev_cld : cld != prev_cld);
		DynLoader.setField(DynLoader.class, "CLDHACK", Boolean.valueOf(prevhack), null);
		boolean success;
		String diag = "";

		try {
			Class<?> clss = null;
			if (sysload) {
				clss = Class.forName(targetclass);
			} else {
				clss = Class.forName(targetclass, true, cld);
			}
			Object obj = clss.newInstance();
			org.junit.Assert.assertEquals(targetclass, obj.getClass().getName());
			// verify that our own class resolver can access any class the above forName() calls can
			org.junit.Assert.assertSame(clss, DynLoader.loadClass(targetclass));
			success = expect;
		} catch (Throwable ex) {
			success = !expect;
			diag = " - "+ex.toString();
		}
		if (!success) {
			String label = target+"/hack="+hack+"/sys="+sysload+"/load="+(diag.length()==0);
			failmsg = label+"; ";
			System.out.println("ERROR on "+targetjar+":"+targetclass+" - "+label+diag);
		}
		return failmsg;
	}

	private String verifyPaths(String[] paths, String[] expect)
	{
		String fails = "";
		for (int idx = 0; idx != expect.length; idx++) {
			String path = paths[idx % 3];
			if (path == null) continue;
			String opdesc = "Class";
			java.net.URL url = null;
			if (idx < 3) {
				url = getClass().getResource(path);
			} else if (idx < 6) {
				opdesc = "CLD";
				url = getClass().getClassLoader().getResource(path);
			} else {
				opdesc = "Sys";
				url = ClassLoader.getSystemResource(path);
			}
			String label = opdesc+":"+path;
			if (announce) System.out.println(label+" => "+(url==null?"NULL":url.getPath()));
			if (url != null) {
				if (!expect[idx].equals("Y")) fails += "N:"+label+"; ";
			} else {
				if (expect[idx].equals("Y")) fails += "Y:"+label+"; ";
			}
		}
		return fails;
	}

	private static void assertPath(String file, java.net.URL url) throws java.io.IOException, java.net.URISyntaxException {
		org.junit.Assert.assertTrue(file+" vs "+url, comparePath(file, url));
	}

	private static boolean comparePath(String file, java.net.URL url) throws java.io.IOException, java.net.URISyntaxException {
		java.io.File fh_path = new java.io.File(file);
		java.io.File fh_url = new java.io.File(url.toURI());
		return fh_path.getCanonicalPath().equals(fh_url.getCanonicalPath());
	}

	// NB: The concept of mapping a resource URL to a File is inherently flawed, but this utility works
	// beecause the resources we're looking up are in the same build tree.
	private static String getResourcePath(String path, Class<?> clss) throws java.io.IOException, java.net.URISyntaxException
	{
		java.net.URL url = DynLoader.getResource(path, clss);
		if (url == null) return null;
		return new java.io.File(url.toURI()).getCanonicalPath();
	}
}
