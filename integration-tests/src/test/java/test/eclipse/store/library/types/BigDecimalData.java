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

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BigDecimalData implements BinaryHandlerTestData {

    private BigDecimal bigDecimalValue;

    // corner-case fields
    private BigDecimal bigDecimalNull;
    private BigDecimal bigDecimalZero;
    private BigDecimal bigDecimalNegative;
    private BigDecimal bigDecimalLarge;
    private BigDecimal bigDecimalHighScale;
    private BigDecimal bigDecimalDifferentScale; // numerically equal but different scale
    private BigDecimal bigDecimalNegativeZero;
    private BigDecimal bigDecimalWithExponent;

    // additional typical usage / corner-cases
    private BigDecimal bigDecimalFromDouble;        // new BigDecimal(double) precision issue
    private BigDecimal bigDecimalValueOfDouble;     // BigDecimal.valueOf(double) preferred
    private BigDecimal bigDecimalStripTrailing;     // stripTrailingZeros() behavior
    private BigDecimal bigDecimalNegativeScale;     // negative scale usage
    private BigDecimal bigDecimalDivideRepeating;   // divide with rounding
    private BigDecimal bigDecimalAddResult;         // result of add
    private BigDecimal bigDecimalMulResult;         // result of multiply

    @Override
    public BinaryHandlerTestData fillSampleData() {
        bigDecimalValue = new BigDecimal(456);

        // corner-case initializations
        bigDecimalNull = null;
        bigDecimalZero = new BigDecimal("0");
        bigDecimalNegative = new BigDecimal("-12345.6789");
        // large value (many digits)
        bigDecimalLarge = new BigDecimal("123456789012345678901234567890.123456789");
        // high scale: many decimal places
        bigDecimalHighScale = new BigDecimal("0.000000000000000000123456789");
        // same numeric value different scale
        bigDecimalDifferentScale = new BigDecimal("1.2300");
        // negative zero representation
        bigDecimalNegativeZero = new BigDecimal("-0.00");
        // exponent form
        bigDecimalWithExponent = new BigDecimal("1.2345E3");

        // additional typical uses / corner-cases
        bigDecimalFromDouble = new BigDecimal(0.1d); // inexact construction (intentional)
        bigDecimalValueOfDouble = BigDecimal.valueOf(0.1d); // preferred exact decimal representation
        bigDecimalStripTrailing = new BigDecimal("123.4500").stripTrailingZeros(); // may change scale
        bigDecimalNegativeScale = new BigDecimal("12345").setScale(-2, RoundingMode.HALF_UP); // round to hundreds
        // dividing 1 by 3 with rounding
        bigDecimalDivideRepeating = new BigDecimal("1").divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP);
        bigDecimalAddResult = bigDecimalValue.add(new BigDecimal("1.5"));
        bigDecimalMulResult = bigDecimalValue.multiply(new BigDecimal("2"));

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        bigDecimalValue = new BigDecimal(555);

        // corner-case updates
        bigDecimalNull = BigDecimal.ONE; // from null to a value
        bigDecimalZero = new BigDecimal("0.00"); // preserve zero but different scale
        bigDecimalNegative = bigDecimalNegative.negate(); // flip sign
        bigDecimalLarge = bigDecimalLarge.add(new BigDecimal("1000"));
        bigDecimalHighScale = bigDecimalHighScale.add(new BigDecimal("0.0000000000000000000000001"));
        bigDecimalDifferentScale = new BigDecimal("1.23"); // reduce scale
        bigDecimalNegativeZero = new BigDecimal("0"); // normalize negative zero to zero
        bigDecimalWithExponent = new BigDecimal("1.2345E4"); // change exponent

        // update typical usage fields
        bigDecimalFromDouble = new BigDecimal(0.2d);
        bigDecimalValueOfDouble = BigDecimal.valueOf(0.2d);
        bigDecimalStripTrailing = new BigDecimal("100.0000").stripTrailingZeros();
        bigDecimalNegativeScale = bigDecimalNegativeScale.setScale(-3, RoundingMode.HALF_UP);
        bigDecimalDivideRepeating = new BigDecimal("2").divide(new BigDecimal("3"), 15, RoundingMode.HALF_UP);
        bigDecimalAddResult = bigDecimalValue.add(new BigDecimal("2.5"));
        bigDecimalMulResult = bigDecimalValue.multiply(new BigDecimal("3"));

        return this;
    }

    public BigDecimal getBigDecimalValue() { return bigDecimalValue; }

    // getters for corner-case fields
    public BigDecimal getBigDecimalNull() { return bigDecimalNull; }
    public BigDecimal getBigDecimalZero() { return bigDecimalZero; }
    public BigDecimal getBigDecimalNegative() { return bigDecimalNegative; }
    public BigDecimal getBigDecimalLarge() { return bigDecimalLarge; }
    public BigDecimal getBigDecimalHighScale() { return bigDecimalHighScale; }
    public BigDecimal getBigDecimalDifferentScale() { return bigDecimalDifferentScale; }
    public BigDecimal getBigDecimalNegativeZero() { return bigDecimalNegativeZero; }
    public BigDecimal getBigDecimalWithExponent() { return bigDecimalWithExponent; }

    // getters for typical usage
    public BigDecimal getBigDecimalFromDouble() { return bigDecimalFromDouble; }
    public BigDecimal getBigDecimalValueOfDouble() { return bigDecimalValueOfDouble; }
    public BigDecimal getBigDecimalStripTrailing() { return bigDecimalStripTrailing; }
    public BigDecimal getBigDecimalNegativeScale() { return bigDecimalNegativeScale; }
    public BigDecimal getBigDecimalDivideRepeating() { return bigDecimalDivideRepeating; }
    public BigDecimal getBigDecimalAddResult() { return bigDecimalAddResult; }
    public BigDecimal getBigDecimalMulResult() { return bigDecimalMulResult; }

    @Override
    public void proveResults(Object o) {
        assertNotNull(o);
        BigDecimalData copy = (BigDecimalData)o;
        // original simple check for primary value uses equals (null-safe)
        assertEquals(this.getBigDecimalValue(), copy.getBigDecimalValue(), "BigDecimal");

        // corner-case verifications with null guards
        if (this.getBigDecimalNull() == null) {
            assertNull(copy.getBigDecimalNull(), "bigDecimalNull should be null");
        } else {
            assertNotNull(copy.getBigDecimalNull(), "bigDecimalNull counterpart is null");
            assertEquals(0, this.getBigDecimalNull().compareTo(copy.getBigDecimalNull()), "bigDecimalNull value");
        }

        if (this.getBigDecimalZero() == null) {
            assertNull(copy.getBigDecimalZero(), "bigDecimalZero should be null");
        } else {
            assertNotNull(copy.getBigDecimalZero(), "bigDecimalZero counterpart is null");
            assertEquals(0, this.getBigDecimalZero().compareTo(copy.getBigDecimalZero()), "bigDecimalZero numeric equality");
        }

        if (this.getBigDecimalNegative() == null) {
            assertNull(copy.getBigDecimalNegative(), "bigDecimalNegative should be null");
        } else {
            assertNotNull(copy.getBigDecimalNegative(), "bigDecimalNegative counterpart is null");
            assertEquals(0, this.getBigDecimalNegative().compareTo(copy.getBigDecimalNegative()), "bigDecimalNegative numeric equality");
            assertEquals(this.getBigDecimalNegative().signum(), copy.getBigDecimalNegative().signum(), "bigDecimalNegative signum parity");
        }

        if (this.getBigDecimalLarge() == null) {
            assertNull(copy.getBigDecimalLarge(), "bigDecimalLarge should be null");
        } else {
            assertNotNull(copy.getBigDecimalLarge(), "bigDecimalLarge counterpart is null");
            assertEquals(0, this.getBigDecimalLarge().compareTo(copy.getBigDecimalLarge()), "bigDecimalLarge numeric equality");
        }

        if (this.getBigDecimalHighScale() == null) {
            assertNull(copy.getBigDecimalHighScale(), "bigDecimalHighScale should be null");
        } else {
            assertNotNull(copy.getBigDecimalHighScale(), "bigDecimalHighScale counterpart is null");
            assertEquals(0, this.getBigDecimalHighScale().compareTo(copy.getBigDecimalHighScale()), "bigDecimalHighScale numeric equality");
        }

        if (this.getBigDecimalDifferentScale() == null) {
            assertNull(copy.getBigDecimalDifferentScale(), "bigDecimalDifferentScale should be null");
        } else {
            assertNotNull(copy.getBigDecimalDifferentScale(), "bigDecimalDifferentScale counterpart is null");
            assertEquals(0, this.getBigDecimalDifferentScale().compareTo(copy.getBigDecimalDifferentScale()), "different scale numeric equality");
        }

        if (this.getBigDecimalNegativeZero() == null) {
            assertNull(copy.getBigDecimalNegativeZero(), "bigDecimalNegativeZero should be null");
        } else {
            assertNotNull(copy.getBigDecimalNegativeZero(), "bigDecimalNegativeZero counterpart is null");
            assertEquals(0, this.getBigDecimalNegativeZero().compareTo(copy.getBigDecimalNegativeZero()), "negative zero numeric equality");
            assertEquals(this.getBigDecimalNegativeZero().signum(), copy.getBigDecimalNegativeZero().signum(), "negative zero signum parity");
        }

        if (this.getBigDecimalWithExponent() == null) {
            assertNull(copy.getBigDecimalWithExponent(), "bigDecimalWithExponent should be null");
        } else {
            assertNotNull(copy.getBigDecimalWithExponent(), "bigDecimalWithExponent counterpart is null");
            assertEquals(0, this.getBigDecimalWithExponent().compareTo(copy.getBigDecimalWithExponent()), "exponent numeric equality");
        }

        // typical usage verifications
        if (this.getBigDecimalFromDouble() == null) {
            assertNull(copy.getBigDecimalFromDouble(), "bigDecimalFromDouble should be null");
        } else {
            assertNotNull(copy.getBigDecimalFromDouble(), "bigDecimalFromDouble counterpart is null");
            assertEquals(0, this.getBigDecimalFromDouble().compareTo(copy.getBigDecimalFromDouble()), "bigDecimalFromDouble numeric equality");
        }

        if (this.getBigDecimalValueOfDouble() == null) {
            assertNull(copy.getBigDecimalValueOfDouble(), "bigDecimalValueOfDouble should be null");
        } else {
            assertNotNull(copy.getBigDecimalValueOfDouble(), "bigDecimalValueOfDouble counterpart is null");
            assertEquals(0, this.getBigDecimalValueOfDouble().compareTo(copy.getBigDecimalValueOfDouble()), "bigDecimalValueOfDouble numeric equality");
        }

        if (this.getBigDecimalStripTrailing() == null) {
            assertNull(copy.getBigDecimalStripTrailing(), "bigDecimalStripTrailing should be null");
        } else {
            assertNotNull(copy.getBigDecimalStripTrailing(), "bigDecimalStripTrailing counterpart is null");
            assertEquals(0, this.getBigDecimalStripTrailing().compareTo(copy.getBigDecimalStripTrailing()), "bigDecimalStripTrailing numeric equality");
            // also check scale parity where meaningful
            assertEquals(this.getBigDecimalStripTrailing().scale(), copy.getBigDecimalStripTrailing().scale(), "stripTrailingZeros scale parity");
        }

        if (this.getBigDecimalNegativeScale() == null) {
            assertNull(copy.getBigDecimalNegativeScale(), "bigDecimalNegativeScale should be null");
        } else {
            assertNotNull(copy.getBigDecimalNegativeScale(), "bigDecimalNegativeScale counterpart is null");
            assertEquals(0, this.getBigDecimalNegativeScale().compareTo(copy.getBigDecimalNegativeScale()), "bigDecimalNegativeScale numeric equality");
            assertEquals(this.getBigDecimalNegativeScale().scale(), copy.getBigDecimalNegativeScale().scale(), "negative scale parity");
        }

        if (this.getBigDecimalDivideRepeating() == null) {
            assertNull(copy.getBigDecimalDivideRepeating(), "bigDecimalDivideRepeating should be null");
        } else {
            assertNotNull(copy.getBigDecimalDivideRepeating(), "bigDecimalDivideRepeating counterpart is null");
            assertEquals(0, this.getBigDecimalDivideRepeating().compareTo(copy.getBigDecimalDivideRepeating()), "bigDecimalDivideRepeating numeric equality");
            assertEquals(this.getBigDecimalDivideRepeating().scale(), copy.getBigDecimalDivideRepeating().scale(), "divide repeating scale parity");
        }

        if (this.getBigDecimalAddResult() == null) {
            assertNull(copy.getBigDecimalAddResult(), "bigDecimalAddResult should be null");
        } else {
            assertNotNull(copy.getBigDecimalAddResult(), "bigDecimalAddResult counterpart is null");
            assertEquals(0, this.getBigDecimalAddResult().compareTo(copy.getBigDecimalAddResult()), "bigDecimalAddResult numeric equality");
        }

        if (this.getBigDecimalMulResult() == null) {
            assertNull(copy.getBigDecimalMulResult(), "bigDecimalMulResult should be null");
        } else {
            assertNotNull(copy.getBigDecimalMulResult(), "bigDecimalMulResult counterpart is null");
            assertEquals(0, this.getBigDecimalMulResult().compareTo(copy.getBigDecimalMulResult()), "bigDecimalMulResult numeric equality");
        }
    }
}
