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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AtomicPrimitiveData implements BinaryHandlerTestData {
    private AtomicBoolean atomicBoolean = new AtomicBoolean(false);
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private AtomicIntegerArray atomicIntegerArray = new AtomicIntegerArray(0);
    private AtomicLong atomicLong = new AtomicLong(0);
    private AtomicLongArray atomicLongArray = new AtomicLongArray(0);

    // corner-case fields
    private AtomicInteger atomicIntegerNegative = new AtomicInteger(0);
    private AtomicIntegerArray atomicIntegerArrayWithValues = new AtomicIntegerArray(0);
    private AtomicLongArray atomicLongArrayWithValues = new AtomicLongArray(0);

    // ===== proposed edge-cases (review & cherry-pick) =====
    private AtomicInteger atomicIntegerMin = new AtomicInteger(0);
    private AtomicInteger atomicIntegerMax = new AtomicInteger(0);
    private AtomicLong atomicLongMin = new AtomicLong(0);
    private AtomicLong atomicLongMax = new AtomicLong(0);
    private AtomicIntegerArray emptyAtomicIntegerArray = new AtomicIntegerArray(0);
    private AtomicLongArray emptyAtomicLongArray = new AtomicLongArray(0);
    private AtomicIntegerArray singleElementAtomicIntegerArray = new AtomicIntegerArray(0);
    private AtomicIntegerArray largeAtomicIntegerArray = new AtomicIntegerArray(0);
    private AtomicLongArray largeAtomicLongArray = new AtomicLongArray(0);

    @Override
    public BinaryHandlerTestData fillSampleData() {
        atomicBoolean = new AtomicBoolean(true);
        atomicInteger = new AtomicInteger(42);
        atomicIntegerArray = new AtomicIntegerArray(10);
        atomicLong = new AtomicLong(10L);
        atomicLongArray = new AtomicLongArray(10);

        // corner-case initializations
        atomicIntegerNegative = new AtomicInteger(-123456);
        atomicIntegerArrayWithValues = new AtomicIntegerArray(new int[]{0, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 9999});
        atomicLongArrayWithValues = new AtomicLongArray(new long[]{Long.MAX_VALUE, Long.MIN_VALUE, -100L, 0L, 100L});

        // ===== proposed edge-cases =====
        atomicIntegerMin = new AtomicInteger(Integer.MIN_VALUE);
        atomicIntegerMax = new AtomicInteger(Integer.MAX_VALUE);
        atomicLongMin = new AtomicLong(Long.MIN_VALUE);
        atomicLongMax = new AtomicLong(Long.MAX_VALUE);
        emptyAtomicIntegerArray = new AtomicIntegerArray(0);
        emptyAtomicLongArray = new AtomicLongArray(0);
        singleElementAtomicIntegerArray = new AtomicIntegerArray(new int[]{42});
        int[] largeInts = new int[10_000];
        for (int i = 0; i < largeInts.length; i++) largeInts[i] = i;
        largeAtomicIntegerArray = new AtomicIntegerArray(largeInts);
        long[] largeLongs = new long[10_000];
        for (int i = 0; i < largeLongs.length; i++) largeLongs[i] = i * 2L;
        largeAtomicLongArray = new AtomicLongArray(largeLongs);

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        atomicBoolean.set(false);
        atomicInteger.set(100);
        atomicIntegerArray.set(0,100);
        atomicLong.set(50);
        atomicLongArray.set(0,50);

        // corner-case updates
        atomicIntegerNegative.set(Integer.MIN_VALUE);
        if (atomicIntegerArrayWithValues.length() > 2) {
            atomicIntegerArrayWithValues.set(2, Integer.MAX_VALUE - 1);
        }
        if (atomicLongArrayWithValues.length() > 0) {
            atomicLongArrayWithValues.set(0, Long.MAX_VALUE - 1);
        }

        return this;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        AtomicPrimitiveData copy = (AtomicPrimitiveData) o;
        assertAll("Atomic types Tests", //
                () -> assertEquals(this.atomicBoolean.get(), copy.atomicBoolean.get()),
                () -> assertEquals(this.atomicInteger.get(), copy.atomicInteger.get()),
                () -> assertEquals( this.atomicIntegerArray.length(), copy.atomicIntegerArray.length()),
                () -> assertEquals(this.atomicLong.get(), copy.atomicLong.get()),
                () -> assertEquals(this.atomicLongArray.length(), copy.atomicLongArray.length()),

                // corner-case verifications
                () -> assertEquals(this.atomicIntegerNegative.get(), copy.atomicIntegerNegative.get(), "atomic integer negative value"),
                () -> assertEquals(this.atomicIntegerArrayWithValues.length(), copy.atomicIntegerArrayWithValues.length(), "atomic integer array length"),
                // only compare element at index 1 when both arrays have that index
                () -> {
                    if (this.atomicIntegerArrayWithValues.length() > 1 && copy.atomicIntegerArrayWithValues.length() > 1) {
                        assertEquals(this.atomicIntegerArrayWithValues.get(1), copy.atomicIntegerArrayWithValues.get(1), "atomic integer array element at 1");
                    }
                },
                () -> assertEquals(this.atomicLongArrayWithValues.length(), copy.atomicLongArrayWithValues.length(), "atomic long array length"),
                // only compare element at index 0 when both arrays have at least one element
                () -> {
                    if (this.atomicLongArrayWithValues.length() > 0 && copy.atomicLongArrayWithValues.length() > 0) {
                        assertEquals(this.atomicLongArrayWithValues.get(0), copy.atomicLongArrayWithValues.get(0), "atomic long array element at 0");
                    }
                },

                // ===== proposed edge-case verifications =====
                () -> assertEquals(this.atomicIntegerMin.get(), copy.atomicIntegerMin.get(), "AtomicInteger MIN_VALUE"),
                () -> assertEquals(this.atomicIntegerMax.get(), copy.atomicIntegerMax.get(), "AtomicInteger MAX_VALUE"),
                () -> assertEquals(this.atomicLongMin.get(), copy.atomicLongMin.get(), "AtomicLong MIN_VALUE"),
                () -> assertEquals(this.atomicLongMax.get(), copy.atomicLongMax.get(), "AtomicLong MAX_VALUE"),
                () -> assertEqualContents(this.emptyAtomicIntegerArray, copy.emptyAtomicIntegerArray, "empty AtomicIntegerArray"),
                () -> assertEqualContents(this.emptyAtomicLongArray, copy.emptyAtomicLongArray, "empty AtomicLongArray"),
                () -> assertEqualContents(this.singleElementAtomicIntegerArray, copy.singleElementAtomicIntegerArray, "single-element AtomicIntegerArray"),
                () -> assertEqualContents(this.largeAtomicIntegerArray, copy.largeAtomicIntegerArray, "large AtomicIntegerArray (10k)"),
                () -> assertEqualContents(this.largeAtomicLongArray, copy.largeAtomicLongArray, "large AtomicLongArray (10k)")
        );
    }

    // ===== proposed edge-cases — element-wise array comparison =====
    private static void assertEqualContents(AtomicIntegerArray a, AtomicIntegerArray b, String label) {
        assertEquals(a.length(), b.length(), label + " length");
        for (int i = 0; i < a.length(); i++) {
            assertEquals(a.get(i), b.get(i), label + "[" + i + "]");
        }
    }

    private static void assertEqualContents(AtomicLongArray a, AtomicLongArray b, String label) {
        assertEquals(a.length(), b.length(), label + " length");
        for (int i = 0; i < a.length(); i++) {
            assertEquals(a.get(i), b.get(i), label + "[" + i + "]");
        }
    }
}
