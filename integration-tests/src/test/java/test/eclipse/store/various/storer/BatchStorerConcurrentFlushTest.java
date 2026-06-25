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
 * Scenario 2: concurrent flush() from multiple threads.
 *
 * Writers feed data into a single BatchStorer while a separate pool of flush
 * threads hammers storer.flush() in a tight loop.  The goal is to expose race
 * conditions between data accumulation and flushing inside BatchStorer.
 *
 * After the stress phase the storage is reloaded and basic integrity is
 * verified.
 */
@Disabled("only manual launch, takes to long")
public class BatchStorerConcurrentFlushTest
{
    private static final int      WRITER_THREADS    = 4;
    private static final int      FLUSH_THREADS     = 4;
    private static final int      INITIAL_LIST_SIZE = 500;
    private static final Duration TEST_DURATION     = Duration.ofMinutes(5);

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
    void concurrentFlushStressTest() throws InterruptedException
    {
        final List<DataItem> data = new ArrayList<>();
        for (int i = 0; i < INITIAL_LIST_SIZE; i++)
        {
            data.add(new DataItem(i, "initial-" + i));
        }

        final AtomicBoolean  stop       = new AtomicBoolean(false);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicLong     flushCalls = new AtomicLong(0);
        final AtomicLong     storeCalls = new AtomicLong(0);
        final AtomicReference<Throwable> firstError = new AtomicReference<>();

        System.out.println("Starting concurrent flush stress test (" + TEST_DURATION.toMinutes() + " min)...");

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(data, tempDir))
        {
            storage.storeRoot();

            try (BatchStorer storer = storage.createBatchStorer(
                    BatchStorer.Controller(10_000, Duration.ofSeconds(2)),
                    Duration.ofMillis(50)
            ))
            {
                final ExecutorService executor =
                        Executors.newFixedThreadPool(WRITER_THREADS + FLUSH_THREADS);

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
                                    storeCalls.incrementAndGet();

                                    final int target = (idx * 17 + iteration * 5) % data.size();
                                    final DataItem item = data.get(target);
                                    item.update("w" + idx + "-upd-" + iteration);
                                    storer.store(item);
                                    storeCalls.incrementAndGet();
                                }
                                iteration++;
                                Thread.yield();
                            }
                            catch (final Exception e)
                            {
                                firstError.compareAndSet(null, e);
                                e.printStackTrace();
                            }
                        }
                    });
                }

                for (int t = 0; t < FLUSH_THREADS; t++)
                {
                    executor.submit(() ->
                    {
                        try { startLatch.await(); }
                        catch (final InterruptedException e) { Thread.currentThread().interrupt(); return; }

                        while (!stop.get())
                        {
                            try
                            {
                                storer.flush();
                                flushCalls.incrementAndGet();
                                Thread.yield();
                            }
                            catch (final Exception e)
                            {
                                firstError.compareAndSet(null, e);
                                e.printStackTrace();
                            }
                        }
                    });
                }

                startLatch.countDown();
                Thread.sleep(TEST_DURATION.toMillis());
                stop.set(true);
                executor.shutdown();
                executor.awaitTermination(30, TimeUnit.SECONDS);

                System.out.println("store() calls: " + storeCalls.get());
                System.out.println("flush() calls: " + flushCalls.get());
                System.out.println("In-memory size: " + data.size());
            }
        }

        assertNull(firstError.get(), "Unexpected exception during stress: " + firstError.get());

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

