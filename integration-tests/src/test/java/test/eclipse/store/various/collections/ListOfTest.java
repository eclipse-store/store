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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip tests for java.util.List.of(...) (JDK 9).
 * <p>
 * Backing types observed on JDK 17:
 * List.of()              -> java.util.ImmutableCollections$ListN
 * List.of(x)             -> java.util.ImmutableCollections$List12
 * List.of(x, y)          -> java.util.ImmutableCollections$List12
 * List.of(x, y, z, ...)  -> java.util.ImmutableCollections$ListN
 * <p>
 * Eclipse Serializer ships BinaryHandlerImmutableCollectionsList12 but no
 * handler for ListN, so every size != 1,2 hits a generic fallback or fails.
 * The empty List.of() is also ListN, so it is *not* covered by the List12 handler.
 * <p>
 * Each test asserts a strict round-trip: the reloaded instance must equal
 * the original by value, preserve iteration order, and remain immutable
 * (mutating it must still throw UnsupportedOperationException).
 */
public class ListOfTest
{
    @TempDir
    Path workDir;

    private List<String> stateDataField;

    @Test
    void listOfEmptyTest()
    {
        // Empty List.of() returns ListN, NOT List12 — easy to overlook.
        stateDataField = List.of();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertNotNull(list);
            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertEquals(stateDataField, list);
            assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
        }
    }

    @Test
    void listOfSingleElementTest()
    {
        stateDataField = List.of("only");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(1, list.size());
            assertEquals("only", list.get(0));
            assertEquals(stateDataField, list);
            assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
        }
    }

    @Test
    void listOfTwoElementsTest()
    {
        stateDataField = List.of("a", "b");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(2, list.size());
            assertEquals("a", list.get(0));
            assertEquals("b", list.get(1));
            assertEquals(stateDataField, list);
            assertThrows(UnsupportedOperationException.class, () -> list.set(0, "x"));
        }
    }

    @Test
    void listOfThreeElementsTest()
    {
        // Size 3 crosses the List12 -> ListN boundary.
        stateDataField = List.of("a", "b", "c");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(3, list.size());
            assertEquals(List.of("a", "b", "c"), list);
            assertEquals(stateDataField, list);
            assertThrows(UnsupportedOperationException.class, () -> list.add("d"));
        }
    }

    @Test
    void listOfFiveElementsTest()
    {
        stateDataField = List.of("a", "b", "c", "d", "e");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(5, list.size());
            for (int i = 0; i < 5; i++) {
                assertEquals(stateDataField.get(i), list.get(i));
            }
            assertEquals(stateDataField, list);
            assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
        }
    }

    @Test
    void listOfHundredElementsTest()
    {
        String[] arr = new String[100];
        for (int i = 0; i < 100; i++) {
            arr[i] = "v_" + i;
        }
        stateDataField = List.of(arr);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(100, list.size());
            assertEquals("v_0", list.get(0));
            assertEquals("v_99", list.get(99));
            assertEquals(stateDataField, list);
        }
    }

    @Test
    void listOfWithIntegersTest()
    {
        List<Integer> ints = List.of(1, 2, 3, 100, -5, Integer.MIN_VALUE, Integer.MAX_VALUE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ints, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<Integer> list = (List<Integer>) storageManager.root();
            assertEquals(ints.size(), list.size());
            for (int i = 0; i < ints.size(); i++) {
                assertEquals(ints.get(i), list.get(i));
            }
        }
    }

    @Test
    void listOfWithDuplicatesPreservedTest()
    {
        // List.of permits duplicates (only Set.of forbids them).
        stateDataField = List.of("dup", "dup", "dup");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(3, list.size());
            assertEquals(List.of("dup", "dup", "dup"), list);
        }
    }

    @Test
    void listOfNestedTest()
    {
        List<List<String>> nested = List.of(
                List.of("a", "b"),
                List.of("c", "d", "e"),
                List.of()
        );

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(nested, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<List<String>> list = (List<List<String>>) storageManager.root();
            assertEquals(3, list.size());
            assertEquals(List.of("a", "b"), list.get(0));
            assertEquals(List.of("c", "d", "e"), list.get(1));
            assertTrue(list.get(2).isEmpty());
            assertEquals(nested, list);
        }
    }

    @Test
    void listOfHashCodeAndEqualsTest()
    {
        stateDataField = List.of("alpha", "beta", "gamma");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(stateDataField.hashCode(), list.hashCode());
            assertEquals(stateDataField, list);
            assertEquals(list, new ArrayList<>(List.of("alpha", "beta", "gamma")));
        }
    }

    @Test
    void listOfIterationOrderTest()
    {
        stateDataField = List.of("first", "second", "third", "fourth", "fifth");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            List<String> collected = new ArrayList<>();
            list.forEach(collected::add);
            assertEquals(List.of("first", "second", "third", "fourth", "fifth"), collected);
        }
    }

    @Test
    void listOfRejectsAddAfterReloadTest()
    {
        // Strong type-preservation check: if the reloaded list silently
        // becomes a mutable ArrayList, this assertion fires.
        stateDataField = List.of("x", "y", "z");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertThrows(UnsupportedOperationException.class, () -> list.add("new"));
            assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
            assertThrows(UnsupportedOperationException.class, () -> list.set(0, "new"));
            assertThrows(UnsupportedOperationException.class, () -> list.clear());
        }
    }

    @Test
    void listOfFromCopyOfTest()
    {
        // List.copyOf also returns ImmutableCollections$ListN.
        ArrayList<String> src = new ArrayList<>(Arrays.asList("p", "q", "r"));
        List<String> immutable = List.copyOf(src);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(immutable, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> list = (List<String>) storageManager.root();
            assertEquals(3, list.size());
            assertEquals(immutable, list);
            assertThrows(UnsupportedOperationException.class, () -> list.add("s"));
        }
    }
}
