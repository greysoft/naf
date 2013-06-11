/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

public final class Secondary
	extends Agent
{
	final com.grey.naf.reactor.Producer<Command> requests; //package-private
	private final Primary primary;

	@Override
	public boolean isPrimary() {return false;}
	@Override
	public Primary getPrimary() {return primary;}
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
		if (Primary.get() == null) return; //Primary has already exited, so don't signal it
		try {
			primary.secondaryUnsubscribed(this);
		} catch (Exception ex) {
			// probably due to Primary shutting down in tandem
			dsptch.logger.trace("NAFMAN="+dsptch.name+" failed to send Unsubscribe to Primary="+primary.dsptch.name+" - "+ex);
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
