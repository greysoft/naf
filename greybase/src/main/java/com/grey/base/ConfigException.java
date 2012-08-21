/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base;

public class ConfigException
	extends GreyException
{
	private static final long serialVersionUID = 1L;

	public ConfigException(Throwable ex, String msg) {
		super(ex, msg);
	}

	public ConfigException(String msg) {
		super(msg);
	}
}
