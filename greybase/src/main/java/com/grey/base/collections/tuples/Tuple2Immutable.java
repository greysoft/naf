/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

public class Tuple2Immutable<T1,T2> extends Tuple implements Tuple2<T1,T2> {

	private final T1 value1;
	private final T2 value2;

	public Tuple2Immutable(T1 value1, T2 value2) {
		this(2, value1, value2);
	}

	protected Tuple2Immutable(int dimension, T1 value1, T2 value2) {
		super(dimension, false);
		this.value1 = value1;
		this.value2 = value2;
	}

	@Override
	public T1 getValue1() {
		return value1;
	}

	@Override
	public T2 getValue2() {
		return value2;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected <T> T getOrdinalValue(int idx) {
		if (idx == 0) return (T)getValue1();
		return (T)getValue2();
	}
}
