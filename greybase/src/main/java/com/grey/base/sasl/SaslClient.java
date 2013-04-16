/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.sasl;

public abstract class SaslClient
	extends SaslEntity
{
	public SaslClient(SaslEntity.MECH id, boolean base64) {super(id, base64);}
}