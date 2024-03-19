/*
 * Copyright 2013-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.dns;

import com.grey.base.config.SysProps;
import com.grey.base.utils.FileOps;
import com.grey.naf.NAFConfig;

public class TestUtils {
	public static String initPaths(Class<?> clss) {
		String rootpath = SysProps.TMPDIR+"/utest/nafdns/"+clss.getPackage().getName()+"/"+clss.getSimpleName();
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
}
