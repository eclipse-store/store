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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * Integration stress test that intentionally lacks synchronization to provoke
 * race conditions when storing the same object concurrently.
 * <p>
 * Purpose:
 * - Produce persistent storage containing inconsistent / "zombie" elements
 * useful for debugging and reproducing concurrency bugs in the storage layer.
 * - Serve as a manual, long-running reproduction scenario rather than a unit test.
 * <p>
 * Notes:
 * - The test is disabled by default and intended for manual execution only.
 * - Mutates the same object from multiple threads and calls `store` / `flush`
 * concurrently without coordination. This is deliberate; do not "fix" application
 * logic here — the repository uses this to expose issues in the underlying library.
 */

@Disabled("only manual launch, takes to long, produce zombies")
public class BatchStorerSameObjectRaceTest
{
    private static final Duration TEST_DURATION = Duration.ofMinutes(3);
    private static final int STORE_THREADS = 8;
    private static final int FLUSH_THREADS = 2;

    @TempDir
    Path tempDir;

    static class SharedState
    {
        int version;
        String label;
        List<String> tags;

        SharedState()
        {
            this.version = 0;
            this.label = "v0";
            this.tags = new ArrayList<>();
            this.tags.add("initial");
        }
    }

    static class Root
    {
        final SharedState shared = new SharedState();
    }

    @Test
    void sameObjectRaceTest() throws InterruptedException
    {
        final Root root = new Root();

        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> firstError = new AtomicReference<>();
        final AtomicLong storeCalls = new AtomicLong(0);
        final AtomicLong flushCalls = new AtomicLong(0);

        System.out.println("Starting same-object race test (" + TEST_DURATION.toMinutes() + " min)...");

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(root, tempDir)) {
            storage.storeRoot();

            try (BatchStorer storer = storage.createBatchStorer(
                    BatchStorer.Controller(50, Duration.ofMillis(200)),
                    Duration.ofMillis(30)
            )) {
                final ExecutorService executor =
                        Executors.newFixedThreadPool(STORE_THREADS + FLUSH_THREADS);

                for (int t = 0; t < STORE_THREADS; t++) {
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
                                root.shared.version = iteration;
                                root.shared.label = "t" + idx + "-v" + iteration;
                                root.shared.tags = new ArrayList<>();
                                root.shared.tags.add("t" + idx);
                                root.shared.tags.add("i" + iteration);

                                storer.store(root.shared);
                                storer.store(root);
                                storeCalls.incrementAndGet();
                                iteration++;
                            } catch (final Exception e) {
                                if (firstError.compareAndSet(null, e)) e.printStackTrace();
                            }
                        }
                    });
                }

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
                        + " | flush() calls: " + flushCalls.get());
                System.out.println("hasPendingData after stop: " + storer.hasPendingData());
            }

            System.out.println("First error during run: " + firstError.get());
        }

        System.out.println("Reloading storage from disk...");
        try (EmbeddedStorageManager reloadStorage = EmbeddedStorage.start(tempDir)) {
            final Root reloaded = (Root) reloadStorage.root();
            assertNotNull(reloaded, "Root must not be null");
            assertNotNull(reloaded.shared, "SharedState must not be null");
            assertNotNull(reloaded.shared.label, "label must not be null");
            assertNotNull(reloaded.shared.tags, "tags must not be null");
            assertFalse(reloaded.shared.tags.isEmpty(), "tags must not be empty");

            System.out.println("Reloaded: version=" + reloaded.shared.version
                    + " label=" + reloaded.shared.label
                    + " tags=" + reloaded.shared.tags);
        }
    }
}

