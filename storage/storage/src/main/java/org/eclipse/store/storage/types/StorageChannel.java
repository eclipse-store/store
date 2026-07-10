package org.eclipse.store.storage.types;

import static org.eclipse.serializer.math.XMath.notNegative;

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;

import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.functional.ThrowingProcedure;
import org.eclipse.serializer.monitoring.MonitoringManager;
import org.eclipse.serializer.persistence.binary.types.Chunk;
import org.eclipse.serializer.persistence.binary.types.ChunksBuffer;
import org.eclipse.serializer.persistence.binary.types.ChunksBufferByteReversing;
import org.eclipse.serializer.persistence.types.PersistenceIdSet;
import org.eclipse.serializer.persistence.types.Unpersistable;
import org.eclipse.serializer.time.XTime;
import org.eclipse.serializer.typing.Disposable;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.BufferSizeProviderIncremental;
import org.eclipse.serializer.util.X;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.exceptions.StorageExceptionTransactionsFileCompaction;
import org.eclipse.store.storage.monitoring.StorageChannelHousekeepingMonitor;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter.AdjacencyFiles;
import org.slf4j.Logger;


/**
 * A single storage worker thread, responsible for a fixed slice (its {@code channelIndex}) of the
 * total entity space.
 * <p>
 * The number of channels in a running storage is fixed at startup by the
 * {@link StorageChannelCountProvider} and partitions every entity by its object id; each
 * {@link StorageChannel} owns the data files, transaction log, entity cache and per-channel
 * housekeeping cursor for its slice. Channels run in dedicated threads via {@link #run()} and
 * cooperatively process tasks dispatched from a central task broker (stores, loads, garbage
 * collection, file cleanup, etc.) while interleaving incremental housekeeping work in idle time.
 * <p>
 * This is a framework-internal coordination interface: applications normally interact with
 * {@link StorageManager} / {@link StorageConnection} and never call methods on a channel directly.
 * The interface is exposed in the public API package so that custom strategy types and tasks in the
 * same package can refer to it.
 *
 * @see StorageChannelCountProvider
 * @see StorageHousekeepingController
 */
public interface StorageChannel extends Runnable, StorageChannelResetablePart, StorageActivePart, Disposable
{
	/**
	 * Returns the {@link StorageTypeDictionary} that this channel uses to interpret persisted
	 * entities.
	 *
	 * @return the channel's {@link StorageTypeDictionary}.
	 */
	public StorageTypeDictionary typeDictionary();

	/**
	 * Collects the entities for the passed object ids that belong to this channel into a
	 * {@link ChunksBuffer}, which is appended to {@code channelChunks} at this channel's slot.
	 *
	 * @param channelChunks the array of {@link ChunksBuffer}s, one per channel, into which the
	 *                      collected data is written.
	 * @param loadOids      the object ids to load; only the subset belonging to this channel is
	 *                      processed.
	 *
	 * @return the {@link ChunksBuffer} this channel wrote into, completed.
	 */
	public ChunksBuffer collectLoadByOids(ChunksBuffer[] channelChunks, PersistenceIdSet loadOids);

	/**
	 * Collects all root entities owned by this channel into a {@link ChunksBuffer} appended to
	 * {@code channelChunks}.
	 *
	 * @param channelChunks the array of {@link ChunksBuffer}s, one per channel.
	 *
	 * @return the {@link ChunksBuffer} this channel wrote into, completed.
	 */
	public ChunksBuffer collectLoadRoots(ChunksBuffer[] channelChunks);

	/**
	 * Collects every entity owned by this channel whose type-id is in {@code loadTids}, into a
	 * {@link ChunksBuffer} appended to {@code channelChunks}.
	 *
	 * @param channelChunks the array of {@link ChunksBuffer}s, one per channel.
	 * @param loadTids      the type ids to load; only entities with these type ids are collected.
	 *
	 * @return the {@link ChunksBuffer} this channel wrote into, completed.
	 */
	public ChunksBuffer collectLoadByTids(ChunksBuffer[] channelChunks, PersistenceIdSet loadTids);

	/**
	 * Writes the channel-local portion of a store chunk to disk and returns the buffers along with
	 * their on-disk positions.
	 * <p>
	 * Must be paired with a subsequent {@link #commitChunkStorage()} or
	 * {@link #rollbackChunkStorage()} call once all participating channels have finished writing,
	 * to make the store atomically visible or to discard it.
	 *
	 * @param timestamp the store transaction timestamp shared across all channels.
	 * @param chunkData the chunk data to write for this channel's slice.
	 *
	 * @return a key/value pair of the written buffers and their assigned on-disk storage positions.
	 */
	public KeyValue<ByteBuffer[], long[]> storeEntities(long timestamp, Chunk chunkData);

	/**
	 * Validates that each passed object id resolves to an existing entity in this channel. The ids are a
	 * store's "trusted references": object ids the storer wrote into the data without storing the
	 * referenced entity itself, trusting that it already exists.
	 * <p>
	 * Depending on the passed policy, missing entities are logged
	 * ({@link StorageReferenceValidationPolicy#LOG}) or rejected by throwing a
	 * {@link org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference}
	 * ({@link StorageReferenceValidationPolicy#FAIL}), which fails the surrounding store task and rolls
	 * the whole store back atomically.
	 * <p>
	 * This check is race-free with respect to the storage garbage collector: GC sweeping for a channel
	 * runs exclusively on that channel's own thread between tasks, and this method is invoked on the same
	 * thread inside the store task, before the store commits. An entity found here therefore cannot be
	 * deleted before the surrounding store is committed.
	 * <p>
	 * Contract for rejections: a failing validation reports ALL missing ids of this channel in one
	 * exception (the complete miss list is collected before throwing). The storer-side self-healing
	 * relies on this to bound its retry attempts at one healing round per channel.
	 *
	 * @param objectIds the trusted reference object ids assigned to this channel; may be {@code null} or empty.
	 * @param policy    the validation policy to apply.
	 */
	public default void validateTrustedReferences(
		final long[]                           objectIds,
		final StorageReferenceValidationPolicy policy
	)
	{
		// no-op by default
	}

