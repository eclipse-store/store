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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip tests for java.util.Set.of(...) (JDK 9).
 *
 * Backing types observed on JDK 17:
 *   Set.of()              -> java.util.ImmutableCollections$SetN
 *   Set.of(x)             -> java.util.ImmutableCollections$Set12
 *   Set.of(x, y)          -> java.util.ImmutableCollections$Set12
 *   Set.of(x, y, z, ...)  -> java.util.ImmutableCollections$SetN
 *
 * Eclipse Serializer ships BinaryHandlerImmutableCollectionsSet12 but no
 * handler for SetN. Empty Set.of() is SetN, not Set12.
 *
 * Iteration order in SetN is *deliberately randomized* per JVM via SALT,
 * so we only assert membership and size, never order.
 */
public class SetOfTest
{
    @TempDir
    Path workDir;

    private Set<String> stateDataField;

    @Test
    void setOfEmptyTest()
    {
        // Empty Set.of() returns SetN.
        stateDataField = Set.of();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertNotNull(set);
            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
            assertEquals(stateDataField, set);
            assertThrows(UnsupportedOperationException.class, () -> set.add("x"));
        }
    }

    @Test
    void setOfSingleTest()
    {
        stateDataField = Set.of("only");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("only"));
            assertEquals(stateDataField, set);
            assertThrows(UnsupportedOperationException.class, () -> set.add("x"));
        }
    }

    @Test
    void setOfTwoTest()
    {
        stateDataField = Set.of("a", "b");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(2, set.size());
            assertTrue(set.contains("a"));
            assertTrue(set.contains("b"));
            assertEquals(stateDataField, set);
            assertThrows(UnsupportedOperationException.class, () -> set.remove("a"));
        }
    }

    @Test
    void setOfThreeTest()
    {
        // Size 3 crosses the Set12 -> SetN boundary.
        stateDataField = Set.of("a", "b", "c");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains("a"));
            assertTrue(set.contains("b"));
            assertTrue(set.contains("c"));
            assertEquals(stateDataField, set);
            assertThrows(UnsupportedOperationException.class, () -> set.add("d"));
        }
    }

    @Test
    void setOfFiveTest()
    {
        stateDataField = Set.of("a", "b", "c", "d", "e");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(5, set.size());
            assertTrue(set.containsAll(stateDataField));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setOfHundredTest()
    {
        String[] arr = new String[100];
        for (int i = 0; i < 100; i++) {
            arr[i] = "v_" + i;
        }
        stateDataField = Set.of(arr);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(100, set.size());
            assertTrue(set.contains("v_0"));
            assertTrue(set.contains("v_99"));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setOfWithIntegersTest()
    {
        Set<Integer> ints = Set.of(1, 2, 3, 100, -5, Integer.MIN_VALUE, Integer.MAX_VALUE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ints, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Integer> set = (Set<Integer>) storageManager.root();
            assertEquals(ints.size(), set.size());
            assertEquals(ints, set);
        }
    }

    @Test
    void setOfNestedTest()
    {
        Set<Set<String>> nested = Set.of(
            Set.of("a", "b"),
            Set.of("c", "d", "e"),
            Set.of()
        );

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(nested, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Set<String>> set = (Set<Set<String>>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains(Set.of("a", "b")));
            assertTrue(set.contains(Set.of("c", "d", "e")));
            assertTrue(set.contains(Set.of()));
            assertEquals(nested, set);
        }
    }

    @Test
    void setOfHashCodeAndEqualsTest()
    {
        stateDataField = Set.of("alpha", "beta", "gamma");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(stateDataField.hashCode(), set.hashCode());
            assertEquals(stateDataField, set);
            assertEquals(set, new HashSet<>(Arrays.asList("alpha", "beta", "gamma")));
        }
    }

    @Test
    void setOfRejectsMutationAfterReloadTest()
    {
        // Type-preservation: if the reloaded set silently becomes a mutable
        // HashSet, these assertions fail.
        stateDataField = Set.of("x", "y", "z");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertThrows(UnsupportedOperationException.class, () -> set.add("new"));
            assertThrows(UnsupportedOperationException.class, () -> set.remove("x"));
            assertThrows(UnsupportedOperationException.class, () -> set.clear());
            assertThrows(UnsupportedOperationException.class, () -> set.addAll(Arrays.asList("p", "q")));
        }
    }

    @Test
    void setOfFromCopyOfTest()
    {
        // Set.copyOf also routes through ImmutableCollections.
        HashSet<String> src = new HashSet<>(Arrays.asList("p", "q", "r"));
        Set<String> immutable = Set.copyOf(src);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(immutable, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertEquals(immutable, set);
            assertThrows(UnsupportedOperationException.class, () -> set.add("s"));
        }
    }
}
