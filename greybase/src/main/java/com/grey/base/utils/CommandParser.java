/*
 * Copyright 2012 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.base.utils;

/*
 * Note that this class does not emulate the well-known getopts facility as it permits long option names (and
 * hence does not allow options to be concatenated). However, it does follow its convention of interpreting a
 * colon-terminated option as one which takes a value.
 */
public final class CommandParser
{
	public abstract static class OptionsHandler
	{
		private final java.util.Set<String> opts_solo = new java.util.HashSet<String>();
		private final java.util.Set<String> opts_withval = new java.util.HashSet<String>();
		private final int min_params;
		private final int max_params;

		public abstract void setOption(String opt);
		public abstract void setOption(String opt, String val);
		public String displayUsage() {return null;}

		public OptionsHandler(String[] opts, int min, int max)
		{
			if (opts != null) {
				for (int idx = 0; idx != opts.length; idx++) {
					if (opts[idx].endsWith(":")) {
						opts_withval.add(opts[idx].substring(0, opts[idx].length() - 1));
					} else {
						opts_solo.add(opts[idx]);
					}
				}
			}
			min_params = min;
			max_params = max;
		}
	}

	private final java.util.List<OptionsHandler> handlers = new java.util.ArrayList<OptionsHandler>();
	public void addHandler(OptionsHandler h) {handlers.add(0, h);}
	private OptionsHandler mainHandler() {return handlers.get(0);}

	public CommandParser(OptionsHandler default_handler)
	{
		addHandler(default_handler);
	}

	public int parse(String[] args)
	{
		return parse(args, 0);
	}

	public int parse(String[] args, int arg)
	{
		int arg0 = arg;
		while (arg < args.length && args[arg].charAt(0) == '-') {
			String opt = args[arg++].substring(1);
			if (opt.length() == 0) break; //special marker to indicate end-of-options
			boolean handled = false;
			int idx = 0;
			do {
				if (idx == handlers.size()) {
					if (opt.equals("h") || opt.equals("help")) {
						usage();
						return -1;
					}
					return fail(args, arg0, "Unrecognised option="+opt+" at pos="+arg);
				}
				OptionsHandler handler = handlers.get(idx++);
				if (handler.opts_solo.contains(opt)) {
					handler.setOption(opt);
					handled = true;
				} else if (handler.opts_withval.contains(opt)) {
					if (arg == args.length) {
						return fail(args, arg0, "Missing value for option="+opt+" at pos="+arg);
					}
					handler.setOption(opt, args[arg++]);
					handled = true;
				}
			} while (!handled);
		}
		// main handler determines if number of parameters is acceptable
		OptionsHandler handler = mainHandler();
		int param_cnt = args.length - arg;
		if (param_cnt < handler.min_params) return fail(args, arg0, "Insufficient params="+param_cnt+" vs min="+handler.min_params);
		if (handler.max_params != -1 && param_cnt > handler.max_params) return fail(args, arg0, "Excess params="+param_cnt+" vs max="+handler.max_params);
		return arg;
	}

	public void usage(String[] args, int arg0, String errmsg)
	{
		System.out.print("\nInvalid parameters="+args.length+":");
		for (int idx = arg0; idx != args.length; idx++) System.out.print(" "+args[idx]);
		System.out.println();
		System.out.println("*** "+errmsg);
		usage();
	}

	public void usage(String[] args, String errmsg)
	{
		usage(args, 0, errmsg);
	}

	private void usage()
	{
		System.out.println("Command-line syntax:");
		String txt = mainHandler().displayUsage();
		if (txt == null) txt = "\tNo help available";
		System.out.println(txt);
	}

	private int fail(String[] args, int arg0, String errmsg)
	{
		usage(args, arg0, errmsg);
		return -1;
	}
}
