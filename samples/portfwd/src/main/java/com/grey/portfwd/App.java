/*
 * Copyright 2018-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.naf.Launcher;

public class App
	extends Launcher
{
	public static void main(String[] args) throws Exception {
		App app = new App(args);
		app.execute("portfwd");
	}

	public App(String[] args) {
		super(args);
		com.grey.base.utils.PkgInfo.announceJAR(getClass(), "portfwd", null);
	}
}
