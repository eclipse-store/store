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

import static org.eclipse.serializer.util.X.notNull;

import java.nio.ByteBuffer;
import java.util.function.Predicate;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceIdSet;

public interface StorageRequestTaskCreator
{
	public StorageChannelTaskInitialize createInitializationTask(
		int                        channelCount       ,
		StorageOperationController operationController
	);

	public StorageRequestTaskStoreEntities createSaveTask(
		Binary                     data               ,
		StorageOperationController operationController
	);

	public StorageRequestTaskLoadByOids createLoadTaskByOids(
		PersistenceIdSet[]         loadOids           ,
		StorageOperationController operationController
	);

	public StorageRequestTaskLoadRoots createRootsLoadTask(
		int                        channelCount       ,
		StorageOperationController operationController
	);
	
	public StorageRequestTaskLoadByTids createLoadTaskByTids(
		PersistenceIdSet           loadTids           ,
		int                        channelCount       ,
		StorageOperationController operationController
	);

	public default StorageRequestTaskExportEntitiesByType createExportTypesTask(
		final int                                 channelCount      ,
		final StorageEntityTypeExportFileProvider exportFileProvider,
		final StorageOperationController          operationController
	)
	{
		return this.createExportTypesTask(channelCount, exportFileProvider, operationController);
	}
	
	public StorageRequestTaskExportEntitiesByType createExportTypesTask(
		int                                         channelCount       ,
		StorageEntityTypeExportFileProvider         exportFileProvider ,
		Predicate<? super StorageEntityTypeHandler> isExportType       ,
		StorageOperationController                  operationController
	);

	public StorageRequestTaskExportChannels createTaskExportChannels(
		int                        channelCount       ,
		StorageLiveFileProvider    fileProvider       ,
		StorageOperationController operationController
	);

	public StorageRequestTaskCreateStatistics createCreateRawFileStatisticsTask(
		int                        channelCount       ,
		StorageOperationController operationController
	);

	public StorageRequestTaskFileCheck createFullFileCheckTask(
		int                        channelCount       ,
		long                       nanoTimeBudget     ,
		StorageOperationController operationController
	);

	public StorageRequestTaskCacheCheck createFullCacheCheckTask(
		int                         channelCount       ,
		long                        nanoTimeBudget     ,
		StorageEntityCacheEvaluator entityEvaluator    ,
		StorageOperationController  operationController
	);

	public StorageRequestTaskTransactionsLogCleanup CreateTransactionsLogCleanupTask(
		int                        channelCount       ,
		StorageOperationController operationController
	);
	
	public StorageRequestTaskImportDataFiles createImportFromFilesTask(
		int                           channelCount          ,
		StorageDataFileEvaluator      fileEvaluator         ,
		StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
		XGettingEnum<AFile>           importFiles           ,
		StorageOperationController    operationController
	);
	
	public StorageRequestTaskImportDataByteBuffers createImportFromByteBuffersTask(
		int                           channelCount          ,
		StorageDataFileEvaluator      fileEvaluator         ,
		StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
		XGettingEnum<ByteBuffer>      importData            ,
		StorageOperationController    operationController
	);

	public StorageChannelTaskShutdown createShutdownTask(
		int                        channelCount       ,
		StorageOperationController operationController
	);



	public final class Default implements StorageRequestTaskCreator
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageTimestampProvider timestampProvider;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(final StorageTimestampProvider timestampProvider)
		{
			super();
			this.timestampProvider = notNull(timestampProvider);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public StorageChannelTaskInitialize createInitializationTask(
			final int                        channelCount       ,
			final StorageOperationController operationController
		)
		{
			return new StorageChannelTaskInitialize.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount                                 ,
				operationController
			);
		}

