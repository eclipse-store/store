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

public class CheckedSetTest
{
    @TempDir
    Path workDir;

    private Set<String> stateDataField;

    @Test
    void checkedSetBasicTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("one");
        baseSet.add("two");
        baseSet.add("three");

        stateDataField = Collections.checkedSet(baseSet, String.class);

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
    void checkedSetEmptyTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
        }
    }

    @Test
    void checkedSetSingleElementTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("single");
        stateDataField = Collections.checkedSet(baseSet, String.class);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("single"));
        }
    }

    @Test
    void checkedSetWithRemovalTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("alpha");
        baseSet.add("beta");
        baseSet.add("gamma");
        stateDataField = Collections.checkedSet(baseSet, String.class);
        stateDataField.remove("beta");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(2, set.size());
            assertTrue(set.contains("alpha"));
            assertFalse(set.contains("beta"));
            assertTrue(set.contains("gamma"));
        }
    }

    @Test
    void checkedSetWithLinkedHashSetTest()
    {
        LinkedHashSet<String> baseSet = new LinkedHashSet<>();
        baseSet.add("first");
        baseSet.add("second");
        baseSet.add("third");
        stateDataField = Collections.checkedSet(baseSet, String.class);

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
    void checkedSetWithTreeSetTest()
    {
        TreeSet<String> baseSet = new TreeSet<>();
        baseSet.add("zebra");
        baseSet.add("apple");
        baseSet.add("mango");
        stateDataField = Collections.checkedSet(baseSet, String.class);

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
    void checkedSetWithNullTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add(null);
        baseSet.add("one");
        baseSet.add("two");
        stateDataField = Collections.checkedSet(baseSet, String.class);

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
    void checkedSetClearTest()
    {
        HashSet<String> baseSet = new HashSet<>();
        baseSet.add("one");
        baseSet.add("two");
        stateDataField = Collections.checkedSet(baseSet, String.class);
        stateDataField.clear();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
        }
    }

    @Test
    void checkedSetAddAllTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.addAll(Arrays.asList("a", "b", "c", "d", "e"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(5, set.size());
            assertTrue(set.containsAll(Arrays.asList("a", "b", "c", "d", "e")));
        }
    }

    @Test
    void checkedSetRemoveAllTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.addAll(Arrays.asList("a", "b", "c", "d", "e"));
        stateDataField.removeAll(Arrays.asList("b", "d"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains("a"));
            assertFalse(set.contains("b"));
            assertTrue(set.contains("c"));
            assertFalse(set.contains("d"));
            assertTrue(set.contains("e"));
        }
    }

    @Test
    void checkedSetRetainAllTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.addAll(Arrays.asList("a", "b", "c", "d", "e"));
        stateDataField.retainAll(Arrays.asList("a", "c", "e"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains("a"));
            assertFalse(set.contains("b"));
            assertTrue(set.contains("c"));
            assertFalse(set.contains("d"));
            assertTrue(set.contains("e"));
        }
    }

    @Test
    void checkedSetLargeDatasetTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);

        for (int i = 0; i < 100; i++) {
            stateDataField.add("element_" + i);
        }

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
    void checkedSetIteratorTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.addAll(Arrays.asList("one", "two", "three", "four"));

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
    void checkedSetWithIntegersTest()
    {
        Set<Integer> intSet = Collections.checkedSet(new HashSet<>(), Integer.class);
        intSet.add(1);
        intSet.add(2);
        intSet.add(3);
        intSet.add(100);
        intSet.add(-5);

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
    void checkedSetToArrayTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.addAll(Arrays.asList("one", "two", "three"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Object[] array = set.toArray();
            assertEquals(3, array.length);
        }
    }

    @Test
    void checkedSetDuplicateAttemptTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        assertTrue(stateDataField.add("element"));
        assertFalse(stateDataField.add("element")); // duplicate should return false

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("element"));
        }
    }

    @Test
    void checkedSetRemoveNonExistentTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.add("one");
        assertFalse(stateDataField.remove("two"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
        }
    }

    @Test
    void checkedSetMaxSizeTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);

        for (int i = 0; i < 1000; i++) {
            stateDataField.add("item_" + i);
        }

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
    void checkedSetContainsAllTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.addAll(Arrays.asList("a", "b", "c", "d"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.containsAll(Arrays.asList("a", "b")));
            assertFalse(set.containsAll(Arrays.asList("a", "e")));
        }
    }

    @Test
    void checkedSetEmptyAfterAddAndRemoveTest()
    {
        stateDataField = Collections.checkedSet(new HashSet<>(), String.class);
        stateDataField.add("temporary");
        stateDataField.remove("temporary");
        assertTrue(stateDataField.isEmpty());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
        }
    }

    @Test
    void checkedSetWithLongTest()
    {
        Set<Long> longSet = Collections.checkedSet(new HashSet<>(), Long.class);
        longSet.add(1L);
        longSet.add(100L);
        longSet.add(1000000L);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(longSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Long> set = (Set<Long>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains(1L));
            assertTrue(set.contains(1000000L));
        }
    }
}

