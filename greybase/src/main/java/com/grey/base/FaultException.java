/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base;

/*
 * This exception can be used by a software layer to wrap exceptions from below it,
 * so that it's own callers don't have to code against underlying interfaces they
 * don't need to be aware of.
 */
public class FaultException
	extends GreyException
{
	private static final long serialVersionUID = 1L;

	public FaultException(Throwable ex, String msg) {
		super(ex, msg);
	}

	public FaultException(String msg) {
		super(msg);
	}
}
