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

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Predicate;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceIdSet;
import org.eclipse.serializer.util.UtilStackTrace;
import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.exceptions.StorageExceptionDisruptingExceptions;
import org.eclipse.store.storage.exceptions.StorageExceptionNotRunning;

public interface StorageTaskBroker
{
	public StorageTask currentTask();

	public StorageRequestTaskLoadRoots enqueueRootsLoadTask()
		throws InterruptedException;

	public StorageRequestTaskLoadByTids enqueueLoadTaskByTids(PersistenceIdSet loadTids)
		throws InterruptedException;

	public StorageRequestTaskLoadByOids enqueueLoadTaskByOids(PersistenceIdSet[] loadOids)
		throws InterruptedException;
	
	public StorageRequestTaskStoreEntities enqueueStoreTask(Binary data)
		throws InterruptedException;

	public default StorageRequestTaskExportEntitiesByType enqueueExportTypesTask(
		final StorageEntityTypeExportFileProvider exportFileProvider
	)
		throws InterruptedException
	{
		return this.enqueueExportTypesTask(exportFileProvider, null);
	}
	
	public StorageRequestTaskExportEntitiesByType enqueueExportTypesTask(
		StorageEntityTypeExportFileProvider         exportFileProvider,
		Predicate<? super StorageEntityTypeHandler> isExportType
	)
		throws InterruptedException;
	
	

	public StorageRequestTask enqueueExportChannelsTask(
		StorageLiveFileProvider fileProvider             ,
		boolean             performGarbageCollection
	)
		throws InterruptedException;

	public StorageRequestTask enqueueImportFromFilesTask(XGettingEnum<AFile> importFiles)
		throws InterruptedException;
	
	public StorageRequestTask enqueueImportFromByteBuffersTask(XGettingEnum<ByteBuffer> importData)
		throws InterruptedException;

	public StorageRequestTaskCreateStatistics enqueueCreateRawFileStatisticsTask()
		throws InterruptedException;

	public StorageChannelTaskInitialize issueChannelInitialization(
		StorageOperationController operationController
	)
		throws InterruptedException;

	public StorageChannelTaskShutdown issueChannelShutdown(StorageOperationController operationController)
		throws InterruptedException;

	public StorageRequestTaskGarbageCollection issueGarbageCollection(long nanoTimeBudget)
		throws InterruptedException;

	public StorageRequestTaskFileCheck issueFileCheck(long nanoTimeBudget)
		throws InterruptedException;

	public StorageRequestTaskCacheCheck issueCacheCheck(
		long                        nanoTimeBudget ,
		StorageEntityCacheEvaluator entityEvaluator
	)
		throws InterruptedException;

	public StorageRequestTaskIntegrityCheck issueIntegrityCheck(long nanoTimeBudget, boolean freshScan)
		throws InterruptedException;

	public StorageRequestTaskTransactionsLogCleanup issueTransactionsLogCleanup()
		throws InterruptedException;

	/**
	 * Enqueues a GATE-REQUESTED all-channel storage flush (durability barrier) with its
	 * durability-maintenance task directly behind it, coalescing onto a pending
	 * maintenance-carrying barrier when one exists; see
	 * {@link StorageRequestTaskStorageFlush#carriesMaintenance()} for the flavor invariant.
	 * Named for the coalescing: the returned barrier may be an EARLIER pending one that does
	 * not cover stores enqueued after it - callers wanting a fresh durability point must use
	 * {@link #issueOnDemandStorageFlush()}. Does not wait for the tasks' completion, so it is
	 * safe to call from a channel thread (the channel processes them in its own work loop).
	 *
	 * @return the enqueued or coalesced-onto barrier task.
	 * @throws InterruptedException if interrupted while enqueueing.
	 */
	public StorageRequestTaskStorageFlush issueCoalescingStorageFlush()
		throws InterruptedException;

