/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package org.slf4j.impl;

/*
 * This was adaptered from slf4j-simple package (but in fact slf4j-log4j version is the same)
 */
public class StaticMarkerBinder
	implements org.slf4j.spi.MarkerFactoryBinder
{
	public static final StaticMarkerBinder SINGLETON = new StaticMarkerBinder();

	private final org.slf4j.IMarkerFactory markerFactory = new org.slf4j.helpers.BasicMarkerFactory();

	private StaticMarkerBinder() {}

	public org.slf4j.IMarkerFactory getMarkerFactory()
	{
		return markerFactory;
	}

	public String getMarkerFactoryClassStr()
	{
		return getMarkerFactory().getClass().getName();
	}
}
