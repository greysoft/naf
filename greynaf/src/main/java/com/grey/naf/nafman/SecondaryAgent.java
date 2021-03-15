/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.naf.reactor.Dispatcher;
import com.grey.naf.reactor.Producer;

public class SecondaryAgent
	extends NafManAgent
	implements Producer.Consumer<NafManCommand>
{
	private final PrimaryAgent primary;
	private final Producer<NafManCommand> requests; //for receiving commands from the primary agent

	@Override
	public PrimaryAgent getPrimary() {return primary;}
	@Override
	public int getPort() {return primary.getPort();}

	public SecondaryAgent(Dispatcher dsptch, NafManRegistry reg, NafManConfig cfg) throws java.io.IOException
	{
		super(dsptch, reg);
		primary = dsptch.getApplicationContext().getNamedItem(PrimaryAgent.class.getName(), null);
		if (primary == null) {
			throw new IllegalStateException("Dispatcher="+dsptch.getName()+": Cannot create Secondary NAFMAN before Primary");
		}
		requests = new Producer<NafManCommand>(NafManCommand.class, dsptch, this);

		// we have to subscribe to the Primary in our constructor due to unit-test timings, but 'requests' producer could be started in our start()
		primary.secondarySubscribed(this);
		requests.start();
	}

	@Override
	public void stop()
	{
		Dispatcher dsptch = getDispatcher();
		PrimaryAgent activePrimary = dsptch.getApplicationContext().getNamedItem(PrimaryAgent.class.getName(), null);
		dsptch.getLogger().info("NAFMAN Secondary="+dsptch.getName()+" shutdown with primary="+primary.getDispatcher().getName()+" - active="+activePrimary);
		setShutdown();
		requests.shutdown();
		if (activePrimary == null) return; //Primary has already exited, so don't signal it

		try {
			primary.secondaryUnsubscribed(this);
		} catch (Exception ex) {
			// probably due to Primary shutting down in tandem
			dsptch.getLogger().trace("NAFMAN="+dsptch.getName()+" failed to send Unsubscribe to Primary="+primary.getDispatcher().getName()+" - "+ex);
		}
	}

	@Override
	public void producerIndication(Producer<NafManCommand> p) throws java.io.IOException
	{
		NafManCommand cmd;
		while ((cmd = requests.consume()) != null) {
			commandReceived(cmd);
		}
	}

	void injectCommand(NafManCommand cmd) throws java.io.IOException {
		requests.produce(cmd);
	}
}
