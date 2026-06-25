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

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LinkedHashMapData implements BinaryHandlerTestData {
    private LinkedHashMap<Integer, Integer> intMap = new LinkedHashMap<>();
    private LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, PrimitiveTypes>>> threeMap = new LinkedHashMap<>();
    private LinkedHashMap<Integer, PrimitiveTypes> primitiveTypeLinkedHashMap = new LinkedHashMap<>();

    // ===== proposed edge-cases (review & cherry-pick) — focus on linked-order semantics =====
    private LinkedHashMap<String, Integer> insertionOrderMap = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> reorderAfterRemoveMap = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> accessOrderMap = new LinkedHashMap<>(16, 0.75f, true);
    private LinkedHashMap<String, String> emptyLinkedHashMap = new LinkedHashMap<>();
    private LinkedHashMap<Integer, Integer> largeLinkedHashMap = new LinkedHashMap<>();
    private LinkedHashMap<String, String> nullKeyLinkedHashMap = new LinkedHashMap<>();
    private LinkedHashMap<String, String> nullValueLinkedHashMap = new LinkedHashMap<>();

    @Override
    public LinkedHashMapData fillSampleData() {
        intMap = createIntMap();
        threeMap = createThreeMap();
        primitiveTypeLinkedHashMap = new LinkedHashMap<>();
        primitiveTypeLinkedHashMap.put(105, PrimitiveTypes.fillSample());

        // ===== proposed edge-cases =====

        // 1. plain insertion order — keys must iterate as [a, b, c, d, e]
        insertionOrderMap = new LinkedHashMap<>();
        insertionOrderMap.put("a", 1);
        insertionOrderMap.put("b", 2);
        insertionOrderMap.put("c", 3);
        insertionOrderMap.put("d", 4);
        insertionOrderMap.put("e", 5);

        // 2. remove + re-insert — re-inserted key moves to the tail; expected order [a, c, b]
        reorderAfterRemoveMap = new LinkedHashMap<>();
        reorderAfterRemoveMap.put("a", 1);
        reorderAfterRemoveMap.put("b", 2);
        reorderAfterRemoveMap.put("c", 3);
        reorderAfterRemoveMap.remove("b");
        reorderAfterRemoveMap.put("b", 22);

        // 3. access-order LinkedHashMap (accessOrder=true) — get() moves entry to tail; expected order [c, a, b]
        accessOrderMap = new LinkedHashMap<>(16, 0.75f, true);
        accessOrderMap.put("a", 1);
        accessOrderMap.put("b", 2);
        accessOrderMap.put("c", 3);
        accessOrderMap.get("a");
        accessOrderMap.get("b");

        // 4. empty — must remain empty after round-trip
        emptyLinkedHashMap = new LinkedHashMap<>();

        // 5. large (10k) — insertion order must survive end-to-end
        largeLinkedHashMap = new LinkedHashMap<>();
        for (int i = 0; i < 10_000; i++) {
            largeLinkedHashMap.put(i, i * 2);
        }

        // 6. null key — its linked-list position must be preserved (here: first)
        nullKeyLinkedHashMap = new LinkedHashMap<>();
        nullKeyLinkedHashMap.put(null, "value-for-null-key");
        nullKeyLinkedHashMap.put("regular", "regular-value");

        // 7. null values — containsKey vs get distinction + order
        nullValueLinkedHashMap = new LinkedHashMap<>();
        nullValueLinkedHashMap.put("k1", null);
        nullValueLinkedHashMap.put("k2", "v2");
        nullValueLinkedHashMap.put("k3", null);

        return this;
    }

    LinkedHashMap<Integer, Integer> createIntMap() {
        LinkedHashMap<Integer, Integer> intMap = new LinkedHashMap<>();
        intMap.put(100, 6);
        return intMap;
    }

    LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, PrimitiveTypes>>> createThreeMap() {
        LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, PrimitiveTypes>>> map = new LinkedHashMap<>();

        LinkedHashMap<Integer, PrimitiveTypes> primitive = new LinkedHashMap<>();
        primitive.put(165, PrimitiveTypes.fillSample());

        LinkedHashMap<Integer, LinkedHashMap<Integer, PrimitiveTypes>> LinkedHashMap = new LinkedHashMap<>();
        LinkedHashMap.put(154, primitive);
        map.put(100, LinkedHashMap);

        return map;
    }

    LinkedHashMap<Integer, Integer> getIntMap() {
        return intMap;
    }

    LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, PrimitiveTypes>>> getThreeMap() {
        return threeMap;
    }

    LinkedHashMap<Integer, PrimitiveTypes> getPrimitiveTypeLinkedHashMap() {
        return primitiveTypeLinkedHashMap;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        LinkedHashMapData copy = (LinkedHashMapData)o;

        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntMap().entrySet(), copy.getIntMap().entrySet()), //
                () -> assertIterableEquals(this.getIntMap().values(), copy.getIntMap().values()), //
                () -> assertIterableEquals(this.getThreeMap().values(), copy.getThreeMap().values()),
                () -> assertIterableEquals(this.getPrimitiveTypeLinkedHashMap().values(), copy.getPrimitiveTypeLinkedHashMap().values()),

                // ===== proposed edge-case verifications — linked-order semantics =====
                // Handler (BinaryHandlerLinkedHashMap + AbstractBinaryHandlerMap):
                //   store()    → iterates entrySet() in current linked-list order
                //   create()   → new LinkedHashMap<>(elementCount)  — 1-arg constructor only
                //   complete() → populateMap() puts entries via putIfAbsent in payload order
                // Consequence: structural iteration order IS preserved across save/load.
                // NOT preserved: accessOrder flag and custom loadFactor — loaded map is always
                // accessOrder=false / loadFactor=0.75 regardless of original constructor args.

                // 1. plain insertion order — keys/values/entries must iterate in same sequence
                () -> assertIterableEquals(this.insertionOrderMap.keySet(), copy.insertionOrderMap.keySet(), "insertion order — keys"),
                () -> assertIterableEquals(this.insertionOrderMap.values(), copy.insertionOrderMap.values(), "insertion order — values"),
                () -> assertIterableEquals(this.insertionOrderMap.entrySet(), copy.insertionOrderMap.entrySet(), "insertion order — entries"),

                // 2. remove + re-insert reordering preserved
                () -> assertIterableEquals(this.reorderAfterRemoveMap.keySet(), copy.reorderAfterRemoveMap.keySet(), "remove+put — keys (expected a,c,b)"),
                () -> assertIterableEquals(this.reorderAfterRemoveMap.values(), copy.reorderAfterRemoveMap.values(), "remove+put — values"),

                // 3. access-order: structural state at save time must round-trip — loaded sequence (c,a,b) == source sequence.
                //    The accessOrder=true *flag* itself is NOT persisted (see handler comment above) — that is a behavioral
                //    property outside the scope of value round-trip and is not asserted here.
                () -> assertIterableEquals(this.accessOrderMap.keySet(), copy.accessOrderMap.keySet(), "access-order — keys (expected c,a,b)"),
                () -> assertIterableEquals(this.accessOrderMap.values(), copy.accessOrderMap.values(), "access-order — values"),

                // 4. empty
                () -> {
                    assertNotNull(copy.emptyLinkedHashMap);
                    assertTrue(copy.emptyLinkedHashMap.isEmpty(), "empty map remains empty");
                },

                // 5. large (10k) — order preserved across entire extent
                () -> assertEquals(this.largeLinkedHashMap.size(), copy.largeLinkedHashMap.size(), "large map size"),
                () -> assertIterableEquals(this.largeLinkedHashMap.entrySet(), copy.largeLinkedHashMap.entrySet(), "large map — entries in order"),

                // 6. null key + its linked-list position — entrySet equality handles null key correctly
                () -> {
                    assertEquals(this.nullKeyLinkedHashMap.size(), copy.nullKeyLinkedHashMap.size(), "null-key map size");
                    assertIterableEquals(this.nullKeyLinkedHashMap.entrySet(), copy.nullKeyLinkedHashMap.entrySet(), "null-key map — entries in order (null key allowed)");
                },

                // 7. null values — entrySet equality handles null value correctly (preserves containsKey discrimination)
                () -> {
                    assertEquals(this.nullValueLinkedHashMap.size(), copy.nullValueLinkedHashMap.size(), "null-value map size");
                    assertIterableEquals(this.nullValueLinkedHashMap.entrySet(), copy.nullValueLinkedHashMap.entrySet(), "null-value map — entries in order (null values allowed)");
                }
        );
    }
}
