/*
 * Copyright 2011-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public final class FileOpsTest
	implements FileOps.LineReader
{
	private static class IndeterminateStream extends java.io.FileInputStream
	{
		private final int availbytes;
		public IndeterminateStream(java.io.File fh, int avail) throws java.io.FileNotFoundException {
			super(fh);
			availbytes = avail;
		}
		@Override
		public int available() {return availbytes;}
	}
	private static final String RSRC_NAME = "file1.txt";
	private static final String RSRC_TEXT = "Hello there!";
	private final String workdir = SysProps.TMPDIR+"/utest/"+getClass().getSimpleName();

	@org.junit.Test
	public void basicIO() throws java.io.IOException
	{
		String origtxt = "This is a test";
		String dirpath2 = workdir+"/dir2";
		String filepath1 = workdir+"/file1";
		String filepath2 = workdir+"/file2";
		String filepath3 = workdir+"/file3";
		String filepath4 = dirpath2+"/file1";
		String filepath5 = workdir+"/file4";
		String filepath5b = workdir+"/file4b";

		// initial setup - we'll test delete more thoroughly later, as we're not sure if the work dir exists to start with
		java.io.File dirh = new java.io.File(workdir);
		FileOps.deleteDirectory(workdir);

		// directory creation
		boolean sts = FileOps.ensureDirExists(workdir);
		org.junit.Assert.assertTrue(sts);
		org.junit.Assert.assertTrue(dirh.exists());
		sts = FileOps.ensureDirExists(workdir);
		org.junit.Assert.assertFalse(sts);
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
		java.io.File fh2 = new java.io.File(filepath2);
		long nbytes = FileOps.copyFile(filepath1, filepath2);
		org.junit.Assert.assertEquals(origtxt.length(), nbytes);
		org.junit.Assert.assertEquals(fh2.length(), nbytes);
		nbytes = FileOps.copyFile(filepath1, filepath2);  //make sure this doesn't append
		org.junit.Assert.assertEquals(origtxt.length(), nbytes);
		org.junit.Assert.assertEquals(fh2.length(), nbytes);

		try {
			FileOps.copyFile(filepath1+"x", filepath2);
			org.junit.Assert.fail("Copy didn't fail as expected on absent input file");
		} catch (java.io.IOException ex) {
			//ok - expected
		}

		try {
			FileOps.copyFile(filepath1, filepath2+"/x");
			org.junit.Assert.fail("Copy didn't fail as expected on absent invalid file");
		} catch (java.io.IOException ex) {
			//ok - expected
		}

		// repeat for the other Copy variant
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
		cnt = FileOps.countFiles(dirh, (java.io.FilenameFilter)null, false, true);
		org.junit.Assert.assertEquals(numfiles, cnt);
		cnt = FileOps.countFiles(dirh, false);
		org.junit.Assert.assertEquals(numfiles-1, cnt);
		cnt = FileOps.countFiles(dirh, (java.io.FilenameFilter)null, false, false);
		org.junit.Assert.assertEquals(numfiles-1, cnt);
		cnt = FileOps.countFiles(dirh, (java.io.FileFilter)null, true, true);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, (java.io.FilenameFilter)null, true, true);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, (java.io.FileFilter)null, true, false);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, (java.io.FilenameFilter)null, true, false);
		org.junit.Assert.assertEquals(1, cnt);

		// now count with a filter
		FileOps.Filter_EndsWith filter = new FileOps.Filter_EndsWith("1", true, true);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(2, cnt);
		cnt = FileOps.countFiles(dirh, filter, false, false);
		org.junit.Assert.assertEquals(1, cnt);
		cnt = FileOps.countFiles(dirh, filter, true, true);
		org.junit.Assert.assertEquals(1, cnt);
		filter = new FileOps.Filter_EndsWith("x", false, true);
		cnt = FileOps.countFiles(dirh, filter, false, true);
		org.junit.Assert.assertEquals(0, cnt);

		// recursive directory deletes
		cnt = FileOps.deleteDirectory(workdir);
		org.junit.Assert.assertFalse("workdir="+workdir, dirh.exists());
		org.junit.Assert.assertEquals(numfiles, cnt);
		cnt = FileOps.deleteDirectory(workdir);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = FileOps.countFiles(dirh, (java.io.FileFilter)null, false, true);
		org.junit.Assert.assertEquals(0, cnt);
		cnt = FileOps.countFiles(dirh, (java.io.FilenameFilter)null, false, true);
		org.junit.Assert.assertEquals(0, cnt);

		// miscellaenous
		org.junit.Assert.assertFalse(FileOps.flush(origtxt));
		org.junit.Assert.assertFalse(FileOps.close(origtxt));
	}

	@org.junit.Test
	public void readResource() throws java.io.IOException, java.net.URISyntaxException
	{
		java.io.File fh = getResource(RSRC_NAME, getClass());

		String rtxt = FileOps.readResourceAsText(RSRC_NAME, getClass(), null);
		org.junit.Assert.assertEquals(fh.length(), rtxt.length());
		org.junit.Assert.assertEquals(RSRC_TEXT, rtxt.trim());

		java.net.URL url = getClass().getResource(RSRC_NAME);
		rtxt = FileOps.readAsText(url, null);
		org.junit.Assert.assertEquals(fh.length(), rtxt.length());
		org.junit.Assert.assertEquals(RSRC_TEXT, rtxt.trim());

		byte[] buf = FileOps.read(url.toString(), -1, null);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
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
	public void readBuffer() throws java.io.IOException, java.net.URISyntaxException
	{
		java.io.File fh = getResource(RSRC_NAME, getClass());

		// test with a null ArrayRef param first
		byte[] buf = FileOps.read(fh, -1, null);
		String rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(fh.length(), buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.equals(rtxt.trim()));

		buf = FileOps.read(fh, (int)fh.length(), null);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(fh.length(), buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.equals(rtxt.trim()));

		buf = FileOps.read(fh, (int)fh.length() + 1, null);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(fh.length(), buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.equals(rtxt.trim()));

		int seglen = 5;  //must be shorter than resource-file contents, AFTER stripping newline
		buf = FileOps.read(fh, seglen, null);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(seglen, buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.substring(0,  seglen).equals(rtxt.trim()));

		seglen = 1;  //similiar to above test, but more of a boundary condition
		buf = FileOps.read(fh, seglen, null);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(seglen, buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.substring(0,  seglen).equals(rtxt.trim()));

		buf = FileOps.read(fh, 0, null);  //a special boundary condition
		org.junit.Assert.assertNull(buf);

		// now test with an ArrayRef param: equal-to, greater-than and less-than file size
		int initcap = (int)fh.length();
		ArrayRef<byte[]> ah = new ArrayRef<byte[]>(byte.class, initcap);
		buf = FileOps.read(fh, -1, ah);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(fh.length(), ah.size());
		org.junit.Assert.assertEquals(fh.length(), buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.equals(rtxt.trim()));

		initcap = (int)fh.length() + 1;
		ah = new ArrayRef<byte[]>(byte.class, initcap);
		buf = FileOps.read(fh, -1, ah);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(fh.length(), ah.size());
		org.junit.Assert.assertEquals(initcap, buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.equals(rtxt.trim()));

		initcap = 5;
		ah = new ArrayRef<byte[]>(byte.class, initcap);
		buf = FileOps.read(fh, -1, ah);
		rtxt = new String(buf, StringOps.DFLT_CHARSET);
		org.junit.Assert.assertEquals(fh.length(), ah.size());
		org.junit.Assert.assertEquals(fh.length(), buf.length);
		org.junit.Assert.assertTrue(RSRC_TEXT.equals(rtxt.trim()));

		// now read from a file which exceeds RDBUFSIZ: 1st test requires more than one read, 2nd is exactly 1 read
		String filepath = workdir+"/binfile1";
		fh = new java.io.File(filepath);
		FileOps.ensureDirExists(workdir);
		buf = new byte[256];
		for (int bval = 0; bval != 256; bval++) buf[bval] = (byte)bval;
		java.io.FileOutputStream ostrm = new java.io.FileOutputStream(filepath, false);
		try {
			while (fh.length() < (FileOps.RDBUFSIZ * 2) + 1) ostrm.write(buf);
		} finally {
			ostrm.close();
		}
		initcap = (int)fh.length();
		ah = new ArrayRef<byte[]>(byte.class, initcap);
		java.io.InputStream strm = new IndeterminateStream(fh, 0);
		try {
			buf = FileOps.read(strm, -1, ah);
		} finally {
			strm.close();
		}
		org.junit.Assert.assertEquals(fh.length(), ah.size());
		org.junit.Assert.assertEquals(fh.length(), buf.length);
		org.junit.Assert.assertEquals(1, buf[1]);
		org.junit.Assert.assertEquals(1, buf[257]);
		org.junit.Assert.assertEquals(2, buf[258]);

		initcap = (int)fh.length();
		ah = new ArrayRef<byte[]>(byte.class, initcap);
		strm = new IndeterminateStream(fh, initcap);
		try {
			buf = FileOps.read(strm, -1, ah);
		} finally {
			strm.close();
		}
		org.junit.Assert.assertEquals(fh.length(), ah.size());
		org.junit.Assert.assertEquals(fh.length(), buf.length);
		org.junit.Assert.assertEquals(1, buf[1]);
		org.junit.Assert.assertEquals(1, buf[257]);
		org.junit.Assert.assertEquals(2, buf[258]);

		FileOps.deleteDirectory(workdir);
	}

	@org.junit.Test
	public void writeFail() throws java.io.IOException
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
	public void filetype() throws java.io.IOException
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
	public void lineReader() throws java.io.IOException, java.net.URISyntaxException
	{
		int cnt = FileOps.readTextLines(getClass().getResource(RSRC_NAME), this, 0, null, 8192);
		org.junit.Assert.assertEquals(2, cnt);

		java.io.File fh = getResource(RSRC_NAME, getClass());
		cnt = FileOps.readTextLines(fh, this, 0, null, 8192);
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