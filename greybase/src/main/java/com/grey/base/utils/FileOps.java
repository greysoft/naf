/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

public class FileOps
{
	public static final String URLPFX_HTTP = "http:";
	public static final String URLPFX_HTTPS = "https:";
	public static final String URLPFX_JAR = "jar:";
	public static final String URLPFX_FILE = "file:";
	protected static final int RDBUFSIZ = SysProps.get("grey.fileio.rdbufsiz", 1024); //min buffer for unknown stream size

	public interface LineReader
	{
		public boolean processLine(String line, int line_number, int mode, Object cbdata) throws Exception;
	}

	public static class Filter_EndsWith implements java.io.FileFilter
	{
		private final String sfx;
		private final boolean nocase;
		private final boolean allowdirs;

		public Filter_EndsWith(CharSequence s, boolean nc, boolean dirs)
		{
			sfx = (nc ? s.toString().toLowerCase() : s.toString());
			nocase = nc;
			allowdirs = dirs;
		}

		@Override
		public boolean accept(java.io.File fh)
		{
			if (allowdirs && fh.isDirectory()) return true;
			String filename = fh.getName();
			if (nocase) filename = filename.toLowerCase();
			return filename.endsWith(sfx);
		}
	}


	public static void writeTextFile(String pthnam, String txt) throws java.io.IOException {writeTextFile(new java.io.File(pthnam), txt, false);}
	public static int deleteDirectory(String pthnam) throws java.io.IOException {return deleteDirectory(new java.io.File(pthnam));}
	public static boolean ensureDirExists(String pthnam) throws java.io.IOException  {return ensureDirExists(new java.io.File(pthnam));}
	public static void deleteFile(java.io.File fh) throws java.io.IOException {deleteFile(fh, "");}


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
		byte[] rdbuf = alloc(bufh, bufcap);  // this is the intermediate read buffer, a staging area between istrm and ostrm
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
			if (nbytes == -1) break;  //end of input stream

			// accumulate data in 'buf' if there's space, to avoid copying into the byte-stream on every single read
			buflen += nbytes;
			totaldata += nbytes;
		}

		if (ostrm == null)
		{
			// NB: This means totaldata == buflen
			// Either the caller provided enough storage, or we allocated an intermediate read buffer that was sufficient
			if (totaldata == 0) return null;  // there was no data

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
			if (buflen != 0) ostrm.write(rdbuf, off, buflen);  // append the final batch of data to the byte stream first
			rdbuf = ostrm.toByteArray();
		}

		if (bufh != null)
		{
			if (bufh.ar_buf != rdbuf) bufh.ar_off = 0;  // we allocated a new backing array, and it has no offset
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
		byte[] buf = read(url, -1, (ArrayRef<byte[]>)null);
		return StringOps.convert(buf, charset);
	}

	public static String readAsText(CharSequence pthnam_cs, String charset) throws java.io.IOException
	{
		byte[] buf = read(pthnam_cs, -1, (ArrayRef<byte[]>)null);
		return StringOps.convert(buf, charset);
	}

	public static void writeTextFile(java.io.File fh, String txt, boolean append) throws java.io.IOException
	{
		java.io.FileWriter cstrm = new java.io.FileWriter(fh, append);
		try {
			cstrm.append(txt);
		} finally {
			cstrm.close();  // this flushes its own buffer and closes the underlying stream
		}
	}

	public static int readTextLines(java.io.InputStream strm, LineReader consumer, int mode, Object cbdata, int bufsiz) throws java.io.IOException
	{
		java.io.InputStreamReader cstrm = new java.io.InputStreamReader(strm);
		java.io.BufferedReader bstrm = new java.io.BufferedReader(cstrm, bufsiz);
		int lno = 0;
		String line;
		try {
			while ((line = bstrm.readLine()) != null) {
				try {
					if (consumer.processLine(line, ++lno, mode, cbdata)) break;
				} catch (Exception ex) {
					throw new RuntimeException("LineReader callback failed on line="+lno+": "+line, ex);
				}
			}
		} finally {
			bstrm.close();
		}
		return lno;
	}

	public static int readTextLines(java.io.File fh, LineReader consumer, int mode, Object cbdata, int bufsiz) throws java.io.IOException
	{
		java.io.InputStream strm = new java.io.FileInputStream(fh);
		return readTextLines(strm, consumer, mode, cbdata, bufsiz);
	}

	public static int readTextLines(java.net.URL path, LineReader consumer, int mode, Object cbdata, int bufsiz) throws java.io.IOException
	{
		java.io.InputStream strm = path.openStream();
		return readTextLines(strm, consumer, mode, cbdata, bufsiz);
	}

	public static long copyFile(java.io.File srcfile, java.io.File dstfile) throws java.io.IOException
	{
		java.io.FileInputStream fin = new java.io.FileInputStream(srcfile);
		java.io.FileOutputStream fout = null;
		long nbytes = 0;

		try {
			fout = new java.io.FileOutputStream(dstfile);
			java.nio.channels.FileChannel inchan = fin.getChannel();
			java.nio.channels.FileChannel outchan = fout.getChannel();
			nbytes = outchan.transferFrom(inchan, 0, inchan.size());
		} finally {
			try {fin.close();} catch (Exception ex) {}
			if (fout != null) try {fout.close();} catch (Exception ex) {}
		}
		return nbytes;
	}

	public static long copyFile(CharSequence srcpath, CharSequence dstpath) throws java.io.IOException
	{
		java.io.File srcfile = new java.io.File(srcpath.toString());
		java.io.File dstfile = new java.io.File(dstpath.toString());
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
		copyFile(srcfile, dstfile);
		deleteFile(srcfile, "MoveFile: ");
	}

	public static void moveFile(CharSequence srcpath, CharSequence dstpath) throws java.io.IOException
	{
		java.io.File srcfile = new java.io.File(srcpath.toString());
		java.io.File dstfile = new java.io.File(dstpath.toString());
		moveFile(srcfile, dstfile);
	}

	private static void deleteFile(java.io.File fh, String tag) throws java.io.IOException
	{
		if (!fh.delete()) {
			//we didn't delete the file - but maybe that's because another thread or process got there first
			if (fh.exists()) {
				//... no, it's definitely the case that we have failed to delete this file
				throw new java.io.IOException(tag+"Failed to delete "+(fh.isDirectory()?"directory":"file")+"="+fh.getAbsolutePath());
			}
		}
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
		java.io.File dirh = fh.getParentFile();  // incredibly, beware that this will be null, if filename is specified without directory slashes
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

	// Beware that this literally does count only files, not directories
	public static int countFiles(java.io.File dirh, boolean recursive)
	{
		return countFiles(dirh, (java.io.FileFilter)null, false, recursive);
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

	public static int deleteDirectory(java.io.File dirh) throws java.io.IOException
	{
		return deleteOlderThan(dirh, -1, null, true);
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

	public static boolean ensureDirExists(java.io.File dirh) throws java.io.IOException
	{
		if (dirh.exists()) return false;
		if (!dirh.mkdirs()) {
			if (!dirh.exists() || !dirh.isDirectory()) throw new java.io.IOException("Failed to create directory: "+dirh.getAbsoluteFile());
		}
		return true;
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