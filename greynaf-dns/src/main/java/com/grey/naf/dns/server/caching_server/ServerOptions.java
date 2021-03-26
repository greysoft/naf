/*
 * Copyright 2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns.server.caching_server;

import com.grey.base.utils.CommandParser;
import com.grey.naf.dns.resolver.engine.PacketDNS;

public class ServerOptions extends CommandParser.OptionsHandler {
	private static final String[] opts = new String[]{"port:", "nameservers:", "tcp"};

	private int port = PacketDNS.INETPORT;
	private String[] nameServers;
	private boolean alwaysTCP;

	public ServerOptions() {
		super(opts, 0, 0);
	}

	@Override
	public void setOption(String opt) {
		if (opt.equals("tcp")) {
			alwaysTCP = true;
		} else {
			super.setOption(opt);
		}
	}

	@Override
	public void setOption(String opt, String val) {
		if (opt.equals("port")) {
			port = Integer.parseInt(val);
		} else if (opt.equals("nameservers")) {
			nameServers = (val.equals("-") ? null : val.split("|"));
		} else {
			super.setOption(opt, val);
		}
	}

	@Override
	public String displayUsage() {
		String txt = "\t-port num -nameservers pipe-separated-hostport-list -tcp";
		txt += "\nAll the params are optional";
		return txt;
	}

	public int getPort() {
		return port;
	}

	public String[] getNameServers() {
		return nameServers;
	}

	public boolean isAlwaysTCP() {
		return alwaysTCP;
	}
}