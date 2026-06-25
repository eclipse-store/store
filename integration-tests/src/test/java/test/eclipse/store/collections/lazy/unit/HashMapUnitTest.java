package test.eclipse.store.collections.lazy.unit;

/*-
 * #%L
 * MicroStream Base
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.collections.lazy.LazyCollection;
import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazySet;
import org.eclipse.serializer.reference.Lazy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashMapUnitTest {

    @TempDir
    Path location;

    @Test
    public void sizeTest() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);

        assertEquals(100, map.size());

    }

    @Test
    public void isEmpty() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();

        assertTrue(map.isEmpty());

    }

    @Test
    public void containsKey() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(null, null);

        assertTrue(map.containsValue(null));
        assertTrue(map.containsKey(5));
    }



    @Test
    public void containsValueNull() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(null, null);

        assertTrue(map.containsKey(null));

        assertTrue(map.containsValue(null));
        assertTrue(map.containsKey(5));
    }


    @Test
    public void containsValue() {
        final String value = "Hi, i am a great value test sentence";
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();

        map.put(1, value);
        assertTrue(map.containsValue(value));

    }

    @Test
    public void getTest() {
        final String value = "Hi, i am a great value test sentence";
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();

        map.put(1, value);
        assertEquals(value, map.get(1));

    }

    @Test
    public void removeTest() {
        final String value = "Hi, i am a great value test sentence";
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();

        map.put(1, value);
        assertEquals(value, map.get(1));

    }

    @Test
    void remoteNullValueTest() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, null);
        map.put(102, null);
        assertEquals(102, map.size());
        map.remove(102);
        assertEquals(101, map.size());
    }


    @Test
    void remoteNullValueHashMapTest() {
        final HashMap<Integer, String> map = Util.generateHashMap(100);
        map.put(101, null);
        map.put(102, null);
        assertEquals(102, map.size());
        map.remove(102);
        assertEquals(101, map.size());
    }

    @Test
    public void putAll(@TempDir final Path secondLocation) {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);


        final LazyHashMap<Integer, String> secondMap = Util.generateMap(100, 100);

        map.putAll(secondMap);

    }

    @Test
    public void putALLtoOneStorage() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);


        final LazyHashMap<Integer, String> secondMap = Util.generateMap(100, 100);

        map.putAll(secondMap);


    }

    /**
     * Just test to prove behavior with  no-lazy collections.
     *
     * @param secondLocation Junit feature to provide private folder in temp directory
     */
    @Test
    public void lazyTwoStorageTest(@TempDir final Path secondLocation) {

        final ArrayList<Lazy<String>> lazyList = Stream.generate(() -> {
                    final String type1 = new String("ahoj");
                    return Lazy.Reference(type1);
                })
                .limit(1000)
                .collect(Collectors.toCollection(ArrayList::new));


    }

    @Test
    public void clear() {
        final String value = "Hi, i am a great value test sentence";
        final LazyHashMap<Integer, String> map = Util.generateMap(100);


        map.clear();

        assertTrue(map.isEmpty());


    }


    /**
     * After fix diese Issue, add some other tests: terator.remove, Set.remove, removeAll, retainAll, and clear operations
     */
    @Test
    public void keySetRemove() {

        final LazyHashMap<Integer, String> map = Util.generateMap(100);

        map.put(101, "some text");
        map.keySet()
                .remove(101);

    }

    @Test
    public void keySet() {

        final LazyHashMap<Integer, String> map = Util.generateMap(100);


        final Set<Integer> keySet = map.keySet();
        assertEquals(100, keySet.size());
        map.put(101, "some text");
        assertEquals(101, keySet.size());


        map.put(102, "another text");
        final Set<Integer> map1KeySet = map.keySet();
        assertEquals(102, keySet.size());
        assertEquals(102, map1KeySet.size());


    }

    @Test
    public void values_clear() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final LazyCollection<String> values = map.values();
        values.clear();
        assertTrue(map.isEmpty());

    }

    @Test
    public void values_removeAll() {
        LazyHashMap<Integer, String> map = Util.generateMap(100);
        final LazyCollection<String> values = map.values();
        values.removeAll(map.values());
        assertTrue(map.isEmpty());

        map = Util.generateMap(100);

    }

    @Test
    public void entrySet() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final LazySet<Map.Entry<Integer, String>> entries = map.entrySet();
        assertEquals(100, entries.size());

    }

    @Test
    void entryTest() {
        final String value = "Ahoj";

        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final LazySet<Map.Entry<Integer, String>> entries = map.entrySet();
        for (final Map.Entry<Integer, String> entry : entries) {
            assertThrows(UnsupportedOperationException.class, () -> entry.setValue(value));
        }


        final LazySet<Map.Entry<Integer, String>> entries1 = map.entrySet();
        for (final Map.Entry<Integer, String> integerStringEntry : entries1) {
            assertTrue(integerStringEntry.getValue()
                    .length() > 0);
            assertTrue(integerStringEntry.getKey() > -1);
        }
    }


    @Test
    void replaceAll() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);

        assertThrows(UnsupportedOperationException.class, () -> map.replaceAll((key, oldValue) -> {
            return oldValue + oldValue;
        }));
    }

    @Test
    void putIfAbsent() {
        final String s = "javax.net.ssl.keyStore";

        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final String value = map.get(50);
        map.put(50, null);
        map.putIfAbsent(50, s);

        assertEquals(s, map.get(50));

    }

    @Test
    void remove_for_specific_key_and_value() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final String value = map.get(60);
        assertFalse(map.remove(60, "javax.net.ssl.keyStore"));
        assertTrue(map.remove(60, value));
        assertEquals(99, map.size());
    }


    @Test
    void computeIfAbsent() {
        final String value = "ahoj";
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, null);

        map.computeIfAbsent(101, v -> value);

        assertEquals(value, map.get(101));
    }

    @Test
    void computeIfPresent() {
        final String value = "ahoj";
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, value);
        map.computeIfPresent(101, (k, v) -> v + value);
        assertEquals(value + value, map.get(101));
    }

    @Test
    void merge() {
        final String value = "ahoj";
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(101, value);
        map.compute(101, (k, v) -> v + value);
        assertEquals(value + value, map.get(101));

        map.merge(101, value, (k, v) -> v + v);
        assertEquals(value + value, map.get(101));

    }

    @Test
    void ofTestEmpty() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final Map<Integer, String> immutableMap = Map.of();
        map.putAll(immutableMap);
        assertEquals(100, map.size());
    }

    @Test
    void ofTestWithValue() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final Map<Integer, String> immutableMap = Map.of(101, "PP", 102, "QQ", 103, "RR");
        map.putAll(immutableMap);
        assertEquals(103, map.size());
    }

    @Test
    void ofEntries() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final Map<Integer, String> immutableMap = Map.ofEntries(Map.entry(101, "ahoj"),
                Map.entry(102, "ahoj2"), Map.entry(103, "ahoj3"));
        map.putAll(immutableMap);
        assertEquals(103, map.size());
    }

    @Test
    void entry() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final Map.Entry<Integer, String> ahoj = Map.entry(101, "ahoj");
        assertThrows(UnsupportedOperationException.class, () -> map.entrySet()
                .add(ahoj));

    }

    @Test
    void copyOf() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final Map<Integer, String> integerStringMap = Map.copyOf(map);
        assertEquals(100, map.size());
    }

    @Test
    void replaceWithOldValue_notExists() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertFalse(map.replace(5, "oldValue", "newValue"));
    }

    @Test
    void replaceWithOldValue() {
        final LazyHashMap<Integer, String> map = Util.generateMap(10);
        final String oldValue = map.get(5);
        assertTrue(map.replace(5, oldValue, "newValue"));
    }

    @Test
    void removeSegmentIfEmpty() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        for (int i = 0; i < map.size(); i++) {
            map.remove(i);
        }
    }

    @Test
    void lazyMapIterator() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.entrySet()
                .removeIf(e -> true);
    }

    /* Segment */

    @Test
    void isSegmentLoaded() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.segments()
                .forEach(segment -> {
                    assertTrue(segment.isLoaded());
                });
    }

    @Test
    void isSegmentModified() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.segments()
                .forEach(segment -> {
                    assertTrue(segment.isModified());
                });
    }

    @Test
    void unloadSegment() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.segments()
                .forEach(LazyHashMap.Segment::unloadSegment);
        map.segments()
                .forEach(segment -> {
                    assertTrue(segment.isLoaded());
                });
    }

    @Test
    void constructorWithMaxSegmentSize() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>(100);
        assertEquals(100, map.getMaxSegmentSize());
        assertNotNull(map);
    }

    @Test
    void constructorWithMaxSegmentSize_0() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>(0);
        assertEquals(0, map.getMaxSegmentSize());

        map = Util.fillHashMap(map, 100, 0);

        assertNotNull(map);
    }

    @Test
    void constructorFromExistingLazyHashMap() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>(100);
        map = Util.fillHashMap(map, 200, 0);
        final LazyHashMap<Integer, String> newMap = new LazyHashMap<>(map);
        assertEquals(map.size(), newMap.size());
        assertTrue(map.getSegmentCount() > 1);
    }

    @Test
    void segmentsTest() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        final Iterable<? extends LazyHashMap<Integer, String>.Segment<?>> segments = map.segments();
        assertNotNull(segments);
    }

    @Test
    void maxSegmentsSize() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>(100);
        assertEquals(100, map.getMaxSegmentSize());
    }


    @Test
    void copyTest() throws CloneNotSupportedException {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();
        map.put(1, "ahoj");
        map.put(2, "second string");

        final LazyHashMap<Integer, String> clone = new LazyHashMap<>(map);

        assertIterableEquals(map.entrySet(), clone.entrySet());
    }


    @Test
    void containsKeyTest() {
        final LazyHashMap<Integer, String> map = Util.generateMap(100);
        map.put(null, null);
        assertTrue(map.containsKey(null));
    }

    @Test
    void getWhatNotExists() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertNull(map.get(5));
    }

    @Test
    void containsValue_notExists() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertFalse(map.containsValue("something"));
    }

    @Test
    void remove_notExists() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertNull(map.remove(5));
    }

    @Test
    void replaceTest_nonExists() {
        final LazyHashMap<Integer, String> map = new LazyHashMap<>();
        assertNull(map.replace(5, "ahoj"));
    }

    @Test
    void replace() {
        final LazyHashMap<Integer, String> map = Util.generateMap(20);
        final String origValue = map.get(5);
        assertEquals(origValue, map.replace(5, "someText"));
    }

    @Test
    void valuesSplitIterator() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.values().spliterator());

        assertTrue(spliterators.size() > 1);
    }

    @Test
    void keysSplitIterator() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.keySet()
                .spliterator());
        assertTrue(spliterators.size() > 1);
        for (final Spliterator<?> spliterator : spliterators) {
            spliterator.tryAdvance(Object::toString);
        }
    }

    @Test
    void keysSplitIteratorReverse() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.keySet()
                .spliterator());
        assertTrue(spliterators.size() > 1);
        for (int i = spliterators.size(); i-- > 0; ) {
            final Spliterator<?> spliterator = spliterators.get(i);
            spliterator.tryAdvance(Object::toString);
        }
    }

    @Test
    void keysSplitIteratorNullAction() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.keySet()
                .spliterator());
        assertTrue(spliterators.size() > 1);
        for (final Spliterator<?> spliterator : spliterators) {
            assertThrows(NullPointerException.class, () -> spliterator.tryAdvance(null));
        }
    }

    @Test
    void keysSplitIteratorConcurrentAction() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.keySet()
                .spliterator());
        assertTrue(spliterators.size() > 1);
        map.put(101, "last item");
        for (final Spliterator<?> spliterator : spliterators) {
            assertThrows(ConcurrentModificationException.class, () -> spliterator.tryAdvance(Object::toString));
        }
    }

    @Test
    void splitIteratorTest() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final Spliterator<String> spliterator = map.values()
                .spliterator();
        final Spliterator<String> split2 = spliterator.trySplit();

        assertTrue(split2.estimateSize() > 0);
        ;
    }

    @Test
    void entrySplitIterator() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.entrySet()
                .spliterator());
        assertTrue(spliterators.size() > 1);
        for (final Spliterator<?> spliterator : spliterators) {
            spliterator.tryAdvance(Object::toString);
        }
    }

    @Test
    void valueSplitIterator() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.values()
                .spliterator());
        assertTrue(spliterators.size() > 1);
        for (final Spliterator<?> spliterator : spliterators) {
            spliterator.tryAdvance(Object::toString);
        }
    }

    @Test
    void segmentsSplitIterator() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final List<Spliterator<?>> spliterators = this.splitAll(map.segments()
                .spliterator());
        assertTrue(spliterators.size() > 1);
        for (final Spliterator<?> spliterator : spliterators) {
            spliterator.tryAdvance(Object::toString);
        }
    }

    private List<Spliterator<?>> splitAll(final Spliterator<?> spliterator) {
        final List<Spliterator<?>> spliterators = new ArrayList<>();
        final Spliterator<?> split = spliterator.trySplit();

        if(split != null) {
            spliterators.addAll(this.splitAll(spliterator));
            spliterators.addAll(this.splitAll(split));
        } else {
            spliterators.add(spliterator);
        }

        return spliterators;
    }

    @Test
    void valueIteratorTest() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        for (final String value : map.values()) {
            assertNotNull(value);
        }
    }

    @Test
    void keyIteratorTest() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        for (final Integer integer : map.keySet()) {
            assertNotNull(integer);
        }
    }

    @Test
    void entryIteratorTest() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        for (final Map.Entry<Integer, String> integerStringEntry : map.entrySet()) {
            assertNotNull(integerStringEntry);
        }
    }

    @Test
    void toStringEmptyMap() {
        final LazyHashMap<Integer,String> map = new LazyHashMap<>();
        assertEquals("{}", map.toString());
    }

    @Test
    void entryHashCode() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final LazySet<Map.Entry<Integer, String>> entries = map.entrySet();
        for (final Map.Entry<Integer, String> entry : entries) {
            assertNotNull(entry.hashCode());
            break;
        }
    }

    @Test
    void entrySetClear() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        map.entrySet().clear();
        assertEquals(0, map.size());
    }

    @Test
    void entrySet_iterateLazyReference() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final LazySet<Map.Entry<Integer, String>> entries = map.entrySet();
        entries.iterateLazyReferences( (l) -> {
            assertTrue(l.isLoaded());
        });
    }

    @Test
    void entrySet_consolidate() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        final LazySet<Map.Entry<Integer, String>> entries = map.entrySet();
        entries.consolidate();
        assertEquals(100, map.size());
    }

    @Test
    void value_iterateLazyReference() {
        final LazyHashMap<Integer,String> map = Util.generateMap(100);
        map.values().iterateLazyReferences( (v) -> {
            assertTrue(v.isLoaded());
        });
    }
}

