/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Tuple2Mutable<T1,T2> extends Tuple implements Tuple2<T1,T2> {

	private T1 value1;
	private T2 value2;
	private final Lock mutex1;
	private final Lock mutex2;

	public Tuple2Mutable() {
		this(false, null, null);
	}

	public Tuple2Mutable(boolean mt, T1 value1, T2 value2) {
		this(mt, 2, value1, value2);
	}

	protected Tuple2Mutable(boolean mt, int dimension, T1 value1, T2 value2) {
		super(dimension, true);

		if (mt) {
			mutex1 = new ReentrantLock();
			mutex2 = new ReentrantLock();
		} else {
			mutex1 = null;
			mutex2 = null;
		}
		lockMutex(mutex1);
		this.value1 = value1;
		releaseMutex(mutex1);

		lockMutex(mutex2);
		this.value2 = value2;
		releaseMutex(mutex2);
	}

	@Override
	public T1 getValue1() {
		lockMutex(mutex1);
		try {
			return value1;
		} finally {
			releaseMutex(mutex1);
		}
	}

	@Override
	public T2 getValue2() {
		lockMutex(mutex2);
		try {
			return value2;
		} finally {
			releaseMutex(mutex2);
		}
	}

	public T1 setValue1(T1 v) {
		lockMutex(mutex1);
		try {
			T1 prev = value1;
			value1 = v;
			return prev;
		} finally {
			releaseMutex(mutex1);
		}
	}

	public T2 setValue2(T2 v) {
		lockMutex(mutex2);
		try {
			T2 prev = value2;
			value2 = v;
			return prev;
		} finally {
			releaseMutex(mutex2);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected <T> T getOrdinalValue(int idx) {
		if (idx == 0) return (T)getValue1();
		return (T)getValue2();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T setOrdinalValue(int idx, T value) {
		if (idx == 0) return (T)setValue1((T1)value);
		return (T)setValue2((T2)value);
	}

	protected final void lockMutex(Lock mutex) {
		if (mutex != null) {
			mutex.lock();
		}
	}

	protected static void releaseMutex(Lock mutex) {
		if (mutex != null) {
			mutex.unlock();
		}
	}
}
