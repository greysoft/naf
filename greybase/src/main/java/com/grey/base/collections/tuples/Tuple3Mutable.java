/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Tuple3Mutable<T1,T2,T3> extends Tuple2Mutable<T1,T2> implements Tuple3<T1,T2,T3> {

	private T3 value3;
	private final Lock mutex;

	public Tuple3Mutable() {
		this(false, null, null, null);
	}

	public Tuple3Mutable(boolean mt, T1 value1, T2 value2, T3 value3) {
		this(mt, 3, value1, value2, value3);
	}

	protected Tuple3Mutable(boolean mt, int dimension, T1 value1, T2 value2, T3 value3) {
		super(mt, dimension, value1, value2);
		mutex = (mt ? new ReentrantLock() : null);
		lockMutex(mutex);
		this.value3 = value3;
		releaseMutex(mutex);
	}

	@Override
	public T3 getValue3() {
		lockMutex(mutex);
		try {
			return value3;
		} finally {
			releaseMutex(mutex);
		}
	}

	public T3 setValue3(T3 v) {
		lockMutex(mutex);
		try {
			T3 prev = value3;
			value3 = v;
			return prev;
		} finally {
			releaseMutex(mutex);
		}
	}

	@Override
	protected <T> T getOrdinalValue(int idx) {
		if (idx == 2) {
			@SuppressWarnings("unchecked") T v = (T)getValue3();
			return v;
		}
		return super.getOrdinalValue(idx);
	}

	@Override
	public <T> T setOrdinalValue(int idx, T value) {
		if (idx == 2) {
			@SuppressWarnings("unchecked") T3 newval = (T3)value;
			@SuppressWarnings("unchecked") T oldval = (T)setValue3(newval);
			return oldval;
		}
		return super.setOrdinalValue(idx, value);
	}
}
