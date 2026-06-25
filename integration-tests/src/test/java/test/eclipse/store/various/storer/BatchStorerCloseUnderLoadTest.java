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
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("only manual launch, takes to long")
public class BatchStorerCloseUnderLoadTest
{
    private static final int      WRITER_THREADS         = 6;
    private static final int      INITIAL_LIST_SIZE      = 500;
    private static final Duration WARMUP                 = Duration.ofSeconds(10);
    private static final Duration WRITER_RUN_AFTER_CLOSE = Duration.ofSeconds(5);

    @TempDir
    Path tempDir;

    static class DataItem
    {
        final int id;
        String    value;
        int       revision;

        DataItem(final int id, final String value)
        {
            this.id       = id;
            this.value    = value;
            this.revision = 0;
        }

        void update(final String v) { this.value = v; this.revision++; }
    }

    @Test
    void closeUnderLoadTest() throws InterruptedException
    {
        final List<DataItem> data = new ArrayList<>();
        for (int i = 0; i < INITIAL_LIST_SIZE; i++)
        {
            data.add(new DataItem(i, "initial-" + i));
        }

        final AtomicBoolean  stop            = new AtomicBoolean(false);
        final CountDownLatch startLatch      = new CountDownLatch(1);
        final AtomicLong     storeAfterClose = new AtomicLong(0);

        System.out.println("Starting close-under-load test (warmup " + WARMUP.toSeconds() + "s)...");

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(data, tempDir))
        {
            storage.storeRoot();

            final BatchStorer storer = storage.createBatchStorer(
                    BatchStorer.Controller(Duration.ofMillis(500)),
                    Duration.ofMillis(100)
            );

            final ExecutorService executor = Executors.newFixedThreadPool(WRITER_THREADS);

            for (int t = 0; t < WRITER_THREADS; t++)
            {
                final int idx = t;
                executor.submit(() ->
                {
                    try { startLatch.await(); }
                    catch (final InterruptedException e) { Thread.currentThread().interrupt(); return; }

                    int iteration = 0;
                    while (!stop.get())
                    {
                        try
                        {
                            synchronized (data)
                            {
                                data.add(new DataItem(data.size(), "w" + idx + "-" + iteration));
                                storer.store(data);
                            }
                            iteration++;
                            Thread.yield();
                        }
                        catch (final Exception e)
                        {
                            // After close() any exception from store() is acceptable.
                            storeAfterClose.incrementAndGet();
                        }
                    }
                });
            }

            startLatch.countDown();
            Thread.sleep(WARMUP.toMillis());

            System.out.println("Calling close() while writers are running...");
            final long closedAt = System.currentTimeMillis();

            // close() must not deadlock — run it in a separate thread with a timeout.
            final Thread closingThread = new Thread(storer::close);
            closingThread.start();
            closingThread.join(30_000);

            System.out.println("close() returned after " + (System.currentTimeMillis() - closedAt) + "ms");
            assertFalse(closingThread.isAlive(), "close() deadlocked - did not return within 30s");

            // Keep writers running to exercise post-close store() calls.
            Thread.sleep(WRITER_RUN_AFTER_CLOSE.toMillis());

            stop.set(true);
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);

            System.out.println("store() calls after close(): " + storeAfterClose.get());
            System.out.println("In-memory size: " + data.size());
        }

        System.out.println("Reloading storage from disk...");

        try (EmbeddedStorageManager reloadStorage = EmbeddedStorage.start(tempDir))
        {
            @SuppressWarnings("unchecked")
            final List<DataItem> reloaded = (List<DataItem>) reloadStorage.root();

            assertFalse(reloaded.isEmpty(), "Reloaded list must not be empty");

            int nulls = 0;
            for (int i = 0; i < reloaded.size(); i++)
            {
                if (reloaded.get(i) == null) nulls++;
            }
            System.out.println("Reloaded size: " + reloaded.size() + " | null items: " + nulls);
            assertTrue(nulls == 0, "Found " + nulls + " null items after reload");
        }
    }
}

