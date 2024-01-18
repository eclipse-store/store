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
import org.eclipse.store.storage.types.StorageEntityCache;

/**
 * JMX MBean definition that provides a summary of monitoring and metrics for
 * all {@link StorageEntityCache} instances of a storage.
 */
@MonitorDescription("Provides a summary of all storage channels entity caches.")
public interface EntityCacheSummaryMonitorMBean
{
	/**
	 * Get the aggregated used cache size from all channel entity caches in bytes.
	 * 
	 * @return used cache size in bytes.
	 */
	@MonitorDescription("The total size of all channel entity caches in bytes.")
	long getUsedCacheSize();

	/**
	 * The number of entries aggregated from all channel entity caches.
	 * 
	 * @return The number of entries.
	 */
	@MonitorDescription("The number of entries aggregated from all channel entity caches.")
	long getEntityCount();
}
