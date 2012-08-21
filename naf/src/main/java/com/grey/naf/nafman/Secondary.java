/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

public class Secondary
	extends Agent
{
	protected final com.grey.naf.reactor.Producer<Command> requests;
	private final Primary primary;

	@Override
	public boolean isPrimary() {return false;}
	@Override
	public int getPort() {return primary.getPort();}

	public Secondary(com.grey.naf.reactor.Dispatcher d, com.grey.base.config.XmlConfig cfg)
			throws com.grey.base.ConfigException, com.grey.base.FaultException, java.io.IOException
	{
		super(d, cfg);
		primary = Primary.get();
		if (primary == null) {
			throw new com.grey.base.ConfigException("Dispatcher="+d.name+": Cannot create Secondary NAFMAN before Primary");
		}
		requests = new com.grey.naf.reactor.Producer<Command>(Command.class, dsptch, this);
		primary.secondarySubscribed(this);
	}

	@Override
	public void stop()
	{
		in_shutdown = true;
		requests.shutdown(false);
		try {
			primary.secondaryUnsubscribed(this);
		} catch (Exception ex) {
			// probably due to Primary shutting down in tandem
			dsptch.logger.debug("NAFMAN="+dsptch.name+" failed to send Unsubscribe to Primary="+primary.dsptch.name);
		}
	}

	@Override
	public void producerIndication(com.grey.naf.reactor.Producer<?> p) throws com.grey.base.FaultException, java.io.IOException
	{
		Command cmd;
		while ((cmd = requests.consume()) != null) {
			commandReceived(cmd);
		}
	}
}
