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

import org.eclipse.serializer.afs.types.AFS;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XGettingEnum;


public interface StorageRequestTaskImportDataFiles extends StorageRequestTaskImportData<AFile>
{
	public final class Default
	extends    StorageRequestTaskImportData.Abstract<AFile>
	implements StorageRequestTaskImportDataFiles
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                          timestamp             ,
			final int                           channelCount          ,
			final StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
			final XGettingEnum<AFile>           importFiles,
			final StorageOperationController    controller
		)
		{
			super(timestamp, channelCount, controller, objectIdRangeEvaluator, importFiles);
		}
		
		@Override
		protected StorageImportSource.Abstract createImportSource(
			final int                               channelIndex,
			final AFile                             file        ,
			final StorageChannelImportBatch.Default headBatch
		)
		{
			return new StorageImportSourceFile.Default(channelIndex, file, headBatch);
		}
		
		@Override
		protected void iterateSource(final AFile file, final ItemAcceptor itemAcceptor)
		{
			final StorageDataFileItemIterator iterator = StorageDataFileItemIterator.New(
				StorageDataFileItemIterator.BufferProvider.New(),
				itemAcceptor::accept
			);
			AFS.execute(file, iterator::iterateStoredItems);
		}

	}

}
