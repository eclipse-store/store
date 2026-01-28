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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages background optimization for a VectorIndex.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Dirty tracking with change counting</li>
 *   <li>Scheduled background optimization at configurable intervals</li>
 *   <li>Debouncing based on minimum change threshold</li>
 *   <li>Graceful shutdown with optional final optimization</li>
 * </ul>
 */
interface BackgroundOptimizationManager
{
    /**
     * Marks the index as dirty with the specified number of changes.
     *
     * @param count the number of changes
     */
    public void markDirty(int count);

    /**
     * Starts the scheduled background optimization task.
     */
    public void startScheduledOptimization();

    /**
     * Shuts down the optimization manager.
     *
     * @param optimizePending if true and there are pending changes, optimize before shutdown
     */
    public void shutdown(boolean optimizePending);


    /**
     * Callback interface for the optimization manager to perform optimization.
     */
    public interface Callback
    {
        /**
         * Performs optimization of the index.
         */
        public void optimize();
    }


    /**
     * Default implementation of BackgroundOptimizationManager.
     */
    public static class Default implements BackgroundOptimizationManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(BackgroundOptimizationManager.class);

        private final Callback                 callback  ;
        private final String                   name      ;
        private final long                     intervalMs;
        private final int                      minChanges;
        private final ScheduledExecutorService scheduler ;

        private final AtomicInteger changeCount = new AtomicInteger(0);

        private ScheduledFuture<?> scheduledTask;
        private volatile boolean   shutdown     = false;

        Default(
            final Callback callback  ,
            final String   name      ,
            final long     intervalMs,
            final int      minChanges
        )
        {
            this.callback   = callback;
            this.name       = name;
            this.intervalMs = intervalMs;
            this.minChanges = minChanges;

            this.scheduler  = Executors.newSingleThreadScheduledExecutor(r ->
            {
                final Thread t = new Thread(r, "VectorIndex-BackgroundOptimization-" + name);
                t.setDaemon(true);
                return t;
            });
        }

        @Override
        public void markDirty(final int count)
        {
            this.changeCount.addAndGet(count);
        }

        @Override
        public void startScheduledOptimization()
        {
            this.scheduledTask = this.scheduler.scheduleAtFixedRate(
                this::optimizeNowIfDirty,
                this.intervalMs,
                this.intervalMs,
                TimeUnit.MILLISECONDS
            );
        }

        /**
         * Optimizes the index if the change threshold has been met.
         */
        private void optimizeNowIfDirty()
        {
            if(this.shutdown)
            {
                return;
            }

            final int currentChanges = this.changeCount.get();
            if(currentChanges < this.minChanges)
            {
                return;
            }

            this.optimizeNow();
        }

        /**
         * Optimizes the index immediately.
         */
        private void optimizeNow()
        {
            LOG.debug("Background optimizing index '{}' with {} changes",
                this.name, this.changeCount.get());

            try
            {
                this.callback.optimize();

                // Reset change count after successful optimization
                this.changeCount.set(0);

                LOG.debug("Background optimization completed for '{}'", this.name);
            }
            catch(final Exception e)
            {
                LOG.error("Background optimization failed for '{}': {}", this.name, e.getMessage(), e);
            }
        }

        @Override
        public void shutdown(final boolean optimizePending)
        {
            this.shutdown = true;

            // Cancel the scheduled task
            if(this.scheduledTask != null)
            {
                this.scheduledTask.cancel(false);
                this.scheduledTask = null;
            }

            // Optimize pending changes if requested
            final int pendingChanges = this.changeCount.get();
            if(optimizePending && pendingChanges > 0)
            {
                LOG.info("Optimizing pending changes for '{}' before shutdown ({} changes)",
                    this.name, pendingChanges);
                this.optimizeNow();
            }

            // Shutdown the scheduler
            this.scheduler.shutdown();
            try
            {
                if(!this.scheduler.awaitTermination(30, TimeUnit.SECONDS))
                {
                    LOG.warn("Background optimization scheduler did not terminate gracefully for '{}'",
                        this.name);
                    this.scheduler.shutdownNow();
                }
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                this.scheduler.shutdownNow();
            }

            LOG.info("Background optimization manager shutdown for '{}'", this.name);
        }

    }

}
