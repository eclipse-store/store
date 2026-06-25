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
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VectorData implements BinaryHandlerTestData {
    byte SAMPLE_BYTE = 100;
    short SAMPLE_SHORT = 50;
    int SAMPLE_INT = 5401;
    long SAMPLE_LONG = 1545455464654L;
    float SAMPLE_FLOAT = 3.141526f;
    double SAMPLE_DOUBLE = 3.141526545;
    boolean SAMPLE_BOOLEAN = Boolean.TRUE;
    char SAMPLE_CHAR = 'c';

    Byte SAMPLE_BYTE_N = 100;
    Short SAMPLE_SHORT_N = 50;
    Integer SAMPLE_INT_N = 5401;
    Long SAMPLE_LONG_N = 1545455464654L;
    Float SAMPLE_FLOAT_N = 3.141526f;
    Double SAMPLE_DOUBLE_N = 3.141526545;
    Boolean SAMPLE_BOOLEAN_N = Boolean.TRUE;
    Character SAMPLE_CHARACTER = 'c';
    String SAMPLE_STRING = "sample string value \t \n";

    private Vector<Object> vector = new Vector<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // Vector is value-compared via assertIterableEquals (size + element-wise .equals, order preserved).
    // Vector's capacityIncrement is a protected field with no public getter — not asserted here;
    // ES's BinaryHandlerVector is not expected to round-trip that growth-policy attribute.
    private Vector<String> emptyVector;
    private Vector<Integer> oversizedCapacityVector;
    private Vector<Integer> largeVector;
    private Vector<Integer> nullsVector;
    private Vector<Integer> duplicatesVector;
    private Vector<Integer> largeValuesVector;
    private Vector<Vector<PrimitiveTypes>> nestedVector;
    private Vector<String> stringEdgeCasesVector;

    Vector<Object> getVector() {
        return vector;
    }

    @Override
    public VectorData fillSampleData() {
        vector.add(0, SAMPLE_BYTE);
        vector.add(1, SAMPLE_SHORT);
        vector.add(2, SAMPLE_INT);
        vector.add(3, SAMPLE_LONG);
        vector.add(4, SAMPLE_FLOAT);
        vector.add(5, SAMPLE_DOUBLE);
        vector.add(6, SAMPLE_BOOLEAN);
        vector.add(7, SAMPLE_CHAR);

        vector.add(8, SAMPLE_BYTE_N);
        vector.add(9, SAMPLE_SHORT_N);
        vector.add(10, SAMPLE_INT_N);
        vector.add(11, SAMPLE_LONG_N);
        vector.add(12, SAMPLE_FLOAT_N);
        vector.add(13, SAMPLE_DOUBLE_N);
        vector.add(14, SAMPLE_BOOLEAN_N);
        vector.add(15, SAMPLE_CHARACTER);
        vector.add(16, SAMPLE_STRING);

        // ===== proposed edge-cases =====
        emptyVector = new Vector<>();
        oversizedCapacityVector = createOversizedCapacityVector();
        largeVector = createLargeVector();
        nullsVector = createNullsVector();
        duplicatesVector = createDuplicatesVector();
        largeValuesVector = createLargeValuesVector();
        nestedVector = createNestedVector();
        stringEdgeCasesVector = createStringEdgeCasesVector();

        return this;
    }

    // ===== proposed edge-cases — getters =====

    public Vector<String> getEmptyVector() {
        return emptyVector;
    }

    public Vector<Integer> getOversizedCapacityVector() {
        return oversizedCapacityVector;
    }

    public Vector<Integer> getLargeVector() {
        return largeVector;
    }

    public Vector<Integer> getNullsVector() {
        return nullsVector;
    }

    public Vector<Integer> getDuplicatesVector() {
        return duplicatesVector;
    }

    public Vector<Integer> getLargeValuesVector() {
        return largeValuesVector;
    }

    public Vector<Vector<PrimitiveTypes>> getNestedVector() {
        return nestedVector;
    }

    public Vector<String> getStringEdgeCasesVector() {
        return stringEdgeCasesVector;
    }

    Vector<Integer> createOversizedCapacityVector() {
        Vector<Integer> v = new Vector<>(1000);
        v.add(1);
        v.add(2);
        v.add(3);
        return v;
    }

    Vector<Integer> createLargeVector() {
        Vector<Integer> v = new Vector<>();
        for (int i = 0; i < 10_000; i++) {
            v.add(i);
        }
        return v;
    }

    Vector<Integer> createNullsVector() {
        Vector<Integer> v = new Vector<>();
        v.add(null);
        v.add(0);
        v.add(null);
        v.add(42);
        return v;
    }

    Vector<Integer> createDuplicatesVector() {
        Vector<Integer> v = new Vector<>();
        v.add(7);
        v.add(7);
        v.add(7);
        return v;
    }

    Vector<Integer> createLargeValuesVector() {
        Vector<Integer> v = new Vector<>();
        v.add(Integer.MAX_VALUE);
        v.add(Integer.MIN_VALUE);
        v.add(0);
        return v;
    }

    Vector<Vector<PrimitiveTypes>> createNestedVector() {
        Vector<Vector<PrimitiveTypes>> outer = new Vector<>();
        outer.add(null); // null inner Vector
        outer.add(new Vector<>()); // empty inner Vector
        Vector<PrimitiveTypes> filled = new Vector<>();
        filled.add(PrimitiveTypes.fillSample());
        outer.add(filled);
        return outer;
    }

    Vector<String> createStringEdgeCasesVector() {
        Vector<String> v = new Vector<>();
        v.add("");
        v.add("a" + ((char) 0) + "b"); // NUL inside string
        v.add(new String(Character.toChars(0x1F600))); // 4-byte UTF-8 (U+1F600) via surrogate pair
        v.add(String.join("", Collections.nCopies(1000, "x"))); // long
        return v;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        VectorData copy = (VectorData) o;
        assertAll("Vector tests",
                () -> assertIterableEquals(this.getVector(), copy.getVector()),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyVector() != null) {
                        assertTrue(copy.getEmptyVector().isEmpty(), "empty vector remains empty");
                    } else {
                        assertNull(copy.getEmptyVector());
                    }
                },
                () -> {
                    // capacity-vs-size: ES must store size, not capacity
                    if (this.getOversizedCapacityVector() != null) {
                        assertEquals(this.getOversizedCapacityVector().size(), copy.getOversizedCapacityVector().size(), "oversized-capacity size");
                        assertIterableEquals(this.getOversizedCapacityVector(), copy.getOversizedCapacityVector(), "oversized-capacity content");
                    } else {
                        assertNull(copy.getOversizedCapacityVector());
                    }
                },
                () -> assertIterableEquals(this.getLargeVector(), copy.getLargeVector(), "large vector (10k)"),
                () -> assertIterableEquals(this.getNullsVector(), copy.getNullsVector(), "Vector<Integer> with nulls"),
                () -> assertIterableEquals(this.getDuplicatesVector(), copy.getDuplicatesVector(), "Vector<Integer> with duplicates"),
                () -> assertIterableEquals(this.getLargeValuesVector(), copy.getLargeValuesVector(), "Vector<Integer> with MIN/MAX"),
                () -> assertIterableEquals(this.getNestedVector(), copy.getNestedVector(), "nested vector with null + empty inner"),
                () -> assertIterableEquals(this.getStringEdgeCasesVector(), copy.getStringEdgeCasesVector(), "string edge cases (empty, NUL, surrogate pair, long)")
        );
    }
}
