/*
 * Copyright 2010-2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class EmailAddressTest
{
	private static final String MBXPART = "mbx";
	private static final String DOMPART = "dom.org";
	private static final String FULLADDR = MBXPART+"@"+DOMPART;

	@org.junit.Test
	public void testEmailAddress() {
		EmailAddress emaddr = new com.grey.base.utils.EmailAddress();
		verifyEmpty(emaddr);
	}

	@org.junit.Test
	public void testEmailAddressCharSequence() {
		EmailAddress emaddr = new com.grey.base.utils.EmailAddress(FULLADDR);
		org.junit.Assert.assertEquals(FULLADDR, emaddr.toString());
		verifyAddress(emaddr, false);
	}

	@org.junit.Test
	public void testDecompose() {
		EmailAddress emaddr = new com.grey.base.utils.EmailAddress(FULLADDR);
		emaddr.decompose();
		verifyAddress(emaddr, true);

		emaddr = new com.grey.base.utils.EmailAddress(MBXPART);
		emaddr.decompose();
		org.junit.Assert.assertEquals(emaddr.mailbox, new com.grey.base.utils.ByteChars(MBXPART));
		org.junit.Assert.assertEquals(0, emaddr.domain.length());
	}

	@org.junit.Test
	public void testReset() {
		EmailAddress emaddr = new com.grey.base.utils.EmailAddress("any_old_address");
		emaddr.reset();
		verifyEmpty(emaddr);
	}

	@org.junit.Test
	public void testSet() {
		EmailAddress emaddr = new com.grey.base.utils.EmailAddress("any_old_address");
		emaddr.set(FULLADDR);
		verifyAddress(emaddr, false);
	}
	
	@org.junit.Test
	public void testParse()
	{
		EmailAddress emaddr = new com.grey.base.utils.EmailAddress();
		com.grey.base.utils.ArrayRef<byte[]> data = setBytes(FULLADDR, 0);
		emaddr.parse(data);
		verifyAddress(emaddr, false);

		emaddr.reset();
		data = setBytes(FULLADDR, 5);
		emaddr.parse(data);
		verifyAddress(emaddr, false);

		emaddr.reset();
		data = setBytes("  "+FULLADDR+"   ", 5);
		emaddr.parse(data);
		verifyAddress(emaddr, false);

		emaddr.reset();
		data = setBytes("  ", 5);
		emaddr.parse(data);
		verifyEmpty(emaddr);

		emaddr.reset();
		data = setBytes("", 5);
		emaddr.parse(data);
		verifyEmpty(emaddr);

		emaddr.reset();
		data = setBytes(" \t "+FULLADDR+".\t ", 5);
		emaddr.parse(data);
		verifyAddress(emaddr, false);

		emaddr.reset();
		data = setBytes("<"+FULLADDR+">", 0);
		emaddr.parse(data);
		verifyAddress(emaddr, false);

		emaddr.reset();
		data = setBytes("  ["+FULLADDR+"]  ", 0);
		emaddr.parse(data);
		verifyAddress(emaddr, false);

		emaddr.reset();
		data = setBytes("<>", 0);
		emaddr.parse(data);
		verifyEmpty(emaddr);

		emaddr.reset();
		data = setBytes("        <>      ", 0);
		emaddr.parse(data);
		verifyEmpty(emaddr);

		emaddr.reset();
		int addrlen = emaddr.full.ar_buf.length + 5;
		StringBuilder strbuf = new StringBuilder(addrlen + 10);
		strbuf.append(FULLADDR).append('.');
		while (strbuf.length() < addrlen) strbuf.append('a');
		String addrstr = strbuf.toString();
		data = setBytes(addrstr, 5);
		emaddr.parse(data);
		org.junit.Assert.assertEquals(addrstr, emaddr.full.toString());

		emaddr.reset();
		data = setBytes(FULLADDR.toUpperCase(), 5);
		emaddr.parse(data);
		verifyAddress(emaddr, false);

		emaddr.reset();
		addrstr = "<randomtext]";
		data = setBytes("  "+addrstr+"  ", 5);
		emaddr.parse(data);
		org.junit.Assert.assertEquals(addrstr, emaddr.full.toString());

		emaddr.reset();
		addrstr = "[randomtext>";
		data = setBytes(addrstr, 0);
		emaddr.parse(data);
		org.junit.Assert.assertEquals(addrstr, emaddr.full.toString());
	}

	private void verifyEmpty(EmailAddress emaddr) {
		org.junit.Assert.assertEquals(0, emaddr.full.length());
		org.junit.Assert.assertEquals(0, emaddr.mailbox.length());
		org.junit.Assert.assertEquals(0, emaddr.domain.length());
	}

	private void verifyAddress(EmailAddress emaddr, boolean decomposed) {
		org.junit.Assert.assertEquals(emaddr.full, new com.grey.base.utils.ByteChars(FULLADDR));
		if (decomposed) {
			org.junit.Assert.assertEquals(emaddr.mailbox, new com.grey.base.utils.ByteChars(MBXPART));
			org.junit.Assert.assertEquals(emaddr.domain, new com.grey.base.utils.ByteChars(DOMPART));
		} else {
			org.junit.Assert.assertEquals(0, emaddr.mailbox.length());
			org.junit.Assert.assertEquals(0, emaddr.domain.length());
		}
	}
	
	private com.grey.base.utils.ArrayRef<byte[]> setBytes(String txt, int off)
	{
		int txtlen = txt.length();
		byte[] arr = new byte[txtlen + off];
		for (int idx = 0; idx != txtlen; idx++) arr[off + idx] = (byte)txt.charAt(idx);
		return new ArrayRef<byte[]>(arr, off, txtlen, false);
	}
}
