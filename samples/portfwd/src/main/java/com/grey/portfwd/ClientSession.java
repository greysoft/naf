/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.TimeOps;
import com.grey.base.config.XmlConfig.XmlConfigException;
import com.grey.naf.NAFConfig;
import com.grey.logging.Logger.LEVEL;

public class ClientSession
	extends com.grey.naf.reactor.CM_Server
	implements com.grey.naf.reactor.TimerNAF.Handler
{
	public static final class Factory
		implements com.grey.naf.reactor.ConcurrentListener.ServerFactory
	{
		final com.grey.naf.reactor.CM_Listener lstnr;
		final com.grey.portfwd.balance.Balancer loadbalancer;
		final com.grey.naf.BufferSpec bufspec;
		final long tmt_idle;

		@Override
		public ClientSession factory_create() {return new ClientSession(this);}
		@Override
		public Class<ClientSession> getServerClass() {return ClientSession.class;}
		@Override
		public void shutdown() {}

		public Factory(com.grey.naf.reactor.CM_Listener l, com.grey.base.config.XmlConfig cfg)
				throws java.net.UnknownHostException
		{
			lstnr = l;
			com.grey.base.config.XmlConfig[] servicecfg = cfg.getSections("services/service");
			com.grey.base.config.XmlConfig balancercfg = cfg.getSection("loadbalancer");
			bufspec = new com.grey.naf.BufferSpec(cfg, "niobuffers", 1024, 512);
			tmt_idle = cfg.getTime("services/@timeout", 0);

			if (servicecfg == null) {
				throw new XmlConfigException("Server="+lstnr.name+": No services found");
			}
			com.grey.base.utils.TSAP[] services = new com.grey.base.utils.TSAP[servicecfg.length];

			for (int idx = 0; idx != services.length; idx++) {
				String address = servicecfg[idx].getValue("@address", true, null);
				services[idx] = com.grey.base.utils.TSAP.build(address, 0, true);
			}
			Object obj = NAFConfig.createEntity(balancercfg, com.grey.portfwd.balance.RoundRobin.class, com.grey.portfwd.balance.Balancer.class, false,
					new Class<?>[]{com.grey.base.utils.TSAP[].class},
					new Object[]{services});
			loadbalancer = com.grey.portfwd.balance.Balancer.class.cast(obj);

			l.getLogger().info("Server for "+lstnr.name+" has LoadBalancer="+loadbalancer.getClass().getName()
					+", Controller="+lstnr.getController().getClass().getName()+", Timeout="+TimeOps.expandMilliTime(tmt_idle));
			l.getLogger().trace("NIO-Buffers: "+bufspec);
		}
	}

	private final Relay relay;
	private final long tmt_idle;
	private com.grey.naf.reactor.TimerNAF tmr;

	ClientSession(Factory fact)
	{
		super(fact.lstnr, fact.bufspec, fact.bufspec);
		tmt_idle = fact.tmt_idle;
		relay = new Relay(this, fact.loadbalancer, fact.bufspec);
	}

	public void initiateIO() throws java.io.IOException
	{
		if (tmt_idle != 0) tmr = getDispatcher().setTimer(tmt_idle, 0, this);
		getReader().receive(0);
	}

	@Override
	protected void connected() throws java.io.IOException
	{
		relay.clientConnected();
	}

	@Override
	protected void ioDisconnected(CharSequence diag)
	{
		relay.clientDisconnected();
		endConnection();
	}

	@Override
	public void ioReceived(ByteArrayRef data) throws java.io.IOException
	{
		if (tmr != null) tmr.reset();
		relay.server.transmit(data);
	}

	public void endConnection()
	{
		if (tmr != null) {
			tmr.cancel();
			tmr = null;
		}
		disconnect(); //returns this object to Listener's ObjectWell of inactive servers
	}

	public void transmit(ByteArrayRef data) throws java.io.IOException
	{
		getWriter().transmit(data);
	}

	// The Timer param must be our 'tmr' field, as we only created one Timer
	@Override
	public void timerIndication(com.grey.naf.reactor.TimerNAF t, com.grey.naf.reactor.Dispatcher d)
	{
		tmr = null; //mark timer as expired
		if (getLogger().isActive(LEVEL.TRC)) getLogger().trace("Closing idle connection: "+getChannel()+" => "+relay.server.getServerAddress().sockaddr);
		ioDisconnected("Timeout");
	}

	// already logged by Dispatcher, no need to do anything else
	@Override
	public void eventError(com.grey.naf.reactor.TimerNAF t, com.grey.naf.reactor.Dispatcher d, Throwable ex) {}
}
