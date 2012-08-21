/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd.balance;

/*
 * In addition to satisfying the explicit methods of this interface, implementing classes must also
 * provide a constructor with this signature:
 * classname(com.grey.base.utils.TSAP[] services)
 */
public interface Balancer
{
	public com.grey.base.utils.TSAP selectService(com.grey.portfwd.ClientSession client);
}
