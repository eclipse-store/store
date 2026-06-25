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

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrimitiveTypes implements BinaryHandlerTestData {
    private static final byte SAMPLE_BYTE = 100;
    private static final short SAMPLE_SHORT = 50;
    private static final int SAMPLE_INT = 5401;
    private static final long SAMPLE_LONG = 1545455464654L;
    private static final float SAMPLE_FLOAT = 3.141526f;
    private static final double SAMPLE_DOUBLE = 3.141526545;
    private static final boolean SAMPLE_BOOLEAN = Boolean.TRUE;
    private static final char SAMPLE_CHAR = 'c';


    private byte byteValue;
    private short shortValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private boolean booleanValue;
    private char charValue;

    // ===== proposed edge-cases (review & cherry-pick) =====
    private byte byteMin;
    private byte byteMax;
    private byte byteZero;
    private short shortMin;
    private short shortMax;
    private int intMin;
    private int intMax;
    private long longMin;
    private long longMax;
    private float floatNaN;
    private float floatPositiveInfinity;
    private float floatNegativeInfinity;
    private float floatMin;
    private float floatMax;
    private float floatMinNormal;
    private float floatNegZero;
    private float floatPosZero;
    private double doubleNaN;
    private double doublePositiveInfinity;
    private double doubleNegativeInfinity;
    private double doubleMin;
    private double doubleMax;
    private double doubleMinNormal;
    private double doubleNegZero;
    private double doublePosZero;
    private char charNul;
    private char charMaxBmp;
    private boolean booleanFalse;

    public PrimitiveTypes() {
        super();
    }

    public static PrimitiveTypes fillSample() {
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        return p;
    }

    @Override
    public PrimitiveTypes fillSampleData() {
        this.byteValue = SAMPLE_BYTE;
        this.shortValue = SAMPLE_SHORT;
        this.intValue = SAMPLE_INT;
        this.longValue = SAMPLE_LONG;
        this.floatValue = SAMPLE_FLOAT;
        this.doubleValue = SAMPLE_DOUBLE;
        this.booleanValue = SAMPLE_BOOLEAN;
        this.charValue = SAMPLE_CHAR;

        // ===== proposed edge-cases =====
        this.byteMin = Byte.MIN_VALUE;
        this.byteMax = Byte.MAX_VALUE;
        this.byteZero = 0;
        this.shortMin = Short.MIN_VALUE;
        this.shortMax = Short.MAX_VALUE;
        this.intMin = Integer.MIN_VALUE;
        this.intMax = Integer.MAX_VALUE;
        this.longMin = Long.MIN_VALUE;
        this.longMax = Long.MAX_VALUE;
        this.floatNaN = Float.NaN;
        this.floatPositiveInfinity = Float.POSITIVE_INFINITY;
        this.floatNegativeInfinity = Float.NEGATIVE_INFINITY;
        this.floatMin = Float.MIN_VALUE;
        this.floatMax = Float.MAX_VALUE;
        this.floatMinNormal = Float.MIN_NORMAL;
        this.floatNegZero = -0.0f;
        this.floatPosZero = 0.0f;
        this.doubleNaN = Double.NaN;
        this.doublePositiveInfinity = Double.POSITIVE_INFINITY;
        this.doubleNegativeInfinity = Double.NEGATIVE_INFINITY;
        this.doubleMin = Double.MIN_VALUE;
        this.doubleMax = Double.MAX_VALUE;
        this.doubleMinNormal = Double.MIN_NORMAL;
        this.doubleNegZero = -0.0;
        this.doublePosZero = 0.0;
        this.charNul = (char) 0;
        this.charMaxBmp = (char) 0xFFFF;
        this.booleanFalse = false;

        return this;
    }

    public byte getByteValue() {
        return byteValue;
    }

    public short getShortValue() {
        return shortValue;
    }

    public int getIntValue() {
        return intValue;
    }

    public long getLongValue() {
        return longValue;
    }

    public float getFloatValue() {
        return floatValue;
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    public boolean isBooleanValue() {
        return booleanValue;
    }

    public char getCharValue() {
        return charValue;
    }

    // ===== proposed edge-cases — getters =====

    public byte getByteMin() {
        return byteMin;
    }

    public byte getByteMax() {
        return byteMax;
    }

    public byte getByteZero() {
        return byteZero;
    }

    public short getShortMin() {
        return shortMin;
    }

    public short getShortMax() {
        return shortMax;
    }

    public int getIntMin() {
        return intMin;
    }

    public int getIntMax() {
        return intMax;
    }

    public long getLongMin() {
        return longMin;
    }

    public long getLongMax() {
        return longMax;
    }

    public float getFloatNaN() {
        return floatNaN;
    }

    public float getFloatPositiveInfinity() {
        return floatPositiveInfinity;
    }

    public float getFloatNegativeInfinity() {
        return floatNegativeInfinity;
    }

    public float getFloatMin() {
        return floatMin;
    }

    public float getFloatMax() {
        return floatMax;
    }

    public float getFloatMinNormal() {
        return floatMinNormal;
    }

    public float getFloatNegZero() {
        return floatNegZero;
    }

    public float getFloatPosZero() {
        return floatPosZero;
    }

    public double getDoubleNaN() {
        return doubleNaN;
    }

    public double getDoublePositiveInfinity() {
        return doublePositiveInfinity;
    }

    public double getDoubleNegativeInfinity() {
        return doubleNegativeInfinity;
    }

    public double getDoubleMin() {
        return doubleMin;
    }

    public double getDoubleMax() {
        return doubleMax;
    }

    public double getDoubleMinNormal() {
        return doubleMinNormal;
    }

    public double getDoubleNegZero() {
        return doubleNegZero;
    }

    public double getDoublePosZero() {
        return doublePosZero;
    }

    public char getCharNul() {
        return charNul;
    }

    public char getCharMaxBmp() {
        return charMaxBmp;
    }

    public boolean isBooleanFalse() {
        return booleanFalse;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        PrimitiveTypes copy = (PrimitiveTypes) o;
        assertAll("Primitive original tests",
                () -> assertEquals(this.getByteValue(), copy.getByteValue(), "byte"),
                () -> assertEquals(this.getShortValue(), copy.getShortValue(), "short"),
                () -> assertEquals(this.getIntValue(), copy.getIntValue(), "int"),
                () -> assertEquals(this.getLongValue(), copy.getLongValue(), "long"),
                () -> assertEquals(this.getFloatValue(), copy.getFloatValue(), "float"),
                () -> assertEquals(this.getDoubleValue(), copy.getDoubleValue(), "double"),
                () -> assertEquals(this.isBooleanValue(), copy.isBooleanValue(), "boolean"),
                () -> assertEquals(this.getCharValue(), copy.getCharValue(), "char"),

                // ===== proposed edge-case verifications =====
                () -> assertEquals(this.getByteMin(), copy.getByteMin(), "byte MIN_VALUE"),
                () -> assertEquals(this.getByteMax(), copy.getByteMax(), "byte MAX_VALUE"),
                () -> assertEquals(this.getByteZero(), copy.getByteZero(), "byte 0"),
                () -> assertEquals(this.getShortMin(), copy.getShortMin(), "short MIN_VALUE"),
                () -> assertEquals(this.getShortMax(), copy.getShortMax(), "short MAX_VALUE"),
                () -> assertEquals(this.getIntMin(), copy.getIntMin(), "int MIN_VALUE"),
                () -> assertEquals(this.getIntMax(), copy.getIntMax(), "int MAX_VALUE"),
                () -> assertEquals(this.getLongMin(), copy.getLongMin(), "long MIN_VALUE"),
                () -> assertEquals(this.getLongMax(), copy.getLongMax(), "long MAX_VALUE"),
                // NaN/Infinity/signed-zero compared bit-exact (this vs copy) — works for both filled and default instances
                () -> assertEquals(Float.floatToRawIntBits(this.getFloatNaN()), Float.floatToRawIntBits(copy.getFloatNaN()), "float NaN bit-exact"),
                () -> assertEquals(Float.floatToRawIntBits(this.getFloatPositiveInfinity()), Float.floatToRawIntBits(copy.getFloatPositiveInfinity()), "float +Infinity bit-exact"),
                () -> assertEquals(Float.floatToRawIntBits(this.getFloatNegativeInfinity()), Float.floatToRawIntBits(copy.getFloatNegativeInfinity()), "float -Infinity bit-exact"),
                () -> assertEquals(this.getFloatMin(), copy.getFloatMin(), "float MIN_VALUE (smallest positive)"),
                () -> assertEquals(this.getFloatMax(), copy.getFloatMax(), "float MAX_VALUE"),
                () -> assertEquals(this.getFloatMinNormal(), copy.getFloatMinNormal(), "float MIN_NORMAL"),
                () -> assertEquals(Float.floatToRawIntBits(this.getFloatNegZero()), Float.floatToRawIntBits(copy.getFloatNegZero()), "-0.0f bit-exact"),
                () -> assertEquals(Float.floatToRawIntBits(this.getFloatPosZero()), Float.floatToRawIntBits(copy.getFloatPosZero()), "+0.0f bit-exact"),
                () -> assertEquals(Double.doubleToRawLongBits(this.getDoubleNaN()), Double.doubleToRawLongBits(copy.getDoubleNaN()), "double NaN bit-exact"),
                () -> assertEquals(Double.doubleToRawLongBits(this.getDoublePositiveInfinity()), Double.doubleToRawLongBits(copy.getDoublePositiveInfinity()), "double +Infinity bit-exact"),
                () -> assertEquals(Double.doubleToRawLongBits(this.getDoubleNegativeInfinity()), Double.doubleToRawLongBits(copy.getDoubleNegativeInfinity()), "double -Infinity bit-exact"),
                () -> assertEquals(this.getDoubleMin(), copy.getDoubleMin(), "double MIN_VALUE"),
                () -> assertEquals(this.getDoubleMax(), copy.getDoubleMax(), "double MAX_VALUE"),
                () -> assertEquals(this.getDoubleMinNormal(), copy.getDoubleMinNormal(), "double MIN_NORMAL"),
                () -> assertEquals(Double.doubleToRawLongBits(this.getDoubleNegZero()), Double.doubleToRawLongBits(copy.getDoubleNegZero()), "-0.0 bit-exact"),
                () -> assertEquals(Double.doubleToRawLongBits(this.getDoublePosZero()), Double.doubleToRawLongBits(copy.getDoublePosZero()), "+0.0 bit-exact"),
                () -> assertEquals(this.getCharNul(), copy.getCharNul(), "char NUL"),
                () -> assertEquals(this.getCharMaxBmp(), copy.getCharMaxBmp(), "char 0xFFFF"),
                () -> assertEquals(this.isBooleanFalse(), copy.isBooleanFalse(), "boolean false")
        );

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveTypes that = (PrimitiveTypes) o;
        return byteValue == that.byteValue && shortValue == that.shortValue && intValue == that.intValue && longValue == that.longValue && Float.compare(that.floatValue, floatValue) == 0 && Double.compare(that.doubleValue, doubleValue) == 0 && booleanValue == that.booleanValue && charValue == that.charValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(byteValue, shortValue, intValue, longValue, floatValue, doubleValue, booleanValue, charValue);
    }

    @Override
    public String toString() {
        return "PrimitiveTypes{" +
                "byteValue=" + byteValue +
                ", shortValue=" + shortValue +
                ", intValue=" + intValue +
                ", longValue=" + longValue +
                ", floatValue=" + floatValue +
                ", doubleValue=" + doubleValue +
                ", booleanValue=" + booleanValue +
                ", charValue=" + charValue +
                '}';
    }
}
