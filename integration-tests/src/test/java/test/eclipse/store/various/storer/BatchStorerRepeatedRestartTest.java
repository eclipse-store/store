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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("only manual launch, takes to long")
public class BatchStorerRepeatedRestartTest
{
    private static final int      CYCLES            = 20;
    private static final Duration CYCLE_DURATION    = Duration.ofSeconds(15);
    private static final int      WRITER_THREADS    = 4;
    private static final int      INITIAL_LIST_SIZE = 200;

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
    void repeatedRestartTest() throws InterruptedException
    {
        // Populate initial data in the first cycle.
        final List<DataItem> firstData = new ArrayList<>();
        for (int i = 0; i < INITIAL_LIST_SIZE; i++)
        {
            firstData.add(new DataItem(i, "initial-" + i));
        }

        try (EmbeddedStorageManager bootstrap = EmbeddedStorage.start(firstData, tempDir))
        {
        }

        int previousSize = INITIAL_LIST_SIZE;

        for (int cycle = 0; cycle < CYCLES; cycle++)
        {
            System.out.println("=== Cycle " + (cycle + 1) + "/" + CYCLES + " ===");

            final AtomicBoolean   stop         = new AtomicBoolean(false);
            final CountDownLatch  startLatch   = new CountDownLatch(1);
            final AtomicReference<Throwable> firstError = new AtomicReference<>();
            final int             cycleLabel   = cycle;
            final AtomicInteger   inMemorySize = new AtomicInteger(0);

            try (EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final List<DataItem> data = (List<DataItem>) storage.root();

                System.out.println("  Loaded size at cycle start: " + data.size());
                assertTrue(data.size() >= previousSize,
                        "Cycle " + cycleLabel + ": size " + data.size() + " is less than previous " + previousSize);

                try (BatchStorer storer = storage.createBatchStorer(
                        BatchStorer.Controller(Duration.ofMillis(500)),
                        Duration.ofMillis(100)
                ))
                {
                    final ExecutorService executor = Executors.newFixedThreadPool(WRITER_THREADS);

                    for (int t = 0; t < WRITER_THREADS; t++)
                    {
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
                            while (!stop.get())
                            {
                                try
                                {
                                    synchronized (data)
                                    {
                                        data.add(new DataItem(data.size(), "c" + cycleLabel + "-w" + idx + "-" + iteration));
                                        storer.store(data);

                                        final int target = (idx * 31 + iteration * 7) % data.size();
                                        final DataItem item = data.get(target);
                                        item.update("c" + cycleLabel + "-upd-" + idx + "-" + iteration);
                                        storer.store(item);
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

                    startLatch.countDown();
                    Thread.sleep(CYCLE_DURATION.toMillis());
                    stop.set(true);
                    executor.shutdown();
                    executor.awaitTermination(15, TimeUnit.SECONDS);

                    inMemorySize.set(data.size());
                    System.out.println("  In-memory size at cycle end: " + inMemorySize.get());
                    previousSize = inMemorySize.get();
                }
            }

            assertNull(firstError.get(), "Cycle " + cycle + " error: " + firstError.get());

            // Verify the data after close — reloaded size must exactly match in-memory size.
            try (EmbeddedStorageManager verify = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final List<DataItem> reloaded = (List<DataItem>) verify.root();

                assertFalse(reloaded.isEmpty(), "Cycle " + cycle + ": reloaded list is empty");

                int nulls = 0;
                for (int i = 0; i < reloaded.size(); i++)
                {
                    if (reloaded.get(i) == null) nulls++;
                }
                assertTrue(nulls == 0, "Cycle " + cycle + ": found " + nulls + " null items");

                System.out.println("  Reloaded size after cycle: " + reloaded.size()
                        + " | expected: " + inMemorySize.get() + " | nulls: " + nulls);
                assertEquals(inMemorySize.get(), reloaded.size(),
                        "Cycle " + cycle + ": reloaded size does not match in-memory size");

                previousSize = reloaded.size();
            }
        }

        System.out.println("All " + CYCLES + " cycles completed successfully.");
    }
}


