package org.eclipse.store.storage.types;

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
