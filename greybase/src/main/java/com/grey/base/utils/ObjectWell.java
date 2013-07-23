/*
 * Copyright 2010-2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import com.grey.base.config.SysProps;

/**
 * An object container which also manufactures objects on demand.
 * <br/>
 * This container extends ObjectPile by acting as an object well (or source), not just a passive store. That is,
 * whereas ObjectPile only lets you take out what you put in, this class's extraction operation may result in new objects
 * being created on demand (and will do so, on the first extraction).
 * <br/>
 * You should only use its store() methods to return objects which were previously extracted from this well (which also
 * means that they would have been originally created by this well), but beware that it does not enforce this.
 * <br/>
 * If no factory is provided, this class auto-creates the stored object elements using their the default constructor.
 * <p>
 * Since objects start off on the well and are expected to be returned to it if and when the caller is done with them,
 * the extract() method effectively hands objects out on loan, while the store() methods return them.
 * <br/>
 * When we talk below about the "extant population", we mean the total number of objects in existence, whether currently
 * stored in the well or out on loan.
 */
public final class ObjectWell<T>
	extends ObjectPile<T>
{
	public interface ObjectFactory
	{
		public Object factory_create();
	}

	private static final int DFLT_POPINCR = SysProps.get("grey.well.popincr", 10);
	private static final ShutdownHook shutdown_hook = (SysProps.get("grey.well.shutdump", false) ? new ShutdownHook() : null);

	public final String name;
	private final Class<T> clss;
	private final ObjectFactory factory;
	private final int maxpop;  //upper limit on the extant population - zero means no limit
	private final int popincr; //number of new objects to create when well runs dry
	private int totalpop; //the size of the extant population

	public int population() {return totalpop;}
	public int maxPopulation() {return maxpop;}

	public ObjectWell(Class<?> c, String well_name) {this(c, null, well_name, 0, 0);}
	public ObjectWell(ObjectFactory f, String well_name) {this(null, f, well_name, 0, 0);}
	
	/*
	 * If the incr param is zero, it will be superceded by the default increment. If callers want to
	 * implement any growth constraints they can use various combinations of initpop and maxpop to
	 * do so.
	 * Note that the generic clss type has to be vague, as Class<T> prevents calls compiling when the
	 * clss arg is a subclass of T, which should be perfectly allowable. This does mean that the caller
	 * could pass in a 'clss' param that's genuinely incompatible with the template type T, but that's
	 * their lookout.
	 */
	public ObjectWell(Class<?> c, ObjectFactory f, String well_name, int initpop, int max)
	{
		this(c, f, well_name, initpop, max, 0);
	}

	public ObjectWell(Class<?> c, ObjectFactory f, String well_name, int initpop, int max, int incr)
	{
		@SuppressWarnings("unchecked")
		Class<T> unchecked_clss = (Class<T>)c;
		clss = unchecked_clss;  //minimised scope of Suppress annotation
		factory = f;
		name = well_name+(clss==null?"":"/"+clss.getName())+(factory==null?"":"/"+factory.getClass().getName());
		maxpop = max;
		popincr = (incr == 0 ? DFLT_POPINCR : incr);
		populate(initpop);
		if (shutdown_hook != null) shutdown_hook.add(this);
	}

	// Returns Null if the well is empty and the extant population has reached the specified max
	@Override
	public T extract()
	{
		if (size() == 0)
		{
			// NB: populate() and the base class extract() will do the right thing if delta ends up
			// as zero (cannot be negative) and Null will get returned.
			int newpop = totalpop + popincr;
			if (maxpop != 0 && newpop > maxpop) newpop = maxpop;
			int delta = newpop - totalpop;
			populate(delta);
		}
		return super.extract();
	}

	@Override
	public void clear()
	{
		clear(0);
	}

	/*
	 * Discards stored objects to reduce the total extant population down to the
	 * requested max, or as near to it as we can (since we cannot recall or discard
	 * objects that are currently out on loan).
	 */
	public int clear(int newmax)
	{
		int delta = totalpop - newmax;
		if (delta <= 0) return 0;
		if (delta > size()) delta = size();

		// discard the requisite number of objects from the pile
		for (int loop = 0; loop != delta; loop++) {
			extract();
			totalpop--;
		}
		return delta;
	}

	private void populate(int incr)
	{
		for (int loop = 0; loop != incr; loop++) {
			T obj = null;
			if (factory == null) {
				try {
					obj = clss.newInstance();
				} catch (Exception ex) {
					throw new RuntimeException("Failed to populate ObjectWell="+name+" at pop="+totalpop+"/"+maxpop, ex);
				}
			} else {
				@SuppressWarnings("unchecked")
				T unchecked = (T)factory.factory_create();
				obj = unchecked;
			}
			store(obj);
			totalpop++;
		}
	}


	// This is purely a test-time debugging aid, to help detect leaks. It is absolutely unsuitable for production
	// deployment, as it prevents objects being garbage-collected and probably prints spurious warnings.
	private static class ShutdownHook extends Thread
	{
		private final java.util.concurrent.ConcurrentHashMap<String, ObjectWell<?>> wells
				= new java.util.concurrent.ConcurrentHashMap<String, ObjectWell<?>>();

		public ShutdownHook() {
			Runtime.getRuntime().addShutdownHook(this);
		}

		public void add(ObjectWell<?> well) {
			ObjectWell<?> dup = wells.putIfAbsent(well.name, well);
			if (dup != null) {
				// Can't throw, because unit tests in particular often recycle the same names, but worth commenting on
				System.out.println("ObjectWell T"+Thread.currentThread().getId()+": Replacing Well="+dup.name
						+" - alloc="+(dup.population() - dup.size())+", extant="+dup.population()+"/"+dup.maxPopulation());
			}
		}
		@Override
		public void run() {
			java.util.ArrayList<String> names = new java.util.ArrayList<String>(wells.keySet());
			java.util.Collections.sort(names);
			System.out.println("ObjectWell: Shutdown Thread=T"+Thread.currentThread().getId()+" - Wells="+names.size());
			for (int idx = 0; idx != names.size(); idx++) {
				ObjectWell<?> well = wells.get(names.get(idx));
				if (well == null) continue;
				System.out.println(" - Well="+well.name+": alloc="+(well.population() - well.size())+", extant="+well.population()+"/"+well.maxPopulation());
			}
		}
	}
}