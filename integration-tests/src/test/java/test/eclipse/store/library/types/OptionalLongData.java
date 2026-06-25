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

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionalLongData implements BinaryHandlerTestData {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    OptionalLong value = OptionalLong.of(0);

    // ===== proposed edge-cases (review & cherry-pick) =====
    // OptionalLong has two distinct states (empty vs present) backed by isPresent + value.
    // The critical probe is empty() vs of(0L): both have value==0 internally, so a handler
    // that fails to persist isPresent would collapse them into the same thing. equals() is
    // value-based on (isPresent, value), so assertEquals distinguishes them correctly.
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalLong emptyOptional;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalLong ofZero;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalLong ofLongMin;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalLong ofLongMax;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalLong ofNegativeOne;

    @Override
    public OptionalLongData fillSampleData() {
        value = OptionalLong.of(44356);

        // ===== proposed edge-cases =====
        emptyOptional = OptionalLong.empty();
        ofZero = OptionalLong.of(0L);
        ofLongMin = OptionalLong.of(Long.MIN_VALUE);
        ofLongMax = OptionalLong.of(Long.MAX_VALUE);
        ofNegativeOne = OptionalLong.of(-1L);

        return this;
    }

    OptionalLong getValue() {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public OptionalLong getEmptyOptional() {
        return emptyOptional;
    }

    public OptionalLong getOfZero() {
        return ofZero;
    }

    public OptionalLong getOfLongMin() {
        return ofLongMin;
    }

    public OptionalLong getOfLongMax() {
        return ofLongMax;
    }

    public OptionalLong getOfNegativeOne() {
        return ofNegativeOne;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        OptionalLongData copy = (OptionalLongData) o;
        assertAll("OptionalLong tests",
                () -> assertEquals(this.getValue(), copy.getValue(), "OptionalLong"),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyOptional() != null) {
                        assertFalse(copy.getEmptyOptional().isPresent(), "empty() must stay empty — must NOT collapse to of(0)");
                        assertEquals(OptionalLong.empty(), copy.getEmptyOptional());
                    } else {
                        assertNull(copy.getEmptyOptional());
                    }
                },
                () -> {
                    if (this.getOfZero() != null) {
                        assertTrue(copy.getOfZero().isPresent(), "of(0) must stay present — must NOT collapse to empty()");
                        assertEquals(0L, copy.getOfZero().getAsLong());
                        assertEquals(OptionalLong.of(0L), copy.getOfZero());
                    } else {
                        assertNull(copy.getOfZero());
                    }
                },
                () -> {
                    if (this.getOfLongMin() != null) {
                        assertEquals(Long.MIN_VALUE, copy.getOfLongMin().getAsLong(), "Long.MIN_VALUE");
                    } else {
                        assertNull(copy.getOfLongMin());
                    }
                },
                () -> {
                    if (this.getOfLongMax() != null) {
                        assertEquals(Long.MAX_VALUE, copy.getOfLongMax().getAsLong(), "Long.MAX_VALUE");
                    } else {
                        assertNull(copy.getOfLongMax());
                    }
                },
                () -> {
                    if (this.getOfNegativeOne() != null) {
                        assertEquals(-1L, copy.getOfNegativeOne().getAsLong(), "-1L (all bits set)");
                    } else {
                        assertNull(copy.getOfNegativeOne());
                    }
                }
        );
    }
}
