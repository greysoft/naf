/*
 * Copyright 2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.errors;

public class NAFConfigException extends NAFException {
	private static final long serialVersionUID = 1L;

	public NAFConfigException(String msg) {
		super(true, msg);
	}

	public NAFConfigException(Throwable cause) {
		super(true, null, cause);
	}

	public NAFConfigException(String msg, Throwable cause) {
		super(true, msg, cause);
	}
}