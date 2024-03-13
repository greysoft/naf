/*
 * Copyright 2024 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf;

public interface EventListenerNAF {
	String EVENTID_ENTITY_STOPPED = "EventListenerNAF_EntityStopped";
	
	void eventIndication(String eventId, Object eventSource, Object eventData);
}
