package test.eclipse.store.various.storer;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stress test for BatchStorer internal synchronization.
 * <p>
 * Multiple writer threads each own a private partition of the shared list and
 * store DIFFERENT objects into a single BatchStorer instance concurrently.
 * User data access is properly synchronized — the pressure is purely on the
 * BatchStorer internals (pending-map, flush pipeline, storage channel locking).
 * <p>
 * Concurrent flush threads call storer.flush() in a tight loop to maximise
 * overlap between serialization and store() enqueuing.
 * <p>
 * After the run the storage is reloaded and the exact item count is verified.
 */
@Disabled("only manual launch, takes to long")
public class BatchStorerHighThroughputTest
{
    private static final Duration TEST_DURATION = Duration.ofMinutes(5);
    private static final int WRITER_THREADS = 8;
    private static final int FLUSH_THREADS = 4;
    private static final int ITEMS_PER_BATCH = 50;

    @TempDir
    Path tempDir;

    static class DataItem
    {
        final int id;
        String value;
        int revision;
        final byte[] payload;

        DataItem(final int id, final String value)
        {
            this.id = id;
            this.value = value;
            this.revision = 0;
            this.payload = new byte[(id % 64) + 16];
        }

        void update(final String v)
        {
            this.value = v;
            this.revision++;
        }
    }

    static class Root
    {
        final List<DataItem> items = new ArrayList<>();
    }

    @Test
    void highThroughputTest() throws InterruptedException
    {
        final Root root = new Root();
        for (int i = 0; i < 200; i++) {
            root.items.add(new DataItem(i, "seed-" + i));
        }

        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> firstError = new AtomicReference<>();
        final AtomicLong storeCalls = new AtomicLong(0);
        final AtomicLong flushCalls = new AtomicLong(0);
        final AtomicLong addedTotal = new AtomicLong(0);

        System.out.println("Starting high-throughput BatchStorer test (" + TEST_DURATION.toMinutes() + " min)...");

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(root, tempDir)) {
            storage.storeRoot();
            storage.store(root.items);

            try (BatchStorer storer = storage.createBatchStorer(
                    BatchStorer.Controller(500, Duration.ofMillis(400)),
                    Duration.ofMillis(50)
            )) {
                final ExecutorService executor =
                        Executors.newFixedThreadPool(WRITER_THREADS + FLUSH_THREADS);

                // Writer threads: each adds items in batches and updates existing items.
                for (int t = 0; t < WRITER_THREADS; t++) {
                    final int idx = t;
                    executor.submit(() ->
                    {
                        try {
                            startLatch.await();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        int iteration = 0;
                        while (!stop.get()) {
                            try {
                                // Add a batch of new items.
                                synchronized (root.items) {
                                    for (int b = 0; b < ITEMS_PER_BATCH; b++) {
                                        final DataItem item = new DataItem(
                                                root.items.size(),
                                                "w" + idx + "-i" + iteration + "-b" + b
                                        );
                                        root.items.add(item);
                                        storer.store(item);
                                    }
                                    storer.store(root.items);
                                    storeCalls.addAndGet(ITEMS_PER_BATCH + 1);
                                    addedTotal.addAndGet(ITEMS_PER_BATCH);
                                }

                                // Update existing items from own "zone".
                                synchronized (root.items) {
                                    for (int u = 0; u < 10; u++) {
                                        final int target = (idx * 31 + iteration * 7 + u * 13) % root.items.size();
                                        final DataItem item = root.items.get(target);
                                        item.update("w" + idx + "-upd-" + iteration);
                                        storer.store(item);
                                        storeCalls.incrementAndGet();
                                    }
                                }

                                iteration++;
                                Thread.yield();
                            } catch (final Exception e) {
                                if (firstError.compareAndSet(null, e)) e.printStackTrace();
                            }
                        }
                    });
                }

                // Flush threads: hammer flush() to create maximum contention.
                for (int t = 0; t < FLUSH_THREADS; t++) {
                    executor.submit(() ->
                    {
                        try {
                            startLatch.await();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        while (!stop.get()) {
                            try {
                                storer.flush();
                                flushCalls.incrementAndGet();
                            } catch (final Exception e) {
                                if (firstError.compareAndSet(null, e)) e.printStackTrace();
                            }
                        }
                    });
                }

                startLatch.countDown();
                Thread.sleep(TEST_DURATION.toMillis());
                stop.set(true);
                executor.shutdown();
                executor.awaitTermination(30, TimeUnit.SECONDS);

                System.out.println("store() calls: " + storeCalls.get()
                        + " | flush() calls: " + flushCalls.get()
                        + " | items added: " + addedTotal.get());
                System.out.println("In-memory size: " + root.items.size());
                System.out.println("hasPendingData before close: " + storer.hasPendingData());
            }
            // BatchStorer closed — final flush happened.
            System.out.println("First error during run: " + firstError.get());
        }

        final int expectedSize;
        synchronized (root.items) {
            expectedSize = root.items.size();
        }

        System.out.println("Reloading storage from disk...");
        try (EmbeddedStorageManager reloadStorage = EmbeddedStorage.start(tempDir)) {
            final Root reloaded = (Root) reloadStorage.root();
            assertNotNull(reloaded, "Root must not be null");
            assertFalse(reloaded.items.isEmpty(), "Reloaded items list must not be empty");

            int nulls = 0;
            for (int i = 0; i < reloaded.items.size(); i++) {
                if (reloaded.items.get(i) == null) nulls++;
            }

            System.out.println("Reloaded items: " + reloaded.items.size()
                    + " | expected: " + expectedSize
                    + " | null items: " + nulls);

            assertTrue(nulls == 0, "Found " + nulls + " null items after reload");
            assertEquals(expectedSize, reloaded.items.size(),
                    "Reloaded size does not match in-memory size");
        }
    }
}