	/**
	 * Reverts the most recent {@link #storeEntities(long, Chunk) store} on this channel, discarding
	 * any data that was written but not yet committed.
	 */
	public void rollbackChunkStorage();

	/**
	 * Commits the most recent {@link #storeEntities(long, Chunk) store} on this channel, making the
	 * written data permanently visible.
	 */
	public void commitChunkStorage();

	/**
	 * Updates the live entity cache to reflect a freshly committed store, recording the new on-disk
	 * positions of the passed chunks.
	 *
	 * @param chunks                 the buffers that were written.
	 * @param chunksStoragePositions the on-disk positions returned by {@link #storeEntities}.
	 *
	 * @throws InterruptedException if the calling channel thread is interrupted while waiting for
	 *                              the cache update slot.
	 */
	public void postStoreUpdateEntityCache(ByteBuffer[] chunks, long[] chunksStoragePositions)
		throws InterruptedException;

	/**
	 * Reads this channel's data and transaction files from disk and returns a per-channel
	 * {@link StorageInventory} describing them.
	 *
	 * @return the {@link StorageInventory} for this channel's files as currently present on disk.
	 */
	public StorageInventory readStorage();

	/**
	 * Issues a garbage-collection request to this channel with the passed time budget.
	 * <p>
	 * Returns {@code true} if the requested work could be completed within the budget,
	 * {@code false} if the channel had to break off and the caller should re-issue the request.
	 *
	 * @param nanoTimeBudget the maximum amount of time, in nanoseconds, this channel is allowed to
	 *                      spend on the GC step.
	 *
	 * @return whether the GC step completed within the budget.
	 */
	public boolean issuedGarbageCollection(long nanoTimeBudget);

	/**
	 * Signals that the marking of the currently issued garbage collection cannot complete
	 * because a channel failed mid-marking. Sibling channels waiting for that channel's marks
	 * exit promptly instead of waiting out the (effectively unbounded) time budget.
	 * Default is a no-op; see {@link StorageEntityMarkMonitor#signalGcMarkingAbort()}.
	 */
	public default void signalGarbageCollectionAbort()
	{
		// no-op by default
	}

	/**
	 * Clears a previously signaled garbage collection abort, arming a new issued garbage
	 * collection attempt. Default is a no-op; see
	 * {@link StorageEntityMarkMonitor#clearGcMarkingAbort()}.
	 */
	public default void clearGarbageCollectionAbort()
	{
		// no-op by default
	}

	/**
	 * Issues a file-cleanup check to this channel with the passed time budget.
	 *
	 * @param nanoTimeBudget the maximum amount of time, in nanoseconds, this channel is allowed to
	 *                       spend on the file-cleanup step.
	 *
	 * @return whether the file-cleanup step completed within the budget.
	 */
	public boolean issuedFileCleanupCheck(long nanoTimeBudget);

	/**
	 * Issues an entity-cache check to this channel with the passed time budget, using the passed
	 * {@link StorageEntityCacheEvaluator} to decide which cached entities to evict.
	 *
	 * @param nanoTimeBudget  the maximum amount of time, in nanoseconds, this channel is allowed
	 *                        to spend on the cache-check step.
	 * @param entityEvaluator the eviction policy to apply for this issued check.
	 *
	 * @return whether the cache-check step completed within the budget.
	 */
	public boolean issuedEntityCacheCheck(long nanoTimeBudget, StorageEntityCacheEvaluator entityEvaluator);

	/**
	 * Issues an on-demand chunk-checksum integrity check to this channel with the passed time budget,
	 * returning this channel's collected anomalies and whether the scan completed within the budget.
	 *
	 * @param nanoTimeBudget the maximum amount of time, in nanoseconds, this channel is allowed to
	 *                       spend on the integrity-check step.
	 * @param freshScan      whether to start a new scan (vs resume the in-progress one).
	 *
	 * @return this channel's findings and completion state.
	 */
	public StorageIntegrityCheckResult issuedIntegrityCheck(long nanoTimeBudget, boolean freshScan);

	/**
	 * Issues a transactions-log cleanup pass to this channel.
	 *
	 * @return whether the cleanup pass completed; {@code true} if no further cleanup is currently
	 *         required.
	 */
	public boolean issuedTransactionsLogCleanup();

	/**
	 * Exports this channel's live data into the directory layout described by the passed
	 * {@link StorageLiveFileProvider}, writing one set of files per channel slot.
	 *
	 * @param fileProvider the {@link StorageLiveFileProvider} to use as the export target layout.
	 */
	public void exportData(StorageLiveFileProvider fileProvider);

