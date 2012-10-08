/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger.LEVEL;

public final class Dispatcher
	implements Runnable, com.grey.naf.EntityReaper, Producer.Consumer
{
	private static final String STOPCMD = "_STOP_";
	private static final String SYSPROP_LOGNAME = "greynaf.dispatchers.logname";
	private static final String SYSPROP_EXITDUMP = "greynaf.dispatchers.exitdump";
	private static final String SYSPROP_HEAPWAIT = "greynaf.dispatchers.heapwait";

	private static final java.util.concurrent.ConcurrentHashMap<String, Dispatcher> activedispatchers 
			= new java.util.concurrent.ConcurrentHashMap<String, Dispatcher>();
	private static final java.util.concurrent.atomic.AtomicInteger anoncnt = new java.util.concurrent.atomic.AtomicInteger();

	private final Thread thrd_main;
	private final java.util.ArrayList<com.grey.naf.Naflet> naflets = new java.util.ArrayList<com.grey.naf.Naflet>();
	private final java.nio.channels.Selector slct;
	private final com.grey.base.utils.Circulist<Timer> activetimers;
	private final com.grey.base.utils.HashedSet<ChannelMonitor> activechannels = new com.grey.base.utils.HashedSet<ChannelMonitor>();
	private final com.grey.base.utils.ObjectQueue<Timer> pendingtimers;  //timers which have expired and are ready to fire
	private final com.grey.base.utils.ObjectWell<Timer> sparetimers;
	private final Producer<Object> apploader;

	private int uniqtimerid;
	private boolean shutdownRequested;
	private boolean shutdownPerformed;

	public final String name;
	public final com.grey.naf.Config nafcfg;
	public final com.grey.naf.nafman.Agent nafman;
	public final com.grey.naf.dns.Resolver dnsresolv;
	public final Flusher flusher;
	public final com.grey.logging.Logger logger;
	private long systime_msecs;

	public final boolean surviveDownstream;  //survive the exit/death of downstream Dispatchers
	private final boolean surviveHandlers; //survive error in event handlers
	private final boolean zeroNaflets;  //true means ok if no Naflets running, else exit when count falls to zero

	public boolean isRunning() {return thrd_main.isAlive();}

	private Dispatcher(com.grey.naf.Config ncfg, com.grey.naf.DispatcherDef def, com.grey.logging.Logger initlog)
			throws com.grey.base.GreyException, java.io.IOException
	{
		name = def.name;
		zeroNaflets = def.zeroNaflets;
		surviveDownstream = def.surviveDownstream;
		surviveHandlers = def.surviveHandlers;
		nafcfg = ncfg;

		systime_msecs = System.currentTimeMillis();
		thrd_main = new Thread(this);
		FileOps.ensureDirExists(nafcfg.path_var);
		FileOps.ensureDirExists(nafcfg.path_tmp);

		slct = java.nio.channels.Selector.open();
		activetimers = new com.grey.base.utils.Circulist<Timer>(Timer.class);
		pendingtimers = new com.grey.base.utils.ObjectQueue<Timer>(Timer.class);
		sparetimers = new com.grey.base.utils.ObjectWell<Timer>(Timer.class, "Dispatcher_Timers_"+name);
		flusher = new Flusher(this, def.flush_interval);
		apploader = new Producer<Object>(Object.class, this, this);

		String logname = SysProps.get(SYSPROP_LOGNAME, name);
		com.grey.logging.Logger dlog = com.grey.logging.Factory.getLogger(name);
		initlog.info("Initialising Dispatcher="+name+" - Logger="+logname+" - "+dlog);
		logger = (dlog == null ? initlog : dlog);
		if (logger != initlog) flusher.register(logger);
		logger.info("Dispatcher="+name+": survive_downstream="+surviveDownstream+", survive_handlers="+surviveHandlers
				+", zero_naflets="+zeroNaflets+", flush="+TimeOps.expandMilliTime(def.flush_interval));
		logger.trace("Dispatcher="+name+": Selector="+slct.getClass().getCanonicalName()+" - Provider="+slct.provider().getClass().getCanonicalName());

		if (def.hasNafman) {
			// We are constructed within a synchronised block, so no race condition checking Primary
			if (com.grey.naf.nafman.Primary.get() == null) {
				nafman = new com.grey.naf.nafman.Primary(this, nafcfg.getNafman());
			} else {
				nafman = new com.grey.naf.nafman.Secondary(this, nafcfg.getNafman());
			}
			logger.info("Dispatcher="+name+": Initialised NAFMAN - "+(nafman.isPrimary() ? "Primary" : "Secondary"));
		} else {
			nafman = null;
		}

		if (def.hasDNS) {
			dnsresolv = com.grey.naf.dns.Resolver.create(this, nafcfg.getDNS());
			logger.info("Dispatcher="+name+": Initialised DNS Resolver="+dnsresolv.getClass().getName());
		} else {
			dnsresolv = null;
		}

		if (def.naflets != null) {
			logger.info("Dispatcher="+name+": Creating Naflets="+def.naflets.length);
			for (int idx = 0; idx != def.naflets.length; idx++) {
				XmlConfig appcfg = def.naflets[idx];
				Object obj = nafcfg.createEntity(appcfg, null, com.grey.naf.Naflet.class, true,
						new Class<?>[]{String.class, com.grey.naf.reactor.Dispatcher.class, com.grey.base.config.XmlConfig.class},
						new Object[]{null, this, appcfg});
				com.grey.naf.Naflet app = com.grey.naf.Naflet.class.cast(obj);
				if (app.naflet_name.charAt(0) == '_') {
					throw new com.grey.base.ConfigException("Invalid Naflet name (starts with underscore) - "+app.naflet_name);
				}
				naflets.add(app);
			}
		}
		logger.info("Dispatcher="+name+": Init complete - Naflets="+naflets.size());
	}

	public Thread start() throws java.io.IOException
	{
		thrd_main.start();
		return thrd_main;
	}

	@Override
	public void run()
	{
		logger.info("Dispatcher="+name
				+": Started thread="+Thread.currentThread().getName()+":T"+Thread.currentThread().getId());
		systime_msecs = System.currentTimeMillis();

		try {
			if (nafman != null) nafman.start();
			if (dnsresolv != null) dnsresolv.start();

			com.grey.naf.Naflet[] arr = listNaflets();
			for (int idx = 0; idx != arr.length; idx++) {
				arr[idx].start(this);
			}

			// Enter main loop
			activate();	
		} catch (Throwable ex) {
			String msg = "Dispatcher="+name+" has terminated abnormally";
			logger.log(LEVEL.ERR, ex, true, msg);
		}
		shutdown(true);
		logger.info("Dispatcher="+name+" has terminated");

		if (SysProps.get(SYSPROP_HEAPWAIT, false)) {
			System.out.println("Dispatcher="+name+" has now terminated, and is suspended to provide a heap dump");
			for (;;) try {Thread.sleep(5000);} catch (Exception ex) {}
		}
	}

	// This method can be (and is meant to be) called by other threads
	public boolean stop(Dispatcher d) throws java.io.IOException
	{
		if (d == this) {
			// handle this request synchronously
			return stop();
		}
		apploader.produce(STOPCMD, d);
		return false;
	}

	private boolean stop()
	{
		if (shutdownPerformed) {
			return true;
		}
		logger.info("Dispatcher="+name+": Received Stop request - naflets="+naflets.size());
		shutdownRequested = true;

		com.grey.naf.Naflet[] arr = listNaflets();
		for (int idx = 0; idx != arr.length; idx++) {
			stopNaflet(arr[idx]);
		}
		logger.trace("Dispatcher="+name+": Issued Stop commands - naflets="+naflets.size());

		if (thrd_main.isAlive()) {
			// Dispatcher event loop is still active, and shutdown() will get called when it terminates
			return false;
		}
		shutdown(false);
		return true;
	}

	private void shutdown(boolean endOfLife)
	{
		if (shutdownPerformed) return;
		try {
			slct.close();
		} catch (Throwable ex) {
			logger.log(LEVEL.INFO, ex, false, "Dispatcher="+name+": Failed to close NIO Selector");
		}
		if (dnsresolv != null)  dnsresolv.stop();
		if (nafman != null) nafman.stop();
		apploader.shutdown(false);

		logger.info("Dispatcher="+name+" exiting - endOfLife="+endOfLife+", DispatcherThread="+(Thread.currentThread() == thrd_main));
		flusher.shutdown();

		activedispatchers.remove(name);
		shutdownPerformed = true;
	}

	public void waitStopped()
	{
		boolean joined = false;
		do {
			try {
				thrd_main.join();
				joined = true;
			} catch (InterruptedException ex) {}
		} while (!joined);
	}

	public long systime() {
		if (systime_msecs == 0) systime_msecs = System.currentTimeMillis();
		return systime_msecs;
	}

	// This is the Dispatcher's main loop.
	// It will execute in here for the entirety of its lifetime, until all the events it is monitoring cease to be.
	private void activate() throws java.io.IOException
	{
		logger.info("Dispatcher="+name+": Entering Reactor event loop with Naflets="+naflets.size()
				+", I/O="+activechannels.size()+", Timers=" + activetimers.size()+", shutdown="+shutdownRequested);

		while (!shutdownRequested && (activechannels.size() + activetimers.size() != 0))
		{
			if (!zeroNaflets && naflets.size() == 0) break;
			long iotmt = 0;

			if (activetimers.size() != 0) {
				if ((iotmt = activetimers.get(0).expiry - systime()) <= 0) {
					// Next timer already due, so set infinitesmal timeout.
					// We need to call the NIO Selector anyway, to pick up pending I/O events as well, else a continuous
					// stream of zero-second timers could starve the I/O handlers.
					iotmt = 1L;
				}
			}
			systime_msecs = 0;
			int keycnt = slct.select(iotmt);
			if (keycnt == 0) {
				// must have been a timer
				fireTimers();
			} else {
				// We definitely have I/O and only check for timers if we already knew we had some before calling select().
				// This guards against timer handlers being starved by continuous I/O.
				// Also invoke the timers first, to ensure zero-second timers are handled before anything else.
				if (iotmt == 1L) fireTimers();
				fireIO();
			}
		}

		int finalkeys = -1;
		if (!shutdownPerformed) {
			//do 1-millisecond poll simply to flush the SelectionKeys, as they're always 1 interval in arrears
			slct.select(1);
			finalkeys = slct.keys().size();
		}
		logger.info("Dispatcher="+name+": Activity ceased - Naflets="+naflets.size()
				+", IO="+activechannels.size()+"/"+finalkeys
				+", Timers="+activetimers.size()+" (spare="+sparetimers.size()+")");
		if (SysProps.get(SYSPROP_EXITDUMP, false)) logger.info("Dumping final state - " +dumpState(null));
		while (activetimers.size() != 0) activetimers.get(0).cancel();
	}

	private void fireTimers()
	{
		// Extract all expired timers before firing any of them, to make sure any further timers they
		// install don't get fired in this run, else ontinuous zero-second timers could prevent us ever
		// completing this loop.
		while (activetimers.size() != 0) {
			// Fire within milliseconds of maturity, as jitter in the system clock means the NIO
			// Selector can trigger a fraction early.
			Timer tmr = activetimers.get(0);
			if (tmr.expiry - systime() >= Timer.JITTER_INTERVAL) break;
			activetimers.remove(0);
			pendingtimers.add(tmr);
		}
		Timer tmr;

		while ((tmr = pendingtimers.remove()) != null) {
			try {
				tmr.fire(this);
			} catch (Throwable ex) {
				logger.log(LEVEL.ERR, ex, true, "Dispatcher="+name+": handler failed on Timer event - "+tmr.handler);
				try {
					tmr.handler.eventError(tmr, this, ex);
				} catch (Throwable ex2) {
					logger.log(LEVEL.ERR, ex2, true, "Dispatcher="+name+": Timer-handler failed to handle error - "
							+tmr.handler+" - "+com.grey.base.GreyException.summary(ex));
				}
				if (!surviveHandlers) {
					logger.warn("Initiating Abort due to error in Timer Handler");
					stop();
				}
			}
			sparetimers.store(tmr.clear());
		}
	}

	private void fireIO()
	{
		java.util.Set<java.nio.channels.SelectionKey> keys = slct.selectedKeys();
		java.util.Iterator<java.nio.channels.SelectionKey> itkey = keys.iterator();

		while (itkey.hasNext()) {
			// By testing if SelectionKey is still valid, we guard against delivering events to a monitor that was disabled by an earlier
			// event in the current callout cycle.
			java.nio.channels.SelectionKey key = itkey.next();
			if (!key.isValid()) continue;
			ChannelMonitor cm = (ChannelMonitor)key.attachment();

			try {
				cm.handleIO(key.readyOps());
			} catch (Throwable ex) {
				logger.log(LEVEL.ERR, ex, true, "Dispatcher="+name+": handler failed on IO event - "+cm);
				try {
					cm.eventError(cm, ex);
				} catch (Throwable ex2) {
					logger.log(LEVEL.ERR, ex2, true, "Dispatcher="+name+": Channel-Monitor failed to handle error - "
							+cm+" - "+com.grey.base.GreyException.summary(ex));
				}
				if (!surviveHandlers) {
					logger.warn("Initiating Abort due to error in IO Handler");
					stop();
				}
			}
		}
		keys.clear(); //this clears the NIO Ready set - NIO would hang otherwise
	}

	// ChannelMonitors must bookend all their activity on each channel between this call and deregisterlIO().
	// In between, they can can call monitorIO() multiple times to commence listening for specific I/O events.
	protected void registerIO(ChannelMonitor cm) throws java.io.IOException
	{
		if (!activechannels.add(cm)) {
			throw new java.io.IOException("Illegal registerIO on CM="+cm.getClass().getName()
					+" - Ops="+(cm.regkey==null? "None" : "0x"+Integer.toHexString(cm.regkey.interestOps())));
		}
	}

	protected void deregisterIO(ChannelMonitor cm) throws java.io.IOException
	{
		if (!activechannels.remove(cm)) {
			throw new java.io.IOException("Illegal deregisterIO on CM="+cm.getClass().getName()
					+" - Ops="+(cm.regkey==null? "None" : "0x"+Integer.toHexString(cm.regkey.interestOps())));
		}
		if (cm.regkey != null) cm.regkey.cancel();
		cm.regkey = null;
	}

	// If remote party disconnects before we enter here, the chan.register() call will throw, so callers have to be prepared to handle
	// exceptions without treating them as an error, but rather as a routine disconnect event
	protected void monitorIO(ChannelMonitor cm, int ops) throws java.nio.channels.ClosedChannelException
	{
		if (shutdownPerformed) return;
		cm.regkey = cm.iochan.register(slct, ops, cm);  // 3rd register() arg has same effect as calling attach(handler) on returned SelectionKey
	}

	public Timer setTimer(long interval, int type, Timer.Handler handler)
	{
		Timer tmr = sparetimers.extract().init(this, handler, interval, type, ++uniqtimerid);
		activateTimer(tmr);
		return tmr;
	}

	public void cancelTimer(Timer tmr)
	{
		if (deactivateTimer(tmr)) sparetimers.store(tmr.clear());
	}

	public long resetTimer(Timer tmr)
	{
		long prev_remaining = systime() - tmr.expiry;
		tmr.expiry = systime() + tmr.interval;
		int idx = activetimers.indexOf(tmr);

		if (idx == -1) {
			// Timer either no longer exists, or has been expired but not yet fired. If the latter, we
			// need to remove it from the about-to-fire expired list
			// Either way, it is not currently on active list, and so needs to be inserted into it.
			pendingtimers.withdraw(tmr);
		} else {
			// The timer is already scheduled, so check if this reset (which has pushed its expiry time baclk)
			// means it is no longer in the correct slot in the active queue.
			if (idx == activetimers.size() - 1 || tmr.expiry <= activetimers.get(idx+1).expiry) {
				return prev_remaining;  // queue position is unchanged, so we're already done
			}
			activetimers.remove(idx);  // remove from active list, before re-inserting in new position
		}
		activateTimer(tmr);
		return prev_remaining;
	}

	private void activateTimer(Timer tmr)
	{
		int pos = 0; // will insert new timer at head of list, if we don't find any earlier timers
		
		for (int idx = activetimers.size() - 1; idx != -1; idx--)
		{
			if (tmr.expiry >= activetimers.get(idx).expiry)
			{
				// insert tmr AFTER this node
				pos = idx + 1;
				break;
			}
		}
		activetimers.insert(pos, tmr);
	}

	// expired timers are placed on the pending-timers list before firing them, so this will prevent the timer from actually firing
	protected boolean deactivateTimer(Timer tmr)
	{
		if (activetimers.remove(tmr)) return true;
		return pendingtimers.withdraw(tmr);
	}

	@Override
	public void producerIndication(Producer<?> p) throws java.io.IOException
	{
		Object event;
		while ((event = apploader.consume()) != null) {
			if (event instanceof String) {
				String evtname = String.class.cast(event);
				if (evtname.equals(STOPCMD)) {
					// we're being asked to stop this entire dispatcher, not just a naflet
					stop();
				} else {
					com.grey.naf.Naflet app = getNaflet(evtname);
					if (app == null) {
						logger.info("Discarding stop request for Naflet="+evtname+"- unknown Naflet");
						continue;
					}
					logger.info("Unloading Naflet="+app.naflet_name+" via Producer");
					stopNaflet(app);
				}
			} else if (event instanceof com.grey.naf.Naflet) {
				com.grey.naf.Naflet app = com.grey.naf.Naflet.class.cast(event);
				if (shutdownRequested) {
					logger.info("Discarding dynamic NAFlet="+app.naflet_name+" as we're in shutdown mode");
					continue;
				}
				logger.info("Loading Naflet="+app.naflet_name+" via Producer");
				naflets.add(app);
				app.start(this);
			}
		}
	}

	// This method can be (and is meant to be) called by other threads.
	// The Naflet is expected to be merely constructed, and we will call its start() method from
	// within the Dispatcher thread once it's been loaded.
	public void loadNaflet(com.grey.naf.Naflet app, Dispatcher d) throws com.grey.base.ConfigException, java.io.IOException
	{
		if (app.naflet_name.charAt(0) == '_') {
			throw new com.grey.base.ConfigException("Invalid Naflet name (starts with underscore) - "+app.naflet_name);
		}
		apploader.produce(app, d);
	}

	public void unloadNaflet(String naflet_name, Dispatcher d) throws java.io.IOException
	{
		apploader.produce(naflet_name, d);
	}

	@Override
	public void entityStopped(Object entity)
	{
		com.grey.naf.Naflet app = com.grey.naf.Naflet.class.cast(entity);
		boolean exists = naflets.remove(app);
		if (!exists) return;  // duplicate notification - ignore
		logger.info("Dispatcher="+name+": Naflet="+app.naflet_name+" has terminated - remaining="+naflets.size());
	}

	private void stopNaflet(com.grey.naf.Naflet app)
	{
		if (app.stop()) {
			entityStopped(app);
		}
	}

	private com.grey.naf.Naflet getNaflet(String naflet_name)
	{
		for (int idx = 0; idx != naflets.size(); idx++) {
			if (naflets.get(idx).naflet_name.equals(naflet_name)) return naflets.get(idx);
		}
		return null;
	}

	// NB: This is not a performance-critical method, expected to be rarely called
	public CharSequence dumpState(StringBuilder sb)
	{
		if (sb == null) sb = new StringBuilder();
		sb.append("Dispatcher: ").append(name);
		sb.append("\nNAFMAN=").append(nafman == null ? "N" : (nafman.isPrimary() ? "Primary" : "Secondary"));
		sb.append("\nDNS=").append(dnsresolv == null ? "N" : "Y");

		sb.append("\nNaflets=").append(naflets.size());
		for (int idx = 0; idx != naflets.size(); idx++) {
			sb.append("\n- Naflet ").append(idx+1).append(": ").append(naflets.get(idx).naflet_name);
		}

		sb.append("\nI/O Channels=").append(activechannels.size());
		java.util.Iterator<ChannelMonitor> itcm = activechannels.iterator();
		while (itcm.hasNext()) {
			ChannelMonitor cm = itcm.next();
			sb.append("\n- Channel=");
			if (Listener.class.isInstance(cm)) {
				sb.append(cm.getClass().getSimpleName()).append('/').append(((Listener)cm).getServerType().getName());
			} else if (cm.getClass().equals(Producer.AlertsPipe.class)) {
				sb.append("Producer/").append(((Producer.AlertsPipe)cm).producer.consumerType);
			} else {
				sb.append(cm.getClass().getName());
			}
			sb.append(": ");
			try {
				cm.dumpState(sb);
			} catch (Throwable ex) {
				// have observed CancelledKeyException happening here during shutdown - not sure how
				sb.append(com.grey.base.GreyException.summary(ex));
			}
		}

		sb.append("\nTimers=").append(activetimers.size());
		for (int idx = 0; idx != activetimers.size(); idx++) {
			Timer tmr = activetimers.get(idx);
			sb.append("\n- Timer=").append(tmr.id).append('/').append(tmr.type);
			sb.append(": Expiry=");
			TimeOps.makeTimeLogger(tmr.expiry, sb, true, true);
			sb.append(" (");
			TimeOps.expandMilliTime(tmr.interval, sb, false);
			sb.append(") Handler=");
			if (tmr.handler == null) {
				sb.append("null");
			} else {
				sb.append(tmr.handler.getClass().getName());
			}
			if (tmr.attachment != null) sb.append('/').append(tmr.attachment.getClass().getName());
		}
		return sb;
	}

	// This lets us take a snapshot of the Naflets to iterate over.
	// It's not safe to iterate over the original 'naflets' list as it can be modified (Senders can exit) during the loop.
	// This is only intended for startup and shutdown, so don't worry about the memory allocation.
	private com.grey.naf.Naflet[] listNaflets()
	{
		return naflets.toArray(new com.grey.naf.Naflet[naflets.size()]);
	}


	public static Dispatcher getDispatcher(String dispatcher_name)
	{
		return activedispatchers.get(dispatcher_name);
	}

	public static Dispatcher[] getDispatchers()
	{
		return activedispatchers.values().toArray(new Dispatcher[activedispatchers.size()]);
	}

	public static Dispatcher create(com.grey.naf.DispatcherDef def, com.grey.naf.Config nafcfg, com.grey.logging.Logger log)
			throws com.grey.base.GreyException, java.io.IOException
	{
		return create(def, nafcfg, 0, log);
	}

	public static Dispatcher create(com.grey.naf.DispatcherDef def, int baseport, com.grey.logging.Logger log)
			throws com.grey.base.GreyException, java.io.IOException
	{
		return create(def, null, baseport, log);
	}

	private static Dispatcher create(com.grey.naf.DispatcherDef def, com.grey.naf.Config nafcfg, int baseport, com.grey.logging.Logger log)
			throws com.grey.base.GreyException, java.io.IOException
	{
		if (nafcfg == null) {
			nafcfg = com.grey.naf.Config.synthesise("<naf/>", baseport);
		}
		if (def.name == null || def.name.length() == 0) {
			def.name = "AnonDispatcher-"+anoncnt.incrementAndGet();
		}
		Dispatcher d;

		synchronized (Dispatcher.class) {
			if (activedispatchers.containsKey(def.name)) {
				throw new com.grey.base.ConfigException("Duplicate Dispatcher="+def.name);
			}
			d =  new Dispatcher(nafcfg, def, log);
			activedispatchers.put(def.name, d);
		}
		return d;
	}

	public static Dispatcher[] launchConfigured(com.grey.naf.Config nafcfg, com.grey.logging.Logger log)
		throws com.grey.base.GreyException, java.io.IOException
	{
		XmlConfig[] cfgdispatchers = nafcfg.getDispatchers();
		if (cfgdispatchers == null) return null;

		Dispatcher[] dlst = new Dispatcher[cfgdispatchers.length];
		log.info("NAF: Launching configured Dispatchers="+cfgdispatchers.length);

		// Do separate loops to create and start the Dispatchers, so that they're all guaranteed to be in single-threaded
		// mode while initialising.
		// First NAFMAN-enabled Dispatcher becomes the primary.
		for (int idx = 0; idx < cfgdispatchers.length; idx++) {
			com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef(cfgdispatchers[idx]);
			dlst[idx] = create(def, nafcfg, log);
		}
		com.grey.naf.nafman.Registry.get().confirmCandidates();

		// dump the sytem-properties
		String txt = System.getProperties().size()+" entries:"+SysProps.EOL;
		txt += System.getProperties().toString().replace(", ", SysProps.EOL+"\t");
		FileOps.writeTextFile(nafcfg.path_var+"/sysprops.dump", txt+SysProps.EOL);

		// Now starts the multi-threaded phase_bounces
		nafcfg.cfgroot = null; //hand memory back to the GC
		for (int idx = 0; idx < cfgdispatchers.length; idx++) {
			dlst[idx].start();
		}
		return dlst;
	}

	public static Dispatcher createConfigured(String name, com.grey.naf.Config nafcfg, com.grey.logging.Logger log)
		throws com.grey.base.GreyException, java.io.IOException
	{
		XmlConfig cfg = nafcfg.getDispatcher(name);
		if (cfg == null) return null;
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef(cfg);
		return create(def, nafcfg, log);
	}
}
