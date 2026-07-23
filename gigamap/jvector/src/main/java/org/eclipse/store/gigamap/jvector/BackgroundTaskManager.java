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

import java.lang.ref.WeakReference;
import java.util.List;
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

    /**
     * Interval of the liveness watchdog that self-terminates the executor once the index
     * (the {@link Callback}) has been garbage-collected. Kept short so an abandoned index's
     * daemon thread is reclaimed promptly, independent of the (possibly long) optimization
     * and persistence intervals. Each tick is only a weak {@code get()} plus a flag check.
     */
    private static final long WATCHDOG_INTERVAL_MS = 1_000L;

    /**
     * Short grace window awaited for the executor thread to terminate after {@link #shutdown} has
     * requested it (or after an overrun triggers {@code shutdownNow()}). Bounds how long shutdown
     * blocks once the final work is done or has been aborted.
     */
    private static final long EXECUTOR_TERMINATION_GRACE_SECONDS = 5L;

    // ========================================================================
    // Indexing Operations
    // ========================================================================

    /**
     * Sealed interface for indexing operations that can be queued.
     */
    private static sealed interface IndexingOperation
        permits IndexingOperation.Add,
                IndexingOperation.Update,
                IndexingOperation.Remove,
                IndexingOperation.BatchAdd
    {
        void execute(Callback callback);

        /**
         * Add a node to the HNSW graph.
         */
        record Add(VectorEntry entry) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphAdd(this.entry);
                callback.markDirtyForBackgroundManagers(1);
            }
        }

        /**
         * Update a node in the HNSW graph (delete + re-add).
         */
        record Update(VectorEntry entry) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphUpdate(this.entry);
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

        /**
         * Batch add multiple nodes to the HNSW graph.
         * <p>
         * Acquires the builder lock once for the entire batch and marks dirty
         * once with the total count, avoiding per-entry overhead.
         */
        record BatchAdd(List<VectorEntry> entries) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphBatchAdd(this.entries);
                callback.markDirtyForBackgroundManagers(this.entries.size());
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
        void applyGraphAdd(VectorEntry entry);

        void applyGraphBatchAdd(List<VectorEntry> entries);

        void applyGraphUpdate(VectorEntry entry);

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
         *
         * @param onShutdown {@code true} when invoked on the shutdown path, in which case a
         *                   full-graph consolidation (exit incremental mode) must be skipped so
         *                   shutdown is not blocked by an O(n) rebuild; {@code false} for
         *                   background/explicit persistence, which may consolidate.
         */
        void doPersistToDisk(boolean onShutdown);
    }

    // ========================================================================
    // Instance fields
    // ========================================================================

    /**
     * The index is held <b>weakly</b>: the executor's scheduled tasks (method references on this
     * manager) would otherwise strongly pin the index — and through it the whole HNSW graph — for
     * as long as the daemon thread lives. Holding it weakly lets an abandoned index (dropped
     * without {@code close()}) become collectable; the liveness watchdog then self-terminates the
     * executor once the referent is gone. Mirrors {@code EvictionManager.IntervalThread}.
     */
    private final WeakReference<Callback>     callbackRef ;
    private final String                      name        ;
    private final ScheduledExecutorService    executor    ;

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

    // Upper bound on how long the shutdown persist may run on the executor before it is aborted.
    private final long          shutdownPersistTimeoutMillis;

    // Liveness watchdog: self-terminates the executor when the index has been abandoned (GC'd)
    private ScheduledFuture<?>  watchdogTask          ;

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
        final int      persistenceMinChanges,
        final long     shutdownPersistTimeoutMillis
    )
    {
        this.callbackRef                  = new WeakReference<>(callback);
        this.name                         = name                        ;
        this.shutdownPersistTimeoutMillis = shutdownPersistTimeoutMillis;

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

        // Always run the liveness watchdog. In eventual-indexing-only mode there is no recurring
        // optimization/persistence task, so without this the idle executor thread would survive
        // forever after the index is abandoned. The watchdog guarantees teardown in every mode.
        this.watchdogTask = this.executor.scheduleAtFixedRate(
            this::checkLiveness,
            WATCHDOG_INTERVAL_MS,
            WATCHDOG_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    // ========================================================================
    // Indexing queue methods
    // ========================================================================


    void enqueueAdd(final VectorEntry entry)
    {
        this.enqueue(new IndexingOperation.Add(entry));
    }

    void enqueueBatchAdd(final List<VectorEntry> entries)
    {
        this.enqueue(new IndexingOperation.BatchAdd(entries));
    }

    void enqueueUpdate(final VectorEntry entry)
    {
        this.enqueue(new IndexingOperation.Update(entry));
    }

    void enqueueRemove(final int ordinal)
    {
        this.enqueue(new IndexingOperation.Remove(ordinal));
    }

    /**
     * Enqueues an indexing operation for background processing.
     */
    private void enqueue(final IndexingOperation op)
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

        // Cancel scheduled tasks (optimization, persistence, watchdog)
        this.cancelScheduledTasks();

        // Perform final work if requested
        boolean timedOut = false;
        if(drainPending || optimizePending || persistPending)
        {
            try
            {
                this.executor.submit(() -> this.finalShutdownWork(drainPending, optimizePending, persistPending))
                    .get(this.shutdownPersistTimeoutMillis, TimeUnit.MILLISECONDS);
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
                // The work is still running and has exceeded its budget. Because on-disk writes are
                // atomic and the index self-heals from the store on next load, aborting it now is
                // safe (no torn file, no data loss). Interrupt promptly rather than waiting a second
                // full grace window, so shutdown is not pinned by work that is being discarded.
                timedOut = true;
                LOG.warn("Shutdown work timed out for '{}' after {} ms; aborting so shutdown can proceed "
                    + "(on-disk index self-heals from store on next load)", this.name, this.shutdownPersistTimeoutMillis);
            }
        }

        // Shutdown the executor. If the final work overran its budget, interrupt it immediately;
        // otherwise request a graceful shutdown and give in-flight work a short window to finish.
        if(timedOut)
        {
            this.executor.shutdownNow();
        }
        else
        {
            this.executor.shutdown();
        }
        try
        {
            if(!this.executor.awaitTermination(EXECUTOR_TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS))
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

    /**
     * Cancels the recurring scheduled tasks (optimization, persistence, watchdog) without
     * touching the executor itself. Shared by {@link #shutdown} and {@link #selfTerminate}.
     */
    private void cancelScheduledTasks()
    {
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
        if(this.watchdogTask != null)
        {
            this.watchdogTask.cancel(false);
            this.watchdogTask = null;
        }
    }

    /**
     * Resolves the weakly-held index. If it has been garbage-collected — meaning the index was
     * abandoned without {@code close()} — this self-terminates the manager so the daemon thread
     * and executor are reclaimed, and returns {@code null}. Callers running on the executor
     * thread must skip their work when this returns {@code null}.
     *
     * @return the live {@link Callback}, or {@code null} if the index has been collected
     */
    private Callback liveCallback()
    {
        final Callback cb = this.callbackRef.get();
        if(cb == null && !this.shutdown)
        {
            this.selfTerminate();
        }
        return cb;
    }

    /**
     * Tears the manager down from within the executor thread after the index was abandoned.
     * <p>
     * Unlike {@link #shutdown}, this must not call {@code awaitTermination} — it runs on the very
     * thread being shut down, so awaiting would dead-lock. It only cancels the recurring tasks,
     * drops pending work and calls {@link ExecutorService#shutdown()}; the current task then
     * returns and the daemon thread ends, making the executor and this manager collectable.
     */
    private void selfTerminate()
    {
        this.shutdown = true;
        this.cancelScheduledTasks();
        this.indexingQueue.clear();
        this.executor.shutdown(); // no awaitTermination: we are on the executor thread
        LOG.info("Background task manager self-terminated for abandoned index '{}'", this.name);
    }

    /**
     * Liveness watchdog body. Resolving the weak reference self-terminates the manager when the
     * index has been collected (see {@link #liveCallback()}).
     */
    private void checkLiveness()
    {
        if(!this.shutdown)
        {
            this.liveCallback();
        }
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
        final Callback cb = this.liveCallback();
        if(cb == null)
        {
            return; // index abandoned; liveCallback() has already self-terminated the manager
        }

        IndexingOperation op;
        while((op = this.indexingQueue.poll()) != null)
        {
            try
            {
                op.execute(cb);
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

        final Callback cb = this.liveCallback();
        if(cb == null)
        {
            return; // index abandoned; liveCallback() has already self-terminated the manager
        }

        LOG.debug("Background optimizing index '{}' with {} changes",
            this.name, this.optimizationChangeCount.get());

        try
        {
            // Drain pending indexing ops inline (same thread, no deadlock)
            this.processAllPendingIndexingOps();

            cb.doOptimize();

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

        final Callback cb = this.liveCallback();
        if(cb == null)
        {
            return; // index abandoned; liveCallback() has already self-terminated the manager
        }

        LOG.debug("Background persisting index '{}' with {} changes",
            this.name, this.persistenceChangeCount.get());

        try
        {
            // Drain pending indexing ops inline (same thread, no deadlock)
            this.processAllPendingIndexingOps();

            cb.doPersistToDisk(false);

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

        // Callback may be null if the index was concurrently collected; skip the graph-touching
        // work in that case (nothing to persist/optimize once the index is gone).
        final Callback cb = this.callbackRef.get();
        if(cb == null)
        {
            return;
        }

        if(optimizePending && this.optimizationChangeCount.get() > 0)
        {
            LOG.info("Optimizing pending changes for '{}' before shutdown ({} changes)",
                this.name, this.optimizationChangeCount.get());
            try
            {
                cb.doOptimize();
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
                cb.doPersistToDisk(true);
                this.persistenceChangeCount.set(0);
            }
            catch(final Exception e)
            {
                LOG.error("Shutdown persistence failed for '{}': {}", this.name, e.getMessage(), e);
            }
        }
    }

}
