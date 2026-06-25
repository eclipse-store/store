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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.serializer.collections.lazy.LazyCollection;
import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazySet;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.serializer.fixtures.types.PrimitiveTypes;

public class HashMapSmokeTest {

    @TempDir
    Path location;

    @Test
    public void sizeTest() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);

        assertEquals(100, map.size());

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertEquals(100, map.size());
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            assertEquals(100, map1.size());
        }
    }

    @Test
    public void isEmpty() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        assertTrue(map.isEmpty());

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertTrue(map.isEmpty());
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            assertTrue(map1.isEmpty());
        }
    }

    @Test
    public void containsKey() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(null, null);

        assertTrue(map.containsValue(null));
        assertTrue(map.containsKey(5));

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertTrue(map.containsKey(5));
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            assertTrue(map1.containsKey(5));
        }
    }

    @Test
    public void containsValueNull() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        map.put(null, null);

        assertTrue(map.containsKey(null));

        assertTrue(map.containsValue(null));
        assertFalse(map.containsKey(5));
    }


    @Test
    public void containsValue() {
        String value = "Hi, i am a great value test sentence";
        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        map.put(1, value);
        assertTrue(map.containsValue(value));

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertTrue(map.containsValue(value));
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            assertTrue(map1.containsValue(value));
        }
    }

    @Test
    public void getTest() {
        String value = "Hi, i am a great value test sentence";
        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        map.put(1, value);
        assertEquals(value, map.get(1));

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertEquals(value, map.get(1));
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            assertEquals(value, map1.get(1));
        }
    }

    @Test
    public void removeTest() {
        String value = "Hi, i am a great value test sentence";
        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        map.put(1, value);
        assertEquals(value, map.get(1));

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertEquals(value, map.get(1));
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            assertEquals(value, map1.get(1));
            map1.remove(1);
            assertTrue(map1.isEmpty());
        }
    }

    @Test
    void remoteNullValueTest() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, null);
        map.put(102, null);
        assertEquals(102, map.size());
        map.remove(102);
        assertEquals(101, map.size());
    }


    @Test
    void remoteNullValueHashMapTest() {
        HashMap<Integer, String> map = Util.generateHashMap(100);
        map.put(101, null);
        map.put(102, null);
        assertEquals(102, map.size());
        map.remove(102);
        assertEquals(101, map.size());
    }

    @Test
    public void putALLtoOneStorage() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);


        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            LazyHashMap<Integer, String> secondMap = Util.generateMap(100, 100);

            map.putAll(secondMap);
            storageManager.store(map);
        }

        map = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertEquals(200, map.size());
        }

    }

    /**
     * Just test to prove behavior with  no-lazy collections.
     *
     * @param secondLocation Junit feature to provide private folder in temp directory
     */
    @Test
    public void lazyTwoStorageTest(@TempDir Path secondLocation) {

        ArrayList<Lazy<PrimitiveTypes>> lazyList = Stream.generate(() -> {
                    PrimitiveTypes type1 = new PrimitiveTypes();
                    type1.fillSampleData();
                    return Lazy.Reference(type1);
                })
                .limit(1000)
                .collect(Collectors.toCollection(ArrayList::new));


        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(lazyList, location)) {

        }


        try (EmbeddedStorageManager storageManager1 = EmbeddedStorage.start(lazyList, secondLocation)) {

        }

    }

    @Test
    public void clear() {
        String value = "Hi, i am a great value test sentence";
        LazyHashMap<Integer, String> map = Util.generateMap(100);

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
        }


        map.clear();

        assertTrue(map.isEmpty());

        try (EmbeddedStorageManager storageManager = Util.startStorage(location)) {
            storageManager.setRoot(map);
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = Util.startStorage(location)) {
            LazyHashMap<Integer, String> root = (LazyHashMap<Integer, String>) storageManager.root();

            assertTrue(root.isEmpty());
        }

    }


    /**
     * After fix diese Issue, add some other tests: terator.remove, Set.remove, removeAll, retainAll, and clear operations
     */
    @Test
    public void keySetRemove() {

        LazyHashMap<Integer, String> map = Util.generateMap(100);

        map.put(101, "some text");

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            map1.keySet()
                    .remove(101);

        }

    }

    @Test
    public void keySet() {

        LazyHashMap<Integer, String> map = Util.generateMap(100);


        Set<Integer> keySet = map.keySet();
        assertEquals(100, keySet.size());
        map.put(101, "some text");
        assertEquals(101, keySet.size());

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {
            assertEquals(101, keySet.size());
            assertNotNull(map.get(101));
        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();

        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            map1.put(102, "another text");
            Set<Integer> map1KeySet = map1.keySet();
            assertEquals(101, keySet.size());
            assertEquals(102, map1KeySet.size());
        }

    }

    @Test
    public void values_clear() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        LazyCollection<String> values = map.values();
        values.clear();
        assertTrue(map.isEmpty());

        map = Util.generateMap(100);
        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {

        }
        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();
        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            LazyCollection<String> values1 = map1.values();
            values1.clear();
            assertTrue(map1.isEmpty());

        }

    }

    @Test
    public void values_removeAll() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        LazyCollection<String> values = map.values();
        values.removeAll(map.values());
        assertTrue(map.isEmpty());

        map = Util.generateMap(100);
        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {

        }

        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();
        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            LazyCollection<String> values1 = map1.values();
            values1.removeAll(map.values());
            assertTrue(map1.isEmpty());

        }
    }

    @Test
    public void entrySet() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        LazySet<Map.Entry<Integer, String>> entries = map.entrySet();
        assertEquals(100, entries.size());

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {

        }
        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();
        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            LazySet<Map.Entry<Integer, String>> entries1 = map1.entrySet();
            assertEquals(100, entries1.size());

        }
    }

    @Test
    void entryTest() {
        String value = "Ahoj";

        LazyHashMap<Integer, String> map = Util.generateMap(100);
        LazySet<Map.Entry<Integer, String>> entries = map.entrySet();
        for (Map.Entry<Integer, String> entry : entries) {
            assertThrows(UnsupportedOperationException.class, () -> entry.setValue(value));
        }

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {

        }
        LazyHashMap<Integer, String> map1 = new LazyHashMap<>();
        try (EmbeddedStorageManager storageManager = Util.startStorage(map1, location)) {
            LazySet<Map.Entry<Integer, String>> entries1 = map1.entrySet();
            for (Map.Entry<Integer, String> integerStringEntry : entries1) {
                assertTrue(integerStringEntry.getValue()
                        .length() > 0);
                assertTrue(integerStringEntry.getKey() >= 0);
            }
        }
    }

    @Test
    void replaceAll() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);

        assertThrows(UnsupportedOperationException.class, () -> map.replaceAll((key, oldValue) -> {
            return oldValue + oldValue;
        }));
    }

    @Test
    void putIfAbsent() {
        String s = "javax.net.ssl.keyStore";

        LazyHashMap<Integer, String> map = Util.generateMap(100);
        String value = map.get(50);
        map.put(50, null);
        map.putIfAbsent(50, s);

        assertEquals(s, map.get(50));

    }

    @Test
    void remove_for_specific_key_and_value() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        String value = map.get(60);
        assertFalse(map.remove(60, "javax.net.ssl.keyStore"));
        assertTrue(map.remove(60, value));
        assertEquals(99, map.size());
    }


    @Test
    void computeIfAbsent() {
        final String value = "ahoj";
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, null);

        map.computeIfAbsent(101, v -> value);

        assertEquals(value, map.get(101));
    }

    @Test
    void computeIfPresent() {
        final String value = "ahoj";
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, value);
        map.computeIfPresent(101, (k, v) -> v + value);
        assertEquals(value + value, map.get(101));
    }

    @Test
    void merge() {
        final String value = "ahoj";
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, value);
        map.compute(101, (k, v) -> v + value);
        assertEquals(value + value, map.get(101));

        map.merge(101, value, (k, v) -> v + v);
        assertEquals(value + value, map.get(101));

    }

    @Test
    void ofTestEmpty() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        Map<Integer, String> immutableMap = Map.of();
        map.putAll(immutableMap);
        assertEquals(100, map.size());
    }

    @Test
    void ofTestWithValue() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        Map<Integer, String> immutableMap = Map.of(101, "PP", 102, "QQ", 103, "RR");
        map.putAll(immutableMap);
        assertEquals(103, map.size());
    }

    @Test
    void ofEntries() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        Map<Integer, String> immutableMap = Map.ofEntries(Map.entry(101, "ahoj"),
                Map.entry(102, "ahoj2"), Map.entry(103, "ahoj3"));
        map.putAll(immutableMap);
        assertEquals(103, map.size());
    }

    @Test
    void entry() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        Map.Entry<Integer, String> ahoj = Map.entry(101, "ahoj");
        assertThrows(UnsupportedOperationException.class, () -> map.entrySet()
                .add(ahoj));

    }

    @Test
    void copyOf() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        Map<Integer, String> integerStringMap = Map.copyOf(map);
        assertEquals(100, map.size());
    }

    @Test
    void equals() {

        LazyHashMap<Integer, String> mapA = new LazyHashMap<>();
        mapA.put(1, "A");
        mapA.put(2, "B");
        mapA.put(2, "A");
        mapA.put(3, null);
        mapA.put(null, "C");
        mapA.put(null, null);


        LazyHashMap<Integer, String> mapB = new LazyHashMap<>();
        mapB.put(1, "A");
        mapB.put(2, "B");
        mapB.put(2, "A");
        mapB.put(3, null);
        mapB.put(null, "C");
        mapB.put(null, null);

        assertIterableEquals(mapA.entrySet(), mapB.entrySet());


    }
}

