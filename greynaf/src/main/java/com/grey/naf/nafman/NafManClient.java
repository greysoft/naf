/*
 * Copyright 2010-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.grey.base.utils.StringOps;
import com.grey.base.utils.FileOps;
import com.grey.naf.NAFConfig;

public class NafManClient
{
	// Null host arg means localhost
	public static String submitCommand(String cmd, String host, int port, com.grey.logging.Logger log) throws java.io.IOException
	{
		//construct the HTTP request
		String httpreq = "GET /"+cmd+" HTTP/1.1\n\n";
		ByteArrayOutputStream response = new ByteArrayOutputStream();
		log(log, "NAFMAN Client connecting to target app at "+(host==null?"":host)+":"+port+" ... ");
		Socket sock = new Socket(host, port);
		log(log, "NAFMAN Client sending command: "+cmd);
		try {
			// send command
			OutputStream ostrm = sock.getOutputStream();
			ostrm.write(httpreq.getBytes(StringOps.DFLT_CHARSET));
			ostrm.flush();
			// wait for response
			FileOps.copyStream(sock.getInputStream(), response);
			log(log, "NAFMAN Client completed command="+cmd);
		} finally {
			sock.close();
		}
		return response.toString(StringOps.DFLT_CHARSET);
	}

	public static String submitCommand(String cmd, String hostport, com.grey.logging.Logger log) throws java.io.IOException
	{
		int pos = hostport.indexOf(':');
		String host = (pos == -1 ? null : hostport.substring(0, pos));
		String port = (pos == -1 ? hostport : hostport.substring(pos+1));
		return submitCommand(cmd, host, Integer.parseInt(port), log);
	}

	public static String submitLocalCommand(String cmd, String cfgfile, com.grey.logging.Logger log) throws java.io.IOException
	{
		NAFConfig nafcfg = NAFConfig.load(cfgfile);
		int port = nafcfg.getPort(NAFConfig.RSVPORT_NAFMAN);
		return submitLocalCommand(cmd, port, log);
	}

	public static String submitLocalCommand(String cmd, int port, com.grey.logging.Logger log) throws java.io.IOException
	{
		return submitCommand(cmd, null, port, log);
	}

	private static void log(com.grey.logging.Logger logger, CharSequence msg)
	{
		if (logger == null) {
			System.out.println(msg);
		} else {
			logger.info(msg);
		}
	}
}