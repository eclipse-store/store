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

import org.eclipse.serializer.collections.lazy.LazyCollection;
import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LazyHashMapStreamsTest {

    @Test
    void readValueStreamAndCloseTest(@TempDir final Path path) {
        final LazyHashMap<String, String> map = createMap(6, 100);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {

            final LazyCollection<String> values = map.values();
            values.iterateLazyReferences(l -> l.clear());
            values.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            long count;
            try (final Stream<String> stream = values.stream()) {
                count = stream
                        .filter(e -> e.contains("Value"))
                        //.peek( e -> System.out.println(e))
                        .count();
            }

            assertEquals(100, count);

            TestHelpers.assertLoadedSegment(map);
        }
    }

    @Test
    void readValueParallelStreamAndCloseTest(@TempDir final Path path) {
        final LazyHashMap<String, String> map = createMap(6, 100);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {

            final LazyCollection<String> values = map.values();
            values.iterateLazyReferences(l -> l.clear());
            values.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            long count;
            try (final Stream<String> stream = values.parallelStream()) {
                count = stream
                        .filter(e -> e.contains("Value"))
                        .count();
            }

            assertEquals(100, count);

            TestHelpers.assertLoadedSegment(map);

        }
    }

    @Test
    void readKeyStreamAndCloseTest(@TempDir final Path path) {
        final LazyHashMap<String, String> map = createMap(6, 100);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {

            final LazyCollection<String> keys = map.keySet();
            keys.iterateLazyReferences(l -> l.clear());
            keys.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            long count;
            try (final Stream<String> stream = keys.stream()) {
                count = stream
                        .filter(e -> e.contains("Key"))
                        //.peek( e -> System.out.println(e))
                        .count();
            }

            assertEquals(100, count);
            TestHelpers.assertLoadedSegment(map);
        }
    }

    @Test
    void readKeyParallelStreamAndCloseTest(@TempDir final Path path) {
        final LazyHashMap<String, String> map = createMap(6, 100);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {
            final LazyCollection<String> keys = map.keySet();
            keys.iterateLazyReferences(l -> l.clear());
            keys.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            long count;
            try (final Stream<String> stream = keys.parallelStream()) {
                count = stream
                        .filter(e -> e.contains("Key"))
                        //.peek( e -> System.out.println(e))
                        .count();
            }

            assertEquals(100, count);
            TestHelpers.assertLoadedSegment(map);
        }
    }

    @Test
    void readEntryStreamAndCloseTest(@TempDir final Path path) {
        final LazyHashMap<String, String> map = createMap(6, 100);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {

            final LazyCollection<Entry<String, String>> entries = map.entrySet();
            entries.iterateLazyReferences(l -> l.clear());
            entries.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            long count;
            try (final Stream<Entry<String, String>> stream = entries.stream()) {
                count = stream
                        .filter(e -> e.getKey()
                                .contains("Key"))
                        .filter(e -> e.getValue()
                                .contains("Value"))
                        .count();
            }

            assertEquals(100, count);
            TestHelpers.assertLoadedSegment(map);
        }
    }

    @Test
    void readEntryParallelStreamAndCloseTest(@TempDir final Path path) {
        final LazyHashMap<String, String> map = createMap(6, 100);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(map, path)) {

            storage.setRoot(map);
            storage.storeRoot();

            final LazyCollection<Entry<String, String>> entries = map.entrySet();
            entries.iterateLazyReferences(l -> l.clear());
            entries.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            long count;
            try (final Stream<Entry<String, String>> stream = entries.parallelStream()) {
                count = stream
                        .filter(e -> e.getKey()
                                .contains("Key"))
                        .filter(e -> e.getValue()
                                .contains("Value"))
                        //.peek( e -> System.out.println(e))
                        .count();
            }

            assertEquals(100, count);
            TestHelpers.assertLoadedSegment(map);
        }
    }

    private LazyHashMap<String, String> createMap(int maxSegmentSize, int count) {
        final LazyHashMap<String, String> map = new LazyHashMap<>(maxSegmentSize);

        for (int i = 0; i < count; i++) {
            map.put("Key " + i, "Value " + i);
        }
        return map;
    }


}
