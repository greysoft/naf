/*
 * Copyright 2011-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;

public class ProducerTest
	implements Producer.Consumer
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

		// Launch Dispatched producer in this thread
		// We pass in same Dispatcher, so it should go into synchronous mode
		setProducedItems("A");
		produce(dsptch, prod);
		org.junit.Assert.assertEquals(produced_items.size(), consumed_cnt);

		// Launch Dispatched producer in another thread
		// We will pass in a null Dispatcher to trigger async mode.
		// The thread-join is a synchronising event, hence we can see consumed_cnt
		setProducedItems("B");
		dthrd = dsptch.start();
		produce(null, prod);
		dsptch.waitStopped(); //wait for Dispatcher to stop - we will have told it to once we finish consuming
		dthrd = null;
		org.junit.Assert.assertEquals(produced_items.size(), consumed_cnt);

		int prevcnt = consumed_cnt;
		prod.shutdown(true);
		org.junit.Assert.assertEquals(prevcnt, consumed_cnt);
		prod.shutdown(false);  //make sure duplicate close is ok

		// Repeat test with synchronous-only Producer
		prod = new Producer<String>(String.class, this, dsptch.logger);
		setProducedItems("C");
		produce(dsptch, prod);
		prod.shutdown(false);
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
			p.produce("How fast can you go", null);
		}
		long systime2 = System.currentTimeMillis();
		d.stop(null);
		d.waitStopped();
		p.shutdown(true);
		System.out.println("BulkTest-"+benchsize+" = "+TimeOps.expandMilliTime(systime2 - systime1));
		org.junit.Assert.assertEquals(benchsize, consumed_cnt);
		benchmark_mode = false;
	}

	@Override
	public void producerIndication(Producer<?> p) throws java.io.IOException
	{
		Object event;
		while ((event = p.consume()) != null) {
			String str = String.class.cast(event);
			if (!benchmark_mode) org.junit.Assert.assertEquals(produced_items.get(consumed_cnt), str);
			consumed_cnt++;
		}
		if (benchmark_mode) return;

		if (consumed_cnt == produced_items.size()) {
			// we've finished consuming - just verify that excess calls retrieve null
			String str = (String)p.consume();
			org.junit.Assert.assertEquals(null, str);

			// stop Dispatcher, so that we can use it's thread's termination as the sign that this test is complete
			if (dthrd != null) {
				dsptch.stop(null);
			}
		}
	}

	private void produce(Dispatcher d, Producer<String> p) throws java.io.IOException
	{
		java.util.ArrayList<String> items = new java.util.ArrayList<String>();
		items.add(produced_items.get(4));
		items.add(produced_items.get(5));
		p.produce(produced_items.get(0), d);
		if (dthrd != null) Timer.sleep(100);
		p.produce(new String[]{produced_items.get(1), produced_items.get(2), produced_items.get(3)}, d);
		if (dthrd != null) Timer.sleep(100);
		p.produce(items, d);
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