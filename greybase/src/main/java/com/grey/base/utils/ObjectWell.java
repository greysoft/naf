/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
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
 * stored inn the well or out on loan.
 */
public final class ObjectWell<T>
	extends ObjectPile<T>
{
	public interface ObjectFactory
	{
		public Object factory_create();
	}

	private static final int DFLT_POPINCR = SysProps.get("grey.well.popincr", 16);

	private final Class<T> clss;
	private final ObjectFactory factory;
	private final int maxpop;  //upper limit on the extant population - zero means no limit
	private final int popincr; //number of new objects to create when well runs dry
	private int totalpop; //the size of the extant population

	public int population() {return totalpop;}
	public int maxPopulation() {return maxpop;}

	public ObjectWell(Class<?> clss) {this(clss, null, 0, 0);}
	public ObjectWell(ObjectFactory fact) {this(null, fact, 0, 0);}
	
	/*
	 * If the incr param is zero, it will be superceded by the default increment. If callers want to
	 * implement any growth constraints they can use various combinations of initpop and maxpop to
	 * do so.
	 * Note that the generic clss type has to be vague, as Class<T> prevents calls compiling when the
	 * clss arg is a subclass of T, which should be perfectly allowable. This does mean that the caller
	 * could pass in a 'clss' param that's genuinely incompatible with the template type T, but that's
	 * their lookout.
	 */
	public ObjectWell(Class<?> clss, ObjectFactory factory, int initpop, int maxpop)
	{
		this(clss, factory, initpop, maxpop, 0);
	}

	public ObjectWell(Class<?> clss, ObjectFactory factory, int initpop, int maxpop, int incr)
	{
		@SuppressWarnings("unchecked")
		Class<T> unchecked_clss = (Class<T>)clss;
		this.clss = unchecked_clss;  //minimised scope of Suppress annotation
		this.factory = factory;
		this.maxpop = maxpop;
		popincr = (incr == 0 ? DFLT_POPINCR : incr);
		populate(initpop);
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
					throw new RuntimeException("Failed to populate ObjectWell at pop="+totalpop+"/"+maxpop, ex);
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
}
