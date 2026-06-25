package test.eclipse.store.handler.basic;

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

import test.eclipse.store.handler.BinaryHandlerTestData;

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

    public PrimitiveTypes() {
        super();
    }

    public static PrimitiveTypes fillSample() {
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        return p;
    }

    @Override
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


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (booleanValue ? 1231 : 1237);
        result = prime * result + byteValue;
        result = prime * result + charValue;
        long temp;
        temp = Double.doubleToLongBits(doubleValue);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + Float.floatToIntBits(floatValue);
        result = prime * result + intValue;
        result = prime * result + (int) (longValue ^ (longValue >>> 32));
        result = prime * result + shortValue;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PrimitiveTypes other = (PrimitiveTypes) obj;
        if (booleanValue != other.booleanValue)
            return false;
        if (byteValue != other.byteValue)
            return false;
        if (charValue != other.charValue)
            return false;
        if (Double.doubleToLongBits(doubleValue) != Double.doubleToLongBits(other.doubleValue))
            return false;
        if (Float.floatToIntBits(floatValue) != Float.floatToIntBits(other.floatValue))
            return false;
        if (intValue != other.intValue)
            return false;
        if (longValue != other.longValue)
            return false;
        return shortValue == other.shortValue;
    }


}
