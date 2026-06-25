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

import java.util.HashMap;
import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class IdentityHashMapData implements BinaryHandlerTestData {
    private IdentityHashMap<Integer, Integer> intMap = createEmptyIntMap();
    private IdentityHashMap<Integer, IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>>> threeMap = createEmptyThreeMap();
    private IdentityHashMap<Integer, PrimitiveTypes> primitiveTypeIdentityHashMap = new IdentityHashMap<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // IdentityHashMap uses == for key/value equality. Cross-JDK identity is NOT preserved across save/load,
    // so verifications below use HashMap wrappers (value semantics) where the content is outside the
    // Integer/String JVM cache. Identity-specific scenarios (self-ref, shared-ref) are deliberately omitted.
    private IdentityHashMap<String, String> nullKeyMap;
    private IdentityHashMap<String, String> nullValueMap;
    private IdentityHashMap<String, String> emptyMap;
    private IdentityHashMap<Integer, Integer> oversizedCapacityMap;
    private IdentityHashMap<Integer, Integer> largeMap;

    @Override
    public IdentityHashMapData fillSampleData() {
        intMap = createIntMap();
        threeMap = createThreeMap();
        primitiveTypeIdentityHashMap = new IdentityHashMap<>();
        primitiveTypeIdentityHashMap.put(105, PrimitiveTypes.fillSample());

        // ===== proposed edge-cases =====
        nullKeyMap = createNullKeyMap();
        nullValueMap = createNullValueMap();
        emptyMap = new IdentityHashMap<>();
        oversizedCapacityMap = createOversizedCapacityMap();
        largeMap = createLargeMap();

        return this;
    }


    IdentityHashMap<Integer, Integer> createEmptyIntMap() {
        IdentityHashMap<Integer, Integer> intMap = new IdentityHashMap<>();
        intMap.put(100, 0);
        return intMap;
    }

    IdentityHashMap<Integer, Integer> createIntMap() {
        IdentityHashMap<Integer, Integer> intMap = new IdentityHashMap<>();
        intMap.put(100, 6);
        return intMap;
    }

    IdentityHashMap<Integer, IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>>> createEmptyThreeMap() {
        IdentityHashMap<Integer, IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>>> map = new IdentityHashMap<>();

        IdentityHashMap<Integer, PrimitiveTypes> primitive = new IdentityHashMap<>();
        primitive.put(100, new PrimitiveTypes());

        IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>> IdentityHashMap = new IdentityHashMap<>();
        IdentityHashMap.put(100, primitive);
        map.put(100, IdentityHashMap);

        return map;
    }

    IdentityHashMap<Integer, IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>>> createThreeMap() {
        IdentityHashMap<Integer, IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>>> map = new IdentityHashMap<>();

        IdentityHashMap<Integer, PrimitiveTypes> primitive = new IdentityHashMap<>();
        primitive.put(100, PrimitiveTypes.fillSample());

        IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>> IdentityHashMap = new IdentityHashMap<>();
        IdentityHashMap.put(100, primitive);
        map.put(100, IdentityHashMap);

        return map;
    }

    IdentityHashMap<Integer, Integer> getIntMap() {
        return intMap;
    }

    IdentityHashMap<Integer, IdentityHashMap<Integer, IdentityHashMap<Integer, PrimitiveTypes>>> getThreeMap() {
        return threeMap;
    }

    IdentityHashMap<Integer, PrimitiveTypes> getPrimitiveTypeIdentityHashMap() {
        return primitiveTypeIdentityHashMap;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        IdentityHashMapData copy = (IdentityHashMapData) o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntMap().entrySet(), copy.getIntMap().entrySet()), //
                () -> assertIterableEquals(this.getIntMap().values(), copy.getIntMap().values()), //
                () -> assertEquals(this.getThreeMap().get(100).get(100).get(100), copy.getThreeMap().get(100).get(100).get(100)),
                () -> assertIterableEquals(this.getPrimitiveTypeIdentityHashMap().values(), copy.getPrimitiveTypeIdentityHashMap().values()),

                // ===== proposed edge-case verifications =====
                () -> {
                    // IdentityHashMap permits null keys (internal NULL_KEY sentinel) — must survive round-trip
                    if (this.getNullKeyMap() != null) {
                        assertEquals(this.getNullKeyMap().size(), copy.getNullKeyMap().size(), "null-key map size");
                        assertTrue(copy.getNullKeyMap().containsKey(null), "null key present");
                        assertEquals("value-for-null-key", copy.getNullKeyMap().get(null));
                        assertEquals(new HashMap<>(this.getNullKeyMap()), new HashMap<>(copy.getNullKeyMap()), "null-key map value content");
                    } else {
                        assertNull(copy.getNullKeyMap());
                    }
                },
                () -> {
                    // null values: multiple distinct null-valued entries must survive (value round-trip)
                    if (this.getNullValueMap() != null) {
                        assertEquals(this.getNullValueMap().size(), copy.getNullValueMap().size(), "null-value map size");
                        assertEquals(new HashMap<>(this.getNullValueMap()), new HashMap<>(copy.getNullValueMap()), "null-value map value content");
                    } else {
                        assertNull(copy.getNullValueMap());
                    }
                },
                () -> {
                    // empty map remains empty
                    if (this.getEmptyMap() != null) {
                        assertNotNull(copy.getEmptyMap());
                        assertTrue(copy.getEmptyMap().isEmpty(), "empty map remains empty");
                    } else {
                        assertNull(copy.getEmptyMap());
                    }
                },
                () -> {
                    // IdentityHashMap(expectedMaxSize=1024) — ES must store entries, not the oversized table
                    if (this.getOversizedCapacityMap() != null) {
                        assertEquals(this.getOversizedCapacityMap().size(), copy.getOversizedCapacityMap().size(), "oversized-capacity size");
                        assertEquals(new HashMap<>(this.getOversizedCapacityMap()), new HashMap<>(copy.getOversizedCapacityMap()), "oversized-capacity content");
                    } else {
                        assertNull(copy.getOversizedCapacityMap());
                    }
                },
                () -> {
                    // large map (10k entries) — most Integer keys/values are outside the JVM cache,
                    // so compare via HashMap wrapper (value semantics) instead of IdentityHashMap.Entry.equals (== semantics)
                    if (this.getLargeMap() != null) {
                        assertEquals(this.getLargeMap().size(), copy.getLargeMap().size(), "large map size");
                        assertEquals(new HashMap<>(this.getLargeMap()), new HashMap<>(copy.getLargeMap()), "large map content");
                    } else {
                        assertNull(copy.getLargeMap());
                    }
                }
        );

    }

    // ===== proposed edge-cases — helpers & getters =====

    public IdentityHashMap<String, String> getNullKeyMap() {
        return nullKeyMap;
    }

    public IdentityHashMap<String, String> getNullValueMap() {
        return nullValueMap;
    }

    public IdentityHashMap<String, String> getEmptyMap() {
        return emptyMap;
    }

    public IdentityHashMap<Integer, Integer> getOversizedCapacityMap() {
        return oversizedCapacityMap;
    }

    public IdentityHashMap<Integer, Integer> getLargeMap() {
        return largeMap;
    }

    IdentityHashMap<String, String> createNullKeyMap() {
        IdentityHashMap<String, String> m = new IdentityHashMap<>();
        m.put(null, "value-for-null-key");
        m.put("regular", "regular-value");
        return m;
    }

    IdentityHashMap<String, String> createNullValueMap() {
        IdentityHashMap<String, String> m = new IdentityHashMap<>();
        m.put("k1", null);
        m.put("k2", "v2");
        m.put("k3", null);
        return m;
    }

    IdentityHashMap<Integer, Integer> createOversizedCapacityMap() {
        IdentityHashMap<Integer, Integer> m = new IdentityHashMap<>(1024);
        m.put(1, 1);
        m.put(2, 2);
        m.put(3, 3);
        return m;
    }

    IdentityHashMap<Integer, Integer> createLargeMap() {
        IdentityHashMap<Integer, Integer> m = new IdentityHashMap<>();
        for (int i = 0; i < 10_000; i++) {
            m.put(i, i * 2);
        }
        return m;
    }
}
