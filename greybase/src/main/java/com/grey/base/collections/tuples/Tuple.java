/*
 * Copyright 2019-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.collections.tuples;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Tuple {

	private final int dimension;
	private final boolean mutable;

	protected abstract <T> T getOrdinalValue(int idx);
	protected <T> T setOrdinalValue(int idx, T v) {throw new UnsupportedOperationException("Tuple cannot be modified - "+this);}

	public Tuple(int dimension, boolean mutable) {
		this.dimension = dimension;
		this.mutable = mutable;
	}

	public int getDimension() {
		return dimension;
	}

	public boolean isMutable() {
		return mutable;
	}

	public <T> T getValue(int idx) {
		if (idx < 0 || idx >= dimension) throw new IllegalArgumentException("Index="+idx+" out of range on tuple="+this);
		return getOrdinalValue(idx);
	}

	public <T> T setValue(int idx, T value) {
		if (!isMutable()) throw new UnsupportedOperationException("Cannot modify immutable tuple - "+this);
		if (idx < 0 || idx >= dimension) throw new IllegalArgumentException("Index="+idx+" out of range on tuple="+this);
		return setOrdinalValue(idx, value);
	}

	public List<Object> getValues() {
		List<Object> terms = new ArrayList<>(dimension);
		for (int idx = 0; idx != dimension; idx++) {
			terms.add(getValue(idx));
		}
		return terms;
	}

	@Override
	public int hashCode() {
		int prime = 31;
		int result = 1;
		for (int idx = 0; idx != dimension; idx++) {
			result = (prime * result) + Objects.hashCode(getOrdinalValue(idx));
		}
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Tuple other = (Tuple)obj;
		if (other.getDimension() != getDimension()) return false;
		for (int idx = 0; idx != dimension; idx++) {
			if (!Objects.deepEquals(getOrdinalValue(idx), other.getOrdinalValue(idx))) return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(super.toString());
		String dlm = "/";
		for (int idx = 0; idx != dimension; idx++) {
			sb.append(dlm).append(idx+1).append('=').append(getOrdinalValue(idx).toString());
			dlm = " ";
		}
		return sb.toString();
	}
}
