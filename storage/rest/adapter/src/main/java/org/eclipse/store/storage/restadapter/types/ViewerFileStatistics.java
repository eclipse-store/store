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

import org.eclipse.store.storage.types.StorageRawFileStatistics.FileStatistics;

/*
 * Simple POJO for easy JSON creation of org.eclipse.storage.restadapter.types.ViewerFileStatistics
 */
public class ViewerFileStatistics extends ViewerStorageFileStatisticsItem
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	long fileNumber;
	String file;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ViewerFileStatistics()
	{
		super();
	}

	public ViewerFileStatistics(
		final long fileCount,
		final long liveDataLength,
		final long totalDataLength,
		final long fileNumber,
		final String file)
	{
		super(fileCount, liveDataLength, totalDataLength);
		this.fileNumber = fileNumber;
		this.file = file;
	}

	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	public static ViewerFileStatistics New(final FileStatistics src)
	{
		return new ViewerFileStatistics(
			src.fileCount(),
			src.liveDataLength(),
			src.totalDataLength(),
			src.fileNumber(),
			src.file());
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public long getFileNumber()
	{
		return this.fileNumber;
	}

	public void setFileNumber(final long fileNumber)
	{
		this.fileNumber = fileNumber;
	}

	public String getFile()
	{
		return this.file;
	}

	public void setFile(final String file)
	{
		this.file = file;
	}
}
