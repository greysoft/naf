/*
 * Copyright 2013-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

import java.io.IOException;

/*
 * This is an empty shell of a Naflet which performs no bespoke processing of its own.
 * It can be used as a placeholder in naf.xml, to initialise a Dispatcher with no other Naflets.
 */
public class ShellNaflet
	extends com.grey.naf.Naflet
{

	public ShellNaflet(String name, com.grey.naf.reactor.Dispatcher dsptch, com.grey.base.config.XmlConfig cfg) throws java.io.IOException
	{
		super(name, dsptch, cfg);
	}

	@Override
	protected void startNaflet() throws IOException
	{
		// do nothing
	}
}