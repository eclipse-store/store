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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

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
 * <p>
 * The worker polls and applies each indexing operation while holding the indexing lock (see
 * {@link #lockIndexing()}). A persist that freezes the in-memory graph holds it as well, so at a
 * freeze no operation is ever polled but not yet applied: every operation counted in the witnesses
 * sampled at the freeze is either applied to the frozen graph already or still in the queue, from
 * where the persist takes it ({@link #takePendingOps()}) and applies it to the frozen graph itself.
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

    /**
     * The most indexing operations one turn of the worker applies before it lets the other tasks of
     * the executor run - a persist the flush threshold requested, the scheduled persist and
     * optimization - and continues in a new turn. Under a sustained eventual-indexing ingest the queue
     * never runs empty, so a turn that drained it would keep every other task waiting for as long as
     * the ingest lasts.
     */
    private static final int MAX_OPERATIONS_PER_TURN = 256;

    // ========================================================================
    // Indexing Operations
    // ========================================================================

    /**
     * Sealed interface for indexing operations that can be queued. Visible to the index, which
     * applies the operations a freeze takes from the queue to the frozen graph itself.
     * <p>
     * An operation carries graph ordinals only: the vector is in the index's vector file from the
     * moment the operation is enqueued, and applying it reads the file, so the queue holds no vector
     * on the heap.
     */
    static sealed interface IndexingOperation
        permits IndexingOperation.Add,
                IndexingOperation.Update,
                IndexingOperation.Remove,
                IndexingOperation.BatchAdd
    {
        void execute(Callback callback);

        /**
         * Add a node to the HNSW graph.
         */
        record Add(int ordinal) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphAdd(this.ordinal);
                callback.markDirtyForBackgroundManagers(1);
            }
        }

        /**
         * Update a node in the HNSW graph (delete + re-add).
         */
        record Update(int ordinal) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphUpdate(this.ordinal);
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
        record BatchAdd(int[] ordinals) implements IndexingOperation
        {
            @Override
            public void execute(final Callback callback)
            {
                callback.applyGraphBatchAdd(this.ordinals);
                callback.markDirtyForBackgroundManagers(this.ordinals.length);
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
        void applyGraphAdd(int ordinal);

        void applyGraphBatchAdd(int[] ordinals);

        void applyGraphUpdate(int ordinal);

        void applyGraphRemove(int ordinal);

        void markDirtyForBackgroundManagers(int count);

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
         * Called from the executor thread: a scheduled run first applies one turn of queued indexing
         * operations, not the whole queue; the shutdown run after the queue was drained, if requested.
         */
        void doOptimize();

        /**
         * Core persistence logic without queue drain.
         * Called from the executor thread. The background persist does not drain the queue first: the
         * freeze moves the queued operations into the graph it writes.
         *
         * @param onShutdown {@code true} when invoked on the shutdown path, which writes what changed
         *                   but never consolidates, so shutdown is not blocked by an O(n) rebuild;
         *                   {@code false} for background persistence, which writes only once enough
         *                   has accumulated and may consolidate.
         * @return whether the persist committed what changed, or found nothing to commit; {@code false}
         *         if it left changes for a later persist. The index retires the changes a commit
         *         covered itself (see {@link BackgroundTaskManager#changesPersisted}), which for a
         *         write handed to its flush thread is only once that write completes, so only the
         *         shutdown path, after which nothing is counted any more, relies on this to clear
         *         the count
         */
        boolean doPersistToDisk(boolean onShutdown);

        /**
         * Reports that an indexing operation failed to apply, which leaves the graph without a
         * change the store has. Called after the failure was logged.
         *
         * @param failure the failure
         */
        default void graphOperationFailed(final RuntimeException failure)
        {
            // Nothing by default; the failure is logged already.
        }

        /**
         * Reports that the worker applied an indexing operation to the graph. The index may have
         * reached a size at which it wants a persist that its check at enqueue time, before the
         * operation was applied, could not see.
         */
        default void graphOperationApplied()
        {
            // Nothing by default.
        }
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

    // Held while an indexing operation is polled and applied, and by a freezing persist; see the
    // class comment. Reentrant, because a background persist freezes on the worker thread itself.
    private final ReentrantLock                            indexingLock          ;

    // Deduplicates persist runs requested ahead of the schedule, see requestFlush().
    private final AtomicBoolean                            flushRequested        ;

    // Test-only: run by the worker with the indexing lock held, right before it applies an operation.
    volatile Runnable                                      applyTestHook         ;

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

    // The manager whose worker applies an indexing operation on the current thread, for the duration of
    // the operation, see isApplyingDiscardedOperation().
    private static final ThreadLocal<BackgroundTaskManager> APPLYING = new ThreadLocal<>();

    // Set by shutdownWithoutWaiting(): the index was cleared, so the queued operations are stale, and so
    // is one that the worker polled before and is still applying.
    private volatile boolean discarded;

    // The executor's worker thread. The executor reports itself terminated from inside this thread,
    // shortly before the thread ends, so shutdown joins it to leave no thread behind.
    private volatile Thread executorThread;

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
            this.executorThread = t;
            return t;
        });

        // Indexing
        this.indexingQueue         = new ConcurrentLinkedQueue<>();
        this.indexingTaskScheduled = new AtomicBoolean(false);
        // Fair, so a persist waiting to freeze gets the lock after the operation being applied rather
        // than losing it to the worker's next operation for as long as the queue is not empty.
        this.indexingLock          = new ReentrantLock(true);
        this.flushRequested        = new AtomicBoolean(false);

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


    void enqueueAdd(final int ordinal)
    {
        this.enqueue(new IndexingOperation.Add(ordinal));
    }

    void enqueueBatchAdd(final int[] ordinals)
    {
        this.enqueue(new IndexingOperation.BatchAdd(ordinals));
    }

    void enqueueUpdate(final int ordinal)
    {
        this.enqueue(new IndexingOperation.Update(ordinal));
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
     * Blocks until the indexing operations enqueued when it is called have been applied; operations
     * enqueued meanwhile are not waited for. Called from user threads (not the executor thread), for
     * instance before an explicit optimize.
     */
    void drainQueue()
    {
        if(this.shutdown)
        {
            return;
        }

        // Bounded by what is queued now, which the queue's order puts first: operations enqueued while
        // the drain runs are not waited for, so under a sustained ingest the drain still ends, and
        // the tasks waiting behind it on the executor get their turn.
        final int queued = this.indexingQueue.size();
        try
        {
            this.executor.submit(() -> this.processPendingIndexingOps(queued)).get();
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
     * Takes the indexing lock, which keeps the worker from polling and applying the next operation
     * until {@link #unlockIndexing()}. Waits for the operation being applied, if any. The persist
     * takes it before the parent monitor and {@code builderLock}, and so does the worker, so the two
     * cannot deadlock.
     */
    void lockIndexing()
    {
        this.indexingLock.lock();
    }

    /**
     * Releases the lock taken by {@link #lockIndexing()}.
     */
    void unlockIndexing()
    {
        this.indexingLock.unlock();
    }

    /**
     * Removes every queued indexing operation and returns them, oldest first, for a persist that
     * applies them to the graph it freezes. The caller must hold the indexing lock, so no operation
     * is in flight, and the parent monitor, so none is being enqueued.
     *
     * @return the operations that were queued
     */
    List<IndexingOperation> takePendingOps()
    {
        if(!this.indexingLock.isHeldByCurrentThread())
        {
            throw new IllegalStateException("takePendingOps() requires the indexing lock");
        }
        final List<IndexingOperation> taken = new ArrayList<>();
        IndexingOperation op;
        while((op = this.indexingQueue.poll()) != null)
        {
            taken.add(op);
        }
        return taken;
    }

    /**
     * Requests a background persist run now rather than at the next scheduled one, regardless of
     * the change threshold: the in-memory graph has reached the size at which it is flushed. Cheap
     * enough to call under the parent monitor, and deduplicated while a requested run is pending.
     * Does nothing without background persistence.
     */
    void requestFlush()
    {
        if(this.persistenceTask == null || this.shutdown)
        {
            return;
        }
        if(this.flushRequested.compareAndSet(false, true))
        {
            try
            {
                this.executor.submit(this::runRequestedPersistence);
            }
            catch(final RejectedExecutionException e)
            {
                // Shutting down concurrently: the shutdown persist takes over.
                this.flushRequested.set(false);
            }
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
     * Counts changes towards persistence only: changes the on-disk index has to record that leave the
     * graph alone, so there is nothing to optimize for them, such as entities added without a vector,
     * which change the witnesses the manifest is checked against.
     *
     * @param count the number of changes
     */
    void markPersistenceDirty(final int count)
    {
        this.persistenceChangeCount.addAndGet(count);
    }

    /**
     * Returns the changes counted towards persistence so far.
     *
     * @return the count
     */
    int getPersistenceChangeCount()
    {
        return this.persistenceChangeCount.get();
    }

    /**
     * Retires changes a persist has committed: the count the index read when it froze what it then
     * committed. Changes counted since stay counted.
     *
     * @param count the changes committed
     */
    void changesPersisted(final int count)
    {
        this.persistenceChangeCount.updateAndGet(current -> Math.max(0, current - count));
    }

    /**
     * Forces the next persistence check to persist, regardless of how many changes have
     * accumulated.
     * <p>
     * Used when the on-disk graph was rejected at load: the rebuilt in-memory graph has to
     * replace the stale files, but a rejection is not a change and so would never trip the
     * {@code minChangesBetweenPersists} threshold on its own. Without this a read-mostly index
     * would rebuild from the store on every restart.
     * <p>
     * At least one change is counted even with a threshold of zero: the shutdown persist runs only
     * for a count above zero.
     */
    void markPersistRequired()
    {
        final int required = Math.max(1, this.persistenceMinChanges);
        this.persistenceChangeCount.updateAndGet(count -> Math.max(count, required));
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
                // budget. Aborting it now is safe: an on-disk index commits its segments atomically and
                // rebuilds its graph from the vector file on the next load, so no torn file or data loss
                // results.
                // Interrupt promptly rather than waiting a second full grace window, so shutdown is not
                // pinned by work that is being discarded.
                timedOut = true;
                LOG.warn("Shutdown work timed out for '{}' after {} ms; interrupting it so shutdown can "
                    + "proceed (a pending on-disk persist, if any, is redone as a graph rebuild on next load)",
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
        // one grace window for both the executor's termination and its worker thread's end
        final long graceDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EXECUTOR_TERMINATION_GRACE_SECONDS);
        try
        {
            if(!this.executor.awaitTermination(EXECUTOR_TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS))
            {
                LOG.warn("Background task executor did not terminate gracefully for '{}'", this.name);
                this.executor.shutdownNow();
            }
            else
            {
                this.joinExecutorThread(graceDeadline);
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
     * Logs a task failure that a close of the index caused at debug level instead of as an error.
     * <p>
     * A close publishes its intent before it shuts this manager down, so that a load-path graph
     * rebuild in flight gives up instead of holding the shutdown hostage (see
     * {@code VectorIndex.Default#close()}). A task that needed that graph then fails with a
     * {@link RebuildAbandonedException}; that is the close doing its job, not something to alert on.
     * Only that type qualifies, found anywhere in the cause chain: any other failure, including any
     * other {@link IllegalStateException}, is a real one and stays an error during shutdown too.
     *
     * @param task what failed, for the log line
     * @param e    the failure
     * @return whether the failure was attributed to the close and logged
     */
    private boolean abandonedByClose(
        final String    task,
        final Exception e
    )
    {
        if(!RebuildAbandonedException.isCause(e))
        {
            return false;
        }
        LOG.debug("{} for '{}' abandoned because the index is closing: {}", task, this.name, e.getMessage());
        return true;
    }

    /**
     * Resolves the weakly-held index. If it has been garbage-collected - meaning the index was
     * abandoned without {@code close()} - this self-terminates the manager so the daemon thread
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
     * Whether the current thread applies an indexing operation for a manager that
     * {@link #shutdownWithoutWaiting()} has discarded since. The operation was polled before the index
     * was cleared, and it must not reach the graph the index has now: the entity it names may be gone,
     * and its id may be taken by a new one. The index asks once it holds the lock that clearing it holds
     * exclusively, which orders the answer after the clearing; on any other thread the answer is
     * {@code false}.
     *
     * @return whether the operation being applied on this thread is stale
     */
    static boolean isApplyingDiscardedOperation()
    {
        final BackgroundTaskManager manager = APPLYING.get();
        return manager != null && manager.discarded;
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
        this.discarded = true;
        this.shutdown  = true;
        this.cancelScheduledTasks();
        this.discardQueue();
        this.executor.shutdown(); // no awaitTermination, see above
        LOG.info("Background task manager shut down without waiting for '{}'", this.name);
    }

    /**
     * Waits for the executor's worker thread to end after the executor terminated. The executor sets its
     * terminated state from inside that thread, before the thread returns, so without the join the thread
     * can still be alive when {@link #shutdown} returns.
     *
     * @param deadline the {@link System#nanoTime()} until which to wait, the end of the shutdown's grace window
     * @throws InterruptedException if interrupted while waiting
     */
    private void joinExecutorThread(final long deadline) throws InterruptedException
    {
        final Thread thread = this.executorThread;
        if(thread == null || thread == Thread.currentThread())
        {
            return;
        }
        final long remainingNanos = deadline - System.nanoTime();
        if(remainingNanos > 0)
        {
            // join(0) would wait forever, so wait at least one millisecond of what is left
            thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
        }
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
            this.processPendingIndexingOps(MAX_OPERATIONS_PER_TURN);
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
        this.processPendingIndexingOps(Integer.MAX_VALUE);
    }

    /**
     * Polls and executes queued indexing operations until the queue is empty or {@code maxOperations}
     * were taken.
     *
     * @param maxOperations the most operations to take
     */
    private void processPendingIndexingOps(final int maxOperations)
    {
        final Callback cb = this.liveCallback();
        if(cb == null)
        {
            return; // index abandoned; liveCallback() has already self-terminated the manager
        }

        int taken = 0;
        while(taken < maxOperations)
        {
            // Polled and applied under the indexing lock, see the class comment. Held across
            // operations rather than taken for each - the lock is fair, so every handoff costs - and let
            // go as soon as a freeze waits for it, which then gets it next.
            this.indexingLock.lock();
            APPLYING.set(this);
            try
            {
                do
                {
                    final IndexingOperation op = this.indexingQueue.poll();
                    if(op == null)
                    {
                        return;
                    }
                    taken++;
                    this.apply(cb, op);
                }
                while(taken < maxOperations && !this.indexingLock.hasQueuedThreads());
            }
            finally
            {
                APPLYING.remove();
                this.indexingLock.unlock();
            }
        }
    }

    /**
     * Applies one indexing operation, under the indexing lock, and reports the outcome to the index.
     */
    private void apply(
        final Callback          cb,
        final IndexingOperation op
    )
    {
        try
        {
            final Runnable hook = this.applyTestHook;
            if(hook != null)
            {
                hook.run();
            }
            op.execute(cb);
            if(!this.discarded)
            {
                cb.graphOperationApplied();
            }
        }
        catch(final RuntimeException e)
        {
            if(!this.abandonedByClose("Indexing operation", e))
            {
                LOG.error("Error applying indexing operation for '{}': {}", this.name, e.getMessage(), e);
                cb.graphOperationFailed(e);
                // The failed operation counted no change, so without this neither a scheduled
                // nor the shutdown persist would run for it: the graph would stay without the
                // change until some later mutation.
                this.markPersistRequired();
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

        try
        {
            // One turn of the queued indexing operations first, not the whole queue: under a sustained
            // ingest the queue never runs empty, so a drain here would keep the persists waiting
            // behind this task for as long as the ingest lasts. The rest stays queued for the worker,
            // whose own turns go on after this one.
            this.processPendingIndexingOps(MAX_OPERATIONS_PER_TURN);

            cb.doOptimize();

            this.optimizationChangeCount.set(0);
            this.optimizationCount.incrementAndGet();

            LOG.debug("Background optimization completed for '{}'", this.name);
        }
        catch(final Exception e)
        {
            if(!this.abandonedByClose("Background optimization", e))
            {
                LOG.error("Background optimization failed for '{}': {}", this.name, e.getMessage(), e);
            }
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

        this.persist(cb);
    }

    /**
     * Runs a persist requested by {@link #requestFlush()}, regardless of the change threshold.
     */
    private void runRequestedPersistence()
    {
        // Cleared first, so a request arriving while this runs schedules another run.
        this.flushRequested.set(false);
        if(this.shutdown)
        {
            return;
        }
        final Callback cb = this.liveCallback();
        if(cb == null)
        {
            return; // index abandoned; liveCallback() has already self-terminated the manager
        }
        this.persist(cb);
    }

    private void persist(final Callback cb)
    {
        LOG.debug("Background persisting index '{}' with {} changes",
            this.name, this.persistenceChangeCount.get());

        // The count is left alone here: the index retires the changes a persist covered once they are
        // committed (see changesPersisted), which for a write handed to its flush thread is only when
        // that write completes, so a persist that declines or is still writing leaves them counted.
        try
        {
            // No drain of the queued indexing operations first: the freeze moves them into the graph
            // it writes, and under a sustained ingest a drain would not end.
            cb.doPersistToDisk(false);

            LOG.debug("Background persistence completed for '{}'", this.name);
        }
        catch(final Exception e)
        {
            if(!this.abandonedByClose("Background persistence", e))
            {
                LOG.error("Background persistence failed for '{}': {}", this.name, e.getMessage(), e);
            }
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
                if(!this.abandonedByClose("Shutdown optimization", e))
                {
                    LOG.error("Shutdown optimization failed for '{}': {}", this.name, e.getMessage(), e);
                }
            }
        }

        if(persistPending && this.persistenceChangeCount.get() > 0)
        {
            LOG.info("Persisting pending changes for '{}' before shutdown ({} changes)",
                this.name, this.persistenceChangeCount.get());
            try
            {
                if(cb.doPersistToDisk(true))
                {
                    this.persistenceChangeCount.set(0);
                }
            }
            catch(final Exception e)
            {
                if(!this.abandonedByClose("Shutdown persistence", e))
                {
                    LOG.error("Shutdown persistence failed for '{}': {}", this.name, e.getMessage(), e);
                }
            }
        }
    }

}
