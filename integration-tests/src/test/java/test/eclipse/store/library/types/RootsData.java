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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RootsData implements BinaryHandlerTestData {
    private List<String> emptyList;
    private Map<String, String> emptyMap;
    private NavigableSet<String> emptyNavigableSet;
    private NavigableMap<String, String> emptyNavigableMap;
    private Comparator<String> reverseOrderComparator;
    private Comparator<String> naturalOrderComparator;
    private BigDecimal zeroBigDecimal;
    private BigDecimal oneBigDecimal;
    private BigDecimal tenBigDecimal;
    private BigInteger zeroBigInteger;
    private BigInteger oneBigInteger;
    private BigInteger tenBigInteger;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<String> emptyOptional;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalInt emptyOptionalInt;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalLong emptyOptionalLong;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private OptionalDouble emptyOptionalDouble;


    @Override
    public RootsData fillSampleData() {
        emptyList = Collections.emptyList();
        emptyMap = Collections.emptyMap();
        emptyNavigableSet = Collections.emptyNavigableSet();
        emptyNavigableMap = Collections.emptyNavigableMap();
        reverseOrderComparator = Collections.reverseOrder();
        naturalOrderComparator = Comparator.naturalOrder();
        zeroBigDecimal = BigDecimal.ZERO;
        oneBigDecimal = BigDecimal.ONE;
        tenBigDecimal = BigDecimal.TEN;
        zeroBigInteger = BigInteger.ZERO;
        oneBigInteger = BigInteger.ONE;
        tenBigInteger = BigInteger.TEN;
        emptyOptional = Optional.empty();
        emptyOptionalInt = OptionalInt.empty();
        emptyOptionalLong = OptionalLong.empty();
        emptyOptionalDouble = OptionalDouble.empty();
        return this;
    }

    Map<String, String> getEmptyMap() {
        return emptyMap;
    }

    List<String> getEmptyList() {
        return emptyList;
    }

    NavigableSet<String> getEmptyNavigableSet() {
        return emptyNavigableSet;
    }

    NavigableMap<String, String> getEmptyNavigableMap() {
        return emptyNavigableMap;
    }

    Comparator<String> getReverseOrderComparator() {
        return reverseOrderComparator;
    }

    Comparator<String> getNaturalOrderComparator() {
        return naturalOrderComparator;
    }

    BigDecimal getZeroBigDecimal() {
        return zeroBigDecimal;
    }

    BigDecimal getOneBigDecimal() {
        return oneBigDecimal;
    }

    BigDecimal getTenBigDecimal() {
        return tenBigDecimal;
    }

    BigInteger getZeroBigInteger() {
        return zeroBigInteger;
    }

    BigInteger getOneBigInteger() {
        return oneBigInteger;
    }

    BigInteger getTenBigInteger() {
        return tenBigInteger;
    }

    Optional<String> getEmptyOptional() {
        return emptyOptional;
    }

    OptionalInt getEmptyOptionalInt() {
        return emptyOptionalInt;
    }

    OptionalLong getEmptyOptionalLong() {
        return emptyOptionalLong;
    }

    OptionalDouble getEmptyOptionalDouble() {
        return emptyOptionalDouble;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        RootsData copy = (RootsData) o;
        assertAll(
                () -> assertEquals(this.emptyList, copy.getEmptyList()),
                () -> assertEquals(this.emptyMap, copy.getEmptyMap()),
                () -> assertEquals(this.emptyNavigableSet, copy.getEmptyNavigableSet()),
                () -> assertEquals(this.emptyNavigableMap, copy.getEmptyNavigableMap()),
                () -> assertEquals(this.reverseOrderComparator, copy.getReverseOrderComparator()),
                () -> assertEquals(this.naturalOrderComparator, copy.getNaturalOrderComparator()),
                () -> assertEquals(this.zeroBigDecimal, copy.getZeroBigDecimal()),
                () -> assertEquals(this.oneBigDecimal, copy.getOneBigDecimal()),
                () -> assertEquals(this.tenBigDecimal, copy.getTenBigDecimal()),
                () -> assertEquals(this.zeroBigInteger, copy.getZeroBigInteger()),
                () -> assertEquals(this.oneBigInteger, copy.getOneBigInteger()),
                () -> assertEquals(this.tenBigInteger, copy.getTenBigInteger()),
                () -> assertEquals(this.emptyOptional, copy.getEmptyOptional()),
                () -> assertEquals(this.emptyOptionalInt, copy.getEmptyOptionalInt()),
                () -> assertEquals(this.emptyOptionalLong, copy.getEmptyOptionalLong()),
                () -> assertEquals(this.emptyOptionalDouble, copy.getEmptyOptionalDouble())
        );

    }
}
