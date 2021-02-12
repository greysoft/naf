/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

public interface Tuple3<T1,T2,T3> extends Tuple2<T1, T2> {
	public abstract T3 getValue3();
}
