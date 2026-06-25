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

public class BasicNonPrimitiveArrayTypes implements BinaryHandlerTestData {
    private static final Byte SAMPLE_BYTE = 100;
    private static final Byte SAMPLE_BYTE_2 = 127;
    private static final Short SAMPLE_SHORT = 50;
    private static final Short SAMPLE_SHORT_2 = 100;
    private static final Integer SAMPLE_INT = 5401;
    private static final Integer SAMPLE_INT_2 = 4401;
    private static final Long SAMPLE_LONG = 1545455464654L;
    private static final Long SAMPLE_LONG_2 = 25546455464654L;
    private static final Float SAMPLE_FLOAT = 3.141526f;
    private static final Float SAMPLE_FLOAT_2 = 2.141526f;
    private static final Double SAMPLE_DOUBLE = 3.141526545;
    private static final Double SAMPLE_DOUBLE_2 = 5.141526545;
    private static final Character SAMPLE_CHAR = 'c';
    private static final Character SAMPLE_CHAR_2 = 'x';

    private Byte[] byteValues;
    private Short[] shortValues;
    private Integer[] intValues;
    private Long[] longValues;
    private Float[] floatValues;
    private Double[] doubleValues;
    private Boolean[] booleanValues;
    private Character[] charValues;
    private Object[] objectValues;

    private Byte[][] byteValueMatrix;
    private Short[][] shortValueMatrix;
    private Integer[][] intValueMatrix;
    private Long[][] longValueMatrix;
    private Float[][] floatValueMatrix;
    private Double[][] doubleValueMatrix;
    private Boolean[][] booleanValueMatrix;
    private Character[][] charValueMatrix;
    private Object[][] objectValueMatrix;

    public BasicNonPrimitiveArrayTypes() {
        byteValues = new Byte[1];
        shortValues = new Short[1];
        intValues = new Integer[1];
        longValues = new Long[1];
        floatValues = new Float[1];
        doubleValues = new Double[1];
        booleanValues = new Boolean[1];
        charValues = new Character[1];

        byteValueMatrix = new Byte[1][1];
        shortValueMatrix = new Short[1][1];
        intValueMatrix = new Integer[1][1];
        longValueMatrix = new Long[1][1];
        floatValueMatrix = new Float[1][1];
        doubleValueMatrix = new Double[1][1];
        booleanValueMatrix = new Boolean[1][1];
        charValueMatrix = new Character[1][1];
    }

    @Override
    public void fillSampleData() {
        byteValues = new Byte[]{SAMPLE_BYTE, SAMPLE_BYTE_2};
        shortValues = new Short[]{SAMPLE_SHORT, SAMPLE_SHORT_2};
        intValues = new Integer[]{SAMPLE_INT, SAMPLE_INT_2};
        longValues = new Long[]{SAMPLE_LONG, SAMPLE_LONG_2};
        floatValues = new Float[]{SAMPLE_FLOAT, SAMPLE_FLOAT_2};
        doubleValues = new Double[]{SAMPLE_DOUBLE, SAMPLE_DOUBLE_2};
        booleanValues = new Boolean[]{Boolean.TRUE, Boolean.FALSE};
        charValues = new Character[]{SAMPLE_CHAR, SAMPLE_CHAR_2};
        objectValues = new Object[]{SAMPLE_INT, SAMPLE_INT_2};

        byteValueMatrix = new Byte[][]{{SAMPLE_BYTE, SAMPLE_BYTE_2}, {SAMPLE_BYTE, SAMPLE_BYTE_2}};
        shortValueMatrix = new Short[][]{{SAMPLE_SHORT, SAMPLE_SHORT_2}, {SAMPLE_SHORT, SAMPLE_SHORT_2}};
        intValueMatrix = new Integer[][]{{SAMPLE_INT, SAMPLE_INT_2}, {SAMPLE_INT, SAMPLE_INT_2}};
        longValueMatrix = new Long[][]{{SAMPLE_LONG, SAMPLE_LONG_2}, {SAMPLE_LONG, SAMPLE_LONG_2}};
        floatValueMatrix = new Float[][]{{SAMPLE_FLOAT, SAMPLE_FLOAT_2}, {SAMPLE_FLOAT, SAMPLE_FLOAT_2}};
        doubleValueMatrix = new Double[][]{{SAMPLE_DOUBLE, SAMPLE_DOUBLE_2}, {SAMPLE_DOUBLE, SAMPLE_DOUBLE_2}};
        booleanValueMatrix = new Boolean[][]{{Boolean.TRUE, Boolean.FALSE}, {Boolean.TRUE, Boolean.FALSE}};
        charValueMatrix = new Character[][]{{SAMPLE_CHAR, SAMPLE_CHAR_2}, {SAMPLE_CHAR, SAMPLE_CHAR_2}};
        objectValueMatrix = new Object[][]{{SAMPLE_INT, SAMPLE_INT_2}, {SAMPLE_INT, SAMPLE_INT_2}};
    }


    public Byte[] getByteValues() {
        return byteValues;
    }

    public Short[] getShortValues() {
        return shortValues;
    }

    public Integer[] getIntValues() {
        return intValues;
    }

    public Long[] getLongValues() {
        return longValues;
    }

    public Float[] getFloatValues() {
        return floatValues;
    }

    public Double[] getDoubleValues() {
        return doubleValues;
    }

    public Boolean[] getBooleanValues() {
        return booleanValues;
    }

    public Character[] getCharValues() {
        return charValues;
    }

    public Byte[][] getByteValueMatrix() {
        return byteValueMatrix;
    }

    public Short[][] getShortValueMatrix() {
        return shortValueMatrix;
    }

    public Integer[][] getIntValueMatrix() {
        return intValueMatrix;
    }

    public Long[][] getLongValueMatrix() {
        return longValueMatrix;
    }

    public Float[][] getFloatValueMatrix() {
        return floatValueMatrix;
    }

    public Double[][] getDoubleValueMatrix() {
        return doubleValueMatrix;
    }

    public Boolean[][] getBooleanValueMatrix() {
        return booleanValueMatrix;
    }

    public Character[][] getCharValueMatrix() {
        return charValueMatrix;
    }

    public Object[] getObjectValues() {
        return objectValues;
    }

    public Object[][] getObjectValueMatrix() {
        return objectValueMatrix;
    }
}
