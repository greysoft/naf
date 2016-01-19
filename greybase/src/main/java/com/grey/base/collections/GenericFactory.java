/*
 * Copyright 2015 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections;

public interface GenericFactory<T>
	extends ObjectWell.ObjectFactory
{
	@Override
	public T factory_create();
}