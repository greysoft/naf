/*
 * Copyright 2012-2018 Yusef Badri - All rights reserved.
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
		final int min_params;
		final int max_params;

		public void setOption(String opt) {throw new RuntimeException("Missing handler for bool-option="+opt);}
		public void setOption(String opt, String val) {throw new RuntimeException("Missing handler for option="+opt+"="+val);}
		public String displayUsage() {return null;}

		boolean containsSoloOption(String opt) {return opts_solo.contains(opt);}
		boolean containsValueOption(String opt) {return opts_withval.contains(opt);}

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
	private final boolean silent;

	public void addHandler(OptionsHandler h) {handlers.add(0, h);}
	private OptionsHandler mainHandler() {return handlers.get(0);}
	
	public CommandParser(OptionsHandler default_handler) {this(default_handler, false);}

	public CommandParser(OptionsHandler default_handler, boolean silent)
	{
		this.silent = silent;
		addHandler(default_handler);
	}

	public int parse(String[] args)
	{
		return parse(args, 0);
	}

	// returns index of first param (ie. first non-options arg)
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
						if (!silent) System.out.print(usage());
						return -1;
					}
					return fail(args, arg0, "Unrecognised option="+opt+" at arg="+arg);
				}
				OptionsHandler handler = handlers.get(idx++);
				if (handler.containsSoloOption(opt)) {
					handler.setOption(opt);
					handled = true;
				} else if (handler.containsValueOption(opt)) {
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

	public String usage(String[] args, int arg0, String errmsg)
	{
		String txt = "\nInvalid parameters="+args.length+":\n";
		for (int idx = arg0; idx != args.length; idx++) txt += " "+args[idx];
		txt += "\n*** "+errmsg+"\n";
		txt += usage();
		if (!silent) System.out.print(txt);
		return txt;
	}

	public String usage(String[] args, String errmsg)
	{
		return usage(args, 0, errmsg);
	}

	private String usage()
	{
		String txt = "Command-line syntax:\n";
		String txt2 = mainHandler().displayUsage();
		if (txt2 == null) txt2 = "\tNo help available";
		txt += txt2+"\n";
		return txt;
	}

	private int fail(String[] args, int arg0, String errmsg)
	{
		usage(args, arg0, errmsg);
		return -1;
	}
}
