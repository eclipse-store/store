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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.types.StorageRawFileStatistics.ChannelStatistics;

/*
 * Simple POJO for easy JSON creation of org.eclipse.storage.restadapter.types.ViewerChannelStatistics
 */
public class ViewerChannelStatistics extends ViewerStorageFileStatisticsItem
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	int channelIndex;
	List<ViewerFileStatistics> files;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ViewerChannelStatistics()
	{
		super();
	}

	public ViewerChannelStatistics(
			final int channelIndex,
			final long fileCount,
			final long liveDataLength,
			final long totalDataLength,
			final List<ViewerFileStatistics> files
		)
		{
			super(fileCount, liveDataLength, totalDataLength);
			this.channelIndex = channelIndex;
			this.files = files;
		}

	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	public static ViewerChannelStatistics New(final ChannelStatistics src)
	{
		final List<ViewerFileStatistics > files = new ArrayList<>();

		src.files().forEach( f-> files.add(ViewerFileStatistics.New(f)));

		return new ViewerChannelStatistics(
			src.channelIndex(),
			src.fileCount(),
			src.liveDataLength(),
			src.totalDataLength(),
			files);
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public int getChannelIndex()
	{
		return this.channelIndex;
	}

	public void setChannelIndex(final int channelIndex)
	{
		this.channelIndex = channelIndex;
	}

	public List<ViewerFileStatistics> getFiles()
	{
		return this.files;
	}

	public void setFiles(final List<ViewerFileStatistics> files)
	{
		this.files = files;
	}
}
