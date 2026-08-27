package test.eclipse.store.various.collections;

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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip tests for java.util.Map.of(...), Map.ofEntries(...) and Map.entry(...) (JDK 9).
 * <p>
 * Backing types observed on JDK 17:
 * Map.of()              -> java.util.ImmutableCollections$MapN
 * Map.of(k, v)          -> java.util.ImmutableCollections$Map1
 * Map.of(k1,v1, k2,v2)  -> java.util.ImmutableCollections$MapN
 * Map.entry(k, v)       -> java.util.KeyValueHolder
 * <p>
 * Unlike List.of and Set.of, Eclipse Serializer ships **no** dedicated
 * handler for Map.of variants and **no** handler for KeyValueHolder.
 * These tests therefore intentionally exercise the generic / fallback path
 * and check whether contents, equality and immutability survive a round trip.
 */
public class MapOfTest
{
    @TempDir
    Path workDir;

    private Map<String, String> stateDataField;

    @Test
    void mapOfEmptyTest()
    {
        stateDataField = Map.of();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertNotNull(map);
            assertTrue(map.isEmpty());
            assertEquals(0, map.size());
            assertEquals(stateDataField, map);
            assertThrows(UnsupportedOperationException.class, () -> map.put("k", "v"));
        }
    }

    @Test
    void mapOfSingleEntryTest()
    {
        // Single-entry Map.of returns Map1.
        stateDataField = Map.of("k1", "v1");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertEquals(1, map.size());
            assertEquals("v1", map.get("k1"));
            assertEquals(stateDataField, map);
            assertThrows(UnsupportedOperationException.class, () -> map.put("k2", "v2"));
        }
    }

    @Test
    void mapOfTwoEntriesTest()
    {
        // Two-entry Map.of crosses Map1 -> MapN.
        stateDataField = Map.of("k1", "v1", "k2", "v2");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertEquals(2, map.size());
            assertEquals("v1", map.get("k1"));
            assertEquals("v2", map.get("k2"));
            assertEquals(stateDataField, map);
        }
    }

    @Test
    void mapOfFiveEntriesTest()
    {
        stateDataField = Map.of(
                "k1", "v1",
                "k2", "v2",
                "k3", "v3",
                "k4", "v4",
                "k5", "v5"
        );

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertEquals(5, map.size());
            for (int i = 1; i <= 5; i++) {
                assertEquals("v" + i, map.get("k" + i));
            }
            assertEquals(stateDataField, map);
            assertThrows(UnsupportedOperationException.class, () -> map.put("k6", "v6"));
        }
    }

    @Test
    void mapOfTenEntriesTest()
    {
        // Map.of has explicit overloads up to 10 entries.
        stateDataField = Map.of(
                "k1", "v1",
                "k2", "v2",
                "k3", "v3",
                "k4", "v4",
                "k5", "v5",
                "k6", "v6",
                "k7", "v7",
                "k8", "v8",
                "k9", "v9",
                "k10", "v10"
        );

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertEquals(10, map.size());
            for (int i = 1; i <= 10; i++) {
                assertEquals("v" + i, map.get("k" + i));
            }
            assertEquals(stateDataField, map);
        }
    }

    @Test
    void mapOfEntriesLargeTest()
    {
        // Map.ofEntries() with > 10 entries — the only way to build big immutable maps.
        @SuppressWarnings("unchecked")
        Map.Entry<String, Integer>[] entries = new Map.Entry[100];
        for (int i = 0; i < 100; i++) {
            entries[i] = Map.entry("k" + i, i);
        }
        Map<String, Integer> bigMap = Map.ofEntries(entries);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(bigMap, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, Integer> map = (Map<String, Integer>) storageManager.root();
            assertEquals(100, map.size());
            for (int i = 0; i < 100; i++) {
                assertEquals(Integer.valueOf(i), map.get("k" + i));
            }
            assertEquals(bigMap, map);
            assertThrows(UnsupportedOperationException.class, () -> map.put("k100", 100));
        }
    }

    @Test
    @Disabled("https://github.com/microstream-one/internal/issues/54")
    void mapEntryStandaloneTest()
    {
        // Map.entry(...) returns KeyValueHolder — its own immutable Map.Entry.
        Map.Entry<String, Integer> entry = Map.entry("foo", 42);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(entry, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map.Entry<String, Integer> reloaded = (Map.Entry<String, Integer>) storageManager.root();
            assertNotNull(reloaded);
            assertEquals("foo", reloaded.getKey());
            assertEquals(Integer.valueOf(42), reloaded.getValue());
            assertEquals(entry, reloaded);
            assertEquals(entry.hashCode(), reloaded.hashCode());
            // KeyValueHolder is immutable.
            assertThrows(UnsupportedOperationException.class, () -> reloaded.setValue(99));
        }
    }

    @Test
    void mapEntryWithNullValueTest()
    {
        // Map.entry forbids nulls — sanity check that the JDK constructor still
        // rejects them (not strictly a serializer test, but useful to surface
        // any wrapper that *would* be acceptable here).
        assertThrows(NullPointerException.class, () -> Map.entry("k", null));
        assertThrows(NullPointerException.class, () -> Map.entry(null, "v"));
    }

    @Test
    void mapOfWithIntegersTest()
    {
        Map<Integer, Integer> ints = Map.of(
                1, 11,
                2, 22,
                3, 33,
                -1, -11,
                Integer.MIN_VALUE, Integer.MAX_VALUE
        );

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ints, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<Integer, Integer> map = (Map<Integer, Integer>) storageManager.root();
            assertEquals(ints.size(), map.size());
            assertEquals(ints, map);
            assertEquals(Integer.valueOf(33), map.get(3));
            assertEquals(Integer.valueOf(Integer.MAX_VALUE), map.get(Integer.MIN_VALUE));
        }
    }

    @Test
    void mapOfNestedTest()
    {
        Map<String, Map<String, Integer>> nested = Map.of(
                "a", Map.of("x", 1, "y", 2),
                "b", Map.of("z", 3),
                "c", Map.of()
        );

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(nested, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, Map<String, Integer>> map = (Map<String, Map<String, Integer>>) storageManager.root();
            assertEquals(3, map.size());
            assertEquals(Map.of("x", 1, "y", 2), map.get("a"));
            assertEquals(Map.of("z", 3), map.get("b"));
            assertTrue(map.get("c").isEmpty());
            assertEquals(nested, map);
        }
    }

    @Test
    void mapOfHashCodeAndEqualsTest()
    {
        stateDataField = Map.of("k1", "v1", "k2", "v2", "k3", "v3");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertEquals(stateDataField.hashCode(), map.hashCode());
            assertEquals(stateDataField, map);

            HashMap<String, String> reference = new HashMap<>();
            reference.put("k1", "v1");
            reference.put("k2", "v2");
            reference.put("k3", "v3");
            assertEquals(reference, map);
        }
    }

    @Test
    void mapOfRejectsMutationAfterReloadTest()
    {
        // If the reloaded map silently becomes a mutable HashMap, these fail.
        stateDataField = Map.of("a", "1", "b", "2", "c", "3");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertThrows(UnsupportedOperationException.class, () -> map.put("d", "4"));
            assertThrows(UnsupportedOperationException.class, () -> map.remove("a"));
            assertThrows(UnsupportedOperationException.class, () -> map.clear());
            assertThrows(UnsupportedOperationException.class, () -> map.putAll(Map.of("e", "5")));
        }
    }

    @Test
    void mapCopyOfTest()
    {
        // Map.copyOf returns ImmutableCollections.MapN.
        HashMap<String, String> src = new HashMap<>();
        src.put("k1", "v1");
        src.put("k2", "v2");
        src.put("k3", "v3");
        Map<String, String> immutable = Map.copyOf(src);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(immutable, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, String> map = (Map<String, String>) storageManager.root();
            assertEquals(3, map.size());
            assertEquals(immutable, map);
            assertThrows(UnsupportedOperationException.class, () -> map.put("k4", "v4"));
        }
    }
}
