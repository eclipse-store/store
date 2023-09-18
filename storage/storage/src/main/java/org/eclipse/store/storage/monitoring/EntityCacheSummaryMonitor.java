package org.eclipse.store.storage.monitoring;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
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
