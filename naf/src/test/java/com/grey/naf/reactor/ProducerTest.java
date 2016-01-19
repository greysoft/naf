/*
 * Copyright 2011-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;

public class ProducerTest
	implements Producer.Consumer<String>
{
	private static final String rootdir = DispatcherTest.initPaths(ProducerTest.class);
	private Dispatcher dsptch;

	private static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("");
	private static final String[] items_to_produce = new String[]{"Item1", "Item2a", "Item2b", "Item2c", "Item3a", "Item3b"};
	private java.util.ArrayList<String> produced_items = new java.util.ArrayList<String>();
	private Thread dthrd;
	private int consumed_cnt;
	private boolean benchmark_mode;

	@org.junit.Test
	public void workflow()
			throws com.grey.base.GreyException, java.io.IOException, InterruptedException
	{
		FileOps.deleteDirectory(rootdir);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "producertest-workflow";
		def.hasNafman = false;
		def.surviveHandlers = false;
		dsptch = Dispatcher.create(def, null, logger);
		Producer<String> prod = new Producer<String>(String.class, dsptch, this);

		// Launch threaded Producer in this thread
		setProducedItems("A");
		produce(prod);
		org.junit.Assert.assertEquals(produced_items.size(), consumed_cnt);

		// Launch threaded Producer in another thread
		// The thread-join is a synchronising event, hence we can see consumed_cnt
		setProducedItems("B");
		dthrd = dsptch.start();
		produce(prod);
		//wait for Dispatcher to stop - we will have told it to once we finish consuming
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		dthrd = null;
		org.junit.Assert.assertEquals(produced_items.size(), consumed_cnt);

		int prevcnt = consumed_cnt;
		prod.shutdown(true);
		org.junit.Assert.assertEquals(prevcnt, consumed_cnt);
		prod.shutdown();  //make sure duplicate close is ok

		// Repeat test with non-MT Producer
		prod = new Producer<String>(String.class, this, null);
		setProducedItems("C");
		produce(prod);
		prod.shutdown();
		org.junit.Assert.assertEquals(produced_items.size(), consumed_cnt);
	}

	// This runs in a very reasonable time, but larger values can be attempted for interactive testing
	@org.junit.Test
	public void bulktest()
			throws com.grey.base.GreyException, java.io.IOException, InterruptedException
	{
		FileOps.deleteDirectory(rootdir);
		int benchsize = 100 * 1000;
		benchmark_mode = true;
		consumed_cnt = 0;
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.name = "producertest-bulk";
		def.hasNafman = false;
		def.surviveHandlers = false;
		Dispatcher d = Dispatcher.create(def, null, logger);
		Producer<String> p = new Producer<String>(String.class, d, this);
		d.start();
		long systime1 = System.currentTimeMillis();
		for (int idx = 0; idx != benchsize; idx++) {
			p.produce("How fast can you go");
		}
		long systime2 = System.currentTimeMillis();
		d.stop();
		Dispatcher.STOPSTATUS stopsts = d.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(d.completedOK());
		p.shutdown(true);
		System.out.println("BulkTest-"+benchsize+" = "+TimeOps.expandMilliTime(systime2 - systime1));
		org.junit.Assert.assertEquals(benchsize, consumed_cnt);
		benchmark_mode = false;
	}

	@Override
	public void producerIndication(Producer<String> p) throws java.io.IOException
	{
		String event;
		while ((event = p.consume()) != null) {
			if (!benchmark_mode) org.junit.Assert.assertEquals(produced_items.get(consumed_cnt), event);
			consumed_cnt++;
		}
		if (benchmark_mode) return;

		if (consumed_cnt == produced_items.size()) {
			// we've finished consuming - just verify that excess calls retrieve null
			String str = p.consume();
			org.junit.Assert.assertEquals(null, str);
			// stop Dispatcher, to allow test to complete
			if (dthrd != null) dsptch.stop();
		}
	}

	private void produce(Producer<String> p) throws java.io.IOException
	{
		java.util.ArrayList<String> items = new java.util.ArrayList<String>();
		items.add(produced_items.get(4));
		items.add(produced_items.get(5));
		p.produce(produced_items.get(0));
		p.produce(new String[]{produced_items.get(1), produced_items.get(2), produced_items.get(3)});
		p.produce(items);
	}

	private void setProducedItems(String phase)
	{
		consumed_cnt = 0;
		produced_items.clear();
		for (int idx = 0; idx != items_to_produce.length; idx++) {
			produced_items.add(phase+"-"+items_to_produce[idx]);
		}
	}
}