/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Clock;

import com.grey.base.config.SysProps;
import com.grey.base.config.XmlConfig;
import com.grey.base.collections.HashedMapIntKey;
import com.grey.base.collections.Circulist;
import com.grey.base.collections.ObjectQueue;
import com.grey.base.collections.ObjectWell;
import com.grey.base.collections.IteratorInt;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.NAFConfig;
import com.grey.naf.Naflet;
import com.grey.naf.dns.resolver.ResolverDNS;
import com.grey.naf.DispatcherDef;
import com.grey.naf.EntityReaper;
import com.grey.naf.nafman.NafManAgent;
import com.grey.naf.nafman.NafManRegistry;
import com.grey.naf.nafman.NafManServer;
import com.grey.naf.nafman.PrimaryAgent;
import com.grey.naf.nafman.SecondaryAgent;
import com.grey.naf.reactor.config.ConcurrentListenerConfig;
import com.grey.naf.errors.NAFConfigException;
import com.grey.logging.Logger;
import com.grey.logging.Logger.LEVEL;

public class Dispatcher
	implements Runnable, TimerNAF.TimeProvider, EntityReaper, Producer.Consumer<Object>
{
	public enum STOPSTATUS {STOPPED, ALIVE, FORCED};

	private static final boolean INTERRUPT_FRIENDLY = SysProps.get("greynaf.dispatchers.interrupts", false);
	private static final long TMT_FORCEDSTOP = SysProps.getTime("greynaf.dispatchers.forcestoptmt", "1s");
	private static final long shutdown_timer_advance = SysProps.getTime("greynaf.dispatchers.shutdown_advance", "1s");
	private static final boolean HEAPWAIT = SysProps.get("greynaf.dispatchers.heapwait", false);
	private static final String STOPCMD = "_STOP_";

	private static final AtomicInteger anonDispatcherCount = new AtomicInteger();

	private final String name;
	private final ApplicationContextNAF appctx;
	private final NafManAgent nafman;
	private final ResolverDNS dnsresolv;
	private final Flusher flusher;
	private final Logger logger;
	private final Clock clock;
	private final HashedMapIntKey<ChannelMonitor> activechannels = new HashedMapIntKey<>(); //keyed on cm_id
	private final Circulist<TimerNAF> activetimers = new Circulist<>(TimerNAF.class);
	private final ObjectQueue<TimerNAF> pendingtimers = new ObjectQueue<>(TimerNAF.class);  //timers which have expired and are ready to fire
	private final ArrayList<Naflet> dynamicNaflets = new ArrayList<>();
	private final ArrayList<Producer<?>> dynamicProducers = new ArrayList<>();
	private final ArrayList<EntityReaper> reapers = new ArrayList<>();
	private final Thread thrd_main;
	private final Thread thrd_init;
	private final java.nio.channels.Selector slct;
	private final ObjectWell<TimerNAF> sparetimers;
	private final Producer<Object> dynamicLoader;
	private final ObjectWell<IOExecWriter.FileWrite> filewritepool;
	private final boolean surviveHandlers; //survive error in event handlers
	private final boolean zeroNafletsOK;  //true means ok if no Naflets running, else exit when count falls to zero
	private final long timeboot;

	private int uniqid_timer;
	private int uniqid_chan;
	private boolean launched;
	private boolean shutdownRequested;
	private boolean shutdownPerformed;
	private long systime_msecs;

	// temp working buffers, preallocated (on demand) for efficiency
	private final java.util.Calendar dtcal = TimeOps.getCalendar(null);
	private final StringBuilder tmpsb = new StringBuilder();
	private java.nio.ByteBuffer tmpniobuf;
	private byte[] tmpmembuf;

	// assume that a Dispatcher which hasn't yet been started is being called by the thread setting it up
	public boolean inThread() {return Thread.currentThread() == thrd_main ||
									(Thread.currentThread() == thrd_init && thrd_main.getState() == Thread.State.NEW);}
	public boolean isRunning() {return thrd_main.isAlive();}
	public boolean isActive() {return isRunning() && !shutdownRequested;}
	public void waitStopped() {waitStopped(0, false);}

	public String getName() {return name;}
	public long getTimeBoot() {return timeboot;}
	public ApplicationContextNAF getApplicationContext() {return appctx;}
	public NafManAgent getAgent() {return nafman;}
	public ResolverDNS getResolverDNS() {return dnsresolv;}
	public Flusher getFlusher() {return flusher;}
	public Logger getLogger() {return logger;}

	IOExecWriter.FileWrite allocFileWrite() {return filewritepool.extract();}
	void releaseFileWrite(IOExecWriter.FileWrite fw) {filewritepool.store(fw);}
	int allocateChannelId() {return ++uniqid_chan;}
	java.util.Calendar getCalendar() {return dtcal;}

	//this is mainly for the benefit of test code - should be tested after Thread join
	private boolean thread_completed;
	private boolean error_abort;
	public boolean completedOK() {return thread_completed && !error_abort;}

	private Dispatcher(ApplicationContextNAF appctx, DispatcherDef def, Logger initlog) throws java.io.IOException
	{
		this.appctx = appctx;
		clock = def.getClock();
		timeboot = clock.millis();
		name = def.getName();
		String logname = def.getLogName();
		zeroNafletsOK = def.isZeroNafletsOK();
		surviveHandlers = def.isSurviveHandlers();
		NAFConfig nafcfg = appctx.getConfig();

		systime_msecs = timeboot;
		thrd_main = new Thread(this, "Dispatcher-"+name);
		thrd_init = Thread.currentThread();

		sparetimers = new ObjectWell<>(TimerNAF.class, "Dispatcher-"+name);
		filewritepool = new ObjectWell<>(new IOExecWriter.FileWrite.Factory(), "Dispatcher-"+name);
		flusher = new Flusher(this, def.getFlushInterval());
		slct = java.nio.channels.Selector.open();

		Logger dlog = null;
		Logger prevThreadLogger = null;
		if (logname != null || initlog == null) dlog = com.grey.logging.Factory.getLogger(logname);
		logger = (dlog == null ? initlog : dlog);
		if (logger != null) prevThreadLogger = Logger.setThreadLogger(logger);
		if (initlog != null) initlog.info("Initialising Dispatcher="+name+" - Logger="+logname+" - "+dlog);
		if (getLogger() != initlog) flusher.register(getLogger());
		getLogger().info("Dispatcher="+name+": baseport="+nafcfg.getBasePort()+", NAFMan="+def.hasNafman()+", DNS="+def.hasDNS()
				+", survive_handlers="+surviveHandlers+", zero_naflets="+zeroNafletsOK
				+", flush="+TimeOps.expandMilliTime(def.getFlushInterval()));
		getLogger().trace("Dispatcher="+name+": Selector="+slct.getClass().getCanonicalName()
				+", Provider="+slct.provider().getClass().getCanonicalName()
				+" - half-duplex="+ChannelMonitor.halfduplex+", jitter="+TimerNAF.JITTER_THRESHOLD
				+", wbufs="+IOExecWriter.MAXBUFSIZ+"/"+IOExecWriter.FILEBUFSIZ);

		//this has to be done after creating slct, and might as well wait till logger exists as well
		dynamicLoader = new Producer<>(Object.class, this, this);
		dynamicLoader.start();

		if (def.hasNafman()) {
			NafManRegistry reg = NafManRegistry.get(appctx);
			if (appctx.getPrimaryAgent() == null) {
				int lstnport = getApplicationContext().getConfig().assignPort(NAFConfig.RSVPORT_NAFMAN);
				XmlConfig lxmlcfg = nafcfg.getNafman().getSection("listener");
				ConcurrentListenerConfig lcfg = new ConcurrentListenerConfig.Builder<>()
						.withName("NAFMAN-Primary")
						.withPort(lstnport)
						.withServerFactory(NafManServer.Factory.class, null)
						.withXmlConfig(lxmlcfg, getApplicationContext())
						.build();
				nafman = new PrimaryAgent(this, reg, lcfg, def.isSurviveDownstream());
			} else {
				nafman = new SecondaryAgent(this, reg);
			}
			getLogger().info("Dispatcher="+name+": Initialised NAFMAN - "+(nafman.isPrimary() ? "Primary" : "Secondary"));
		} else {
			nafman = null;
		}

		if (def.hasDNS()) {
			dnsresolv = ResolverDNS.create(this, nafcfg.getDNS());
			getLogger().info("Dispatcher="+name+": Initialised DNS Resolver="+dnsresolv.getClass().getName());
		} else {
			dnsresolv = null;
		}

		XmlConfig[] nafletsConfig = def.getNafletsConfig();
		if (nafletsConfig != null) {
			getLogger().info("Dispatcher="+name+": Creating Naflets="+nafletsConfig.length);
			for (int idx = 0; idx != nafletsConfig.length; idx++) {
				XmlConfig appcfg = nafletsConfig[idx];
				Object obj = NAFConfig.createEntity(appcfg, null, Naflet.class, true,
						new Class<?>[]{String.class, getClass(), appcfg.getClass()},
						new Object[]{null, this, appcfg});
				Naflet app = Naflet.class.cast(obj);
				if (app.getName().charAt(0) == '_') {
					throw new NAFConfigException("Invalid Naflet name (starts with underscore) - "+app.getName());
				}
				addNaflet(app);
			}
		}
		getLogger().info("Dispatcher="+name+": Init complete - Naflets="+getNafletCount());
		if (logger != null) Logger.setThreadLogger(prevThreadLogger);
	}

	public Thread start()
	{
		getLogger().info("Dispatcher="+name+": Loaded JARs "+com.grey.base.utils.PkgInfo.getLoadedJARs());
		launched = true;
		thrd_main.start();
		Logger.setThreadLogger(getLogger(), thrd_main.getId());
		return thrd_main;
	}

	@Override
	public void run()
	{
		getLogger().info("Dispatcher="+name+": Started thread="+Thread.currentThread().getName()+":T"+Thread.currentThread().getId());
		systime_msecs = getRealTime();
		boolean ok = true;

		try {
			if (nafman != null) nafman.start();
			if (dnsresolv != null) dnsresolv.start();

			Naflet[] arr = listNaflets();
			for (int idx = 0; idx != arr.length; idx++) {
				arr[idx].start(this);
			}

			// Enter main loop
			activate();	
		} catch (Throwable ex) {
			String msg = "Dispatcher="+name+" has terminated abnormally";
			getLogger().log(LEVEL.ERR, ex, true, msg);
			ok = false;
		}
		shutdown(true);
		getLogger().info("Dispatcher="+name+" thread has terminated with abort="+error_abort+" - heapwait="+HEAPWAIT);
		try {getLogger().flush(); } catch (Exception ex) {getLogger().trace("Dispatcher="+name+": Final thread flush failed - "+ex);}
		thread_completed = ok;

		if (HEAPWAIT) {
			//this is purely to support interactive troubleshooting - hold process alive so debug tools can attach
			for (;;) TimerNAF.sleep(5000);
		}
	}

	// This method can be called by other threads
	public boolean stop()
	{
		if (inThread()) return stopSynchronously();
		try {
			dynamicLoader.produce(STOPCMD);
		} catch (java.io.IOException ex) {
			//probably a harmless error caused by Dispatcher already being shut down
			getLogger().trace("Failed to send cmd="+STOPCMD+" to Dispatcher="+name+", Thread="+threadInfo()+" - "+ex);
		}
		return false;
	}

	// This must only be called within the Dispatcher thread
	private boolean stopSynchronously()
	{
		if (shutdownPerformed) return true;
		getLogger().info("Dispatcher="+name+": Received Stop request - naflets="+getNafletCount()+", shutdownreq="+shutdownRequested
				+", Thread="+threadInfo());

		if (!shutdownRequested) {
			shutdownRequested = true; //must set this before notifying the naflets
			Naflet[] arr = listNaflets();
			for (int idx = 0; idx != arr.length; idx++) {
				stopNaflet(arr[idx]);
			}
			for (Producer<?> p : dynamicProducers) {
				p.shutdown();
			}
			getLogger().trace("Dispatcher="+name+": Issued Stop commands - naflets="+getNafletCount()+", producers="+dynamicProducers.size());
		}

		if (launched) {
			// Dispatcher event loop is still active, and shutdown() will get called when it terminates
			return false;
		}
		shutdown(false);
		return true;
	}

	private void shutdown(boolean endOfLife)
	{
		if (shutdownPerformed) return;
		getLogger().info("Dispatcher="+name+" in shutdown with endOfLife="+endOfLife+" - Thread="+threadInfo());
		try {
			if (slct.isOpen()) slct.close();
		} catch (Throwable ex) {
			getLogger().log(LEVEL.INFO, ex, false, "Dispatcher="+name+": Failed to close NIO Selector");
		}
		if (dnsresolv != null)  dnsresolv.stop();
		if (nafman != null) nafman.stop();
		dynamicLoader.shutdown();

		int reaper_cnt = 0;
		synchronized (reapers) {
			reaper_cnt = reapers.size();
			while (reapers.size() != 0) {
				EntityReaper r = reapers.remove(reapers.size()-1);
				r.entityStopped(this);
			}
		}

		getLogger().info("Dispatcher="+name+" shutdown completed - Naflets="+getNafletCount()+", Channels="+activechannels.size()
				+", Timers="+activetimers.size()+":"+pendingtimers.size()
				+", reapers="+reaper_cnt
				+" (well="+sparetimers.size()+"/"+sparetimers.population()+")");
		if (dynamicNaflets.size() != 0) getLogger().trace("Naflets: "+dynamicNaflets);
		if (activetimers.size()+pendingtimers.size() != 0) getLogger().trace("Timers: Active="+activetimers+" - Pending="+pendingtimers);
		if (activechannels.size() != 0) {
			getLogger().trace("Channels: "+activechannels);
			closeAllChannels();
			getLogger().info("Issued Disconnects - remaining channels="+(activechannels.size()==0?Integer.valueOf(0):activechannels));
		}
		try {getLogger().flush(); } catch (Exception ex) {getLogger().trace("Dispatcher="+name+": shutdown() flush failed - "+ex);}
		flusher.shutdown();
		appctx.deregister(this);
		shutdownPerformed = true;
	}

	public STOPSTATUS waitStopped(long timeout, boolean force)
	{
		if (timeout < 0) timeout = 1L;
		boolean done = false;
		do {
			try {
				thrd_main.join(timeout);
				done = true;
			} catch (InterruptedException ex) {}
		} while (!done);
		if (!thrd_main.isAlive()) return STOPSTATUS.STOPPED;
		if (!force) return STOPSTATUS.ALIVE;
		getLogger().warn("Dispatcher="+name+": Forced stop after timeout="+timeout+" - Interrupt="+INTERRUPT_FRIENDLY);
		if (INTERRUPT_FRIENDLY) thrd_main.interrupt(); //maximise the chances of waking up a blocked thread
		stop();
		if (waitStopped(TMT_FORCEDSTOP, false) == STOPSTATUS.STOPPED) return STOPSTATUS.FORCED;
		return STOPSTATUS.ALIVE; //failed to stop it - could only happen if blocked in an application callback
	}

	/**
	 * This should only be called within the Dispatcher thread.
	 */
	@Override
	public long getSystemTime() {
		if (systime_msecs == 0) systime_msecs = getRealTime();
		return systime_msecs;
	}
	
	/**
	 * This returns the instantaneous system time, and is thread-safe.
	 */
	public long getRealTime() {
		return clock.millis();
	}

	// This is the Dispatcher's main loop.
	// It will execute in here for the entirety of its lifetime, until all the events it is monitoring cease to be.
	private void activate() throws java.io.IOException
	{
		getLogger().info("Dispatcher="+name+": Entering Reactor event loop with Naflets="+getNafletCount()
				+", Channels="+activechannels.size()+", Timers="+activetimers.size()+", shutdown="+shutdownRequested);

		while (!shutdownRequested && (activechannels.size() + activetimers.size() != 0))
		{
			if (!zeroNafletsOK && getNafletCount() == 0) break;
			if (INTERRUPT_FRIENDLY) Thread.interrupted();//clear any pending interrupt status
			systime_msecs = 0;

			if (activetimers.size() == 0) {
				if (slct.select() != 0) fireIO();
			} else {
				long iotmt = activetimers.get(0).getExpiryTime() - getSystemTime();
				if (iotmt <= 0) {
					//next timer already due, but we still need to check for I/O as well
					if (slct.selectNow() != 0) fireIO();
					fireTimers();
				} else {
					if (slct.select(iotmt) == 0) {
						fireTimers();
					} else {
						fireIO();
					}
				}
			}
		}

		int finalkeys = -1;
		if (!shutdownPerformed) {
			//do a final Select to flush the SelectionKeys, as they're always one interval in arrears
			finalkeys = slct.selectNow();
		}
		getLogger().info("Dispatcher="+name+": Reactor loop terminated - Naflets="+getNafletCount()
				+", Channels="+activechannels.size()+"/"+finalkeys
				+", Timers="+activetimers.size()+" (pending="+pendingtimers.size()+")");
	}

	private void fireTimers()
	{
		// Extract all expired timers before firing any of them, to make sure any further timers they
		// install don't get fired in this loop, else continuous zero-second timers could prevent us ever
		// completing the loop.
		// It would also not be safe to take the obvious option of storing pending timers as an ArrayList
		// and looping over it, as pending timers can be withdrawn by the action of preceding ones, and
		// that would throw the loop iteration out.
		while (activetimers.size() != 0) {
			// Fire within milliseconds of maturity, as jitter in the system clock means the NIO
			// Selector can trigger a fraction early.
			TimerNAF tmr = activetimers.get(0);
			if (tmr.getExpiryTime() - getSystemTime() >= TimerNAF.JITTER_THRESHOLD) break; //no expired timers left
			activetimers.remove(0);
			pendingtimers.add(tmr);
		}
		TimerNAF tmr;

		while ((tmr = pendingtimers.remove()) != null) {
			try {
				tmr.fire(this);
			} catch (Throwable ex) {
				try {
					eventHandlerFailed(null, tmr, ex);
				} catch (Throwable ex2) {
					getLogger().log(LEVEL.ERR, ex2, true, "Error handler failed on timer - "+tmr);
				}
			}
			sparetimers.store(tmr.clear());
		}
	}

	private void fireIO()
	{
		Set<java.nio.channels.SelectionKey> keys = slct.selectedKeys();
		Iterator<java.nio.channels.SelectionKey> itkey = keys.iterator();

		while (itkey.hasNext()) {
			// By testing if SelectionKey is still valid, we guard against delivering events to a monitor that was disabled by an earlier
			// event in the current callout cycle.
			java.nio.channels.SelectionKey key = itkey.next();
			if (!key.isValid()) continue;
			ChannelMonitor cm = (ChannelMonitor)key.attachment();

			try {
				cm.handleIO(key.readyOps());
			} catch (Throwable ex) {
				try {
					eventHandlerFailed(cm, null, ex);
				} catch (Throwable ex2) {
					getLogger().log(LEVEL.ERR, ex2, true, "Error handler failed on I/O - "+cm);
				}
			}
		}
		keys.clear(); //this clears the NIO Ready set - NIO would hang otherwise
	}

	//BrokenPipe is handled differently, but beware of situations where it was thrown by a ChannelMonitor
	//other than the one whose callback has just failed. This error handler can only deal with the Timer
	//or ChannelMonitor in whose context it's being called, and it's up to the latter to handle broken
	//pipes in any other associated connections.
	private void eventHandlerFailed(ChannelMonitor cm, TimerNAF tmr, Throwable ex)
	{
		final boolean bpex = (ex instanceof CM_Stream.BrokenPipeException); //BrokenPipe already logged
		final ChannelMonitor cmerr = (cm == null ?
				(tmr.getHandler() instanceof ChannelMonitor ? (ChannelMonitor)tmr.getHandler() : null)
				: cm);
		try {
			if (bpex
					&& ((CM_Stream.BrokenPipeException)ex).cm == cmerr
					&& cmerr != null //should be non-null if prior conditions  hold, but make double sure
					&& !(cmerr instanceof CM_UDP)) {
				cmerr.failed(true, ex);
			} else {
				if (!bpex) {
					tmpsb.setLength(0);
					tmpsb.append("Dispatcher=").append(name).append(": Error on ");
					tmpsb.append(cm == null ? "Timer" : "I/O");
					tmpsb.append(" handler=").append(cm == null ? tmr : cm);
					if (cmerr != null) {
						tmpsb.append(" - cmstate: ");
						cmerr.dumpState(tmpsb, true);
						tmpsb.append('\n'); //state could be quite long, so make exception more visible on next line
					}
					getLogger().log(LEVEL.ERR, ex, true, tmpsb);
				}
				if (cm == null) {
					tmr.getHandler().eventError(tmr, this, ex);
				} else {
					cm.failed(false, ex);
				}
			}
		} catch (Throwable ex2) {
			getLogger().log(LEVEL.ERR, ex2, true, "Dispatcher="+name+": Error Handler failed - "+(cm==null?tmr.getHandler():cm)
					+" - "+com.grey.base.ExceptionUtils.summary(ex));
		}
		if (!surviveHandlers) {
			getLogger().warn("Initiating Abort due to error in "+(cm==null?"Timer":"I/O")+" Handler");
			error_abort = true;
			stopSynchronously();
		}
	}

	private void closeAllChannels() {
		List<ChannelMonitor> lst = activechannels.getValues();
		List<CM_Listener> lstnrs = new ArrayList<>();

		for (int idx = 0; idx != lst.size(); idx++) {
			ChannelMonitor cm = lst.get(idx);
			if (!activechannels.containsValue(cm)) continue; //must have been removed as side-effect of another close
			if (cm instanceof CM_Listener) {
				//needs to be stopped in a top-down manner, rather than bubbling up from socket closure
				lstnrs.add((CM_Listener)cm);
			} else {
				cm.disconnect(false);
			}
		}

		for (int idx = 0; idx != lstnrs.size(); idx++) {
			lstnrs.get(idx).stop(true);
		}
	}

	// ChannelMonitors must bookend all their activity between a single call to this method and another one
	// to deregisterlIO().
	// In between, they can can call monitorIO() multiple times to stop and start listening for specific I/O events.
	void registerIO(ChannelMonitor cm)
	{
		if (activechannels.put(cm.getCMID(), cm) != null) {
			throw new IllegalStateException("Illegal registerIO on CM="+cm.getClass().getName()+"/E"+cm.getCMID()
					+" - Ops="+showInterestOps(cm.getRegistrationKey()));
		}
	}

	void deregisterIO(ChannelMonitor cm)
	{
		if (activechannels.remove(cm.getCMID()) == null) {
			throw new IllegalStateException("Illegal deregisterIO on CM="+cm.getClass().getName()+"/E"+cm.getCMID()
					+" - Ops="+showInterestOps(cm.getRegistrationKey()));
		}
		if (cm.getRegistrationKey() != null) {
			cm.getRegistrationKey().cancel();
			cm.setRegistrationKey(null);
		}
	}

	void conditionalDeregisterIO(ChannelMonitor cm)
	{
		if (activechannels.containsKey(cm.getCMID())) deregisterIO(cm);
	}

	void monitorIO(ChannelMonitor cm, int ops) throws java.nio.channels.ClosedChannelException
	{
		if (shutdownPerformed) return;
		if (cm.getRegistrationKey() == null) { //equivalent to !cm.iochan.isRegistered(), but obviously cheaper
			//3rd arg has same effect as calling attach(handler) on returned SelectionKey
			cm.setRegistrationKey(cm.getChannel().register(slct, ops, cm));
		} else {
			cm.getRegistrationKey().interestOps(ops);
		}
	}
	
	public TimerNAF setTimer(long interval, int type, TimerNAF.Handler handler) {
		return setTimer(interval, type, handler, null);
	}

	public TimerNAF setTimer(long interval, int type, TimerNAF.Handler handler, Object attachment)
	{
		TimerNAF tmr = sparetimers.extract().init(this, handler, interval, type, ++uniqid_timer, attachment);
		if (shutdownRequested && interval < shutdown_timer_advance) {
			//This timer will never get fired, so if it was intended as a zero-second (or similiar)
			//action, do it now.
			//Some apps set a short delay on their ChannelMonitor.disconnect() call to avoid
			//reentrancy issues, so without this they would never disconnect. That's obviously
			//a worse outcome than risking any reentrancy issues during the final shutdown.
			try {
				tmr.fire(this);
			} catch (Throwable ex) {
				getLogger().log(LEVEL.ERR, ex, true, "Dispatcher="+name+": Shutdown error on Timer handler - "+tmr);
			}
			sparetimers.store(tmr.clear());
			return null; //callers need to handle null return as meaning timer already executed
		}
		activateTimer(tmr);
		return tmr;
	}

	void cancelTimer(TimerNAF tmr)
	{
		//remove from scheduled queue
		if (!activetimers.remove(tmr)) {
			//remove from ready-to-fire queue
			if (!pendingtimers.withdraw(tmr)) {
				//unknown timer - it is safe to repeat a cancel-timer op, but this could be a bug - worth logging
				getLogger().info("Cancel on unknown Timer="+tmr+" - "+activetimers+" - pend="+pendingtimers);
				return;
			}
		}
		//NB: This is safe against duplicate store() calls because it's illegal to cancel a timer after it's fired
		sparetimers.store(tmr.clear());
	}

	void resetTimer(TimerNAF tmr)
	{
		tmr.resetExpiry();
		int idx = activetimers.indexOf(tmr);

		if (idx == -1) {
			// Timer either no longer exists, or has been expired but not yet fired. If the latter, we
			// need to remove it from the about-to-fire expired list
			// Either way, it is not currently on active list, and so needs to be inserted into it.
			pendingtimers.withdraw(tmr);
		} else {
			// The timer is already scheduled - remove from active list, before re-inserting in new position
			activetimers.remove(idx);
		}
		activateTimer(tmr);
	}

	private void activateTimer(TimerNAF tmr)
	{
		int pos = 0; // will insert new timer at head of list, if we don't find any earlier timers
		if (tmr.getInterval() != 0) {
			//zero-sec timers go straight to front of queue, even ahead of other zero-sec ones
			for (int idx = activetimers.size() - 1; idx != -1; idx--) {
				if (tmr.getExpiryTime() >= activetimers.get(idx).getExpiryTime()) {
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
		while ((event = dynamicLoader.consume()) != null) {
			getLogger().info("Loading dynamic handler - "+event);
			if (event.getClass() == String.class) {
				String evtname = (String)event;
				if (evtname.equals(STOPCMD)) {
					// we're being asked to stop this entire dispatcher, not just a naflet
					stopSynchronously();
				} else {
					// the received item is a Naflet name, to be stopped
					Naflet app = getNaflet(evtname);
					if (app == null) {
						getLogger().info("Discarding stop request for unknown Naflet="+evtname);
						continue;
					}
					getLogger().info("Unloading Naflet="+app.getName()+" via Producer");
					stopNaflet(app);
				}
			} else {
				if (shutdownRequested) {
					getLogger().info("Discarding dynamic handler as we're in shutdown mode - "+event);
					continue;
				}
				if (event instanceof Naflet) {
					Naflet app = (Naflet)event;
					if (app.getDispatcher() != this) {
						getLogger().warn("Dispatcher="+getName()+" rejecting dynamic Naflet from wrong dispatcher="+app.getDispatcher().getName());
						return;
					}
					addNaflet(app);
					app.start(this);
				} else if (event instanceof Producer) {
					Producer<?> prod = (Producer<?>)event;
					if (prod.getDispatcher() != this) {
						getLogger().warn("Dispatcher="+getName()+" rejecting dynamic Producer from wrong dispatcher="+prod.getDispatcher().getName());
						return;
					}
					if (dynamicProducers.remove(prod)) {
						prod.shutdown();
					} else {
						dynamicProducers.add(prod);
						prod.start();
					}
				} else {
					getLogger().warn("Dispatcher="+getName()+" rejecting unknown dynamic handler - "+event);
				}
			}
		}
	}

	// This method can be (and is meant to be) called by other threads.
	// The Naflet is expected to be merely constructed, and we will call its start() method from
	// within the Dispatcher thread once it's been loaded.
	public void loadNaflet(Naflet app) throws java.io.IOException
	{
		if (app.getName().charAt(0) == '_') {
			throw new IllegalArgumentException("Invalid Naflet name (starts with underscore) - "+app.getName());
		}
		dynamicLoader.produce(app);
	}

	public void unloadNaflet(String naflet_name) throws java.io.IOException
	{
		dynamicLoader.produce(naflet_name);
	}

	public void loadProducer(Producer<?> prod) throws java.io.IOException
	{
		dynamicLoader.produce(prod);
	}

	public void unloadProducer(Producer<?> prod) throws java.io.IOException
	{
		dynamicLoader.produce(prod);
	}

	@Override
	public void entityStopped(Object entity)
	{
		Naflet app = Naflet.class.cast(entity);
		boolean exists = removeNaflet(app);
		if (!exists) return;  // duplicate notification - ignore
		getLogger().info("Dispatcher="+name+": Naflet="+app.getName()+" has terminated - remaining="+getNafletCount());
	}

	private void stopNaflet(Naflet app)
	{
		app.stop();
	}

	// This can be called from other threads
	public void registerReaper(EntityReaper reaper)
	{
		synchronized (reapers) {
			if (!reapers.contains(reaper)) reapers.add(reaper);
		}
	}

	// This can be called from other threads
	public void cancelReaper(EntityReaper reaper)
	{
		synchronized (reapers) {
			reapers.remove(reaper);
		}
	}

	// NB: This is not a performance-critical method, expected to be rarely called
	// The markup is XML, and if some of it happens to look like XHTML, that's a happy coincidence ...
	public CharSequence dumpState(StringBuilder sb, boolean verbose)
	{
		if (sb == null) {
			sb = tmpsb;
			sb.setLength(0);
		}
		dtcal.setTimeInMillis(timeboot);
		sb.append("<infonodes>");
		sb.append("<infonode name=\"Disposition\" dispatcher=\"").append(name).append("\">");
		sb.append("NAFMAN = ").append(nafman == null ? "No" : (nafman.isPrimary() ? "Primary" : "Secondary"));
		sb.append("<br/>DNS = ").append(dnsresolv == null ? "No" : dnsresolv);
		sb.append("<br/>Log-Level = ").append(getLogger().getLevel());
		sb.append("<br/>Boot-Time = ");
		TimeOps.makeTimeLogger(dtcal, sb, true, false);
		if (shutdownRequested) sb.append("<br/>In Shutdown");
		sb.append("</infonode>");

		synchronized (dynamicNaflets) {
			sb.append("<infonode name=\"NAFlets\" total=\"").append(dynamicNaflets.size()).append("\">");
			for (int idx = 0; idx != dynamicNaflets.size(); idx++) {
				sb.append("<item id=\"").append(dynamicNaflets.get(idx).getName()).append("\">");
				sb.append(dynamicNaflets.get(idx).getClass().getName()).append("</item>");
			}
		}
		sb.append("</infonode>");

		// NB: 'total' attribute will be different to 'item' count, as the former is the actual number of
		// registered channels, while the latter is only the "interesting" ones.
		sb.append("<infonode name=\"IO Channels\" total=\"").append(activechannels.size()).append("\">");
		IteratorInt itcm = activechannels.keysIterator();
		while (itcm.hasNext()) {
			ChannelMonitor cm = activechannels.get(itcm.next());
			int prevlen1 = sb.length();
			sb.append("<item id=\"").append(cm.getCMID()).append("\"");
			sb.append(" cankill=\"y\"").append(" time=\"").append(cm.getStartTime()).append("\"");
			sb.append('>');
			int prevlen2 = sb.length();
			try {
				cm.dumpState(sb, verbose);
			} catch (Throwable ex) {
				// have observed CancelledKeyException happening here during shutdown - not sure how
				sb.append(com.grey.base.ExceptionUtils.summary(ex));
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
			TimerNAF tmr = activetimers.get(idx);
			sb.append("<item>ID=").append(tmr.getID()).append(':').append(tmr.getType()).append(" - Expires ");
			TimeOps.makeTimeLogger(tmr.getExpiryTime(), sb, true, true).append(" (");
			TimeOps.expandMilliTime(tmr.getInterval(), sb, false).append(")<br/>Handler=");
			if (tmr.getHandler() == null) {
				sb.append("null");
			} else {
				sb.append(tmr.getHandler().getClass().getName());
			}
			if (tmr.getAttachment() != null) sb.append('/').append(tmr.getAttachment().getClass().getName());
			sb.append("</item>");
		}
		sb.append("</infonode>");
		sb.append("</infonodes>");
		return sb;
	}

	// Since ChannelMonitors are reused, a non-zero stime arg protects against killing a previous incarnation.
	// Actually ChannelMonitor IDs are now (since 2nd March 2014) unique per incarnation, but leave in the
	// start-time check anyway for robustness.
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
	public Naflet[] listNaflets()
	{
		synchronized (dynamicNaflets) {
			return dynamicNaflets.toArray(new Naflet[getNafletCount()]);
		}
	}

	private Naflet getNaflet(String naflet_name)
	{
		synchronized (dynamicNaflets) {
			for (int idx = 0; idx != getNafletCount(); idx++) {
				if (dynamicNaflets.get(idx).getName().equals(naflet_name)) return dynamicNaflets.get(idx);
			}
		}
		return null;
	}

	private void addNaflet(Naflet app)
	{
		synchronized (dynamicNaflets) {
			dynamicNaflets.add(app);
		}
	}

	private boolean removeNaflet(Naflet app)
	{
		synchronized (dynamicNaflets) {
			return dynamicNaflets.remove(app);
		}
	}
	
	private int getNafletCount()
	{
		synchronized (dynamicNaflets) {
			return dynamicNaflets.size();
		}
	}

	// convenience method which leverages a single pre-allocated transfer buffer for this thread
	public int transfer(java.nio.ByteBuffer src, java.nio.ByteBuffer dst)
	{
		int nbytes = com.grey.base.utils.NIOBuffers.transfer(src, dst, tmpmembuf);
		if (nbytes < 0) {
			allocMemBuffer(-nbytes);
			nbytes = com.grey.base.utils.NIOBuffers.transfer(src, dst, tmpmembuf);
		}
		return nbytes;
	}

	// This returns a temp buffer which must be used immediately, as the next call to
	// this method will probably return the same buffer.
	public java.nio.ByteBuffer allocNIOBuffer(int cap)
	{
		if (tmpniobuf == null || tmpniobuf.capacity() < cap) {
			tmpniobuf = com.grey.base.utils.NIOBuffers.create(cap, false);
		}
		tmpniobuf.clear();
		return tmpniobuf;
	}

	// This returns a temp buffer which must be used immediately, as the next call to
	// this method will probably return the same buffer.
	public byte[] allocMemBuffer(int cap)
	{
		if (tmpmembuf == null || tmpmembuf.length < cap) {
			tmpmembuf = new byte[cap];
		}
		return tmpmembuf;
	}

	private String threadInfo() {
		return (launched?(isRunning()?"live":"dead"):"init")+"/"+thrd_main.getState();
	}

	@Override
	public String toString() {
		return "Dispatcher="+name+"/"+threadInfo()+"/"+getNafletCount()+"/"+activechannels.size()+"/"+activetimers.size();
	}

	public static Dispatcher create(ApplicationContextNAF appctx, DispatcherDef def, com.grey.logging.Logger log)
		throws java.io.IOException
	{
		if (def == null) {
			def = new DispatcherDef.Builder().build();
		}
		if (def.getName() == null || def.getName().isEmpty()) {
			String name = appctx.getName()+"-AnonDispatcher-"+anonDispatcherCount.incrementAndGet();
			def = new DispatcherDef.Builder(def).withName(name).build();
		}
		Dispatcher d = new Dispatcher(appctx, def, log);
		appctx.register(d);
		return d;
	}

	public static List<Dispatcher> launchConfigured(ApplicationContextNAF appctx, Logger log)
		throws java.io.IOException
	{
		NAFConfig nafcfg = appctx.getConfig();
		XmlConfig[] cfgdispatchers = nafcfg.getDispatchers();
		if (cfgdispatchers == null) return null;
		List<Dispatcher> dlst = new ArrayList<>(cfgdispatchers.length);
		log.info("NAF: Launching configured Dispatchers="+cfgdispatchers.length);

		// Do separate loops to create and start the Dispatchers, so that they're all guaranteed to be in single-threaded
		// mode while initialising.
		// First NAFMAN-enabled Dispatcher becomes the primary.
		for (int idx = 0; idx < cfgdispatchers.length; idx++) {
			DispatcherDef def = new DispatcherDef.Builder(cfgdispatchers[idx]).build();
			Dispatcher d = create(appctx, def, log);
			dlst.add(d);
		}

		// log the initial config
		String txt = dumpConfig(appctx);
		log.info("Initialisation of the configured NAF Dispatchers is now complete\n"+txt);

		// Now starts the multi-threaded phase
		for (int idx = 0; idx != dlst.size(); idx++) {
			dlst.get(idx).start();
		}
		return dlst;
	}

	public static Dispatcher createConfigured(ApplicationContextNAF appctx, String dname, Logger log)
		throws java.io.IOException
	{
		XmlConfig cfg = appctx.getConfig().getDispatcher(dname);
		if (cfg == null) return null;
		DispatcherDef def = new DispatcherDef.Builder(cfg).build();
		return create(appctx, def, log);
	}

	public static String dumpConfig(ApplicationContextNAF appctx)
	{
		String txt = "";
		Collection<Dispatcher> dispatchers = appctx.getDispatchers();
		txt += "Dispatchers="+dispatchers.size()+":";
		for (Dispatcher d : dispatchers) {
			synchronized (d.dynamicNaflets) {
				txt += "\n- "+d.name+": NAFlets="+d.dynamicNaflets.size();
				String dlm = " - ";
				for (int idx2 = 0; idx2 != d.dynamicNaflets.size(); idx2++) {
					txt += dlm + d.dynamicNaflets.get(idx2).getName();
					dlm = ", ";
				}
			}
		}
		Collection<CM_Listener> listeners = appctx.getListeners();
		txt += "\nListeners="+listeners.size();
		for (CM_Listener l : listeners) {
			txt += "\n- "+l.getName()+": Port="+l.getPort()+", Server="+l.getServerType().getName()+" (Dispatcher="+l.getDispatcher().name+")";
		}
		return txt;
	}

	private static String showInterestOps(java.nio.channels.SelectionKey key)
	{
		if (key == null) return "None";
		if (!key.isValid()) return "Cancelled";
		return "0x"+Integer.toHexString(key.interestOps());
	}
}