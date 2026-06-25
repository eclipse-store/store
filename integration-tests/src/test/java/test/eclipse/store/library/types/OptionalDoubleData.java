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

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionalDoubleData implements BinaryHandlerTestData {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    OptionalDouble value = OptionalDouble.of(0);

    // ===== proposed edge-cases (review & cherry-pick) =====
    // OptionalDouble adds double-specific concerns on top of the empty/present distinction:
    //   - NaN, ±Infinity must survive bit-exactly (NaN equals NaN via Double.compare, but raw
    //     bit pattern matters too — Double.doubleToRawLongBits gives the exact preservation check)
    //   - -0.0 vs +0.0 are bit-distinct; OptionalDouble.equals uses Double.compare which
    //     distinguishes them
    //   - empty() vs of(0.0): collapse probe like OptionalInt/OptionalLong
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalDouble emptyOptional;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalDouble ofZero;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalDouble ofNaN;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalDouble ofPositiveInfinity;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalDouble ofNegativeZero;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalDouble ofDoubleMax;

    @Override
    public OptionalDoubleData fillSampleData() {
        value = OptionalDouble.of(44356.455d);

        // ===== proposed edge-cases =====
        emptyOptional = OptionalDouble.empty();
        ofZero = OptionalDouble.of(0.0);
        ofNaN = OptionalDouble.of(Double.NaN);
        ofPositiveInfinity = OptionalDouble.of(Double.POSITIVE_INFINITY);
        ofNegativeZero = OptionalDouble.of(-0.0);
        ofDoubleMax = OptionalDouble.of(Double.MAX_VALUE);

        return this;
    }

    OptionalDouble getValue() {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public OptionalDouble getEmptyOptional() {
        return emptyOptional;
    }

    public OptionalDouble getOfZero() {
        return ofZero;
    }

    public OptionalDouble getOfNaN() {
        return ofNaN;
    }

    public OptionalDouble getOfPositiveInfinity() {
        return ofPositiveInfinity;
    }

    public OptionalDouble getOfNegativeZero() {
        return ofNegativeZero;
    }

    public OptionalDouble getOfDoubleMax() {
        return ofDoubleMax;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        OptionalDoubleData copy = (OptionalDoubleData)o;
        assertAll("OptionalDouble tests",
                () -> assertEquals(this.getValue(), copy.getValue(), "OptionalDouble"),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyOptional() != null) {
                        assertFalse(copy.getEmptyOptional().isPresent(), "empty() must stay empty — must NOT collapse to of(0.0)");
                        assertEquals(OptionalDouble.empty(), copy.getEmptyOptional());
                    } else {
                        assertNull(copy.getEmptyOptional());
                    }
                },
                () -> {
                    if (this.getOfZero() != null) {
                        assertTrue(copy.getOfZero().isPresent(), "of(0.0) must stay present — must NOT collapse to empty()");
                        assertEquals(0.0, copy.getOfZero().getAsDouble());
                        assertEquals(OptionalDouble.of(0.0), copy.getOfZero());
                    } else {
                        assertNull(copy.getOfZero());
                    }
                },
                () -> {
                    if (this.getOfNaN() != null) {
                        // NaN: Double.compare(NaN, NaN) == 0, so equals() works; raw-bits asserts the exact pattern
                        assertTrue(Double.isNaN(copy.getOfNaN().getAsDouble()), "NaN survives");
                        assertEquals(
                                Double.doubleToRawLongBits(Double.NaN),
                                Double.doubleToRawLongBits(copy.getOfNaN().getAsDouble()),
                                "NaN bit-exact"
                        );
                    } else {
                        assertNull(copy.getOfNaN());
                    }
                },
                () -> {
                    if (this.getOfPositiveInfinity() != null) {
                        assertEquals(Double.POSITIVE_INFINITY, copy.getOfPositiveInfinity().getAsDouble(), "+Infinity");
                    } else {
                        assertNull(copy.getOfPositiveInfinity());
                    }
                },
                () -> {
                    if (this.getOfNegativeZero() != null) {
                        // -0.0 vs +0.0: bit pattern differs; Double.compare distinguishes them
                        assertEquals(
                                Double.doubleToRawLongBits(-0.0),
                                Double.doubleToRawLongBits(copy.getOfNegativeZero().getAsDouble()),
                                "-0.0 bit-exact (distinct from +0.0)"
                        );
                    } else {
                        assertNull(copy.getOfNegativeZero());
                    }
                },
                () -> {
                    if (this.getOfDoubleMax() != null) {
                        assertEquals(Double.MAX_VALUE, copy.getOfDoubleMax().getAsDouble(), "Double.MAX_VALUE");
                    } else {
                        assertNull(copy.getOfDoubleMax());
                    }
                }
        );
    }
}