	/**
	 * Enqueues a fresh, PURE all-channel storage flush and returns it for the caller to wait on:
	 * file synchronization and watermark raise only, no maintenance task behind it. Unlike
	 * {@link #issueCoalescingStorageFlush()}, the barrier is never coalesced onto an earlier pending one:
	 * stores enqueued after that earlier barrier would sit behind it in the task queue and thus
	 * not be covered by its synchronization. On the barrier's completion, every store enqueued
	 * before this call is durable on every channel.
	 *
	 * @return the enqueued barrier task.
	 * @throws InterruptedException if interrupted while enqueueing.
	 */
	public StorageRequestTaskStorageFlush issueOnDemandStorageFlush()
		throws InterruptedException;

	public StorageRequestTaskExportAdjacencyData exportAdjacencyData(Path workingDir)
		throws InterruptedException;
	
	public StorageOperationController operationController();

	public final class Default implements StorageTaskBroker
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		// can't have a strong reference to StorageManager since that would prevent automatic shutdown
		private final StorageOperationController    operationController   ;
		private final StorageDataFileEvaluator      fileEvaluator         ;
		private final StorageObjectIdRangeEvaluator objectIdRangeEvaluator;
		private final StorageRequestTaskCreator     taskCreator           ;
		private final int                           channelCount          ;

		// WEAK reference to the StorageSystem: the broker is reachable from the live,
		// non-daemon channel threads (Thread -> StorageChannel -> taskBroker), so a strong reference
		// here would keep the StorageSystem strongly reachable and defeat auto-shutdown-by-unreach-
		// ability - the StorageOperationController's WeakReference<StorageSystem> could never clear,
		// leaking the channel threads and the off-heap entity cache when an application drops its
		// manager without shutdown(). Held only to lazily reach the shared mark monitor at load-task
		// enqueue time (the monitor does not yet exist when this broker is constructed); during normal
		// operation the application/manager keeps the system strongly reachable, so it resolves.
		private final WeakReference<StorageSystem>  storageSystemReference;

		private volatile StorageTask currentHead;

		// the most recently enqueued maintenance-carrying storage flush and its adjacent
		// maintenance task, kept to coalesce concurrent gate requests and to detect tail-adjacency
		// (see issueCoalescingStorageFlush / issueOnDemandStorageFlush). Only maintenance-carrying
		// barriers register here (the coalesce condition additionally checks carriesMaintenance()
		// - the canonical invariant lives on that accessor). Accessed only under this broker's
		// monitor.
		private StorageRequestTaskStorageFlush         pendingStorageFlush      ;
		private StorageRequestTaskDurabilityMaintenance pendingStorageMaintenance;

