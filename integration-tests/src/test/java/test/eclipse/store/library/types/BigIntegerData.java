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

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BigIntegerData implements BinaryHandlerTestData {
    private BigInteger bigIntegerValue;

    // corner-case fields and typical usage
    private BigInteger bigIntegerNull;
    private BigInteger bigIntegerZero;
    private BigInteger bigIntegerNegative;
    private BigInteger bigIntegerLarge;
    private BigInteger bigIntegerFromBytes;
    private BigInteger bigIntegerFromHex;
    private BigInteger bigIntegerShiftLeft;
    private BigInteger bigIntegerPow;
    private BigInteger bigIntegerGcd;
    private BigInteger bigIntegerModPow;
    private BigInteger bigIntegerAddResult;
    private BigInteger bigIntegerMulResult;

    @Override
    public BigIntegerData fillSampleData() {
        bigIntegerValue = new java.math.BigInteger("852");

        // corner-case and typical initializations
        bigIntegerNull = null;
        bigIntegerZero = BigInteger.ZERO;
        bigIntegerNegative = new BigInteger("-98765432109876543210");
        bigIntegerLarge = new BigInteger("1234567890123456789012345678901234567890");
        bigIntegerFromBytes = new BigInteger(new byte[]{0x00, 0x01, 0x02, 0x03}); // leading zero byte
        bigIntegerFromHex = new BigInteger("FFEEAABB", 16);
        bigIntegerShiftLeft = bigIntegerValue.shiftLeft(20);
        bigIntegerPow = bigIntegerValue.pow(6);
        bigIntegerGcd = bigIntegerValue.gcd(new BigInteger("1000"));
        bigIntegerModPow = bigIntegerValue.modPow(new BigInteger("3"), new BigInteger("1009"));
        bigIntegerAddResult = bigIntegerValue.add(new BigInteger("1000"));
        bigIntegerMulResult = bigIntegerValue.multiply(new BigInteger("7"));

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        bigIntegerValue = bigIntegerValue.add(new BigInteger("444"));

        // corner-case updates
        bigIntegerNull = BigInteger.ONE; // from null to a value
        bigIntegerZero = BigInteger.ZERO; // keep zero but possibly different instance
        bigIntegerNegative = bigIntegerNegative.negate(); // flip sign
        bigIntegerLarge = bigIntegerLarge.add(BigInteger.TEN);
        bigIntegerFromBytes = new BigInteger(new byte[]{0x01, 0x02});
        bigIntegerFromHex = new BigInteger("ABCDEF01", 16);
        bigIntegerShiftLeft = bigIntegerShiftLeft.shiftLeft(5);
        bigIntegerPow = bigIntegerPow.multiply(BigInteger.valueOf(2));
        bigIntegerGcd = bigIntegerGcd.gcd(new BigInteger("256"));
        bigIntegerModPow = bigIntegerModPow.modPow(new BigInteger("5"), new BigInteger("2003"));
        bigIntegerAddResult = bigIntegerValue.add(new BigInteger("2000"));
        bigIntegerMulResult = bigIntegerValue.multiply(new BigInteger("11"));

        return this;
    }

    BigInteger getBigIntegerValue() {
        return bigIntegerValue;
    }

    // getters for corner-case fields
    public BigInteger getBigIntegerNull() { return bigIntegerNull; }
    public BigInteger getBigIntegerZero() { return bigIntegerZero; }
    public BigInteger getBigIntegerNegative() { return bigIntegerNegative; }
    public BigInteger getBigIntegerLarge() { return bigIntegerLarge; }
    public BigInteger getBigIntegerFromBytes() { return bigIntegerFromBytes; }
    public BigInteger getBigIntegerFromHex() { return bigIntegerFromHex; }
    public BigInteger getBigIntegerShiftLeft() { return bigIntegerShiftLeft; }
    public BigInteger getBigIntegerPow() { return bigIntegerPow; }
    public BigInteger getBigIntegerGcd() { return bigIntegerGcd; }
    public BigInteger getBigIntegerModPow() { return bigIntegerModPow; }
    public BigInteger getBigIntegerAddResult() { return bigIntegerAddResult; }
    public BigInteger getBigIntegerMulResult() { return bigIntegerMulResult; }

    @Override
    public void proveResults(Object o) {
        assertNotNull(o);
        BigIntegerData copy = (BigIntegerData) o;
        assertEquals(this.getBigIntegerValue(), copy.getBigIntegerValue(), "BigInteger");

        // corner-case verifications with null guards
        if (this.getBigIntegerNull() == null) {
            assertNull(copy.getBigIntegerNull(), "bigIntegerNull should be null");
        } else {
            assertNotNull(copy.getBigIntegerNull(), "bigIntegerNull counterpart is null");
            assertEquals(this.getBigIntegerNull(), copy.getBigIntegerNull(), "bigIntegerNull value");
        }

        if (this.getBigIntegerZero() == null) {
            assertNull(copy.getBigIntegerZero(), "bigIntegerZero should be null");
        } else {
            assertNotNull(copy.getBigIntegerZero(), "bigIntegerZero counterpart is null");
            assertEquals(this.getBigIntegerZero(), copy.getBigIntegerZero(), "bigIntegerZero numeric equality");
        }

        if (this.getBigIntegerNegative() == null) {
            assertNull(copy.getBigIntegerNegative(), "bigIntegerNegative should be null");
        } else {
            assertNotNull(copy.getBigIntegerNegative(), "bigIntegerNegative counterpart is null");
            assertEquals(this.getBigIntegerNegative(), copy.getBigIntegerNegative(), "bigIntegerNegative numeric equality");
        }

        if (this.getBigIntegerLarge() == null) {
            assertNull(copy.getBigIntegerLarge(), "bigIntegerLarge should be null");
        } else {
            assertNotNull(copy.getBigIntegerLarge(), "bigIntegerLarge counterpart is null");
            assertEquals(this.getBigIntegerLarge(), copy.getBigIntegerLarge(), "bigIntegerLarge numeric equality");
        }

        if (this.getBigIntegerFromBytes() == null) {
            assertNull(copy.getBigIntegerFromBytes(), "bigIntegerFromBytes should be null");
        } else {
            assertNotNull(copy.getBigIntegerFromBytes(), "bigIntegerFromBytes counterpart is null");
            assertEquals(this.getBigIntegerFromBytes(), copy.getBigIntegerFromBytes(), "bigIntegerFromBytes equality");
        }

        if (this.getBigIntegerFromHex() == null) {
            assertNull(copy.getBigIntegerFromHex(), "bigIntegerFromHex should be null");
        } else {
            assertNotNull(copy.getBigIntegerFromHex(), "bigIntegerFromHex counterpart is null");
            assertEquals(this.getBigIntegerFromHex(), copy.getBigIntegerFromHex(), "bigIntegerFromHex equality");
        }

        if (this.getBigIntegerShiftLeft() == null) {
            assertNull(copy.getBigIntegerShiftLeft(), "bigIntegerShiftLeft should be null");
        } else {
            assertNotNull(copy.getBigIntegerShiftLeft(), "bigIntegerShiftLeft counterpart is null");
            assertEquals(this.getBigIntegerShiftLeft(), copy.getBigIntegerShiftLeft(), "bigIntegerShiftLeft equality");
        }

        if (this.getBigIntegerPow() == null) {
            assertNull(copy.getBigIntegerPow(), "bigIntegerPow should be null");
        } else {
            assertNotNull(copy.getBigIntegerPow(), "bigIntegerPow counterpart is null");
            assertEquals(this.getBigIntegerPow(), copy.getBigIntegerPow(), "bigIntegerPow equality");
        }

        if (this.getBigIntegerGcd() == null) {
            assertNull(copy.getBigIntegerGcd(), "bigIntegerGcd should be null");
        } else {
            assertNotNull(copy.getBigIntegerGcd(), "bigIntegerGcd counterpart is null");
            assertEquals(this.getBigIntegerGcd(), copy.getBigIntegerGcd(), "bigIntegerGcd equality");
        }

        if (this.getBigIntegerModPow() == null) {
            assertNull(copy.getBigIntegerModPow(), "bigIntegerModPow should be null");
        } else {
            assertNotNull(copy.getBigIntegerModPow(), "bigIntegerModPow counterpart is null");
            assertEquals(this.getBigIntegerModPow(), copy.getBigIntegerModPow(), "bigIntegerModPow equality");
        }

        if (this.getBigIntegerAddResult() == null) {
            assertNull(copy.getBigIntegerAddResult(), "bigIntegerAddResult should be null");
        } else {
            assertNotNull(copy.getBigIntegerAddResult(), "bigIntegerAddResult counterpart is null");
            assertEquals(this.getBigIntegerAddResult(), copy.getBigIntegerAddResult(), "bigIntegerAddResult equality");
        }

        if (this.getBigIntegerMulResult() == null) {
            assertNull(copy.getBigIntegerMulResult(), "bigIntegerMulResult should be null");
        } else {
            assertNotNull(copy.getBigIntegerMulResult(), "bigIntegerMulResult counterpart is null");
            assertEquals(this.getBigIntegerMulResult(), copy.getBigIntegerMulResult(), "bigIntegerMulResult equality");
        }
    }
}
