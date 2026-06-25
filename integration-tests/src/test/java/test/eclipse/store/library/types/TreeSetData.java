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
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TreeSetData implements BinaryHandlerTestData {
    TreeSet<String> value = new TreeSet<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // TreeSet is a SortedSet — iteration is in comparator-defined order. assertIterableEquals doubles
    // as both a content-equality check and a sort-order check. The reverse/caseInsensitive probes
    // additionally test whether the handler preserves the Comparator: if the comparator is dropped,
    // copy falls back to natural ordering and the iteration sequence diverges from `this`.
    // Lambdas / anonymous comparator classes are intentionally avoided — only public JDK singletons
    // (Collections.reverseOrder(), String.CASE_INSENSITIVE_ORDER) so failures point at ES, not at
    // synthetic-class handling.
    private TreeSet<Integer> emptyTreeSet;
    private TreeSet<Integer> intTreeSet;
    private TreeSet<Integer> reverseTreeSet;
    private TreeSet<String> caseInsensitiveTreeSet;
    private TreeSet<Integer> largeTreeSet;

    @Override
    public TreeSetData fillSampleData() {
        value.add("SomeString");

        // ===== proposed edge-cases =====
        emptyTreeSet = new TreeSet<>();
        intTreeSet = createIntTreeSet();
        reverseTreeSet = createReverseTreeSet();
        caseInsensitiveTreeSet = createCaseInsensitiveTreeSet();
        largeTreeSet = createLargeTreeSet();

        return this;
    }

    // ===== proposed edge-cases — getters =====

    public TreeSet<Integer> getEmptyTreeSet() {
        return emptyTreeSet;
    }

    public TreeSet<Integer> getIntTreeSet() {
        return intTreeSet;
    }

    public TreeSet<Integer> getReverseTreeSet() {
        return reverseTreeSet;
    }

    public TreeSet<String> getCaseInsensitiveTreeSet() {
        return caseInsensitiveTreeSet;
    }

    public TreeSet<Integer> getLargeTreeSet() {
        return largeTreeSet;
    }

    TreeSet<Integer> createIntTreeSet() {
        TreeSet<Integer> s = new TreeSet<>();
        // insert out of order — iteration must come back as [1, 2, 3, 4, 5]
        s.add(3);
        s.add(1);
        s.add(5);
        s.add(2);
        s.add(4);
        return s;
    }

    TreeSet<Integer> createReverseTreeSet() {
        // Collections.reverseOrder() returns a public singleton (Collections.ReverseComparator.REVERSE_ORDER).
        // Iteration after round-trip must remain [5, 4, 3, 2, 1] — otherwise the comparator was dropped.
        TreeSet<Integer> s = new TreeSet<>(Collections.reverseOrder());
        s.add(1);
        s.add(5);
        s.add(3);
        s.add(2);
        s.add(4);
        return s;
    }

    TreeSet<String> createCaseInsensitiveTreeSet() {
        // String.CASE_INSENSITIVE_ORDER is a public singleton (String.CaseInsensitiveComparator).
        // Natural String.compareTo would order ["APPLE", "Cherry", "banana"] (uppercase first);
        // case-insensitive comparator orders ["APPLE", "banana", "Cherry"]. Divergence after round-trip
        // signals dropped comparator.
        TreeSet<String> s = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        s.add("banana");
        s.add("APPLE");
        s.add("Cherry");
        return s;
    }

    TreeSet<Integer> createLargeTreeSet() {
        TreeSet<Integer> s = new TreeSet<>();
        // Insert in non-sorted order to make sortedness round-trip meaningful
        for (int i = 999; i >= 0; i--) {
            s.add(i);
        }
        return s;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        TreeSetData copy = (TreeSetData)o;
        assertAll("TreeSet tests",
                () -> assertIterableEquals(this.value, copy.value),

                // ===== proposed edge-case verifications =====
                // assertIterableEquals doubles as a sort-order check: TreeSet's iterator returns
                // elements in comparator-defined order, so a divergent order points at a dropped
                // comparator (for reverse/caseInsensitive) or at content corruption.
                () -> {
                    if (this.getEmptyTreeSet() != null) {
                        assertTrue(copy.getEmptyTreeSet().isEmpty(), "empty TreeSet remains empty");
                    } else {
                        assertNull(copy.getEmptyTreeSet());
                    }
                },
                () -> {
                    if (this.getIntTreeSet() != null) {
                        assertIterableEquals(this.getIntTreeSet(), copy.getIntTreeSet(), "natural-order TreeSet<Integer> — sorted iteration");
                        assertEquals(Integer.valueOf(1), copy.getIntTreeSet().first(), "first() = 1 (natural min)");
                        assertEquals(Integer.valueOf(5), copy.getIntTreeSet().last(), "last() = 5 (natural max)");
                    } else {
                        assertNull(copy.getIntTreeSet());
                    }
                },
                () -> {
                    if (this.getReverseTreeSet() != null) {
                        // If comparator preserved → iteration [5,4,3,2,1]; if dropped → [1,2,3,4,5]
                        assertIterableEquals(this.getReverseTreeSet(), copy.getReverseTreeSet(), "reverse-order TreeSet — comparator preserved");
                        assertEquals(Integer.valueOf(5), copy.getReverseTreeSet().first(), "first() = 5 (reverse: largest first)");
                        assertEquals(Integer.valueOf(1), copy.getReverseTreeSet().last(), "last() = 1 (reverse: smallest last)");
                    } else {
                        assertNull(copy.getReverseTreeSet());
                    }
                },
                () -> {
                    if (this.getCaseInsensitiveTreeSet() != null) {
                        // If comparator preserved → ["APPLE","banana","Cherry"];
                        // if dropped → ["APPLE","Cherry","banana"] (natural String order, uppercase first)
                        System.out.println(this.getCaseInsensitiveTreeSet());
                        System.out.println(copy.getCaseInsensitiveTreeSet());
                        assertIterableEquals(this.getCaseInsensitiveTreeSet(), copy.getCaseInsensitiveTreeSet(), "case-insensitive TreeSet — comparator preserved");
                    } else {
                        assertNull(copy.getCaseInsensitiveTreeSet());
                    }
                },
                () -> {
                    if (this.getLargeTreeSet() != null) {
                        assertEquals(this.getLargeTreeSet().size(), copy.getLargeTreeSet().size(), "large TreeSet size");
                        assertIterableEquals(this.getLargeTreeSet(), copy.getLargeTreeSet(), "large TreeSet sorted iteration end-to-end");
                    } else {
                        assertNull(copy.getLargeTreeSet());
                    }
                }
        );
    }
}
