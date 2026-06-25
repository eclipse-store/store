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

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionalIntData implements BinaryHandlerTestData {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    OptionalInt value = OptionalInt.of(0);

    // ===== proposed edge-cases (review & cherry-pick) =====
    // OptionalInt has two distinct states (empty vs present) backed by isPresent + value.
    // Critical probe: empty() vs of(0). Both have value==0 internally — a handler that fails
    // to persist isPresent would collapse them. equals() is value-based on (isPresent, value),
    // so assertEquals distinguishes them correctly; the isPresent()-direct asserts catch the
    // same collapse if equals were somehow broken too.
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalInt emptyOptional;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalInt ofZero;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalInt ofIntMin;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalInt ofIntMax;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalInt ofNegativeOne;

    @Override
    public OptionalIntData fillSampleData() {
        value = OptionalInt.of(44356);

        // ===== proposed edge-cases =====
        emptyOptional = OptionalInt.empty();
        ofZero = OptionalInt.of(0);
        ofIntMin = OptionalInt.of(Integer.MIN_VALUE);
        ofIntMax = OptionalInt.of(Integer.MAX_VALUE);
        ofNegativeOne = OptionalInt.of(-1);

        return this;
    }

    OptionalInt getValue() {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public OptionalInt getEmptyOptional() {
        return emptyOptional;
    }

    public OptionalInt getOfZero() {
        return ofZero;
    }

    public OptionalInt getOfIntMin() {
        return ofIntMin;
    }

    public OptionalInt getOfIntMax() {
        return ofIntMax;
    }

    public OptionalInt getOfNegativeOne() {
        return ofNegativeOne;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        OptionalIntData copy = (OptionalIntData) o;
        assertAll("OptionalInt tests",
                () -> assertEquals(this.getValue(), copy.getValue(), "OptionalInt"),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyOptional() != null) {
                        assertFalse(copy.getEmptyOptional().isPresent(), "empty() must stay empty — must NOT collapse to of(0)");
                        assertEquals(OptionalInt.empty(), copy.getEmptyOptional());
                    } else {
                        assertNull(copy.getEmptyOptional());
                    }
                },
                () -> {
                    if (this.getOfZero() != null) {
                        assertTrue(copy.getOfZero().isPresent(), "of(0) must stay present — must NOT collapse to empty()");
                        assertEquals(0, copy.getOfZero().getAsInt());
                        assertEquals(OptionalInt.of(0), copy.getOfZero());
                    } else {
                        assertNull(copy.getOfZero());
                    }
                },
                () -> {
                    if (this.getOfIntMin() != null) {
                        assertEquals(Integer.MIN_VALUE, copy.getOfIntMin().getAsInt(), "Integer.MIN_VALUE");
                    } else {
                        assertNull(copy.getOfIntMin());
                    }
                },
                () -> {
                    if (this.getOfIntMax() != null) {
                        assertEquals(Integer.MAX_VALUE, copy.getOfIntMax().getAsInt(), "Integer.MAX_VALUE");
                    } else {
                        assertNull(copy.getOfIntMax());
                    }
                },
                () -> {
                    if (this.getOfNegativeOne() != null) {
                        assertEquals(-1, copy.getOfNegativeOne().getAsInt(), "-1 (all bits set)");
                    } else {
                        assertNull(copy.getOfNegativeOne());
                    }
                }
        );
    }
}
