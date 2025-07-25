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

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.monitoring.MonitoringManager;
import org.eclipse.serializer.persistence.types.ObjectIdsSelector;
import org.eclipse.serializer.persistence.types.PersistenceLiveStorerRegistry;
import org.eclipse.serializer.reference.Referencing;
import org.eclipse.serializer.util.BufferSizeProvider;
import org.eclipse.serializer.util.BufferSizeProviderIncremental;
import org.eclipse.store.storage.monitoring.EntityCacheMonitor;
import org.eclipse.store.storage.monitoring.EntityCacheSummaryMonitor;


public interface StorageChannelsCreator
{
	public StorageChannel[] createChannels(
		int                                        channelCount                 ,
		StorageInitialDataFileNumberProvider       initialDataFileNumberProvider,
		StorageExceptionHandler                    exceptionHandler             ,
		StorageDataFileEvaluator                   fileDissolver                ,
		StorageLiveFileProvider                    liveFileProvider             ,
		StorageEntityCacheEvaluator                entityCacheEvaluator         ,
		StorageTypeDictionary                      typeDictionary               ,
		StorageTaskBroker                          taskBroker                   ,
		StorageOperationController                 operationController          ,
		StorageHousekeepingBroker                  housekeepingBroker           ,
		StorageHousekeepingController              housekeepingController       ,
		StorageTimestampProvider                   timestampProvider            ,
		StorageWriteController                     writeController              ,
		StorageFileWriter.Provider                 writerProvider               ,
		StorageGCZombieOidHandler                  zombieOidHandler             ,
		StorageRootOidSelector.Provider            rootOidSelectorProvider      ,
		StorageObjectIdMarkQueue.Creator           oidMarkQueueCreator          ,
		StorageEntityMarkMonitor.Creator           entityMarkMonitorCreator     ,
		StorageBackupHandler                       backupHandler                ,
		StorageEventLogger                         eventLogger                  ,
		ObjectIdsSelector liveObjectIdChecker                                   ,
		Referencing<PersistenceLiveStorerRegistry> refStorerRegistry            ,
		boolean                                    switchByteOrder              ,
		long                                       rootTypeId                   ,
		MonitoringManager                          monitorManager               ,
		StorageEntityCollector.Creator             entityCollectorCreator       ,
		StorageTransactionsFileCleaner.Creator     transactionFileCleanerCreator
	);



	public static final class Default implements StorageChannelsCreator
	{
		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public final StorageChannel.Default[] createChannels(
			final int                                        channelCount                 ,
			final StorageInitialDataFileNumberProvider       initialDataFileNumberProvider,
			final StorageExceptionHandler                    exceptionHandler             ,
			final StorageDataFileEvaluator                   dataFileEvaluator            ,
			final StorageLiveFileProvider                    liveFileProvider             ,
			final StorageEntityCacheEvaluator                entityCacheEvaluator         ,
			final StorageTypeDictionary                      typeDictionary               ,
			final StorageTaskBroker                          taskBroker                   ,
			final StorageOperationController                 operationController          ,
			final StorageHousekeepingBroker                  housekeepingBroker           ,
			final StorageHousekeepingController              housekeepingController       ,
			final StorageTimestampProvider                   timestampProvider            ,
			final StorageWriteController                     writeController              ,
			final StorageFileWriter.Provider                 writerProvider               ,
			final StorageGCZombieOidHandler                  zombieOidHandler             ,
			final StorageRootOidSelector.Provider            rootOidSelectorProvider      ,
			final StorageObjectIdMarkQueue.Creator           oidMarkQueueCreator          ,
			final StorageEntityMarkMonitor.Creator           entityMarkMonitorCreator     ,
			final StorageBackupHandler                       backupHandler                ,
			final StorageEventLogger                         eventLogger                  ,
			final ObjectIdsSelector                          liveObjectIdChecker          ,
			final Referencing<PersistenceLiveStorerRegistry> refStorerRegistry            ,
			final boolean                                    switchByteOrder              ,
			final long                                       rootTypeId                   ,
			final MonitoringManager                          monitorManager               ,
			final StorageEntityCollector.Creator             entityCollectorCreator       ,
			final StorageTransactionsFileCleaner.Creator     transactionFileCleanerCreator
		)
		{
			// (14.07.2016 TM)TODO: make configuration dynamic
			final int  markBufferLength         = 10000; // see comment in StorageEntityCache. Must be big!
			final long markingWaitTimeMs        =    10;
			final int  loadingBufferSize        =  XMemory.defaultBufferSize();
			final int  readingDefaultBufferSize =  XMemory.defaultBufferSize();

			final StorageChannel.Default[] channels = new StorageChannel.Default[channelCount];

			final StorageObjectIdMarkQueue[] markQueues = new StorageObjectIdMarkQueue[channels.length];
			for(int i = 0; i < markQueues.length; i++)
			{
				markQueues[i] = oidMarkQueueCreator.createOidMarkQueue(markBufferLength);
			}
			final StorageEntityMarkMonitor markMonitor = entityMarkMonitorCreator.createEntityMarkMonitor(
				markQueues,
				eventLogger,
				refStorerRegistry
			);
			
			final BufferSizeProviderIncremental loadingBufferSizeProvider = BufferSizeProviderIncremental.New(loadingBufferSize);
			final BufferSizeProvider readingDefaultBufferSizeProvider     = BufferSizeProvider.New(readingDefaultBufferSize);
			
			final EntityCacheMonitor[] cacheMonitors = new EntityCacheMonitor[channelCount];
			
			for(int i = 0; i < channels.length; i++)
			{
				// entity cache to register entities, cache entity data, perform garbage collection
				final StorageEntityCache.Default entityCache = new StorageEntityCache.Default(
					i                                                ,
					channels.length                                  ,
					entityCacheEvaluator                             ,
					typeDictionary                                   ,
					markMonitor                                      ,
					zombieOidHandler                                 ,
					rootOidSelectorProvider.provideRootOidSelector(i),
					rootTypeId                                       ,
					markQueues[i]                                    ,
					eventLogger                                      ,
					liveObjectIdChecker                              ,
					markingWaitTimeMs                                ,
					markBufferLength
				);
				
				cacheMonitors[i] = new EntityCacheMonitor(entityCache);
				monitorManager.registerMonitor(cacheMonitors[i]);

				// file manager to handle "file" IO (whatever "file" might be, might be a RDBMS binary table as well)
				final StorageFileManager.Default fileManager = new StorageFileManager.Default(
					i                               ,
					initialDataFileNumberProvider   ,
					timestampProvider               ,
					liveFileProvider                ,
					dataFileEvaluator               ,
					entityCache                     ,
					writeController                 ,
					writerProvider.provideWriter(i) ,
					readingDefaultBufferSizeProvider,
					backupHandler                   ,
					transactionFileCleanerCreator
				);

				// required to resolve the initializer cyclic dependency
				entityCache.initializeStorageManager(fileManager);

				// everything bundled together in a "channel".
				channels[i] = new StorageChannel.Default(
					i                        ,
					exceptionHandler         ,
					taskBroker               ,
					operationController      ,
					housekeepingBroker       ,
					housekeepingController   ,
					entityCache              ,
					switchByteOrder          ,
					loadingBufferSizeProvider,
					fileManager              ,
					eventLogger              ,
					monitorManager           ,
					entityCollectorCreator
				);

			}
			
			monitorManager.registerMonitor(new EntityCacheSummaryMonitor(cacheMonitors));
			
			return channels;
		}

	}

}
