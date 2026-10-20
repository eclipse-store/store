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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for what the eventual-indexing worker of {@link BackgroundTaskManager} reports to its index
 * around each operation it applies.
 */
class BackgroundTaskManagerIndexingTest
{
    /**
     * Records the callbacks of the worker and the persists; adding to the graph fails when asked to.
     */
    private static final class RecordingCallback implements BackgroundTaskManager.Callback
    {
        final AtomicInteger  applied          = new AtomicInteger();
        final AtomicInteger  failed           = new AtomicInteger();
        final AtomicInteger  persists         = new AtomicInteger();
        final AtomicInteger  addsAtPersist    = new AtomicInteger(-1);
        final AtomicInteger  adds             = new AtomicInteger();
        final CountDownLatch persisted        = new CountDownLatch(1);
        volatile boolean     failAdds         ;

        @Override
        public void applyGraphAdd(final int ordinal)
        {
            if(this.failAdds)
            {
                throw new IllegalStateException("simulated graph operation failure");
            }
            this.adds.incrementAndGet();
        }

        @Override
        public void applyGraphBatchAdd(final int[] ordinals)
        {
            // no-op
        }

        @Override
        public void applyGraphUpdate(final int ordinal)
        {
            // no-op
        }

        @Override
        public void applyGraphRemove(final int ordinal)
        {
            // no-op
        }

        @Override
        public void markDirtyForBackgroundManagers(final int count)
        {
            // no-op
        }

        @Override
        public void doOptimize()
        {
            // no-op
        }

        @Override
        public boolean doPersistToDisk(final boolean onShutdown)
        {
            this.persists.incrementAndGet();
            this.addsAtPersist.compareAndSet(-1, this.adds.get());
            this.persisted.countDown();
            return true;
        }

        @Override
        public void graphOperationFailed(final RuntimeException failure)
        {
            this.failed.incrementAndGet();
        }

        @Override
        public void graphOperationApplied()
        {
            this.applied.incrementAndGet();
        }
    }

    private static BackgroundTaskManager eventualManager(final RecordingCallback callback)
    {
        return new BackgroundTaskManager(
            callback,
            "test-index",
            true,     // eventualIndexing
            false,    // backgroundOptimization
            0L,       // optimizationIntervalMs
            0,        // optimizationMinChanges
            true,     // backgroundPersistence
            600_000L, // persistenceIntervalMs: the scheduled persist never runs within the test
            3,        // persistenceMinChanges
            5_000L    // shutdownPersistTimeoutMillis
        );
    }

