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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Manages background graph indexing for a VectorIndex.
 * <p>
 * When eventual indexing is enabled, graph mutations (add/update/remove) are
 * queued and applied asynchronously by a single background worker thread.
 * The vector store is still updated synchronously so that data is not lost,
 * but the expensive HNSW graph operations are deferred.
 * <p>
 * This trades immediate search consistency for reduced latency on mutation
 * operations — search results may not immediately reflect the most recent
 * mutations (eventual consistency).
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Queuing of graph indexing operations (add, update, remove)</li>
 *   <li>Sequential application of operations by a single background thread</li>
 *   <li>Draining the queue (blocking until all pending operations are applied)</li>
 *   <li>Graceful shutdown with optional drain</li>
 * </ul>
 */
interface BackgroundIndexingManager
{
    /**
     * Enqueues an indexing operation for background processing.
     *
     * @param operation the operation to enqueue
     */
    void enqueue(IndexingOperation operation);

    /**
     * Blocks until all currently enqueued operations have been applied.
     * <p>
     * This is used before {@code optimize()} and {@code persistToDisk()} to
     * ensure the graph is complete before those operations proceed.
     */
    void drainQueue();

    /**
     * Discards all pending operations without applying them.
     * <p>
     * Used during {@code internalRemoveAll()} where pending operations
     * refer to stale ordinals that are no longer valid.
     */
    void discardQueue();

    /**
     * Shuts down the background indexing manager.
     *
     * @param drainPending if true, drain all pending operations before shutdown
     */
    void shutdown(boolean drainPending);

    /**
     * Returns the number of pending operations in the queue.
     * Useful for monitoring and testing.
     *
     * @return the number of pending operations
     */
    int getPendingCount();


    // ========================================================================
    // Indexing Operations
    // ========================================================================

    /**
     * Sealed interface for indexing operations that can be queued.
     */
    sealed interface IndexingOperation
        permits IndexingOperation.Add,
                IndexingOperation.Update,
                IndexingOperation.Remove,
                IndexingOperation.DrainBarrier
    {
        /**
         * Add a node to the HNSW graph.
         */
        record Add(int ordinal, float[] vector) implements IndexingOperation {}

        /**
         * Update a node in the HNSW graph (delete + re-add).
         */
        record Update(int ordinal, float[] vector) implements IndexingOperation {}

        /**
         * Remove a node from the HNSW graph.
         */
        record Remove(int ordinal) implements IndexingOperation {}

        /**
         * Sentinel operation for drainQueue() — signals the worker to release the latch.
         */
        record DrainBarrier(CountDownLatch latch) implements IndexingOperation {}
    }


    // ========================================================================
    // Callback
    // ========================================================================

    /**
     * Callback interface for applying graph operations.
     * Implemented by {@code VectorIndex.Default}.
     */
    interface Callback
    {
        /**
         * Adds a node to the HNSW graph.
         *
         * @param ordinal the node ordinal
         * @param vector  the vector data
         */
        void applyGraphAdd(int ordinal, float[] vector);

        /**
         * Updates a node in the HNSW graph (delete old + add new).
         *
         * @param ordinal the node ordinal
         * @param vector  the new vector data
         */
        void applyGraphUpdate(int ordinal, float[] vector);

        /**
         * Removes a node from the HNSW graph.
         *
         * @param ordinal the node ordinal
         */
        void applyGraphRemove(int ordinal);

        /**
         * Marks dirty for persistence/optimization background managers.
         *
         * @param count the number of changes
         */
        void markDirtyForBackgroundManagers(int count);
    }


    // ========================================================================
    // Default Implementation
    // ========================================================================

    /**
     * Default implementation of BackgroundIndexingManager.
     * <p>
     * Uses a single daemon worker thread that continuously takes operations
     * from a {@link LinkedBlockingQueue} and applies them via the callback.
     */
    static class Default implements BackgroundIndexingManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(BackgroundIndexingManager.class);

        private final Callback                                callback;
        private final String                                  name    ;
        private final LinkedBlockingQueue<IndexingOperation>  queue   ;
        private final Thread                                  worker  ;

        private volatile boolean shutdown = false;

        Default(final Callback callback, final String name)
        {
            this.callback = callback;
            this.name     = name    ;
            this.queue    = new LinkedBlockingQueue<>();

            this.worker = new Thread(this::workerLoop, "VectorIndex-BackgroundIndexing-" + name);
            this.worker.setDaemon(true);
            this.worker.start();

            LOG.info("Background indexing started for '{}'", name);
        }

        @Override
        public void enqueue(final IndexingOperation operation)
        {
            this.queue.add(operation);
        }

        @Override
        public void drainQueue()
        {
            if(this.shutdown)
            {
                return;
            }

            final CountDownLatch latch = new CountDownLatch(1);
            this.queue.add(new IndexingOperation.DrainBarrier(latch));

            try
            {
                latch.await();
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while draining indexing queue for '{}'", this.name);
            }
        }

        @Override
        public void discardQueue()
        {
            final int discarded = this.queue.size();
            this.queue.clear();
            if(discarded > 0)
            {
                LOG.info("Discarded {} pending indexing operations for '{}'", discarded, this.name);
            }
        }

        @Override
        public void shutdown(final boolean drainPending)
        {
            if(drainPending)
            {
                LOG.info("Draining {} pending indexing operations for '{}' before shutdown",
                    this.queue.size(), this.name);
                this.drainQueue();
            }

            this.shutdown = true;
            this.worker.interrupt();

            try
            {
                this.worker.join(30_000);
                if(this.worker.isAlive())
                {
                    LOG.warn("Background indexing worker did not terminate gracefully for '{}'", this.name);
                }
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            LOG.info("Background indexing manager shutdown for '{}'", this.name);
        }

        @Override
        public int getPendingCount()
        {
            return this.queue.size();
        }

        /**
         * Worker loop that continuously takes operations from the queue and applies them.
         */
        private void workerLoop()
        {
            while(!this.shutdown)
            {
                try
                {
                    final IndexingOperation op = this.queue.take();
                    this.applyOperation(op);
                }
                catch(final InterruptedException e)
                {
                    if(!this.shutdown)
                    {
                        LOG.debug("Background indexing worker interrupted for '{}'", this.name);
                    }
                    // Re-check shutdown flag in loop condition
                }
                catch(final Exception e)
                {
                    LOG.error("Error applying indexing operation for '{}': {}", this.name, e.getMessage(), e);
                }
            }
        }

        /**
         * Applies a single indexing operation via the callback.
         */
        private void applyOperation(final IndexingOperation op)
        {
            if(op instanceof IndexingOperation.Add add)
            {
                this.callback.applyGraphAdd(add.ordinal(), add.vector());
                this.callback.markDirtyForBackgroundManagers(1);
            }
            else if(op instanceof IndexingOperation.Update update)
            {
                this.callback.applyGraphUpdate(update.ordinal(), update.vector());
                this.callback.markDirtyForBackgroundManagers(1);
            }
            else if(op instanceof IndexingOperation.Remove remove)
            {
                this.callback.applyGraphRemove(remove.ordinal());
                this.callback.markDirtyForBackgroundManagers(1);
            }
            else if(op instanceof IndexingOperation.DrainBarrier barrier)
            {
                barrier.latch().countDown();
            }
        }
    }

}
