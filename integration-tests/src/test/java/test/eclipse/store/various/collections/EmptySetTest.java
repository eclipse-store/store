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

public class EmptySetTest
{
    @TempDir
    Path workDir;

    private Set<String> stateDataField;

    @Test
    void emptySetBasicTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
        }
    }

    @Test
    void emptySetIsEmptyTest()
    {
        stateDataField = Collections.emptySet();
        assertTrue(stateDataField.isEmpty());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
        }
    }

    @Test
    void emptySetSizeTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(0, set.size());
        }
    }

    @Test
    void emptySetContainsTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertFalse(set.contains("anything"));
            assertFalse(set.contains(null));
            assertFalse(set.contains(""));
        }
    }

    @Test
    void emptySetIteratorTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            int count = 0;
            for (String element : set) {
                count++;
            }
            assertEquals(0, count);
        }
    }

    @Test
    void emptySetToArrayTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Object[] array = set.toArray();
            assertEquals(0, array.length);
        }
    }

    @Test
    void emptySetContainsAllEmptyTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.containsAll(Collections.emptyList()));
        }
    }

    @Test
    void emptySetContainsAllNonEmptyTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertFalse(set.containsAll(Arrays.asList("element")));
        }
    }

    @Test
    void emptySetEqualsTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(Collections.emptySet(), set);
            assertEquals(new HashSet<>(), set);
        }
    }

    @Test
    void emptySetHashCodeTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(0, set.hashCode());
        }
    }

    @Test
    void emptySetToStringTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertNotNull(set.toString());
            assertEquals("[]", set.toString());
        }
    }

    @Test
    void emptySetIntegerTest()
    {
        Set<Integer> intSet = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(intSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Integer> set = (Set<Integer>) storageManager.root();
            assertTrue(set.isEmpty());
            assertFalse(set.contains(0));
            assertFalse(set.contains(1));
        }
    }

    @Test
    void emptySetLongTest()
    {
        Set<Long> longSet = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(longSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Long> set = (Set<Long>) storageManager.root();
            assertTrue(set.isEmpty());
            assertFalse(set.contains(0L));
        }
    }

    @Test
    void emptySetDoubleTest()
    {
        Set<Double> doubleSet = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(doubleSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Double> set = (Set<Double>) storageManager.root();
            assertTrue(set.isEmpty());
            assertFalse(set.contains(0.0));
        }
    }

    @Test
    void emptySetMultipleInstancesTest()
    {
        Set<String> emptySet1 = Collections.emptySet();
        Set<String> emptySet2 = Collections.emptySet();

        // Both should be equal
        assertEquals(emptySet1, emptySet2);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(emptySet1, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(emptySet2, set);
        }
    }

    @Test
    void emptySetNotEqualsNonEmptyTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Set<String> nonEmptySet = new HashSet<>();
            nonEmptySet.add("item");
            assertNotEquals(nonEmptySet, set);
        }
    }

    @Test
    void emptySetStreamTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(0, set.stream().count());
        }
    }

    @Test
    void emptySetCompareWithNewHashSetTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Set<String> newHashSet = new HashSet<>();
            assertEquals(newHashSet, set);
            assertEquals(newHashSet.size(), set.size());
        }
    }

    @Test
    void emptySetIteratorHasNextTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Iterator<String> iterator = set.iterator();
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    void emptySetForEachTest()
    {
        stateDataField = Collections.emptySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            final int[] count = {0};
            set.forEach(element -> count[0]++);
            assertEquals(0, count[0]);
        }
    }

    @Test
    void emptySetTypedTest()
    {
        Set<String> typedEmptySet = Collections.emptySet();
        stateDataField = typedEmptySet;

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
        }
    }
}

