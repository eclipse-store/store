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
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TreeMapData implements BinaryHandlerTestData {
    private TreeMap<Integer, Integer> intMap = new TreeMap<>();
    private TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, PrimitiveTypes>>> threeMap = new TreeMap<>();
    private TreeMap<Integer, PrimitiveTypes> primitiveTypeTreeMap = new TreeMap<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // TreeMap is a SortedMap — entrySet() iterates in comparator-defined key order. assertIterableEquals
    // on entrySet doubles as both a content check and a sort-order check; divergent order after
    // round-trip signals a dropped Comparator (reverse / caseInsensitive probes).
    // Null-key probe is intentionally omitted: TreeMap with natural ordering rejects null keys
    // (NPE on compareTo). A null-tolerant comparator would have to be synthetic (e.g. Comparator
    // .nullsFirst(naturalOrder())), which is not a JDK singleton and would muddy the failure mode.
    private TreeMap<Integer, Integer> emptyTreeMap;
    private TreeMap<Integer, Integer> reverseTreeMap;
    private TreeMap<String, Integer> caseInsensitiveTreeMap;
    private TreeMap<String, String> nullValueMap;
    private TreeMap<Integer, Integer> largeTreeMap;

    @Override
    public TreeMapData fillSampleData() {
        intMap = createIntMap();
        threeMap = createThreeMap();
        primitiveTypeTreeMap = new TreeMap<>();
        primitiveTypeTreeMap.put(105, PrimitiveTypes.fillSample());

        // ===== proposed edge-cases =====
        emptyTreeMap = new TreeMap<>();
        reverseTreeMap = createReverseTreeMap();
        caseInsensitiveTreeMap = createCaseInsensitiveTreeMap();
        nullValueMap = createNullValueMap();
        largeTreeMap = createLargeTreeMap();

        return this;
    }

    TreeMap<Integer, Integer> createIntMap() {
        TreeMap<Integer, Integer> intMap = new TreeMap<>();
        intMap.put(100, 6);
        return intMap;
    }

    TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, PrimitiveTypes>>> createThreeMap() {
        TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, PrimitiveTypes>>> map = new TreeMap<>();

        TreeMap<Integer, PrimitiveTypes> primitive = new TreeMap<>();
        primitive.put(165, PrimitiveTypes.fillSample());

        TreeMap<Integer, TreeMap<Integer, PrimitiveTypes>> TreeMap = new TreeMap<>();
        TreeMap.put(154, primitive);
        map.put(100, TreeMap);

        return map;
    }

    TreeMap<Integer, Integer> getIntMap() {
        return intMap;
    }

    TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, PrimitiveTypes>>> getThreeMap() {
        return threeMap;
    }

    TreeMap<Integer, PrimitiveTypes> getPrimitiveTypeTreeMap() {
        return primitiveTypeTreeMap;
    }

    // ===== proposed edge-cases — getters =====

    public TreeMap<Integer, Integer> getEmptyTreeMap() {
        return emptyTreeMap;
    }

    public TreeMap<Integer, Integer> getReverseTreeMap() {
        return reverseTreeMap;
    }

    public TreeMap<String, Integer> getCaseInsensitiveTreeMap() {
        return caseInsensitiveTreeMap;
    }

    public TreeMap<String, String> getNullValueMap() {
        return nullValueMap;
    }

    public TreeMap<Integer, Integer> getLargeTreeMap() {
        return largeTreeMap;
    }

    TreeMap<Integer, Integer> createReverseTreeMap() {
        // Collections.reverseOrder() — public singleton. If preserved, iteration is [5,4,3,2,1];
        // if dropped, the loaded TreeMap falls back to natural order [1,2,3,4,5] and the test fails.
        TreeMap<Integer, Integer> m = new TreeMap<>(Collections.reverseOrder());
        m.put(1, 10);
        m.put(5, 50);
        m.put(3, 30);
        m.put(2, 20);
        m.put(4, 40);
        return m;
    }

    TreeMap<String, Integer> createCaseInsensitiveTreeMap() {
        // String.CASE_INSENSITIVE_ORDER — public singleton. Natural String.compareTo would sort
        // ["APPLE","Cherry","banana"] (uppercase first); case-insensitive comparator sorts
        // ["APPLE","banana","Cherry"]. Divergence signals dropped comparator.
        TreeMap<String, Integer> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        m.put("banana", 1);
        m.put("APPLE", 2);
        m.put("Cherry", 3);
        return m;
    }

    TreeMap<String, String> createNullValueMap() {
        // TreeMap permits null values (only keys are restricted by natural ordering).
        TreeMap<String, String> m = new TreeMap<>();
        m.put("k1", null);
        m.put("k2", "v2");
        m.put("k3", null);
        return m;
    }

    TreeMap<Integer, Integer> createLargeTreeMap() {
        TreeMap<Integer, Integer> m = new TreeMap<>();
        // Insert in reverse to make sortedness round-trip meaningful
        for (int i = 999; i >= 0; i--) {
            m.put(i, i * 2);
        }
        return m;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        TreeMapData copy = (TreeMapData) o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntMap().entrySet(), copy.getIntMap().entrySet()), //
                () -> assertIterableEquals(this.getIntMap().values(), copy.getIntMap().values()), //
                () -> assertIterableEquals(this.getThreeMap().values(), copy.getThreeMap().values()),
                () -> assertIterableEquals(this.getPrimitiveTypeTreeMap().values(), copy.getPrimitiveTypeTreeMap().values()),

                // ===== proposed edge-case verifications =====
                // assertIterableEquals on entrySet doubles as content + sort-order check —
                // TreeMap iterates in comparator-defined key order, so divergence flags a dropped Comparator.
                () -> {
                    if (this.getEmptyTreeMap() != null) {
                        assertTrue(copy.getEmptyTreeMap().isEmpty(), "empty TreeMap remains empty");
                    } else {
                        assertNull(copy.getEmptyTreeMap());
                    }
                },
                () -> {
                    if (this.getReverseTreeMap() != null) {
                        // If comparator preserved → entries iterate as [5→50, 4→40, 3→30, 2→20, 1→10]
                        assertIterableEquals(this.getReverseTreeMap().entrySet(), copy.getReverseTreeMap().entrySet(), "reverse-order TreeMap — comparator preserved");
                        assertEquals(Integer.valueOf(5), copy.getReverseTreeMap().firstKey(), "firstKey() = 5 (reverse: largest key first)");
                        assertEquals(Integer.valueOf(1), copy.getReverseTreeMap().lastKey(), "lastKey() = 1 (reverse: smallest key last)");
                    } else {
                        assertNull(copy.getReverseTreeMap());
                    }
                },
                () -> {
                    if (this.getCaseInsensitiveTreeMap() != null) {
                        System.out.println(this.getCaseInsensitiveTreeMap());
                        System.out.println(copy.getCaseInsensitiveTreeMap());
                        assertIterableEquals(this.getCaseInsensitiveTreeMap().entrySet(), copy.getCaseInsensitiveTreeMap().entrySet(), "case-insensitive TreeMap — comparator preserved");
                    } else {
                        assertNull(copy.getCaseInsensitiveTreeMap());
                    }
                },
                () -> {
                    if (this.getNullValueMap() != null) {
                        assertEquals(this.getNullValueMap().size(), copy.getNullValueMap().size(), "null-value TreeMap size");
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
                    if (this.getLargeTreeMap() != null) {
                        assertEquals(this.getLargeTreeMap().size(), copy.getLargeTreeMap().size(), "large TreeMap size");
                        assertIterableEquals(this.getLargeTreeMap().entrySet(), copy.getLargeTreeMap().entrySet(), "large TreeMap sorted iteration end-to-end");
                    } else {
                        assertNull(copy.getLargeTreeMap());
                    }
                }
        );

    }
}
