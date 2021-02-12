/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

public class Tuple3Immutable<T1,T2,T3> extends Tuple2Immutable<T1,T2> implements Tuple3<T1,T2,T3> {

	private final T3 value3;

	public Tuple3Immutable(T1 value1, T2 value2, T3 value3) {
		this(3, value1, value2, value3);
	}

	protected Tuple3Immutable(int dimension, T1 value1, T2 value2, T3 value3) {
		super(dimension, value1, value2);
		this.value3 = value3;
	}

	@Override
	public T3 getValue3() {
		return value3;
	}

	@Override
	protected <T> T getOrdinalValue(int idx) {
		if (idx == 2) {
			@SuppressWarnings("unchecked") T v = (T)getValue3();
			return v;
		}
		return super.getOrdinalValue(idx);
	}
}