    /**
     * An eventual-indexing manager whose background optimization is due on every tick.
     */
    private static BackgroundTaskManager eventualManagerWithOptimization(
        final RecordingCallback callback,
        final long              optimizationIntervalMs
    )
    {
        return new BackgroundTaskManager(
            callback,
            "test-index",
            true,                   // eventualIndexing
            true,                   // backgroundOptimization
            optimizationIntervalMs, // optimizationIntervalMs
            0,                      // optimizationMinChanges: due on every tick
            true,                   // backgroundPersistence
            600_000L,               // persistenceIntervalMs: the scheduled persist never runs within the test
            3,                      // persistenceMinChanges
            5_000L                  // shutdownPersistTimeoutMillis
        );
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void appliedOperation_isReported()
    {
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = eventualManager(callback);
        try
        {
            manager.enqueueAdd(1);
            manager.drainQueue();

            assertEquals(1, callback.applied.get());
            assertEquals(0, callback.failed.get());
        }
        finally
        {
            manager.shutdown(false, false, false);
        }
    }

    /**
     * A failed operation counts no change, yet it leaves the graph without one the store has. It must
     * still make a persist due, here the shutdown persist, which runs only for counted changes.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void failedOperation_isReportedAndMakesAPersistDue()
    {
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = eventualManager(callback);
        callback.failAdds = true;

        manager.enqueueAdd(1);
        manager.drainQueue();
        manager.shutdown(false, false, true);

        assertEquals(1, callback.failed.get());
        assertEquals(0, callback.applied.get());
        assertEquals(1, callback.persists.get(), "the shutdown persist must run for the failed operation");
    }

    /**
     * A failed operation makes the shutdown persist due also with a threshold of zero changes, which a
     * persist that is merely required must not leave at a count of zero: the shutdown persist runs only
     * for a count above zero.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void failedOperation_withAThresholdOfZero_makesTheShutdownPersistDue()
    {
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = new BackgroundTaskManager(
            callback,
            "test-index",
            true,     // eventualIndexing
            false,    // backgroundOptimization
            0L,       // optimizationIntervalMs
            0,        // optimizationMinChanges
            true,     // backgroundPersistence
            600_000L, // persistenceIntervalMs: the scheduled persist never runs within the test
            0,        // persistenceMinChanges
            5_000L    // shutdownPersistTimeoutMillis
        );
        callback.failAdds = true;

        manager.enqueueAdd(1);
        manager.drainQueue();
        manager.shutdown(false, false, true);

        assertEquals(1, callback.failed.get());
        assertEquals(1, callback.persists.get(), "the shutdown persist must run for the failed operation");
    }

    /**
     * A persist requested while the queue is long - as it stays under a sustained eventual-indexing
     * ingest - runs after one bounded turn of the worker, not after the whole queue: the worker yields
     * the executor between turns, and the persist does not drain the queue first.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void requestedPersist_duringALongQueue_runsBeforeTheQueueIsDrained() throws InterruptedException
    {
        final int                   queued   = 2_000;
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = eventualManager(callback);
        final CountDownLatch        holding  = new CountDownLatch(1);
        final CountDownLatch        release  = new CountDownLatch(1);
        manager.applyTestHook = () ->
        {
            manager.applyTestHook = null;
            holding.countDown();
            try
            {
                release.await(10, TimeUnit.SECONDS);
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        };
        try
        {
            manager.enqueueAdd(0);
            assertTrue(holding.await(10, TimeUnit.SECONDS));
            for(int i = 1; i < queued; i++)
            {
                manager.enqueueAdd(i);
            }
            manager.requestFlush();
            release.countDown();

            assertTrue(callback.persisted.await(10, TimeUnit.SECONDS), "the requested persist must run");
            assertTrue(callback.addsAtPersist.get() < queued,
                "the persist ran only after " + callback.addsAtPersist.get() + " of " + queued + " operations");
        }
        finally
        {
            manager.shutdown(false, false, false);
        }
    }

    /**
     * A background optimization due while the queue is long applies one bounded turn of it, not the
     * whole queue, so a persist requested meanwhile still runs before the queue is drained. A drain in
     * the optimization would keep the persist waiting for as long as an ingest keeps the queue full.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void requestedPersist_behindADueOptimization_runsBeforeTheQueueIsDrained() throws InterruptedException
    {
        final int                   queued   = 5_000;
        final long                  interval = 500L;
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = eventualManagerWithOptimization(callback, interval);
        final CountDownLatch        holding  = new CountDownLatch(1);
        final CountDownLatch        release  = new CountDownLatch(1);
        manager.applyTestHook = () ->
        {
            manager.applyTestHook = null;
            holding.countDown();
            try
            {
                release.await(10, TimeUnit.SECONDS);
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        };
        try
        {
            manager.enqueueAdd(0);
            assertTrue(holding.await(10, TimeUnit.SECONDS));
            // The first optimization tick falls due while the worker is held, so it runs before the
            // persist requested below; the next tick is not due before that persist.
            Thread.sleep(interval + interval / 2);
            for(int i = 1; i < queued; i++)
            {
                manager.enqueueAdd(i);
            }
            manager.requestFlush();
            release.countDown();

            assertTrue(callback.persisted.await(10, TimeUnit.SECONDS), "the requested persist must run");
            assertTrue(callback.addsAtPersist.get() < queued,
                "the persist ran only after " + callback.addsAtPersist.get() + " of " + queued + " operations");
        }
        finally
        {
            manager.shutdown(false, false, false);
        }
    }

    /**
     * A drain waits for the operations queued when it is called, not for those enqueued while it runs,
     * so under a sustained ingest it still ends.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void drainQueue_whileOperationsKeepComing_ends()
    {
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = eventualManager(callback);
        final AtomicInteger         next     = new AtomicInteger(1_000);
        // Each applied operation enqueues another one, so the queue never runs empty.
        manager.applyTestHook = () ->
            manager.enqueueAdd((int)next.getAndIncrement());
        try
        {
            for(int i = 0; i < 100; i++)
            {
                manager.enqueueAdd(i);
            }
            manager.drainQueue();

            assertTrue(callback.adds.get() >= 100, "the operations queued at the call must be applied");
        }
        finally
        {
            manager.applyTestHook = null;
            manager.shutdown(false, false, false);
        }
    }
}
