/*
 * Copyright 2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.InputStream;
import java.io.IOException;

import org.junit.Test;
import org.junit.Assert;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.AdditionalAnswers;

public class FileOpsReadTest {
	@Test
	public void testRead_ZeroAvailableWithLargeBuffer() throws Exception {
		int[] input = new int[] {1, 200, 3, 4, 5, 6, 7, 8}; //including an 8-bit value
		verifyRead(0, 2*input.length, input);
	}

	@Test
	public void testRead_ZeroAvailableWithSmallBuffer() throws Exception {
		int[] input = new int[] {1, 2, 3, 4, 5, 6, 7, 8};
		verifyRead(0, (input.length/2)-1, input);
	}

	@Test
	public void testRead_ZeroAvailableWithPathologicalBuffer() throws Exception {
		int[] input = new int[] {1, 2, 3, 4, 5, 6, 7, 8};
		verifyRead(0, 0, input);
	}

	@Test
	public void testRead_ExcessAvailable() throws Exception {
		int[] input = new int[] {1, 2, 3, 4, 5, 6, 7, 8};
		verifyRead(input.length * 2, 0, input);
	}

	@Test
	public void testRead_ExactAvailable() throws Exception {
		int[] input = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
		verifyRead(input.length, 0, input);
	}

	@Test
	public void testRead_PartialAvailable() throws Exception {
		int[] input = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
		verifyRead(input.length/2, 0, input);
	}

	@Test
	public void testRead_PartialRequest() throws Exception {
		int[] input = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
		verifyRead(input.length/2, 0, 0, input);
	}

	@Test
	public void testRead_ExcessiveRequest() throws Exception {
		int[] input = new int[] {0, 1, 2, 3, 4, 5};
		verifyRead(input.length+4, 0, 0, input);
	}

	@Test
	public void testRead_NoData() throws Exception {
		verifyRead(0, 0, null);
	}

	@Test
	public void testRead_NoDataWithFalseAvailable() throws Exception {
		verifyRead(20, 0, null);
	}

	@Test
	public void testRead_MultipleReads() throws Exception {
		int[] input = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
		verifyRead(0, input.length/3, input);
	}

	@Test
	public void testZeroRead() throws Exception {
		int[] input = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
		ByteArrayRef buf1 = verifyRead(0, input.length, null, input);
		Assert.assertEquals(0, buf1.size());

		buf1 = new ByteArrayRef(input.length);
		ByteArrayRef buf2 = verifyRead(0, input.length, buf1, input);
		Assert.assertTrue(buf2 == buf1);
		Assert.assertEquals(0, buf1.size());
	}

	@Test
	public void testRead_ExistingBufWithOffset() throws Exception {
		verifyExistingContents(true, null);
	}

	@Test
	public void testRead_ExistingBufWithContents() throws Exception {
		verifyExistingContents(false, new byte[] {101, 102, 103});
	}

	@Test
	public void testRead_ExistingBufWithOffsetContents() throws Exception {
		verifyExistingContents(true, new byte[] {101, 102, 103});
	}

	private static void verifyExistingContents(boolean withOffset, byte[] prevcontents) throws IOException {
		int[] input = new int[] {1, 2, 3, 4, 5, 6, 7, 8};
		int off = (withOffset ? 3 : 0);
		byte[] arr = new byte[input.length + off + (prevcontents == null ? 0 : prevcontents.length)];
		Arrays.fill(arr, (byte)99);
		if (prevcontents != null) System.arraycopy(prevcontents, 0, arr, off, prevcontents.length);
		ByteArrayRef bufh = new ByteArrayRef(arr, off, prevcontents == null ? 0 : prevcontents.length);
		byte[] prevbuf = bufh.buffer();
		bufh = verifyRead(input.length, 0, bufh, input);
		Assert.assertTrue(prevbuf == bufh.buffer());
	}

	private static ByteArrayRef verifyRead(int reqlen, int available, ByteArrayRef bufh, int[] input) throws IOException {
		int inputsize = (input == null ? 0 : input.length);
		int consumed = (reqlen == -1 ? inputsize : Math.min(reqlen, inputsize));
		InputStream strm = Mockito.spy(InputStream.class);
		Mockito.when(strm.available()).thenReturn(available);
		Mockito.when(strm.read()).thenAnswer(readAnswer(input));

		byte[] prevbuf = (bufh == null ? null : bufh.buffer());
		int prevsize = (bufh == null ? 0 : bufh.size());
		int prevoff = (bufh == null ? 0 : bufh.offset());
		byte[] prevcontents = null;
		if (prevbuf != null) {
			prevcontents = new byte[prevoff + bufh.size()]; //save pre-offset contents too, to ensure not corrupted
			System.arraycopy(prevbuf, 0, prevcontents, 0, prevcontents.length);
		}

		ByteArrayRef buf1 = bufh;
		bufh = FileOps.read(strm, reqlen, bufh);
		if (bufh == null) {
			Assert.assertEquals(0, reqlen);
			Assert.assertNull(buf1);
		} else {
			Assert.assertEquals(consumed + prevsize, bufh.size());
			for (int idx = 0; idx != consumed; idx++) Assert.assertEquals("index="+idx, input[idx], bufh.byteAt(prevsize+idx));
		}

		if (prevcontents != null) {
			for (int idx = 0; idx != prevcontents.length; idx++) {
				Assert.assertEquals("index="+idx, prevcontents[idx], prevbuf[idx]);
			}
			if (prevsize != 0) {
				for (int idx = 0; idx != prevsize; idx++) Assert.assertEquals("index="+idx, prevcontents[prevoff+idx], bufh.byteAt(idx));
			}
		}
		return bufh;
	}

	private static void verifyRead(int reqlen, int available, int bufsiz, int[] input) throws IOException {
		verifyRead(reqlen, available, new ByteArrayRef(bufsiz), input);
		if (bufsiz == 0) verifyRead(reqlen, available, null, input);
	}

	private static void verifyRead(int available,  int bufsiz, int[] input) throws IOException {
		verifyRead(-1, available, bufsiz, input);
	}

	private static Answer<Integer> readAnswer(int[] input) {
		List<Integer> lst = new ArrayList<>();
		if (input != null) {
			for (int bval : input) lst.add(bval);
		}
		lst.add(-1);
		return AdditionalAnswers.returnsElementsOf(lst);
	}
}