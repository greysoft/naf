/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class PrototypeFactoryTest
	implements PrototypeFactory.PrototypeObject
{
	private final PrototypeFactory fact = new PrototypeFactory(this);
	private int marker;

	@org.junit.Test
	public void creation()
	{
		PrototypeFactoryTest obj = verify(9);
		PrototypeFactoryTest obj2 = verify(3);
		org.junit.Assert.assertNotSame(obj, obj2);
	}

	@Override
	public PrototypeFactory.PrototypeObject prototype_create()
	{
		PrototypeFactoryTest obj = new PrototypeFactoryTest();
		obj.marker = marker;
		return obj;
	}

	private PrototypeFactoryTest verify(int markerval) {
		marker = markerval;
		PrototypeFactoryTest obj = (PrototypeFactoryTest)fact.factory_create();
		org.junit.Assert.assertSame(getClass(), obj.getClass());
		org.junit.Assert.assertNotSame(obj, this);
		org.junit.Assert.assertEquals(markerval, obj.marker);
		org.junit.Assert.assertEquals(markerval, this.marker);
		return obj;
	}
}
