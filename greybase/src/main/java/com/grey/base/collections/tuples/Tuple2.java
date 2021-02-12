/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

public interface Tuple2<T1,T2> {
	public abstract T1 getValue1();
	public abstract T2 getValue2();
}
