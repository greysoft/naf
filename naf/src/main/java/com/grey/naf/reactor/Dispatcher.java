/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.logging.Logger.LEVEL;

public final class Dispatcher
	implements Runnable, com.grey.naf.EntityReaper, Producer.Consumer<Object>
{
	private static final long shutdown_timer_advance = SysProps.getTime("greynaf.dispatchers.shutdown_advance", "1s");
	private static final String SYSPROP_HEAPWAIT = "greynaf.dispatchers.heapwait";
	private static final String STOPCMD = "_STOP_";

	private static final java.util.ArrayList<Dispatcher> activedispatchers = new java.util.ArrayList<Dispatcher>();
	private static final java.util.concurrent.atomic.AtomicInteger anoncnt = new java.util.concurrent.atomic.AtomicInteger();

	public final long timeboot = System.currentTimeMillis();
	public final String name;
	public final com.grey.naf.Config nafcfg;
	public final com.grey.naf.nafman.Agent nafman;
	public final com.grey.naf.dns.Resolver dnsresolv;
	public final Flusher flusher;
	public final com.grey.logging.Logger logger;
	private long systime_msecs;

	private final Thread thrd_main;
	private final java.util.ArrayList<com.grey.naf.Naflet> naflets = new java.util.ArrayList<com.grey.naf.Naflet>();
	private final java.nio.channels.Selector slct;
	private final com.grey.base.utils.HashedMapIntKey<ChannelMonitor> activechannels;
	private final com.grey.base.utils.Circulist<Timer> activetimers;
	private final com.grey.base.utils.ObjectQueue<Timer> pendingtimers;  //timers which have expired and are ready to fire
	private final com.grey.base.utils.ObjectWell<Timer> sparetimers;
	private final Producer<Object> apploader;
	final com.grey.base.utils.ObjectWell<IOExecWriter.FileWrite> filewritepool;

	public final boolean surviveDownstream;  //survive the exit/death of downstream Dispatchers
	private final boolean surviveHandlers; //survive error in event handlers
	private final boolean zeroNafletsOK;  //true means ok if no Naflets running, else exit when count falls to zero

	private int uniqid_timer;
	private int uniqid_chan;
	private boolean shutdownRequested;
	private boolean shutdownPerformed;

	// temp working buffers, preallocated (on demand) for efficiency
	final java.util.Calendar dtcal = TimeOps.getCalendar(null); //package-private
	private final StringBuilder tmpsb = new StringBuilder();
	private java.nio.ByteBuffer tmpniobuf;
	private byte[] xferbuf;

	// assume that a Dispatcher which hasn't yet been started is being called by the thread setting it up
	public boolean inThread() {return Thread.currentThread() == thrd_main || thrd_main.getState() == Thread.State.NEW;}
	public boolean isRunning() {return thrd_main.isAlive();}

	int allocateChannelId() {return ++uniqid_chan;}

	private Dispatcher(com.grey.naf.Config ncfg, com.grey.naf.DispatcherDef def, com.grey.logging.Logger initlog)
			throws com.grey.base.GreyException, java.io.IOException
	{
		name = def.name;
		zeroNafletsOK = def.zeroNafletsOK;
		surviveDownstream = def.surviveDownstream;
		surviveHandlers = def.surviveHandlers;
		nafcfg = ncfg;

		systime_msecs = System.currentTimeMillis();
		thrd_main = new Thread(this);
		FileOps.ensureDirExists(nafcfg.path_var);
		FileOps.ensureDirExists(nafcfg.path_tmp);

		activechannels = new com.grey.base.utils.HashedMapIntKey<ChannelMonitor>();
		activetimers = new com.grey.base.utils.Circulist<Timer>(Timer.class);
		pendingtimers = new com.grey.base.utils.ObjectQueue<Timer>(Timer.class);
		sparetimers = new com.grey.base.utils.ObjectWell<Timer>(Timer.class, "Dispatcher-"+name);
		filewritepool = new com.grey.base.utils.ObjectWell<IOExecWriter.FileWrite>(new IOExecWriter.FileWrite.Factory(), "Dispatcher-"+name);
		flusher = new Flusher(this, def.flush_interval);
		slct = java.nio.channels.Selector.open();

		com.grey.logging.Logger dlog = com.grey.logging.Factory.getLogger(def.logname);
		initlog.info("Initialising Dispatcher="+name+" - Logger="+def.logname+" - "+dlog);
		logger = (dlog == null ? initlog : dlog);
		if (logger != initlog) flusher.register(logger);
		logger.info("Dispatcher="+name+": survive_downstream="+surviveDownstream+", survive_handlers="+surviveHandlers
				+", zero_naflets="+zeroNafletsOK+", flush="+TimeOps.expandMilliTime(def.flush_interval));
		logger.trace("Dispatcher="+name+": Selector="+slct.getClass().getCanonicalName()
				+", Provider="+slct.provider().getClass().getCanonicalName()
				+" - half-duplex="+ChannelMonitor.halfduplex+", jitter="+Timer.JITTER_THRESHOLD
				+", wbufs="+IOExecWriter.MAXBUFSIZ+"/"+IOExecWriter.FILEBUFSIZ);

		//this has to be done after creating slct, and might as well wait till logger exists as well
		apploader = new Producer<Object>(Object.class, this, this);

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
						new Class<?>[]{String.class, getClass(), appcfg.getClass()},
						new Object[]{null, this, appcfg});
				com.grey.naf.Naflet app = com.grey.naf.Naflet.class.cast(obj);
				if (app.naflet_name.charAt(0) == '_') {
					throw new com.grey.base.ConfigException("Invalid Naflet name (starts with underscore) - "+app.naflet_name);
				}
				addNaflet(app);
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
		logger.info("Dispatcher="+name+" terminated - Naflets="+naflets.size()+", Channels="+activechannels.size()
				+", Timers="+activetimers.size()+":"+pendingtimers.size()
				+" (well="+sparetimers.size()+"/"+sparetimers.population()+")");
		if (naflets.size() != 0) logger.trace("Naflets: "+naflets);
		if (activechannels.size() != 0) logger.trace("Channels: "+activechannels);
		if (activetimers.size()+pendingtimers.size() != 0) logger.trace("Timers: Active="+activetimers+" - Pending="+pendingtimers);
		try {logger.flush(); } catch (Exception ex) {}

		if (SysProps.get(SYSPROP_HEAPWAIT, false)) {
			System.out.println("Dispatcher="+name+" has now terminated, and is suspended to offer a heap dump");
			for (;;) Timer.sleep(5000);
		}
	}

	// This method can be called by other threads
	public boolean stop()
	{
		if (inThread()) return stopSynchronously();

		try {
			apploader.produce(STOPCMD);
		} catch (java.io.IOException ex) {
			//probably a harmless error caused by Dispatcher already being shut down
			logger.trace("Failed to send cmd="+STOPCMD+" to Dispatcher="+name+"/Running="+isRunning()+" - "+ex);
		}
		return false;
	}

	// This must only be called within the Dispatcher thread
	private boolean stopSynchronously()
	{
		if (shutdownPerformed) return true;
		logger.info("Dispatcher="+name+": Received Stop request - naflets="+naflets.size()+", shutdownreq="+shutdownRequested);

		if (!shutdownRequested) {
			shutdownRequested = true; //must set this before notifying the naflets
			com.grey.naf.Naflet[] arr = listNaflets();
			for (int idx = 0; idx != arr.length; idx++) {
				stopNaflet(arr[idx]);
			}
			logger.trace("Dispatcher="+name+": Issued Stop commands - naflets="+naflets.size());

		}

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
			if (slct.isOpen()) slct.close();
		} catch (Throwable ex) {
			logger.log(LEVEL.INFO, ex, false, "Dispatcher="+name+": Failed to close NIO Selector");
		}
		if (dnsresolv != null)  dnsresolv.stop();
		if (nafman != null) nafman.stop();
		apploader.shutdown();

		logger.info("Dispatcher="+name+" exiting - endOfLife="+endOfLife+"/inThread="+inThread()
				+"/Alive="+thrd_main.isAlive()+"/"+thrd_main.getState());
		flusher.shutdown();

		synchronized (activedispatchers) {
			activedispatchers.remove(this);
		}
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

	public long systime()
	{
		if (systime_msecs == 0) systime_msecs = System.currentTimeMillis();
		return systime_msecs;
	}

	// This is the Dispatcher's main loop.
	// It will execute in here for the entirety of its lifetime, until all the events it is monitoring cease to be.
	private void activate() throws java.io.IOException
	{
		logger.info("Dispatcher="+name+": Entering Reactor event loop with Naflets="+naflets.size()
				+", Channels="+activechannels.size()+", Timers="+activetimers.size()+", shutdown="+shutdownRequested);

		while (!shutdownRequested && (activechannels.size() + activetimers.size() != 0))
		{
			if (!zeroNafletsOK && naflets.size() == 0) break;
			long iotmt = (activetimers.size() == 0 ? 0 : activetimers.get(0).expiry - systime());
			if (iotmt <= 0) {
				// Next timer already due, so set infinitesmal timeout.
				// We need to call the NIO Selector anyway, to pick up pending I/O events as well, else a continuous
				// stream of zero-second timers could starve the I/O handlers.
				iotmt = 1L;
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
			//do a final Select to flush the SelectionKeys, as they're always 1 interval in arrears
			finalkeys = slct.selectNow();
		}
		logger.info("Dispatcher="+name+": Reactor loop terminated - Naflets="+naflets.size()
				+", Channels="+activechannels.size()+"/"+finalkeys
				+", Timers="+activetimers.size()+" (pending="+pendingtimers.size()+")");
	}

	private void fireTimers()
	{
		// Extract all expired timers before firing any of them, to make sure any further timers they
		// install don't get fired in this loop, else continuous zero-second timers could prevent us ever
		// completing the loop.
		while (activetimers.size() != 0) {
			// Fire within milliseconds of maturity, as jitter in the system clock means the NIO
			// Selector can trigger a fraction early.
			Timer tmr = activetimers.get(0);
			if (tmr.expiry - systime() >= Timer.JITTER_THRESHOLD) break; //no expired timers left
			activetimers.remove(0);
			pendingtimers.add(tmr);
		}
		Timer tmr;

		while ((tmr = pendingtimers.remove()) != null) {
			try {
				tmr.fire(this);
			} catch (Throwable ex) {
				eventHandlerFailed(null, tmr, ex);
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
				eventHandlerFailed(cm, null, ex);
			}
		}
		keys.clear(); //this clears the NIO Ready set - NIO would hang otherwise
	}

	//BrokenPipe is handled differently, but beware of situations where it was thrown by a ChannelMonitor
	//other than the one whose callback has just failed. This error handler can only deal with the Timer
	//or ChannelMonitor in whose context it's being called, and it's up to the latter to handle broken
	//pipes in any other associated connections.
	private void eventHandlerFailed(ChannelMonitor cm, Timer tmr, Throwable ex)
	{
		final boolean bpex = (ex instanceof ChannelMonitor.BrokenPipeException); //BrokenPipe already logged
		final ChannelMonitor cmerr = (cm == null ?
				(tmr.handler instanceof ChannelMonitor ? (ChannelMonitor)tmr.handler : null)
				: cm);
		try {
			if (bpex && ((ChannelMonitor.BrokenPipeException)ex).cm == cmerr && cmerr != null) {
				//cmerr ought to be non-null if the prior conditions are met, but make double sure
				cmerr.failed(true, ex);
			} else {
				if (!bpex) {
					tmpsb.setLength(0);
					tmpsb.append("Dispatcher=").append(name).append(": Error on ");
					tmpsb.append(cm == null ? "Timer" : "IO");
					tmpsb.append(" handler=").append(cm == null ? tmr.handler : cm);
					if (cmerr != null) {
						tmpsb.append(" - cmstate: ");
						cmerr.dumpState(tmpsb, true);
					}
					logger.log(LEVEL.ERR, ex, true, tmpsb);
				}
				if (cm == null) {
					tmr.handler.eventError(tmr, this, ex);
				} else {
					cm.failed(false, ex);
				}
			}
		} catch (Throwable ex2) {
			logger.log(LEVEL.ERR, ex2, true, "Dispatcher="+name+": Error Handler failed - "+(cm==null?tmr.handler:cm)
					+" - "+com.grey.base.GreyException.summary(ex));
		}
		if (!surviveHandlers) {
			logger.warn("Initiating Abort due to error in "+(cm==null?"Timer":"I/O")+" Handler");
			stopSynchronously();
		}
	}

	// ChannelMonitors must bookend all their activity between a single call to this method and another one
	// to deregisterlIO().
	// In between, they can can call monitorIO() multiple times to stop and start listening for specific I/O events.
	void registerIO(ChannelMonitor cm) throws java.io.IOException
	{
		if (activechannels.put(cm.cm_id, cm) != null) {
			throw new java.io.IOException("Illegal registerIO on CM="+cm.getClass().getName()
					+" - Ops="+(cm.regkey==null? "None" : "0x"+Integer.toHexString(cm.regkey.interestOps())));
		}
	}

	void deregisterIO(ChannelMonitor cm) throws java.io.IOException
	{
		if (activechannels.remove(cm.cm_id) == null) {
			throw new java.io.IOException("Illegal deregisterIO on CM="+cm.getClass().getName()
					+" - Ops="+(cm.regkey==null? "None" : "0x"+Integer.toHexString(cm.regkey.interestOps())));
		}
		if (cm.regkey != null) {
			cm.regkey.cancel();
			cm.regkey = null;
		}
	}

	void conditionalDeregisterIO(ChannelMonitor cm) throws java.io.IOException
	{
		if (activechannels.containsKey(cm.cm_id)) deregisterIO(cm);
	}

	void monitorIO(ChannelMonitor cm, int ops) throws java.nio.channels.ClosedChannelException
	{
		if (shutdownPerformed) return;
		if (cm.regkey == null) { //equivalent to !cm.iochan.isRegistered(), but obviously cheaper
			//3rd arg has same effect as calling attach(handler) on returned SelectionKey
			cm.regkey = cm.iochan.register(slct, ops, cm);
		} else {
			cm.regkey.interestOps(ops);
		}
	}

	public Timer setTimer(long interval, int type, Timer.Handler handler)
	{
		Timer tmr = sparetimers.extract().init(this, handler, interval, type, ++uniqid_timer);
		if (shutdownRequested && interval < shutdown_timer_advance) {
			//This timer will never get fired, so if it was intended as a zero-second (or similiar)
			//action, do it now.
			//Some apps set a short delay on their ChannelMonitor.disconnect() call to avoid
			//reentrancy issues, so without this they would never disconnect. That's obviously
			//a worse outcome than risking any reentrancy issues during the final shutdown.
			try {
				tmr.fire(this);
			} catch (Throwable ex) {
				logger.log(LEVEL.ERR, ex, true, "Dispatcher="+name+": Shutdown error on Timer handler="+tmr.handler);
			}
			sparetimers.store(tmr.clear());
			return null; //callers need to handle null return as meaning timer already executed
		}
		activateTimer(tmr);
		return tmr;
	}

	void cancelTimer(Timer tmr)
	{
		//remove from scheduled queue
		if (!activetimers.remove(tmr)) {
			//remove from ready-to-fire queue
			if (!pendingtimers.withdraw(tmr)) {
				//unknown timer - it is safe to repeat a cancel-timer op, but this could be a bug - worth logging
				logger.info("Cancel on unknown Timer="+tmr+" - "+activetimers+" - pend="+pendingtimers);
				return;
			}
		}
		sparetimers.store(tmr.clear());
	}

	long resetTimer(Timer tmr)
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
			// The timer is already scheduled, so check if this reset (which has pushed its expiry time back)
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
		if (tmr.interval != 0) {
			//zero-sec timers go straight to front of queue, even ahead of other zero-sec ones
			for (int idx = activetimers.size() - 1; idx != -1; idx--) {
				if (tmr.expiry >= activetimers.get(idx).expiry) {
					// insert tmr AFTER this node
					pos = idx + 1;
					break;
				}
			}
		}
		activetimers.insert(pos, tmr);
	}

	@Override
	public void producerIndication(Producer<Object> p) throws java.io.IOException
	{
		Object event;
		while ((event = apploader.consume()) != null) {
			if (event.getClass() == String.class) {
				String evtname = (String)event;
				if (evtname.equals(STOPCMD)) {
					// we're being asked to stop this entire dispatcher, not just a naflet
					stopSynchronously();
				} else {
					// the received item is a Naflet name, to be stopped
					com.grey.naf.Naflet app = getNaflet(evtname);
					if (app == null) {
						logger.info("Discarding stop request for Naflet="+evtname+" - unknown Naflet");
						continue;
					}
					logger.info("Unloading Naflet="+app.naflet_name+" via Producer");
					stopNaflet(app);
				}
			} else {
				com.grey.naf.Naflet app = (com.grey.naf.Naflet)event;
				if (shutdownRequested) {
					logger.info("Discarding dynamic NAFlet="+app.naflet_name+" as we're in shutdown mode");
					continue;
				}
				logger.info("Loading Naflet="+app.naflet_name+" via Producer");
				addNaflet(app);
				app.start(this);
			}
		}
	}

	// This method can be (and is meant to be) called by other threads.
	// The Naflet is expected to be merely constructed, and we will call its start() method from
	// within the Dispatcher thread once it's been loaded.
	public void loadNaflet(com.grey.naf.Naflet app) throws com.grey.base.ConfigException, java.io.IOException
	{
		if (app.naflet_name.charAt(0) == '_') {
			throw new com.grey.base.ConfigException("Invalid Naflet name (starts with underscore) - "+app.naflet_name);
		}
		apploader.produce(app);
	}

	public void unloadNaflet(String naflet_name) throws java.io.IOException
	{
		apploader.produce(naflet_name);
	}

	@Override
	public void entityStopped(Object entity)
	{
		com.grey.naf.Naflet app = com.grey.naf.Naflet.class.cast(entity);
		boolean exists = removeNaflet(app);
		if (!exists) return;  // duplicate notification - ignore
		logger.info("Dispatcher="+name+": Naflet="+app.naflet_name+" has terminated - remaining="+naflets.size());
	}

	private void stopNaflet(com.grey.naf.Naflet app)
	{
		app.stop();
	}

	private com.grey.naf.Naflet getNaflet(String naflet_name)
	{
		for (int idx = 0; idx != naflets.size(); idx++) {
			if (naflets.get(idx).naflet_name.equals(naflet_name)) return naflets.get(idx);
		}
		return null;
	}

	// NB: This is not a performance-critical method, expected to be rarely called
	// The markup is XML, and if some of it happens to look like XHTML, that's a happy coincidence ...
	public CharSequence dumpState(StringBuilder sb, boolean verbose)
	{
		if (sb == null) {
			sb = tmpsb;
			sb.setLength(0);
		}
		sb.append("<infonodes>");
		sb.append("<infonode name=\"Disposition\" dispatcher=\"").append(name).append("\">");
		sb.append("NAFMAN = ").append(nafman == null ? "No" : (nafman.isPrimary() ? "Primary" : "Secondary"));
		sb.append("<br/>DNS = ").append(dnsresolv == null ? "No" : dnsresolv);
		sb.append("<br/>Log-Level = ").append(logger.getLevel());
		if (shutdownRequested) sb.append("<br/>In Shutdown");
		sb.append("</infonode>");

		sb.append("<infonode name=\"NAFlets\" total=\"").append(naflets.size()).append("\">");
		for (int idx = 0; idx != naflets.size(); idx++) {
			sb.append("<item id=\"").append(naflets.get(idx).naflet_name).append("\">");
			sb.append(naflets.get(idx).getClass().getName()).append("</item>");
		}
		sb.append("</infonode>");

		// NB: 'total' attribute will be different to 'item' count, as the former is the actual number of
		// registered channels, while the latter is only the "interesting" ones.
		sb.append("<infonode name=\"IO Channels\" total=\"").append(activechannels.size()).append("\">");
		com.grey.base.utils.IteratorInt itcm = activechannels.keysIterator();
		while (itcm.hasNext()) {
			ChannelMonitor cm = activechannels.get(itcm.next());
			int prevlen1 = sb.length();
			sb.append("<item id=\"").append(cm.cm_id).append("\"");
			if (cm.canKill()) sb.append(" cankill=\"y\"").append(" time=\"").append(cm.getStartTime()).append("\"");
			sb.append('>');
			int prevlen2 = sb.length();
			try {
				cm.dumpState(sb, verbose);
			} catch (Throwable ex) {
				// have observed CancelledKeyException happening here during shutdown - not sure how
				sb.append(com.grey.base.GreyException.summary(ex));
			}
			if (sb.length() == prevlen2) {
				sb.setLength(prevlen1);
			} else {
				sb.append("</item>");
			}
		}
		sb.append("</infonode>");

		// As above, the 'total' attribute will be different to the 'item' count, as the latter depends on various options
		sb.append("<infonode name=\"Timers\" total=\"").append(activetimers.size()).append("\">");
		int cnt = (verbose ? activetimers.size() : 0);
		for (int idx = 0; idx != cnt; idx++) {
			Timer tmr = activetimers.get(idx);
			sb.append("<item>ID=").append(tmr.id).append(':').append(tmr.type).append(" - Expires ");
			TimeOps.makeTimeLogger(tmr.expiry, sb, true, true).append(" (");
			TimeOps.expandMilliTime(tmr.interval, sb, false).append(")<br/>Handler=");
			if (tmr.handler == null) {
				sb.append("null");
			} else {
				sb.append(tmr.handler.getClass().getName());
			}
			if (tmr.attachment != null) sb.append('/').append(tmr.attachment.getClass().getName());
			sb.append("</item>");
		}
		sb.append("</infonode>");
		sb.append("</infonodes>");
		return sb;
	}

	//Since ChannelMonitors are reused, a non-zero stimee arg protects against killing a previous incarnation
	public boolean killConnection(int id, long stime, String diag) throws java.io.IOException
	{
		ChannelMonitor cm = activechannels.get(id);
		if (cm == null) return false;
		if (stime != 0 && stime != cm.getStartTime()) return false;
		cm.ioDisconnected(diag);
		return true;
	}

	// This lets us take a snapshot of the Naflets to iterate over.
	// It's not safe to iterate over the original 'naflets' list as it can be modified (Senders can exit) during the loop.
	// This is only intended for startup and shutdown, so don't worry about the memory allocation.
	// This method is also used by other threads (eg. NAFMAN) to get a snapshot of the current NAFlets, and it is for this
	// reason that it is synchronised. This method needs to be synchronised against local actions that add/remove entries from
	// the Naflets list.
	public com.grey.naf.Naflet[] listNaflets()
	{
		synchronized (naflets) {
			return naflets.toArray(new com.grey.naf.Naflet[naflets.size()]);
		}
	}

	private void addNaflet(com.grey.naf.Naflet app)
	{
		synchronized (naflets) {
			naflets.add(app);
		}
	}

	private boolean removeNaflet(com.grey.naf.Naflet app)
	{
		synchronized (naflets) {
			return naflets.remove(app);
		}
	}

	// convenience method which leverages a single pre-allocated transfer buffer for this thread
	public int transfer(java.nio.ByteBuffer src, java.nio.ByteBuffer dst)
	{
		int nbytes = com.grey.base.utils.NIOBuffers.transfer(src, dst, xferbuf);
		if (nbytes < 0) {
			xferbuf = new byte[-nbytes];
			nbytes = com.grey.base.utils.NIOBuffers.transfer(src, dst, xferbuf);
		}
		return nbytes;
	}

	// This returns a temp buffer which must be used immediately, as the next call to
	// this method will probably return the same buffer.
	public java.nio.ByteBuffer allocBuffer(int cap)
	{
		if (tmpniobuf == null || tmpniobuf.capacity() < cap) {
			tmpniobuf = com.grey.base.utils.NIOBuffers.create(cap, false);
		}
		tmpniobuf.clear();
		return tmpniobuf;
	}


	public static Dispatcher getDispatcher(String dname)
	{
		synchronized (activedispatchers) {
			for (int idx = 0; idx != activedispatchers.size(); idx++) {
				if (activedispatchers.get(idx).name.equals(dname)) return activedispatchers.get(idx);
			}
		}
		return null;
	}

	public static Dispatcher[] getDispatchers()
	{
		synchronized (activedispatchers) {
			return activedispatchers.toArray(new Dispatcher[activedispatchers.size()]);
		}
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

		synchronized (activedispatchers) {
			if (getDispatcher(def.name) != null) {
				throw new com.grey.base.ConfigException("Duplicate Dispatcher="+def.name);
			}
			d =  new Dispatcher(nafcfg, def, log);
			activedispatchers.add(d);
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

		// log the initial config and dump the sytem-properties
		String txt = dumpConfig();
		log.info("Initialisation of the configured NAF context is now complete\n"+txt);
		txt = System.getProperties().size()+" entries:"+SysProps.EOL;
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

	public static String dumpConfig()
	{
		String txt = "";
		synchronized (activedispatchers) {
			txt += "Dispatchers="+activedispatchers.size()+":";
			for (int idx = 0; idx != activedispatchers.size(); idx++) {
				Dispatcher d = activedispatchers.get(idx);
				if (d == null) continue; //must have just exited
				txt += "\n- "+d.name+": NAFlets="+d.naflets.size();
				String dlm = " - ";
				for (int idx2 = 0; idx2 != d.naflets.size(); idx2++) {
					txt += dlm + d.naflets.get(idx2).naflet_name;
					dlm = ", ";
				}
			}
		}
		String[] lnames = Listener.getNames();
		txt += "\nListeners="+lnames.length;
		for (int idx = 0; idx != lnames.length; idx++) {
			Listener l = Listener.getByName(lnames[idx]);
			txt += "\n- "+lnames[idx]+": Port="+l.getLocalPort()+", Server="+l.getServerType().getName()+" (Dispatcher="+l.dsptch.name+")";
		}
		return txt;
	}
}