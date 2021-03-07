/*
 * Copyright 2012-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd.balance;

import java.util.List;

import com.grey.base.utils.TSAP;
import com.grey.logging.Logger;
import com.grey.portfwd.ClientSession;

public class RoundRobin
	implements Balancer
{
	private final List<TSAP> services;
	private int next_service;

	public RoundRobin(List<TSAP> services, Logger logger)
	{
		this.services = services;
		logger.info("Created load-balancer="+this+" with services="+services.size()+"/"+services);
	}

	@Override
	public TSAP selectService(ClientSession client)
	{
		int idx = next_service++;
		if (next_service == services.size()) next_service = 0;
		return services.get(idx);
	}
}