		// set once the shutdown task is issued: from then on enqueueTask rejects every enqueue, so no
		// task can chain after the shutdown barrier. Such a task would leave a channel parked in
		// awaitNext, missing shutdown's endAwaitNext wake (it targets only the shutdown task) and
		// stalling shutdown. Accessed only under this broker's monitor (every enqueue is synchronized).
		private boolean channelShutdownIssued;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final StorageRequestTaskCreator     taskCreator           ,
			final StorageOperationController    operationController   ,
			final StorageDataFileEvaluator      fileEvaluator         ,
			final StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
			final int                           channelCount          ,
			final StorageSystem                 storageSystem
		)
		{
			super();
			this.taskCreator            = notNull(taskCreator);
			this.operationController    = notNull(operationController);
			this.fileEvaluator          = notNull(fileEvaluator);
			this.objectIdRangeEvaluator = notNull(objectIdRangeEvaluator);
			this.channelCount           =         channelCount;
			this.storageSystemReference = new WeakReference<>(notNull(storageSystem));
			this.currentHead            = new StorageTask.DummyTask();
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		private StorageRequestTaskGarbageCollection enqueueTaskPrependingFullGc(
			final StorageTask task          ,
			final long        nanoTimeBudget
		)
			throws InterruptedException
		{
			final StorageRequestTaskGarbageCollection gcTask;
			this.enqueueTasksAndNotifyAll(
				gcTask = new StorageRequestTaskGarbageCollection.Default(
					task.timestamp() - 1,
					this.channelCount   ,
					nanoTimeBudget      ,
					task                ,
					this.operationController
				),
				task
			);
			return gcTask;
		}

		private synchronized void enqueueTasksAndNotifyAll(
			final StorageTask firstTask ,
			final StorageTask secondTask
		)
			throws InterruptedException
		{
			/* The first task is the next task to be processed, the second task is the new head task, i.e.
			 * the new last task that gets future tasks attached to.
			 * It is the first task's responsibility to (eventually) lead to the second task in order to
			 * close the task chain.
			 */
			final StorageTask currentHead = this.enqueueTask(firstTask, secondTask);

			// notify waiting threads via current head
			synchronized(currentHead)
			{
				currentHead.notifyAll();
			}
		}

		private void enqueueTaskAndNotifyAll(final StorageTask task) throws InterruptedException
		{
			final StorageTask currentHead = this.enqueueTask(task);
			synchronized(currentHead)
			{
				currentHead.notifyAll();
			}
		}

		private StorageTask enqueueTask(final StorageTask task)
		{
			return this.enqueueTask(task, task);
		}

		private StorageTask enqueueTask(final StorageTask nextTask, final StorageTask newHeadTask)
		{
			/* (12.06.2019 TM)NOTE:
			 * prevents application threads from waiting forever for a storage
			 * that is already shutdown due to an error (e.g. IO-location not reachable).
			 */
			if(!this.operationController.checkProcessingEnabled())
			{
				throw new StorageExceptionNotRunning("Storage is shut down.");
			}

			// No task may follow the shutdown task: it would chain behind the shutdown barrier (whose
			// succeed() resets the channel) and leave a channel parked in awaitNext on it, missing
			// shutdown's endAwaitNext wake. Reject like a disabled controller; the sole channel-driven
			// enqueue (the storage flush) treats this as a benign shutdown signal, see StorageChannel.
			if(this.channelShutdownIssued)
			{
				throw new StorageExceptionNotRunning("Storage shutdown has been initiated.");
			}

			return this.uncheckedEnqueueTask(nextTask, newHeadTask);
		}
		
		private StorageTask uncheckedEnqueueTask(final StorageTask nextTask, final StorageTask newHeadTask)
		{
			/* (15.02.2019 TM)FIXME: That single-head queue is dangerous. Probably the source for some hangups.
			 * Just build a proper queue with head and tail, ffs.
			 */
			final StorageTask currentHead;
			(currentHead = this.currentHead).setNext(nextTask);
			this.currentHead = newHeadTask;
			return currentHead;
		}

		@Override
		public final StorageTask currentTask()
		{
			return this.currentHead;
		}

		@Override
		public final synchronized StorageRequestTaskGarbageCollection issueGarbageCollection(
			final long nanoTimeBudget
		)
			throws InterruptedException
		{
			final StorageRequestTask dummy =
				new StorageChannelSynchronizingTask.AbstractCompletingTask.Dummy(this.channelCount, this.operationController)
			;
			final StorageRequestTaskGarbageCollection gcTask =
				this.enqueueTaskPrependingFullGc(dummy, nanoTimeBudget)
			;
			return gcTask;
		}

		@Override
		public final synchronized StorageRequestTaskCacheCheck issueCacheCheck(
			final long                        nanoTimeBudget ,
			final StorageEntityCacheEvaluator entityEvaluator
		)
			throws InterruptedException
		{
			final StorageRequestTaskCacheCheck task = this.taskCreator.createFullCacheCheckTask(
				this.channelCount,
				nanoTimeBudget,
				entityEvaluator,
				this.operationController
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageRequestTaskFileCheck issueFileCheck(
			final long nanoTimeBudget
		)
			throws InterruptedException
		{
			final StorageRequestTaskFileCheck task = this.taskCreator.createFullFileCheckTask(
				this.channelCount,
				nanoTimeBudget,
				this.operationController
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageRequestTaskIntegrityCheck issueIntegrityCheck(
			final long    nanoTimeBudget,
			final boolean freshScan
		)
			throws InterruptedException
		{
			final StorageRequestTaskIntegrityCheck task = this.taskCreator.createIntegrityCheckTask(
				this.channelCount,
				nanoTimeBudget,
				freshScan,
				this.operationController
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}
		
		@Override
		public final synchronized StorageRequestTaskTransactionsLogCleanup issueTransactionsLogCleanup()
			throws InterruptedException
		{
			final StorageRequestTaskTransactionsLogCleanup task = this.taskCreator.CreateTransactionsLogCleanupTask(
				this.channelCount,
				this.operationController
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageRequestTaskStorageFlush issueCoalescingStorageFlush()
			throws InterruptedException
		{
			/*
			 * A PURE barrier at the queue tail is the best target: appending the maintenance task
			 * directly behind it under this monitor reproduces the gate pair's adjacency exactly
			 * (nothing can sit between the tail and the append), on the NEWEST barrier - covering
			 * more stores than a pending older pair would - and saves the all-channel fsync a
			 * separate gate barrier would cost. The barrier is upgraded and registered only after
			 * the append succeeded, so the flavor flag and the coalescing-target invariant stay
			 * truthful. Note: despite its name, currentHead is the most recently enqueued task.
			 */
			if(this.currentHead instanceof StorageRequestTaskStorageFlush
				&& !((StorageRequestTaskStorageFlush)this.currentHead).carriesMaintenance()
			)
			{
				final StorageRequestTaskStorageFlush tailBarrier =
					(StorageRequestTaskStorageFlush)this.currentHead;
				final StorageRequestTaskDurabilityMaintenance maintenance = this.enqueueMaintenanceTask();
				if(maintenance != null)
				{
					tailBarrier.upgradeToCarryMaintenance();
					this.pendingStorageFlush       = tailBarrier;
					this.pendingStorageMaintenance = maintenance;
				}
				return tailBarrier;
			}

			// Coalesce concurrent requests: a flush already queued and not yet fully processed covers
			// every channel's deferred gate in this wave, so N channels deferring in the same cycle
			// share one barrier instead of enqueuing N (each otherwise fsynced by all N channels).
			// Stores issued after it re-request next cycle. The carriesMaintenance check is the
			// machine-guard of the flavor invariant (canonical statement on that accessor): only the
			// gate path registers the field, so it is normally redundant - but a future change
			// registering a pure barrier then degrades to enqueueing a fresh pair here instead of
			// silently starving the gate's deferred work.
			if(this.pendingStorageFlush != null
				&& !this.pendingStorageFlush.isProcessed()
				&& this.pendingStorageFlush.carriesMaintenance()
			)
			{
				return this.pendingStorageFlush;
			}

			return this.enqueueStorageFlushWithMaintenance();
		}

		@Override
		public final synchronized StorageRequestTaskStorageFlush issueOnDemandStorageFlush()
			throws InterruptedException
		{
			/*
			 * Tail-adjacent reuse: if the pending gate pair's maintenance task is still the queue
			 * tail, nothing was enqueued after its barrier, so that barrier covers every store
			 * enqueued before this call - exactly a fresh barrier's contract, without the second
			 * all-channel fsync. Only an unprocessed barrier is reused: a processed one may have
			 * skipped under a writability state a fresh barrier would not repeat.
			 */
			if(this.pendingStorageFlush != null
				&& !this.pendingStorageFlush.isProcessed()
				&& this.currentHead == this.pendingStorageMaintenance
			)
			{
				return this.pendingStorageFlush;
			}

			/*
			 * A PURE barrier: file synchronization and watermark raise only - no maintenance task
			 * behind it, and deliberately NOT registered as a coalescing target (see
			 * StorageRequestTaskStorageFlush#carriesMaintenance() for the flavor invariant; a gate
			 * request finding this barrier at the tail may append the maintenance and upgrade it).
			 * This issuer's contract is pure durability; deferred work it incidentally enables (the
			 * raised watermark) is picked up when the durability gates next run.
			 */
			return this.enqueueStorageFlush(false);
		}

		/**
		 * Enqueues a storage-flush barrier and its durability-maintenance task as an adjacent pair
		 * under this broker's monitor (all enqueues are synchronized here): no store can be enqueued
		 * between them, so the maintenance provably runs with the barrier's watermark still covering
		 * every processed store - the property the durability gates rely on. Kept as separate tasks
		 * so the barrier's issuers wake at pure file-synchronization latency and the two concerns
		 * stay independent (the maintenance is self-guarded if the barrier fails or skips). For the
		 * flavor invariant see {@link StorageRequestTaskStorageFlush#carriesMaintenance()}.
		 */
		private StorageRequestTaskStorageFlush enqueueStorageFlushWithMaintenance()
			throws InterruptedException
		{
			final StorageRequestTaskStorageFlush task = this.enqueueStorageFlush(true);

			final StorageRequestTaskDurabilityMaintenance maintenance = this.enqueueMaintenanceTask();
			if(maintenance != null)
			{
				// registered only AFTER the maintenance enqueue succeeded: every coalescing target
				// provides the adjacent maintenance slot, without exception. No coalescer can observe
				// the intermediate state - this whole method runs under the broker's monitor.
				this.pendingStorageFlush       = task;
				this.pendingStorageMaintenance = maintenance;
			}

			return task;
		}

		/** The single construction site for flush barriers of either flavor. */
		private StorageRequestTaskStorageFlush enqueueStorageFlush(final boolean carriesMaintenance)
			throws InterruptedException
		{
			final StorageRequestTaskStorageFlush task = this.taskCreator.createStorageFlushTask(
				this.channelCount,
				this.operationController,
				carriesMaintenance
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}

		/**
		 * Enqueues a durability-maintenance task at the queue tail, or returns {@code null} if the
		 * storage began shutting down or was disrupted (the operation controller's state is not
		 * guarded by this broker's monitor): the preceding barrier then still provides its
		 * durability guarantee to waiting issuers, the skipped maintenance is moot (its gates would
		 * re-defer and re-arm on a dead storage anyway), and the caller must NOT register the
		 * barrier as a coalescing target nor upgrade its flavor flag.
		 */
		private StorageRequestTaskDurabilityMaintenance enqueueMaintenanceTask()
			throws InterruptedException
		{
			try
			{
				final StorageRequestTaskDurabilityMaintenance maintenance =
					this.taskCreator.createDurabilityMaintenanceTask(
						this.channelCount,
						this.operationController
					);
				this.enqueueTaskAndNotifyAll(maintenance);
				return maintenance;
			}
			catch(final StorageExceptionNotRunning | StorageExceptionDisruptingExceptions e)
			{
				return null;
			}
		}

		@Override
		public final synchronized StorageRequestTaskExportAdjacencyData exportAdjacencyData(final Path exportDirectory)
			throws InterruptedException
		{
			final StorageRequestTaskExportAdjacencyData task = this.taskCreator.createExportAdjacencyDataTask(
				this.channelCount,
				this.operationController,
				exportDirectory
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageRequestTask enqueueExportChannelsTask(
			final StorageLiveFileProvider fileProvider             ,
			final boolean             performGarbageCollection
		)
			throws InterruptedException
		{
			final StorageRequestTaskExportChannels task = this.taskCreator.createTaskExportChannels(
				this.channelCount,
				fileProvider,
				this.operationController
			);

			/*
			 * If the data shall "just" be exported as fast as possible and potential unreachable entities
			 * are not a problem, then not performing the GC is preferable.
			 * If the exported data shall represent a definite minimum of all reachable entities and the
			 * required time for a full GC is not an issue (e.g. nightly chronjob), then performing the GC
			 * is preferable.
			 * Both cases are equally viable depending on the situation. Hence, the required flag.
			 */
			if(performGarbageCollection)
			{
				// enqueue task with a prepended full GC
				this.enqueueTaskPrependingFullGc(task, Long.MAX_VALUE); // must let GC complete to get viable results
			}
			else
			{
				// enqueue task directly
				this.enqueueTaskAndNotifyAll(task);
			}

			/*
			 * in both cases, the actual task is the last to be processed, so the calling thread
			 * must always wait on the actual task.
			 */

			return task;
		}

		@Override
		public final synchronized StorageRequestTask enqueueImportFromFilesTask(final XGettingEnum<AFile> importFiles)
			throws InterruptedException
		{
			// always use the internal evaluator to match live operation
			final StorageRequestTaskImportDataFiles task = this.taskCreator.createImportFromFilesTask(
				this.channelCount          ,
				this.fileEvaluator         ,
				this.objectIdRangeEvaluator,
				importFiles                ,
				this.operationController
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}
		
		@Override
		public final synchronized StorageRequestTask enqueueImportFromByteBuffersTask(final XGettingEnum<ByteBuffer> importData)
			throws InterruptedException
		{
			// always use the internal evaluator to match live operation
			final StorageRequestTaskImportDataByteBuffers task = this.taskCreator.createImportFromByteBuffersTask(
				this.channelCount          ,
				this.fileEvaluator         ,
				this.objectIdRangeEvaluator,
				importData                 ,
				this.operationController
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageRequestTaskCreateStatistics enqueueCreateRawFileStatisticsTask() throws InterruptedException
		{
			final StorageRequestTaskCreateStatistics task = this.taskCreator.createCreateRawFileStatisticsTask(
				this.channelCount, this.operationController
			);
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}


		@Override
		public final synchronized StorageRequestTaskExportEntitiesByType enqueueExportTypesTask(
			final StorageEntityTypeExportFileProvider         exportFileProvider,
			final Predicate<? super StorageEntityTypeHandler> isExportType
		)
			throws InterruptedException
		{
			final StorageRequestTaskExportEntitiesByType task = this.taskCreator.createExportTypesTask(
				this.channelCount ,
				exportFileProvider,
				isExportType,
				this.operationController
			);

			// must let GC complete to get viable results
			this.enqueueTaskPrependingFullGc(task, Long.MAX_VALUE);

			// return actual task
			return task;
		}
		
		/**
		 * The task broker cannot rely on any outside logic to pass an array with valid length or validate its length.
		 * Every channel-count-depending array must be validated right before it is enqueued as a task to prevent
		 * the system from crashing.
		 */
		private void validateChannelCount(final int channelCount)
		{
			if(channelCount != this.channelCount)
			{
				throw UtilStackTrace.cutStacktraceByOne(new StorageException(
					"Invalid channel count, given: " + channelCount +
					", expected: " + this.channelCount
				));
			}
		}

		@Override
		public final synchronized StorageRequestTaskStoreEntities enqueueStoreTask(final Binary data)
			throws InterruptedException
		{
			this.validateChannelCount(data.channelCount());
			
			// task creation must be called AFTER acquiring the lock to ensure temporal consistency in the task chain
			final StorageRequestTaskStoreEntities task = this.taskCreator.createSaveTask(data, this.operationController);
			
			this.enqueueTaskAndNotifyAll(task);
			return task;
		}

		/**
		 * Arm the task-scoped pending-load gate for a load task and enqueue it. The task is given the
		 * shared mark monitor (so it can clear the gate when it completes on all channels, via
		 * {@link StorageRequestTaskLoad.Abstract#onLastCompletion()}), then the gate is signaled BEFORE
		 * the task becomes visible to any channel: a channel mid-housekeeping when the load
		 * is enqueued then observes the gate at its next sweep-initiation check and cannot initiate a
		 * sweep in the enqueue -> pickup gap. If the task does not arm the gate (a custom load task that
		 * would not clear it - see {@link StorageRequestTaskLoad#registerPendingLoadTaskGate}), the gate
		 * is not signaled, so it cannot leak. If the enqueue itself fails (e.g. StorageExceptionNotRunning
		 * while processing is disabled), the task will never be processed to completion, so the gate is
		 * released here.
		 */
		private void enqueueLoadTaskAndNotifyAll(final StorageRequestTaskLoad task) throws InterruptedException
		{
			// resolve the system through the weak reference (internal#97): during normal operation the
			// application/manager keeps it strongly reachable, so this returns non-null; a null means the
			// storage was abandoned (GC'd) without shutdown(), where there is nothing to enqueue against.
			final StorageSystem system = this.storageSystemReference.get();
			if(system == null)
			{
				throw new StorageExceptionNotRunning(
					"Storage is shut down: the StorageSystem has been garbage-collected"
					+ " (the manager was abandoned without shutdown())."
				);
			}
			// entityMarkMonitor() throws if the system is not running; do it before signaling anything.
			final StorageEntityMarkMonitor markMonitor = system.entityMarkMonitor();
			final boolean armed = task.registerPendingLoadTaskGate(markMonitor);
			if(armed)
			{
				markMonitor.signalPendingLoadTask();
			}
			try
			{
				this.enqueueTaskAndNotifyAll(task);
			}
			catch(final Throwable t)
			{
				if(armed)
				{
					markMonitor.clearPendingLoadTask();
				}
				throw t;
			}
		}

		@Override
		public final synchronized StorageRequestTaskLoadByOids enqueueLoadTaskByOids(
			final PersistenceIdSet[] loadOids
		)
			throws InterruptedException
		{
			this.validateChannelCount(loadOids.length);

			// task creation must be called AFTER acquiring the lock to ensure temporal consistency in the task chain
			final StorageRequestTaskLoadByOids task = this.taskCreator.createLoadTaskByOids(
				loadOids, this.operationController
			);
			this.enqueueLoadTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageRequestTaskLoadRoots enqueueRootsLoadTask() throws InterruptedException
		{
			// task creation must be called AFTER acquiring the lock to ensure temporal consistency in the task chain
			final StorageRequestTaskLoadRoots task = this.taskCreator.createRootsLoadTask(
				this.channelCount, this.operationController
			);
			this.enqueueLoadTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageRequestTaskLoadByTids enqueueLoadTaskByTids(
			final PersistenceIdSet loadTids
		)
			throws InterruptedException
		{
			// task creation must be called AFTER acquiring the lock to ensure temporal consistency in the task chain
			final StorageRequestTaskLoadByTids task = this.taskCreator.createLoadTaskByTids(
				loadTids, this.channelCount, this.operationController
			);
			this.enqueueLoadTaskAndNotifyAll(task);
			return task;
		}

		@Override
		public final synchronized StorageChannelTaskInitialize issueChannelInitialization(
			final StorageOperationController operationController

		)
			throws InterruptedException
		{
			final StorageChannelTaskInitialize task = this.taskCreator.createInitializationTask(
				this.channelCount  ,
				operationController
			);
			
			/* (12.06.2019 TM)NOTE:
			 * Even more special case:
			 * Cannot check for running storage in the initialization that will cause it to run.
			 * Plus the old special case:
			 * Cannot wait on the task before the channel threads are started
			 */
			final StorageTask currentHead = this.uncheckedEnqueueTask(task, task);
			synchronized(currentHead)
			{
				currentHead.notifyAll();
			}
			
			return task;
		}

		@Override
		public final synchronized StorageChannelTaskShutdown issueChannelShutdown(
			final StorageOperationController operationController
		)
			throws InterruptedException
		{
			final StorageChannelTaskShutdown task = this.taskCreator.createShutdownTask(
				this.channelCount  ,
				operationController
			);
			// special case: cannot wait on the task before the channel threads are started
			this.enqueueTaskAndNotifyAll(task);

			// From here enqueueTask rejects every further enqueue, keeping the shutdown task the last
			// task in the chain (see channelShutdownIssued). Set after enqueuing the shutdown task so
			// its own enqueue passes; safe because every enqueue path runs under this broker's monitor.
			this.channelShutdownIssued = true;

			return task;
		}

		@Override
		public StorageOperationController operationController()
		{
			return this.operationController;
		}

	}

	public interface Creator
	{
		public StorageTaskBroker createTaskBroker(
			StorageSystem             storageSystem,
			StorageRequestTaskCreator taskCreator
		);



		public final class Default implements Creator
		{
			public Default()
			{
				super();
			}
			
			@Override
			public StorageTaskBroker createTaskBroker(
				final StorageSystem             storageSystem,
				final StorageRequestTaskCreator taskCreator
			)
			{
				return new StorageTaskBroker.Default(
					taskCreator,
					storageSystem.operationController(),
					storageSystem.configuration().dataFileEvaluator(),
					storageSystem.objectIdRangeEvaluator(),
					storageSystem.channelCountProvider().getChannelCount(),
					storageSystem
				);
			}

		}
		
	}

}
