/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

public class DispatcherDef
{
	public String name;
	public boolean hasNafman = true;
	public boolean hasDNS = false;
	public boolean zeroNaflets = true;
	public boolean surviveDownstream = true;
	public boolean surviveHandlers = true;
	public long flush_interval;
	public com.grey.base.config.XmlConfig[] naflets;

	public DispatcherDef() {}

	public DispatcherDef(com.grey.base.config.XmlConfig cfg) throws com.grey.base.ConfigException
	{
		name = cfg.getValue("@name", false, name);
		hasNafman = cfg.getBool("@nafman", hasNafman);
		hasDNS = cfg.getBool("@dns", hasDNS);
		zeroNaflets = cfg.getBool("@zero_naflets", zeroNaflets);
		surviveDownstream = cfg.getBool("@survive_downstream", surviveDownstream);
		surviveHandlers = cfg.getBool("@survive_handlers", surviveHandlers);
		flush_interval = cfg.getTime("@flush", flush_interval);

		String xpath = "naflets/naflet"+com.grey.base.config.XmlConfig.XPATH_ENABLED;
		naflets = cfg.subSections(xpath);
	}
}
