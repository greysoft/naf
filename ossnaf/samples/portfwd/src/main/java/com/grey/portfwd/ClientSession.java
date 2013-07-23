/*
 * Copyright 2012-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.portfwd;

import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger.LEVEL;

public class ClientSession
	extends com.grey.naf.reactor.ConcurrentListener.Server
	implements com.grey.naf.reactor.Timer.Handler
{
	private final com.grey.portfwd.balance.Balancer loadbalancer;
	private final Relay relay;
	private final com.grey.naf.BufferSpec bufspec;
	private final long tmt_idle;
	private com.grey.naf.reactor.Timer tmr;

	// This is the prototype object which the Listener uses to create the rest
	public ClientSession(com.grey.naf.reactor.ConcurrentListener l, com.grey.base.config.XmlConfig cfg)
		throws com.grey.base.ConfigException, java.net.UnknownHostException
	{
		super(l);
		relay = null;
		com.grey.base.config.XmlConfig[] servicecfg = cfg.subSections("services/service");
		com.grey.base.config.XmlConfig balancercfg = new com.grey.base.config.XmlConfig(cfg, "loadbalancer");
		bufspec = new com.grey.naf.BufferSpec(cfg, "niobuffers", 1024, 512);
		tmt_idle = cfg.getTime("services/@timeout", 0);

		if (servicecfg == null) {
			throw new com.grey.base.ConfigException("Server="+lstnr.name+": No services found");
		}
		com.grey.base.utils.TSAP[] services = new com.grey.base.utils.TSAP[servicecfg.length];

		for (int idx = 0; idx != services.length; idx++) {
			String address = servicecfg[idx].getValue("@address", true, null);
			services[idx] = com.grey.base.utils.TSAP.build(address, 0);
		}
		Object obj = dsptch.nafcfg.createEntity(balancercfg, com.grey.portfwd.balance.RoundRobin.class, com.grey.portfwd.balance.Balancer.class, false,
				new Class<?>[]{com.grey.base.utils.TSAP[].class},
				new Object[]{services});
		loadbalancer = com.grey.portfwd.balance.Balancer.class.cast(obj);

		dsptch.logger.info("Server for "+lstnr.name+" has LoadBalancer="+loadbalancer.getClass().getName()
				+", Controller="+lstnr.controller.getClass().getName()+", Timeout="+TimeOps.expandMilliTime(tmt_idle));
		dsptch.logger.trace("NIO-Buffers: "+bufspec);
	}

	// This is (or will be) an active server object
	private ClientSession(ClientSession proto)
	{
		super(proto.lstnr);
		tmt_idle = proto.tmt_idle;
		loadbalancer = null;
		bufspec = null;
		relay = new Relay(this, proto.loadbalancer, proto.bufspec);
		chanreader = new com.grey.naf.reactor.IOExecReader(proto.bufspec);
		chanwriter = new com.grey.naf.reactor.IOExecWriter(proto.bufspec);
	}

	// this is only invoked in the prototype instance
	@Override
	public com.grey.base.utils.PrototypeFactory.PrototypeObject prototype_create()
	{
		return new ClientSession(this);
	}

	public void initiateIO() throws com.grey.base.FaultException, java.io.IOException
	{
		if (tmt_idle != 0) tmr = dsptch.setTimer(tmt_idle, 0, this);
		chanreader.receive(0);
	}

	@Override
	protected void connected() throws com.grey.base.FaultException, java.io.IOException
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
	public void ioReceived(com.grey.base.utils.ArrayRef<byte[]> data) throws java.io.IOException
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

	public void transmit(com.grey.base.utils.ArrayRef<byte[]> data) throws java.io.IOException
	{
		chanwriter.transmit(data.ar_buf, data.ar_off, data.ar_len);
	}

	// The Timer param must be our 'tmr' field, as we only created one Timer
	@Override
	public void timerIndication(com.grey.naf.reactor.Timer t, com.grey.naf.reactor.Dispatcher d)
	{
		tmr = null; //mark timer as expired
		if (dsptch.logger.isActive(LEVEL.TRC)) dsptch.logger.trace("Closing idle connection: "+iochan+" => "+relay.server.getServerAddress().sockaddr);
		ioDisconnected("Timeout");
	}

	// already logged by Dispatcher, no need to do anything else
	@Override
	public void eventError(com.grey.naf.reactor.Timer tmr, com.grey.naf.reactor.Dispatcher d, Throwable ex) {}
}
