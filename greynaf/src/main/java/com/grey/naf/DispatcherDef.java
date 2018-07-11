/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import com.grey.base.config.SysProps;

public class DispatcherDef
{
	public static final String SYSPROP_LOGNAME = "greynaf.dispatchers.logname";

	public boolean zeroNafletsOK = true;
	public boolean surviveDownstream = true;
	public boolean surviveHandlers = true;

	public String name;
	public String logname;
	public boolean hasNafman;
	public boolean hasDNS;
	public long flush_interval;
	public com.grey.base.config.XmlConfig[] naflets;

	public DispatcherDef() {}
	public DispatcherDef(String n) {name=n;}

	public DispatcherDef(com.grey.base.config.XmlConfig cfg)
	{
		name = cfg.getValue("@name", true, name);
		logname = cfg.getValue("@logname", true, SysProps.get(SYSPROP_LOGNAME, name));
		hasNafman = cfg.getBool("@nafman", hasNafman);
		hasDNS = cfg.getBool("@dns", hasDNS);
		zeroNafletsOK = cfg.getBool("@zero_naflets", zeroNafletsOK);
		surviveDownstream = cfg.getBool("@survive_downstream", surviveDownstream);
		surviveHandlers = cfg.getBool("@survive_handlers", surviveHandlers);
		flush_interval = cfg.getTime("@flush", flush_interval);

		String xpath = "naflets/naflet"+com.grey.base.config.XmlConfig.XPATH_ENABLED;
		naflets = cfg.getSections(xpath);
	}
}
