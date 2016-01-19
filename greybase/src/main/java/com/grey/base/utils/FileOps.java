/*
 * Copyright 2010-2016 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.FileAttribute;

import com.grey.base.config.SysProps;

public class FileOps
{
	public static final String URLPFX_HTTP = "http:";
	public static final String URLPFX_HTTPS = "https:";
	public static final String URLPFX_JAR = "jar:";
	public static final String URLPFX_FILE = "file:";

	// declare any option combinations we need in advance, to save creating auto-boxed arrays when calling an NIO method
	public static final StandardOpenOption[] OPENOPTS_NONE = new StandardOpenOption[0];
	public static final StandardCopyOption[] COPYOPTS_NONE = new StandardCopyOption[0];
	public static final LinkOption[] LINKOPTS_NONE = new LinkOption[0];
	public static final FileAttribute<?>[] FATTR_NONE = new FileAttribute[0];
	public static final StandardOpenOption[] OPENOPTS_CREATE = new StandardOpenOption[]{StandardOpenOption.CREATE};
	public static final StandardOpenOption[] OPENOPTS_CREATNEW = new StandardOpenOption[]{StandardOpenOption.CREATE_NEW};
	public static final StandardOpenOption[] OPENOPTS_READ = new StandardOpenOption[]{StandardOpenOption.READ};
	public static final StandardOpenOption[] OPENOPTS_DSYNC = new StandardOpenOption[]{StandardOpenOption.DSYNC};
	public static final StandardOpenOption[] OPENOPTS_CREATE_DSYNC = new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.DSYNC};
	public static final StandardOpenOption[] OPENOPTS_CREATNEW_DSYNC = new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.DSYNC};
	public static final StandardOpenOption[] OPENOPTS_CREATE_WRITE = new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE};
	public static final StandardOpenOption[] OPENOPTS_CREATE_WRITE_TRUNC = new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
	public static final StandardCopyOption[] COPYOPTS_REPLACE = new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING};
	public static final StandardCopyOption[] COPYOPTS_ATOMIC = new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};

	protected static final int RDBUFSIZ = SysProps.get("grey.fileio.rdbufsiz", 1024); //min buffer for unknown stream size

	public interface LineReader
	{
		public boolean processLine(String line, int line_number, int mode, Object cbdata) throws Exception;
	}

	private static class PathComparator_ByFilename
		implements java.util.Comparator<java.nio.file.Path>, java.io.Serializable
	{
		private static final long serialVersionUID = 1L;
		public PathComparator_ByFilename() {} //eliminate warning about synthetic accessor
		@Override
		public int compare(java.nio.file.Path p1, java.nio.file.Path p2) {
			if (p1 == null) return (p2 == null ? 0 : -1);
			if (p2 == null) return 1;
			java.nio.file.Path f1 = p1.getFileName();
			java.nio.file.Path f2 = p2.getFileName();
			if (f1 == null) {
				if (f2 != null) return -1;
				return p1.compareTo(p2);
			}
			if (f2 == null) return 1;
			int cmp = f1.compareTo(f2);
			if (cmp == 0) cmp = p1.compareTo(p2);
			return cmp;
		}
	}
	private static final PathComparator_ByFilename cmp_filename = new PathComparator_ByFilename();

	public static class Filter_EndsWith implements java.io.FileFilter
	{
		private final String[] sfx;
		private final boolean nocase;
		private final boolean invert;
		private final boolean recursive;

		public Filter_EndsWith(CharSequence s) {this(new String[]{s.toString()}, false, false);}
		public Filter_EndsWith(CharSequence[] s, boolean nc, boolean inv) {this(s, nc, inv, true);}

		public Filter_EndsWith(CharSequence[] s, boolean nc, boolean inv, boolean dirs)
		{
			nocase = nc;
			invert = inv;
			recursive = dirs;
			sfx = new String[s.length];
			for (int idx = 0; idx != sfx.length; idx++) {
				sfx[idx] = (nocase ? s[idx].toString().toLowerCase() : s[idx].toString());
			}
		}

		@Override
		public boolean accept(java.io.File fh)
		{
			if (fh.isDirectory()) return recursive;
			String filename = fh.getName();
			if (nocase) filename = filename.toLowerCase();
			boolean ok = false;
			for (int idx = 0; idx != sfx.length; idx++) {
				if (filename.endsWith(sfx[idx])){
					ok = true;
					break;
				}
			}
			return (invert ? !ok :ok);
		}
	}


	public static byte[] read(java.io.File fh) throws java.io.IOException {return read(fh, -1, null);}
	public static void writeTextFile(String pthnam, String txt) throws java.io.IOException {writeTextFile(new java.io.File(pthnam), txt, false);}
	public static int deleteDirectory(String pthnam) throws java.io.IOException {return deleteDirectory(new java.io.File(pthnam));}
	public static void ensureDirExists(String pthnam) throws java.io.IOException {ensureDirExists(java.nio.file.Paths.get(pthnam));}
	public static void ensureDirExists(java.io.File dirh) throws java.io.IOException {ensureDirExists(dirh.toPath());}
	public static java.util.ArrayList<String> directoryListSimple(java.nio.file.Path dirh) throws java.io.IOException {return directoryListSimple(dirh, 0, null);}
	public static void sortByFilename(java.nio.file.Path[] paths) {sortByFilename(paths, 0, paths.length);}


	// Read requested number of bytes from stream, and return as byte array.
	// If bufh is non-null, then it will be used (and grown if necessary) to hold the data and its backing array is returned.
	// If reqlen is -1, that means read to end of stream, which we refer to as an unbounded read.
	// Note that this method does not append to bufh, it will replaces whatever is already there.
	//
	// The bounded case is pretty straightforward, but unbounded reads are complicated by the fact that in the general case, we can't tell how
	// many bytes are in the URL's content stream in advance. We therefore use a ByteArrayOutputStream to accumulate its data.
	// However, the caller may know via other means how large the content is or may be, and therefore allocate 'bufh' storage which is large
	// enough to hold it all. Or, the intermediate read buffer we allocate might be enough to hold the entire content.
	// In these cases, it seems a shame to go to the wasted expense of allocating a ByteArrayOutputStream, copying the read buffer into it,
	// and then allocating another byte array at the end to return its contents. We therefore go to a moderate amount of trouble to defer
	// the introduction of the ByteArrayOutputStream until the intermediate read buffer (which might or might not be based on caller-provider
	// storage) fills up.
	public static byte[] read(java.io.InputStream istrm, int reqlen, ArrayRef<byte[]> bufh) throws java.io.IOException
	{
		int bufcap = reqlen;
		if (bufcap == -1)
		{
			bufcap = istrm.available();
			if (bufcap < RDBUFSIZ) bufcap = RDBUFSIZ;
		}
		byte[] rdbuf = alloc(bufh, bufcap); //this is the intermediate read buffer, a staging area between istrm and ostrm
		int off = (bufh == null ? 0 : bufh.ar_off);
		java.io.ByteArrayOutputStream ostrm = null;
		int totaldata = 0;
		int buflen = 0;

		while (totaldata != reqlen)
		{
			if (buflen == bufcap)
			{
				// The intermediate read buffer is full, so flush it to the byte-array-stream.
				// If reqlen was -1 and InputStream.available() gave us an accurate total, then the creation of the ByteArrayOutputStream
				// is a waste as we've already got the whole stream stored in 'rdbuf'. But, there's no way of knowing for sure as we can't
				// depend on available()==0 really meaning we're at EOF.
				if (ostrm == null) ostrm = new java.io.ByteArrayOutputStream(bufcap);
				ostrm.write(rdbuf, off, buflen);
				buflen = 0;
			}
			int nbytes = istrm.read(rdbuf, off + buflen, bufcap - buflen);
			if (nbytes == -1) break; //end of input stream

			// accumulate data in 'buf' if there's space, to avoid copying into the byte-stream on every single read
			buflen += nbytes;
			totaldata += nbytes;
		}

		if (ostrm == null)
		{
			// NB: This means totaldata == buflen
			// Either the caller provided enough storage, or we allocated an intermediate read buffer that was sufficient
			if (totaldata == 0) return null; //there was no data

			if (bufh == null && totaldata != bufcap)
			{
				// The buffer we initially allocated was too large, and it's length attribute is the only indication the caller will have
				// of how much data we did read, so we have to return a smaller buffer that's exactly sized - allocate it and copy into it.
				byte[] buf2 = new byte[totaldata];
				System.arraycopy(rdbuf, off, buf2, 0, totaldata);
				rdbuf = buf2;
			}
		}
		else
		{
			// the caller didn't provide enough storage (or maybe any storage), so return a copy of the ByteArrayOutputStream's buffer
			if (buflen != 0) ostrm.write(rdbuf, off, buflen); //append the final batch of data to the byte stream first
			rdbuf = ostrm.toByteArray();
		}

		if (bufh != null)
		{
			if (bufh.ar_buf != rdbuf) bufh.ar_off = 0; //we allocated a new backing array, and it has no offset
			bufh.ar_buf = rdbuf;
			bufh.ar_len = totaldata;
		}
		return rdbuf;
	}

	public static byte[] read(java.io.File fh, int reqlen, ArrayRef<byte[]> bufh) throws java.io.IOException
	{
		if (reqlen == -1) reqlen = (int)fh.length();
		return read(new java.io.FileInputStream(fh), reqlen, bufh);
	}

	public static byte[] read(java.io.FileInputStream strm, int reqlen, ArrayRef<byte[]> bufh) throws java.io.IOException
	{
		if (reqlen == -1) reqlen = strm.available();
		return readAndClose(strm, reqlen, bufh);
	}

	public static byte[] read(java.net.URL url, int reqlen, ArrayRef<byte[]> bufh) throws java.io.IOException
	{
		return readAndClose(url.openStream(), reqlen, bufh);
	}

	public static byte[] read(CharSequence pthnam_cs, int reqlen, ArrayRef<byte[]> bufh) throws java.io.IOException
	{
		String pthnam = pthnam_cs.toString();
		java.net.URL url = makeURL(pthnam);
		if (url != null) return read(url, reqlen, bufh);
		return read(new java.io.File(pthnam), reqlen, bufh);
	}

	public static byte[] readResource(String path, Class<?> clss) throws java.io.IOException
	{
		java.net.URL url = DynLoader.getResource(path, clss);
		if (url == null) return null;
		java.io.InputStream strm = url.openStream();
		return readAndClose(strm, -1, null);
	}

	public static String readResourceAsText(String path, Class<?> clss, String charset) throws java.io.IOException
	{
		byte[] buf = readResource(path, clss);
		if (buf == null) return null;
		return StringOps.convert(buf, charset);
	}

	public static String readAsText(java.io.InputStream strm, String charset) throws java.io.IOException
	{
		byte[] buf = read(strm, -1, null);
		return StringOps.convert(buf, charset);
	}

	public static String readAsText(java.io.File fh, String charset) throws java.io.IOException
	{
		byte[] buf = read(fh, -1, null);
		return StringOps.convert(buf, charset);
	}

	public static String readAsText(java.net.URL url, String charset) throws java.io.IOException
	{
		byte[] buf = read(url, -1, null);
		return StringOps.convert(buf, charset);
	}

	public static String readAsText(CharSequence pthnam_cs, String charset) throws java.io.IOException
	{
		byte[] buf = read(pthnam_cs, -1, null);
		return StringOps.convert(buf, charset);
	}

	public static void writeTextFile(java.io.File fh, String txt, boolean append) throws java.io.IOException
	{
		java.io.FileWriter cstrm = new java.io.FileWriter(fh, append);
		try {
			cstrm.append(txt);
		} finally {
			cstrm.close(); //this flushes its own buffer and closes the underlying stream
		}
	}

	public static int readTextLines(java.io.InputStream strm, LineReader consumer, int bufsiz, String cmnt,
			int mode, Object cbdata) throws java.io.IOException
	{
		java.io.InputStreamReader cstrm = new java.io.InputStreamReader(strm);
		java.io.BufferedReader bstrm = new java.io.BufferedReader(cstrm, bufsiz);
		int lno = 0;
		String line;
		try {
			while ((line = bstrm.readLine()) != null) {
				try {
					if (cmnt != null) {
						int pos = line.indexOf(cmnt);
						if (pos != -1) line = line.substring(0, pos);
						line = line.trim();
						if (line.length() == 0) continue;
					}
					if (consumer.processLine(line, ++lno, mode, cbdata)) break;
				} catch (Exception ex) {
					throw new java.io.IOException("LineReader callback failed on line="+lno+": "+line+" - "+ex, ex);
				}
			}
		} finally {
			bstrm.close();
		}
		return lno;
	}

	public static int readTextLines(java.io.File fh, LineReader consumer, int bufsiz, String cmnt,
			int mode, Object cbdata) throws java.io.IOException
	{
		java.io.InputStream strm = new java.io.FileInputStream(fh);
		return readTextLines(strm, consumer, bufsiz, cmnt, mode, cbdata);
	}

	public static int readTextLines(java.net.URL path, LineReader consumer, int bufsiz, String cmnt,
			int mode, Object cbdata) throws java.io.IOException
	{
		java.io.InputStream strm = path.openStream();
		return readTextLines(strm, consumer, bufsiz, cmnt, mode, cbdata);
	}

	public static long copyFile(java.nio.file.Path srcfile, java.nio.file.Path dstfile) throws java.io.IOException
	{
		java.nio.channels.FileChannel inchan = java.nio.channels.FileChannel.open(srcfile, OPENOPTS_READ);
		java.nio.channels.FileChannel outchan = null;
		long nbytes = 0;
		long filesize;

		try {
			filesize = inchan.size();
			outchan = java.nio.channels.FileChannel.open(dstfile, OPENOPTS_CREATE_WRITE_TRUNC);
			nbytes = outchan.transferFrom(inchan, 0, inchan.size());
		} finally {
			try {inchan.close();} catch (Exception ex) {}
			if (outchan != null) try {outchan.close();} catch (Exception ex) {}
		}
		if (nbytes != filesize) throw new java.io.IOException("file-copy="+nbytes+" vs "+filesize+" for "+srcfile+" => "+dstfile);
		return nbytes;
	}

	public static long copyFile(CharSequence srcpath, CharSequence dstpath) throws java.io.IOException
	{
		java.nio.file.Path srcfile = java.nio.file.Paths.get(srcpath.toString());
		java.nio.file.Path dstfile = java.nio.file.Paths.get(dstpath.toString());
		return copyFile(srcfile, dstfile);
	}

	// This is a fail-safe wrapper around File.renameTo() since that can fail if the destination path is on a
	// different file system or potentially for other causes which don't preclude other means of I/O.
	// renameTo() is the simplest and most efficient (and hopefully atomic) method, but we fall back to the
	// more laborious byte-by-byte copy if it fails.
	public static void moveFile(java.io.File srcfile, java.io.File dstfile) throws java.io.IOException
	{
		if (srcfile.renameTo(dstfile)) {
			return;
		}
		java.nio.file.Path srcpath = srcfile.toPath();
		copyFile(srcpath, dstfile.toPath());
		deleteFile(srcpath);
	}

	public static void moveFile(CharSequence srcpath, CharSequence dstpath) throws java.io.IOException
	{
		java.io.File srcfile = new java.io.File(srcpath.toString());
		java.io.File dstfile = new java.io.File(dstpath.toString());
		moveFile(srcfile, dstfile);
	}

	public static void deleteFile(java.io.File fh) throws java.io.IOException
	{
		java.io.IOException ex = deleteFile(fh.toPath());
		if (ex != null) throw new java.io.IOException("Failed to delete "+(fh.isDirectory()?"directory":"file")+"="+fh.getAbsolutePath());
	}

	public static java.io.IOException deleteFile(java.nio.file.Path fh)
	{
		try {
			java.nio.file.Files.delete(fh);
		} catch (java.io.IOException ex) {
			if (java.nio.file.Files.exists(fh, LINKOPTS_NONE)) return ex; //genuine error
		}
		return null;
	}

	public static int copyStream(java.io.InputStream istrm, java.io.OutputStream ostrm) throws java.io.IOException
	{
		byte buf[] = new byte[RDBUFSIZ];
		int total = 0;
		int nbytes;

		while ((nbytes = istrm.read(buf)) != -1)
		{
			ostrm.write(buf, 0, nbytes);
			total += nbytes;
		}
		return total;
	}

	public static boolean close(Object obj) throws java.io.IOException
	{
		if (!(obj instanceof java.io.Closeable)) {
			//at least try and flush it if possible, since a close() would have achieved that much
			flush(obj);
			return false;
		}
		((java.io.Closeable)obj).close();
		return true;
	}

	public static boolean flush(Object obj) throws java.io.IOException
	{
		if (!(obj instanceof java.io.Flushable)) return false;
		((java.io.Flushable)obj).flush();
		return true;
	}

	public static java.io.PrintStream redirectStdio(CharSequence pthnam, boolean autoflush, boolean stdout, boolean stderr) throws java.io.IOException
	{
		java.io.File fh = new java.io.File(pthnam.toString());
		java.io.File dirh = fh.getParentFile(); //incredibly, beware that this will be null, if filename is specified without directory slashes
		if (dirh != null) ensureDirExists(dirh);
		java.io.FileOutputStream fstrm = new java.io.FileOutputStream(fh, true);
		java.io.PrintStream pstrm = null;
		boolean success = false;

		try {
			pstrm = new java.io.PrintStream(fstrm, autoflush);
			fstrm = null;
			if (stdout) System.setOut(pstrm);
			if (stderr) System.setErr(pstrm);
			success = true;
		} finally {
			if (fstrm != null) fstrm.close();
			if (!success && pstrm != null) pstrm.close();
		}
		return pstrm;
	}

	public static java.net.URL makeURL(String pthnam) throws java.net.MalformedURLException
	{
		if (!pthnam.startsWith(URLPFX_FILE)
				&& !pthnam.startsWith(URLPFX_HTTP)
				&& !pthnam.startsWith(URLPFX_HTTPS)
				&& !pthnam.startsWith(URLPFX_JAR)) {
			return null;
		}
		return new java.net.URL(pthnam);
	}

	public static java.util.ArrayList<java.nio.file.Path> directoryList(java.nio.file.Path dirh, boolean recursive) throws java.io.IOException
	{
		java.util.ArrayList<java.nio.file.Path> lst = new java.util.ArrayList<java.nio.file.Path>();
		try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = java.nio.file.Files.newDirectoryStream(dirh)) {
			for (java.nio.file.Path fpath : ds) {
				if (!recursive || !java.nio.file.Files.isDirectory(fpath, LINKOPTS_NONE)) {
					lst.add(fpath);
				} else {
					java.util.ArrayList<java.nio.file.Path> lst2 = directoryList(fpath, true);
					lst.addAll(lst2);
				}
			}
		} catch (java.nio.file.NoSuchFileException ex) {
			return null; //exists() test below could return true by now, due to race conditions
		} catch (java.io.IOException ex) {
			if (!java.nio.file.Files.exists(dirh, LINKOPTS_NONE)) return null;
			throw ex;
		}
		return lst;
	}

	public static java.util.ArrayList<String> directoryListSimple(java.nio.file.Path dirh, int max, java.util.ArrayList<String> lst)
		throws java.io.IOException
	{
		if (lst == null) lst = new java.util.ArrayList<String>();
		try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = java.nio.file.Files.newDirectoryStream(dirh)) {
			for (java.nio.file.Path fpath : ds) {
				if (max != 0 && lst.size() == max) break;
				lst.add(getFilename(fpath));
			}
		} catch (java.nio.file.NoSuchFileException ex) {
			return null; //exists() test below could return true by now, due to race conditions
		} catch (java.io.IOException ex) {
			if (!java.nio.file.Files.exists(dirh, LINKOPTS_NONE)) return null;
			throw ex;
		}
		return lst;
	}

	// This avoids FindBugs warnings for cases where we know Path.getParent() can't return null
	public static java.nio.file.Path parentDirectory(java.nio.file.Path fh)
	{
		java.nio.file.Path dh = fh.getParent();
		if (dh == null) throw new IllegalStateException("Non-null parent dir was expected for "+fh);
		return dh;
	}

	// This avoids FindBugs warnings for cases where we know Path.getFilename() can't return null
	public static String getFilename(java.nio.file.Path fh)
	{
		java.nio.file.Path name = fh.getFileName();
		if (name == null) throw new IllegalStateException("Non-null filename was expected for "+fh);
		return name.toString();
	}

	// Beware that this literally does count only files, not directories
	public static int countFiles(java.io.File dirh, boolean recursive)
	{
		return countFiles(dirh, (java.io.FileFilter)null, false, recursive);
	}

	public static int countFiles(java.io.File dirh, boolean stopOnFirst, boolean recursive)
	{
		return countFiles(dirh, (java.io.FileFilter)null, stopOnFirst, recursive);
	}

	public static int countFiles(java.io.File dirh, java.io.FileFilter filter, boolean stopOnFirst, boolean recursive)
	{
		java.io.File files[] = dirh.listFiles(filter);
		int dirsize = (files == null ? 0 : files.length);
		int cnt = 0;

		for (int idx = 0; idx != dirsize; idx++) {
			if (files[idx].isDirectory()) {
				if (recursive) cnt += countFiles(files[idx], filter, stopOnFirst, true);
			} else {
				cnt++;
			}
			if (stopOnFirst && cnt != 0) return cnt;
		}
		return cnt;
	}

	// Identical to the above, but with a filename-oriented filter. For situations where the filter class would only use the File
	// handle to obtain the filename anyway, this method may be more efficient than the one above, assuming it costs the JDK less
	// to feed us filenames than File handles.
	public static int countFiles(java.io.File dirh, java.io.FilenameFilter filter, boolean stopOnFirst, boolean recursive)
	{
		java.io.File files[] = dirh.listFiles(filter);
		int dirsize = (files == null ? 0 : files.length);
		int cnt = 0;

		for (int idx = 0; idx != dirsize; idx++) {
			if (files[idx].isDirectory()) {
				if (recursive) cnt += countFiles(files[idx], filter, stopOnFirst, true);
			} else {
				cnt++;
			}
			if (stopOnFirst && cnt != 0) return cnt;
		}
		return cnt;
	}

	public static int deleteOlderThan(java.io.File dirh, long min_age, java.io.FileFilter filter, boolean recursive)
		throws java.io.IOException
	{
		if (!dirh.exists()) return 0;
		java.io.File files[] = dirh.listFiles(filter);
		int dirsize = (files == null ? 0 : files.length);
		int cnt = 0;

		for (int idx = 0; idx != dirsize; idx++) {
			if (files[idx].isDirectory()) {
				if (recursive) cnt += deleteOlderThan(files[idx], min_age, filter, true);
			} else {
				if (min_age == -1 || files[idx].lastModified() < min_age) {
					deleteFile(files[idx]);
					cnt++;
				}
			}
		}
		if (min_age == -1) deleteFile(dirh);
		return cnt;
	}

	public static int deleteDirectory(java.io.File dirh) throws java.io.IOException
	{
		return deleteOlderThan(dirh, -1, null, true);
	}

	public static void ensureDirExists(java.nio.file.Path dirh) throws java.io.IOException
	{
		java.nio.file.Files.createDirectories(dirh, FATTR_NONE);
	}

	public static void sortByFilename(java.util.ArrayList<java.nio.file.Path> paths)
	{
		java.util.Collections.sort(paths, cmp_filename);
	}

	public static void sortByFilename(java.nio.file.Path[] paths, int off, int len)
	{
		java.util.Arrays.sort(paths, off, off+len, cmp_filename);
	}

	public static String getFileType(String filename)
	{
		String dirslash = SysProps.get("file.separator");
		int pos_slash = filename.lastIndexOf(dirslash);
		if (pos_slash == -1) pos_slash = filename.lastIndexOf('/'); //because this serves as generic separator
		int pos = filename.lastIndexOf('.');
		if (pos <= pos_slash) return null;
		return filename.substring(pos+1);
	}

	private static byte[] readAndClose(java.io.InputStream strm, int reqlen, ArrayRef<byte[]> bufh) throws java.io.IOException
	{
		try {
			return read(strm, reqlen, bufh);
		} finally {
			if (strm != null) strm.close();
		}
	}

	// NB: This doesn't just alloc capacity, it also sets bufh.len to the allocated size in advance, expecting caller to read in that much
	private static byte[] alloc(ArrayRef<byte[]> bufh, int size)
	{
		byte[] buf = null;

		if (bufh == null) {
			if (size != 0) buf = new byte[size];
		} else {
			if (bufh.ar_buf.length - bufh.ar_off < size) {
				bufh.ar_buf = new byte[size];
				bufh.ar_off = 0;
			}
			bufh.ar_len = size;
			buf = bufh.ar_buf;
		}
		return buf;
	}
}