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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for internal#112: {@link BackgroundTaskManager#shutdown} must be time-boxed by the
 * configured shutdown-persist timeout. A persist that overruns the budget is aborted promptly rather
 * than pinning shutdown for the full grace window.
 */
class BackgroundTaskManagerShutdownTest
{
    /**
     * Minimal {@link BackgroundTaskManager.Callback} whose {@code doPersistToDisk} blocks for a long
     * time but returns cooperatively when interrupted (mirrors how the real persist unwinds when the
     * executor is interrupted mid-write).
     */
    private static final class SlowPersistCallback implements BackgroundTaskManager.Callback
    {
        final CountDownLatch persistStarted     = new CountDownLatch(1);
        final AtomicBoolean  persistInterrupted = new AtomicBoolean(false);

        @Override
        public void applyGraphAdd(final VectorEntry entry) { /* no-op */ }

        @Override
        public void applyGraphBatchAdd(final List<VectorEntry> entries) { /* no-op */ }

        @Override
        public void applyGraphUpdate(final VectorEntry entry) { /* no-op */ }

        @Override
        public void applyGraphRemove(final int ordinal) { /* no-op */ }

        @Override
        public void markDirtyForBackgroundManagers(final int count) { /* no-op */ }

        @Override
        public void doOptimize() { /* no-op */ }

        @Override
        public void doPersistToDisk(final boolean onShutdown)
        {
            this.persistStarted.countDown();
            try
            {
                // Far longer than the configured timeout; the shutdown must interrupt this.
                Thread.sleep(60_000);
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                this.persistInterrupted.set(true);
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shutdownIsBoundedWhenPersistOverrunsTimeout() throws InterruptedException
    {
        final long shutdownPersistTimeoutMillis = 300L;

        final SlowPersistCallback callback = new SlowPersistCallback();
        final BackgroundTaskManager manager = new BackgroundTaskManager(
            callback,
            "test-index",
            false,   // eventualIndexing
            false,   // backgroundOptimization
            0L,      // optimizationIntervalMs
            0,       // optimizationMinChanges
            true,    // backgroundPersistence
            600_000L,// persistenceIntervalMs — long, so the scheduled task never fires during the test
            1,       // persistenceMinChanges
            shutdownPersistTimeoutMillis
        );

        // Register a pending change so the shutdown persist actually runs.
        manager.markDirty(5);

        final long startNanos = System.nanoTime();
        manager.shutdown(false, false, true); // persistPending = true
        final long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        // The persist must have been invoked and then aborted by the shutdown, not run to completion.
        assertTrue(callback.persistStarted.await(1, TimeUnit.SECONDS),
            "Shutdown persist should have been invoked");
        assertTrue(callback.persistInterrupted.get(),
            "The overrunning persist should have been interrupted by shutdown");

        // Shutdown must return well within the timeout + grace window, nowhere near the 60s sleep.
        assertTrue(elapsedMillis < 15_000,
            "shutdown() must be bounded, took " + elapsedMillis + " ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shutdownReturnsQuicklyWhenNoPendingWork() throws InterruptedException
    {
        final SlowPersistCallback callback = new SlowPersistCallback();
        final BackgroundTaskManager manager = new BackgroundTaskManager(
            callback,
            "test-index-clean",
            false, false, 0L, 0, true, 600_000L, 1, 300L
        );

        // No markDirty(): persistenceChangeCount stays 0, so finalShutdownWork skips the persist.
        final long startNanos = System.nanoTime();
        manager.shutdown(false, false, true);
        final long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertFalse(callback.persistInterrupted.get(), "No persist should have run without pending changes");
        assertTrue(elapsedMillis < 5_000, "Clean shutdown should be near-instant, took " + elapsedMillis + " ms");
    }
}
