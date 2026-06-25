package test.eclipse.store.legacy.legacy.primitive.data;

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

public class PrimitiveLegacy2 {

    private static final byte SAMPLE_BYTE = 100;
    private static final short SAMPLE_SHORT = 50;
    private static final int SAMPLE_INT = 5401;
    private static final long SAMPLE_LONG = 1545455464654L;
    private static final float SAMPLE_FLOAT = 3.141526f;
    private static final double SAMPLE_DOUBLE = 3.141526545;
    private static final boolean SAMPLE_BOOLEAN = Boolean.TRUE;
    private static final char SAMPLE_CHAR = 'c';


    private Byte byteValue;
    private Short shortValue;
    private Integer intValue;
    private Long longValue;
    private Float floatValue;
    private Double doubleValue;
    private Boolean booleanValue;
    private Character charValue;

    public PrimitiveLegacy2() {
        super();
    }

    public static PrimitiveLegacy2 fillSample() {
        PrimitiveLegacy2 p = new PrimitiveLegacy2();
        p.fillSampleData();
        return p;
    }

    public void fillSampleData() {
        this.byteValue = SAMPLE_BYTE;
        this.shortValue = SAMPLE_SHORT;
        this.intValue = SAMPLE_INT;
        this.longValue = SAMPLE_LONG;
        this.floatValue = SAMPLE_FLOAT;
        this.doubleValue = SAMPLE_DOUBLE;
        this.booleanValue = SAMPLE_BOOLEAN;
        this.charValue = SAMPLE_CHAR;
    }

    public Byte getByteValue() {
        return byteValue;
    }

    public void setByteValue(Byte byteValue) {
        this.byteValue = byteValue;
    }

    public Short getShortValue() {
        return shortValue;
    }

    public void setShortValue(Short shortValue) {
        this.shortValue = shortValue;
    }

    public Integer getIntValue() {
        return intValue;
    }

    public void setIntValue(Integer intValue) {
        this.intValue = intValue;
    }

    public Long getLongValue() {
        return longValue;
    }

    public void setLongValue(Long longValue) {
        this.longValue = longValue;
    }

    public Float getFloatValue() {
        return floatValue;
    }

    public void setFloatValue(Float floatValue) {
        this.floatValue = floatValue;
    }

    public Double getDoubleValue() {
        return doubleValue;
    }

    public void setDoubleValue(Double doubleValue) {
        this.doubleValue = doubleValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public void setBooleanValue(Boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public Character getCharValue() {
        return charValue;
    }

    public void setCharValue(Character charValue) {
        this.charValue = charValue;
    }

    @Override
    public String toString() {
        return "{" +
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
