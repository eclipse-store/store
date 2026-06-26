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
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SetFromMapTest
{
    @TempDir
    Path workDir;


    private Set<String> stateDataField;

    //https://github.com/eclipse-serializer/serializer/pull/138
    @Test
    void setFromMapTest()
    {

        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>(5);

        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("one");
        stateDataField.add("two");
        stateDataField.add("three");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }


        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            //compare two sets
            assertIterableEquals(stateDataField, set);
        }

    }

    @Test
    void setFromMapEmptyTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapWithRemovalTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("one");
        stateDataField.add("two");
        stateDataField.add("three");
        stateDataField.remove("two");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(2, set.size());
            assertTrue(set.contains("one"));
            assertFalse(set.contains("two"));
            assertTrue(set.contains("three"));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromHashMapTest()
    {
        HashMap<String, Boolean> map = new HashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("alpha");
        stateDataField.add("beta");
        stateDataField.add("gamma");
        stateDataField.add("delta");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(4, set.size());
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromTreeMapTest()
    {
        TreeMap<String, Boolean> map = new TreeMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("zebra");
        stateDataField.add("apple");
        stateDataField.add("mango");
        stateDataField.add("banana");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(4, set.size());
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromConcurrentHashMapTest()
    {
        ConcurrentHashMap<String, Boolean> map = new ConcurrentHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("concurrent1");
        stateDataField.add("concurrent2");
        stateDataField.add("concurrent3");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapWithDuplicateAttemptTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        assertTrue(stateDataField.add("element"));
        assertFalse(stateDataField.add("element")); // duplicate should return false

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("element"));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapLargeDatasetTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);

        for (int i = 0; i < 100; i++) {
            stateDataField.add("element_" + i);
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(100, set.size());
            assertEquals(stateDataField, set);

            for (int i = 0; i < 100; i++) {
                assertTrue(set.contains("element_" + i));
            }
        }
    }

    @Test
    void setFromMapClearTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("one");
        stateDataField.add("two");
        stateDataField.clear();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapWithNullTest()
    {
        HashMap<String, Boolean> map = new HashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add(null);
        stateDataField.add("one");
        stateDataField.add("two");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertTrue(set.contains(null));
            assertTrue(set.contains("one"));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapSingleElementTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("single");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("single"));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapAddAllTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.addAll(Arrays.asList("a", "b", "c", "d", "e"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(5, set.size());
            assertTrue(set.containsAll(Arrays.asList("a", "b", "c", "d", "e")));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapRemoveAllTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
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
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapRetainAllTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
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
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapIteratorRemovalTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.addAll(Arrays.asList("one", "two", "three", "four"));

        Iterator<String> iterator = stateDataField.iterator();
        while (iterator.hasNext()) {
            String element = iterator.next();
            if (element.equals("two")) {
                iterator.remove();
            }
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(3, set.size());
            assertFalse(set.contains("two"));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapContainsAllTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.addAll(Arrays.asList("a", "b", "c", "d"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.containsAll(Arrays.asList("a", "b")));
            assertFalse(set.containsAll(Arrays.asList("a", "e")));
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapToArrayTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.addAll(Arrays.asList("one", "two", "three"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Object[] array = set.toArray();
            assertEquals(3, array.length);
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapWithIntegersTest()
    {
        LinkedHashMap<Integer, Boolean> map = new LinkedHashMap<>();
        Set<Integer> intSet = Collections.newSetFromMap(map);
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
            assertEquals(intSet, set);
        }
    }

    @Test
    void setFromMapMaxSizeTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);

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
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapRemoveNonExistentTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("one");
        assertFalse(stateDataField.remove("two"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertEquals(stateDataField, set);
        }
    }

    @Test
    void setFromMapEmptyAfterAddAndRemoveTest()
    {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        stateDataField = Collections.newSetFromMap(map);
        stateDataField.add("temporary");
        stateDataField.remove("temporary");
        assertTrue(stateDataField.isEmpty());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
            assertEquals(stateDataField, set);
        }
    }
}
