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

public class UnmodifiableSetTest
{
    @TempDir
    Path workDir;

    private Set<String> stateDataField;

    @Test
    void unmodifiableSetBasicTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("one");
        baseSet.add("two");
        baseSet.add("three");

        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains("one"));
            assertTrue(set.contains("two"));
            assertTrue(set.contains("three"));
        }
    }

    @Test
    void unmodifiableSetEmptyTest()
    {
        stateDataField = Collections.unmodifiableSet(new HashSet<>());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
        }
    }

    @Test
    void unmodifiableSetSingleElementTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("single");
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("single"));
        }
    }

    @Test
    void unmodifiableSetWithLinkedHashSetTest()
    {
        LinkedHashSet<String> baseSet = new LinkedHashSet<>();
        baseSet.add("first");
        baseSet.add("second");
        baseSet.add("third");
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains("first"));
            assertTrue(set.contains("second"));
            assertTrue(set.contains("third"));
        }
    }

    @Test
    void unmodifiableSetWithTreeSetTest()
    {
        TreeSet<String> baseSet = new TreeSet<>();
        baseSet.add("zebra");
        baseSet.add("apple");
        baseSet.add("mango");
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains("zebra"));
            assertTrue(set.contains("apple"));
            assertTrue(set.contains("mango"));
        }
    }

    @Test
    void unmodifiableSetWithNullTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add(null);
        baseSet.add("one");
        baseSet.add("two");
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains(null));
            assertTrue(set.contains("one"));
            assertTrue(set.contains("two"));
        }
    }

    @Test
    void unmodifiableSetLargeDatasetTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            baseSet.add("element_" + i);
        }
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(100, set.size());
            for (int i = 0; i < 100; i++) {
                assertTrue(set.contains("element_" + i));
            }
        }
    }

    @Test
    void unmodifiableSetIteratorTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.addAll(Arrays.asList("one", "two", "three", "four"));
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            int count = 0;
            for (String element : set) {
                assertNotNull(element);
                count++;
            }
            assertEquals(4, count);
        }
    }

    @Test
    void unmodifiableSetWithIntegersTest()
    {
        HashSet<Integer> baseSet = new HashSet<>();
        baseSet.add(1);
        baseSet.add(2);
        baseSet.add(3);
        baseSet.add(100);
        baseSet.add(-5);
        Set<Integer> intSet = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(intSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Integer> set = (Set<Integer>) storageManager.root();
            assertEquals(5, set.size());
            assertTrue(set.contains(1));
            assertTrue(set.contains(100));
            assertTrue(set.contains(-5));
        }
    }

    @Test
    void unmodifiableSetToArrayTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.addAll(Arrays.asList("one", "two", "three"));
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Object[] array = set.toArray();
            assertEquals(3, array.length);
        }
    }

    @Test
    void unmodifiableSetContainsTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.addAll(Arrays.asList("a", "b", "c", "d"));
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.contains("a"));
            assertTrue(set.contains("d"));
            assertFalse(set.contains("z"));
        }
    }

    @Test
    void unmodifiableSetContainsAllTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.addAll(Arrays.asList("a", "b", "c", "d"));
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.containsAll(Arrays.asList("a", "b")));
            assertFalse(set.containsAll(Arrays.asList("a", "e")));
        }
    }

    @Test
    void unmodifiableSetMaxSizeTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            baseSet.add("item_" + i);
        }
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1000, set.size());
            assertTrue(set.contains("item_0"));
            assertTrue(set.contains("item_999"));
        }
    }

    @Test
    void unmodifiableSetEqualsTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.addAll(Arrays.asList("a", "b", "c"));
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            HashSet<String> expectedSet = new HashSet<>(Arrays.asList("a", "b", "c"));
            assertEquals(expectedSet, set);
        }
    }

    @Test
    void unmodifiableSetHashCodeTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.addAll(Arrays.asList("x", "y", "z"));
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertNotNull(set.hashCode());
        }
    }

    @Test
    void unmodifiableSetFromSetOfTest()
    {
        stateDataField = Collections.unmodifiableSet(Set.of("a", "b", "c"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains("a"));
            assertTrue(set.contains("b"));
            assertTrue(set.contains("c"));
        }
    }

    @Test
    void unmodifiableSetWithDuplicatesInBaseTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("duplicate");
        baseSet.add("duplicate"); // Set naturally handles duplicates
        baseSet.add("unique");
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(2, set.size());
            assertTrue(set.contains("duplicate"));
            assertTrue(set.contains("unique"));
        }
    }

    @Test
    void unmodifiableSetToStringTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("test");
        stateDataField = Collections.unmodifiableSet(baseSet);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertNotNull(set.toString());
            assertTrue(set.toString().contains("test"));
        }
    }

    @Test
    void unmodifiableSetIsEmptyTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        stateDataField = Collections.unmodifiableSet(baseSet);
        assertTrue(stateDataField.isEmpty());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
        }
    }
}

