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


import static org.junit.jupiter.api.Assertions.*;

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

    // corner-case fields
    private Byte[] byteValuesWithNulls;
    private Integer[] intValuesDuplicates;
    private Float[] floatValuesSpecial; // NaN/Infinity
    private Double[] doubleValuesSpecial;
    private Character[] charValuesWithNulls;
    private Object[] objectValuesHetero;
    private Integer[][] jaggedIntMatrix;
    private Integer[][] matrixWithNullRow;
    private Byte[] emptyByteArray;
    private Integer[] largeIntArray;

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
    public BasicNonPrimitiveArrayTypes fillSampleData() {
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

        // corner-case initializations
        byteValuesWithNulls = new Byte[]{null, SAMPLE_BYTE, null};
        intValuesDuplicates = new Integer[]{7, 7, 7};
        floatValuesSpecial = new Float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        doubleValuesSpecial = new Double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        charValuesWithNulls = new Character[]{null, SAMPLE_CHAR};
        objectValuesHetero = new Object[]{"str", 123, null, new Integer[]{1,2}};
        jaggedIntMatrix = new Integer[][]{{1,2}, {3}, {}}; // rows of varying lengths
        matrixWithNullRow = new Integer[][]{{1,2}, null, {3,4}}; // middle row null
        emptyByteArray = new Byte[]{};
        largeIntArray = new Integer[512];
        for (int i = 0; i < largeIntArray.length; i++) largeIntArray[i] = i;

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        byteValues = new Byte[]{SAMPLE_BYTE_2, SAMPLE_BYTE};
        shortValues = new Short[]{SAMPLE_SHORT_2, SAMPLE_SHORT_2};
        intValues = new Integer[]{SAMPLE_INT_2, SAMPLE_INT_2};
        longValues = new Long[]{SAMPLE_LONG_2, SAMPLE_LONG_2};
        floatValues = new Float[]{SAMPLE_FLOAT_2, SAMPLE_FLOAT_2};
        doubleValues = new Double[]{SAMPLE_DOUBLE_2, SAMPLE_DOUBLE_2};
        booleanValues = new Boolean[]{Boolean.FALSE, Boolean.FALSE};
        charValues = new Character[]{SAMPLE_CHAR_2, SAMPLE_CHAR};
        objectValues = new Object[]{SAMPLE_INT_2, SAMPLE_INT_2};

        byteValueMatrix = new Byte[][]{{SAMPLE_BYTE_2, SAMPLE_BYTE_2}, {SAMPLE_BYTE, SAMPLE_BYTE_2}};
        shortValueMatrix = new Short[][]{{SAMPLE_SHORT_2, SAMPLE_SHORT_2}, {SAMPLE_SHORT, SAMPLE_SHORT_2}};
        intValueMatrix = new Integer[][]{{SAMPLE_INT_2, SAMPLE_INT_2}, {SAMPLE_INT, SAMPLE_INT_2}};
        longValueMatrix = new Long[][]{{SAMPLE_LONG_2, SAMPLE_LONG_2}, {SAMPLE_LONG, SAMPLE_LONG_2}};
        floatValueMatrix = new Float[][]{{SAMPLE_FLOAT_2, SAMPLE_FLOAT_2}, {SAMPLE_FLOAT, SAMPLE_FLOAT_2}};
        doubleValueMatrix = new Double[][]{{SAMPLE_DOUBLE_2, SAMPLE_DOUBLE_2}, {SAMPLE_DOUBLE, SAMPLE_DOUBLE_2}};
        booleanValueMatrix = new Boolean[][]{{Boolean.FALSE, Boolean.FALSE}, {Boolean.TRUE, Boolean.FALSE}};
        charValueMatrix = new Character[][]{{SAMPLE_CHAR_2, SAMPLE_CHAR_2}, {SAMPLE_CHAR, SAMPLE_CHAR_2}};
        objectValueMatrix = new Object[][]{{SAMPLE_INT_2, SAMPLE_INT_2}, {SAMPLE_INT, SAMPLE_INT_2}};

        // corner-case updates
        byteValuesWithNulls = new Byte[]{SAMPLE_BYTE, null};
        intValuesDuplicates = new Integer[]{7, 7, 7, 7};
        floatValuesSpecial = new Float[]{Float.NaN, Float.NaN};
        doubleValuesSpecial = new Double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        charValuesWithNulls = new Character[]{SAMPLE_CHAR, null};
        objectValuesHetero = new Object[]{"changed", 456, new Integer[]{3,4}};
        jaggedIntMatrix = new Integer[][]{{9}, {8,7,6}};
        matrixWithNullRow = new Integer[][]{{5,6}, {7}, {8,9}}; // remove null row
        emptyByteArray = new Byte[]{}; // still empty
        if (largeIntArray != null && largeIntArray.length > 0) largeIntArray[0] = -999;

        return this;
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

    // getters for corner-case fields
    public Byte[] getByteValuesWithNulls() { return byteValuesWithNulls; }
    public Integer[] getIntValuesDuplicates() { return intValuesDuplicates; }
    public Float[] getFloatValuesSpecial() { return floatValuesSpecial; }
    public Double[] getDoubleValuesSpecial() { return doubleValuesSpecial; }
    public Character[] getCharValuesWithNulls() { return charValuesWithNulls; }
    public Object[] getObjectValuesHetero() { return objectValuesHetero; }
    public Integer[][] getJaggedIntMatrix() { return jaggedIntMatrix; }
    public Integer[][] getMatrixWithNullRow() { return matrixWithNullRow; }
    public Byte[] getEmptyByteArray() { return emptyByteArray; }
    public Integer[] getLargeIntArray() { return largeIntArray; }


    @Override
    public void proveResults(Object o) {
        assertNotNull(o);
        BasicNonPrimitiveArrayTypes copy = (BasicNonPrimitiveArrayTypes)  o;
        assertAll("Primitive Types tests", //
                () -> assertArrayEquals(this.getByteValues(), copy.getByteValues(), "Byte[]"),
                () -> assertArrayEquals(this.getShortValues(), copy.getShortValues(), "Short[]"),
                () -> assertArrayEquals(this.getIntValues(), copy.getIntValues(), "Integer[]"),
                () -> assertArrayEquals(this.getLongValues(), copy.getLongValues(), "Long[]"),
                () -> assertArrayEquals(this.getFloatValues(), copy.getFloatValues(), "Float[]"),
                () -> assertArrayEquals(this.getDoubleValues(), copy.getDoubleValues(), "Double[]"),
                () -> assertArrayEquals(this.getBooleanValues(), copy.getBooleanValues(), "Boolean[]"),
                () -> assertArrayEquals(this.getCharValues(), copy.getCharValues(), "Character[]"),
                () -> assertArrayEquals(this.getObjectValues(), copy.getObjectValues(), "Object[]"),

                () -> assertArrayEquals(this.getByteValueMatrix(), copy.getByteValueMatrix(), "Byte[][]"),
                () -> assertArrayEquals(this.getShortValueMatrix(), copy.getShortValueMatrix(), "Short[][]"),
                () -> assertArrayEquals(this.getIntValueMatrix(), copy.getIntValueMatrix(), "Integer[][]"),
                () -> assertArrayEquals(this.getLongValueMatrix(), copy.getLongValueMatrix(), "Long[][]"),
                () -> assertArrayEquals(this.getFloatValueMatrix(), copy.getFloatValueMatrix(), "Float[][]"),
                () -> assertArrayEquals(this.getDoubleValueMatrix(), copy.getDoubleValueMatrix(), "Double[][]"),
                () -> assertArrayEquals(this.getBooleanValueMatrix(), copy.getBooleanValueMatrix(), "Boolean[][]"),
                () -> assertArrayEquals(this.getCharValueMatrix(), copy.getCharValueMatrix(), "Character[][]"),
                () -> assertArrayEquals(this.getObjectValueMatrix(), copy.getObjectValueMatrix(), "Object[][]"),

                // corner-case verifications
                () -> {
                    if (this.getByteValuesWithNulls() == null) {
                        assertNull(copy.getByteValuesWithNulls());
                    } else {
                        assertArrayEquals(this.getByteValuesWithNulls(), copy.getByteValuesWithNulls(), "Byte[] with nulls");
                    }
                },
                () -> {
                    if (this.getIntValuesDuplicates() == null) {
                        assertNull(copy.getIntValuesDuplicates());
                    } else {
                        assertArrayEquals(this.getIntValuesDuplicates(), copy.getIntValuesDuplicates(), "Integer[] duplicates");
                    }
                },
                () -> {
                    if (this.getFloatValuesSpecial() == null) {
                        assertNull(copy.getFloatValuesSpecial());
                    } else {
                        // compare NaN/Infinity parity elementwise
                        Float[] a = this.getFloatValuesSpecial();
                        Float[] b = copy.getFloatValuesSpecial();
                        assertEquals(a.length, b.length, "float special length");
                        for (int i = 0; i < a.length; i++) {
                            Float va = a[i];
                            Float vb = b[i];
                            if (va == null && vb == null) continue;
                            if (va == null || vb == null) fail("float special null mismatch at " + i);
                            if (Float.isNaN(va) || Float.isNaN(vb)) {
                                assertEquals(Float.isNaN(va), Float.isNaN(vb), "float NaN parity at " + i);
                            } else {
                                assertEquals(va, vb, "float value at " + i);
                            }
                        }
                    }
                },
                () -> {
                    if (this.getDoubleValuesSpecial() == null) {
                        assertNull(copy.getDoubleValuesSpecial());
                    } else {
                        Double[] a = this.getDoubleValuesSpecial();
                        Double[] b = copy.getDoubleValuesSpecial();
                        assertEquals(a.length, b.length, "double special length");
                        for (int i = 0; i < a.length; i++) {
                            Double va = a[i];
                            Double vb = b[i];
                            if (va == null && vb == null) continue;
                            if (va == null || vb == null) fail("double special null mismatch at " + i);
                            if (Double.isNaN(va) || Double.isNaN(vb)) {
                                assertEquals(Double.isNaN(va), Double.isNaN(vb), "double NaN parity at " + i);
                            } else {
                                assertEquals(va, vb, "double value at " + i);
                            }
                        }
                    }
                },
                () -> {
                    if (this.getCharValuesWithNulls() == null) {
                        assertNull(copy.getCharValuesWithNulls());
                    } else {
                        assertArrayEquals(this.getCharValuesWithNulls(), copy.getCharValuesWithNulls(), "Character[] with nulls");
                    }
                },
                () -> {
                    if (this.getObjectValuesHetero() == null) {
                        assertNull(copy.getObjectValuesHetero());
                    } else {
                        assertArrayEquals(this.getObjectValuesHetero(), copy.getObjectValuesHetero(), "Object[] hetero");
                    }
                },
                () -> {
                    if (this.getJaggedIntMatrix() == null) {
                        assertNull(copy.getJaggedIntMatrix());
                    } else {
                        Integer[][] a = this.getJaggedIntMatrix();
                        Integer[][] b = copy.getJaggedIntMatrix();
                        assertEquals(a.length, b.length, "jagged matrix rows");
                        for (int r = 0; r < a.length; r++) {
                            Integer[] ar = a[r];
                            Integer[] br = b[r];
                            if (ar == null && br == null) continue;
                            if (ar == null || br == null) fail("jagged row null mismatch at " + r);
                            assertArrayEquals(ar, br, "jagged row " + r);
                        }
                    }
                },
                () -> {
                    if (this.getMatrixWithNullRow() == null) {
                        assertNull(copy.getMatrixWithNullRow());
                    } else {
                        Integer[][] a = this.getMatrixWithNullRow();
                        Integer[][] b = copy.getMatrixWithNullRow();
                        assertEquals(a.length, b.length, "matrixWithNullRow rows");
                        for (int r = 0; r < a.length; r++) {
                            Integer[] ar = a[r];
                            Integer[] br = b[r];
                            if (ar == null && br == null) continue;
                            if (ar == null || br == null) fail("matrixWithNullRow mismatch at " + r);
                            assertArrayEquals(ar, br, "matrixWithNullRow row " + r);
                        }
                    }
                },
                () -> {
                    if (this.getEmptyByteArray() == null) {
                        assertNull(copy.getEmptyByteArray());
                    } else {
                        assertArrayEquals(this.getEmptyByteArray(), copy.getEmptyByteArray(), "empty byte array");
                    }
                },
                () -> {
                    if (this.getLargeIntArray() == null) {
                        assertNull(copy.getLargeIntArray());
                    } else {
                        assertEquals(this.getLargeIntArray().length, copy.getLargeIntArray().length, "large array length");
                        assertEquals(this.getLargeIntArray()[0], copy.getLargeIntArray()[0], "large array first elem");
                    }
                }

        );
    }
}
