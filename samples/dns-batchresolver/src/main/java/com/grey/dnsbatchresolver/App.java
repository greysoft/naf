/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.dnsbatchresolver;

import com.grey.naf.Launcher;

public class App
	extends Launcher
{
	public static void main(String[] args) throws Exception {
		App app = new App(args);
		app.execute("dnsbatchresolver");
	}

	public App(String[] args) {
		super(args);
	}
}