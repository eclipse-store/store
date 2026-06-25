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
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentSkipListMapData implements BinaryHandlerTestData {
    ConcurrentSkipListMap<Integer, PrimitiveTypes> value = new ConcurrentSkipListMap<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // ConcurrentSkipListMap is a concurrent SortedMap — entrySet() iterates in comparator-defined
    // key order. assertIterableEquals on entrySet doubles as both content + sort-order check;
    // divergent order signals a dropped Comparator (reverse / caseInsensitive probes).
    // No null-value probe: unlike TreeMap, ConcurrentSkipListMap rejects null keys AND values.
    private ConcurrentSkipListMap<Integer, Integer> emptyMap;
    private ConcurrentSkipListMap<Integer, Integer> reverseMap;
    private ConcurrentSkipListMap<String, Integer> caseInsensitiveMap;
    private ConcurrentSkipListMap<Integer, Integer> largeMap;

    @Override
    public ConcurrentSkipListMapData fillSampleData() {
        value.put(1, PrimitiveTypes.fillSample());

        // ===== proposed edge-cases =====
        emptyMap = new ConcurrentSkipListMap<>();
        reverseMap = createReverseMap();
        caseInsensitiveMap = createCaseInsensitiveMap();
        largeMap = createLargeMap();

        return this;
    }

    ConcurrentSkipListMap<Integer, PrimitiveTypes> getValue() {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public ConcurrentSkipListMap<Integer, Integer> getEmptyMap() {
        return emptyMap;
    }

    public ConcurrentSkipListMap<Integer, Integer> getReverseMap() {
        return reverseMap;
    }

    public ConcurrentSkipListMap<String, Integer> getCaseInsensitiveMap() {
        return caseInsensitiveMap;
    }

    public ConcurrentSkipListMap<Integer, Integer> getLargeMap() {
        return largeMap;
    }

    ConcurrentSkipListMap<Integer, Integer> createReverseMap() {
        // Collections.reverseOrder() — public singleton. If preserved, iteration is [5,4,3,2,1];
        // if dropped, the loaded map falls back to natural ordering and the test fails.
        ConcurrentSkipListMap<Integer, Integer> m = new ConcurrentSkipListMap<>(Collections.reverseOrder());
        m.put(1, 10);
        m.put(5, 50);
        m.put(3, 30);
        m.put(2, 20);
        m.put(4, 40);
        return m;
    }

    ConcurrentSkipListMap<String, Integer> createCaseInsensitiveMap() {
        // String.CASE_INSENSITIVE_ORDER — public singleton. Divergence flags dropped comparator.
        ConcurrentSkipListMap<String, Integer> m = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
        m.put("banana", 1);
        m.put("APPLE", 2);
        m.put("Cherry", 3);
        return m;
    }

    ConcurrentSkipListMap<Integer, Integer> createLargeMap() {
        ConcurrentSkipListMap<Integer, Integer> m = new ConcurrentSkipListMap<>();
        for (int i = 999; i >= 0; i--) {
            m.put(i, i * 2);
        }
        return m;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ConcurrentSkipListMapData copy = (ConcurrentSkipListMapData) o;
        assertAll("ConcurrentSkipListMap",
                () -> assertIterableEquals(this.getValue().entrySet(), copy.getValue().entrySet()),
                () -> assertIterableEquals(this.getValue().values(), copy.getValue().values()),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyMap() != null) {
                        assertTrue(copy.getEmptyMap().isEmpty(), "empty map remains empty");
                    } else {
                        assertNull(copy.getEmptyMap());
                    }
                },
                () -> {
                    if (this.getReverseMap() != null) {
                        // If comparator preserved → entries iterate as [5→50, 4→40, 3→30, 2→20, 1→10]
                        assertIterableEquals(this.getReverseMap().entrySet(), copy.getReverseMap().entrySet(), "reverse-order map — comparator preserved");
                        assertEquals(Integer.valueOf(5), copy.getReverseMap().firstKey(), "firstKey() = 5 (reverse: largest first)");
                        assertEquals(Integer.valueOf(1), copy.getReverseMap().lastKey(), "lastKey() = 1 (reverse: smallest last)");
                    } else {
                        assertNull(copy.getReverseMap());
                    }
                },
                () -> {
                    if (this.getCaseInsensitiveMap() != null) {
                        System.out.println(this.getCaseInsensitiveMap());
                        System.out.println(copy.getCaseInsensitiveMap());
                        assertIterableEquals(this.getCaseInsensitiveMap().entrySet(), copy.getCaseInsensitiveMap().entrySet(), "case-insensitive map — comparator preserved");
                    } else {
                        assertNull(copy.getCaseInsensitiveMap());
                    }
                },
                () -> {
                    if (this.getLargeMap() != null) {
                        assertEquals(1000, copy.getLargeMap().size(), "large map size");
                        assertIterableEquals(this.getLargeMap().entrySet(), copy.getLargeMap().entrySet(), "large map sorted iteration end-to-end");
                    } else {
                        assertNull(copy.getLargeMap());
                    }
                }
        );
    }
}
