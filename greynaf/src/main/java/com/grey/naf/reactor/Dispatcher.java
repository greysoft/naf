/*
 * Copyright 2010-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.time.Clock;

import com.grey.base.config.SysProps;
import com.grey.base.collections.HashedMapIntKey;
import com.grey.base.collections.Circulist;
import com.grey.base.collections.ObjectQueue;
import com.grey.base.collections.IteratorInt;
import com.grey.base.collections.ObjectPool;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.Naflet;
import com.grey.naf.EntityReaper;
import com.grey.naf.nafman.NafManAgent;
import com.grey.naf.nafman.NafManConfig;
import com.grey.naf.nafman.NafManRegistry;
import com.grey.naf.nafman.PrimaryAgent;
import com.grey.naf.nafman.SecondaryAgent;
import com.grey.naf.reactor.IOExecWriter.FileWrite;
import com.grey.naf.reactor.config.DispatcherConfig;
import com.grey.naf.errors.NAFConfigException;
import com.grey.logging.Logger;
import com.grey.logging.Logger.LEVEL;

public class Dispatcher
	implements Runnable, TimerNAF.TimeProvider, EntityReaper, Producer.Consumer<Object>
{
	public enum STOPSTATUS {STOPPED, ALIVE, FORCED}

	private static final boolean INTERRUPT_FRIENDLY = SysProps.get("greynaf.dispatchers.interrupts", false);
	private static final long TMT_FORCEDSTOP = SysProps.getTime("greynaf.dispatchers.forcestoptmt", "1s");
	private static final boolean HEAPWAIT = SysProps.get("greynaf.dispatchers.heapwait", false);
	private static final String STOPCMD = "_STOP_";

	private static final AtomicInteger anonDispatcherCount = new AtomicInteger();

	private final String dname;
	private final ApplicationContextNAF appctx;
	private final Flusher flusher;
	private final Logger logger;
	private final Clock clock;
	private final Thread threadMain;
	private final Thread threadInitial;
	private final boolean surviveHandlers; //survive error in event handlers
	private final long timeBoot;

	private final Map<String, Object> namedItems = new ConcurrentHashMap<>();
	private final ArrayList<DispatcherRunnable> dynamicRunnables = new ArrayList<>();
	private final ArrayList<EntityReaper> reapers = new ArrayList<>(); //objects that wish to be infomed of our shutdown
	private final HashedMapIntKey<ChannelMonitor> activeChannels = new HashedMapIntKey<>(); //keyed on cm_id
	private final Circulist<TimerNAF> activeTimers = new Circulist<>();
	private final ObjectQueue<TimerNAF> pendingTimers = new ObjectQueue<>();  //timers which have expired and are ready to fire
	private final ObjectPool<TimerNAF> timerPool;
	private final ObjectPool<IOExecWriter.FileWrite> fileWritePool;
	private final java.nio.channels.Selector slct;
	private final Producer<Object> dynamicLoader;
	private final boolean threadTolerant = SysProps.get("greynaf.dispatchers.tolerant_threadchecks", false); //for benefit of some unit tests

	private final AtomicInteger nextChannelId = new AtomicInteger(1);
	private int nextTimerId = 1; //only used within Disoatcher thread, so not synchronised
	private boolean launched;
	private boolean shutdownRequested;
	private boolean shutdownPerformed;
	private long systime_msecs;

	// temp working buffers, preallocated (on demand) for efficiency
	private final java.util.Calendar dtcal = TimeOps.getCalendar(null);
	private final StringBuilder tmpsb = new StringBuilder();
	private java.nio.ByteBuffer tmpniobuf;
	private byte[] tmpmembuf;

	public boolean isDispatcherThread() {return Thread.currentThread() == threadMain;}
	public boolean isRunning() {return threadMain.isAlive();}
	public boolean isActive() {return isRunning() && !shutdownRequested;}
	public void waitStopped() {waitStopped(0, false);}

	public String getName() {return dname;}
	public long getTimeBoot() {return timeBoot;}
	public ApplicationContextNAF getApplicationContext() {return appctx;}
	public Flusher getFlusher() {return flusher;}
	public Logger getLogger() {return logger;}

	IOExecWriter.FileWrite allocFileWrite() {return fileWritePool.extract();}
	void releaseFileWrite(IOExecWriter.FileWrite fw) {fileWritePool.store(fw);}
	int allocateChannelId() {return nextChannelId.getAndIncrement();}
	java.util.Calendar getCalendar() {return dtcal;}

	//this is mainly for the benefit of test code - should be tested after Thread join
	private boolean thread_completed;
	private boolean error_abort;
	public boolean completedOK() {return thread_completed && !error_abort;}

	public static Dispatcher create(ApplicationContextNAF appctx, DispatcherConfig def, Logger log) throws java.io.IOException {
		if (def == null) {
			def = new DispatcherConfig.Builder().build();
		}
		if (def.getName() == null || def.getName().isEmpty()) {
			String name = appctx.getName()+"-AnonDispatcher-"+anonDispatcherCount.incrementAndGet();
			def = new DispatcherConfig.Builder(def).withName(name).build();
		}
		Dispatcher dsptch = new Dispatcher(appctx, def, log);
		appctx.register(dsptch);

		// Create the NAFMAN agent (if any)
		NafManConfig nafmanConfig = appctx.getNafManConfig();
		if (nafmanConfig != null) {
			NafManRegistry reg = NafManRegistry.get(appctx);
			Supplier<PrimaryAgent> supplier = () -> {
				try {
					return new PrimaryAgent(dsptch, reg, nafmanConfig);
				} catch (Exception ex) {
					throw new NAFConfigException("Failed to create primary NAFMAN agent for Dispatcher="+dsptch.getName(), ex);
				}
			};
			NafManAgent agent = appctx.getNamedItem(PrimaryAgent.class.getName(), supplier);
			if (agent.getDispatcher() != dsptch) {
				agent = new SecondaryAgent(dsptch, reg, nafmanConfig);
			}
			dsptch.setNamedItem(NafManAgent.class.getName(), agent);
			dsptch.getLogger().info("Dispatcher="+dsptch.getName()+": Initialised NAFMAN - "+(agent.isPrimary() ? "Primary" : "Secondary"));
		}
		return dsptch;
	}

	private Dispatcher(ApplicationContextNAF appctx, DispatcherConfig def, Logger initlog) throws java.io.IOException {
		this.appctx = appctx;
		dname = def.getName();
		surviveHandlers = def.isSurviveHandlers();

		clock = def.getClock();
		timeBoot = clock.millis();
		systime_msecs = timeBoot;

		String logname = def.getLogName();
		Logger dlog = initlog;
		if (logname != null || initlog == null) dlog = com.grey.logging.Factory.getLogger(logname == null ? dname : logname);
		logger = dlog;
		if (initlog != null) initlog.info("Initialising Dispatcher="+dname+" in AppCtx="+appctx.getName()+" - Logger="+logname+" => "+dlog);

		threadMain = new Thread(this, "Dispatcher-"+dname);
		threadInitial = Thread.currentThread();

		timerPool = new ObjectPool<>(() -> new TimerNAF());
		fileWritePool = new ObjectPool<>(() -> new FileWrite());
		slct = java.nio.channels.Selector.open();

		dynamicLoader = new Producer<>("DispatcherRunnables", this, this);

		flusher = new Flusher(this, def.getFlushInterval());
		if (getLogger() != initlog) flusher.register(getLogger());

		getLogger().info("Dispatcher="+dname+": Initialised with baseport="+appctx.getConfig().getBasePort()
				+", NAFMan="+(appctx.getNafManConfig()!=null)+", survive_handlers="+surviveHandlers
				+", flush="+TimeOps.expandMilliTime(def.getFlushInterval())
				+"\n\tSelector="+slct.getClass().getCanonicalName()+", Provider="+slct.provider().getClass().getCanonicalName()
				+" - half-duplex="+ChannelMonitor.halfduplex+", timer-jitter="+TimerNAF.JITTER_THRESHOLD
				+", wbufs="+IOExecWriter.MAXBUFSIZ+"/"+IOExecWriter.FILEBUFSIZ);
	}

	public Thread start()
	{
		getLogger().info("Dispatcher="+getName()+": Loaded JARs "+com.grey.base.utils.PkgInfo.getLoadedJARs());
		launched = true;
		threadMain.start();
		Logger.setThreadLogger(getLogger(), threadMain.getId());
		return threadMain;
	}

	@Override
	public void run()
	{
		getLogger().info("Dispatcher="+getName()+": Started thread="+Thread.currentThread().getName()+":T"+Thread.currentThread().getId());
		systime_msecs = getRealTime();
		boolean ok = true;

		try {
			// Enter our main execution loop
			NafManAgent agent  = getNafManAgent();
			if (agent != null) agent.start();
			dynamicLoader.startDispatcherRunnable();
			activate();	
		} catch (Throwable ex) {
			String msg = "Dispatcher="+getName()+" has terminated abnormally";
			getLogger().log(LEVEL.ERR, ex, true, msg);
			ok = false;
		}
		shutdown(true);
		try {getLogger().flush(); } catch (Exception ex) {getLogger().trace("Dispatcher="+getName()+": Final thread flush failed - "+ex);}
		getLogger().info("Dispatcher="+getName()+" thread has terminated with abort="+error_abort+" - heapwait="+HEAPWAIT);
		
		if (ObjectPool.DEBUG) {
			if (timerPool.getActiveCount() != 0)
				throw new IllegalStateException("Dispatcher="+getName()+" has active timers on exit - count="+timerPool.getActiveCount());
			if (fileWritePool.getActiveCount() != 0)
				throw new IllegalStateException("Dispatcher="+getName()+" has active FileWrites on exit - count="+fileWritePool.getActiveCount());
		}

		if (HEAPWAIT) {
			//this is purely to support interactive troubleshooting - hold process alive so debug tools can attach
			for (;;) TimerNAF.sleep(5000);
		}
		thread_completed = ok;
	}

	// This method can be called by other threads
	public boolean stop()
	{
		if (isDispatcherThread() || threadMain.getState() == Thread.State.NEW) {
			return stopSynchronously();
		}

		try {
			dynamicLoader.produce(STOPCMD);
		} catch (java.io.IOException ex) {
			//probably a harmless error caused by Dispatcher already being shut down
			getLogger().info("Dispatcher="+getName()+": Failed to send cmd="+STOPCMD+" to Dispatcher="+getName()+", Thread="+threadInfo()+" - "+ex);
		}
		return false;
	}

	// This must only be called within the Dispatcher thread
	private boolean stopSynchronously()
	{
		if (shutdownPerformed) return true;
		getLogger().info("Dispatcher="+getName()+": Received Stop request with shutdown="+shutdownRequested+"/"+launched+", Thread="+threadInfo()
				+" - Runnables="+dynamicRunnables.size()+"/"+getNafletCount()+", Channels="+activeChannels.size());
		shutdownRequested = true; //must set this before notifying the runnables

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
		getLogger().info("Dispatcher="+getName()+" in shutdown with endOfLife="+endOfLife+", Thread="+threadInfo()
				+" - Runnables="+dynamicRunnables.size()+"/"+getNafletCount()+", Channels="+activeChannels.size()
				+", Timers="+activeTimers.size()+":"+pendingTimers.size());
		try {
			if (slct.isOpen()) slct.close();
		} catch (Throwable ex) {
			getLogger().log(LEVEL.INFO, ex, false, "Dispatcher="+getName()+": Failed to close NIO Selector");
		}

		int reaper_cnt;
		synchronized (reapers) {
			reaper_cnt = reapers.size();
			while (reapers.size() != 0) {
				EntityReaper r = reapers.remove(reapers.size()-1);
				r.entityStopped(this);
			}
		}
		if (getNafManAgent() != null) getNafManAgent().stop();

		if (activeChannels.size() != 0) {
			getLogger().trace("Channels: "+activeChannels);
			closeAllChannels();
			getLogger().info("Issued Disconnects - remaining channels="+(activeChannels.size()==0?Integer.valueOf(0):activeChannels));
		}

		List<DispatcherRunnable> lst = new ArrayList<>(dynamicRunnables);// take copy of list to prevent concurrent modification
		for (DispatcherRunnable r : lst) {
			if (r instanceof ChannelMonitor) { //activeChannels should now be empty, but skip any entries that might remain
				ChannelMonitor cm = (ChannelMonitor)r;
				if (activeChannels.get(cm.getCMID()) == r) continue;
			}
			r.stopDispatcherRunnable();
		}
		try {getLogger().flush(); } catch (Exception ex) {getLogger().trace("Dispatcher="+getName()+": shutdown() flush failed - "+ex);}
		flusher.shutdown();
		dynamicLoader.stopDispatcherRunnable();
		getApplicationContext().deregister(this);

		getLogger().info("Dispatcher="+getName()+": Shutdown completed - Runnables="+dynamicRunnables.size()+"/"+getNafletCount()+", Channels="+activeChannels.size()
				+", Timers="+activeTimers.size()+":"+pendingTimers.size()
				+", reapers="+reaper_cnt);
		if (dynamicRunnables.size() != 0) getLogger().trace("Dynamic Runnables: "+dynamicRunnables);
		if (activeTimers.size()+pendingTimers.size() != 0) getLogger().trace("Timers: Active="+activeTimers+" - Pending="+pendingTimers);
		shutdownPerformed = true;
	}

	private void closeAllChannels() {
		List<CM_Listener> lstnrs = new ArrayList<>();
		List<ChannelMonitor> lst = new ArrayList<>(activeChannels.getValues()); // take copy of list to prevent concurrent modification

		for (ChannelMonitor cm : lst) {
			if (!activeChannels.containsValue(cm)) continue; //must have been removed as side-effect of another close
			if (cm instanceof CM_Listener) {
				//needs to be stopped in a top-down manner below, rather than bubbling up from socket closure
				lstnrs.add((CM_Listener)cm);
			} else {
				cm.disconnect(false);
			}
		}

		for (CM_Listener l : lstnrs) {
			l.stop(true);
		}
	}

	// meant to be called by other threads
	public STOPSTATUS waitStopped(long timeout, boolean force)
	{
		if (timeout < 0) timeout = 1L;
		boolean done = false;
		do {
			try {
				threadMain.join(timeout);
				done = true;
			} catch (InterruptedException ex) {}
		} while (!done);
		if (!threadMain.isAlive()) return STOPSTATUS.STOPPED;
		if (!force) return STOPSTATUS.ALIVE;
		getLogger().warn("Dispatcher="+getName()+": Forced stop after timeout="+timeout+" - Interrupt="+INTERRUPT_FRIENDLY);
		if (INTERRUPT_FRIENDLY) threadMain.interrupt(); //maximise the chances of waking up a blocked thread
		stop();
		if (waitStopped(TMT_FORCEDSTOP, false) == STOPSTATUS.STOPPED) return STOPSTATUS.FORCED;
		return STOPSTATUS.ALIVE; //failed to stop it - could only happen if blocked in an application callback
	}

	// This is the Dispatcher's main loop.
	// It will execute in here for the entirety of its lifetime, until all the events it is monitoring cease to be.
	private void activate() throws java.io.IOException
	{
		getLogger().info("Dispatcher="+getName()+": Entering Reactor event loop with Runnables="+dynamicRunnables.size()+"/"+getNafletCount()
				+", Channels="+activeChannels.size()+", Timers="+activeTimers.size()+", shutdown="+shutdownRequested);

		while (!shutdownRequested && (activeChannels.size() + activeTimers.size() != 0))
		{
			if (INTERRUPT_FRIENDLY) Thread.interrupted();//clear any pending interrupt status
			systime_msecs = 0;

			if (activeTimers.size() == 0) {
				if (slct.select() != 0) fireIO();
			} else {
				long iotmt = activeTimers.get(0).getExpiryTime() - getSystemTime();
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
		getLogger().info("Dispatcher="+getName()+": Reactor event loop terminated - Runnables="+dynamicRunnables.size()+"/"+getNafletCount()
				+", Channels="+activeChannels.size()+"/"+finalkeys
				+", Timers="+activeTimers.size()+" (pending="+pendingTimers.size()+")");
	}

	private void fireTimers()
	{
		// Extract all expired timers before firing any of them, to make sure any further timers they
		// install don't get fired in this loop, else continuous zero-second timers could prevent us ever
		// completing the loop.
		// It would also not be safe to take the obvious option of storing pending timers as an ArrayList
		// and looping over it, as pending timers can be withdrawn by the action of preceding ones, and
		// that would throw the loop iteration out.
		while (activeTimers.size() != 0) {
			// Fire within milliseconds of maturity, as jitter in the system clock means the NIO
			// Selector can trigger a fraction early.
			TimerNAF tmr = activeTimers.get(0);
			if (tmr.getExpiryTime() - getSystemTime() >= TimerNAF.JITTER_THRESHOLD) break; //no expired timers left
			activeTimers.remove(0);
			pendingTimers.add(tmr);
		}
		TimerNAF tmr;

		while ((tmr = pendingTimers.remove()) != null) {
			try {
				tmr.fire(this);
			} catch (Throwable ex) {
				try {
					eventHandlerFailed(null, tmr, ex);
				} catch (Throwable ex2) {
					getLogger().log(LEVEL.ERR, ex2, true, "Dispatcher="+getName()+": Error handler failed on timer - "+tmr);
				}
			}
			timerPool.store(tmr.clear());
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
					getLogger().log(LEVEL.ERR, ex2, true, "Dispatcher="+getName()+": Error handler failed on I/O - "+cm);
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
					&& cmerr != null //should be non-null if prior conditions hold, but make double sure
					&& !(cmerr instanceof CM_UDP)) {
				cmerr.failed(true, ex);
			} else {
				if (!bpex) {
					tmpsb.setLength(0);
					tmpsb.append("Dispatcher=").append(getName()).append(": Error on ");
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
			getLogger().log(LEVEL.ERR, ex2, true, "Dispatcher="+getName()+": Error Handler failed - "+(cm==null?tmr.getHandler():cm)
					+" - "+com.grey.base.ExceptionUtils.summary(ex));
		}
		if (!surviveHandlers) {
			getLogger().warn("Dispatcher="+getName()+": Initiating Abort due to error in "+(cm==null?"Timer":"I/O")+" Handler");
			error_abort = true;
			stopSynchronously();
		}
	}

	// ChannelMonitors must bookend all their activity between a single call to this method and another one
	// to deregisterlIO().
	// In between, they can can call monitorIO() multiple times to stop and start listening for specific I/O events.
	void registerIO(ChannelMonitor cm) {
		verifyIsDispatcherThread();
		if (activeChannels.put(cm.getCMID(), cm) != null) {
			throw new IllegalStateException("Dispatcher="+getName()+": Illegal registerIO on CM="+cm.getClass().getName()+"/E"+cm.getCMID()
					+" - Ops="+showInterestOps(cm.getRegistrationKey())+" - "+cm);
		}
	}

	void deregisterIO(ChannelMonitor cm) {
		verifyIsDispatcherThread();
		if (activeChannels.remove(cm.getCMID()) == null) {
			throw new IllegalStateException("Dispatcher="+getName()+": Illegal deregisterIO on CM="+cm.getClass().getName()+"/E"+cm.getCMID()
					+" - Ops="+showInterestOps(cm.getRegistrationKey())+" - "+cm);
		}
		if (cm.getRegistrationKey() != null) {
			cm.getRegistrationKey().cancel();
			cm.setRegistrationKey(null);
		}
	}

	void conditionalDeregisterIO(ChannelMonitor cm) {
		if (activeChannels.containsKey(cm.getCMID())) deregisterIO(cm);
	}

	void monitorIO(ChannelMonitor cm, int ops) throws java.nio.channels.ClosedChannelException {
		verifyIsDispatcherThread();
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

	public TimerNAF setTimer(long interval, int type, TimerNAF.Handler handler, Object attachment) {
		verifyIsSyncThread(false);
		TimerNAF tmr = timerPool.extract().init(this, handler, interval, type, nextTimerId++, attachment);
		activateTimer(tmr);
		return tmr;
	}

	void cancelTimer(TimerNAF tmr) {
		verifyIsDispatcherThread();
		//remove from scheduled queue
		if (!activeTimers.remove(tmr)) {
			//remove from ready-to-fire queue
			if (!pendingTimers.withdraw(tmr)) {
				//unknown timer - it is safe to repeat a cancel-timer op, but this could be a bug - worth logging
				getLogger().info("Dispatcher="+getName()+": Cancel on unknown Timer="+tmr+" - "+activeTimers+" - pend="+pendingTimers);
				return;
			}
		}
		//NB: This is safe against duplicate store() calls because it's illegal to cancel a timer after it's fired
		timerPool.store(tmr.clear());
	}

	void resetTimer(TimerNAF tmr) {
		verifyIsDispatcherThread();
		tmr.resetExpiry();
		int idx = activeTimers.indexOf(tmr);

		if (idx == -1) {
			// Timer either no longer exists, or has been expired but not yet fired. If the latter, we
			// need to remove it from the about-to-fire expired list
			// Either way, it is not currently on active list, and so needs to be inserted into it.
			pendingTimers.withdraw(tmr);
		} else {
			// The timer is already scheduled - remove from active list, before re-inserting in new position
			activeTimers.remove(idx);
		}
		activateTimer(tmr);
	}

	private void activateTimer(TimerNAF tmr) {
		int pos = 0; // will insert new timer at head of list, if we don't find any earlier timers
		if (tmr.getInterval() != 0) {
			//zero-sec timers go straight to front of queue, even ahead of other zero-sec ones
			for (int idx = activeTimers.size() - 1; idx != -1; idx--) {
				if (tmr.getExpiryTime() >= activeTimers.get(idx).getExpiryTime()) {
					// insert tmr AFTER this node
					pos = idx + 1;
					break;
				}
			}
		}
		activeTimers.insert(pos, tmr);
	}

	@Override
	public void entityStopped(Object entity) {
		verifyIsDispatcherThread();
		boolean exists = (entity instanceof DispatcherRunnable ? dynamicRunnables.remove(entity) : false);
		getLogger().info("Dispatcher="+getName()+" has received entity termination with exists="+exists+", runnables="+dynamicRunnables.size()+"/"+getNafletCount()+" - "+entity);
	}

	@Override
	public void producerIndication(Producer<Object> producer) {
		verifyIsDispatcherThread();
		Object event;
		while ((event = producer.consume()) != null) {
			getLogger().info("Dispatcher="+getName()+": Received dynamic event - "+event);
			if (event.getClass() == String.class) {
				String evtname = (String)event;
				if (evtname.equals(STOPCMD)) {
					// we're being asked to stop this entire Dispatcher
					stopSynchronously();
				} else {
					// the received item is a Naflet name, to be stopped
					Naflet app = getNaflet(evtname);
					if (app == null) {
						getLogger().info("Dispatcher="+getName()+": Discarding stop request for unknown Naflet="+evtname);
						continue;
					}
					try {
						handleDynamicRunnable(app);
					} catch (Throwable ex) {
						getLogger().log(LEVEL.WARN, ex, true, "Dispatcher="+getName()+" Failed to stop Naflet - "+event);
					}
				}
			} else {
				if (shutdownRequested) {
					getLogger().info("Dispatcher="+getName()+": Discarding dynamic event as we're in shutdown mode - "+event);
					continue;
				}
				if (event instanceof DispatcherRunnable) {
					try {
						handleDynamicRunnable((DispatcherRunnable)event);
					} catch (Throwable ex) {
						getLogger().log(LEVEL.ERR, ex, true, "Dispatcher="+getName()+" Failed to handle runnable - "+event);
					}
				} else {
					getLogger().warn("Dispatcher="+getName()+": Rejecting unrecognised dynamic event - "+event);
				}
			}
		}
	}

	private void handleDynamicRunnable(DispatcherRunnable r) throws java.io.IOException {
		if (r.getDispatcher() != this) {
			getLogger().warn("Dispatcher="+getName()+" Rejecting dynamic runnable from wrong dispatcher="+r.getDispatcher().getName()+" - "+r);
			return;
		}
		if (dynamicRunnables.remove(r)) {
			getLogger().info("Dispatcher="+getName()+": Unloading dynamic runnable - "+r);
			r.stopDispatcherRunnable();
		} else {
			getLogger().info("Dispatcher="+getName()+": Loading dynamic runnable - "+r);
			r.startDispatcherRunnable();
			dynamicRunnables.add(r);
		}
	}

	/**
	 * These load/unload methods can be called by other threads, and this load is suitable for objects that either:
	 * a) Have been created and initialised in same thread as Dispatcher, which then calls this method before Dispatcher starts
	 * b) Are immutable, or else defer all their initialisation till startDispatcherRunnable()
	 * Their startDispatcherRunnable() method will be called within the Dispatcher thread.
	 */
	public void loadRunnable(DispatcherRunnable r) throws java.io.IOException {
		if (r.getName().charAt(0) == '_') {
			throw new IllegalArgumentException("Dispatcher="+getName()+" rejecting invalid Runnable name (starts with underscore) - "+r.getName()+"="+r);
		}
		dynamicLoader.produce(r);
	}

	public void unloadRunnable(DispatcherRunnable r) throws java.io.IOException {
		dynamicLoader.produce(r);
	}

	public void unloadNaflet(String naflet_name) throws java.io.IOException {
		dynamicLoader.produce(naflet_name);
	}

	// This can be called from other threads
	public void registerReaper(EntityReaper reaper) {
		synchronized (reapers) {
			if (!reapers.contains(reaper)) reapers.add(reaper);
		}
	}

	// This can be called from other threads
	public void cancelReaper(EntityReaper reaper) {
		synchronized (reapers) {
			reapers.remove(reaper);
		}
	}

	private Naflet getNaflet(String naflet_name) {
		for (DispatcherRunnable r : dynamicRunnables) {
			if (r instanceof Naflet) {
				if (naflet_name.equals(r.getName())) return (Naflet)r;
			}
		}
		return null;
	}

	private int getNafletCount() {
		int cnt = 0;
		for (DispatcherRunnable r : dynamicRunnables) {
			if (r instanceof Naflet) cnt++;
		}
		return cnt;
	}

	@SuppressWarnings("unchecked")
	public <T> T getNamedItem(String name, Supplier<T> supplier) {
		if (supplier == null) return (T)namedItems.get(name);
		return (T)namedItems.computeIfAbsent(name, k -> supplier.get());
	}

	public <T> T setNamedItem(String name, T item) {
		@SuppressWarnings("unchecked") T prev = (T)namedItems.put(name, item);
		return prev;
	}

	public <T> T removeNamedItem(String name) {
		@SuppressWarnings("unchecked") T prev = (T)namedItems.remove(name);
		return prev;
	}

	public NafManAgent getNafManAgent() {
		return getNamedItem(NafManAgent.class.getName(), null);
	}

	/**
	 * This should only be called within the Dispatcher thread.
	 */
	@Override
	public long getSystemTime() {
		verifyIsSyncThread(true);
		if (systime_msecs == 0) systime_msecs = getRealTime();
		return systime_msecs;
	}

	/**
	 * This returns the instantaneous system time, and is thread-safe.
	 */
	@Override
	public long getRealTime() {
		return clock.millis();
	}

	// Since ChannelMonitors are reused, a non-zero stime arg protects against killing a previous incarnation.
	// Actually ChannelMonitor IDs are now (since 2nd March 2014) unique per incarnation, but leave in the
	// start-time check anyway for robustness.
	public boolean killConnection(int id, long stime, String diag) throws java.io.IOException
	{
		verifyIsDispatcherThread();
		ChannelMonitor cm = activeChannels.get(id);
		if (cm == null) return false;
		if (stime != 0 && stime != cm.getStartTime()) return false;
		cm.ioDisconnected(diag);
		return true;
	}

	// NB: This is not a performance-critical method, expected to be rarely called
	// The markup is XML, and if some of it happens to look like XHTML, that's a happy coincidence ...
	public CharSequence dumpState(StringBuilder sb, boolean verbose)
	{
		verifyIsDispatcherThread();
		if (sb == null) {
			sb = tmpsb;
			sb.setLength(0);
		}
		NafManAgent agent  = getNafManAgent();
		dtcal.setTimeInMillis(timeBoot);
		sb.append("<infonodes>");
		sb.append("<infonode name=\"Disposition\" dispatcher=\"").append(getName()).append("\">");
		sb.append("Application-Context = ").append(getApplicationContext().getName());
		sb.append("<br/>NAFMAN = ").append(agent == null ? "No" : (agent.isPrimary() ? "Primary" : "Secondary"));
		sb.append("<br/>Log-Level = ").append(getLogger().getLevel());
		sb.append("<br/>Boot-Time = ");
		TimeOps.makeTimeLogger(dtcal, sb, true, false);
		if (shutdownRequested) sb.append("<br/>In Shutdown");
		sb.append("</infonode>");

		sb.append("<infonode name=\"NAFlets\" total=\"").append(getNafletCount()).append("\">");
		for (DispatcherRunnable r : dynamicRunnables) {
			if (r instanceof Naflet) {
				sb.append("<item id=\"").append(r.getName()).append("\">");
				sb.append(r.getClass().getName()).append("</item>");
			}
		}
		sb.append("</infonode>");

		int rcnt = dynamicRunnables.size() - getNafletCount();
		if (rcnt != 0) {
			sb.append("<infonode name=\"Runnables\" total=\"").append(dynamicRunnables.size()-getNafletCount()).append("\">");
			for (DispatcherRunnable r : dynamicRunnables) {
				if (r instanceof Naflet) continue;
				sb.append("<item id=\"").append(r.getName()).append("\">");
				sb.append(r.getClass().getName()).append("</item>");
			}
			sb.append("</infonode>");
		}

		sb.append("<infonode name=\"Named Items\" total=\"").append(namedItems.size()).append("\">");
		for (Map.Entry<String,?> ent : namedItems.entrySet()) {
			sb.append("<item id=\"").append(ent.getKey()).append("\">");
			sb.append(ent.getValue().getClass().getName()).append("</item>");
		}
		sb.append("</infonode>");

		// NB: 'total' attribute will be different to 'item' count, as the former is the actual number of
		// registered channels, while the latter is only the "interesting" ones.
		sb.append("<infonode name=\"IO Channels\" total=\"").append(activeChannels.size()).append("\">");
		IteratorInt itcm = activeChannels.keysIterator();
		while (itcm.hasNext()) {
			ChannelMonitor cm = activeChannels.get(itcm.next());
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
		sb.append("<infonode name=\"Timers\" total=\"").append(activeTimers.size()).append("\">");
		int cnt = (verbose ? activeTimers.size() : 0);
		for (int idx = 0; idx != cnt; idx++) {
			TimerNAF tmr = activeTimers.get(idx);
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

	// convenience method which leverages a single pre-allocated transfer buffer for this thread
	public int transfer(java.nio.ByteBuffer src, java.nio.ByteBuffer dst)
	{
		verifyIsSyncThread(false);
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
		verifyIsSyncThread(false);
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
		verifyIsSyncThread(false);
		if (tmpmembuf == null || tmpmembuf.length < cap) {
			tmpmembuf = new byte[cap];
		}
		return tmpmembuf;
	}

	private String threadInfo() {
		return (launched?(isRunning()?"live":"dead"):"init")+"/"+threadMain.getState();
	}

	private void verifyIsDispatcherThread() {
		Thread thrd = Thread.currentThread();
		if (!isDispatcherThread()) throw new IllegalStateException("Dispatcher="+getName()
				+" in thread="+threadMain.getId()+"/"+threadMain.getName()+"/"+threadMain.getState()
				+" called by other thread="+thrd.getId()+"/"+thrd.getName()+"/"+thrd.getState());
	}

	private void verifyIsSyncThread(boolean lenient) {
		Thread thrd = Thread.currentThread();
		if (isDispatcherThread()
				|| (thrd == threadInitial && threadMain.getState() == Thread.State.NEW)) return;
		if (threadTolerant) {
			if (lenient
					&& (threadMain.getState() == Thread.State.NEW || threadMain.getState() == Thread.State.TERMINATED)) return;
		}
		throw new IllegalStateException("Dispatcher="+getName()+" in thread="+threadMain.getId()+"/"+threadMain.getName()+"/"+threadMain.getState()
				+" called by non-sync thread="+thrd.getId()+"/"+thrd.getName()+"/"+thrd.getState());
	}

	@Override
	public String toString() {
		return "Dispatcher="+getName()+" - appctx="+getApplicationContext().getName();
	}

	private static String showInterestOps(java.nio.channels.SelectionKey key) {
		if (key == null) return "None";
		if (!key.isValid()) return "Cancelled";
		return "0x"+Integer.toHexString(key.interestOps());
	}
}