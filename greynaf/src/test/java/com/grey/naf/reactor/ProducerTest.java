/*
 * Copyright 2011-2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.naf.TestUtils;

public class ProducerTest
	implements Producer.Consumer<String>
{
	private static final String rootdir = TestUtils.initPaths(ProducerTest.class);
	private static final ApplicationContextNAF appctx = TestUtils.createApplicationContext("ProducerTest", true);

	private static final com.grey.logging.Logger logger = com.grey.logging.Factory.getLoggerNoEx("");
	private static final String[] items_to_produce = new String[]{"Item1", "Item2a", "Item2b", "Item2c", "Item3a", "Item3b"};
	private java.util.ArrayList<String> produced_items = new java.util.ArrayList<String>();
	private int produced_cnt;
	private int consumed_cnt;
	private boolean benchmark_mode;

	@org.junit.Test
	public void workflow()
			throws java.io.IOException, InterruptedException
	{
		FileOps.deleteDirectory(rootdir);
		com.grey.naf.reactor.config.DispatcherConfig def = new com.grey.naf.reactor.config.DispatcherConfig.Builder()
				.withName("producertest-workflow")
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, logger);
		Producer<String> prod = new Producer<>("utest-workflow", dsptch, this);
		dsptch.loadRunnable(prod);
		setProducedItems();
		produced_cnt = produced_items.size();
		dsptch.start();
		produce(prod);
		//wait for Dispatcher to stop - we will have told it to once we finish consuming
		// The thread-join is a synchronising event, hence we can see consumed_cnt
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		org.junit.Assert.assertEquals(produced_items.size(), consumed_cnt);

		int prevcnt = consumed_cnt;
		prod.shutdown(true);
		org.junit.Assert.assertEquals(prevcnt, consumed_cnt);
		prod.stopDispatcherRunnable();  //make sure duplicate close is ok
	}

	// This runs in a very reasonable time, but larger values can be attempted for interactive testing
	@org.junit.Test
	public void bulktest()
			throws java.io.IOException, InterruptedException
	{
		FileOps.deleteDirectory(rootdir);
		produced_cnt = 1_000_000;
		benchmark_mode = true;
		consumed_cnt = 0;
		com.grey.naf.reactor.config.DispatcherConfig def = new com.grey.naf.reactor.config.DispatcherConfig.Builder()
				.withName("producertest-bulk")
				.withSurviveHandlers(false)
				.build();
		Dispatcher dsptch = Dispatcher.create(appctx, def, logger);
		Producer<String> p = new Producer<>("utest-bulk", dsptch, this);
		dsptch.loadRunnable(p);
		dsptch.start();
		long systime1 = System.currentTimeMillis();
		for (int idx = 0; idx != produced_cnt; idx++) {
			p.produce("How fast can you go");
		}
		long systime2 = System.currentTimeMillis();
		// The thread-join is a synchronising event, hence we can see consumed_cnt
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		p.shutdown(true);
		System.out.println("BulkTest-"+produced_cnt+" = "+TimeOps.expandMilliTime(systime2 - systime1));
		org.junit.Assert.assertEquals(produced_cnt, consumed_cnt);
	}

	@Override
	public void producerIndication(Producer<String> p) throws java.io.IOException
	{
		String event;
		while ((event = p.consume()) != null) {
			if (!benchmark_mode) org.junit.Assert.assertEquals(produced_items.get(consumed_cnt), event);
			consumed_cnt++;
		}

		if (consumed_cnt == produced_cnt) {
			// we've finished consuming - just verify that excess calls retrieve null
			String str = p.consume();
			org.junit.Assert.assertEquals(null, str);
			// stop Dispatcher, to allow test to complete
			p.getDispatcher().stop();
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

	private void setProducedItems()
	{
		consumed_cnt = 0;
		produced_items.clear();
		for (int idx = 0; idx != items_to_produce.length; idx++) {
			produced_items.add("item-"+items_to_produce[idx]);
		}
	}
}