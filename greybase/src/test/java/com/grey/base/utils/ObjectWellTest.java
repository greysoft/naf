/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class ObjectWellTest
	implements ObjectWell.ObjectFactory
{
	private static class WellObject
	{
		private int mark;
	}

	private int createcnt;

	@Override
	public WellObject factory_create()
	{
		createcnt++;
		WellObject obj = new WellObject();
		obj.mark = createcnt;
		return obj;
	}

	@org.junit.Test
	public void defaultWells()
	{
		createcnt = 0;
		ObjectWell<WellObject> well = new ObjectWell<WellObject>(this);
		org.junit.Assert.assertEquals(0, well.size());
		org.junit.Assert.assertEquals(0, well.population());
		org.junit.Assert.assertEquals(0, well.maxPopulation());
		// first extraction should trigger a population increment
		WellObject obj = well.extract();
		org.junit.Assert.assertEquals(createcnt, well.population());
		org.junit.Assert.assertEquals(well.population() - 1, well.size()); //we've extracted one
		org.junit.Assert.assertEquals(0, well.maxPopulation());
		org.junit.Assert.assertTrue(obj.mark > 0 && obj.mark <= createcnt);
		int dflt_incr = well.population();  //so now we know the default increment
		org.junit.Assert.assertTrue(dflt_incr > 2);
		// Extract the rest of the current well objects, verifying their expected properties
		// The extant population should remain constant, while the number of objects on the well declines
		int prevpop = well.population();
		java.util.Set<WellObject> extracts = new java.util.HashSet<WellObject>();
		java.util.Set<Integer> marks = new java.util.HashSet<Integer>();
		extracts.add(obj);
		marks.add(obj.mark);
		int cnt = 1;
		while (well.size() != 0) {
			cnt++;
			obj = well.extract();
			org.junit.Assert.assertEquals(prevpop, createcnt); //make sure factory wasn't invoked
			org.junit.Assert.assertEquals(prevpop, well.population());
			org.junit.Assert.assertEquals(well.population() - cnt, well.size());
			org.junit.Assert.assertEquals(0, well.maxPopulation());
			org.junit.Assert.assertTrue(obj.mark > 0 && obj.mark <= createcnt);
			org.junit.Assert.assertTrue(extracts.add(obj));
			org.junit.Assert.assertTrue(marks.add(obj.mark));
		}
		org.junit.Assert.assertEquals(dflt_incr, cnt);
		org.junit.Assert.assertEquals(well.population(), extracts.size());
		org.junit.Assert.assertEquals(well.population(), marks.size());

		// return all the objects to the well, to test the clear() methods
		well.bulkStore(extracts);
		org.junit.Assert.assertEquals(prevpop, well.population());
		org.junit.Assert.assertEquals(well.population(), well.size());
		well.extract();
		well.extract();
		org.junit.Assert.assertEquals(prevpop, well.population());
		org.junit.Assert.assertEquals(prevpop-2, well.size());
		int delta = well.clear(prevpop + 2);
		org.junit.Assert.assertEquals(0, delta);
		org.junit.Assert.assertEquals(prevpop, well.population());
		org.junit.Assert.assertEquals(prevpop-2, well.size());
		delta = well.clear(prevpop);
		org.junit.Assert.assertEquals(0, delta);
		org.junit.Assert.assertEquals(prevpop, well.population());
		org.junit.Assert.assertEquals(prevpop-2, well.size());
		delta = well.clear(prevpop - 1); //still greater than size()
		org.junit.Assert.assertEquals(1, delta);
		org.junit.Assert.assertEquals(prevpop - 1, well.population());
		prevpop = well.population();
		org.junit.Assert.assertEquals(prevpop-2, well.size());
		prevpop = well.population();
		delta = well.clear(well.size());
		org.junit.Assert.assertEquals(2, delta);
		org.junit.Assert.assertEquals(prevpop - 2, well.population());
		prevpop = well.population();
		org.junit.Assert.assertEquals(prevpop-2, well.size());
		well.clear();
		org.junit.Assert.assertEquals(2, well.population());
		org.junit.Assert.assertEquals(0, well.size());
		delta = well.clear(0);
		org.junit.Assert.assertEquals(0, delta);
		org.junit.Assert.assertEquals(2, well.population());
		org.junit.Assert.assertEquals(0, well.size());

		// Now test the other default ObjectWell constructor, which uses the template objects' default constructor
		// rather than a factory
		ObjectWell<String> well2 = new ObjectWell<String>(String.class);
		org.junit.Assert.assertEquals(0, well2.size());
		org.junit.Assert.assertEquals(0, well2.population());
		org.junit.Assert.assertEquals(0, well2.maxPopulation());
		String str = well2.extract();
		org.junit.Assert.assertEquals(dflt_incr, well2.population());
		org.junit.Assert.assertEquals(well2.population() - 1, well2.size());
		org.junit.Assert.assertEquals(0, well2.maxPopulation());
		org.junit.Assert.assertEquals(0, str.length());
	}

	@org.junit.Test
	public void customLimits()
	{
		createcnt = 0;
		int initpop = 5;
		int incr = 2;
		int maxpop = 9;
		ObjectWell<WellObject> well = new ObjectWell<WellObject>(null, this, initpop, maxpop, incr);
		org.junit.Assert.assertEquals(initpop, createcnt);
		org.junit.Assert.assertEquals(initpop, well.population());
		org.junit.Assert.assertEquals(initpop, well.size());
		// discard the well contents
		for (int loop = 0; loop != initpop; loop++) well.extract();
		org.junit.Assert.assertEquals(initpop, createcnt);
		org.junit.Assert.assertEquals(initpop, well.population());
		org.junit.Assert.assertEquals(0, well.size());
		// trigger another population increment - population increases to 7
		WellObject obj = well.extract();
		org.junit.Assert.assertNotNull(obj);
		org.junit.Assert.assertEquals(initpop+incr, createcnt);
		org.junit.Assert.assertEquals(createcnt, well.population());
		org.junit.Assert.assertEquals(incr - 1, well.size()); //we've just extracted one
		int prevpop = well.population();
		// empty the well again
		obj = well.extract();
		org.junit.Assert.assertNotNull(obj);
		org.junit.Assert.assertEquals(prevpop, createcnt);
		org.junit.Assert.assertEquals(prevpop, well.population());
		org.junit.Assert.assertEquals(0, well.size());
		// trigger another population increment - population increases to the limit of 9
		obj = well.extract();
		org.junit.Assert.assertNotNull(obj);
		org.junit.Assert.assertEquals(prevpop+incr, createcnt);
		org.junit.Assert.assertEquals(createcnt, well.population());
		org.junit.Assert.assertEquals(incr - 1, well.size()); //we've just extracted one
		prevpop = well.population();
		org.junit.Assert.assertEquals(maxpop, prevpop);//sanity-check this test's logic
		// empty the well again
		obj = well.extract();
		org.junit.Assert.assertNotNull(obj);
		org.junit.Assert.assertEquals(prevpop, createcnt);
		org.junit.Assert.assertEquals(prevpop, well.population());
		org.junit.Assert.assertEquals(0, well.size());
		// now that we're at the limit, we can't  generate any more objects
		obj = well.extract();
		org.junit.Assert.assertNull(obj);
		org.junit.Assert.assertEquals(prevpop, createcnt);
		org.junit.Assert.assertEquals(createcnt, well.population());
		org.junit.Assert.assertEquals(0, well.size());

		// in this well, the increments will not be aligned on maxpop, so final population should be partial
		createcnt = 0;
		initpop = 7;
		incr = 2;
		maxpop = 8;
		well = new ObjectWell<WellObject>(null, this, initpop, maxpop, incr);
		org.junit.Assert.assertEquals(initpop, createcnt);
		org.junit.Assert.assertEquals(initpop, well.population());
		org.junit.Assert.assertEquals(initpop, well.size());
		// discard the well contents
		for (int loop = 0; loop != initpop; loop++) well.extract();
		org.junit.Assert.assertEquals(initpop, createcnt);
		org.junit.Assert.assertEquals(initpop, well.population());
		org.junit.Assert.assertEquals(0, well.size());
		// trigger another population increment - population increases only as far as 8, rather than 9
		obj = well.extract();
		org.junit.Assert.assertNotNull(obj);
		org.junit.Assert.assertEquals(initpop+incr-1, createcnt);
		org.junit.Assert.assertEquals(createcnt, well.population());
		org.junit.Assert.assertEquals(0, well.size()); //we've just extracted one
		org.junit.Assert.assertEquals(maxpop, well.population());//sanity-check this test's logic
	}
}
