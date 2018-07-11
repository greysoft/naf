/*
 * Copyright 2011-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public final class FileOpsTest
	implements FileOps.LineReader
{
	private static final String RSRC_NAME = "file1.txt";
	private static final String RSRC_TEXT = "Hello there!";
	private final String workdir = SysProps.TMPDIR+"/utest/greybase/"+getClass().getSimpleName();

	@org.junit.Test
	public void testDirectoryOps() throws java.io.IOException
	{
		String origtxt = "This is a test";
		String dirpath2 = workdir+"/dir2";
		String filepath1 = workdir+"/file1";
		String filepath2 = workdir+"/file2";
		String filepath3 = workdir+"/file3";
		String filepath4 = dirpath2+"/file21";
		String filepath5 = workdir+"/file4";
		String filepath5b = workdir+"/file4b";

		// initial setup - we'll test delete more thoroughly later, as we're not sure if the work dir exists to start with
		java.io.File dirh = new java.io.File(workdir);
		FileOps.deleteDirectory(workdir);

		// directory creation
		org.junit.Assert.assertFalse(dirh.exists());
		FileOps.ensureDirExists(workdir);
		org.junit.Assert.assertTrue(dirh.exists());
		FileOps.ensureDirExists(workdir);
		org.junit.Assert.assertTrue(dirh.exists());

		// text-file I/O
		FileOps.writeTextFile(filepath1, origtxt);
		String rtxt = FileOps.readAsText(filepath1, null);
		org.junit.Assert.assertTrue(origtxt.equals(rtxt));
		rtxt = FileOps.readAsText(new java.io.File(filepath1), null);
		org.junit.Assert.assertTrue(origtxt.equals(rtxt));
		java.io.FileInputStream strm = new java.io.FileInputStream(filepath1);
		try {
			rtxt = FileOps.readAsText(strm, null);
		} finally {
			strm.close();
		}
		org.junit.Assert.assertTrue(origtxt.equals(rtxt));

		// file copy
		final java.io.File fh1 = new java.io.File(filepath1);
		final java.io.File fh2 = new java.io.File(filepath2);
		org.junit.Assert.assertFalse(fh2.exists());
		long nbytes = FileOps.copyFile(filepath1, filepath2);
		org.junit.Assert.assertEquals(origtxt.length(), nbytes);
		org.junit.Assert.assertEquals(nbytes, fh2.length());
		org.junit.Assert.assertFalse(java.nio.file.Files.isSameFile(fh1.toPath(), fh2.toPath()));
		//make sure copy-to-existing overwrites
		FileOps.writeTextFile(filepath1, origtxt.substring(1));
		nbytes = FileOps.copyFile(fh1.toPath(), fh2.toPath());
		org.junit.Assert.assertEquals(origtxt.length() - 1, nbytes);
		org.junit.Assert.assertEquals(nbytes, fh2.length());
		org.junit.Assert.assertFalse(java.nio.file.Files.isSameFile(fh1.toPath(), fh2.toPath()));

		try {
			FileOps.copyFile(filepath1+"x", filepath2);
			org.junit.Assert.fail("Copy didn't fail as expected on absent input file");
		} catch (java.io.IOException ex) {} //ok - expected
		try {
			FileOps.copyFile(filepath1, filepath2+"/x");
			org.junit.Assert.fail("Copy didn't fail as expected on absent invalid file");
		} catch (java.io.IOException ex) {} //ok - expected

		// repeat for the other Copy variant
		FileOps.writeTextFile(filepath1, origtxt);
		java.io.File fh3 = new java.io.File(filepath3);
		java.io.FileOutputStream fout = null;
		java.io.FileInputStream fin = new java.io.FileInputStream(filepath1);
		try {
			fout = new java.io.FileOutputStream(filepath3);
			nbytes = FileOps.copyStream(fin, fout);
		} finally {
			fin.close();
			if (fout != null) fout.close();
			fout = null;
		}
		org.junit.Assert.assertEquals(origtxt.length(), nbytes);
		org.junit.Assert.assertEquals(fh3.length(), nbytes);
		fin = new java.io.FileInputStream(filepath1);
		try {
			fout = new java.io.FileOutputStream(filepath3);
			nbytes = FileOps.copyStream(fin, fout);
			org.junit.Assert.assertEquals(origtxt.length(), nbytes);
			org.junit.Assert.assertEquals(fh3.length(), nbytes);
			org.junit.Assert.assertTrue(FileOps.flush(fout));
		} finally {
			fin.close();
			if (fout != null) org.junit.Assert.assertTrue(FileOps.close(fout));
		}
		rtxt = FileOps.readAsText(fh3, null);
		org.junit.Assert.assertTrue(origtxt.equals(rtxt));

		// now test Move-File
		FileOps.copyFile(filepath1, filepath5);
		FileOps.moveFile(filepath5, filepath5b);
		java.io.File fh5 = new java.io.File(filepath5);
		java.io.File fh5b = new java.io.File(filepath5b);
		rtxt = FileOps.readAsText(filepath5b, null);
		org.junit.Assert.assertTrue(origtxt.equals(rtxt));
		org.junit.Assert.assertFalse(fh5.exists());
		//the atomic rename should fail when dest already exists, and exercise the fallback copy
		FileOps.copyFile(filepath1, filepath5);
		FileOps.moveFile(filepath5, filepath5b);
		rtxt = FileOps.readAsText(filepath5b, null);
		org.junit.Assert.assertTrue(origtxt.equals(rtxt));
		org.junit.Assert.assertFalse(fh5.exists());
		// clear up as the following step precedes this and expects 4 files
		if (!fh5b.delete()) throw new java.io.IOException("Failed to delete tempfile - "+fh5b.getAbsolutePath());

		// add and populate a nested dir, then count 'em
		FileOps.ensureDirExists(dirpath2);
		FileOps.copyFile(filepath1, filepath4);
		int numfiles = 4;
		int cnt = FileOps.countFiles(dirh, true);
		org.junit.Assert.assertEquals(numfiles, cnt);
		cnt = FileOps.countFiles(dirh, false, true);
		org.junit.Assert.assertEquals(numfiles, cnt);
		cnt = FileOps.countFiles(dirh, false);
		org.junit.Assert.assertEquals(numfiles-1, cnt);
		cnt = FileOps.countFiles(dirh, false, false);
		org.junit.Assert.assertEquals(numfiles-1, cnt);
		cnt = FileOps.countFiles(dirh, true, true);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, true, true);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, true, false);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, true, false);
		org.junit.Assert.assertEquals(1, cnt);

		// now count with a filter
		FileOps.Filter_EndsWith filter = new FileOps.Filter_EndsWith("e1");
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(1, cnt);
		filter = new FileOps.Filter_EndsWith("1");
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(2, cnt);
		filter = new FileOps.Filter_EndsWith("E1");
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(0, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"e1"}, false, false);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(1, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"e1"}, true, false);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(1, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"E1"}, false, false);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(0, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"E1"}, true, false);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(1, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"e2"}, false, false);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(1, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"e2"}, false, true);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(3, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"E2"}, true, true);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(3, cnt);
		filter = new FileOps.Filter_EndsWith(new String[]{"e1"}, false, false);
		cnt = FileOps.countFiles(dirh, filter, false, false);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, filter, true, true);
		org.junit.Assert.assertEquals(1, cnt);
		filter = new FileOps.Filter_EndsWith("x");
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(0, cnt);

		//test the NIO listing methods
		java.util.ArrayList<String> namlst = FileOps.directoryListSimple(dirh.toPath());
		org.junit.Assert.assertEquals(4, namlst.size());
		java.util.Collections.sort(namlst);
		org.junit.Assert.assertEquals("dir2", namlst.get(0));
		org.junit.Assert.assertEquals("file1", namlst.get(1));
		org.junit.Assert.assertEquals("file2", namlst.get(2));
		org.junit.Assert.assertEquals("file3", namlst.get(3));
		java.util.ArrayList<java.nio.file.Path> lst = FileOps.directoryList(dirh.toPath(), true);
		java.util.Collections.sort(lst);
		org.junit.Assert.assertEquals(4, lst.size());
		org.junit.Assert.assertEquals("file21", lst.get(0).getFileName().toString());
		org.junit.Assert.assertEquals("file1", lst.get(1).getFileName().toString());
		org.junit.Assert.assertEquals("file2", lst.get(2).getFileName().toString());
		org.junit.Assert.assertEquals("file3", lst.get(3).getFileName().toString());
		java.nio.file.Path nulldir = java.nio.file.Paths.get(dirh.getAbsolutePath()+"/nosuchdir");
		namlst = FileOps.directoryListSimple(nulldir);
		org.junit.Assert.assertNull(namlst); //Directory listing on non-existing file returns null
		nulldir = java.nio.file.Paths.get(dirh.getAbsolutePath()+"/nosuchdir1/nisuchdir2"); //try deeper missing tree
		namlst = FileOps.directoryListSimple(nulldir);
		org.junit.Assert.assertNull(namlst);
		java.nio.file.Path path1 = java.nio.file.Paths.get(dirh.getAbsolutePath()+"/file1");
		try {
			namlst = FileOps.directoryListSimple(path1);
			org.junit.Assert.fail("Directory listing on non-directory is expected to fail - ="+namlst);
		} catch (java.nio.file.NotDirectoryException ex) {}//expected
		java.nio.file.Files.createDirectories(nulldir);
		namlst = FileOps.directoryListSimple(nulldir);
		org.junit.Assert.assertEquals(0, namlst.size()); //Directory listing on empty directory returns empty list
		java.nio.file.Files.delete(nulldir);
		namlst = FileOps.directoryListSimple(nulldir);
		org.junit.Assert.assertNull(namlst);

		org.junit.Assert.assertTrue(fh1.exists());
		FileOps.deleteFile(fh1);
		org.junit.Assert.assertFalse(fh1.exists());
		FileOps.deleteFile(fh1);
		org.junit.Assert.assertFalse(fh1.exists());
		java.io.File dh = new java.io.File(workdir+"/dir9");
		org.junit.Assert.assertFalse(dh.exists());
		FileOps.ensureDirExists(dh);
		org.junit.Assert.assertTrue(dh.exists());
		FileOps.ensureDirExists(dh);
		org.junit.Assert.assertTrue(dh.exists());
		java.io.File fh = new java.io.File(dh, "file9");
		FileOps.writeTextFile(fh, "blah", false);
		org.junit.Assert.assertTrue(fh.exists());
		try {
			FileOps.ensureDirExists(fh);
			org.junit.Assert.fail("mkdir is expected to fail if it conflicts with existing file");
		} catch (java.io.IOException ex) {}
		try {
			FileOps.deleteFile(dh);
			org.junit.Assert.fail("deleteFile is expected to fail on non-empty directory");
		} catch (java.io.IOException ex) {}
		org.junit.Assert.assertTrue(fh.exists());
		org.junit.Assert.assertTrue(dh.exists());

		// recursive directory deletes
		cnt = FileOps.deleteDirectory(workdir);
		org.junit.Assert.assertFalse("workdir="+workdir, dirh.exists());
		org.junit.Assert.assertEquals(numfiles, cnt);
		cnt = FileOps.deleteDirectory(workdir);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = FileOps.countFiles(dirh, false, true);
		org.junit.Assert.assertEquals(0, cnt);

		java.nio.file.Path pth_root = java.nio.file.Paths.get("/");
		java.nio.file.Path pth = java.nio.file.Paths.get("/dir1");
		org.junit.Assert.assertEquals(pth_root, FileOps.parentDirectory(pth));
		org.junit.Assert.assertEquals("dir1", FileOps.getFilename(pth));
		try {
			java.nio.file.Path pth2 = FileOps.parentDirectory(pth_root);
			org.junit.Assert.fail("parentDirectory is expected to throw on null - "+pth2);
		} catch (IllegalStateException ex) {}
		try {
			String fname = FileOps.getFilename(pth_root);
			org.junit.Assert.fail("getFilename is expected to throw on null - "+fname);
		} catch (IllegalStateException ex) {}

		// miscellaenous
		org.junit.Assert.assertFalse(FileOps.flush(origtxt));
		org.junit.Assert.assertFalse(FileOps.close(origtxt));
	}

	@org.junit.Test
	public void testFilenameSort()
	{
		java.util.ArrayList<java.nio.file.Path> lst = new java.util.ArrayList<java.nio.file.Path>();
		lst.add(java.nio.file.Paths.get("/dir1/file2"));
		lst.add(java.nio.file.Paths.get("/"));
		lst.add(java.nio.file.Paths.get("/dir2/file1"));
		lst.add(java.nio.file.Paths.get("/dir1/file1"));
		lst.add(null);
		lst.add(null);
		lst.add(java.nio.file.Paths.get("/"));
		int siz = lst.size();
		java.nio.file.Path[] arr = lst.toArray(new java.nio.file.Path[siz]);
		FileOps.sortByFilename(lst);
		FileOps.sortByFilename(arr);
		org.junit.Assert.assertEquals(siz, lst.size());
		int pos = 0;
		org.junit.Assert.assertNull(lst.get(pos++));
		org.junit.Assert.assertNull(lst.get(pos++));
		org.junit.Assert.assertEquals(java.io.File.separator, lst.get(pos++).toString());
		org.junit.Assert.assertEquals(java.io.File.separator, lst.get(pos++).toString());
		org.junit.Assert.assertEquals(java.io.File.separator+"dir1"+java.io.File.separator+"file1", lst.get(pos++).toString());
		org.junit.Assert.assertEquals(java.io.File.separator+"dir2"+java.io.File.separator+"file1", lst.get(pos++).toString());
		org.junit.Assert.assertEquals(java.io.File.separator+"dir1"+java.io.File.separator+"file2", lst.get(pos++).toString());
		for (int idx = 0; idx != siz; idx++) {
			org.junit.Assert.assertEquals(lst.get(idx), arr[idx]);
		}
	}

	@org.junit.Test
	public void testReadResource() throws java.io.IOException, java.net.URISyntaxException
	{
		java.io.File fh = getResource(RSRC_NAME, getClass());

		String rtxt = FileOps.readResourceAsText(RSRC_NAME, getClass(), null);
		org.junit.Assert.assertEquals(fh.length(), rtxt.length());
		org.junit.Assert.assertEquals(RSRC_TEXT, rtxt.trim());

		java.net.URL url = getClass().getResource(RSRC_NAME);
		rtxt = FileOps.readAsText(url, null);
		org.junit.Assert.assertEquals(fh.length(), rtxt.length());
		org.junit.Assert.assertEquals(RSRC_TEXT, rtxt.trim());

		ByteArrayRef bufh = FileOps.read(url.toString(), -1, null);
		byte[] arr = bufh.toArray();
		rtxt = new String(arr, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(fh.length(), rtxt.length());
		org.junit.Assert.assertEquals(RSRC_TEXT, rtxt.trim());

		String urlpath = url.toString();
		org.junit.Assert.assertNotNull(FileOps.makeURL(urlpath));
		org.junit.Assert.assertNotNull(FileOps.makeURL(urlpath.replace(FileOps.URLPFX_FILE, FileOps.URLPFX_HTTP)));
		org.junit.Assert.assertNotNull(FileOps.makeURL(urlpath.replace(FileOps.URLPFX_FILE, FileOps.URLPFX_HTTPS)));
		org.junit.Assert.assertNotNull(FileOps.makeURL("jar:file:///name.jar!/path/to/resource"));
		org.junit.Assert.assertNull(FileOps.makeURL(fh.getCanonicalPath()));
	}

	@org.junit.Test
	public void testWriteFail() throws java.io.IOException
	{
		String txt = "This is a test";
		String pthnam = workdir+"/writeFail/wfail.txt";
		try {
			FileOps.writeTextFile(pthnam, txt);
			org.junit.Assert.fail("Write didn't fail as expected on absent directory");
		} catch (java.io.IOException ex) {
			//ok - expected
		}
	}

	@org.junit.Test
	public void testFileType() throws java.io.IOException
	{
		String dirslash = SysProps.get("file.separator");
		final String filesfx = "txt";
		String filename = "name."+filesfx;
		String sfx = FileOps.getFileType(filename);
		org.junit.Assert.assertTrue(filesfx.equals(sfx));

		filename = dirslash+"path"+dirslash+"to.somewhere"+dirslash+"dotted.filename."+filesfx;
		sfx = FileOps.getFileType(filename);
		org.junit.Assert.assertTrue(filesfx.equals(sfx));

		filename = "nofiletype";
		sfx = FileOps.getFileType(filename);
		org.junit.Assert.assertTrue(sfx == null);

		filename = dirslash+"path"+dirslash+"to.somewhere"+dirslash+"nofiletype";
		sfx = FileOps.getFileType(filename);
		org.junit.Assert.assertTrue(sfx == null);

		filename = "/path/to.somewhere/nofiletype";
		sfx = FileOps.getFileType(filename);
		org.junit.Assert.assertTrue(sfx == null);
	}

	@org.junit.Test
	public void testLineReader() throws java.io.IOException, java.net.URISyntaxException
	{
		int cnt = FileOps.readTextLines(getClass().getResource(RSRC_NAME), this, 8192, null, 0, null);
		org.junit.Assert.assertEquals(2, cnt);

		java.io.File fh = getResource(RSRC_NAME, getClass());
		cnt = FileOps.readTextLines(fh, this, 8192, null, 0, null);
		org.junit.Assert.assertEquals(2, cnt);
	}

	@Override
	public boolean processLine(String line, int lno, int mode, Object cbdata)
	{
		switch (lno) {
		case 1:
			org.junit.Assert.assertEquals(RSRC_TEXT, line);
			break;
		case 2:
			org.junit.Assert.assertEquals(0, line.length());
			break;
		default:
			org.junit.Assert.fail("Unexpected LineReader line-number="+lno);
		}
		return false;
	}

	// NB: The concept of mapping a resource URL to a File is inherently flawed, but this utility works
	// beecause the resources we're looking up are in the same build tree.
	private static java.io.File getResource(String path, Class<?> clss) throws java.net.URISyntaxException
	{
		java.net.URL url = DynLoader.getResource(path, clss);
		if (url == null) return null;
		return new java.io.File(url.toURI());
	}
}