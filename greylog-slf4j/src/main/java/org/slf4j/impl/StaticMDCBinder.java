/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package org.slf4j.impl;

/*
 * This was adaptered from slf4j-simple package
 */
public class StaticMDCBinder
{
	public static final StaticMDCBinder SINGLETON = new StaticMDCBinder();

	private StaticMDCBinder() {}

	public org.slf4j.spi.MDCAdapter getMDCA()
	{
		return new org.slf4j.helpers.NOPMDCAdapter();
	}

	public String getMDCAdapterClassStr()
	{
		return getMDCA().getClass().getName();
	}
}
