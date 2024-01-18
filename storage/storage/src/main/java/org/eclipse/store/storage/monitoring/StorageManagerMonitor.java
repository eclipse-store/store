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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.monitoring.MetricMonitor;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.types.StorageManager;
import org.eclipse.store.storage.types.StorageRawFileStatistics;

public class StorageManagerMonitor implements StorageManagerMonitorMXBean, MetricMonitor
{

	private final WeakReference<StorageManager> storageManager;
	

	public static class FileStatistics
	{

		private final String fileName;
		private final long totalDataLength;
		private final long liveDataLength;

		public FileStatistics(final org.eclipse.store.storage.types.StorageRawFileStatistics.FileStatistics fileStatistics)
		{
			this.fileName = fileStatistics.file();
			this.totalDataLength = fileStatistics.totalDataLength();
			this.liveDataLength = fileStatistics.liveDataLength();
		}

		public String getFileName()
		{
			return this.fileName;
		}
		
		public long getTotalDataLength()
		{
			return this.totalDataLength;
		}

		public long getLiveDataLength()
		{
			return this.liveDataLength;
		}
		
	}
	
	public static class ChannelStatistics
	{

		private final long fileCount;
		private final long totalDataLength;
		private final long liveDataLength;
		
		private final List<FileStatistics> fileStatistics = new ArrayList<>();

		public ChannelStatistics(
			final KeyValue<Integer, ? extends org.eclipse.store.storage.types.StorageRawFileStatistics.ChannelStatistics> channelStatistics)
		{
	
			this.fileCount = channelStatistics.value().fileCount();
			this.totalDataLength = channelStatistics.value().totalDataLength();
			this.liveDataLength = channelStatistics.value().liveDataLength();
			
			channelStatistics.value().files().forEach(f -> this.fileStatistics.add(new FileStatistics(f)));
		}

		public long getFileCount()
		{
			return this.fileCount;
		}

		public long getTotalDataLength()
		{
			return this.totalDataLength;
		}

		public long getLiveDataLength()
		{
			return this.liveDataLength;
		}
		
		public List<FileStatistics> getFileStatistics()
		{
			return this.fileStatistics;
		}
	}
	
	public static class StorageStatistics
	{

		private final int channelCount;
		private final long fileCount;
		private final long totalDataLength;
		private final long liveDataLength;
		
		private final List<ChannelStatistics> channelStatistics = new ArrayList<>();
		
		public StorageStatistics(final StorageRawFileStatistics statistics)
		{
			this.channelCount = statistics.channelCount();
			this.fileCount = statistics.fileCount();
			this.totalDataLength = statistics.totalDataLength();
			this.liveDataLength = statistics.liveDataLength();
			
			statistics.channelStatistics().forEach( c-> this.channelStatistics.add(new ChannelStatistics(c)));
		}

		public int getChannelCount()
		{
			return this.channelCount;
		}

		public long getFileCount()
		{
			return this.fileCount;
		}

		public long getTotalDataLength()
		{
			return this.totalDataLength;
		}

		public long getLiveDataLength()
		{
			return this.liveDataLength;
		}

		public double getUsageRatio()
		{
			return  ((double)this.liveDataLength / (double)this.totalDataLength);
		}
		
		public List<ChannelStatistics> getChannelStatistics()
		{
			return this.channelStatistics;
		}
		
	}
	
	public StorageManagerMonitor(final StorageManager storageConnection)
	{
		this.storageManager = new WeakReference<>(storageConnection);
	}

	@Override
	public void issueFullGarbageCollection()
	{
		this.storageManager.get().issueFullGarbageCollection();
	}
	
	@Override
	public void issueFullCacheCheck()
	{
		this.storageManager.get().issueFullCacheCheck();
	}
	
	@Override
	public void issueFullFileCheck()
	{
		this.storageManager.get().issueFullFileCheck();
	}
		
	@Override
	public StorageStatistics getStorageStatistics()
	{
		return new StorageStatistics(this.storageManager.get().createStorageStatistics());
	}
	
	@Override
	public String getName()
	{
		return "name=EmbeddedStorage";
	}

}
