package com.grey.base.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/*
 * Copyright 2010-2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
public class EnvExpressionTest {
	@Before
	public void setup() {
		SysProps.clearAppEnv();
	}

	@Test
	public void testLiteral() {
		String s = EnvExpression.eval("ABC");
		Assert.assertEquals("ABC", s);
	}

	@Test
	public void testEnvWithoutDefault() {
		SysProps.setAppEnv("KEY1", "vaL1");
		String s = EnvExpression.eval("${KEY1}");
		Assert.assertEquals("vaL1", s);
	}

	@Test
	public void testMissingEnvWithoutDefault() {
		String s = EnvExpression.eval("${KEY1}");
		Assert.assertNull(s);

		SysProps.setAppEnv("KEY1", "va1");
		s = EnvExpression.eval("${key1}");
		Assert.assertNull(s);
	}

	@Test
	public void testEnvWithDefault() {
		SysProps.setAppEnv("KEY1", "val1");
		String s = EnvExpression.eval("${KEY1:dflt1}");
		Assert.assertEquals("val1", s);
	}

	@Test
	public void testMissingEnvWithDefault() {
		String s = EnvExpression.eval("${KEY1:dflT1}");
		Assert.assertEquals("dflT1", s);
	}

	@Test
	public void testMissingEnvWithBlankDefault() {
		String s = EnvExpression.eval("${KEY1:}");
		Assert.assertNull(s);
	}

	@Test
	public void testLiteralOpen() {
		String s = EnvExpression.eval("${$}");
		Assert.assertEquals("${", s);
	}

	@Test
	public void testConcatenation() {
		SysProps.setAppEnv("KEY1", "one");
		String expr = "ABC${KEY1}DEF${KEY2:two}GHI${KEY3}JKL${$}MNO$PQR";
		String s = EnvExpression.eval(expr);
		Assert.assertEquals("ABConeDEFtwoGHIJKL${MNO$PQR", s);
	}

	@Test
	public void testBareDollar() {
		String expr = "$ABC$XYZ$";
		String s = EnvExpression.eval(expr);
		Assert.assertEquals("$ABC$XYZ$", s);
	}

	@Test
	public void testBareBraces() {
		String expr = "}ABC{XYZ{";
		String s = EnvExpression.eval(expr);
		Assert.assertEquals("}ABC{XYZ{", s);
	}

	@Test
	public void testEmpty() {
		String s = EnvExpression.eval("");
		Assert.assertEquals("", s);
	}

	@Test
	public void testUnclosedExpression() {
		String expr = "${ABC";
		try {
			String s = EnvExpression.eval(expr);
			Assert.fail("Expected to fail on unclosed env term at start - "+expr+" => "+s);
		} catch (IllegalArgumentException ex) {}

		expr = "ABC${XYZ";
		try {
			String s = EnvExpression.eval(expr);
			Assert.fail("Expected to fail on unclosed env term in middle - "+expr+" => "+s);
		} catch (IllegalArgumentException ex) {}

		expr = "ABC${";
		try {
			String s = EnvExpression.eval(expr);
			Assert.fail("Expected to fail on unclosed env term at end - "+expr+" => "+s);
		} catch (IllegalArgumentException ex) {}
	}
}
