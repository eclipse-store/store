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

import org.eclipse.serializer.monitoring.MetricMonitor;

public class EntityCacheSummaryMonitor implements EntityCacheSummaryMonitorMBean, MetricMonitor
{
	private final EntityCacheMonitor[] cacheMonitors;

	public EntityCacheSummaryMonitor(final EntityCacheMonitor[] cacheMonitors)
	{
		this.cacheMonitors = cacheMonitors;
	}

	@Override
	public String getName()
	{
		return "name=EntityCacheSummary";
	}
	
	@Override
	public synchronized long getEntityCount()
	{
		int entityCount = 0;
		for (final EntityCacheMonitor entityCacheMonitor : this.cacheMonitors)
		{
			entityCount += entityCacheMonitor.getEntityCount();
		}
		return entityCount;
		
	}

	@Override
	public synchronized long getUsedCacheSize()
	{
		int usedCacheSize = 0;
		for (final EntityCacheMonitor entityCacheMonitor : this.cacheMonitors)
		{
			usedCacheSize += entityCacheMonitor.getUsedCacheSize();
		}
		return usedCacheSize;
	}

}
