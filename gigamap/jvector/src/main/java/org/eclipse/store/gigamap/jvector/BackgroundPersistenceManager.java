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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages background persistence for a VectorIndex.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Dirty tracking with change counting</li>
 *   <li>Scheduled background persistence at configurable intervals</li>
 *   <li>Debouncing based on minimum change threshold</li>
 *   <li>Graceful shutdown with optional final persistence</li>
 * </ul>
 */
interface BackgroundPersistenceManager
{
    /**
     * Marks the index as dirty with the specified number of changes.
     *
     * @param count the number of changes
     */
    public void markDirty(int count);

    /**
     * Starts the scheduled background persistence task.
     */
    public void startScheduledPersistence();

    /**
     * Shuts down the persistence manager.
     *
     * @param persistPending if true and there are pending changes, persist before shutdown
     */
    public void shutdown(boolean persistPending);


    /**
     * Callback interface for the persistence manager to perform persistence.
     */
    public interface Callback
    {
        /**
         * Persists the index to disk.
         */
        public void persistToDisk();
    }


    /**
     * Default implementation of BackgroundPersistenceManager.
     */
    public static class Default implements BackgroundPersistenceManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(BackgroundPersistenceManager.class);

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
            this.callback   = callback  ;
            this.name       = name      ;
            this.intervalMs = intervalMs;
            this.minChanges = minChanges;

            this.scheduler = Executors.newSingleThreadScheduledExecutor(r ->
            {
                final Thread t = new Thread(r, "VectorIndex-BackgroundPersistence-" + name);
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
        public void startScheduledPersistence()
        {
            this.scheduledTask = this.scheduler.scheduleAtFixedRate(
                this::persistNowIfDirty,
                this.intervalMs,
                this.intervalMs,
                TimeUnit.MILLISECONDS
            );
        }

        /**
         * Persists the index if the change threshold has been met.
         */
        private void persistNowIfDirty()
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

            this.persistNow();
        }

        /**
         * Persists the index immediately, regardless of dirty state or threshold.
         */
        private void persistNow()
        {
            LOG.debug("Background persisting index '{}' with {} changes",
                this.name, this.changeCount.get());

            try
            {
                this.callback.persistToDisk();

                // Reset change count after successful persistence
                this.changeCount.set(0);

                LOG.debug("Background persistence completed for '{}'", this.name);
            }
            catch(final Exception e)
            {
                LOG.error("Background persistence failed for '{}': {}", this.name, e.getMessage(), e);
            }
        }

        @Override
        public void shutdown(final boolean persistPending)
        {
            this.shutdown = true;

            // Cancel the scheduled task
            if(this.scheduledTask != null)
            {
                this.scheduledTask.cancel(false);
                this.scheduledTask = null;
            }

            // Persist pending changes if requested
            final int pendingChanges = this.changeCount.get();
            if(persistPending && pendingChanges > 0)
            {
                LOG.info("Persisting pending changes for '{}' before shutdown ({} changes)",
                    this.name, pendingChanges);
                this.persistNow();
            }

            // Shutdown the scheduler
            this.scheduler.shutdown();
            try
            {
                if(!this.scheduler.awaitTermination(30, TimeUnit.SECONDS))
                {
                    LOG.warn("Background persistence scheduler did not terminate gracefully for '{}'",
                        this.name);
                    this.scheduler.shutdownNow();
                }
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                this.scheduler.shutdownNow();
            }

            LOG.info("Background persistence manager shutdown for '{}'", this.name);
        }

    }

}
