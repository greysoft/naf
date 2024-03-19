/*
 * Copyright 2013-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;
import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.naf.nafman.NafManConfig;
import com.grey.logging.Logger;

public class TestUtils {

	public static String initPaths(Class<?> clss) {
		String rootpath = SysProps.TMPDIR+"/utest/naf/"+clss.getPackage().getName()+"/"+clss.getSimpleName();
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_ROOT, rootpath);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_CONF, null);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_VAR, null);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_LOGS, null);
		SysProps.set(NAFConfig.SYSPROP_DIRPATH_TMP, null);
		try {
			FileOps.deleteDirectory(rootpath);
		} catch (Exception ex) {
			throw new RuntimeException("DispatcherTest.initPaths failed to remove root="+rootpath+" - "+ex, ex);
		}
		return rootpath;
	}

	public static ApplicationContextNAF createApplicationContext(String name, String cfgpath, boolean withNafman, Logger bootLogger) {
		NAFConfig nafcfg = new NAFConfig.Builder().withConfigFile(cfgpath).build();
		return createApplicationContext(name, nafcfg, withNafman, bootLogger);
	}

	public static ApplicationContextNAF createApplicationContext(String name, boolean withNafman, Logger bootLogger) {
		NAFConfig nafcfg = new NAFConfig.Builder().withBasePort(NAFConfig.RSVPORT_ANON).build();
		return createApplicationContext(name, nafcfg, withNafman, bootLogger);
	}

	private static ApplicationContextNAF createApplicationContext(String name, NAFConfig nafcfg, boolean withNafman, Logger bootLogger) {
		NafManConfig nafmanConfig = (withNafman ? new NafManConfig.Builder(nafcfg).build() : null);
		return ApplicationContextNAF.builder()
				.withName(name)
				.withNafConfig(nafcfg)
				.withNafManConfig(nafmanConfig)
				.withBootLogger(bootLogger)
				.build();
	}

	// NB: The concept of mapping a resource URL to a File is inherently flawed, but this utility works because the resources we're
	// looking up are in the same build tree.
	public static String getResourcePath(String path, Class<?> clss) throws java.io.IOException, java.net.URISyntaxException {
		java.net.URL url = DynLoader.getResource(path, clss);
		if (url == null) return null;
		return new java.io.File(url.toURI()).getCanonicalPath();
	}
}
