/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class PrototypeFactory
	implements ObjectWell.ObjectFactory
{
	public interface PrototypeObject
	{
		public PrototypeObject prototype_create();
	}


	private final PrototypeObject prototype;

	public PrototypeFactory(PrototypeObject proto) {
		prototype = proto;
	}

	// The prototype object creates and returns an object of it's own type, which would therefore also be a PrototypeObject, even though
	// the newly created instance is unlikely to also be used as a prototype.
	@Override
	public PrototypeObject factory_create() {
		return prototype.prototype_create();
	}
}
