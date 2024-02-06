/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base;

import com.grey.base.config.SysProps;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.TimeOps;

/* Example command-lines:
 *     java -cp greybase.jar com.grey.base.Exec showtime 1278443608419 gmt
 */
public class Exec
{
	private static final boolean listprops = SysProps.get("grey.listprops", false);
	private static final String extracp = SysProps.get("grey.cp");

	public static void main(String[] argv) throws Exception
	{
		if (listprops) {
			SysProps.dump(System.getProperties(), System.out);
		}
		if (extracp != null) {
			DynLoader.load(extracp);
		}
		if (argv == null || argv.length == 0) {
			System.out.println("No command specified");
			return;
		}
		int argc = 0;
		String cmd = argv[argc++].toUpperCase().intern();
		System.out.println();

		if (cmd == "SHOWTIME") {
			long systime_now = System.currentTimeMillis();
			String tzarg = null;
			long systime = Long.parseLong(argv[argc++]);
			if (argv.length > argc) tzarg = argv[argc++].toUpperCase();
			java.util.Calendar dtcal = com.grey.base.utils.TimeOps.getCalendar(systime, tzarg);
			java.util.TimeZone tz = dtcal.getTimeZone();
			java.util.Date dt = new java.util.Date(dtcal.getTimeInMillis());
			System.out.println(dt.getClass().getName()+": "+dt);
			System.out.println("TimeZone: "+TimeOps.displayTimezone(tz, systime));
			StringBuilder sb = com.grey.base.utils.TimeOps.makeTimeISO8601(dtcal, null, true, false, true);
			System.out.println("ISO8601: " + sb.toString());
			sb = com.grey.base.utils.TimeOps.makeTimeRFC822(dtcal, null);
			System.out.println("RFC822: " + sb.toString());
			sb = com.grey.base.utils.TimeOps.makeTimeLogger(dtcal, null, true, true);
			System.out.println("LogFormat: " + sb.toString());
			System.out.println("Time Now="+systime_now+", Diff="+(systime_now - systime));
		} else if (cmd == "ENCTIME") {
			int yy = Integer.parseInt(argv[argc++]);
			int mm = Integer.parseInt(argv[argc++]);
			int dd = Integer.parseInt(argv[argc++]);
			int hour = Integer.parseInt(argv[argc++]);
			int min = Integer.parseInt(argv[argc++]);
			long systime = TimeOps.getSystime(null, yy, mm, dd, hour, min);
			System.out.println("Time = "+systime);
		} else if (cmd == "SHOWIP") {
			int ip = Integer.parseInt(argv[argc++]);
			System.out.println("IP = " + com.grey.base.utils.IP.displayDottedIP(ip, null));
		} else if (cmd == "ENCIP") {
			String ipdotted = argv[argc++];
			System.out.println("IP = " + com.grey.base.utils.IP.convertDottedIP(ipdotted));
		} else if (cmd == "BASE64") {
			String enctxt = argv[argc++];
			char[] arrc = enctxt.toCharArray();
			byte[] arrb = com.grey.base.crypto.Base64.decode(arrc, 0, arrc.length, null);
			String plain = new String(arrb);
			System.out.println("Plain="+plain.length()+" ["+plain+"]");
		} else if (cmd == "ENCBASE64") {
			String plaintxt = argv[argc++];
			byte[] arrb = plaintxt.getBytes();
			char[] arrc = com.grey.base.crypto.Base64.encode(arrb, 0, arrb.length, 0, null);
			String enctxt = new String(arrc);
			System.out.println("Base64="+enctxt.length()+" ["+enctxt+"]");
		} else if (cmd == "HEX") {
			String enctxt = argv[argc++];
			byte[] arrb = com.grey.base.crypto.Ascii.hexDecode(enctxt.toCharArray());
			String plaintxt = new String(arrb);
			System.out.println("Plain="+plaintxt.length()+" ["+plaintxt+"]");
		} else if (cmd == "ENCHEX") {
			String plaintxt = argv[argc++];
			char[] arrc = com.grey.base.crypto.Ascii.hexEncode(plaintxt.getBytes());
			String enctxt = new String(arrc);
			System.out.println("Hex="+enctxt.length()+" ["+enctxt+"]");
		} else if (cmd == "LOADCLASS") {
			String name = argv[argc++];
			Class<?> clss = DynLoader.loadClass(name);
			System.out.println("Loaded class="+clss.getName());
			Object obj = clss.getDeclaredConstructor().newInstance();
			System.out.println("Created Object: "+obj);
		} else {
			System.out.println("Unrecognised command [" + cmd + "]");
		}
	}
}
