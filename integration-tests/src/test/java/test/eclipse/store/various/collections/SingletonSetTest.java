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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SingletonSetTest
{
    @TempDir
    Path workDir;

    private Set<String> stateDataField;

    @Test
    void singletonSetBasicTest()
    {
        stateDataField = Collections.singleton("onlyOne");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("onlyOne"));
        }
    }

    @Test
    void singletonSetWithStringTest()
    {
        stateDataField = Collections.singleton("test");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("test"));
            assertFalse(set.contains("other"));
        }
    }

    @Test
    void singletonSetWithIntegerTest()
    {
        Set<Integer> intSet = Collections.singleton(42);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(intSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Integer> set = (Set<Integer>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(42));
            assertFalse(set.contains(0));
        }
    }

    @Test
    void singletonSetWithNullTest()
    {
        stateDataField = Collections.singleton(null);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(null));
        }
    }

    @Test
    void singletonSetContainsTest()
    {
        stateDataField = Collections.singleton("element");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.contains("element"));
            assertFalse(set.contains("nonexistent"));
        }
    }

    @Test
    void singletonSetIsEmptyTest()
    {
        stateDataField = Collections.singleton("item");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertFalse(set.isEmpty());
        }
    }

    @Test
    void singletonSetIteratorTest()
    {
        stateDataField = Collections.singleton("single");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            int count = 0;
            for (String element : set) {
                assertEquals("single", element);
                count++;
            }
            assertEquals(1, count);
        }
    }

    @Test
    void singletonSetToArrayTest()
    {
        stateDataField = Collections.singleton("value");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Object[] array = set.toArray();
            assertEquals(1, array.length);
            assertEquals("value", array[0]);
        }
    }

    @Test
    void singletonSetContainsAllTest()
    {
        stateDataField = Collections.singleton("element");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertTrue(set.containsAll(Collections.singletonList("element")));
            assertFalse(set.containsAll(Arrays.asList("element", "other")));
        }
    }

    @Test
    void singletonSetEqualsTest()
    {
        stateDataField = Collections.singleton("item");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Set<String> expected = Collections.singleton("item");
            assertEquals(expected, set);
        }
    }

    @Test
    void singletonSetHashCodeTest()
    {
        stateDataField = Collections.singleton("test");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertNotNull(set.hashCode());
        }
    }

    @Test
    void singletonSetToStringTest()
    {
        stateDataField = Collections.singleton("value");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertNotNull(set.toString());
            assertTrue(set.toString().contains("value"));
        }
    }

    @Test
    void singletonSetWithLongStringTest()
    {
        String longString = "This is a very long string that contains multiple words and characters";
        stateDataField = Collections.singleton(longString);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(longString));
        }
    }

    @Test
    void singletonSetWithNegativeIntegerTest()
    {
        Set<Integer> intSet = Collections.singleton(-999);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(intSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Integer> set = (Set<Integer>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(-999));
        }
    }

    @Test
    void singletonSetWithZeroTest()
    {
        Set<Integer> intSet = Collections.singleton(0);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(intSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Integer> set = (Set<Integer>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(0));
        }
    }

    @Test
    void singletonSetWithLongTest()
    {
        Set<Long> longSet = Collections.singleton(123456789L);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(longSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Long> set = (Set<Long>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(123456789L));
        }
    }

    @Test
    void singletonSetWithDoubleTest()
    {
        Set<Double> doubleSet = Collections.singleton(3.14159);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(doubleSet, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<Double> set = (Set<Double>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(3.14159));
        }
    }

    @Test
    void singletonSetWithEmptyStringTest()
    {
        stateDataField = Collections.singleton("");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains(""));
        }
    }

    @Test
    void singletonSetWithSpecialCharactersTest()
    {
        stateDataField = Collections.singleton("!@#$%^&*()_+-=[]{}|;:',.<>?/~`");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("!@#$%^&*()_+-=[]{}|;:',.<>?/~`"));
        }
    }

    @Test
    void singletonSetWithUnicodeTest()
    {
        stateDataField = Collections.singleton("Hello 世界 🌍");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            assertEquals(1, set.size());
            assertTrue(set.contains("Hello 世界 🌍"));
        }
    }

    @Test
    void singletonSetCompareToOtherSetTest()
    {
        stateDataField = Collections.singleton("item");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(stateDataField, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Set<String> set = (Set<String>) storageManager.root();
            Set<String> otherSet = new HashSet<>();
            otherSet.add("item");
            assertEquals(otherSet, set);
        }
    }
}

