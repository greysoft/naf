/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd.balance;

public class RoundRobin
	implements Balancer
{
	private final com.grey.base.utils.TSAP[] services;
	private int next_service;

	public RoundRobin(com.grey.base.utils.TSAP[] services)
	{
		this.services = services;
	}

	@Override
	public com.grey.base.utils.TSAP selectService(com.grey.portfwd.ClientSession client)
	{
		int idx = next_service++;
		if (next_service == services.length) next_service = 0;
		return services[idx];
	}
}
