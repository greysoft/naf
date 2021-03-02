/*
 * Copyright 2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.errors;

public class NAFException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final boolean is_error;

	public NAFException(boolean error, String msg, Throwable cause) {
		super(msg, cause);
		this.is_error = error;
	}

	public NAFException(boolean error, String msg) {
		this(error, msg, null);
	}

	public NAFException(String msg) {
		this(msg, null);
	}

	public NAFException(Throwable cause) {
		this(null, cause);
	}

	public NAFException(String msg, Throwable cause) {
		this(false, msg, cause);
	}

	public boolean error() {
		return is_error;
	}

	@Override
	public String toString() {
		return super.toString()+" - error="+error();
	}

	public static boolean isError(Throwable ex) {
		if (ex instanceof NAFException) return ((NAFException)ex).error();
		return (ex instanceof Error
				|| ex instanceof RuntimeException);
	}
}