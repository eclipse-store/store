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
import org.eclipse.store.storage.types.StorageChannelHousekeepingResult;

public class StorageChannelHousekeepingMonitor implements StorageChannelHousekeepingMonitorMBean, MetricMonitor
{
	private final int channelIndex;
	private StorageChannelHousekeepingResult fileCleanupCheckResult;
	private StorageChannelHousekeepingResult garbageCollectionResult;
	private StorageChannelHousekeepingResult entityCacheCheckResult;


	public StorageChannelHousekeepingMonitor(final int channelIndex)
	{
		this.channelIndex = channelIndex;
	}

	@Override
	public String getName()
	{
		return "channel=channel-"
				+ this.channelIndex
				+ ",group=housekeeping";
	}

	public void setFileCleanupCheckResult(final StorageChannelHousekeepingResult fileCleanupCheckResult)
	{
		this.fileCleanupCheckResult = fileCleanupCheckResult;
	}

	public void setGarbageCollectionResult(final StorageChannelHousekeepingResult garbageCollectionResult)
	{
		this.garbageCollectionResult = garbageCollectionResult;
	}

	public void setEntityCacheCheckResult(final StorageChannelHousekeepingResult entityCacheCheckResult)
	{
		this.entityCacheCheckResult = entityCacheCheckResult;
	}
	
	@Override
	public long getFileCleanupCheckDuration()
	{
		return this.fileCleanupCheckResult.getDuration();
	}
	
	@Override
	public long getFileCleanupCheckStartTime()
	{
		return this.fileCleanupCheckResult.getStartTime();
	}
	
	@Override
	public boolean getFileCleanupCheckResult()
	{
		return this.fileCleanupCheckResult.getResult();
	}
	
	@Override
	public long getFileCleanupCheckBudget()
	{
		return this.fileCleanupCheckResult.getBudget();
	}
	
	@Override
	public long getGarbageCollectionDuration()
	{
		return this.garbageCollectionResult.getDuration();
	}
	
	@Override
	public long getGarbageCollectionStartTime()
	{
		return this.garbageCollectionResult.getStartTime();
	}
	
	@Override
	public boolean getGarbageCollectionResult()
	{
		return this.garbageCollectionResult.getResult();
	}
	
	@Override
	public long getGarbageCollectionBudget()
	{
		return this.garbageCollectionResult.getBudget();
	}
	
	@Override
	public long getEntityCacheCheckDuration()
	{
		return this.entityCacheCheckResult.getDuration();
	}
	
	@Override
	public long getEntityCacheCheckStartTime()
	{
		return this.entityCacheCheckResult.getStartTime();
	}
	
	@Override
	public boolean getEntityCacheCheckResult()
	{
		return this.entityCacheCheckResult.getResult();
	}
	
	@Override
	public long getEntityCacheCheckBudget()
	{
		return this.entityCacheCheckResult.getBudget();
	}

}
