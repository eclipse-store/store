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

import org.eclipse.store.storage.monitoring.StorageManagerMonitor.StorageStatistics;
import org.eclipse.store.storage.types.StorageManager;

/**
 * JMX MBean definition that provides monitoring and metrics for
 * a {@link StorageManager} instance.
 */
@MonitorDescription("Provides storage statistics and house keeping operations")
public interface StorageManagerMonitorMXBean
{
	/**
	 * Get storage statistics.
	 * 
	 * @return storage statistics.
	 */
	@MonitorDescription("query the storage for storage statistics. "
			+ "This will block storage operations until it is completed.")
	StorageStatistics getStorageStatistics();
	
	/**
	 * Issue a full storage garbage collection.
	 */
	@MonitorDescription("issue a full storage garbage collection run. "
			+ "This will block storage operations until it is completed.")
	
	void issueFullGarbageCollection();

	/**
	 * Issue a full storage file check.
	 */
	@MonitorDescription("issue a full storage file check run. "
			+ "This will block storage operations until it is completed.")
	void issueFullFileCheck();

	/**
	 * Issue a full storage cache check.
	 */
	@MonitorDescription("issue a full storage chache check run. "
			+ "This will block storage operations until it is completed.")
	void issueFullCacheCheck();
}