	// (19.07.2014 TM)TODO: refactor storage typing to avoid classes in public API
	/**
	 * Prepares this channel to receive imported data by registering the import with the storage
	 * garbage collector (blocking new sweeps for the whole import task and quiescing an already
	 * flagged one, see StorageEntityCache.Default#registerPendingImportUpdate), switching its file
	 * manager into import mode and returning the entity cache that the importer will populate.
	 * <p>
	 * Every preparation must be paired with a {@link #cleanupImportData()} call once the import
	 * task has ultimately completed (committed or rolled back).
	 *
	 * @return the channel's entity cache, ready to receive imported entities.
	 */
	public StorageEntityCache.Default prepareImportData();

	/**
	 * Copies the data from the passed {@link StorageImportSource} into this channel's storage.
	 *
	 * @param importSource the source to read imported data from.
	 */
	public void importData(StorageImportSource importSource);

	/**
	 * Rolls back an in-progress {@link #importData(StorageImportSource) import}, restoring the
	 * channel's state from before the import started.
	 *
	 * @param cause the throwable that triggered the rollback (used for diagnostic logging only).
	 */
	public void rollbackImportData(Throwable cause);

	/**
	 * Commits an in-progress {@link #importData(StorageImportSource) import}, making the imported
	 * data visible under the passed transaction timestamp.
	 *
	 * @param taskTimestamp the timestamp that identifies the import transaction.
	 */
	public void commitImportData(long taskTimestamp);

	/**
	 * Clears the garbage collection coordination state registered by
	 * {@link #prepareImportData()}, releasing sweep initiation again. Must be called exactly once
	 * per {@link #prepareImportData()} after the import task has ultimately completed, no matter
	 * the outcome (committed or rolled back). Idempotent and robust if the preparation never ran
	 * or failed halfway.
	 * <p>
	 * The default implementation is a no-op for {@link StorageChannel} implementations whose
	 * {@link #prepareImportData()} does not register any garbage collection coordination state.
	 */
	public default void cleanupImportData()
	{
		// no-op by default. To be overridden by implementations that register gc state in prepareImportData().
	}

	/**
	 * Exports every entity of the passed type owned by this channel into the passed file.
	 *
	 * @param type the type whose entities shall be exported.
	 * @param file the target file to write the exported entities to.
	 *
	 * @return the number of bytes written and the number of entities exported, as a key/value pair.
	 *
	 * @throws IOException if writing to the target file fails.
	 */
	public KeyValue<Long, Long> exportTypeEntities(StorageEntityTypeHandler type, AWritableFile file)
		throws IOException;

	/**
	 * Exports every entity of the passed type owned by this channel for which the passed predicate
	 * returns {@code true}, into the passed file.
	 *
	 * @param type            the type whose entities shall be exported.
	 * @param file            the target file to write the exported entities to.
	 * @param predicateEntity an entity-level filter; only entities for which this predicate returns
	 *                        {@code true} are exported.
	 *
	 * @return the number of bytes written and the number of entities exported, as a key/value pair.
	 *
	 * @throws IOException if writing to the target file fails.
	 */
	public KeyValue<Long, Long> exportTypeEntities(
		StorageEntityTypeHandler         type           ,
		AWritableFile                    file           ,
		Predicate<? super StorageEntity> predicateEntity
	) throws IOException;

	/**
	 * Builds the per-channel slice of the raw file statistics describing this channel's data files.
	 *
	 * @return a {@link StorageRawFileStatistics.ChannelStatistics} describing this channel's files.
	 */
	public StorageRawFileStatistics.ChannelStatistics createRawFileStatistics();

	/**
	 * Initializes this channel's storage from the passed inventory, replaying transaction logs and
	 * computing a per-channel {@link StorageIdAnalysis}.
	 *
	 * @param taskTimestamp            the timestamp of the initialization task.
	 * @param consistentStoreTimestamp the timestamp identifying the last fully-committed store.
	 * @param storageInventory         the {@link StorageInventory} previously read by
	 *                                 {@link #readStorage()}.
	 *
	 * @return the {@link StorageIdAnalysis} for this channel's slice.
	 */
	public StorageIdAnalysis initializeStorage(
		long             taskTimestamp           ,
		long             consistentStoreTimestamp,
		StorageInventory storageInventory
	);

	/**
	 * Signals to this channel that a complete GC sweep across all channels has finished, so that
	 * the channel may restart its file-cleanup cursor and re-evaluate cleanup eligibility.
	 */
	public void signalGarbageCollectionSweepCompleted();

//	public void truncateData();

	/**
	 * Clears any pending store state held by this channel after a store has been processed
	 * (committed or rolled back).
	 */
	public void cleanupStore();

	/**
	 * Iterates this channel's data files and writes per-file adjacency-data exports into the
	 * passed directory.
	 *
	 * @param exportDirectory the directory to write the adjacency-data files into.
	 *
	 * @return the {@link AdjacencyFiles} descriptor of the files this channel produced.
	 */
	public AdjacencyFiles collectAdjacencyData(Path exportDirectory);


	

	/**
	 * Default {@link StorageChannel} implementation: the long-running worker that pulls tasks from a
	 * shared {@link StorageTaskBroker} and interleaves housekeeping work in the gaps. The instance is
	 * marked {@link Unpersistable} because its mutable runtime state is not safe to persist as a
	 * regular entity.
	 */
	public final class Default implements StorageChannel, Unpersistable, StorageHousekeepingExecutor
	{
		private final static Logger logger = Logging.getLogger(StorageChannel.class);
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final int                            channelIndex             ;
		private final StorageExceptionHandler        exceptionHandler         ;
		private final StorageTaskBroker              taskBroker               ;
		private final StorageOperationController     operationController      ;
		private final StorageHousekeepingController  housekeepingController   ;
		private final StorageHousekeepingBroker      housekeepingBroker       ;
		private final StorageFileManager.Default     fileManager              ;
		private final StorageEntityCache.Default     entityCache              ;
		private final boolean                        switchByteOrder          ;
		private final BufferSizeProviderIncremental  loadingBufferSizeProvider;
		private final StorageEventLogger             eventLogger              ;
		private final StorageEntityCollector.Creator entityCollectorCreator   ;

