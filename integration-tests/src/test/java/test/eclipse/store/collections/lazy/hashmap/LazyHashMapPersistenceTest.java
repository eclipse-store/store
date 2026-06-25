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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyHashMapPersistenceTest {


    @SuppressWarnings("unchecked")
    @Test
    void EmptyMap(@TempDir final Path path) {
        LazyHashMap<Integer, String> map = new LazyHashMap<>(3);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {
        }

        LazyHashMap<Integer, String> map2 = new LazyHashMap<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map2, path)) {

            assertNotNull(map2);
            map2.remove(12);
            map2.put(111, "Hello");

            assertEquals("Hello", map2.get(111));
        }

    }

    @Test
    void testToString(@TempDir final Path path) {
        LazyHashMap<Integer, String> map = new LazyHashMap<>(3);
        for (int i = 0; i < 10; i++) {
            map.put(i, "E" + i);
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {

            map.values()
                    .forEach(String::isBlank);

            assertEquals("{[ 1 unloaded Elements], [ 1 unloaded Elements], [ 1 unloaded Elements], [3=E3], [4=E4], [5=E5], [6=E6], [7=E7, 8=E8, 9=E9]}",
                    map.toString());

        }
    }

    @Test
    void testToStringEmpty(@TempDir final Path path) {
        LazyHashMap<Integer, String> map = new LazyHashMap<>(3);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {
            final AtomicInteger counter = new AtomicInteger();
            map.segments()
                    .forEach(s -> {
                        if (counter.getAndIncrement() % 2 > 0) {
                            s.unloadSegment();
                        }
                    });

            assertEquals("{}", map.toString());

        }
    }

    @Test
    void isSegmentModifiedTest(@TempDir final Path path) {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        map.put(1, "ahoj");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {

            map.segments()
                    .forEach(s -> {
                        assertTrue(s.isLoaded());
                        assertFalse(s.isModified());
                    });

            map.put(1, "super");

            map.segments()
                    .forEach(s -> {
                        assertTrue(s.isLoaded());
                        assertTrue(s.isModified());
                    });

            storageManager.store(map);
        }

        LazyHashMap<Integer, String> reloadedMap = new LazyHashMap<>();
        try (final EmbeddedStorageManager reloadedStorage = EmbeddedStorage.start(reloadedMap, path)) {
            assertEquals(map.get(1), reloadedMap.get(1));
        }
    }

    @Test
    void replaceTest(@TempDir final Path path) {
        LazyHashMap<String, MapEntry> map = new LazyHashMap<>(13);
        map.put("Key 1", new MapEntry("to be replaced"));

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {
            map.replace("Key 1", new MapEntry("has been replaced"));
            storage.store(map);
        }

        LazyHashMap<String, MapEntry> reloadedMap = new LazyHashMap<>();
        try (final EmbeddedStorageManager reloadedStorage = EmbeddedStorage.start(reloadedMap, path)) {
            final MapEntry reloadedEntry = reloadedMap.get("Key 1");
            assertEquals("has been replaced", reloadedEntry.name);
        }

    }

    static class MapEntry {
        String name;

        public MapEntry(final String name) {
            super();
            this.name = name;
        }

        @Override
        public String toString() {
            return "id: " + this.name;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.name);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (this.getClass() != obj.getClass())
                return false;
            final MapEntry other = (MapEntry) obj;
            return Objects.equals(this.name, other.name);
        }
    }
}
