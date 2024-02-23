/*
 * Copyright 2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

public interface AssignableReapable
{
	EntityReaper getReaper();
	void setReaper(EntityReaper rpr);
}
