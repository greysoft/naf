/*
 * Copyright 2012-2014 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

public class CommandParserTest
{
	public static class Handler extends CommandParser.OptionsHandler
	{
		public String opt1;
		public String opt2;
		public boolean flag1;
		public boolean flag2;

		public Handler(String[] opts, int min, int max) {super(opts, min, max);}

		@Override
		public void setOption(String opt) {
			if (opt.equals("f1")) {
				flag1 = true;
			} else if (opt.equals("g")) {
				flag2 = true;
			} else {
				throw new RuntimeException("Missing case for bool-option="+opt);
			}
		}

		@Override
		public void setOption(String opt, String val) {
			if (opt.equals("option1")) {
				opt1 = val;
			} else if (opt.equals("w")) {
				opt2 = val;
			} else {
				throw new RuntimeException("Missing case for value-option="+opt);
			}
		}
	}

	private static final String[] opts = new String[]{"f1", "g", "option1:", "w:"};

	@org.junit.Test
	public void validCommand()
	{
		String[] args = new String[]{"-bad", "-option1", "val1", "-g", "param1", "param2"};
		Handler handler = new Handler(opts, 2, -1);
		CommandParser parser = new CommandParser(handler, true);
		int param1 = parser.parse(args, 1);
		org.junit.Assert.assertEquals(4, param1);
		org.junit.Assert.assertEquals("param1", args[param1]);
		org.junit.Assert.assertEquals("param2", args[param1+1]);
		org.junit.Assert.assertFalse(handler.flag1);
		org.junit.Assert.assertTrue(handler.flag2);
		org.junit.Assert.assertEquals("val1", handler.opt1);
		org.junit.Assert.assertNull(handler.opt2);

		args = new String[]{"-w", "val2", "-f1"};
		handler = new Handler(opts, 0, 0);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args);
		org.junit.Assert.assertEquals(args.length, param1);
		org.junit.Assert.assertTrue(handler.flag1);
		org.junit.Assert.assertFalse(handler.flag2);
		org.junit.Assert.assertNull(handler.opt1);
		org.junit.Assert.assertEquals("val2", handler.opt2);

		args = new String[]{"param1", "param2"};
		handler = new Handler(null, 0, 2);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args, 0);
		org.junit.Assert.assertEquals(0, param1);
		org.junit.Assert.assertEquals("param1", args[param1]);

		args = new String[]{"-f1", "-", "-arg1", "arg2"};
		handler = new Handler(opts, 0, 2);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args, 0);
		org.junit.Assert.assertEquals(2, param1);
		org.junit.Assert.assertEquals("-arg1", args[param1]);
		org.junit.Assert.assertEquals("arg2", args[param1+1]);
	}

	@org.junit.Test
	public void invalidCommand()
	{
		String[] args = new String[]{"-bad", "-option1", "val1", "-g", "param1", "param2"};
		Handler handler = new Handler(opts, 0, -1);
		CommandParser parser = new CommandParser(handler, true);
		int param1 = parser.parse(args);
		org.junit.Assert.assertEquals(-1, param1);

		args = new String[]{"-g", "-option1"};
		handler = new Handler(opts, 0, -1);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args);
		org.junit.Assert.assertEquals(-1, param1);

		handler = new Handler(null, 0, -1);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args);
		org.junit.Assert.assertEquals(-1, param1);

		args = new String[0];
		handler = new Handler(null, 1, -1);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args);
		org.junit.Assert.assertEquals(-1, param1);

		args = new String[]{"param1"};
		handler = new Handler(null, 0, 0);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args);
		org.junit.Assert.assertEquals(-1, param1);
	}

	@org.junit.Test
	public void help()
	{
		String[] args = new String[]{"-option1", "val1", "-g", "-h", "-f1", "param1"};
		Handler handler = new Handler(opts, 0, -1);
		CommandParser parser = new CommandParser(handler, true);
		int param1 = parser.parse(args);
		org.junit.Assert.assertEquals(-1, param1);

		args = new String[]{"-help"};
		handler = new Handler(null, 0, -1);
		parser = new CommandParser(handler, true);
		param1 = parser.parse(args);
		org.junit.Assert.assertEquals(-1, param1);
	}
}
