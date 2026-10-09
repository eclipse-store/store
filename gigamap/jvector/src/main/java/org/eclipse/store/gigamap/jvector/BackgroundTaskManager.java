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
     * <p>
     * Every operation carries the graph epoch of the index at the time it was enqueued (see
     * {@code VectorIndex.Default#graphEpoch}). The callbacks drop an operation whose epoch is no longer the
     * index's: its graph was rebuilt from the source of truth, which already holds the mutation, or replaced by a
     * new generation, where the ordinal means another entity.
     */
    private static sealed interface IndexingOperation
        permits IndexingOperation.Add,
                IndexingOperation.Update,
                IndexingOperation.Remove,
                IndexingOperation.BatchAdd
    {
        void execute(Callback callback);

        /** the index's graph epoch when this operation was enqueued */
        long epoch();

        /**
         * Add a node to the HNSW graph.
         */
        record Add(VectorEntry entry, long epoch) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphAdd(this.entry, this.epoch);
            }
        }

        /**
         * Update a node in the HNSW graph (delete + re-add).
         */
        record Update(VectorEntry entry, long epoch) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphUpdate(this.entry, this.epoch);
            }
        }

        /**
         * Remove a node from the HNSW graph.
         */
        record Remove(int ordinal, long epoch) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphRemove(this.ordinal, this.epoch);
            }
        }

        /**
         * Batch add multiple nodes to the HNSW graph.
         * <p>
         * Acquires the builder lock once for the entire batch and marks dirty
         * once with the total count, avoiding per-entry overhead.
         */
        record BatchAdd(List<VectorEntry> entries, long epoch) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphBatchAdd(this.entries, this.epoch);
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
        /**
         * The {@code epoch} of each graph operation is the index's graph epoch when the operation was enqueued;
         * the implementation drops the operation if the epoch has moved since (see {@link IndexingOperation}).
         */
        void applyGraphAdd(VectorEntry entry, long epoch);

        void applyGraphBatchAdd(List<VectorEntry> entries, long epoch);

        void applyGraphUpdate(VectorEntry entry, long epoch);

        void applyGraphRemove(int ordinal, long epoch);

        void markDirtyForBackgroundManagers(int count);

        /**
         * Records that a graph operation failed after its mutation was counted: the graph is behind its
         * witnesses until it is rebuilt from the source of truth. Called from the per-operation catch of the
         * indexing drain (on the worker, or on a persist thread applying the queue inline) and from the index's
         * own synchronous paths, with any locks held: the implementation only sets fields and submits a task.
         *
         * @param cause the failure
         */
        void markGraphIncomplete(Throwable cause);

        /**
         * {@link #markGraphIncomplete(Throwable)} for a failed queued operation, reported after the operation
         * released the builder lock: the implementation ignores the failure if the graph epoch moved since the
         * operation was enqueued, because the graph that failed it has been rebuilt or replaced in the meantime.
         *
         * @param cause the failure
         * @param epoch the operation's epoch
         */
        void markGraphIncomplete(Throwable cause, long epoch);

        /**
         * Rebuilds the graph from the source of truth if a graph operation failed since it was last built,
         * otherwise does nothing. Called on the executor thread after {@link #requestGraphRepair()}, with no
         * lock held; the implementation takes the locks it needs.
         */
        void repairGraph();

        /**
         * Whether this index needs an optimization it cannot earn through the change count.
         * <p>
         * Asked on every scheduled tick, rather than answered once and remembered, because the
         * answer changes with the index: an index restored below the codebook's training minimum
         * says no until enough vectors arrive, and then says yes. A remembered request would have
         * been consumed by the optimization that declined in between, and nothing would ask again.
         *
         * @return true if the next scheduled optimization should run regardless of the threshold
         */
        default boolean needsUnearnedOptimization()
        {
            // The ordinary answer: an index earns its optimizations through the change count.
            return false;
        }

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

    // One repair task at a time: every failed operation of a batch requests one, one rebuild serves them all.
    private final AtomicBoolean                            repairScheduled       ;

    /**
     * Operations enqueued and not yet applied, the ones already polled by the worker included (the queue size
     * misses those). Each one's mutation is already counted in the index's persist witnesses, so while this is not
     * zero the graph is behind them and a persist must not capture it as current.
     */
    private final AtomicInteger                            pendingGraphOps       ;

    // Operations currently executing, on the worker or inline on a persist thread; see hasOpInFlight().
    private final AtomicInteger                            opsInFlight           ;

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
        this.repairScheduled       = new AtomicBoolean(false);
        this.pendingGraphOps       = new AtomicInteger(0);
        this.opsInFlight           = new AtomicInteger(0);

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


    void enqueueAdd(final VectorEntry entry, final long epoch)
    {
        this.enqueue(new IndexingOperation.Add(entry, epoch));
    }

    void enqueueBatchAdd(final List<VectorEntry> entries, final long epoch)
    {
        this.enqueue(new IndexingOperation.BatchAdd(entries, epoch));
    }

    void enqueueUpdate(final VectorEntry entry, final long epoch)
    {
        this.enqueue(new IndexingOperation.Update(entry, epoch));
    }

    void enqueueRemove(final int ordinal, final long epoch)
    {
        this.enqueue(new IndexingOperation.Remove(ordinal, epoch));
    }

    /**
     * Enqueues an indexing operation for background processing.
     */
    private void enqueue(final IndexingOperation op)
    {
        // Before the queue: a reader of pendingGraphOps must never see the op in the queue but not in the count.
        this.pendingGraphOps.incrementAndGet();
        this.indexingQueue.add(op);
        if(this.indexingTaskScheduled.compareAndSet(false, true))
        {
            this.executor.submit(this::processIndexingBatch);
        }
    }

    /**
     * Returns the number of operations enqueued and not yet applied, the one the worker has polled included.
     * <p>
     * Under the index's write lock and monitor nothing is enqueued or applied, so the count is exact up to one:
     * the decrement runs after the callback released the read lock. An over-count costs one redundant rebuild,
     * never a missed op.
     */
    int pendingGraphOps()
    {
        return this.pendingGraphOps.get();
    }

    /**
     * Whether the worker has polled an operation and not finished it. Under the index's write lock such an
     * operation is blocked on the read lock; it cannot be applied inline and is covered by a rebuild instead.
     */
    boolean hasOpInFlight()
    {
        return this.opsInFlight.get() > 0;
    }

    /**
     * Applies the operations still in the queue on the calling thread. For a persist that holds the builder write
     * lock but not yet the parent-map monitor: the callbacks take the read lock, which a write-lock holder may take
     * again, and the worker cannot apply anything in the meantime. Must not be called with the monitor held, because
     * the callbacks are the worker's and may enter a ForkJoinPool whose workers need it (see
     * {@code VectorIndex.Default#drainDeferredBuilderOps}). Does not cover an operation the worker has already
     * polled; {@link #pendingGraphOps()} still counts that one afterwards.
     */
    void applyQueuedOpsInline()
    {
        this.processAllPendingIndexingOps();
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
        // Polled one by one, so each discarded op leaves the pending count exactly once, however the worker
        // interleaves. An op the worker has polled already is not in the queue and stays counted until it ran.
        int discarded = 0;
        while(this.indexingQueue.poll() != null)
        {
            this.pendingGraphOps.decrementAndGet();
            discarded++;
        }
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

    /**
     * Schedules one {@link Callback#repairGraph()} on the executor, unless one is already scheduled. Called from any
     * thread, with any locks held: this only submits a task. The task runs after the batch that is applying
     * operations now, so one rebuild serves every failure of that batch.
     *
     * @return {@code false} if the manager is shut down, so no repair will run on it
     */
    boolean requestGraphRepair()
    {
        if(this.shutdown)
        {
            return false;
        }
        if(this.repairScheduled.compareAndSet(false, true))
        {
            try
            {
                this.executor.submit(this::runGraphRepair);
            }
            catch(final RejectedExecutionException e)
            {
                // shut down between the flag check above and the submit (close(), removeAll())
                this.repairScheduled.set(false);
                return false;
            }
        }
        return true;
    }

    private void runGraphRepair()
    {
        // Reset first: a failure requested while the repair runs must be able to schedule the next one.
        this.repairScheduled.set(false);
        if(this.shutdown)
        {
            // A repair is not worth blocking a shutdown; a shutdown persist writes nothing while the graph is
            // incomplete, and the next load rebuilds from the store.
            return;
        }
        final Callback cb = this.liveCallback();
        if(cb == null)
        {
            return;
        }
        // a failed repair is recorded by the index and reported from search(); nothing else to do here
        this.runGuarded("Graph repair", cb::repairGraph);
    }

    /**
     * Runs the body of a background task. A failure is logged and swallowed, so a periodic task keeps its schedule
     * and a one-off task ends quietly. A {@link VirtualMachineError} is logged and rethrown: the JVM is in trouble,
     * retrying every tick would only repeat the damage, and the executor would end the periodic task silently.
     */
    private void runGuarded(final String task, final Runnable body)
    {
        try
        {
            body.run();
        }
        catch(final VirtualMachineError e)
        {
            LOG.error("{} of '{}' stops after a fatal error: {}", task, this.name, e.getMessage(), e);
            throw e;
        }
        catch(final Throwable t)
        {
            LOG.error("{} of '{}' failed: {}", task, this.name, t.getMessage(), t);
        }
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
     * Forces the next persistence check to persist, regardless of how many changes have
     * accumulated.
     * <p>
     * Used when the on-disk graph was rejected at load: the rebuilt in-memory graph has to
     * replace the stale files, but a rejection is not a change and so would never trip the
     * {@code minChangesBetweenPersists} threshold on its own. Without this a read-mostly index
     * would rebuild from the store on every restart.
     */
    void markPersistRequired()
    {
        this.persistenceChangeCount.updateAndGet(count -> Math.max(count, this.persistenceMinChanges));
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
                // The shutdown work (drain / optimize / persist) is still running and has exceeded its
                // budget. Aborting it now is safe: an on-disk index writes its graph atomically and
                // self-heals from the store on next load, so no torn file or data loss results.
                // Interrupt promptly rather than waiting a second full grace window, so shutdown is not
                // pinned by work that is being discarded.
                timedOut = true;
                LOG.warn("Shutdown work timed out for '{}' after {} ms; interrupting it so shutdown can "
                    + "proceed (a pending on-disk persist, if any, self-heals from the store on next load)",
                    this.name, this.shutdownPersistTimeoutMillis);
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
     * Shuts the manager down without waiting for its executor thread: drops pending work, cancels the
     * recurring tasks and stops the executor. For a caller holding locks that a running task may be waiting
     * for (internalRemoveAll holds the builder write lock and the parent-map monitor): waiting for that task
     * would only stall until the termination grace period expires. A running task finishes once those locks
     * are released.
     */
    void shutdownWithoutWaiting()
    {
        this.shutdown = true;
        this.cancelScheduledTasks();
        this.discardQueue();
        this.executor.shutdown(); // no awaitTermination, see above
        LOG.info("Background task manager shut down without waiting for '{}'", this.name);
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
        this.discardQueue();
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
            this.opsInFlight.incrementAndGet();
            try
            {
                op.execute(cb);
            }
            catch(final Throwable t)
            {
                // The op is polled and lost either way, but its mutation was counted when it was enqueued: the
                // graph is behind its witnesses. Logged first, then recorded so a persist rebuilds instead of
                // capturing, and a repair can be scheduled. An Error is recorded too and rethrown. With the op's
                // epoch: the op released the builder lock before this catch, and a rebuild in between already
                // accounts for its mutation.
                LOG.error("Error applying indexing operation for '{}', the graph is rebuilt from the source of"
                    + " truth: {}", this.name, t.getMessage(), t);
                cb.markGraphIncomplete(t, op.epoch());
                if(t instanceof Error)
                {
                    throw (Error)t;
                }
            }
            finally
            {
                // After the op ran, not when it was polled: a polled op blocked on the read lock is still pending.
                this.opsInFlight.decrementAndGet();
                this.pendingGraphOps.decrementAndGet();
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

        final Callback cb = this.liveCallback();
        if(cb == null)
        {
            return; // index abandoned; liveCallback() has already self-terminated the manager
        }

        // Either enough has changed, or the index needs an optimization it cannot earn that way.
        // The second is a question asked fresh each tick rather than a request held somewhere, so
        // it cannot be consumed by an optimization that did not do what it was needed for.
        if(this.optimizationChangeCount.get() < this.optimizationMinChanges
            && !cb.needsUnearnedOptimization())
        {
            return;
        }

        LOG.debug("Background optimizing index '{}' with {} changes",
            this.name, this.optimizationChangeCount.get());

        // Guarded: a throwable escaping a scheduled task cancels the task for good, and an Error out of the inline
        // drain would silently end background optimization. The drain has already recorded the failure for the repair.
        this.runGuarded("Background optimization", () ->
        {
            // Drain pending indexing ops inline (same thread, no deadlock)
            this.processAllPendingIndexingOps();

            cb.doOptimize();

            this.optimizationChangeCount.set(0);
            this.optimizationCount.incrementAndGet();

            LOG.debug("Background optimization completed for '{}'", this.name);
        });
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

        this.runGuarded("Background persistence", () ->
        {
            // Drain pending indexing ops inline (same thread, no deadlock)
            this.processAllPendingIndexingOps();

            cb.doPersistToDisk(false);

            this.persistenceChangeCount.set(0);

            LOG.debug("Background persistence completed for '{}'", this.name);
        });
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
