package test.eclipse.store.collections.lazy.hashmap.unload;

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

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class UnloaderTest
{

    @TempDir
    Path location;

    SegmentStatistics loadedSegments;

    @Test
    void streamTestDefault()
    {
        LazyHashMap<Integer, MapPerson> personMap = HashMapGenerator.generate(200, new LazySegmentUnloader.Default(), 50);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personMap, location)) {

            personMap.keySet()
                    .stream()
                    .map(integer -> integer + 1)
                    .collect(Collectors.toList());

            personMap.entrySet()
                    .stream()
                    .map(mapPersonEntry -> mapPersonEntry.toString())
                    .collect(Collectors.toList());

            Assertions.assertEquals(2, getLoadedSegments(personMap).loaded);
        }
    }

    @Test
    @Disabled("flaky test")
    void parallelStreamTestDefault() throws InterruptedException
    {
        LazyHashMap<Integer, MapPerson> personMap = HashMapGenerator.generate(200, new LazySegmentUnloader.Default(), 50);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personMap, location)) {

            personMap.keySet()
                    .parallelStream()
                    .map(integer -> integer + 1)
                    .collect(Collectors.toList());

            personMap.entrySet()
                    .parallelStream()
                    .map(mapPersonEntry -> mapPersonEntry.toString())
                    .collect(Collectors.toList());

            Awaitility.await()
                    .atMost(Duration.ofMillis(5000))
                    .pollInterval(Duration.ofMillis(20))
                    .untilAsserted(() -> Assertions.assertEquals(2, getLoadedSegments(personMap).loaded));

        }
    }

    @Test
    void parallelStreamTestTimed() throws InterruptedException
    {
        LazyHashMap<Integer, MapPerson> personMap = HashMapGenerator.generate(2000, new LazySegmentUnloader.Timed(50), 100);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personMap, location)) {

            personMap.keySet()
                    .parallelStream()
                    .map(integer -> integer + 1)
                    .collect(Collectors.toList());

            personMap.entrySet()
                    .parallelStream()
                    .map(mapPersonEntry -> mapPersonEntry.toString())
                    .collect(Collectors.toList());

            Thread.sleep(80);
            personMap.get(6);

            Assertions.assertEquals(1, getLoadedSegments(personMap).loaded);
        }
    }

    @Test
    void parallelStreamTestNever() throws InterruptedException
    {
        LazyHashMap<Integer, MapPerson> personMap = HashMapGenerator.generate(20_000, new LazySegmentUnloader.Never());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personMap, location)) {

            personMap.keySet()
                    .parallelStream()
                    .map(integer -> integer + 1)
                    .collect(Collectors.toList());

            Assertions.assertEquals(0, getLoadedSegments(personMap).unloaded);
        }
    }

    @Test
    void tryUnloadTestDefault()
    {
        LazyHashMap<Integer, MapPerson> personMap = HashMapGenerator.generate(2000, new LazySegmentUnloader.Default(), 100);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personMap, location)) {

            personMap.keySet()
                    .parallelStream()
                    .map(integer -> integer + 1)
                    .collect(Collectors.toList());

            personMap.entrySet()
                    .parallelStream()
                    .map(mapPersonEntry -> mapPersonEntry.toString())
                    .collect(Collectors.toList());

            personMap.keySet().tryUnload(true);
            //System.out.println(getLoadedSegments(personMap));
            Assertions.assertEquals(0, getLoadedSegments(personMap).loaded);
        }
    }

    private SegmentStatistics getLoadedSegments(LazyHashMap<?, ?> map)
    {
        final AtomicInteger loadedCount = new AtomicInteger();
        final AtomicInteger unloadedCount = new AtomicInteger();
        map.segments()
                .forEach(s -> {
                    if (s.isLoaded()) {
                        loadedCount.incrementAndGet();
                    } else {
                        unloadedCount.incrementAndGet();
                    }
                });

        return new SegmentStatistics(loadedCount.get(), unloadedCount.get());
    }

    static class SegmentStatistics
    {
        int loaded;
        int unloaded;

        public SegmentStatistics(final int loaded, final int unloaded)
        {
            super();
            this.loaded = loaded;
            this.unloaded = unloaded;
        }

        @Override
        public String toString()
        {
            return "SegmentStatistics [loaded=" + this.loaded + ", unloaded=" + this.unloaded + "]";
        }
    }
}
