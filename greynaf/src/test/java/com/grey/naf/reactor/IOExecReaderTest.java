/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

import java.nio.ByteBuffer;

import com.grey.base.utils.ByteArrayRef;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.TimeOps;
import com.grey.naf.ApplicationContextNAF;
import com.grey.base.utils.NIOBuffers;

import org.mockito.Mockito;

public class IOExecReaderTest
{
	private static final String rootdir = DispatcherTest.initPaths(IOExecReaderTest.class);
	private static final ApplicationContextNAF appctx = ApplicationContextNAF.create("IORtest");

	private static class CMR
		extends CM_Stream
	{
		private final com.grey.naf.BufferSpec bufspec;
		private final java.nio.channels.WritableByteChannel wchan;
		private final com.grey.base.utils.ByteChars bc = new com.grey.base.utils.ByteChars();
		private final StringBuilder sb = new StringBuilder();
		private int phase;
		public boolean completed;
		private String expect;
		private String writedata;

		public CMR(Dispatcher d, java.nio.channels.SelectableChannel r, java.nio.channels.WritableByteChannel w,
					com.grey.naf.BufferSpec bufspec)
				throws java.io.IOException {
			super(d, bufspec, null);
			wchan = w;
			this.bufspec = bufspec;
			registerConnectedChannel(r, true);
			getReader().receive(0);
		}

		public void write(CharSequence data) throws java.io.IOException {
			java.nio.ByteBuffer niobuf = NIOBuffers.encode(data, null, bufspec.directbufs);
			int nbytes = wchan.write(niobuf);
			org.junit.Assert.assertEquals(data.length(), nbytes);
			expect = data.toString();
		}

		@Override
		public void ioReceived(ByteArrayRef rcvdata) throws java.io.IOException
		{
			bc.populate(rcvdata.buffer(), rcvdata.offset(), rcvdata.size());
			org.junit.Assert.assertTrue("expect="+expect+" vs "+bc, com.grey.base.utils.StringOps.sameSeq(expect, bc));
			char[] carr;

			switch (phase++)
			{
			case 0:
				// send enough to exactly fill the receive buffer
				carr = new char[bufspec.rcvbufsiz];
				java.util.Arrays.fill(carr, 'z');
				sb.setLength(0);
				sb.append(carr);
				write(sb);
				break;
			case 1:
				// send one byte more than receive buffer can hold - will take us two callbacks to receive this
				String prev = expect;
				sb.append("a");
				write(sb);
				expect = prev;
				break;
			case 2:
				expect = "a"; //what we wrote in phase 1
				break;
			case 3:
				//switch to a delimited read and do a send that will take two callbacks to receive
				getReader().receiveDelimited((byte)'\n');
				writedata = "abcde\n123456789\n";
				write(writedata);
				expect = writedata.substring(0,  6);
				org.junit.Assert.assertEquals('\n', expect.charAt(expect.length()-1)); //sanity check
				break;
			case 4:
				// we've received first line, so the second one should be next
				expect = writedata.substring(6);
				org.junit.Assert.assertEquals('\n', expect.charAt(expect.length()-1)); //sanity check
				break;
			case 5:
				//switch to a fixed-size read - do a send that will take more than 3 reads to consume
				getReader().receive(10);
				writedata = "abcdefghij0123456789ABCDEFGHIJxyz";
				org.junit.Assert.assertEquals(33, writedata.length()); //sanity check
				write(writedata);
				expect = writedata.substring(0,  10);
				break;
			case 6:
				expect = writedata.substring(10, 20);
				org.junit.Assert.assertEquals("0123456789", expect); //sanity check
				break;
			case 7:
				// exercises the compaction code path
				expect = writedata.substring(20, 30);
				break;
			case 8:
				// discard the leftover bytes from previous write and verify a fresh send works
				int nbytes = getReader().flush();
				org.junit.Assert.assertEquals(writedata.length()-30, nbytes); //sanity check
				writedata = "AlphaOmega";
				write(writedata);
				break;
			case 9:
				// exercise the "deadlock" code path
				getReader().receiveDelimited((byte)'\n');
				carr = new char[bufspec.rcvbufsiz];
				java.util.Arrays.fill(carr, 'z');
				sb.setLength(0);
				sb.append(carr).append("a\n");
				write(sb);
				expect = sb.substring(0, bufspec.rcvbufsiz);
				break;
			case 10:
				// we've received partial line due to buffer-full deadlock - rest of it will come in next callback
				expect = "a\n";
				break;
			case 11:
				synchronized (this) {
					completed = true;
				}
				getReader().endReceive();
				disconnect();
				disconnect();//make sure twice is safe
				getDispatcher().stop();
				break;
			default:
				throw new RuntimeException("Missing case for phase="+phase);
			}
		}
	}

	@org.junit.Test
	public void testDirectBuffer() throws java.io.IOException
	{
		launch(new com.grey.naf.BufferSpec(25, 0, true));
	}

	@org.junit.Test
	public void testHeapBuffer() throws java.io.IOException
	{
		launch(new com.grey.naf.BufferSpec(25, 0, false));
	}

	@org.junit.Test
	public void testOffsetBuffer() throws java.io.IOException
	{
		int offset = 5;
		com.grey.naf.BufferSpec bufspec = new com.grey.naf.BufferSpec(25, 0, false);
		bufspec = Mockito.spy(bufspec);
		Mockito.when(bufspec.createReadBuffer()).thenReturn(((ByteBuffer)NIOBuffers.create(bufspec.rcvbufsiz+offset, bufspec.directbufs).position(offset)).slice());
		launch(bufspec);
	}

	private void launch(com.grey.naf.BufferSpec bufspec) throws java.io.IOException
	{
		FileOps.deleteDirectory(rootdir);
		com.grey.naf.DispatcherDef def = new com.grey.naf.DispatcherDef();
		def.surviveHandlers = false;
		Dispatcher dsptch = Dispatcher.create(appctx, def, com.grey.logging.Factory.getLogger("no-such-logger"));
		java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
		java.nio.channels.Pipe.SourceChannel rep = pipe.source();
		java.nio.channels.Pipe.SinkChannel wep = pipe.sink();
		CMR cm = new CMR(dsptch, rep, wep, bufspec);
		org.junit.Assert.assertTrue(cm.isConnected());
		cm.write("abcdexyz1234567890");  //has to be shorter than CMR.rcvcap
		dsptch.start();
		// make sure the Dispatcher didn't bomb out on an error
		Dispatcher.STOPSTATUS stopsts = dsptch.waitStopped(TimeOps.MSECS_PER_SECOND * 10, true);
		org.junit.Assert.assertEquals(Dispatcher.STOPSTATUS.STOPPED, stopsts);
		org.junit.Assert.assertTrue(dsptch.completedOK());
		synchronized (cm) {
			org.junit.Assert.assertTrue(cm.completed);
		}
		wep.close();
		org.junit.Assert.assertFalse(cm.isConnected());
	}
}