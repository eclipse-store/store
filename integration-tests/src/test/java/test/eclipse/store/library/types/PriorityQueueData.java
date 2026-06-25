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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PriorityQueueData implements BinaryHandlerTestData {
    PriorityQueue<String> value = new PriorityQueue<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // PriorityQueue's iterator() does NOT guarantee priority order — it traverses the internal heap
    // array in arbitrary layout order. The robust API for "what came out in priority order" is poll().
    // All probes below drain via poll() into a list and compare against expected sequences.
    // The reversePQ probe additionally tests whether Comparator.reverseOrder() survives — divergence
    // (ascending vs descending) signals a dropped comparator.
    // PriorityQueue does not permit null elements, so no null-entry probe.
    private PriorityQueue<Integer> emptyPQ;
    private PriorityQueue<Integer> naturalOrderPQ;
    private PriorityQueue<Integer> reversePQ;
    private PriorityQueue<Integer> duplicatesPQ;
    private PriorityQueue<Integer> largePQ;

    @Override
    public PriorityQueueData fillSampleData() {
        value.add("C");
        value.add("C++");
        value.add("Java");
        value.add("Python");

        // ===== proposed edge-cases =====
        emptyPQ = new PriorityQueue<>();
        naturalOrderPQ = createNaturalOrderPQ();
        reversePQ = createReversePQ();
        duplicatesPQ = createDuplicatesPQ();
        largePQ = createLargePQ();

        return this;
    }

    public PriorityQueue<String> getValue() {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public PriorityQueue<Integer> getEmptyPQ() {
        return emptyPQ;
    }

    public PriorityQueue<Integer> getNaturalOrderPQ() {
        return naturalOrderPQ;
    }

    public PriorityQueue<Integer> getReversePQ() {
        return reversePQ;
    }

    public PriorityQueue<Integer> getDuplicatesPQ() {
        return duplicatesPQ;
    }

    public PriorityQueue<Integer> getLargePQ() {
        return largePQ;
    }

    PriorityQueue<Integer> createNaturalOrderPQ() {
        // Insertion order is shuffled; poll() must yield ascending.
        PriorityQueue<Integer> q = new PriorityQueue<>();
        for (int n : new int[]{3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5}) {
            q.add(n);
        }
        return q;
    }

    PriorityQueue<Integer> createReversePQ() {
        // Comparator.reverseOrder() — public singleton. If preserved, poll() yields descending [5..1].
        // If dropped, copy falls back to natural ordering and poll() yields ascending [1..5].
        PriorityQueue<Integer> q = new PriorityQueue<>(Comparator.reverseOrder());
        for (int n : new int[]{1, 5, 3, 2, 4}) {
            q.add(n);
        }
        return q;
    }

    PriorityQueue<Integer> createDuplicatesPQ() {
        PriorityQueue<Integer> q = new PriorityQueue<>();
        q.add(5);
        q.add(5);
        q.add(5);
        return q;
    }

    PriorityQueue<Integer> createLargePQ() {
        // Pseudo-random insertion (deterministic) so the test stays stable across runs while still
        // exercising a non-trivial heap structure.
        PriorityQueue<Integer> q = new PriorityQueue<>();
        int n = 1000;
        for (int i = 0; i < n; i++) {
            q.add((i * 2654435761L >>> 0) % 100_000 == 0 ? 0 : (int) ((i * 2654435761L) % 1_000_000L));
        }
        return q;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        PriorityQueueData copy = (PriorityQueueData) o;
        assertAll("PriorityQueue tests",
                () -> assertIterableEquals(this.getValue(), copy.getValue()),

                // ===== proposed edge-case verifications =====
                // Robust priority-order check: drain via poll() and compare the resulting sequence.
                () -> {
                    if (this.getEmptyPQ() != null) {
                        assertTrue(copy.getEmptyPQ().isEmpty(), "empty PQ remains empty");
                    } else {
                        assertNull(copy.getEmptyPQ());
                    }
                },
                () -> {
                    if (this.getNaturalOrderPQ() != null) {
                        List<Integer> drained = drain(copy.getNaturalOrderPQ());
                        assertEquals(Arrays.asList(1, 1, 2, 3, 3, 4, 5, 5, 5, 6, 9), drained, "natural-order poll() sequence");
                    } else {
                        assertNull(copy.getNaturalOrderPQ());
                    }
                },
                () -> {
                    if (this.getReversePQ() != null) {
                        // If comparator preserved → [5,4,3,2,1]; if dropped → [1,2,3,4,5]
                        List<Integer> drained = drain(copy.getReversePQ());
                        assertEquals(Arrays.asList(5, 4, 3, 2, 1), drained, "reverse-order poll() sequence — comparator preserved");
                    } else {
                        assertNull(copy.getReversePQ());
                    }
                },
                () -> {
                    if (this.getDuplicatesPQ() != null) {
                        List<Integer> drained = drain(copy.getDuplicatesPQ());
                        assertEquals(Arrays.asList(5, 5, 5), drained, "duplicate priorities all polled");
                    } else {
                        assertNull(copy.getDuplicatesPQ());
                    }
                },
                () -> {
                    if (this.getLargePQ() != null) {
                        int expectedSize = this.getLargePQ().size();
                        assertEquals(expectedSize, copy.getLargePQ().size(), "large PQ size");
                        // Monotonic poll() ascending end-to-end
                        Integer prev = null;
                        int count = 0;
                        while (!copy.getLargePQ().isEmpty()) {
                            Integer next = copy.getLargePQ().poll();
                            if (prev != null) {
                                assertTrue(prev <= next, "non-monotonic poll at index " + count + ": " + prev + " > " + next);
                            }
                            prev = next;
                            count++;
                        }
                        assertEquals(expectedSize, count, "large PQ drained count");
                    } else {
                        assertNull(copy.getLargePQ());
                    }
                }
        );
    }

    private static List<Integer> drain(PriorityQueue<Integer> q) {
        List<Integer> out = new ArrayList<>(q.size());
        while (!q.isEmpty()) {
            out.add(q.poll());
        }
        return out;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        value.add("ahoj");
        return this;
    }
}
