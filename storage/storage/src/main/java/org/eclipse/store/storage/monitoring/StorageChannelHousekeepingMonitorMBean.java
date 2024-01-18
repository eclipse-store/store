package org.eclipse.store.storage.monitoring;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.monitoring.MonitorDescription;
import org.eclipse.store.storage.types.StorageChannel;

/**
 * JMX MBean definition that provides monitoring and metrics of
 * the {@link StorageChannel}.
 */
@MonitorDescription("Provides monitoring and metrics data of a storage channel.")
public interface StorageChannelHousekeepingMonitorMBean
{
	/**
	 * Get the result of the last housekeeping cache check cycle.
	 * 
	 * @return True if cache is empty.
	 */
	@MonitorDescription("Result of the last housekeeping cache check.")
	boolean getEntityCacheCheckResult();

	/**
	 * Get starting time of the last housekeeping cache check cycle.
	 * 
	 * @return Time in ms since 1970.
	 */
	@MonitorDescription("Starting time of the last housekeeping cache check in ms since 1970.")
	long getEntityCacheCheckStartTime();

	/**
	 * Get the duration of the last housekeeping cache check cycle in ns.
	 * 
	 * @return Duration of the last housekeeping cache check cycle in ns.
	 */
	@MonitorDescription("Duration of the last housekeeping cache check cycle in ns")
	long getEntityCacheCheckDuration();
	
	/**
	 * Get the time budget of the last housekeeping cache check cycle in ns.
	 * 
	 * @return Time budget of the last housekeeping cache check cycle in ns.
	 */
	@MonitorDescription("Time budget of the last housekeeping cache check cycle in ns.")
	long getEntityCacheCheckBudget();

	/**
	 * Get the result of the last housekeeping garbage collection cycle.
	 * 
	 * @return True if completed in time budget.
	 */
	@MonitorDescription("Result of the last housekeeping garbage collection cycle.")
	boolean getGarbageCollectionResult();

	/**
	 * Get starting time of the last housekeeping garbage collection cycle.
	 * 
	 * @return Time in ms since 1970.
	 */
	@MonitorDescription("Starting time of the last housekeeping garbage collection cycle in ms since 1970.")
	long getGarbageCollectionStartTime();

	/**
	 * Get the duration of the last housekeeping garbage collection cycle in ns.
	 * 
	 * @return Duration of the last housekeeping garbage collection cycle in ns.
	 */
	@MonitorDescription("Duration of the last housekeeping garbage collection cycle in ns")
	long getGarbageCollectionDuration();

	/**
	 * Get the time budget of the last housekeeping garbage collection cycle in ns.
	 * 
	 * @return Time budget of the last housekeeping garbage collection cycle in ns.
	 */
	@MonitorDescription("Time budget of the last housekeeping garbage collection cycle in ns.")
	long getGarbageCollectionBudget();
	
	/**
	 * Get the result of the last housekeeping file cleanup cycle.
	 * 
	 * @return True if completed in time budget.
	 */
	@MonitorDescription("Result of the last housekeeping file cleanup cycle.")
	boolean getFileCleanupCheckResult();

	/**
	 * Get starting time of the last housekeeping file cleanup cycle.
	 * 
	 * @return Time in ms since 1970.
	 */
	@MonitorDescription("Starting time of the last housekeeping file cleanup cycle in ms since 1970.")
	long getFileCleanupCheckStartTime();

	/**
	 * Get the duration of the last housekeeping file cleanup cycle in ns.
	 * 
	 * @return Duration of the last housekeeping file cleanup cycle in ns.
	 */
	@MonitorDescription("Duration of the last housekeeping file cleanup cycle in ns")
	long getFileCleanupCheckDuration();
	
	/**
	 * Get the time budget of the last housekeeping file cleanup cycle in ns.
	 * 
	 * @return Time budget of the last housekeeping file cleanup cycle in ns.
	 */
	@MonitorDescription("Time budget of the last housekeeping garbage collection cycle in ns.")
	long getFileCleanupCheckBudget();

}
