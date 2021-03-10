/*
 * Copyright 2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.Launcher;
import com.grey.naf.nafman.NafManRegistry;

public class App
	extends Launcher
{
	public static void main(String[] args) throws Exception {
		App app = new App(args);
		app.execute("portfwd");
	}

	private static final NafManRegistry.DefCommand[] nafman_cmds = new NafManRegistry.DefCommand[] {
			new NafManRegistry.DefCommand(Task.CMD_SHOWCONNS, "Port-Forwarder", "Show connection details", NafManRegistry.RSRC_CMDSTATUS, true)
	};


	public App(String[] args) {
		super(args);
		com.grey.base.utils.PkgInfo.announceJAR(getClass(), "portfwd", null);
	}

	@Override
	protected void setupNafMan(ApplicationContextNAF appctx) {
		NafManRegistry.get(appctx).loadCommands(nafman_cmds);
	}
}
