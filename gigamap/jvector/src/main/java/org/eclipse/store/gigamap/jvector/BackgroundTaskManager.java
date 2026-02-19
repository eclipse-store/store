package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unified background task manager for VectorIndex.
 * <p>
 * Consolidates background indexing, optimization, and persistence into a single
 * {@link ScheduledExecutorService} with one daemon thread. All three workloads
 * serialize on the same builder write-lock and never do useful work in parallel,
 * so a single thread is sufficient.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Queuing and batch processing of graph indexing operations (add, update, remove)</li>
 *   <li>Scheduled background optimization at configurable intervals with debouncing</li>
 *   <li>Scheduled background persistence at configurable intervals with debouncing</li>
 *   <li>Graceful shutdown with optional drain, optimize, and persist</li>
 * </ul>
 */
class BackgroundTaskManager
{
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundTaskManager.class);

    // ========================================================================
    // Indexing Operations
    // ========================================================================

    /**
     * Sealed interface for indexing operations that can be queued.
     */
    sealed interface IndexingOperation
        permits IndexingOperation.Add,
                IndexingOperation.Update,
                IndexingOperation.Remove
    {
        void execute(Callback callback);

        /**
         * Add a node to the HNSW graph.
         */
        record Add(int ordinal, float[] vector) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphAdd(this.ordinal, this.vector);
                callback.markDirtyForBackgroundManagers(1);
            }
        }

        /**
         * Update a node in the HNSW graph (delete + re-add).
         */
        record Update(int ordinal, float[] vector) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphUpdate(this.ordinal, this.vector);
                callback.markDirtyForBackgroundManagers(1);
            }
        }

        /**
         * Remove a node from the HNSW graph.
         */
        record Remove(int ordinal) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphRemove(this.ordinal);
                callback.markDirtyForBackgroundManagers(1);
            }
        }
    }

    // ========================================================================
    // Callback
    // ========================================================================

    /**
     * Callback interface for applying graph operations and core optimization/persistence.
     * Implemented by {@code VectorIndex.Default}.
     */
    interface Callback
    {
        void applyGraphAdd(int ordinal, float[] vector);

        void applyGraphUpdate(int ordinal, float[] vector);

        void applyGraphRemove(int ordinal);

        void markDirtyForBackgroundManagers(int count);

        /**
         * Core optimization logic without queue drain.
         * Called from the executor thread (inline drain already done).
         */
        void doOptimize();

        /**
         * Core persistence logic without queue drain.
         * Called from the executor thread (inline drain already done).
         */
        void doPersistToDisk();
    }

    // ========================================================================
    // Instance fields
    // ========================================================================

    private final Callback                    callback ;
    private final String                      name     ;
    private final ScheduledExecutorService    executor ;

    // Indexing queue and dedup flag
    private final ConcurrentLinkedQueue<IndexingOperation> indexingQueue         ;
    private final AtomicBoolean                            indexingTaskScheduled ;

    // Optimization state
    private final AtomicInteger optimizationChangeCount;
    private final AtomicLong    optimizationCount      ;
    private final int           optimizationMinChanges ;
    private ScheduledFuture<?>  optimizationTask       ;

    // Persistence state
    private final AtomicInteger persistenceChangeCount;
    private final int           persistenceMinChanges ;
    private ScheduledFuture<?>  persistenceTask       ;

    private volatile boolean shutdown = false;

    // ========================================================================
    // Constructor
    // ========================================================================

    BackgroundTaskManager(
        final Callback callback,
        final String   name,
        final boolean  eventualIndexing,
        final boolean  backgroundOptimization,
        final long     optimizationIntervalMs,
        final int      optimizationMinChanges,
        final boolean  backgroundPersistence,
        final long     persistenceIntervalMs,
        final int      persistenceMinChanges
    )
    {
        this.callback = callback;
        this.name     = name    ;

        this.executor = Executors.newSingleThreadScheduledExecutor(r ->
        {
            final Thread t = new Thread(r, "VectorIndex-Background-" + name);
            t.setDaemon(true);
            return t;
        });

        // Indexing
        this.indexingQueue         = new ConcurrentLinkedQueue<>();
        this.indexingTaskScheduled = new AtomicBoolean(false);

        // Optimization
        this.optimizationChangeCount = new AtomicInteger(0);
        this.optimizationCount       = new AtomicLong(0);
        this.optimizationMinChanges  = optimizationMinChanges;

        // Persistence
        this.persistenceChangeCount = new AtomicInteger(0);
        this.persistenceMinChanges  = persistenceMinChanges;

        // Start scheduled tasks
        if(backgroundOptimization)
        {
            this.optimizationTask = this.executor.scheduleAtFixedRate(
                this::runOptimizationIfDirty,
                optimizationIntervalMs,
                optimizationIntervalMs,
                TimeUnit.MILLISECONDS
            );
            LOG.info("Background optimization started for index '{}' with interval {}ms",
                name, optimizationIntervalMs);
        }

        if(backgroundPersistence)
        {
            this.persistenceTask = this.executor.scheduleAtFixedRate(
                this::runPersistenceIfDirty,
                persistenceIntervalMs,
                persistenceIntervalMs,
                TimeUnit.MILLISECONDS
            );
            LOG.info("Background persistence started for index '{}' with interval {}ms",
                name, persistenceIntervalMs);
        }

        if(eventualIndexing)
        {
            LOG.info("Eventual indexing enabled for index '{}'", name);
        }
    }

    // ========================================================================
    // Indexing queue methods
    // ========================================================================

    /**
     * Enqueues an indexing operation for background processing.
     */
    void enqueue(final IndexingOperation op)
    {
        this.indexingQueue.add(op);
        if(this.indexingTaskScheduled.compareAndSet(false, true))
        {
            this.executor.submit(this::processIndexingBatch);
        }
    }

    /**
     * Blocks until all currently enqueued indexing operations have been applied.
     * Called from user threads (not the executor thread) before optimize/persistToDisk.
     */
    void drainQueue()
    {
        if(this.shutdown)
        {
            return;
        }

        try
        {
            this.executor.submit(this::processAllPendingIndexingOps).get();
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while draining indexing queue for '{}'", this.name);
        }
        catch(final ExecutionException e)
        {
            LOG.error("Error while draining indexing queue for '{}': {}", this.name, e.getMessage(), e);
        }
    }

    /**
     * Discards all pending indexing operations without applying them.
     * Used during {@code internalRemoveAll()} where pending operations
     * refer to stale ordinals that are no longer valid.
     */
    void discardQueue()
    {
        final int discarded = this.indexingQueue.size();
        this.indexingQueue.clear();
        this.indexingTaskScheduled.set(false);
        if(discarded > 0)
        {
            LOG.info("Discarded {} pending indexing operations for '{}'", discarded, this.name);
        }
    }

    /**
     * Returns the number of pending indexing operations in the queue.
     */
    int getPendingIndexingCount()
    {
        return this.indexingQueue.size();
    }

    // ========================================================================
    // Optimization monitoring
    // ========================================================================

    /**
     * Marks dirty for optimization and persistence tracking.
     */
    void markDirty(final int count)
    {
        this.optimizationChangeCount.addAndGet(count);
        this.persistenceChangeCount.addAndGet(count);
    }

    /**
     * Returns the number of times optimization has been performed.
     */
    long getOptimizationCount()
    {
        return this.optimizationCount.get();
    }

    /**
     * Returns the current pending change count for optimization.
     */
    int getOptimizationPendingChangeCount()
    {
        return this.optimizationChangeCount.get();
    }

    // ========================================================================
    // Shutdown
    // ========================================================================

    /**
     * Shuts down the background task manager.
     *
     * @param drainPending   if true, drain all pending indexing operations
     * @param optimizePending if true and there are pending changes, optimize before shutdown
     * @param persistPending  if true and there are pending changes, persist before shutdown
     */
    void shutdown(final boolean drainPending, final boolean optimizePending, final boolean persistPending)
    {
        this.shutdown = true;

        // Cancel scheduled tasks
        if(this.optimizationTask != null)
        {
            this.optimizationTask.cancel(false);
            this.optimizationTask = null;
        }
        if(this.persistenceTask != null)
        {
            this.persistenceTask.cancel(false);
            this.persistenceTask = null;
        }

        // Perform final work if requested
        if(drainPending || optimizePending || persistPending)
        {
            try
            {
                this.executor.submit(() -> this.finalShutdownWork(drainPending, optimizePending, persistPending))
                    .get(30, TimeUnit.SECONDS);
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            catch(final ExecutionException e)
            {
                LOG.error("Error during shutdown work for '{}': {}", this.name, e.getMessage(), e);
            }
            catch(final TimeoutException e)
            {
                LOG.warn("Shutdown work timed out for '{}'", this.name);
            }
        }

        // Shutdown the executor
        this.executor.shutdown();
        try
        {
            if(!this.executor.awaitTermination(30, TimeUnit.SECONDS))
            {
                LOG.warn("Background task executor did not terminate gracefully for '{}'", this.name);
                this.executor.shutdownNow();
            }
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        }

        LOG.info("Background task manager shutdown for '{}'", this.name);
    }

    // ========================================================================
    // Internal methods — all run on the executor thread
    // ========================================================================

    /**
     * Processes all pending indexing ops in a batch.
     * Called via {@code executor.submit()} when ops are enqueued.
     */
    private void processIndexingBatch()
    {
        try
        {
            this.processAllPendingIndexingOps();
        }
        finally
        {
            this.indexingTaskScheduled.set(false);
            // Re-check: if new ops were added after we polled the last one
            // but before we reset the flag, schedule another batch.
            if(!this.indexingQueue.isEmpty())
            {
                if(this.indexingTaskScheduled.compareAndSet(false, true))
                {
                    this.executor.submit(this::processIndexingBatch);
                }
            }
        }
    }

    /**
     * Polls and executes all currently queued indexing operations.
     * Safe to call from the executor thread (inline) or via {@code Future.get()} from user threads.
     */
    private void processAllPendingIndexingOps()
    {
        IndexingOperation op;
        while((op = this.indexingQueue.poll()) != null)
        {
            try
            {
                op.execute(this.callback);
            }
            catch(final Exception e)
            {
                LOG.error("Error applying indexing operation for '{}': {}", this.name, e.getMessage(), e);
            }
        }
    }

    /**
     * Runs optimization if the change threshold has been met.
     * Called by the scheduled optimization task on the executor thread.
     */
    private void runOptimizationIfDirty()
    {
        if(this.shutdown)
        {
            return;
        }

        if(this.optimizationChangeCount.get() < this.optimizationMinChanges)
        {
            return;
        }

        LOG.debug("Background optimizing index '{}' with {} changes",
            this.name, this.optimizationChangeCount.get());

        try
        {
            // Drain pending indexing ops inline (same thread, no deadlock)
            this.processAllPendingIndexingOps();

            this.callback.doOptimize();

            this.optimizationChangeCount.set(0);
            this.optimizationCount.incrementAndGet();

            LOG.debug("Background optimization completed for '{}'", this.name);
        }
        catch(final Exception e)
        {
            LOG.error("Background optimization failed for '{}': {}", this.name, e.getMessage(), e);
        }
    }

    /**
     * Runs persistence if the change threshold has been met.
     * Called by the scheduled persistence task on the executor thread.
     */
    private void runPersistenceIfDirty()
    {
        if(this.shutdown)
        {
            return;
        }

        if(this.persistenceChangeCount.get() < this.persistenceMinChanges)
        {
            return;
        }

        LOG.debug("Background persisting index '{}' with {} changes",
            this.name, this.persistenceChangeCount.get());

        try
        {
            // Drain pending indexing ops inline (same thread, no deadlock)
            this.processAllPendingIndexingOps();

            this.callback.doPersistToDisk();

            this.persistenceChangeCount.set(0);

            LOG.debug("Background persistence completed for '{}'", this.name);
        }
        catch(final Exception e)
        {
            LOG.error("Background persistence failed for '{}': {}", this.name, e.getMessage(), e);
        }
    }

    /**
     * Performs final shutdown work on the executor thread.
     */
    private void finalShutdownWork(
        final boolean drainPending,
        final boolean optimizePending,
        final boolean persistPending
    )
    {
        if(drainPending)
        {
            LOG.info("Draining {} pending indexing operations for '{}' before shutdown",
                this.indexingQueue.size(), this.name);
            this.processAllPendingIndexingOps();
        }

        if(optimizePending && this.optimizationChangeCount.get() > 0)
        {
            LOG.info("Optimizing pending changes for '{}' before shutdown ({} changes)",
                this.name, this.optimizationChangeCount.get());
            try
            {
                this.callback.doOptimize();
                this.optimizationChangeCount.set(0);
                this.optimizationCount.incrementAndGet();
            }
            catch(final Exception e)
            {
                LOG.error("Shutdown optimization failed for '{}': {}", this.name, e.getMessage(), e);
            }
        }

        if(persistPending && this.persistenceChangeCount.get() > 0)
        {
            LOG.info("Persisting pending changes for '{}' before shutdown ({} changes)",
                this.name, this.persistenceChangeCount.get());
            try
            {
                this.callback.doPersistToDisk();
                this.persistenceChangeCount.set(0);
            }
            catch(final Exception e)
            {
                LOG.error("Shutdown persistence failed for '{}': {}", this.name, e.getMessage(), e);
            }
        }
    }

}
