/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

public interface EntityReaper
{
	// The param is not necessarily of type AssignableReapable, as some objects simply report back to a fixed reaper or mutiple listeners
	void entityStopped(Object obj);
}
