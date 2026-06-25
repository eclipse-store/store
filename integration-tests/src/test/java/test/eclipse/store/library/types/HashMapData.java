package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashMapData implements BinaryHandlerTestData {
    private HashMap<Integer, Integer> intMap = new HashMap<>();
    private HashMap<Integer, HashMap<Integer, HashMap<Integer, PrimitiveTypes>>> threeMap = new HashMap<>();
    private HashMap<Integer, PrimitiveTypes> primitiveTypeHashMap = new HashMap<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    private HashMap<String, String> nullKeyMap;
    private HashMap<String, String> nullValueMap;
    private HashMap<String, String> emptyMap;
    private HashMap<Integer, Integer> oversizedCapacityMap;
    private HashMap<Integer, Integer> largeMap;
    private HashMap<String, Integer> stringKeyEdgeCasesMap;

    @Override
    public HashMapData fillSampleData() {
        intMap = createIntMap();
        threeMap = createThreeMap();
        primitiveTypeHashMap = new HashMap<>();
        primitiveTypeHashMap.put(105, PrimitiveTypes.fillSample());

        // ===== proposed edge-cases =====
        nullKeyMap = createNullKeyMap();
        nullValueMap = createNullValueMap();
        emptyMap = new HashMap<>();
        oversizedCapacityMap = createOversizedCapacityMap();
        largeMap = createLargeMap();
        stringKeyEdgeCasesMap = createStringKeyEdgeCasesMap();

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        // existing fields
        intMap.put(200, 12);
        HashMap<Integer, HashMap<Integer, PrimitiveTypes>> nested = new HashMap<>();
        HashMap<Integer, PrimitiveTypes> innerNested = new HashMap<>();
        innerNested.put(200, PrimitiveTypes.fillSample());
        nested.put(200, innerNested);
        threeMap.put(200, nested);
        primitiveTypeHashMap.put(206, PrimitiveTypes.fillSample());

        // ===== proposed edge-cases — touch where safe (no shared-ref / self-ref / emptyMap mutation) =====
        nullKeyMap.put("another", "another-value");
        nullValueMap.put("k4", null); // another null value
        oversizedCapacityMap.put(4, 4);
        largeMap.put(10_000, 20_000);
        stringKeyEdgeCasesMap.put("plain", 5);

        return this;
    }

    HashMap<Integer, Integer> createIntMap() {
        HashMap<Integer, Integer> intMap = new HashMap<>();
        intMap.put(100, 6);
        return intMap;
    }

    HashMap<Integer, HashMap<Integer, HashMap<Integer, PrimitiveTypes>>> createThreeMap() {
        HashMap<Integer, HashMap<Integer, HashMap<Integer, PrimitiveTypes>>> map = new HashMap<>();

        HashMap<Integer, PrimitiveTypes> primitive = new HashMap<>();
        primitive.put(165, PrimitiveTypes.fillSample());

        HashMap<Integer, HashMap<Integer, PrimitiveTypes>> hashMap = new HashMap<>();
        hashMap.put(154, primitive);
        map.put(100, hashMap);

        return map;
    }

    HashMap<Integer, Integer> getIntMap() {
        return intMap;
    }

    HashMap<Integer, HashMap<Integer, HashMap<Integer, PrimitiveTypes>>> getThreeMap() {
        return threeMap;
    }

    HashMap<Integer, PrimitiveTypes> getPrimitiveTypeHashMap() {
        return primitiveTypeHashMap;
    }


    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        HashMapData copy = (HashMapData) o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntMap().entrySet(), copy.getIntMap().entrySet()), //
                () -> assertIterableEquals(this.getIntMap().values(), copy.getIntMap().values()), //
                () -> assertIterableEquals(this.getThreeMap().values(), copy.getThreeMap().values()),
                () -> assertIterableEquals(this.getPrimitiveTypeHashMap().values(), copy.getPrimitiveTypeHashMap().values()),

                // ===== proposed edge-case verifications =====
                () -> {
                    // HashMap allows exactly one null key — must survive round-trip
                    if (this.getNullKeyMap() != null) {
                        assertEquals(this.getNullKeyMap().size(), copy.getNullKeyMap().size(), "null-key map size");
                        assertTrue(copy.getNullKeyMap().containsKey(null), "null key present");
                        assertEquals("value-for-null-key", copy.getNullKeyMap().get(null));
                        assertEquals("regular-value", copy.getNullKeyMap().get("regular"));
                    } else {
                        assertNull(copy.getNullKeyMap());
                    }
                },
                () -> {
                    // null values: containsKey must still report true, get returns null
                    if (this.getNullValueMap() != null) {
                        assertEquals(this.getNullValueMap().size(), copy.getNullValueMap().size(), "null-value map size");
                        assertTrue(copy.getNullValueMap().containsKey("k1"), "containsKey k1 (null value)");
                        assertTrue(copy.getNullValueMap().containsKey("k3"), "containsKey k3 (null value)");
                        assertNull(copy.getNullValueMap().get("k1"));
                        assertNull(copy.getNullValueMap().get("k3"));
                        assertEquals("v2", copy.getNullValueMap().get("k2"));
                    } else {
                        assertNull(copy.getNullValueMap());
                    }
                },
                () -> {
                    // empty map remains empty after round-trip
                    if (this.getEmptyMap() != null) {
                        assertNotNull(copy.getEmptyMap());
                        assertTrue(copy.getEmptyMap().isEmpty(), "empty map remains empty");
                    } else {
                        assertNull(copy.getEmptyMap());
                    }
                },
                () -> {
                    // capacity-vs-size: ES must store entries, not bucket array
                    if (this.getOversizedCapacityMap() != null) {
                        assertEquals(this.getOversizedCapacityMap().size(), copy.getOversizedCapacityMap().size(), "oversized-capacity size");
                        assertEquals(this.getOversizedCapacityMap(), copy.getOversizedCapacityMap(), "oversized-capacity content");
                    } else {
                        assertNull(copy.getOversizedCapacityMap());
                    }
                },
                () -> {
                    // large map (10k+ entries)
                    if (this.getLargeMap() != null) {
                        assertEquals(this.getLargeMap().size(), copy.getLargeMap().size(), "large map size");
                        assertEquals(this.getLargeMap(), copy.getLargeMap(), "large map content");
                    } else {
                        assertNull(copy.getLargeMap());
                    }
                },
                () -> {
                    // string keys: empty, NUL inside, surrogate pair, long
                    if (this.getStringKeyEdgeCasesMap() != null) {
                        assertEquals(this.getStringKeyEdgeCasesMap(), copy.getStringKeyEdgeCasesMap(), "string-key edge cases (empty, NUL, surrogate pair, long)");
                    } else {
                        assertNull(copy.getStringKeyEdgeCasesMap());
                    }
                }
        );
    }

    // ===== proposed edge-cases — helpers & getters =====

    public HashMap<String, String> getNullKeyMap() {
        return nullKeyMap;
    }

    public HashMap<String, String> getNullValueMap() {
        return nullValueMap;
    }

    public HashMap<String, String> getEmptyMap() {
        return emptyMap;
    }

    public HashMap<Integer, Integer> getOversizedCapacityMap() {
        return oversizedCapacityMap;
    }

    public HashMap<Integer, Integer> getLargeMap() {
        return largeMap;
    }

    public HashMap<String, Integer> getStringKeyEdgeCasesMap() {
        return stringKeyEdgeCasesMap;
    }

    HashMap<String, String> createNullKeyMap() {
        HashMap<String, String> m = new HashMap<>();
        m.put(null, "value-for-null-key");
        m.put("regular", "regular-value");
        return m;
    }

    HashMap<String, String> createNullValueMap() {
        HashMap<String, String> m = new HashMap<>();
        m.put("k1", null);
        m.put("k2", "v2");
        m.put("k3", null);
        return m;
    }

    HashMap<Integer, Integer> createOversizedCapacityMap() {
        HashMap<Integer, Integer> m = new HashMap<>(1024);
        m.put(1, 1);
        m.put(2, 2);
        m.put(3, 3);
        return m;
    }

    HashMap<Integer, Integer> createLargeMap() {
        HashMap<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            m.put(i, i * 2);
        }
        return m;
    }

    HashMap<String, Integer> createStringKeyEdgeCasesMap() {
        HashMap<String, Integer> m = new HashMap<>();
        m.put("", 1);
        m.put("a" + ((char) 0) + "b", 2); // NUL inside key
        m.put(new String(Character.toChars(0x1F600)), 3); // 4-byte UTF-8 (U+1F600) via surrogate pair
        m.put(String.join("", Collections.nCopies(1000, "x")), 4); // long key
        return m;
    }
}
