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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip tests for the unmodifiable lists produced by:
 *   Stream.toList()                          (JDK 16)
 *   Collectors.toUnmodifiableList()          (JDK 10)
 *   Collectors.toUnmodifiableSet()           (JDK 10)
 *   Collectors.toUnmodifiableMap(...)        (JDK 10)
 *
 * On JDK 17 every Stream.toList() invocation returns
 * java.util.ImmutableCollections$ListN regardless of size — even for
 * 0 and 1 element. The List12 handler therefore never applies and these
 * collections always go through the generic / fallback path.
 *
 * The reloaded collection must remain unmodifiable: a successful add()
 * means the type was silently downgraded to ArrayList/HashSet/HashMap
 * during persistence, which is a behavior bug.
 */
public class StreamToListTest
{
    @TempDir
    Path workDir;

    @Test
    void streamToListEmptyTest()
    {
        // Even Stream.of().toList() returns ListN, not List0/List12.
        List<String> empty = Stream.<String>of().toList();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(empty, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertNotNull(list);
            assertTrue(list.isEmpty());
            assertEquals(empty, list);
            assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
        }
    }

    @Test
    void streamToListSingleTest()
    {
        List<String> single = Stream.of("only").toList();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(single, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(1, list.size());
            assertEquals("only", list.get(0));
            assertEquals(single, list);
            assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
        }
    }

    @Test
    void streamToListManyTest()
    {
        List<Integer> ints = IntStream.rangeClosed(1, 50).boxed().toList();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ints, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<Integer> list = (List<Integer>) storageManager.root();
            assertEquals(50, list.size());
            for (int i = 0; i < 50; i++) {
                assertEquals(Integer.valueOf(i + 1), list.get(i));
            }
            assertEquals(ints, list);
            assertThrows(UnsupportedOperationException.class, () -> list.add(99));
        }
    }

    @Test
    void streamToListPreservesOrderTest()
    {
        List<String> ordered = Stream.of("first", "second", "third", "fourth").toList();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ordered, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(List.of("first", "second", "third", "fourth"), list);
        }
    }

    @Test
    void streamToListWithNullsTest()
    {
        // Stream.toList() permits nulls (unlike List.of). This is one of the
        // few documented behavioural differences and worth a round-trip check.
        List<String> withNulls = Stream.of("a", null, "b", null, "c").toList();
        assertEquals(5, withNulls.size());
        assertNull(withNulls.get(1));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(withNulls, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(5, list.size());
            assertEquals("a", list.get(0));
            assertNull(list.get(1));
            assertEquals("b", list.get(2));
            assertNull(list.get(3));
            assertEquals("c", list.get(4));
            assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
        }
    }

    @Test
    void collectorsToUnmodifiableListTest()
    {
        List<String> list = Stream.of("a", "b", "c", "d")
            .collect(Collectors.toUnmodifiableList());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> reloaded = (List<String>) storageManager.root();
            assertEquals(4, reloaded.size());
            assertEquals(list, reloaded);
            assertThrows(UnsupportedOperationException.class, () -> reloaded.add("e"));
        }
    }

    @Test
    void collectorsToUnmodifiableSetTest()
    {
        Set<String> set = Stream.of("a", "b", "c", "d")
            .collect(Collectors.toUnmodifiableSet());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(set, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> reloaded = (Set<String>) storageManager.root();
            assertEquals(4, reloaded.size());
            assertEquals(set, reloaded);
            assertTrue(reloaded.containsAll(Arrays.asList("a", "b", "c", "d")));
            assertThrows(UnsupportedOperationException.class, () -> reloaded.add("e"));
        }
    }

    @Test
    void collectorsToUnmodifiableMapTest()
    {
        Map<String, Integer> map = Stream.of("a", "b", "c", "d")
            .collect(Collectors.toUnmodifiableMap(s -> s, String::length));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Map<String, Integer> reloaded = (Map<String, Integer>) storageManager.root();
            assertEquals(4, reloaded.size());
            assertEquals(map, reloaded);
            assertEquals(Integer.valueOf(1), reloaded.get("a"));
            assertThrows(UnsupportedOperationException.class, () -> reloaded.put("e", 1));
        }
    }

    @Test
    void streamToListEqualsArrayListTest()
    {
        // Defensive: equality against an ArrayList of the same content is the
        // contractual definition of List#equals.
        List<String> immutable = Stream.of("p", "q", "r", "s").toList();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(immutable, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> reloaded = (List<String>) storageManager.root();
            assertEquals(new ArrayList<>(Arrays.asList("p", "q", "r", "s")), reloaded);
            assertEquals(immutable.hashCode(), reloaded.hashCode());
        }
    }

    @Test
    void streamToListInsideHolderTest()
    {
        // A more realistic shape: an unmodifiable list embedded as a field
        // inside a domain object that is the storage root.
        Holder root = new Holder();
        root.label = "demo";
        root.items = Stream.of("a", "b", "c", "d", "e").toList();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Holder reloaded = (Holder) storageManager.root();
            assertEquals("demo", reloaded.label);
            assertEquals(5, reloaded.items.size());
            assertEquals(List.of("a", "b", "c", "d", "e"), reloaded.items);
            assertThrows(UnsupportedOperationException.class, () -> reloaded.items.add("f"));
        }
    }

    static class Holder
    {
        String label;
        List<String> items;
    }
}
