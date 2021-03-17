/*
 * Copyright 2021 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.reactor;

public interface DispatcherRunnable {
	String getName();
	Dispatcher getDispatcher();
	default void startDispatcherRunnable() throws java.io.IOException {}
	default boolean stopDispatcherRunnable() {return true;}
}
