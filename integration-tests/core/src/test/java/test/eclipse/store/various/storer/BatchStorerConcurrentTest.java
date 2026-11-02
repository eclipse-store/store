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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("only manual launch, takes to long")
public class BatchStorerConcurrentTest
{
    private static final Duration TEST_DURATION = Duration.ofMinutes(5);
    private static final int WRITER_THREADS = 8;
    private static final int INITIAL_LIST_SIZE = 1_000;

    @TempDir
    Path tempDir;

    static class DataItem
    {
        final int id;
        String value;
        int revision;

        DataItem(final int id, final String value)
        {
            this.id = id;
            this.value = value;
            this.revision = 0;
        }

        void update(final String newValue)
        {
            this.value = newValue;
            this.revision++;
        }

        @Override
        public String toString()
        {
            return "DataItem{id=" + id + ", revision=" + revision + ", value='" + value + "'}";
        }
    }

    @Test
    void concurrentWritersStressTest() throws InterruptedException
    {
        System.out.println(tempDir.toString());
        final List<DataItem> data = new ArrayList<>();
        for (int i = 0; i < INITIAL_LIST_SIZE; i++) {
            data.add(new DataItem(i, "initial-" + i));
        }

        final AtomicInteger[] addedByThread = new AtomicInteger[WRITER_THREADS];
        for (int i = 0; i < WRITER_THREADS; i++) {
            addedByThread[i] = new AtomicInteger(0);
        }

        final AtomicLong totalAdded = new AtomicLong(0);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch startLatch = new CountDownLatch(1);

        System.out.println("Starting BatchStorer concurrent stress test (" + TEST_DURATION.toMinutes() + " min)...");

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(data, tempDir)) {

            try (BatchStorer storer = storage.createBatchStorer(
                    BatchStorer.Controller(4000),
                    Duration.ofMillis(100)
            )) {
                final ExecutorService executor = Executors.newFixedThreadPool(WRITER_THREADS);

                for (int threadIdx = 0; threadIdx < WRITER_THREADS; threadIdx++) {
                    final int idx = threadIdx;
                    final AtomicInteger myAdded = addedByThread[idx];

                    executor.submit(() ->
                    {
                        try {
                            startLatch.await();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        int localAdds = 0;
                        int iteration = 0;

                        while (!stop.get()) {
                            try {
                                synchronized (data) {
                                    data.add(new DataItem(data.size(), "thread-" + idx + "-iter-" + iteration));
                                    storer.store(data);
                                }
                                localAdds++;
                                myAdded.incrementAndGet();
                                totalAdded.incrementAndGet();

                                synchronized (data) {
                                    if (!data.isEmpty()) {
                                        final int target = (idx * 31 + iteration * 7) % data.size();
                                        final DataItem item = data.get(target);
                                        item.update("updated-by-thread-" + idx + "-at-iter-" + iteration);
                                        storer.store(item);
                                        storer.store(data);
                                    }
                                }

                                iteration++;
                                Thread.yield();
                            } catch (final Exception e) {
                                System.err.println("Writer thread " + idx + " caught exception: " + e);
                                e.printStackTrace();
                            }
                        }

                        System.out.println("Writer thread " + idx + " finished after " + localAdds + " adds.");
                    });
                }

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
                            synchronized (data) {
                                data.add(new DataItem(data.size(), "direct-" + iteration));
                                storage.store(data);

                                if (data.size() > 1) {
                                    final int target = (iteration * 13) % data.size();
                                    final DataItem item = data.get(target);
                                    item.update("direct-store-iter-" + iteration);
                                    storage.store(item);
                                }
                            }
                            iteration++;
                            Thread.yield();
                        } catch (final Exception e) {
                            System.err.println("Direct-store thread caught exception: " + e);
                            e.printStackTrace();
                        }
                    }

                    System.out.println("Direct-store thread finished after " + iteration + " iterations.");
                });

                startLatch.countDown();
                Thread.sleep(TEST_DURATION.toMillis());
                stop.set(true);
                executor.shutdown();
                executor.awaitTermination(30, TimeUnit.SECONDS);

                System.out.println("Stress phase complete. Total items added: " + totalAdded.get());
                System.out.println("Final list size (in-memory): " + data.size());
            }
        }

        System.out.println("Reloading storage from disk...");

        try (EmbeddedStorageManager reloadStorage = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked") final List<DataItem> reloaded = (List<DataItem>) reloadStorage.root();

            assertFalse(reloaded.isEmpty(), "Reloaded list must not be empty");

            int errors = 0;
            for (int i = 0; i < reloaded.size(); i++) {
                if (reloaded.get(i) == null) {
                    System.err.println("Null item at index " + i);
                    errors++;
                }
            }

            assertTrue(errors == 0, "Found " + errors + " null/corrupt items in reloaded list");

            long totalExpectedAdds = INITIAL_LIST_SIZE;
            for (int i = 0; i < WRITER_THREADS; i++) {
                final int added = addedByThread[i].get();
                totalExpectedAdds += added;
                System.out.println("  Thread " + i + " added " + added + " items");
            }
            System.out.println("Expected max list size: " + totalExpectedAdds);
            System.out.println("Actual reloaded size:   " + reloaded.size());
        }
    }
}