		private final HousekeepingTask[] housekeepingTasks;
		
		private int nextHouseKeepingIndex;

		/**
		 * A nanosecond timestamp marking the calculated end of the current housekeeping interval.
		 * @see {@link StorageHousekeepingController#housekeepingIntervalMs()}
		 */
		private long housekeepingIntervalBoundTimeNs;

		/**
		 * The remaining housekeeping budget in nanoseconds for the current interval.
		 * @see StorageHousekeepingController#housekeepingTimeBudgetNs()
		 */
		private long housekeepingIntervalBudgetNs;
		
		private boolean active;

		private final StorageChannelHousekeepingMonitor monitoringData;


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(
			final int                            hashIndex                ,
			final StorageExceptionHandler        exceptionHandler         ,
			final StorageTaskBroker              taskBroker               ,
			final StorageOperationController     operationController      ,
			final StorageHousekeepingBroker      housekeepingBroker       ,
			final StorageHousekeepingController  housekeepingController   ,
			final StorageEntityCache.Default     entityCache              ,
			final boolean                        switchByteOrder          ,
			final BufferSizeProviderIncremental  loadingBufferSizeProvider,
			final StorageFileManager.Default     fileManager              ,
			final StorageEventLogger             eventLogger              ,
			final MonitoringManager              monitorManager           ,
			final StorageEntityCollector.Creator entityCollectorCreator
		)
		{
			super();
			this.channelIndex              = notNegative(hashIndex)                ;
			this.exceptionHandler          =     notNull(exceptionHandler)         ;
			this.taskBroker                =     notNull(taskBroker)               ;
			this.operationController       =     notNull(operationController)      ;
			this.housekeepingBroker        =     notNull(housekeepingBroker)       ;
			this.fileManager               =     notNull(fileManager)              ;
			this.entityCache               =     notNull(entityCache)              ;
			this.housekeepingController    =     notNull(housekeepingController)   ;
			this.loadingBufferSizeProvider =     notNull(loadingBufferSizeProvider);
			this.eventLogger               =     notNull(eventLogger)              ;
			this.switchByteOrder           =             switchByteOrder           ;
			this.entityCollectorCreator    =     notNull(entityCollectorCreator)   ;
			
			// depends on this.fileManager!
			this.housekeepingTasks = this.defineHouseKeepingTasks();
			
			this.monitoringData = new StorageChannelHousekeepingMonitor(this.channelIndex);
			monitorManager.registerMonitor(this.monitoringData);
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////
		
		private HousekeepingTask[] defineHouseKeepingTasks()
		{
			final BulkList<HousekeepingTask> tasks = BulkList.New();
			tasks.add(this::houseKeepingCheckFileCleanup);
			tasks.add(this::houseKeepingGarbageCollection);
			tasks.add(this::houseKeepingEntityCacheCheck);
			tasks.add(this::houseKeepingTransactionFile);
			// (16.06.2020 TM)TODO: priv#49: housekeeping task that closes data files after a timeout.

			return tasks.toArray(HousekeepingTask.class);
		}

		private int getCurrentHouseKeepingIndexAndAdvance()
		{
			if(this.nextHouseKeepingIndex >= this.housekeepingTasks.length)
			{
				this.nextHouseKeepingIndex = 1;
				return 0;
			}
			return this.nextHouseKeepingIndex++;
		}

		private void houseKeeping()
		{
			final long currentNanotime;

			if((currentNanotime = System.nanoTime()) >= this.housekeepingIntervalBoundTimeNs)
			{
				this.housekeepingIntervalBoundTimeNs = currentNanotime
					+ Storage.millisecondsToNanoseconds(this.housekeepingController.housekeepingIntervalMs())
				;
				this.housekeepingIntervalBudgetNs = this.housekeepingController.housekeepingTimeBudgetNs();
			}
			else if(this.housekeepingIntervalBudgetNs <= 0)
			{
				return;
			}
			else if(this.housekeepingIntervalBoundTimeNs - currentNanotime < this.housekeepingIntervalBudgetNs)
			{
				// cap remaining housekeeping budget at the current interval's housekeeping time bound
				this.housekeepingIntervalBudgetNs = this.housekeepingIntervalBoundTimeNs - currentNanotime;
			}

			final long budgetOffset = currentNanotime + this.housekeepingIntervalBudgetNs;


			// execute every task once at most per cycle (therefore the counter, but NOT for selecting the task)
			for(int c = 0; c < this.housekeepingTasks.length; c++)
			{
				// call the next task (next from last cycle or just another one if there is still time)
				this.housekeepingTasks[this.getCurrentHouseKeepingIndexAndAdvance()].perform();

				// intentionally checked AFTER the first housekeeping task to guarantee at least one task to be executed
				if((this.housekeepingIntervalBudgetNs = budgetOffset - System.nanoTime()) <= 0)
				{
					break;
				}
			}

		}
		
		@Override
		public boolean performIssuedGarbageCollection(final long nanoTimeBudget)
		{
			logger.trace("StorageChannel#{} performing issued garbage collection", this.channelIndex);
			
			// turn budget into the budget bounding value for easier and faster checking
			final long nanoTimeBudgetBound = XTime.calculateNanoTimeBudgetBound(nanoTimeBudget);

			return this.entityCache.issuedGarbageCollection(nanoTimeBudgetBound, this);
		}
		
		@Override
		public boolean performIssuedFileCleanupCheck(final long nanoTimeBudget)
		{
			if(!this.fileManager.isFileCleanupEnabled())
			{
				return true;
			}
			
			logger.trace("StorageChannel#{} performing issued file cleanup check", this.channelIndex);
			
			// turn budget into the budget bounding value for easier and faster checking
			final long nanoTimeBudgetBound = XTime.calculateNanoTimeBudgetBound(nanoTimeBudget);
			
			return this.fileManager.issuedFileCleanupCheck(nanoTimeBudgetBound);
		}
		
		@Override
		public boolean performIssuedEntityCacheCheck(
			final long                        nanoTimeBudget,
			final StorageEntityCacheEvaluator evaluator
		)
		{
			logger.trace("StorageChannel#{} performing issued entity cache check", this.channelIndex);
			
			// turn budget into the budget bounding value for easier and faster checking
			final long nanoTimeBudgetBound = XTime.calculateNanoTimeBudgetBound(nanoTimeBudget);

			return this.entityCache.issuedEntityCacheCheck(nanoTimeBudgetBound, evaluator);
		}

		@Override
		public StorageIntegrityCheckResult performIssuedIntegrityCheck(final long nanoTimeBudget, final boolean freshScan)
		{
			logger.trace("StorageChannel#{} performing issued integrity check", this.channelIndex);

			// turn budget into the budget bounding value for easier and faster checking
			final long nanoTimeBudgetBound = XTime.calculateNanoTimeBudgetBound(nanoTimeBudget);

			return this.fileManager.verifyChunkChecksums(nanoTimeBudgetBound, freshScan);
		}

		@Override
		public final boolean performFileCleanupCheck(final long nanoTimeBudget)
		{
			if(!this.fileManager.isFileCleanupEnabled())
			{
				return true;
			}
			
			logger.trace("StorageChannel#{} performing incremental file cleanup check", this.channelIndex);
			
			// turn budget into the budget bounding value for easier and faster checking
			final long nanoTimeBudgetBound = XTime.calculateNanoTimeBudgetBound(nanoTimeBudget);
			
					
			final StorageChannelHousekeepingResult result = StorageChannelHousekeepingResult.create(nanoTimeBudget,
				() -> this.fileManager.incrementalFileCleanupCheck(nanoTimeBudgetBound));
			
			this.monitoringData.setFileCleanupCheckResult(result);
			
			return result.getResult();
		}
		
		@Override
		public boolean performGarbageCollection(final long nanoTimeBudget)
		{
			logger.trace("StorageChannel#{} performing incremental garbage collection", this.channelIndex);
			
			// turn budget into the budget bounding value for easier and faster checking
			final long nanoTimeBudgetBound = XTime.calculateNanoTimeBudgetBound(nanoTimeBudget);
			
			final StorageChannelHousekeepingResult result = StorageChannelHousekeepingResult.create(nanoTimeBudget,
				() -> this.entityCache.incrementalGarbageCollection(nanoTimeBudgetBound, this));
			
			this.monitoringData.setGarbageCollectionResult(result);
			
			return result.getResult();
		}
		
		@Override
		public boolean performEntityCacheCheck(
			final long nanoTimeBudget
		)
		{
			logger.trace("StorageChannel#{} performing incremental entity cache check", this.channelIndex);
			
			// turn budget into the budget bounding value for easier and faster checking
			final long nanoTimeBudgetBound = XTime.calculateNanoTimeBudgetBound(nanoTimeBudget);
			
			final StorageChannelHousekeepingResult result = StorageChannelHousekeepingResult.create(nanoTimeBudget,
				() -> this.entityCache.incrementalEntityCacheCheck(nanoTimeBudgetBound));
			
			this.monitoringData.setEntityCacheCheckResult(result);
			
			return result.getResult();
		}
		
		@Override
		public boolean performTransactionFileCheck(final boolean checkSize)
		{
			if(!this.fileManager.isFileCleanupEnabled())
			{
				return true;
			}
			
			logger.trace("StorageChannel#{} performing transaction file check", this.channelIndex);
			
			return this.fileManager.issuedTransactionFileCheck(checkSize);
		}
		
		@Override
		public final boolean issuedGarbageCollection(final long nanoTimeBudget)
		{
			return this.housekeepingBroker.performIssuedGarbageCollection(this, nanoTimeBudget);
		}

		@Override
		public final void signalGarbageCollectionAbort()
		{
			this.entityCache.signalGcMarkingAbort();
		}

		@Override
		public final void clearGarbageCollectionAbort()
		{
			this.entityCache.clearGcMarkingAbort();
		}

		@Override
		public boolean issuedFileCleanupCheck(final long nanoTimeBudget)
		{
			return this.housekeepingBroker.performIssuedFileCleanupCheck(this, nanoTimeBudget);
		}

		@Override
		public boolean issuedEntityCacheCheck(
			final long                        nanoTimeBudget,
			final StorageEntityCacheEvaluator entityEvaluator
		)
		{
			return this.housekeepingBroker.performIssuedEntityCacheCheck(this, nanoTimeBudget, entityEvaluator);
		}

		@Override
		public StorageIntegrityCheckResult issuedIntegrityCheck(final long nanoTimeBudget, final boolean freshScan)
		{
			return this.housekeepingBroker.performIssuedIntegrityCheck(this, nanoTimeBudget, freshScan);
		}

		@Override
		public boolean issuedTransactionsLogCleanup()
		{
			try
			{
				return this.housekeepingBroker.performTransactionFileCheck(this, false);
			}
			catch(final StorageExceptionTransactionsFileCompaction e)
			{
				/*
				 * The live transaction log is broken and only the retained swap file can heal it
				 * on the next initialization; any further append would be discarded by that heal.
				 * Unlike ordinary task problems, this failure must therefore stop the channel.
				 */
				this.operationController.registerDisruption(e);
				this.operationController.setChannelProcessingEnabled(false);
				throw e;
			}
		}
		
		private long calculateSpecificHousekeepingTimeBudget(final long nanoTimeBudget)
		{
			return Math.min(nanoTimeBudget, this.housekeepingIntervalBudgetNs);
		}

		final boolean houseKeepingCheckFileCleanup()
		{
			if(!this.fileManager.isFileCleanupEnabled())
			{
				return true;
			}
			
			final long nanoTimeBudget = this.calculateSpecificHousekeepingTimeBudget(
				this.housekeepingController.fileCheckTimeBudgetNs()
			);
			
			return this.housekeepingBroker.performFileCleanupCheck(this, nanoTimeBudget);
		}

		final boolean houseKeepingGarbageCollection()
		{
			final long nanoTimeBudget = this.calculateSpecificHousekeepingTimeBudget(
				this.housekeepingController.garbageCollectionTimeBudgetNs()
			);
			
			return this.housekeepingBroker.performGarbageCollection(this, nanoTimeBudget);
		}

		final boolean houseKeepingEntityCacheCheck()
		{
			final long nanoTimeBudget = this.calculateSpecificHousekeepingTimeBudget(
				this.housekeepingController.liveCheckTimeBudgetNs()
			);
			
			return this.housekeepingBroker.performEntityCacheCheck(this, nanoTimeBudget);
		}
		
		final boolean houseKeepingTransactionFile()
		{
			return this.housekeepingBroker.performTransactionFileCheck(this, true);
		}

		private void work() throws InterruptedException
		{
			logger.debug("StorageChannel#{} started", this.channelIndex);
			
			final StorageOperationController    operationController    = this.operationController   ;
			final StorageHousekeepingController housekeepingController = this.housekeepingController;

			StorageTask processedTask = new StorageTask.DummyTask();
			StorageTask currentTask   = notNull(this.taskBroker.currentTask());

			while(true)
			{
				// ensure to process every task only once in case no new task came in in time (see below).
				if(currentTask != processedTask)
				{
					currentTask.processBy(this);
					processedTask = currentTask;
				}

				/*
				 * Must check immediately after task processing to abort BEFORE houseKeeping is called in case
				 * of shutdown (otherwise NPE on headFile etc.). So do-while not possible.
				 * Also, may NOT check before task processing as the first task is initializing which in turn
				 * enables channel processing on success. So no simple while condition possible.
				 */
				if(!operationController.checkProcessingEnabled())
				{
					logger.debug("StorageChannel#{} processing disabled", this.channelIndex);
					this.eventLogger.logChannelProcessingDisabled(this);
					break;
				}

				// do a little housekeeping, either after a new task or use time if no new task came in.
				try
				{
					this.houseKeeping();
				}
				catch(final Throwable t)
				{
					logger.error("StorageChannel#{} encountered disrupting exception", this.channelIndex, t);
					this.eventLogger.logDisruption(this, t);
					this.operationController.setChannelProcessingEnabled(false);
					logger.debug("StorageChannel#{} processing disabled", this.channelIndex);
					this.operationController.registerDisruption(t);
					this.eventLogger.logChannelProcessingDisabled(this);
					break;
				}
				

				// check and wait for the next task to come in
				if((currentTask = processedTask.awaitNext(housekeepingController.housekeepingIntervalMs())) == null)
				{
					// revert to processed task to wait on it again for the next task
					currentTask = processedTask;
				}
			}
			
			logger.debug("StorageChannel#{} stopped", this.channelIndex);
			this.eventLogger.logChannelStoppedWorking(this);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public synchronized boolean isActive()
		{
			return this.active;
		}
		
		private synchronized void activate()
		{
			this.active = true;
		}
		
		private synchronized void deactivate()
		{
			this.active = false;
		}

		@Override
		public final void run()
		{
			// first thing to do
			this.activate();
			
			Throwable workingDisruption = null;
			try
			{
				this.work();
			}
			catch(final Throwable t)
			{
				/*
				 * Note that `t` could be an error, or it could even be a checked exception thrown via
				 * Proxy reflective tinkering or Unsafe mechanisms.
				 * However, Throwable cannot be rethrown in Runnable#run() without cheating exception checking again.
				 * Luckily, in this special case, reporting the cause and then dying "silently" is sufficient.
				 *
				 * Note: applies to interruption as well, because on privately managed threads,
				 * interrupting ultimately means just stop running in an ordered fashion
				 */
				workingDisruption = t;
				logger.error("StorageChannel#{} encountered disrupting exception", this.channelIndex, t);
				this.eventLogger.logDisruption(this, t);
				this.exceptionHandler.handleException(t, this);
			}
			finally
			{
				try
				{
					this.dispose();
				}
				catch(final Throwable t1)
				{
					if(workingDisruption != null)
					{
						t1.addSuppressed(workingDisruption);
					}
				}
				finally
				{
					// finally finally: guaranteed last thing to do ever in any case. Ever.
					this.deactivate();
				}
			}
		}

		@Override
		public void commitChunkStorage()
		{
			this.fileManager.commitWrite();
		}

		@Override
		public KeyValue<ByteBuffer[], long[]> storeEntities(final long timestamp, final Chunk chunkData)
		{
			// reset even if there is no new data to account for (potential) new data in other channel
			this.entityCache.registerPendingStoreUpdate();

			final ByteBuffer[] buffers = chunkData.buffers();
			
			// (11.03.2019 TM)FIXME: priv#74: Pre-Write EntityValidator
			
			// set new data flag, even if chunk has no data to account for (potential) data in other channels
			return X.KeyValue(buffers, this.fileManager.storeChunks(timestamp, buffers));
		}

		@Override
		public void validateTrustedReferences(
			final long[]                           objectIds,
			final StorageReferenceValidationPolicy policy
		)
		{
			// fail fast on broken wiring; a null policy mid-validation would mask the actual store failure.
			notNull(policy);

			if(objectIds == null || objectIds.length == 0 || !policy.isValidating())
			{
				return;
			}

			long[] missing = null;
			int    count   = 0;
			for(final long objectId : objectIds)
			{
				if(this.entityCache.getEntry(objectId) == null)
				{
					if(missing == null)
					{
						missing = new long[objectIds.length];
					}
					missing[count++] = objectId;
				}
			}

			if(missing == null)
			{
				return;
			}

			final long[] missingObjectIds = count == missing.length
				? missing
				: Arrays.copyOf(missing, count)
			;

			if(policy.isHealing())
			{
				// under heal, a rejection is EXPECTED to be repaired by the storer - WARN, not ERROR.
				// If healing gives up, the failure surfaces loudly via the thrown exception anyway.
				logger.warn(
					"StorageChannel#{} store references non-existing entities (dangling references): {}"
					+ " - rejecting for the storer to heal.",
					this.channelIndex,
					Arrays.toString(missingObjectIds)
				);
			}
			else
			{
				logger.error(
					"StorageChannel#{} store references non-existing entities (dangling references): {}",
					this.channelIndex,
					Arrays.toString(missingObjectIds)
				);
			}
			this.eventLogger.logStoreDetectedDanglingReferences(this.channelIndex, missingObjectIds);

			if(policy.isFailing())
			{
				throw new StorageExceptionConsistencyDanglingReference(this.channelIndex, missingObjectIds);
			}
		}

		@Override
		public void postStoreUpdateEntityCache(final ByteBuffer[] chunks, final long[] chunksStoragePositions)
			throws InterruptedException
		{
			// all chunks were written into the same file, so it is viable to pass the current file right here
			this.entityCache.postStorePutEntities(chunks, chunksStoragePositions, this.fileManager.currentStorageFile());
		}

		@Override
		public final int channelIndex()
		{
			return this.channelIndex;
		}

		@Override
		public final StorageTypeDictionary typeDictionary()
		{
			return this.entityCache.typeDictionary();
		}
		
		private ChunksBuffer createLoadingChunksBuffer(final ChunksBuffer[] channelChunks)
		{
			return this.switchByteOrder
				? ChunksBufferByteReversing.New(channelChunks, this.loadingBufferSizeProvider)
				: ChunksBuffer.New(channelChunks, this.loadingBufferSizeProvider)
			;
		}

		@Override
		public final ChunksBuffer collectLoadByOids(final ChunksBuffer[] resultArray, final PersistenceIdSet loadOids)
		{
			logger.debug("StorageChannel#{} loading {} references", this.channelIndex, loadOids.size());

			/* it is probably best to start (any maybe continue) with lots of small, memory-agile
			 * byte buffers than to estimate one sufficiently huge bulky byte buffer.
			 */
			final ChunksBuffer chunks = this.createLoadingChunksBuffer(resultArray);
			if(!loadOids.isEmpty())
			{
				// block sweep initiation while collecting so handed-out entities can be gc-protected consistently
				this.entityCache.registerPendingLoad();
				try
				{
					// progress must have been incremented accordingly at task creation time
					loadOids.iterate(this.entityCollectorCreator.create(this.entityCache, chunks));
				}
				finally
				{
					this.entityCache.clearPendingLoad();
				}
			}

			return chunks.complete();
		}

		@Override
		public final ChunksBuffer collectLoadRoots(final ChunksBuffer[] resultArray)
		{
			// pretty straight forward: cram all root instances the entity cache knows of into the buffer
			final ChunksBuffer chunks = this.createLoadingChunksBuffer(resultArray);
			this.entityCache.copyRoots(chunks);
			return chunks.complete();
		}

		@Override
		public final ChunksBuffer collectLoadByTids(final ChunksBuffer[] resultArray, final PersistenceIdSet loadTids)
		{
			final ChunksBuffer chunks = this.createLoadingChunksBuffer(resultArray);
			if(!loadTids.isEmpty())
			{
				// block sweep initiation while collecting so handed-out entities can be gc-protected consistently
				this.entityCache.registerPendingLoad();
				try
				{
					// progress must have been incremented accordingly at task creation time
					loadTids.iterate(new StorageEntityCollector.EntityCollectorByTid(this.entityCache, chunks));
				}
				finally
				{
					this.entityCache.clearPendingLoad();
				}
			}
			return chunks.complete();
		}

		@Override
		public final void exportData(final StorageLiveFileProvider fileProvider)
		{
			this.fileManager.exportData(fileProvider);
		}

		@Override
		public StorageEntityCache.Default prepareImportData()
		{
			// gc coordination first: block new sweeps for the task's duration, quiesce a flagged one
			this.entityCache.registerPendingImportUpdate();
			this.fileManager.prepareImport();
			return this.entityCache;
		}

		@Override
		public void importData(final StorageImportSource importSource)
		{
			this.fileManager.copyData(importSource);
		}

		@Override
		public void rollbackImportData(final Throwable cause)
		{
			this.fileManager.rollbackImport();
		}

		@Override
		public void commitImportData(final long taskTimestamp)
		{
			this.fileManager.commitImport(taskTimestamp);
		}

		@Override
		public void cleanupImportData()
		{
			this.entityCache.clearPendingImportUpdate();
		}

		@Override
		public final KeyValue<Long, Long> exportTypeEntities(
			final StorageEntityTypeHandler         type           ,
			final AWritableFile                    file           ,
			final Predicate<? super StorageEntity> predicateEntity
		)
			throws IOException
		{
			final StorageEntityType.Default entities = this.entityCache.getType(type.typeId());
			if(entities == null || entities.entityCount() == 0)
			{
				return X.KeyValue(0L, 0L);
			}

			final long byteCount = entities.iterateEntities(
				new ThrowingProcedure<StorageEntity.Default, IOException>()
				{
					long byteCount;

					@Override
					public void accept(final StorageEntity.Default e) throws IOException
					{
						if(!predicateEntity.test(e))
						{
							return;
						}
						this.byteCount += e.exportTo(file);
					}
				}
			).byteCount;

			return X.KeyValue(byteCount, entities.entityCount());
		}

		// intentionally implemented redundantly to the other exportTypeEntities for performance reasons
		@Override
		public final KeyValue<Long, Long> exportTypeEntities(
			final StorageEntityTypeHandler type,
			final AWritableFile            file
		)
			throws IOException
		{
			final StorageEntityType.Default entities = this.entityCache.getType(type.typeId());
			if(entities == null || entities.entityCount() == 0)
			{
				return X.KeyValue(0L, 0L);
			}

			final long byteCount = entities.iterateEntities(
				new ThrowingProcedure<StorageEntity.Default, IOException>()
				{
					long byteCount;

					@Override
					public void accept(final StorageEntity.Default e) throws IOException
					{
						this.byteCount += e.exportTo(file);
					}
				}
			).byteCount;

			return X.KeyValue(byteCount, entities.entityCount());
		}

		@Override
		public final StorageRawFileStatistics.ChannelStatistics createRawFileStatistics()
		{
			return this.fileManager.createRawFileStatistics();
		}

		@Override
		public final void rollbackChunkStorage()
		{
			this.fileManager.rollbackWrite();
		}

		@Override
		public final StorageInventory readStorage()
		{
			return this.fileManager.readStorage();
		}

		@Override
		public final StorageIdAnalysis initializeStorage(
			final long             taskTimestamp           ,
			final long             consistentStoreTimestamp,
			final StorageInventory storageInventory
		)
		{
			return this.fileManager.initializeStorage(
				taskTimestamp           ,
				consistentStoreTimestamp,
				storageInventory        ,
				this
			);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final void reset()
		{
			this.entityCache.reset();
			this.fileManager.reset();
		}

		@Override
		public final void signalGarbageCollectionSweepCompleted()
		{
			this.fileManager.restartFileCleanupCursor();
		}

		@Override
		public void cleanupStore()
		{
			this.entityCache.clearPendingStoreUpdate();
		}

		@Override
		public final void dispose()
		{
			this.entityCache.reset();
			this.fileManager.dispose();
		}
		
		@Override
		public AdjacencyFiles collectAdjacencyData(final Path exportDirectory) {
			
			logger.debug("Channel {} collecting object references.", this.channelIndex);
												
			final StorageAdjacencyDataExporter adjacencyDataCollector = new StorageAdjacencyDataExporter.Default(
				this.typeDictionary(),
				exportDirectory,
				this.channelIndex
			);
			this.fileManager.iterateStorageFiles(adjacencyDataCollector::exportAdjacencyData);
			
			logger.debug("Channel {} collecting object references finished.", this.channelIndex);
			
			return adjacencyDataCollector.getExportetFiles();
		}
	}

	/**
	 * One discrete unit of incremental housekeeping work executed by a channel between application
	 * tasks (file cleanup, garbage collection, entity-cache eviction, transaction-log cleanup).
	 * <p>
	 * Implementations consult the channel's current housekeeping budget and may defer work to a
	 * future cycle by returning {@code false}, so that the channel keeps responsive to incoming
	 * application tasks even when housekeeping is busy.
	 */
	@FunctionalInterface
	public interface HousekeepingTask
	{
		/**
		 * Performs a housekeeping task with reference to a starting time of the current housekeeping cycle
		 * (typically to make the best effort attempt to not exceed a certain time budget).
		 * Returns {@literal true} if the task was completed (e.g. currently no more work to dor)
		 * or {@literal false} if the task execution had to be interrupted.
		 *
		 * @return whether the task was completed in the given time budget.
		 */
		public boolean perform();
	}

}
