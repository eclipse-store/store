package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.Date;
import java.util.HashMap;

import org.eclipse.store.storage.types.StorageRawFileStatistics;

/*
 * Simple POJO for easy JSON creation of org.eclipse.storage.restadapter.types.ViewerStorageRawFileStatistics
 */
public class ViewerStorageFileStatistics extends ViewerStorageFileStatisticsItem
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	Date creationTime;
	HashMap<Integer, ViewerChannelStatistics> channelStatistics;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ViewerStorageFileStatistics()
	{
		super();
	}

	public ViewerStorageFileStatistics(
			final Date creationTime,
			final long fileCount,
			final long liveDataLength,
			final long totalDataLength,
			final HashMap<Integer, ViewerChannelStatistics> channelStatistics)
		{
			super(fileCount, liveDataLength, totalDataLength);
			this.creationTime = creationTime;
			this.channelStatistics = channelStatistics;
		}

	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	public static ViewerStorageFileStatistics New(final StorageRawFileStatistics src)
	{
		final HashMap<Integer, ViewerChannelStatistics> channelStatistics = new HashMap<>();

		src.channelStatistics().forEach(e -> channelStatistics.put(e.key(), ViewerChannelStatistics.New(e.value())));

		return new ViewerStorageFileStatistics(
			src.creationTime(),
			src.fileCount(),
			src.liveDataLength(),
			src.totalDataLength(),
			channelStatistics);
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public Date getCreationTime()
	{
		return this.creationTime;
	}

	public void setCreationTime(final Date creationTime)
	{
		this.creationTime = creationTime;
	}

	public HashMap<Integer, ViewerChannelStatistics> getChannelStatistics()
	{
		return this.channelStatistics;
	}

	public void setChannelStatistics(final HashMap<Integer, ViewerChannelStatistics> channelStatistics)
	{
		this.channelStatistics = channelStatistics;
	}
}
