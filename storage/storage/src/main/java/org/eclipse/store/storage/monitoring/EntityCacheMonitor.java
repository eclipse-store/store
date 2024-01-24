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

import java.lang.ref.WeakReference;

import org.eclipse.serializer.monitoring.MetricMonitor;
import org.eclipse.store.storage.types.StorageEntityCache;

public class EntityCacheMonitor implements EntityCacheMonitorMBean, MetricMonitor
{
	private final WeakReference<StorageEntityCache.Default> storageEntityCache;
	private int channelIndex;
	
	public EntityCacheMonitor(final StorageEntityCache.Default storageEntityCache)
	{
		super();
		this.storageEntityCache = new WeakReference<>(storageEntityCache);
		this.channelIndex = storageEntityCache.channelIndex();
	}

	@Override
	public int getChannelIndex()
	{
		return this.channelIndex;
	}

	@Override
	public long getLastSweepStart()
	{
		return this.storageEntityCache.get().lastSweepStart();
	}

	@Override
	public long getLastSweepEnd()
	{
		return this.storageEntityCache.get().lastSweepEnd();
	}

	@Override
	public long getEntityCount()
	{
		return this.storageEntityCache.get().entityCount();
	}

	@Override
	public long getUsedCacheSize()
	{
		return this.storageEntityCache.get().cacheSize();
	}

	@Override
	public String getName()
	{
		return "channel=channel-"
			+ this.channelIndex
			+ ",group=Entity cache";
	}

}
