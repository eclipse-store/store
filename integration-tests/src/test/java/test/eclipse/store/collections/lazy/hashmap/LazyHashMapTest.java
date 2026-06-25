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

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyHashMapTest {

    @Test
    void addNullObject() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        map.put("Key 1", null);
        final String value = map.get("Key 1");

        assertNull(value, "expected null value");
        assertTrue(map.containsKey("Key 1"), "key not found in map");
    }

    @Test
    void addSingleValueToEmptyMap() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        map.put("Key 1", "Value 1");
        final String value = map.get("Key 1");

        assertEquals("Value 1", value);
    }


    @Test
    void addManyIntKey() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put(i, "Value " + i);
        }

        for (int i = 0; i < 100; i++) {
            final String value = map.get(i);
            assertEquals("Value " + i, value, "Value " + i + " does not match!");
        }
    }

    @Test
    void addManyStringKey() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        for (int i = 0; i < 100; i++) {
            final String value = map.get("Key " + i);
            assertEquals("Value " + i, value, "Value " + i + " does not match!");
        }

    }

    @Test
    void removeFromFront() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        for (int i = 0; i < 100; i++) {
            final String value = map.remove("Key " + i);
            assertEquals("Value " + i, value, "Value " + i + " does not match!");
        }
        assertTrue(map.isEmpty());
    }

    @Test
    void removeFromBack() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        for (int i = 99; i >= 0; i--) {
            final String value = map.remove("Key " + i);
            assertEquals("Value " + i, value, "Value " + i + " does not match!");
        }
        assertTrue(map.isEmpty());
    }

    @Test
    void removeRandom() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        final int numElements = 100;
        final List<Integer> toBeRemoved = new ArrayList<>(numElements);

        for (int i = 0; i < numElements; i++) {
            map.put("Key " + i, "Value " + i);
            toBeRemoved.add(i);
        }

        final Random rnd = new Random(System.nanoTime());

        while (!map.isEmpty()) {
            final int rint = toBeRemoved.remove(rnd.nextInt(toBeRemoved.size()));
            final String removed = map.remove("Key " + rint);

            assertEquals("Value " + rint, removed, "removed Value " + rint + " does not match!");
        }
        assertTrue(map.isEmpty());

        final AtomicInteger counter = new AtomicInteger();
        map.segments()
                .forEach(s -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void removeKeyValue() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        assertFalse(map.remove("Key 11", "Value 12"), "removing non existing value should return false");
        assertTrue(map.remove("Key 11", "Value 11"), "removing  existing value should return true");
        assertEquals(99, map.size());
        assertFalse(map.containsValue("Value 11"));
    }


    @Test
    void replace() {
        LazyHashMap<String, String> map = new LazyHashMap<>();
        final String putResult = map.put("Key", "Value");
        assertEquals(null, putResult);

        final String replaceResult = map.replace("Key", "Replaced");
        assertEquals("Value", replaceResult);
        assertEquals("Replaced", map.get("Key"));

        final String notReplacedResult = map.replace("no key", "not in map");
        assertNull(notReplacedResult);
        assertNull(map.get("no key"));
    }

    @Test
    void replaceIfEqual() {
        LazyHashMap<String, String> map = new LazyHashMap<>();
        final String putResult = map.put("Key", "Value");
        assertEquals(null, putResult);

        final boolean replaceResult = map.replace("Key", "Value", "Replaced");
        assertTrue(replaceResult);
        assertEquals("Replaced", map.get("Key"));

        final boolean replaceResult_noValue = map.replace("Key", "Value", "Replaced again");
        assertFalse(replaceResult_noValue);
        assertEquals("Replaced", map.get("Key"));

        final boolean replaceResult_noKey = map.replace("no key", "Replaced", "not in map");
        assertFalse(replaceResult_noKey);
        assertNull(map.get("no key"));
    }

    @Test
    void forEach() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        map.forEach((k, v) -> {
            assertNotNull(k, "key should not be null!");
            assertNotNull(v, "value should not be null");
        });
    }

    @Test
    void entrySet() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        final Set<Entry<String, String>> entrySet = map.entrySet();
        assertNotNull(entrySet);
        assertEquals(100, entrySet.size());
    }

    @Test
    void entrySetRemove() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        final Set<Entry<String, String>> entrySet = map.entrySet();
        assertNotNull(entrySet);
        assertEquals(100, entrySet.size());

        final boolean removed = entrySet.remove(Map.entry("Key 11", "Value 11"));
        assertTrue(removed, "entrySet remove expected to return true");
    }


    @Test
    void iteratorKeySet() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        int total = 0;
        int count = 0;
        final Iterator<String> iter = map.keySet()
                .iterator();
        while (iter.hasNext()) {
            final String v = iter.next();
            //System.out.println(v);
            if (v.contains("0")) {
                count++;
            }
            total++;
        }

        assertEquals(10, count);
        assertEquals(100, total);

    }

    @Test
    void iteratorKeySetRemove() {

        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        final Iterator<String> iter = map.keySet()
                .iterator();
        while (iter.hasNext()) {
            final String v = iter.next();
            //System.out.println(v);
            if (v.contains("0")) {
                iter.remove();
            }
        }

        assertEquals(90, map.size());

        map.values()
                .forEach(v -> {
                    assertFalse(v.contains("0"), "Element should be removed!");
                });

    }

    @Test
    void iteratorKeySetRemoveAll() {

        LazyHashMap<String, String> map = new LazyHashMap<>();

        for (int i = 0; i < 100; i++) {
            map.put("Key " + i, "Value " + i);
        }

        final Iterator<String> iter = map.keySet()
                .iterator();
        while (iter.hasNext()) {
            final String v = iter.next();
            //System.out.println(v);
            iter.remove();
        }

        assertEquals(0, map.size());

        map.values()
                .forEach(v -> {
                    assertFalse(v.contains("0"), "Element should be removed!");
                });

        final AtomicInteger counter = new AtomicInteger();
        map.segments()
                .forEach(s -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void removeNull() {
        LazyHashMap<String, String> map = new LazyHashMap<>();

        map.put("Entry 0", null);
        assertEquals(1, map.size());

        map.remove("Entry 0");
        assertEquals(0, map.size());
    }

    @Test
    void replaceNull() {
        LazyHashMap<String, String> map = new LazyHashMap<>();
        map.put("Entry 0", null);
        assertNull(map.replace("Entry 0", "Hello World"));
        assertEquals("Hello World", map.get("Entry 0"));
    }

    @Test
    void replaceWithNull() {
        LazyHashMap<String, String> map = new LazyHashMap<>();
        map.put("Entry 0", "Hello World");
        assertEquals("Hello World", map.replace("Entry 0", null));
        assertEquals(null, map.get("Entry 0"));
    }

    @Test
    public void containsKeyNull() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        map.put(105, null);
        map.put(5, null);

        assertTrue(map.containsValue(null));
        assertTrue(map.containsKey(5));
        assertFalse(map.containsValue("not contained"));
        assertFalse(map.containsKey(11));
    }

    @Test
    public void getValueNull() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        map.put(105, null);
        map.put(5, null);
        map.put(null, null);

        map.get(null);
    }

    @Test
    void copyTest() throws CloneNotSupportedException {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put(i, "Value " + i);
        }
        LazyHashMap<Integer, String> clone = new LazyHashMap<>(map);

        assertIterableEquals(map.entrySet(), clone.entrySet());
        assertEquals(map.size(), clone.size(), "map size");
        assertEquals(map.getMaxSegmentSize(), clone.getMaxSegmentSize(), "maxSegmentSize");

        //test for non cloned segments
        final List<Object> originalSegments = new ArrayList<>();
        map.segments()
                .forEach(s -> originalSegments.add(s));
        final List<Object> clonedSegments = new ArrayList<>();
        clone.segments()
                .forEach(s -> clonedSegments.add(s));

        originalSegments.forEach(o -> assertFalse(clonedSegments.contains(o), "same segment in original and clone"));

        final List<Object> originalEntries = Arrays.asList(map.entrySet()
                .toArray());
        final List<Object> clonedEntries = Arrays.asList(clone.entrySet()
                .toArray());
        assertEquals(100, clonedEntries.size());
        assertIterableEquals(originalEntries, clonedEntries);

        for (int i = 0; i < originalEntries.size(); i++) {
            final Object originalEntry = originalEntries.get(i);
            final Object clonedEntry = clonedEntries.get(i);
            assertEquals(originalEntry, clonedEntry, "originalEntry not equals clonedEntry");
            assertFalse(originalEntry == clonedEntry, "originalEntry == clonedEntry");
        }

    }

    @Test
    void clearTest() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put(i, "Value " + i);
        }
        assertEquals(100, map.size());

        map.clear();
        assertEquals(0, map.size());

        final AtomicInteger counter = new AtomicInteger();
        map.segments()
                .forEach(s -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void addAfterClear() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put(i, "Value " + i);
        }
        assertEquals(100, map.size());

        map.clear();
        assertEquals(0, map.size());

        map.put(1, "Value 1");
        assertEquals(1, map.size());

        assertEquals("Value 1", map.get(1));
    }

    @Test
    void getFromEmptyMap() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertNull(map.get(1));
    }

    @Test
    void containsKeyEmptyMap() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertFalse(map.containsKey(1));
    }

    @Test
    void removeFromEmptyMap() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertNull(map.remove(1));
    }

    @Test
    void replaceFromEmptyMap() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertNull(map.replace(1, "non"));
    }

    @Test
    void replaceIfEqualsFromEmptyMap() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertFalse(map.replace(1, "non", "new"));
    }

    @Test
    void ReplaceAll() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        map.put(1, "Entry 1");
        assertThrows(UnsupportedOperationException.class, () ->
                map.replaceAll((k, v) -> {
                    return v.toUpperCase();
                }));
    }

}