		@Override
		public StorageChannelTaskShutdown createShutdownTask(
			final int                        channelCount       ,
			final StorageOperationController operationController
		)
		{
			return new StorageChannelTaskShutdown.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount,
				operationController
			);
		}

		@Override
		public StorageRequestTaskStoreEntities createSaveTask(
			final Binary                     data               ,
			final StorageOperationController operationController
		)
		{
			return new StorageRequestTaskStoreEntities.Default(
				this.timestampProvider.currentNanoTimestamp(),
				data,
				operationController
			);
		}

		@Override
		public StorageRequestTaskLoadByOids createLoadTaskByOids(
			final PersistenceIdSet[]         loadOids           ,
			final StorageOperationController operationController
		)
		{
			return new StorageRequestTaskLoadByOids.Default(
				this.timestampProvider.currentNanoTimestamp(),
				loadOids,
				operationController
			);
		}

		@Override
		public StorageRequestTaskLoadRoots createRootsLoadTask(
			final int                        channelCount       ,
			final StorageOperationController operationController
		)
		{
			return new StorageRequestTaskLoadRoots.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount,
				operationController
			);
		}
		
		@Override
		public StorageRequestTaskLoadByTids createLoadTaskByTids(
			final PersistenceIdSet           loadTids           ,
			final int                        channelCount       ,
			final StorageOperationController operationController
		)
		{
			return new StorageRequestTaskLoadByTids.Default(
				this.timestampProvider.currentNanoTimestamp(),
				loadTids,
				channelCount,
				operationController
			);
		}

		@Override
		public StorageRequestTaskExportEntitiesByType createExportTypesTask(
			final int                                         channelCount       ,
			final StorageEntityTypeExportFileProvider         exportFileProvider ,
			final Predicate<? super StorageEntityTypeHandler> isExportType       ,
			final StorageOperationController                  operationController
		)
		{
			return new StorageRequestTaskExportEntitiesByType.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount                                 ,
				exportFileProvider                           ,
				isExportType,
				operationController
			);
		}

		@Override
		public StorageRequestTaskExportChannels createTaskExportChannels(
			final int                        channelCount       ,
			final StorageLiveFileProvider    fileProvider       ,
			final StorageOperationController operationController
		)
		{
			return new StorageRequestTaskExportChannels.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount,
				fileProvider,
				operationController
			);
		}

		@Override
		public StorageRequestTaskCreateStatistics createCreateRawFileStatisticsTask(
			final int                        channelCount       ,
			final StorageOperationController operationController
		)
		{
			return new StorageRequestTaskCreateStatistics.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount,
				operationController
			);
		}

		@Override
		public StorageRequestTaskFileCheck createFullFileCheckTask(
			final int                        channelCount       ,
			final long                       nanoTimeBudget     ,
			final StorageOperationController operationController
		)
		{
			return new StorageRequestTaskFileCheck.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount,
				nanoTimeBudget,
				operationController
			);
		}

		@Override
		public StorageRequestTaskCacheCheck createFullCacheCheckTask(
			final int                         channelCount       ,
			final long                        nanoTimeBudget     ,
			final StorageEntityCacheEvaluator entityEvaluator    ,
			final StorageOperationController  operationController
		)
		{
			return new StorageRequestTaskCacheCheck.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount,
				nanoTimeBudget,
				entityEvaluator,
				operationController
			);
		}

		@Override
		public StorageRequestTaskTransactionsLogCleanup CreateTransactionsLogCleanupTask(
			final int channelCount,
			final StorageOperationController operationController)
		{
			return new StorageRequestTaskTransactionsLogCleanup.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount,
				operationController
			);
		}
		
		@Override
		public StorageRequestTaskImportDataFiles createImportFromFilesTask(
			final int                           channelCount          ,
			final StorageDataFileEvaluator      fileEvaluator         ,
			final StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
			final XGettingEnum<AFile>           importFiles           ,
			final StorageOperationController    operationController
		)
		{
			return new StorageRequestTaskImportDataFiles.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount                                 ,
				objectIdRangeEvaluator                       ,
				importFiles                                  ,
				operationController
			);
		}
		
		@Override
		public StorageRequestTaskImportDataByteBuffers createImportFromByteBuffersTask(
			final int                           channelCount          ,
			final StorageDataFileEvaluator      fileEvaluator         ,
			final StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
			final XGettingEnum<ByteBuffer>      importData            ,
			final StorageOperationController    operationController
		)
		{
			return new StorageRequestTaskImportDataByteBuffers.Default(
				this.timestampProvider.currentNanoTimestamp(),
				channelCount                                 ,
				objectIdRangeEvaluator                       ,
				importData                                   ,
				operationController
			);
		}

	}

}
