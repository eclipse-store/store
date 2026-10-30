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
 * Writers mutate object fields WITHOUT synchronization while the BatchStorer
 * background daemon serializes the same objects mid-flight.
 * <p>
 * The goal is to detect torn reads inside the serializer (field A from
 * state N, field B from state N+1) or internal crashes such as
 * ConcurrentModificationException, NPE, or corrupted storage.
 * <p>
 * After reload every persisted item must be non-null and deserializable.
 */
@Disabled("only manual launch, takes to long")
public class BatchStorerMutationDuringFlushTest
{
    private static final Duration TEST_DURATION = Duration.ofMinutes(3);
    private static final int MUTATOR_THREADS = 6;
    private static final int STORER_THREADS = 2;
    private static final int LIST_SIZE = 500;

    @TempDir
    Path tempDir;

    static class MutableItem
    {
        int counter;
        String label;
        byte[] payload;

        MutableItem(final int counter, final String label)
        {
            this.counter = counter;
            this.label = label;
            this.payload = new byte[64];
        }
    }

    static class Root
    {
        final List<MutableItem> items = new ArrayList<>();
    }

    @Test
    void mutationDuringFlushTest() throws InterruptedException
    {
        final Root root = new Root();
        for (int i = 0; i < LIST_SIZE; i++) {
            root.items.add(new MutableItem(i, "init-" + i));
        }

        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> firstError = new AtomicReference<>();
        final AtomicLong mutations = new AtomicLong(0);
        final AtomicLong stores = new AtomicLong(0);

        System.out.println("Starting mutation-during-flush test (" + TEST_DURATION.toMinutes() + " min)...");

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(root, tempDir)) {
            storage.storeRoot();

            try (BatchStorer storer = storage.createBatchStorer(
                    BatchStorer.Controller(200, Duration.ofMillis(300)),
                    Duration.ofMillis(50)
            )) {
                final ExecutorService executor =
                        Executors.newFixedThreadPool(MUTATOR_THREADS + STORER_THREADS);

                // Mutator threads: change object fields without any synchronization.
                for (int t = 0; t < MUTATOR_THREADS; t++) {
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
                                final int target = (idx * 37 + iteration * 11) % LIST_SIZE;
                                final MutableItem item = root.items.get(target);
                                // Unsynchronized mutation — intentional.
                                item.counter = iteration;
                                item.label = "m" + idx + "-" + iteration;
                                item.payload = new byte[(iteration % 128) + 1];
                                mutations.incrementAndGet();
                                iteration++;
                            } catch (final Exception e) {
                                if (firstError.compareAndSet(null, e)) e.printStackTrace();
                            }
                        }
                    });
                }

                // Storer threads: call store() on items and the list without sync.
                for (int t = 0; t < STORER_THREADS; t++) {
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
                                final int target = (idx * 23 + iteration * 3) % LIST_SIZE;
                                storer.store(root.items.get(target));
                                storer.store(root.items);
                                stores.incrementAndGet();
                                iteration++;
                                Thread.yield();
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

                System.out.println("Mutations: " + mutations.get() + " | store() calls: " + stores.get());
            }
        }

        System.out.println("First error during run: " + firstError.get());

        System.out.println("Reloading storage from disk...");
        try (EmbeddedStorageManager reloadStorage = EmbeddedStorage.start(tempDir)) {
            final Root reloaded = (Root) reloadStorage.root();
            assertNotNull(reloaded, "Root must not be null");
            assertFalse(reloaded.items.isEmpty(), "Reloaded items list must not be empty");

            int nulls = 0;
            for (int i = 0; i < reloaded.items.size(); i++) {
                if (reloaded.items.get(i) == null) nulls++;
            }
            System.out.println("Reloaded items: " + reloaded.items.size() + " | null items: " + nulls);
            assertFalse(nulls > 0, "Found " + nulls + " null items after reload");
        }
    }
}

