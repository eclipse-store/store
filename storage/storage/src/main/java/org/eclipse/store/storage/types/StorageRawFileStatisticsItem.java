package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

public interface StorageRawFileStatisticsItem
{
	public long fileCount();

	public long liveDataLength();

	public long totalDataLength();



	public abstract class Abstract implements StorageRawFileStatisticsItem
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final long fileCount      ;
		final long liveDataLength ;
		final long totalDataLength;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Abstract(final long fileCount, final long liveDataLength, final long totalDataLength)
		{
			super();
			this.fileCount       = fileCount      ;
			this.liveDataLength  = liveDataLength ;
			this.totalDataLength = totalDataLength;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final long fileCount()
		{
			return this.fileCount;
		}

		@Override
		public final long liveDataLength()
		{
			return this.liveDataLength;
		}

		@Override
		public final long totalDataLength()
		{
			return this.totalDataLength;
		}

	}

}
