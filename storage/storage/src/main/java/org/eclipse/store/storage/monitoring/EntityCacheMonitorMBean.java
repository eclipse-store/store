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
 * JMX MBean definition that provides monitoring and metrics for
 * a {@link StorageEntityCache} instance.
 */
@MonitorDescription("Provides monitoring and metrics data of a StorageEntityCache instance.")
public interface EntityCacheMonitorMBean
{
	/**
	 * Get the channel index.
	 * 
	 * @return the channel index.
	 */
	@MonitorDescription("The channel index")
	public int getChannelIndex();

	/**
	 * Get the timestamp of the last start of a cache sweep.
	 * 
	 *  @return time stamp in ms.
	 */
	@MonitorDescription("Timestamp of the last start of a cache sweep in ms.")
	public long getLastSweepStart();

	/**
	 * Get the timestamp of the last end of a cache sweep.
	 * 
	 *  @return time stamp in ms.
	 */
	@MonitorDescription("Timestamp of the last end of a cache sweep in ms.")
	public long getLastSweepEnd();

	/**
	 * The number of entries in the channels entity cache.
	 * 
	 * @return The number of entries in the channels entity cache.
	 */
	@MonitorDescription("The number of entries in the channels entity cache.")
	public long getEntityCount();

	/**
	 * Get the used cache size in bytes.
	 * 
	 * @return used cache size in bytes.
	 */
	@MonitorDescription("The used cache size in bytes.")
	public long getUsedCacheSize();
}
