/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.ByteOps;
import com.grey.base.utils.StringOps;

public final class Client
{
	// Null host arg means localhost
	public static String submitCommand(String host, int port, Command.Def def, String[] args, com.grey.logging.Logger log) throws java.io.IOException
	{
		//serialise the args - if any
		StringBuilder xmit_args = new StringBuilder();
		for (int idx = 0; args != null && idx != args.length; idx++) {
			if (idx != 0) xmit_args.append((char)Command.ARGSDLM);
			xmit_args.append(args[idx]);
		}

		//construct the NAFMAN protocol frame to send
		byte[] xmitbuf = new byte[Command.HDRLEN+xmit_args.length()];
		ByteOps.encodeInt(def.code, xmitbuf, 0, Command.FLDLEN_CODE);
		ByteOps.encodeInt(xmit_args.length(), xmitbuf, Command.FLDLEN_CODE, Command.FLDLEN_ARGSLEN);
		for (int idx = 0; idx != xmit_args.length(); idx++) {
			xmitbuf[Command.HDRLEN+idx] = (byte)xmit_args.charAt(idx);
		}

		log(log, "NAFMAN Client connecting to target app at "+(host==null?"":host)+":"+port+" ... ");
		java.io.ByteArrayOutputStream response = new java.io.ByteArrayOutputStream();
		java.net.Socket sock = new java.net.Socket(host, port);
		log(log, "NAFMAN Client sending command: "+def.name+" "+xmit_args);
		try {
			// send command
			java.io.OutputStream ostrm = sock.getOutputStream();
			ostrm.write(xmitbuf);
			ostrm.flush();
			// wait for response
			com.grey.base.utils.FileOps.copyStream(sock.getInputStream(), response);
			log(log, "NAFMAN Client completed command="+def.name+"/"+def.code);
		} finally {
			sock.close();
		}
		return response.toString(StringOps.DFLT_CHARSET);
	}

	public static String submitCommand(String hostport, Command.Def def, String[] args, com.grey.logging.Logger log) throws java.io.IOException
	{
		int pos = hostport.indexOf(':');
		String host = (pos == -1 ? null : hostport.substring(0, pos));
		String port = (pos == -1 ? hostport : hostport.substring(pos+1));
		return submitCommand(host, Integer.parseInt(port), def, args, log);
	}

	public static String submitLocalCommand(String cfgfile, Command.Def def, String[] args, com.grey.logging.Logger log)
			throws com.grey.base.ConfigException, java.io.IOException
	{
		com.grey.naf.Config nafcfg = com.grey.naf.Config.load(cfgfile);
		int port = nafcfg.getPort(com.grey.naf.Config.RSVPORT_NAFMAN);
		return submitCommand(null, port, def, args, log);
	}

	public static Command.Def parseCommand(String cmdname, int argc, boolean lenient, com.grey.logging.Logger log)
	{
		StringBuilder sb = new StringBuilder();
		Command.Def def = Registry.get().getCommand(cmdname, sb);

		if (def == null) {
			if (lenient) {
				int code = 0;
				try {
					code = Integer.parseInt(cmdname);
				} catch (NumberFormatException ex) {
					//code=0 will fail, but it allows us to probe the NAFMAN agent's error-handling
				}
				def = new Command.Def(code, cmdname, 0, 100, false, null);
			} else {
				log(log, "ERROR:\n"+sb);
			}
			return def;
		}
		sb.setLength(0);
		sb = Registry.get().validateArgCount(def, argc, sb);

		if (sb != null && !lenient) {
			def = null;
			log(log, sb);
		}
		return def;
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
