/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.StringOps;

public final class Client
{
	// Null host arg means localhost
	public static String submitCommand(String cmd, String host, int port, com.grey.logging.Logger log) throws java.io.IOException
	{
		//construct the HTTP request
		String httpreq = "GET /"+cmd+" HTTP/1.1\n\n";
        java.io.ByteArrayOutputStream response = new java.io.ByteArrayOutputStream();
		log(log, "NAFMAN Client connecting to target app at "+(host==null?"":host)+":"+port+" ... ");
        java.net.Socket sock = new java.net.Socket(host, port);
        log(log, "NAFMAN Client sending command: "+cmd);
        try {
            // send command
            java.io.OutputStream ostrm = sock.getOutputStream();
            ostrm.write(httpreq.getBytes(StringOps.DFLT_CHARSET));
            ostrm.flush();
            // wait for response
            com.grey.base.utils.FileOps.copyStream(sock.getInputStream(), response);
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

	public static String submitLocalCommand(String cmd, String cfgfile, com.grey.logging.Logger log)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgfile);
		int port = nafcfg.getPort(com.grey.naf.Config.RSVPORT_NAFMAN);
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