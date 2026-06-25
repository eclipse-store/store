package test.eclipse.store.collections.lazy.hashset;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static test.eclipse.store.collections.lazy.hashset.Util.faker;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.serializer.collections.lazy.LazyHashSet;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HashSetStoreTest {

    @TempDir
    Path location;

    @Test
    void saveLazyHashSet() {
        LazyHashSet<String> lazyHashSet = Util.generateLazyHashSet(10, 100);

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(lazyHashSet, location)) {

        }

        LazyHashSet<String> lazyHashSet1 = new LazyHashSet<>();
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(lazyHashSet1, location)) {
            assertIterableEquals(lazyHashSet, lazyHashSet1);
            assertEquals(lazyHashSet.size(), lazyHashSet1.size());
        }
    }

    @Test
    void unloaderTest() throws InterruptedException {
        LazyHashSet<String> lazyHashSet = new LazyHashSet<>(3, new LazySegmentUnloader.Timed(50));
        for (int i = 0; i < 100; i++) {
            lazyHashSet.add(faker.lorem()
                    .sentence());
        }
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(lazyHashSet, location)) {

            Thread.sleep(800);
            lazyHashSet.contains(50);

            AtomicInteger atomicInteger = new AtomicInteger(0);

            lazyHashSet.segments()
                    .forEach(segment -> {
                        if (segment.isLoaded()) {
                            atomicInteger.incrementAndGet();
                        }
                    });
            assertEquals(1, atomicInteger.get());
        }
    }

    @Test
    void unloaderStreamTest() throws InterruptedException {
        LazyHashSet<String> lazyHashSet = new LazyHashSet<>(3, new LazySegmentUnloader.Timed(50));
        for (int i = 0; i < 100; i++) {
            lazyHashSet.add(faker.lorem()
                    .sentence());
        }
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(lazyHashSet, location)) {

            Stream<String> stream = lazyHashSet.stream();
            stream.map(s -> s.toString()).collect(Collectors.toList());
            Thread.sleep(800);
            lazyHashSet.contains(50);

            AtomicInteger atomicInteger = new AtomicInteger(0);

            lazyHashSet.segments()
                    .forEach(segment -> {
                        if (segment.isLoaded()) {
                            atomicInteger.incrementAndGet();
                        }
                    });
            assertEquals(1, atomicInteger.get());
        }
    }
}
