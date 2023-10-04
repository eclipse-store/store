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

import org.eclipse.store.storage.types.StorageRawFileStatisticsItem;

/*
 * Simple POJO for easy JSON creation org.eclipse.storage.restadapter.types.ViewerStorageRawFileStatisticsItem
 */
public class ViewerStorageFileStatisticsItem
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	long fileCount;
	long liveDataLength;
	long totalDataLength;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ViewerStorageFileStatisticsItem()
	{
		super();
	}

	public ViewerStorageFileStatisticsItem(
		final long fileCount,
		final long liveDataLength,
		final long totalDataLength)
	{
		super();
		this.fileCount = fileCount;
		this.liveDataLength = liveDataLength;
		this.totalDataLength = totalDataLength;
	}

	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	public static ViewerStorageFileStatisticsItem New(final StorageRawFileStatisticsItem src)
	{
		return new ViewerStorageFileStatisticsItem(
			src.fileCount(),
			src.liveDataLength(),
			src.totalDataLength());
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public long getFileCount()
	{
		return this.fileCount;
	}

	public void setFileCount(final long fileCount)
	{
		this.fileCount = fileCount;
	}

	public long getLiveDataLength()
	{
		return this.liveDataLength;
	}

	public void setLiveDataLength(final long liveDataLength)
	{
		this.liveDataLength = liveDataLength;
	}

	public long getTotalDataLength()
	{
		return this.totalDataLength;
	}

	public void setTotalDataLength(final long totalDataLength)
	{
		this.totalDataLength = totalDataLength;
	}
}
