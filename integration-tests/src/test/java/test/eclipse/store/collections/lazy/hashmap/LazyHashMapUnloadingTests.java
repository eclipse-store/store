package test.eclipse.store.collections.lazy.hashmap;

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

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
public class LazyHashMapUnloadingTests {

    @Test
    void autoUnloadUnloadRemovedSegments(@TempDir final Path path) throws InterruptedException {

        LazyHashMap<String, String> map = new LazyHashMap<>(3, new LazySegmentUnloader.Timed(50));
        for (int i = 0; i < 100; i++) {
            map.put("MyKey" + i, "E" + i);
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {

            assertEquals(43, getLoadedSegments(map).loaded);
            assertEquals(0, getLoadedSegments(map).unloaded);

            for (int i = 30; i < 80; i++) {
                map.remove("MyKey" + i, "E" + i);
            }
            storageManager.store(map);

            Thread.sleep(100);
            map.get("MyKey30");

            assertEquals(1, getLoadedSegments(map).loaded);
            assertEquals(22, getLoadedSegments(map).unloaded);

        }

        LazyHashMap<String, String> map2 = new LazyHashMap<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map2, path)) {
            map2.entrySet()
                    .iterateLazyReferences(l -> assertFalse(l.isLoaded()));
        }

    }

    @Test
    void unloadingWhilePut(@TempDir final Path path) {

        LazyHashMap<String, String> map = new LazyHashMap<>(10);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {

            for (int i = 0; i < 100; i++) {
                map.put("MyKey" + i, "E" + i);
                storageManager.store(map);
                //   System.out.println(getLoadedSegements(map));
            }
            Assertions.assertNotEquals(0, getLoadedSegments(map).unloaded);
        }
    }


    @Test
    void unloadSegment(@TempDir final Path path) {
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {

            LazyHashMap<Integer, String> map = createLazyMap(44);

            storage.setRoot(map);
            storage.storeRoot();

            map.segments()
                    .forEach(LazyHashMap.Segment::unloadSegment);

            map.entrySet()
                    .iterateLazyReferences(l -> assertFalse(l.isLoaded()));

        }
    }

    @Test
    void unloadStored(@TempDir final Path path) {

        LazyHashMap<Integer, String> map = createLazyMap(44);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {

            map.entrySet()
                    .iterateLazyReferences(l -> l.clear());
            map.entrySet()
                    .iterateLazyReferences(l -> assertFalse(l.isLoaded()));

        }
    }

    @Test
    void UnloadNotStored(@TempDir final Path path) {

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {

            LazyHashMap<Integer, String> map = createLazyMap(44);
            storage.setRoot(map);

            map.entrySet()
                    .iterateLazyReferences(l -> l.clear());
            map.entrySet()
                    .iterateLazyReferences(l -> assertTrue(l.isLoaded()));

        }
    }

    @Test
    void unloaded_reloadedStorage(@TempDir final Path path) {

        try (final EmbeddedStorageManager storageReloaded = createRealodedStorageWithList(path)) {

            @SuppressWarnings("unchecked")
            LazyHashMap<Integer, String> mapReloaded = (LazyHashMap<Integer, String>) storageReloaded.root();

            mapReloaded.entrySet()
                    .iterateLazyReferences(l -> assertFalse(l.isLoaded()));
            mapReloaded.entrySet()
                    .iterateLazyReferences(l -> l.clear());

        }
    }

    @Test
    void UnloadNotStored_reloadedStorage(@TempDir final Path path) {

        try (final EmbeddedStorageManager storageReloaded = createRealodedStorageWithList(path)) {

            @SuppressWarnings("unchecked")
            LazyHashMap<Integer, String> mapReloaded = (LazyHashMap<Integer, String>) storageReloaded.root();
            mapReloaded.entrySet()
                    .iterateLazyReferences(l -> assertFalse(l.isLoaded()));

            mapReloaded.put(44, "New Entry");

            mapReloaded.entrySet()
                    .iterateLazyReferences(l -> l.clear());

            mapReloaded.segments()
                    .forEach(s -> {
                        if (s.isModified()) {
                            assertTrue(s.isLoaded(), "expected modified segment to be loaded!");
                        } else {
                            assertFalse(s.isLoaded(), "expectedunmodified segment to be unloaded!");
                        }
                    });

            storageReloaded.store(mapReloaded);
            mapReloaded.entrySet()
                    .iterateLazyReferences(l -> l.clear());
            mapReloaded.entrySet()
                    .iterateLazyReferences(l -> assertFalse(l.isLoaded()));

        }
    }


    static EmbeddedStorageManager createRealodedStorageWithList(final Path path) {

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {
            LazyHashMap<Integer, String> map = createLazyMap(44);
            storage.setRoot(map);
            storage.storeRoot();
            storage.shutdown();

            System.gc();

        }

        return EmbeddedStorage.start(path);
    }

    private static LazyHashMap<Integer, String> createLazyMap(final int size) {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        for (int i = 0; i < size; i++) {
            map.put(i, "Entry " + i);
        }
        return map;
    }

    private SegmentStatistics getLoadedSegments(LazyHashMap<?, ?> map) {
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

    static class SegmentStatistics {
        int loaded;
        int unloaded;

        public SegmentStatistics(final int loaded, final int unloaded) {
            super();
            this.loaded = loaded;
            this.unloaded = unloaded;
        }

        @Override
        public String toString() {
            return "SegmentStatistics [loaded=" + this.loaded + ", unloaded=" + this.unloaded + "]";
        }
    }
}
